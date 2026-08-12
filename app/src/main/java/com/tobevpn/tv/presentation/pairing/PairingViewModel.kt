package com.tobevpn.tv.presentation.pairing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.DevicePairingPollResult
import com.tobevpn.tv.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PairingMode {
    OWN_ACCOUNT,
    OTHER_DEVICE,
}

sealed interface PairingUiState {
    data object Loading : PairingUiState
    data class WaitingForScan(val qrData: String, val code: String? = null) : PairingUiState
    data object Authenticating : PairingUiState
    data object Success : PairingUiState
    data class Error(val message: String? = null, @param:StringRes val messageRes: Int = R.string.error_generic) : PairingUiState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    companion object {
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_ATTEMPTS = 60
        // Cap the auto-regenerate cycle. After this many idle expirations we stop
        // burning network calls and ask the user to retry manually — common case
        // is "TV left on overnight on the pairing screen".
        private const val MAX_REQUEST_RETRIES = 5
    }

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Loading)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(PairingMode.OWN_ACCOUNT)
    val mode: StateFlow<PairingMode> = _mode.asStateFlow()

    private var pollJob: Job? = null
    private var consecutiveExpirations = 0
    private var requestVersion = 0

    init {
        observeAuthenticatedSession()
        requestCode()
    }

    fun requestCode() {
        pollJob?.cancel()
        consecutiveExpirations = 0
        val version = nextRequestVersion()
        viewModelScope.launch {
            if (completeIfAlreadyAuthenticated()) return@launch
            _state.value = PairingUiState.Loading
            requestCurrentModeCode(version)
        }
    }

    /** Hidden path for Google Play review — see [AuthRepository.completeDemoLogin]. */
    fun completeDemoLogin() {
        viewModelScope.launch {
            authRepository.completeDemoLogin()
            completeAuthentication()
        }
    }

    fun selectMode(mode: PairingMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        requestCode()
    }

    private fun rotateCode() {
        // Internal recycle path — preserve consecutiveExpirations across rotations
        // so we can stop after MAX_REQUEST_RETRIES idle cycles. requestCode() is
        // the user-initiated path and resets the counter.
        pollJob?.cancel()
        val version = nextRequestVersion()
        viewModelScope.launch {
            if (completeIfAlreadyAuthenticated()) return@launch
            _state.value = PairingUiState.Loading
            requestCurrentModeCode(version)
        }
    }

    private suspend fun requestCurrentModeCode(version: Int) {
        when (_mode.value) {
            PairingMode.OWN_ACCOUNT -> requestOwnAccountCode(version)
            PairingMode.OTHER_DEVICE -> requestDevicePairingCode(version)
        }
    }

    private suspend fun requestOwnAccountCode(version: Int) {
        authRepository.requestTelegramAuth()
            .onSuccess { authToken ->
                if (!isCurrentRequest(version)) return
                _state.value = PairingUiState.WaitingForScan(
                    qrData = TelegramLinks.buildWebStartLink(authToken),
                )
                startTelegramPolling(authToken, version)
            }
            .onFailure { e ->
                if (!isCurrentRequest(version)) return
                _state.value = PairingUiState.Error(
                    message = e.message,
                    messageRes = R.string.auth_error_server,
                )
            }
    }

    private suspend fun requestDevicePairingCode(version: Int) {
        authRepository.requestDevicePairing()
            .onSuccess { pairing ->
                if (!isCurrentRequest(version)) return
                _state.value = PairingUiState.WaitingForScan(
                    qrData = createPairingDeepLink(pairing.code),
                    code = pairing.code,
                )
                startPolling(pairing.code, version)
            }
            .onFailure { e ->
                if (!isCurrentRequest(version)) return
                _state.value = PairingUiState.Error(
                    message = e.message,
                    messageRes = R.string.auth_error_server,
                )
            }
    }

    private fun createPairingDeepLink(code: String): String {
        return "tobevpn://pair?code=$code"
    }

    private fun startTelegramPolling(authToken: String, version: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                if (!isCurrentRequest(version)) return@launch
                val result = authRepository.checkAuthStatus(authToken)
                if (!isCurrentRequest(version)) return@launch
                result.onSuccess { authenticated ->
                    if (authenticated) {
                        completeAuthentication()
                        return@launch
                    }
                }
                result.onFailure { e ->
                    if (e.message == "expired") {
                        consecutiveExpirations++
                        if (consecutiveExpirations >= MAX_REQUEST_RETRIES) {
                            _state.value = PairingUiState.Error(
                                messageRes = R.string.auth_error_server,
                            )
                            return@launch
                        }
                        rotateCode()
                    } else {
                        if (!isCurrentRequest(version)) return@launch
                        _state.value = PairingUiState.Error(
                            message = e.message,
                            messageRes = R.string.auth_error_server,
                        )
                    }
                    return@launch
                }
            }
            consecutiveExpirations++
            if (consecutiveExpirations >= MAX_REQUEST_RETRIES) {
                if (!isCurrentRequest(version)) return@launch
                _state.value = PairingUiState.Error(
                    messageRes = R.string.auth_error_server,
                )
                return@launch
            }
            rotateCode()
        }
    }

    private fun startPolling(code: String, version: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                if (!isCurrentRequest(version)) return@launch
                val result = authRepository.checkDevicePairingStatus(code)
                if (!isCurrentRequest(version)) return@launch
                result.onSuccess { status ->
                    when (status) {
                        DevicePairingPollResult.Completed -> {
                            completeAuthentication()
                            return@launch
                        }
                        DevicePairingPollResult.Expired -> {
                            consecutiveExpirations++
                            if (consecutiveExpirations >= MAX_REQUEST_RETRIES) {
                                _state.value = PairingUiState.Error(
                                    messageRes = R.string.auth_error_server,
                                )
                                return@launch
                            }
                            rotateCode()
                            return@launch
                        }
                        DevicePairingPollResult.Pending -> Unit
                    }
                }
                result.onFailure { e ->
                    if (!isCurrentRequest(version)) return@launch
                    _state.value = PairingUiState.Error(
                        message = e.message,
                        messageRes = R.string.auth_error_server,
                    )
                    return@launch
                }
            }
            // Polling window finished without auth or explicit expiry — recycle once,
            // unless we've already burned through the retry budget.
            consecutiveExpirations++
            if (consecutiveExpirations >= MAX_REQUEST_RETRIES) {
                if (!isCurrentRequest(version)) return@launch
                _state.value = PairingUiState.Error(
                    messageRes = R.string.auth_error_server,
                )
                return@launch
            }
            rotateCode()
        }
    }

    private fun observeAuthenticatedSession() {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { authState ->
                if (authState is AuthState.Authenticated) {
                    completeAuthentication()
                }
            }
        }
    }

    private suspend fun completeIfAlreadyAuthenticated(): Boolean {
        return if (authRepository.getAuthStateSnapshot() is AuthState.Authenticated) {
            completeAuthentication()
            true
        } else {
            false
        }
    }

    private fun completeAuthentication() {
        requestVersion++
        pollJob?.cancel()
        _state.value = PairingUiState.Authenticating
        _state.value = PairingUiState.Success
    }

    private fun nextRequestVersion(): Int {
        requestVersion += 1
        return requestVersion
    }

    private fun isCurrentRequest(version: Int): Boolean {
        return version == requestVersion && _state.value !is PairingUiState.Success
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

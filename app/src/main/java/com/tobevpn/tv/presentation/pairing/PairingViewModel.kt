package com.tobevpn.tv.presentation.pairing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.DevicePairingPollResult
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

    init {
        requestCode()
    }

    fun requestCode() {
        pollJob?.cancel()
        consecutiveExpirations = 0
        _state.value = PairingUiState.Loading
        requestCurrentModeCode()
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
        _state.value = PairingUiState.Loading
        requestCurrentModeCode()
    }

    private fun requestCurrentModeCode() {
        when (_mode.value) {
            PairingMode.OWN_ACCOUNT -> requestOwnAccountCode()
            PairingMode.OTHER_DEVICE -> requestDevicePairingCode()
        }
    }

    private fun requestOwnAccountCode() {
        viewModelScope.launch {
            authRepository.requestTelegramAuth()
                .onSuccess { authToken ->
                    _state.value = PairingUiState.WaitingForScan(
                        qrData = TelegramLinks.buildWebStartLink(authToken),
                    )
                    startTelegramPolling(authToken)
                }
                .onFailure { e ->
                    _state.value = PairingUiState.Error(
                        message = e.message,
                        messageRes = R.string.auth_error_server,
                    )
                }
        }
    }

    private fun requestDevicePairingCode() {
        viewModelScope.launch {
            authRepository.requestDevicePairing()
                .onSuccess { pairing ->
                    _state.value = PairingUiState.WaitingForScan(
                        qrData = createPairingDeepLink(pairing.code),
                        code = pairing.code,
                    )
                    startPolling(pairing.code)
                }
                .onFailure { e ->
                    _state.value = PairingUiState.Error(
                        message = e.message,
                        messageRes = R.string.auth_error_server,
                    )
                }
        }
    }

    private fun createPairingDeepLink(code: String): String {
        return "tobevpn://pair?code=$code"
    }

    private fun startTelegramPolling(authToken: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                val result = authRepository.checkAuthStatus(authToken)
                result.onSuccess { authenticated ->
                    if (authenticated) {
                        _state.value = PairingUiState.Authenticating
                        _state.value = PairingUiState.Success
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
                _state.value = PairingUiState.Error(
                    messageRes = R.string.auth_error_server,
                )
                return@launch
            }
            rotateCode()
        }
    }

    private fun startPolling(code: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                val result = authRepository.checkDevicePairingStatus(code)
                result.onSuccess { status ->
                    when (status) {
                        DevicePairingPollResult.Completed -> {
                            _state.value = PairingUiState.Authenticating
                            _state.value = PairingUiState.Success
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
                _state.value = PairingUiState.Error(
                    messageRes = R.string.auth_error_server,
                )
                return@launch
            }
            rotateCode()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

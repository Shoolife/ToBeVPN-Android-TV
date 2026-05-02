package com.tobevpn.tv.presentation.pairing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PairingUiState {
    data object Loading : PairingUiState
    data class WaitingForScan(val qrData: String) : PairingUiState
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

    private var pollJob: Job? = null
    private var consecutiveExpirations = 0

    init {
        requestCode()
    }

    fun requestCode() {
        pollJob?.cancel()
        consecutiveExpirations = 0
        _state.value = PairingUiState.Loading
        viewModelScope.launch {
            authRepository.requestTelegramAuth()
                .onSuccess { authToken ->
                    _state.value = PairingUiState.WaitingForScan(
                        TelegramLinks.buildWebStartLink(authToken)
                    )
                    startPolling(authToken)
                }
                .onFailure { e ->
                    _state.value = PairingUiState.Error(
                        message = e.message,
                        messageRes = R.string.auth_error_server,
                    )
                }
        }
    }

    private fun rotateCode() {
        // Internal recycle path — preserve consecutiveExpirations across rotations
        // so we can stop after MAX_REQUEST_RETRIES idle cycles. requestCode() is
        // the user-initiated path and resets the counter.
        pollJob?.cancel()
        _state.value = PairingUiState.Loading
        viewModelScope.launch {
            authRepository.requestTelegramAuth()
                .onSuccess { authToken ->
                    _state.value = PairingUiState.WaitingForScan(
                        TelegramLinks.buildWebStartLink(authToken)
                    )
                    startPolling(authToken)
                }
                .onFailure { e ->
                    _state.value = PairingUiState.Error(
                        message = e.message,
                        messageRes = R.string.auth_error_server,
                    )
                }
        }
    }

    private fun startPolling(authToken: String) {
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
                        return@launch
                    }
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

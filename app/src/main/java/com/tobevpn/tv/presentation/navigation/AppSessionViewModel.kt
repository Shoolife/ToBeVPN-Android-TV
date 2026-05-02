package com.tobevpn.tv.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectionManager: VpnConnectionManager,
) : ViewModel() {

    companion object {
        // 60s gives the server enough of a window to flip "unlinked" → tunnel-down
        // without spamming /api/device/refresh roughly nine thousand times a day on
        // a TV that's typically left on for hours.
        private const val DEVICE_LINK_POLL_INTERVAL_MS = 60_000L
    }

    val authState: StateFlow<AuthState?> = authRepository.observeAuthState()
        .map<AuthState, AuthState?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .map { state -> state is AuthState.Authenticated }
                .distinctUntilChanged()
                .collectLatest { isAuthenticated ->
                if (!isAuthenticated) return@collectLatest

                while (isActive) {
                    delay(DEVICE_LINK_POLL_INTERVAL_MS)
                    val stillLinked = authRepository.syncDeviceSessionState().getOrNull()
                    if (stillLinked == false) {
                        connectionManager.stopVpn()
                        break
                    }
                }
            }
        }
    }
}

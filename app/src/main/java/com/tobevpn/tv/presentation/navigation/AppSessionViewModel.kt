package com.tobevpn.tv.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectionManager: VpnConnectionManager,
) : ViewModel() {

    companion object {
        private const val DEVICE_LINK_POLL_INTERVAL_MS = 5L * 60L * 1000L
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
                        withContext(NonCancellable) {
                            authRepository.clearRemoteUnlinkedSession()
                        }
                        break
                    }
                }
            }
        }
    }
}

package com.tobevpn.tv.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.repository.AppFilterRepository
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.AppFilterState
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.util.LocaleManager
import com.tobevpn.tv.vpn.VpnConnectionManager
import com.tobevpn.tv.vpn.XRayCore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val vpnRepository: VpnRepository,
    private val connectionManager: VpnConnectionManager,
    appFilterRepository: AppFilterRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    val appFilterState: StateFlow<AppFilterState> = appFilterRepository.observeState()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppFilterState(AppFilterMode.OFF, emptySet()),
        )

    val xrayVersion: String = XRayCore.getVersion()

    private val _language = MutableStateFlow(LocaleManager.current())
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(tag: String) {
        LocaleManager.apply(tag)
        _language.value = tag
    }

    init {
        viewModelScope.launch {
            val isConnected = connectionManager.connectionState.value is ConnectionState.Connected
            authRepository.syncSubscription(overwriteUsage = !isConnected)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            vpnRepository.refreshServers()
        }
    }
}

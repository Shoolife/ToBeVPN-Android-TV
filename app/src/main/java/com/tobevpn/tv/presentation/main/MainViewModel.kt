package com.tobevpn.tv.presentation.main

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.ServerQualityRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.domain.model.UsageInfo
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionManager: VpnConnectionManager,
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PrefsDataStore,
    private val serverQualityRepository: ServerQualityRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val usageInfo: StateFlow<UsageInfo> = connectionManager.usageInfo

    private val _serverPing = MutableStateFlow<Long>(0)

    val currentServer: StateFlow<Server?> = combine(
        prefsDataStore.selectedServerId,
        prefsDataStore.automaticServerSelection,
        vpnRepository.observeServers(),
        _serverPing,
    ) { selectedId, automatic, servers, ping ->
        val availableServers = servers.filter { it.isAvailable }
        val selected = selectedId?.let { id -> availableServers.find { it.id == id } }
        val server = selected ?: availableServers.firstOrNull().takeIf { automatic }
        server?.copy(ping = if (ping >= 0) ping else server.ping)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val automaticServerSelection: StateFlow<Boolean> = prefsDataStore.automaticServerSelection
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    val subscriptionUsageBlocked: StateFlow<Boolean> = authRepository.observeSubscriptionUsageBlocked()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val updateRequired: StateFlow<Boolean> = authRepository.observeUpdateRequired()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var initialized = false
    private var lastSyncTime = 0L

    init {
        viewModelScope.launch {
            authRepository.getOrCreateDeviceId()
            authRepository.syncSubscription()
            // Force a block-state check on startup so a banned device can't
            // connect before the first throttled sync lands.
            runCatching { authRepository.pingHwidOnly() }
            val servers = vpnRepository.refreshServers().getOrNull().orEmpty()
            ensureAutomaticServerSelected(servers, forceSelection = true)
            lastSyncTime = System.currentTimeMillis()
            initialized = true
        }
        // Periodic block re-check (every 30s) so a ban applied mid-session is
        // picked up without requiring the user to background the app.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                runCatching { authRepository.pingHwidOnly() }
            }
        }
        viewModelScope.launch {
            var lastServer: Server? = null
            currentServer.collect { server ->
                if (server != null) {
                    val previous = lastServer
                    val vpnConfigChanged = previous == null || !server.hasSameVpnConfig(previous)
                    lastServer = server
                    if (!vpnConfigChanged) return@collect
                    prefsDataStore.setSelectedServerId(server.id)
                    _serverPing.value = serverQualityRepository.measurePing(server, force = true)
                    if (previous != null) {
                        val state = connectionState.value
                        val managedServer = connectionManager.currentServer.value
                        if ((state is ConnectionState.Connected || state is ConnectionState.Connecting) &&
                            managedServer?.hasSameVpnConfig(server) != true
                        ) {
                            connectionManager.switchServer(server)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                // 60s when idle is enough to keep the ping label fresh without the
                // app firing two TCP probes per minute on a TV that's just sitting on
                // the home screen all day.
                delay(60_000)
                if (connectionState.value is ConnectionState.Connected) continue
                val server = currentServer.value ?: continue
                val ping = serverQualityRepository.measurePing(server, force = true)
                _serverPing.value = ping
                if (ping < 0L && automaticServerSelection.value) {
                    selectAutomaticServer(excludeServerId = server.id)
                }
            }
        }
    }

    private suspend fun ensureAutomaticServerSelected(
        servers: List<Server>,
        forceSelection: Boolean = false,
    ) {
        if (!prefsDataStore.isAutomaticServerSelection()) return
        val selectedId = prefsDataStore.getSelectedServerId()
        if (!forceSelection && selectedId != null && servers.any { it.id == selectedId && it.isAvailable }) {
            return
        }
        val best = serverQualityRepository.selectBestServer(servers) ?: return
        prefsDataStore.setAutomaticSelectedServerId(best.id)
    }

    private suspend fun selectAutomaticServer(excludeServerId: String? = null): Server? {
        if (!prefsDataStore.isAutomaticServerSelection()) return null
        val best = serverQualityRepository.selectBestServer(
            servers = vpnRepository.getServers(),
            excludeServerId = excludeServerId,
            forceProbe = true,
        ) ?: return null
        prefsDataStore.setAutomaticSelectedServerId(best.id)
        return best
    }

    fun toggleConnection() {
        viewModelScope.launch {
            when (connectionState.value) {
                is ConnectionState.Disconnected, is ConnectionState.Error -> {
                    // Refuse to start a tunnel for a blocked subscription.
                    // The UI shows the block dialog reactively off the flag.
                    if (subscriptionUsageBlocked.value) return@launch
                    val selected = currentServer.value ?: return@launch
                    val automatic = prefsDataStore.isAutomaticServerSelection()
                    if (!automatic && (!selected.isAvailable || selected.ping < 0)) return@launch
                    authRepository.syncSubscription()
                    val availableServers = vpnRepository.refreshServers(forceRefresh = true)
                        .getOrNull()
                        .orEmpty()
                        .filter { it.isAvailable }
                    val server = if (automatic) {
                        serverQualityRepository.selectBestServer(availableServers, forceProbe = true)
                    } else {
                        availableServers.firstOrNull { it.id == selected.id }
                            ?: availableServers.firstOrNull { it.name == selected.name }
                    } ?: return@launch
                    if (automatic) {
                        prefsDataStore.setAutomaticSelectedServerId(server.id)
                    }
                    if (!automatic &&
                        serverQualityRepository.measurePing(server, force = true) < 0L
                    ) {
                        return@launch
                    }
                    connectionManager.startVpn(server)
                }
                is ConnectionState.Connected, is ConnectionState.Connecting -> {
                    connectionManager.stopVpn()
                }
            }
        }
    }

    /** Re-sync subscription & servers when app returns to foreground (throttled to 5s). */
    fun onResume() {
        if (!initialized) return
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 5_000) return
        lastSyncTime = now
        viewModelScope.launch {
            val isConnected = connectionState.value is ConnectionState.Connected
            authRepository.syncSubscription(overwriteUsage = !isConnected)
            runCatching { authRepository.pingHwidOnly() }
            val servers = vpnRepository.refreshServers().getOrNull().orEmpty()
            ensureAutomaticServerSelected(servers)
        }
    }

    fun getVpnPermissionIntent(activity: Activity): Intent? {
        return VpnService.prepare(activity)
    }
}

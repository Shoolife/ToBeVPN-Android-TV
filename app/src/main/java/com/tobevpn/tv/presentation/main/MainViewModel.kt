package com.tobevpn.tv.presentation.main

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.domain.model.UsageInfo
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionManager: VpnConnectionManager,
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PrefsDataStore,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val usageInfo: StateFlow<UsageInfo> = connectionManager.usageInfo

    private val _serverPing = MutableStateFlow<Long>(-1)

    val currentServer: StateFlow<Server?> = combine(
        prefsDataStore.selectedServerId,
        vpnRepository.observeServers(),
        _serverPing,
    ) { selectedId, servers, ping ->
        val server = if (selectedId != null) {
            servers.find { it.id == selectedId }
        } else {
            servers.firstOrNull()
        }
        server?.copy(ping = if (ping >= 0) ping else server.ping)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            vpnRepository.refreshServers()
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
            var lastServerId: String? = null
            currentServer.collect { server ->
                if (server != null && server.id != lastServerId) {
                    val previousId = lastServerId
                    lastServerId = server.id
                    _serverPing.value = measureTcpPing(server.address, server.port)
                    if (previousId != null) {
                        val state = connectionState.value
                        if (state is ConnectionState.Connected || state is ConnectionState.Connecting) {
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
                _serverPing.value = measureTcpPing(server.address, server.port)
            }
        }
    }

    private suspend fun measureTcpPing(host: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 3000)
                }
                System.currentTimeMillis() - start
            } catch (_: Exception) {
                -1L
            }
        }
    }

    fun toggleConnection() {
        viewModelScope.launch {
            when (connectionState.value) {
                is ConnectionState.Disconnected, is ConnectionState.Error -> {
                    // Refuse to start a tunnel for a blocked subscription.
                    // The UI shows the block dialog reactively off the flag.
                    if (subscriptionUsageBlocked.value) return@launch
                    val server = currentServer.value ?: return@launch
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
            vpnRepository.refreshServers()
        }
    }

    fun getVpnPermissionIntent(activity: Activity): Intent? {
        return VpnService.prepare(activity)
    }
}

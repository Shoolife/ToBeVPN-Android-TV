package com.tobevpn.tv.presentation.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.ServerSelectionPreferences
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.ServerQualityRepository
import com.tobevpn.tv.data.repository.ServerProfileProbeRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

data class ServerProbeProgress(
    val completed: Int,
    val total: Int,
)

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PrefsDataStore,
    private val serverQualityRepository: ServerQualityRepository,
    private val serverProfileProbeRepository: ServerProfileProbeRepository,
) : ViewModel() {

    private val _pings = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _profilePings = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _profilePingsEnabled = MutableStateFlow(false)
    private val _profilePingsMeasured = MutableStateFlow(false)
    val profilePingsMeasured: StateFlow<Boolean> = _profilePingsMeasured.asStateFlow()
    private val _profileProbeProgress = MutableStateFlow<ServerProbeProgress?>(null)
    val profileProbeProgress: StateFlow<ServerProbeProgress?> =
        _profileProbeProgress.asStateFlow()
    private val refreshMutex = Mutex()
    private val screenActive = MutableStateFlow(false)

    fun setScreenActive(active: Boolean) {
        screenActive.value = active
    }

    val servers: StateFlow<List<Server>> = combine(
        vpnRepository.observeServers(),
        _pings,
        _profilePings,
        _profilePingsEnabled,
    ) { serverList, tcpPings, profilePings, useProfilePings ->
            val pingMap = if (useProfilePings) profilePings else tcpPings
            serverList.map { server ->
                // ping == 0 means "not measured yet" (UI shows nothing);
                // a measured value of -1 means "unreachable" (UI shows Unavailable),
                // matching the phone client.
                pingMap[server.id]?.let { server.copy(ping = it) } ?: server.copy(ping = 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverSelection: StateFlow<ServerSelectionPreferences> = prefsDataStore.serverSelection
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ServerSelectionPreferences(
                selectedId = null,
                selectedKey = null,
                automatic = true,
            ),
        )

    val isAdminProfile: StateFlow<Boolean> = authRepository.observeAuthState()
        .map { state -> (state as? AuthState.Authenticated)?.isAdminProfile == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshServers(force = false)
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (!screenActive.value) continue
                if (_profilePingsEnabled.value) continue
                val serverList = servers.value
                if (serverList.isNotEmpty()) {
                    measurePings(serverList)
                }
            }
        }
    }

    fun refreshServers(force: Boolean = true) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                _isLoading.value = true
                _error.value = null
                authRepository.syncSubscription()
                val result = vpnRepository.refreshServers(forceRefresh = force)
                result.onFailure { _error.value = it.message }
                result.onSuccess { refreshed ->
                    if (force) {
                        measureProfiles(refreshed, force = true)
                        if (serverSelection.value.automatic) {
                            persistBestVerifiedServer(refreshed)
                        }
                    } else {
                        updatePings(refreshed)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _error.value = error.message
            } finally {
                _isLoading.value = false
                refreshMutex.unlock()
            }
        }
    }

    private fun measurePings(serverList: List<Server>) {
        viewModelScope.launch {
            updatePings(serverList)
        }
    }

    private suspend fun updatePings(serverList: List<Server>) {
        _pings.value = serverQualityRepository.measurePings(serverList, force = true)
    }

    suspend fun selectAutomaticServer(): Boolean {
        val current = servers.value
        if (current.isEmpty()) return false
        val complete = _profilePingsMeasured.value &&
            current.all { (_profilePings.value[it.id] ?: 0L) != 0L }
        if (!complete) measureProfiles(current, force = true)
        return persistBestVerifiedServer(current)
    }

    private suspend fun persistBestVerifiedServer(servers: List<Server>): Boolean {
        val best = serverQualityRepository.selectBestVerifiedServer(
            servers = servers,
            verifiedDelays = _profilePings.value,
        ) ?: return false
        prefsDataStore.setAutomaticSelectedServer(
            id = stableServerId(best),
            key = serverSelectionKey(best),
        )
        return true
    }

    private suspend fun measureProfiles(
        servers: List<Server>,
        force: Boolean,
    ): Map<String, Long> {
        val candidates = servers.filter(Server::isAvailable).distinctBy(Server::id)
        _profilePingsEnabled.value = true
        _profilePingsMeasured.value = false
        _profilePings.value = candidates.associate { it.id to 0L }
        _profileProbeProgress.value = ServerProbeProgress(0, candidates.size)
        return try {
            serverProfileProbeRepository.measureProfileDelays(
                servers = candidates,
                force = force,
                onResult = { id, delayMs, completed, total ->
                    _profilePings.update { current -> current + (id to delayMs) }
                    _profileProbeProgress.update { previous ->
                        ServerProbeProgress(
                            completed = maxOf(previous?.completed ?: 0, completed),
                            total = total,
                        )
                    }
                },
            ).also { _profilePings.value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _profilePings.update { current ->
                current.mapValues { (_, delay) -> delay.takeIf { it != 0L } ?: -1L }
            }
            throw error
        } finally {
            _profilePingsMeasured.value = true
            val finished = _profileProbeProgress.value
            viewModelScope.launch {
                delay(PROBE_PROGRESS_COMPLETION_HOLD_MS)
                if (_profileProbeProgress.value == finished) {
                    _profileProbeProgress.value = null
                }
            }
        }
    }

    suspend fun selectServer(server: Server): Boolean {
        // Focus and server metadata can update between key-down and this call.
        // Never persist an offline or failed-probe entry.
        if (!server.isSelectable) return false
        prefsDataStore.setManualSelectedServer(
            id = stableServerId(server),
            key = serverSelectionKey(server),
        )
        return true
    }

    private companion object {
        const val PROBE_PROGRESS_COMPLETION_HOLD_MS = 800L
    }
}

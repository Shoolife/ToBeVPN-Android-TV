package com.tobevpn.tv.presentation.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.ServerSelectionPreferences
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.ServerProfileProbeRepository
import com.tobevpn.tv.data.repository.ServerQualityRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

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
    // Whether the refresh holding refreshMutex runs the full check, and
    // whether a Refresh press arrived while a plain load was running.
    private var activeRefreshForced = false
    private var pendingForcedRefresh = false
    // Quiet check of profiles the last full check has no result for.
    private var missingProfilesJob: Job? = null
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
            serverList.map { server ->
                // ping == 0 means "not measured yet" (UI shows nothing);
                // a measured value of -1 means "unreachable" (UI shows Unavailable),
                // matching the phone client. Profile results are keyed by the
                // complete profile, TCP pings by endpoint.
                val ping = if (useProfilePings) profilePings[server.probeKey] else tcpPings[server.id]
                ping?.let { server.copy(ping = it) } ?: server.copy(ping = 0)
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
        // The subscription can hand out another host/SNI for the same server
        // on any refresh, also while or right after a check runs: such a row
        // has no result for its profile and showed "Unavailable". Check just
        // those, without a second progress card.
        viewModelScope.launch {
            vpnRepository.observeServers().collect { checkMissingProfiles(it) }
        }
        refreshServers(force = false)
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (!screenActive.value) continue
                if (_profilePingsEnabled.value) continue
                val serverList = servers.value
                if (serverList.isNotEmpty()) {
                    // Awaited, not launched: with dead servers a batch can
                    // outlast the 5 s period, and overlapping batches piled
                    // up sockets and threads.
                    updatePings(serverList)
                }
            }
        }
    }

    fun refreshServers(force: Boolean = true) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) {
                // A Refresh press while the open-screen load is still running
                // used to be dropped silently: run the check right after it.
                // A press during the check itself needs no second check.
                if (force && !activeRefreshForced) pendingForcedRefresh = true
                return@launch
            }
            activeRefreshForced = force
            try {
                _isLoading.value = true
                _error.value = null
                // The sync refreshes plan data; the server list below has its
                // own request, so a slow panel must not hold the check for
                // long. It is cancelled rather than left running: it also
                // rewrites the server list, which must not change mid-check
                // (rows would lose their results). Plan data catches up on the
                // next sync.
                withTimeoutOrNull(SUBSCRIPTION_SYNC_WAIT_MS) {
                    authRepository.syncSubscription()
                }
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
                val runPendingCheck = pendingForcedRefresh
                pendingForcedRefresh = false
                activeRefreshForced = false
                refreshMutex.unlock()
                if (runPendingCheck) {
                    refreshServers(force = true)
                } else {
                    checkMissingProfilesNow()
                }
            }
        }
    }

    private suspend fun updatePings(serverList: List<Server>) {
        _pings.value = serverQualityRepository.measurePings(serverList, force = true)
    }

    suspend fun selectAutomaticServer(): Boolean {
        val current = servers.value
        if (current.isEmpty()) return false
        val complete = _profilePingsMeasured.value &&
            current
                .filter { it.isAvailable }
                .all { (_profilePings.value[it.probeKey] ?: 0L) != 0L }
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
        val candidates = servers.filter(Server::isAvailable).distinctBy(Server::probeKey)
        // A quiet check still running would write over the new results.
        missingProfilesJob?.cancel()
        _profilePingsEnabled.value = true
        _profilePingsMeasured.value = false
        _profilePings.value = candidates.associate { it.probeKey to 0L }
        _profileProbeProgress.value = ServerProbeProgress(0, candidates.size)
        return try {
            serverProfileProbeRepository.measureProfileDelays(
                servers = candidates,
                force = force,
                onResult = { profileKey, delayMs, completed, total ->
                    _profilePings.update { current -> current + (profileKey to delayMs) }
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

    /**
     * Server list changes while refreshMutex is held (a check, the open-screen
     * load) are skipped by the observer; this catches up once it is released.
     */
    private fun checkMissingProfilesNow() {
        viewModelScope.launch {
            checkMissingProfiles(vpnRepository.observeServers().first())
        }
    }

    private fun checkMissingProfiles(serverList: List<Server>) {
        if (!_profilePingsEnabled.value || !_profilePingsMeasured.value) return
        if (refreshMutex.isLocked || missingProfilesJob?.isActive == true) return
        val known = _profilePings.value
        val missing = serverList
            .filter { it.isAvailable && it.probeKey !in known }
            .distinctBy(Server::probeKey)
        if (missing.isEmpty()) return
        missingProfilesJob = viewModelScope.launch {
            _profilePings.update { current -> current + missing.associate { it.probeKey to 0L } }
            try {
                serverProfileProbeRepository.measureProfileDelays(
                    servers = missing,
                    force = false,
                    onResult = { profileKey, delayMs, _, _ ->
                        _profilePings.update { current -> current + (profileKey to delayMs) }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _profilePings.update { current ->
                    current + missing
                        .filter { (current[it.probeKey] ?: 0L) == 0L }
                        .associate { it.probeKey to -1L }
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
        const val SUBSCRIPTION_SYNC_WAIT_MS = 4_000L
    }
}

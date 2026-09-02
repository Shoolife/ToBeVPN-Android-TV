package com.tobevpn.tv.presentation.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.ServerSelectionPreferences
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.ServerQualityRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PrefsDataStore,
    private val serverQualityRepository: ServerQualityRepository,
) : ViewModel() {

    private val _pings = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val refreshMutex = Mutex()
    private val screenActive = MutableStateFlow(false)

    fun setScreenActive(active: Boolean) {
        screenActive.value = active
    }

    val servers: StateFlow<List<Server>> = vpnRepository.observeServers()
        .combine(_pings) { serverList, pingMap ->
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
        refreshServers()
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (!screenActive.value) continue
                val serverList = servers.value
                if (serverList.isNotEmpty()) {
                    measurePings(serverList)
                }
            }
        }
    }

    fun refreshServers() {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                _isLoading.value = true
                _error.value = null
                val result = coroutineScope {
                    val serversDeferred = async { vpnRepository.refreshServers() }
                    authRepository.syncSubscription()
                    serversDeferred.await()
                }
                result.onFailure { _error.value = it.message }
                result.onSuccess { updatePings(it) }
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
        val best = serverQualityRepository.selectBestServer(servers.value, forceProbe = true)
            ?: return false
        prefsDataStore.setAutomaticSelectedServer(
            id = stableServerId(best),
            key = serverSelectionKey(best),
        )
        return true
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
}

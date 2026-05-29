package com.tobevpn.tv.presentation.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.VpnRepository
import com.tobevpn.tv.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PrefsDataStore,
) : ViewModel() {

    private val _pings = MutableStateFlow<Map<String, Long>>(emptyMap())

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshServers()
        viewModelScope.launch {
            while (true) {
                delay(5000)
                val serverList = servers.value
                if (serverList.isNotEmpty()) {
                    measurePings(serverList)
                }
            }
        }
    }

    fun refreshServers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            authRepository.syncSubscription()
            val result = vpnRepository.refreshServers()
            result.onFailure { _error.value = it.message }
            result.onSuccess { measurePings(it) }
            _isLoading.value = false
        }
    }

    private fun measurePings(serverList: List<Server>) {
        viewModelScope.launch {
            val results = serverList.map { server ->
                async(Dispatchers.IO) {
                    server.id to measureTcpPing(server.address, server.port)
                }
            }.awaitAll()
            // Keep failed (-1) results too so the list can show "Unavailable",
            // exactly like the phone client.
            _pings.value = results.toMap()
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

    fun selectServer(server: Server) {
        viewModelScope.launch {
            prefsDataStore.setSelectedServerId(server.id)
        }
    }
}

package com.tobevpn.tv.vpn

import android.content.Context
import android.content.Intent
import com.tobevpn.tv.R
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.dao.TrafficLogDao
import com.tobevpn.tv.data.local.entity.TrafficLogEntity
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.UsageRepository
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.domain.model.UsageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnConnectionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val usageRepository: UsageRepository,
    private val sessionDao: SessionDao,
    private val trafficLogDao: TrafficLogDao,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentServer = MutableStateFlow<Server?>(null)
    val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()

    val usageInfo: StateFlow<UsageInfo> = usageRepository.observeUsage()
        .stateIn(scope, SharingStarted.Eagerly, UsageInfo())

    private var usageTrackingJob: Job? = null
    private var connectionStartTime = 0L
    private var sessionStartBytes = 0L
    private var sessionStartSeconds = 0L
    private var connectionGeneration = 0

    init {
        scope.launch { usageRepository.ensureInitialized() }
    }

    private suspend fun isPaidUser(): Boolean {
        val session = sessionDao.getSession() ?: return false
        return session.authState == "AUTHENTICATED" &&
            (session.userPlan == "PAID" || session.userPlan == "ADMIN")
    }

    fun startVpn(server: Server) {
        scope.launch {
            val gen: Int
            mutex.withLock {
                val current = _connectionState.value
                if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return@launch

                if (!isPaidUser() && usageRepository.isExhausted()) {
                    _connectionState.value = ConnectionState.Error(context.getString(R.string.vpn_error_limit_exhausted))
                    return@launch
                }

                connectionGeneration++
                gen = connectionGeneration
                _currentServer.value = server
                _connectionState.value = ConnectionState.Connecting
            }

            // Fire-and-forget: hits the panel's public sub URL with HWID headers
            // so Remnawave registers/refreshes the HWID device on every connect.
            launch { runCatching { authRepository.syncSubscription(overwriteUsage = false) } }

            val intent = Intent(context, ToBeVpnService::class.java).apply {
                action = ToBeVpnService.ACTION_START
                putExtra(ToBeVpnService.EXTRA_SERVER_CONFIG, VpnConfig.buildConfigJson(server))
                putExtra(ToBeVpnService.EXTRA_GENERATION, gen)
            }
            context.startForegroundService(intent)
        }
    }

    fun stopVpn() {
        scope.launch { performStop() }
    }

    fun switchServer(server: Server) {
        scope.launch {
            val wasConnected: Boolean
            mutex.withLock {
                val current = _connectionState.value
                wasConnected = current is ConnectionState.Connected || current is ConnectionState.Connecting
            }
            if (wasConnected) {
                performStop()
                delay(300)
            }
            startVpn(server)
        }
    }

    private suspend fun performStop(errorMessage: String? = null) {
        mutex.withLock {
            val current = _connectionState.value
            if (current is ConnectionState.Disconnected) return

            connectionGeneration++
            stopUsageTracking()
            _connectionState.value = if (errorMessage != null) {
                ConnectionState.Error(errorMessage)
            } else {
                ConnectionState.Disconnected
            }
            saveSessionLog()
        }

        val intent = Intent(context, ToBeVpnService::class.java).apply {
            action = ToBeVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateState(state: ConnectionState, generation: Int = -1) {
        scope.launch {
            mutex.withLock {
                if (generation != -1 && generation != connectionGeneration) return@launch

                val prev = _connectionState.value

                when (state) {
                    is ConnectionState.Connected -> {
                        if (prev !is ConnectionState.Connecting) return@launch
                        _connectionState.value = state
                        connectionStartTime = System.currentTimeMillis()
                        val usage = usageRepository.getUsage()
                        sessionStartBytes = usage.bytesUsed
                        sessionStartSeconds = usage.timeUsedSeconds
                        usageRepository.setLastConnected(connectionStartTime)
                        startUsageTracking()
                    }
                    is ConnectionState.Disconnected -> {
                        if (prev is ConnectionState.Disconnected || prev is ConnectionState.Error) return@launch
                        connectionGeneration++
                        _connectionState.value = state
                        stopUsageTracking()
                        saveSessionLog()
                    }
                    is ConnectionState.Error -> {
                        if (prev is ConnectionState.Disconnected) return@launch
                        connectionGeneration++
                        _connectionState.value = state
                        stopUsageTracking()
                        saveSessionLog()
                    }
                    is ConnectionState.Connecting -> {
                        if (prev is ConnectionState.Disconnected || prev is ConnectionState.Connecting) {
                            _connectionState.value = state
                        }
                    }
                }
            }
        }
    }

    private fun startUsageTracking() {
        usageTrackingJob?.cancel()
        val gen = connectionGeneration
        usageTrackingJob = scope.launch {
            val paid = isPaidUser()
            // Heartbeat counter — fires registerCurrentDevice every HEARTBEAT_TICKS
            // seconds while VPN is connected. /api/device/register is the only
            // client-callable endpoint that bumps `last_seen_at` server-side, so
            // without this the TV's "Last active" in the device list freezes at
            // the moment the app was last foregrounded — wrong for a TV that
            // typically streams in the background for hours.
            var heartbeatCounter = 0
            while (gen == connectionGeneration) {
                delay(1000)
                if (_connectionState.value !is ConnectionState.Connected) break
                if (gen != connectionGeneration) break

                val usage = usageRepository.getUsage()
                val newTime = usage.timeUsedSeconds + 1

                val upBytes = XRayCore.queryStats("proxy", "uplink")
                val downBytes = XRayCore.queryStats("proxy", "downlink")
                val newBytes = usage.bytesUsed + upBytes + downBytes

                usageRepository.updateUsage(newBytes, newTime)

                heartbeatCounter++
                if (heartbeatCounter >= HEARTBEAT_TICKS) {
                    heartbeatCounter = 0
                    if (sessionDao.getSession()?.telegramId != null) {
                        runCatching { authRepository.registerCurrentDevice() }
                    }
                }

                if (!paid) {
                    val updated = usageRepository.getUsage()
                    if (updated.isExhausted) {
                        performStop(context.getString(R.string.vpn_error_limit_exhausted))
                        break
                    }
                }
            }
        }
    }

    private suspend fun saveSessionLog() {
        val usage = usageRepository.getUsage()
        val sessionBytes = usage.bytesUsed - sessionStartBytes
        val sessionTime = usage.timeUsedSeconds - sessionStartSeconds
        if (sessionBytes <= 0 && sessionTime <= 0) return
        val authenticated = sessionDao.getSession()?.authState == "AUTHENTICATED"
        trafficLogDao.insert(
            TrafficLogEntity(
                bytesUsed = sessionBytes,
                timeUsedSeconds = sessionTime,
                serverId = _currentServer.value?.id ?: "",
                timestamp = connectionStartTime / 1000,
                isAuthenticated = authenticated,
            )
        )
    }

    private fun stopUsageTracking() {
        usageTrackingJob?.cancel()
        usageTrackingJob = null
    }

    companion object {
        private const val HEARTBEAT_TICKS = 60
    }
}

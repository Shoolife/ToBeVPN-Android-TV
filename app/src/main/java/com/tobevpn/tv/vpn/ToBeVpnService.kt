package com.tobevpn.tv.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.tobevpn.tv.MainActivity
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.AppFilterRepository
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.AppFilterState
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.util.SafeDiagnostics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class ToBeVpnService : VpnService(), CoreCallbackHandler {

    @Inject
    lateinit var connectionManager: VpnConnectionManager

    @Inject
    lateinit var appFilterRepository: AppFilterRepository

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val coreLifecycleLock = Any()
    private val networkJobLock = Any()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val physicalNetworkSelector = PhysicalNetworkSelector<Network>()
    private var networkHandoverJob: Job? = null
    private var networkBaselineJob: Job? = null
    private var underlyingNetworkTimeoutJob: Job? = null

    @Volatile
    private var networkBaselineReady = false
    private var activeConfigJson: String? = null

    @Volatile
    private var cleanedUp = false

    @Volatile
    private var waitingForNetworkResume = false

    @Volatile
    private var activeConnectionGeneration = -1

    override fun onCreate() {
        super.onCreate()
        SafeDiagnostics.info(TAG, "VPN service created")
        activeInstance.set(this)
        XRayCore.init(this)
        XRayCore.createController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_SERVER_CONFIG)
                val generation = intent.getIntExtra(EXTRA_GENERATION, -1)
                if (config == null || !connectionManager.mayServiceStart(generation)) {
                    // startForegroundService() needs a matching foreground call
                    // even if this request became stale before it was delivered.
                    val hasActiveSession = vpnInterface != null || XRayCore.isRunning || waitingForNetworkResume
                    if (!hasActiveSession) {
                        startForeground(
                            NOTIFICATION_ID,
                            createNotification(getString(R.string.state_disconnected)),
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                    return START_NOT_STICKY
                }
                synchronized(coreLifecycleLock) {
                    cleanedUp = false
                    waitingForNetworkResume = false
                    activeConnectionGeneration = generation
                }
                SafeDiagnostics.info(TAG, "VPN start accepted: generation=$generation")
                startVpn(config, generation)
            }

            ACTION_STOP -> {
                val forceStop = intent.getBooleanExtra(EXTRA_FORCE_STOP, false)
                val stopBeforeGeneration = intent.getIntExtra(
                    EXTRA_STOP_BEFORE_GENERATION,
                    Int.MAX_VALUE,
                )
                if (forceStop || activeConnectionGeneration < stopBeforeGeneration) {
                    cleanupVpn()
                    stopSelf(startId)
                }
            }

            ACTION_DISCONNECT -> connectionManager.stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(configJson: String, generation: Int) {
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.state_connecting)))
        serviceScope.launch {
            try {
                val fd = setupTunInterface() ?: run {
                    connectionManager.updateState(
                        ConnectionState.Error(getString(R.string.vpn_error_tun_failed)),
                        generation,
                    )
                    cleanupVpn(expectedGeneration = generation)
                    return@launch
                }

                val loopGeneration = synchronized(coreLifecycleLock) {
                    if (cleanedUp || generation != activeConnectionGeneration) {
                        null
                    } else {
                        vpnInterface = fd
                        XRayCore.startLoop(configJson, fd.fd).also {
                            activeConfigJson = configJson
                        }
                    }
                } ?: run {
                    fd.close()
                    return@launch
                }

                val becameStale = synchronized(coreLifecycleLock) {
                    if (cleanedUp || generation != activeConnectionGeneration) {
                        XRayCore.stopLoop(loopGeneration)
                        if (vpnInterface === fd) vpnInterface = null
                        true
                    } else {
                        false
                    }
                }
                if (becameStale) {
                    fd.close()
                    return@launch
                }

                registerNetworkCallback()
                // Xray being alive is only a core-ready signal. The manager
                // keeps public state Connecting until an end-to-end probe works.
                connectionManager.handleTunnelCoreStarted(generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SafeDiagnostics.warn(
                    TAG,
                    "VPN start failed: ${SafeDiagnostics.failureCategory(error)}",
                )
                connectionManager.updateState(
                    ConnectionState.Error(getString(R.string.error_generic)),
                    generation,
                )
                cleanupVpn(expectedGeneration = generation)
            }
        }
    }

    private suspend fun setupTunInterface(): ParcelFileDescriptor? {
        val filter = appFilterRepository.getSnapshot()
        val builder = Builder()
            .setSession("ToBeVPN")
            .setMtu(1500)
            .addAddress("10.10.14.1", 30)
            .addAddress("fd00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addDnsServer("2606:4700:4700::1111")
            .addDnsServer("2001:4860:4860::8888")
        applyAppFilter(builder, filter)
        return builder.establish()
    }

    private fun applyAppFilter(builder: Builder, state: AppFilterState) {
        when (state.mode) {
            AppFilterMode.OFF -> tryDisallow(builder, packageName)
            AppFilterMode.WHITELIST -> state.selectedPackages.forEach { tryAllow(builder, it) }
            AppFilterMode.BLACKLIST -> {
                tryDisallow(builder, packageName)
                state.selectedPackages.forEach { tryDisallow(builder, it) }
            }
        }
    }

    private fun tryAllow(builder: Builder, packageName: String) {
        try {
            builder.addAllowedApplication(packageName)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        }
    }

    private fun tryDisallow(builder: Builder, packageName: String) {
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        }
    }

    private fun cleanupVpn(expectedGeneration: Int? = null) {
        val loopGenerationToStop: Int
        val tunToClose: ParcelFileDescriptor?
        synchronized(coreLifecycleLock) {
            if (expectedGeneration != null && expectedGeneration != activeConnectionGeneration) return
            if (cleanedUp) return
            cleanedUp = true
            waitingForNetworkResume = false
            activeConnectionGeneration = -1
            loopGenerationToStop = XRayCore.currentLoopGeneration
            tunToClose = vpnInterface
            vpnInterface = null
            activeConfigJson = null
        }
        unregisterNetworkCallback()
        synchronized(coreLifecycleLock) {
            runCatching { tunToClose?.close() }
            XRayCore.stopLoop(loopGenerationToStop)
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        stopSelf()
        SafeDiagnostics.info(TAG, "VPN service cleanup completed")
    }

    /** Keep the foreground service while removing a dead tunnel. */
    private fun pauseTunnelForNetworkResume(expectedGeneration: Int): Boolean {
        val loopGenerationToStop: Int
        val tunToClose: ParcelFileDescriptor?
        synchronized(coreLifecycleLock) {
            if (cleanedUp || waitingForNetworkResume || expectedGeneration != activeConnectionGeneration) {
                return false
            }
            waitingForNetworkResume = true
            loopGenerationToStop = XRayCore.currentLoopGeneration
            tunToClose = vpnInterface
            vpnInterface = null
            activeConfigJson = null
        }
        unregisterNetworkCallback()
        synchronized(coreLifecycleLock) {
            runCatching { tunToClose?.close() }
            XRayCore.stopLoop(loopGenerationToStop)
            if (cleanedUp || !waitingForNetworkResume) return false
            updateNotification(getString(R.string.vpn_waiting_for_network_resume))
        }
        return true
    }

    /** Reload only Xray while retaining the established Android TUN. */
    private fun reloadCore(
        expectedGeneration: Int,
        configJson: String? = null,
        reason: String,
        showConnectedNotification: Boolean,
    ): Boolean = synchronized(coreLifecycleLock) {
        if (cleanedUp || waitingForNetworkResume || expectedGeneration != activeConnectionGeneration) {
            return@synchronized false
        }
        val tun = vpnInterface ?: return@synchronized false
        val targetConfig = configJson ?: activeConfigJson ?: return@synchronized false
        return@synchronized try {
            val newLoopGeneration = XRayCore.startLoop(targetConfig, tun.fd)
            if (cleanedUp || expectedGeneration != activeConnectionGeneration) {
                XRayCore.stopLoop(newLoopGeneration)
                false
            } else {
                activeConfigJson = targetConfig
                if (showConnectedNotification) {
                    updateNotification(getString(R.string.state_connected))
                }
                SafeDiagnostics.info(TAG, "XRay core reloaded: reason=$reason generation=$expectedGeneration")
                true
            }
        } catch (error: Exception) {
            SafeDiagnostics.warn(
                TAG,
                "XRay core reload failed: reason=$reason failure=${SafeDiagnostics.failureCategory(error)}",
            )
            false
        }
    }

    private fun markTunnelValidated(expectedGeneration: Int): Boolean =
        synchronized(coreLifecycleLock) {
            if (cleanedUp || expectedGeneration != activeConnectionGeneration || !XRayCore.isRunning) {
                return@synchronized false
            }
            updateNotification(getString(R.string.state_connected))
            true
        }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            // The builder includes NOT_RESTRICTED by default. Remove it so
            // restricted-but-usable physical networks are still observed.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cleanedUp) return
                updatePhysicalNetworkCandidate(cm, network, cm.getNetworkCapabilities(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (cleanedUp) return
                updatePhysicalNetworkCandidate(cm, network, networkCapabilities)
            }

            override fun onLost(network: Network) {
                if (cleanedUp) return
                val change = physicalNetworkSelector.onLost(network)
                if (physicalNetworkSelector.hasUsableNetwork()) cancelUnderlyingNetworkTimeout()
                else scheduleUnderlyingNetworkTimeout(cm)
                handlePhysicalNetworkSelectionChange(change)
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
            scheduleUnderlyingNetworkTimeout(cm)
            scheduleNetworkBaselineCompletion(cm)
        } catch (error: Exception) {
            networkCallback = null
            physicalNetworkSelector.reset()
            SafeDiagnostics.warn(
                TAG,
                "Physical network callback failed: ${SafeDiagnostics.failureCategory(error)}",
            )
        }
    }

    private fun updatePhysicalNetworkCandidate(
        cm: ConnectivityManager,
        network: Network,
        capabilities: NetworkCapabilities?,
    ) {
        // onAvailable can precede capabilities. Do not create a fake
        // zero-priority candidate; onCapabilitiesChanged will provide it.
        if (capabilities == null || !isPhysicalInternet(capabilities)) return
        val change = physicalNetworkSelector.update(
            network = network,
            validated = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ),
            priority = physicalNetworkPriority(capabilities),
        )
        if (physicalNetworkSelector.hasUsableNetwork()) cancelUnderlyingNetworkTimeout()
        else scheduleUnderlyingNetworkTimeout(cm)
        handlePhysicalNetworkSelectionChange(change)
    }

    private fun handlePhysicalNetworkSelectionChange(
        change: PhysicalNetworkSelector.SelectionChange<Network>,
    ) {
        when (change.type) {
            PhysicalNetworkSelector.ChangeType.UNCHANGED -> Unit
            PhysicalNetworkSelector.ChangeType.UNAVAILABLE -> cancelNetworkHandover()
            PhysicalNetworkSelector.ChangeType.INITIAL,
            PhysicalNetworkSelector.ChangeType.HANDOVER -> {
                val selected = change.current ?: return
                // Initial callbacks populate a baseline only. They must not be
                // interpreted as a Wi-Fi/Ethernet handover.
                if (!networkBaselineReady) return
                scheduleNetworkHandover(selected, activeConnectionGeneration)
            }
        }
    }

    private fun scheduleNetworkBaselineCompletion(cm: ConnectivityManager) {
        val previous: Job?
        val replacement: Job
        synchronized(networkJobLock) {
            previous = networkBaselineJob
            replacement = serviceScope.launch(start = CoroutineStart.LAZY) {
                delay(NETWORK_CALLBACK_BASELINE_MS)
                if (cleanedUp) return@launch
                networkBaselineReady = true
                if (physicalNetworkSelector.selectedOrNull() == null) {
                    scheduleUnderlyingNetworkTimeout(cm)
                } else {
                    cancelUnderlyingNetworkTimeout()
                }
            }
            networkBaselineJob = replacement
        }
        previous?.cancel()
        replacement.start()
    }

    private fun scheduleNetworkHandover(network: Network, generation: Int) {
        val previous: Job?
        val replacement: Job
        synchronized(networkJobLock) {
            previous = networkHandoverJob
            replacement = serviceScope.launch(start = CoroutineStart.LAZY) {
                delay(NETWORK_HANDOVER_DEBOUNCE_MS)
                if (!cleanedUp &&
                    generation == activeConnectionGeneration &&
                    physicalNetworkSelector.isSelected(network)
                ) {
                    connectionManager.handleUnderlyingNetworkHandover(generation)
                }
            }
            networkHandoverJob = replacement
        }
        previous?.cancel()
        replacement.start()
    }

    private fun cancelNetworkHandover() {
        synchronized(networkJobLock) {
            networkHandoverJob.also { networkHandoverJob = null }
        }?.cancel()
    }

    private fun cancelNetworkBaselineCompletion() {
        synchronized(networkJobLock) {
            networkBaselineJob.also { networkBaselineJob = null }
        }?.cancel()
    }

    private fun cancelUnderlyingNetworkTimeout() {
        synchronized(networkJobLock) {
            underlyingNetworkTimeoutJob.also { underlyingNetworkTimeoutJob = null }
        }?.cancel()
    }

    private fun scheduleUnderlyingNetworkTimeout(cm: ConnectivityManager) {
        if (cleanedUp || activeConnectionGeneration < 0) return
        val generation = activeConnectionGeneration
        val startedAtMs = SystemClock.elapsedRealtime()
        val initialTimeoutMs = UnderlyingNetworkPolicy.teardownTimeoutMs(
            underlyingNetworkAvailability(cm),
        )
        if (initialTimeoutMs == null) {
            cancelUnderlyingNetworkTimeout()
            return
        }
        synchronized(networkJobLock) {
            if (underlyingNetworkTimeoutJob?.isCompleted == false) return
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                while (!cleanedUp && generation == activeConnectionGeneration) {
                    val availability = underlyingNetworkAvailability(cm)
                    val timeoutMs = UnderlyingNetworkPolicy.teardownTimeoutMs(availability)
                        ?: return@launch
                    val deadline = NetworkAvailabilityDeadline(
                        startedAtMs = startedAtMs,
                        timeoutMs = timeoutMs,
                    )
                    val now = SystemClock.elapsedRealtime()
                    if (deadline.isExpired(now)) {
                        connectionManager.handleUnderlyingNetworkUnavailable(generation)
                        return@launch
                    }
                    delay(deadline.nextCheckDelayMs(now, UNDERLYING_NETWORK_RECHECK_MS))
                }
            }
            underlyingNetworkTimeoutJob = job
            job.start()
        }
    }

    private fun isPhysicalInternet(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    @Suppress("DEPRECATION")
    private fun underlyingNetworkAvailability(cm: ConnectivityManager): UnderlyingNetworkAvailability {
        var hasPhysicalInternet = false
        cm.allNetworks.forEach { network ->
            val capabilities = cm.getNetworkCapabilities(network) ?: return@forEach
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) return@forEach
            hasPhysicalInternet = true
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return UnderlyingNetworkAvailability.VALIDATED
            }
        }
        return if (hasPhysicalInternet) {
            UnderlyingNetworkAvailability.UNVALIDATED
        } else {
            UnderlyingNetworkAvailability.UNAVAILABLE
        }
    }

    private fun physicalNetworkPriority(capabilities: NetworkCapabilities): Int = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 300
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 200
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 100
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 50
        else -> 0
    }

    private fun unregisterNetworkCallback() {
        cancelNetworkHandover()
        cancelNetworkBaselineCompletion()
        cancelUnderlyingNetworkTimeout()
        networkBaselineReady = false
        networkCallback?.let { callback ->
            try {
                getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        physicalNetworkSelector.reset()
    }

    private fun createNotification(status: String): Notification {
        createNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ToBeVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAction = Notification.Action.Builder(
            null,
            getString(R.string.vpn_notification_action_disconnect),
            disconnectIntent,
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onDestroy() {
        activeInstance.compareAndSet(this, null)
        val generationAtDestroy = activeConnectionGeneration
        val wasWaitingForNetwork = waitingForNetworkResume
        val hadActiveSession = !cleanedUp && (vpnInterface != null || XRayCore.isRunning)
        if (hadActiveSession) {
            cleanupVpn()
            connectionManager.handleServiceDestroyed(generationAtDestroy)
        } else if (wasWaitingForNetwork) {
            connectionManager.handleNetworkWaitServiceDestroyed()
        }
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        cleanupVpn()
        connectionManager.stopVpn()
    }

    override fun startup(): Long = 0

    override fun shutdown(): Long = 0

    override fun onEmitStatus(l: Long, s: String?): Long = 0

    companion object {
        private const val TAG = "ToBeVpnService"
        const val ACTION_START = "com.tobevpn.tv.START"
        const val ACTION_STOP = "com.tobevpn.tv.STOP"
        const val ACTION_DISCONNECT = "com.tobevpn.tv.DISCONNECT"
        const val EXTRA_SERVER_CONFIG = "server_config"
        const val EXTRA_GENERATION = "connection_generation"
        const val EXTRA_STOP_BEFORE_GENERATION = "stop_before_generation"
        const val EXTRA_FORCE_STOP = "force_stop"
        private const val CHANNEL_ID = "tobevpn_tv_channel"
        private const val NOTIFICATION_ID = 1
        private const val NETWORK_HANDOVER_DEBOUNCE_MS = 1_000L
        private const val NETWORK_CALLBACK_BASELINE_MS = 500L
        private const val UNDERLYING_NETWORK_RECHECK_MS = 5_000L
        private val activeInstance = AtomicReference<ToBeVpnService?>()

        fun reloadActiveCore(
            expectedGeneration: Int,
            configJson: String? = null,
            reason: String,
            showConnectedNotification: Boolean = true,
        ): Boolean = activeInstance.get()?.reloadCore(
            expectedGeneration = expectedGeneration,
            configJson = configJson,
            reason = reason,
            showConnectedNotification = showConnectedNotification,
        ) ?: false

        fun markActiveTunnelValidated(expectedGeneration: Int): Boolean =
            activeInstance.get()?.markTunnelValidated(expectedGeneration) ?: false

        fun pauseActiveTunnelForNetworkResume(expectedGeneration: Int): Boolean =
            activeInstance.get()?.pauseTunnelForNetworkResume(expectedGeneration) ?: false

        fun cleanupActiveInstance(expectedGeneration: Int? = null): Boolean {
            val service = activeInstance.get() ?: return false
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.cleanupVpn(expectedGeneration)
            } else {
                Handler(Looper.getMainLooper()).postAtFrontOfQueue {
                    service.cleanupVpn(expectedGeneration)
                }
            }
            return true
        }
    }
}

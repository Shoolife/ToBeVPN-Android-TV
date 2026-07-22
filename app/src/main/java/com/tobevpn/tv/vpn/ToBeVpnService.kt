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
import com.tobevpn.tv.MainActivity
import com.tobevpn.tv.R
import com.tobevpn.tv.data.repository.AppFilterRepository
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.AppFilterState
import com.tobevpn.tv.domain.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var cleanedUp = false
    @Volatile
    private var activeConnectionGeneration = -1

    override fun onCreate() {
        super.onCreate()
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
                    // startForegroundService() always requires a matching
                    // startForeground(), even if this request became stale
                    // while the user was pressing Stop. Briefly satisfy that
                    // contract and then close this start request.
                    val hasActiveSession = vpnInterface != null || XRayCore.isRunning
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
                cleanedUp = false
                activeConnectionGeneration = generation
                startVpn(config, generation)
            }
            ACTION_STOP -> {
                // From manager — state already handled, just clean up resources
                val forceStop = intent.getBooleanExtra(EXTRA_FORCE_STOP, false)
                val stopBeforeGeneration = intent.getIntExtra(
                    EXTRA_STOP_BEFORE_GENERATION,
                    Int.MAX_VALUE,
                )
                if (forceStop || activeConnectionGeneration < stopBeforeGeneration) {
                    cleanupVpn()
                    // cleanup can already have run through cleanupActiveInstance().
                    // Stop this later ACTION_STOP start request as well; otherwise
                    // Android keeps the VpnService registered after its TUN is gone.
                    stopSelf(startId)
                }
            }
            ACTION_DISCONNECT -> {
                cleanupVpn()
                // From notification button — route through manager for proper state handling.
                connectionManager.stopVpn()
            }
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

                // If disconnect was requested while TUN was being created, bail out
                if (cleanedUp || generation != activeConnectionGeneration) {
                    fd.close()
                    return@launch
                }

                vpnInterface = fd
                val loopGeneration = XRayCore.startLoop(configJson, fd.fd)
                if (cleanedUp || generation != activeConnectionGeneration) {
                    XRayCore.stopLoop(loopGeneration)
                    if (vpnInterface === fd) vpnInterface = null
                    fd.close()
                    return@launch
                }

                connectionManager.updateState(ConnectionState.Connected, generation)
                updateNotification(getString(R.string.state_connected))
                registerNetworkCallback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface a localized message — `e.message` is usually English
                // and ends up in the connection error UI on user devices.
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
            AppFilterMode.OFF -> {
                tryDisallow(builder, packageName)
            }
            AppFilterMode.WHITELIST -> {
                state.selectedPackages.forEach { tryAllow(builder, it) }
            }
            AppFilterMode.BLACKLIST -> {
                tryDisallow(builder, packageName)
                state.selectedPackages.forEach { tryDisallow(builder, it) }
            }
        }
    }

    private fun tryAllow(builder: Builder, pkg: String) {
        try {
            builder.addAllowedApplication(pkg)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        }
    }

    private fun tryDisallow(builder: Builder, pkg: String) {
        try {
            builder.addDisallowedApplication(pkg)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        }
    }

    /**
     * Cleans up VPN resources without touching connection state.
     * State transitions are handled exclusively by VpnConnectionManager.
     *
     * Close the application-owned TUN descriptor before native cleanup so
     * traffic cannot continue after the UI changes to disconnected. The
     * native core then releases its Android TUN registration in stopLoop(),
     * which removes the system VPN indicator. Do not establish a temporary
     * replacement TUN here: Android reports it as another network transition.
     *
     * A stale ACTION_STOP is filtered by generation before this method is
     * called, so cleanup can force the started service down immediately.
     */
    private fun cleanupVpn(expectedGeneration: Int? = null) {
        if (expectedGeneration != null && expectedGeneration != activeConnectionGeneration) return
        if (cleanedUp) return
        cleanedUp = true
        activeConnectionGeneration = -1
        val loopGenerationToStop = XRayCore.currentLoopGeneration
        unregisterNetworkCallback()
        // Stop routing before completing native teardown.
        vpnInterface?.close()
        vpnInterface = null
        // Android can revoke this service as soon as another VPN starts.
        // Release the native loop and its local listener before returning.
        XRayCore.stopLoop(loopGenerationToStop)
        // Drop our foreground notification; Android removes its VPN key when
        // native TUN teardown completes above.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) { }
        // Release the current service start request immediately. A later
        // ACTION_STOP request is stopped in onStartCommand as well.
        stopSelf()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
                connectionManager.requestTunnelHealthCheck()
            }
            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                val cm = getSystemService(ConnectivityManager::class.java)
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) { }
            networkCallback = null
        }
    }

    private fun createNotification(status: String): Notification {
        createNotificationChannel()

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Notification button routes through manager (ACTION_DISCONNECT, not ACTION_STOP)
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ToBeVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopAction = Notification.Action.Builder(
            null, getString(R.string.vpn_notification_action_disconnect), disconnectIntent,
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        activeInstance.compareAndSet(this, null)
        val generationAtDestroy = activeConnectionGeneration
        val hadActiveSession = !cleanedUp && (vpnInterface != null || XRayCore.isRunning)
        if (hadActiveSession) {
            cleanupVpn()
            connectionManager.handleServiceDestroyed(generationAtDestroy)
        }
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        // System revoked VPN — clean up resources immediately, let manager handle state
        cleanupVpn()
        connectionManager.stopVpn()
    }

    // CoreCallbackHandler
    override fun startup(): Long = 0
    override fun shutdown(): Long = 0
    override fun onEmitStatus(l: Long, s: String?): Long = 0

    companion object {
        const val ACTION_START = "com.tobevpn.tv.START"
        const val ACTION_STOP = "com.tobevpn.tv.STOP"
        const val ACTION_DISCONNECT = "com.tobevpn.tv.DISCONNECT"
        const val EXTRA_SERVER_CONFIG = "server_config"
        const val EXTRA_GENERATION = "connection_generation"
        const val EXTRA_STOP_BEFORE_GENERATION = "stop_before_generation"
        const val EXTRA_FORCE_STOP = "force_stop"
        private const val CHANNEL_ID = "tobevpn_tv_channel"
        private const val NOTIFICATION_ID = 1
        private val activeInstance = AtomicReference<ToBeVpnService?>()

        fun cleanupActiveInstance(): Boolean {
            val service = activeInstance.get() ?: return false
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.cleanupVpn()
            } else {
                Handler(Looper.getMainLooper()).postAtFrontOfQueue {
                    service.cleanupVpn()
                }
            }
            return true
        }
    }
}

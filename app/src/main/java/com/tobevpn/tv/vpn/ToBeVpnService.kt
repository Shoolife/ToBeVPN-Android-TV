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
import android.os.ParcelFileDescriptor
import com.tobevpn.tv.MainActivity
import com.tobevpn.tv.R
import com.tobevpn.tv.domain.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import javax.inject.Inject

@AndroidEntryPoint
class ToBeVpnService : VpnService(), CoreCallbackHandler {

    @Inject
    lateinit var connectionManager: VpnConnectionManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        XRayCore.init(this)
        XRayCore.createController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_SERVER_CONFIG) ?: return START_NOT_STICKY
                val generation = intent.getIntExtra(EXTRA_GENERATION, -1)
                startVpn(config, generation)
            }
            ACTION_STOP -> {
                cleanupVpn()
            }
            ACTION_DISCONNECT -> {
                connectionManager.stopVpn()
            }
        }
        return START_STICKY
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
                    cleanupVpn()
                    return@launch
                }
                vpnInterface = fd

                XRayCore.startLoop(configJson, fd.fd)

                connectionManager.updateState(ConnectionState.Connected, generation)
                updateNotification(getString(R.string.state_connected))
                registerNetworkCallback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectionManager.updateState(
                    ConnectionState.Error(e.message ?: getString(R.string.error_generic)),
                    generation,
                )
                cleanupVpn()
            }
        }
    }

    private fun setupTunInterface(): ParcelFileDescriptor? {
        return Builder()
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
            .addDisallowedApplication(packageName)
            .establish()
    }

    private fun cleanupVpn() {
        unregisterNetworkCallback()
        vpnInterface?.close()
        vpnInterface = null
        serviceScope.launch {
            XRayCore.stopLoop()
        }
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

        val disconnectIntent = PendingIntent.getService(
            this, 1,
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
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
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
        private const val CHANNEL_ID = "tobevpn_tv_channel"
        private const val NOTIFICATION_ID = 1
    }
}

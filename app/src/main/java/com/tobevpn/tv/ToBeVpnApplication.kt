package com.tobevpn.tv

import android.app.Application
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.update.UpdateDownloader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ToBeVpnApplication : Application() {

    @Inject
    lateinit var bootstrapManager: BootstrapManager

    @Inject
    lateinit var updateDownloader: UpdateDownloader

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Hilt injects application fields from super.onCreate(), and building
        // those dependencies probes the encrypted database. Register SQLCipher
        // JNI first so that probe cannot race native library initialization.
        System.loadLibrary("sqlcipher")
        super.onCreate()
        // Hydrate cached tokens from the encrypted DB and obtain a fresh access token
        // before the UI starts hitting the API. If we're offline this fails silently
        // and TokenAuthenticator will re-try on the first 401.
        appScope.launch {
            runCatching { updateDownloader.cleanupStaleDownloads() }
            runCatching { bootstrapManager.ensureBootstrapped() }
        }
    }
}

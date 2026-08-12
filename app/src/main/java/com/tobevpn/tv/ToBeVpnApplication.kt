package com.tobevpn.tv

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Looper
import android.util.Log
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.update.UpdateDownloader
import com.tobevpn.tv.util.DiagnosticLogManager
import com.tobevpn.tv.util.SafeDiagnostics
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

    @Inject
    lateinit var diagnosticLogManager: DiagnosticLogManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Hilt injects application fields from super.onCreate(), and building
        // those dependencies probes the encrypted database. Register SQLCipher
        // JNI first so that probe cannot race native library initialization.
        System.loadLibrary("sqlcipher")
        super.onCreate()
        SafeDiagnostics.installSink(
            value = diagnosticLogManager::record,
            isDetailedLoggingEnabled = diagnosticLogManager::isCollectionActive,
        )
        installCrashDiagnostics()
        // Hydrate cached tokens from the encrypted DB and obtain a fresh access token
        // before the UI starts hitting the API. If we're offline this fails silently
        // and TokenAuthenticator will re-try on the first 401.
        appScope.launch {
            runCatching { diagnosticLogManager.initialize() }
            SafeDiagnostics.info(TAG, "TV application process started")
            runCatching { updateDownloader.cleanupStaleDownloads() }
            runCatching { bootstrapManager.ensureBootstrapped() }
        }
    }

    private fun installCrashDiagnostics() {
        val delegate = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val threadKind = if (thread === Looper.getMainLooper().thread) "MAIN" else "BACKGROUND"
                diagnosticLogManager.recordCritical(
                    level = Log.ERROR,
                    tag = "UncaughtFailure",
                    message = "Uncaught TV application failure: thread=$threadKind " +
                        SafeDiagnostics.failureSummary(error),
                )
            } finally {
                delegate.uncaughtException(thread, error)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val message = "Android memory trim callback: level=$level"
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            SafeDiagnostics.warn(TAG, message)
        } else {
            SafeDiagnostics.trace(TAG, message)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SafeDiagnostics.warn(TAG, "Android low-memory callback received")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SafeDiagnostics.trace(
            TAG,
            "TV runtime configuration changed: ui_mode=${newConfig.uiMode} font_scale=${newConfig.fontScale}",
        )
    }

    private companion object {
        const val TAG = "ToBeVpnApplication"
    }
}

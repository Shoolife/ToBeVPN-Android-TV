package com.tobevpn.tv.data.remote

import android.util.Log
import com.tobevpn.tv.data.device.DeviceFingerprintProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Direct GET on the panel's public subscription URL with HWID headers.
// This is the only request Remnawave actually parses to create/refresh an
// HWID device record; the bot's /api/* endpoints don't expose it to the panel.
// We hit the URL (a) before each VPN connect, (b) on subscription refresh.
@Singleton
class SubscriptionPinger @Inject constructor(
    private val fingerprintProvider: DeviceFingerprintProvider,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun ping(subscriptionUrl: String?) = withContext(Dispatchers.IO) {
        if (subscriptionUrl.isNullOrBlank()) return@withContext
        try {
            val fp = fingerprintProvider.get()
            val req = Request.Builder()
                .url(subscriptionUrl)
                .get()
                .header("x-hwid", fp.hwid)
                .header("x-device-os", fp.platform)
                .header("x-ver-os", fp.osVersion)
                .header("x-device-model", fp.model)
                .header("User-Agent", fp.userAgent)
                .build()
            client.newCall(req).execute().use { /* body discarded */ }
        } catch (e: Exception) {
            Log.w("SubscriptionPinger", "ping failed: ${e.message}")
        }
    }
}

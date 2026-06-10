package com.tobevpn.tv.data.device

import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.dao.SessionDao
import kotlinx.coroutines.flow.firstOrNull
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable install-scoped device ID used by the app-auth backend.
 *
 * Existing installs keep their current persisted session/device ID so the
 * server does not create a duplicate linked device during migration.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    private val prefsDataStore: PrefsDataStore,
    private val sessionDao: SessionDao,
    private val fingerprintProvider: DeviceFingerprintProvider,
) {
    suspend fun getOrCreate(): String {
        val session = sessionDao.getSession()
        val sessionDeviceId = session?.deviceId?.takeIf { it.isNotBlank() }
        val hwidDeviceId = stableDeviceIdFromHwid()
        val isLinked = session?.authState == "AUTHENTICATED" && session.telegramId != null

        if (sessionDeviceId != null &&
            (isLinked || hwidDeviceId == null || sessionDeviceId == hwidDeviceId)
        ) {
            if (prefsDataStore.deviceId.firstOrNull() != sessionDeviceId) {
                prefsDataStore.setDeviceId(sessionDeviceId)
            }
            return sessionDeviceId
        }

        if (hwidDeviceId != null) {
            if (prefsDataStore.deviceId.firstOrNull() != hwidDeviceId) {
                prefsDataStore.setDeviceId(hwidDeviceId)
            }
            return hwidDeviceId
        }

        val stored = prefsDataStore.deviceId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (stored != null) {
            return stored
        }

        val installId = UUID.randomUUID().toString()
        prefsDataStore.setDeviceId(installId)
        return installId
    }

    private fun stableDeviceIdFromHwid(): String? {
        val hwid = fingerprintProvider.get().hwid
            .trim()
            .lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() && it != LEGACY_BROKEN_ANDROID_ID }
            ?: return null

        return UUID.nameUUIDFromBytes(
            "$DEVICE_ID_NAMESPACE:$hwid".toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }

    private companion object {
        const val DEVICE_ID_NAMESPACE = "tobevpn:android-tv:device-id:v1"
        const val LEGACY_BROKEN_ANDROID_ID = "9774d56d682e549c"
    }
}

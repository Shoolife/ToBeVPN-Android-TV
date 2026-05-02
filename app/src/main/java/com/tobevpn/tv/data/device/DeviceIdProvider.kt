package com.tobevpn.tv.data.device

import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.dao.SessionDao
import kotlinx.coroutines.flow.firstOrNull
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
) {
    suspend fun getOrCreate(): String {
        val sessionDeviceId = sessionDao.getSession()?.deviceId?.takeIf { it.isNotBlank() }
        if (sessionDeviceId != null) {
            val stored = prefsDataStore.deviceId.firstOrNull()
            if (stored != sessionDeviceId) {
                prefsDataStore.setDeviceId(sessionDeviceId)
            }
            return sessionDeviceId
        }

        val stored = prefsDataStore.deviceId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (stored != null) {
            return stored
        }

        val installId = UUID.randomUUID().toString()
        prefsDataStore.setDeviceId(installId)
        return installId
    }
}

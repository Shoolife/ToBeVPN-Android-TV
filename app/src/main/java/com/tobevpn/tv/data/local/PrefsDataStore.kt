package com.tobevpn.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("tobevpn_tv_prefs")

@Singleton
class PrefsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val AUTOMATIC_SERVER_SELECTION = booleanPreferencesKey("automatic_server_selection")
        val SERVER_QUALITY_STATE = stringPreferencesKey("server_quality_state")
        val USD_RATE = doublePreferencesKey("usd_rate")
        val USD_RATE_TIMESTAMP = longPreferencesKey("usd_rate_timestamp")
        val DEVICE_ID_V2 = booleanPreferencesKey("device_id_v2")
        val SERVER_CACHE_OWNER = stringPreferencesKey("server_cache_owner")
        val BLOCKED_SUBSCRIPTION_OWNER = stringPreferencesKey("blocked_subscription_owner")
        val UPDATE_REQUIRED = booleanPreferencesKey("update_required")
    }

    val deviceId: Flow<String?> = context.dataStore.data.map { it[Keys.DEVICE_ID] }
    val onboardingSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }
    val selectedServerId: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_SERVER_ID] }
    val automaticServerSelection: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.AUTOMATIC_SERVER_SELECTION] ?: (it[Keys.SELECTED_SERVER_ID] == null)
    }

    suspend fun getCachedUsdRate(): Pair<Double, Long>? {
        val prefs = context.dataStore.data.first()
        val rate = prefs[Keys.USD_RATE]
        val ts = prefs[Keys.USD_RATE_TIMESTAMP]
        return if (rate != null && ts != null) rate to ts else null
    }

    suspend fun setCachedUsdRate(rate: Double, timestamp: Long) {
        context.dataStore.edit {
            it[Keys.USD_RATE] = rate
            it[Keys.USD_RATE_TIMESTAMP] = timestamp
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }

    suspend fun setSelectedServerId(id: String) {
        context.dataStore.edit {
            val automatic = it[Keys.AUTOMATIC_SERVER_SELECTION] ?: (it[Keys.SELECTED_SERVER_ID] == null)
            it[Keys.SELECTED_SERVER_ID] = id
            it[Keys.AUTOMATIC_SERVER_SELECTION] = automatic
        }
    }

    suspend fun setManualSelectedServerId(id: String) {
        context.dataStore.edit {
            it[Keys.SELECTED_SERVER_ID] = id
            it[Keys.AUTOMATIC_SERVER_SELECTION] = false
        }
    }

    suspend fun setAutomaticSelectedServerId(id: String) {
        context.dataStore.edit {
            it[Keys.SELECTED_SERVER_ID] = id
            it[Keys.AUTOMATIC_SERVER_SELECTION] = true
        }
    }

    suspend fun isAutomaticServerSelection(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.AUTOMATIC_SERVER_SELECTION] ?: (prefs[Keys.SELECTED_SERVER_ID] == null)
    }

    suspend fun getSelectedServerId(): String? {
        return context.dataStore.data.first()[Keys.SELECTED_SERVER_ID]
    }

    suspend fun getServerQualityState(): String? {
        return context.dataStore.data.first()[Keys.SERVER_QUALITY_STATE]
    }

    suspend fun setServerQualityState(value: String) {
        context.dataStore.edit { it[Keys.SERVER_QUALITY_STATE] = value }
    }

    val isDeviceIdV2: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEVICE_ID_V2] ?: false }

    suspend fun markDeviceIdV2() {
        context.dataStore.edit { it[Keys.DEVICE_ID_V2] = true }
    }

    suspend fun setServerCacheOwner(shortUuid: String) {
        context.dataStore.edit { it[Keys.SERVER_CACHE_OWNER] = cacheOwnerHash(shortUuid) }
    }

    suspend fun isServerCacheOwner(shortUuid: String): Boolean {
        return context.dataStore.data.first()[Keys.SERVER_CACHE_OWNER] == cacheOwnerHash(shortUuid)
    }

    suspend fun clearServerCacheOwner() {
        context.dataStore.edit { it.remove(Keys.SERVER_CACHE_OWNER) }
    }

    // --- Subscription usage block (is-hack) ---
    // The flag is bound to the owning shortUuid (hashed) so a stale block
    // from a previous account can't carry over to a new one.
    suspend fun setSubscriptionUsageBlocked(shortUuid: String, blocked: Boolean) {
        val owner = cacheOwnerHash(shortUuid)
        context.dataStore.edit {
            if (blocked) {
                it[Keys.BLOCKED_SUBSCRIPTION_OWNER] = owner
            } else if (it[Keys.BLOCKED_SUBSCRIPTION_OWNER] == owner) {
                it.remove(Keys.BLOCKED_SUBSCRIPTION_OWNER)
            }
        }
    }

    suspend fun isSubscriptionUsageBlocked(shortUuid: String): Boolean {
        return context.dataStore.data.first()[Keys.BLOCKED_SUBSCRIPTION_OWNER] == cacheOwnerHash(shortUuid)
    }

    fun observeSubscriptionUsageBlocked(shortUuid: String): Flow<Boolean> {
        val owner = cacheOwnerHash(shortUuid)
        return context.dataStore.data
            .map { it[Keys.BLOCKED_SUBSCRIPTION_OWNER] == owner }
            .distinctUntilChanged()
    }

    // --- Forced update (update-required) ---
    suspend fun setUpdateRequired(required: Boolean) {
        context.dataStore.edit {
            if (required) it[Keys.UPDATE_REQUIRED] = true
            else it.remove(Keys.UPDATE_REQUIRED)
        }
    }

    fun observeUpdateRequired(): Flow<Boolean> {
        return context.dataStore.data
            .map { it[Keys.UPDATE_REQUIRED] == true }
            .distinctUntilChanged()
    }

    private fun cacheOwnerHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }
}

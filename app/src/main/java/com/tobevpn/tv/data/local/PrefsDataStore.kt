package com.tobevpn.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tobevpn.tv.BuildConfig
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
        val SUBSCRIPTION_URL_OWNER = stringPreferencesKey("subscription_url_owner")
        val SUBSCRIPTION_URL_VALUE = stringPreferencesKey("subscription_url_value")
        val BLOCKED_SUBSCRIPTION_OWNER = stringPreferencesKey("blocked_subscription_owner")
        val APP_FILTER_MODE = stringPreferencesKey("app_filter_mode")
        val APP_FILTER_PACKAGES = stringPreferencesKey("app_filter_packages")
        // Scope the persisted block to the installed build. After an update,
        // a block recorded by the previous version must not lock the new
        // version before it can read the current minimum-version header.
        val UPDATE_REQUIRED = booleanPreferencesKey(
            "minimum_version_update_required_${BuildConfig.VERSION_NAME}",
        )
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

    suspend fun setCachedSubscriptionUrl(shortUuid: String, url: String?) {
        val owner = cacheOwnerHash(shortUuid)
        context.dataStore.edit {
            if (url.isNullOrBlank()) {
                if (it[Keys.SUBSCRIPTION_URL_OWNER] == owner) {
                    it.remove(Keys.SUBSCRIPTION_URL_OWNER)
                    it.remove(Keys.SUBSCRIPTION_URL_VALUE)
                }
            } else {
                it[Keys.SUBSCRIPTION_URL_OWNER] = owner
                it[Keys.SUBSCRIPTION_URL_VALUE] = url
            }
        }
    }

    suspend fun getCachedSubscriptionUrl(shortUuid: String): String? {
        val prefs = context.dataStore.data.first()
        return if (prefs[Keys.SUBSCRIPTION_URL_OWNER] == cacheOwnerHash(shortUuid)) {
            prefs[Keys.SUBSCRIPTION_URL_VALUE]?.takeIf { it.isNotBlank() }
        } else {
            null
        }
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

    // --- Forced update ---
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

    val appFilterMode: Flow<String?> = context.dataStore.data.map { it[Keys.APP_FILTER_MODE] }
    val appFilterPackages: Flow<Set<String>?> = context.dataStore.data.map {
        it[Keys.APP_FILTER_PACKAGES]?.let(::decodeAppFilterPackages)
    }

    suspend fun getAppFilterMode(): String? {
        return context.dataStore.data.first()[Keys.APP_FILTER_MODE]
    }

    suspend fun setAppFilterMode(mode: String) {
        context.dataStore.edit { it[Keys.APP_FILTER_MODE] = mode }
    }

    suspend fun getAppFilterPackages(): Set<String>? {
        return context.dataStore.data.first()[Keys.APP_FILTER_PACKAGES]?.let(::decodeAppFilterPackages)
    }

    suspend fun setAppFilterPackages(packageNames: Collection<String>) {
        context.dataStore.edit {
            it[Keys.APP_FILTER_PACKAGES] = encodeAppFilterPackages(packageNames)
        }
    }

    private fun cacheOwnerHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun encodeAppFilterPackages(packageNames: Collection<String>): String =
        packageNames.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString(separator = "\n")

    private fun decodeAppFilterPackages(raw: String): Set<String> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
}

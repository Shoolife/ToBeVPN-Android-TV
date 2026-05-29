package com.tobevpn.tv.data.repository

import android.content.Context
import android.os.Build
import com.tobevpn.tv.data.device.DeviceIdProvider
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.SessionStore
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.entity.SessionEntity
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.SubscriptionPinger
import com.tobevpn.tv.data.remote.dto.AuthRequestDto
import com.tobevpn.tv.data.remote.dto.DeviceRegisterRequestDto
import com.tobevpn.tv.data.remote.dto.DeviceUnlinkRequestDto
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.domain.model.UserPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val sessionStore: SessionStore,
    private val botApi: BotApi,
    private val bootstrapManager: BootstrapManager,
    private val usageRepository: UsageRepository,
    private val subscriptionPinger: SubscriptionPinger,
    private val deviceIdProvider: DeviceIdProvider,
    private val prefsDataStore: PrefsDataStore,
) {
    /** Observe the subscription-usage block flag for the current session's shortUuid. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeSubscriptionUsageBlocked(): Flow<Boolean> {
        return sessionDao.observeSession()
            .map { it?.shortUuid }
            .distinctUntilChanged()
            .flatMapLatest { shortUuid ->
                if (shortUuid.isNullOrBlank()) {
                    kotlinx.coroutines.flow.flowOf(false)
                } else {
                    prefsDataStore.observeSubscriptionUsageBlocked(shortUuid)
                }
            }
    }

    /** Observe the forced-update flag. */
    fun observeUpdateRequired(): Flow<Boolean> = prefsDataStore.observeUpdateRequired()

    /**
     * HWID-marker ping used by the connect path so the panel registers the
     * device on every VPN start and we learn the current block / update state.
     * Returns true if usage is blocked. The subscription URL is resolved via
     * the panel JSON endpoint (TV doesn't cache it in the session row).
     */
    suspend fun pingHwidOnly(): Boolean {
        val session = sessionDao.getSession() ?: return false
        val shortUuid = session.shortUuid ?: return false
        val wasBlocked = runCatching {
            prefsDataStore.isSubscriptionUsageBlocked(shortUuid)
        }.getOrDefault(false)
        return try {
            val subInfo = botApi.getSubscriptionInfo(shortUuid).response
            val url = subInfo.subscriptionUrl ?: return wasBlocked
            val result = subscriptionPinger.ping(url) ?: return wasBlocked
            prefsDataStore.setSubscriptionUsageBlocked(shortUuid, result.isUsageBlocked)
            prefsDataStore.setUpdateRequired(result.isUpdateRequired)
            result.isUsageBlocked
        } catch (_: Exception) {
            wasBlocked
        }
    }
    data class PlanLimitsInfo(
        val trafficLimitBytes: Long,
        val deviceLimit: Int,
    )

    private fun planForPanelUser(panelUser: com.tobevpn.tv.data.remote.dto.PanelUserDto): String {
        val squads = panelUser.activeInternalSquads.map { it.name.uppercase(Locale.US) }
        return when {
            "ADMINS" in squads -> "ADMIN"
            "STANDART" in squads -> "PAID"
            else -> "FREE_TRIAL"
        }
    }

    private fun parsePanelExpireAtMillis(value: String?): Long {
        if (value.isNullOrBlank()) return Long.MIN_VALUE
        return try {
            val normalized = value.replace("Z", "+00:00")
            java.time.OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        } catch (_: Exception) {
            Long.MIN_VALUE
        }
    }

    private fun selectBestPanelUser(
        users: List<com.tobevpn.tv.data.remote.dto.PanelUserDto>,
        preferredPanelUserUuid: String? = null,
        preferredShortUuid: String? = null,
    ): com.tobevpn.tv.data.remote.dto.PanelUserDto? {
        users.firstOrNull { preferredPanelUserUuid != null && it.uuid == preferredPanelUserUuid }?.let {
            return it
        }
        users.firstOrNull { preferredShortUuid != null && it.shortUuid == preferredShortUuid }?.let {
            return it
        }
        return users.maxWithOrNull(
            compareBy<com.tobevpn.tv.data.remote.dto.PanelUserDto>(
                { when (planForPanelUser(it)) {
                    "ADMIN" -> 3
                    "PAID" -> 2
                    else -> 1
                } },
                { if (it.status.equals("ACTIVE", ignoreCase = true)) 1 else 0 },
                { if (it.trafficLimitStrategy.equals("MONTH", ignoreCase = true)) 1 else 0 },
                { parsePanelExpireAtMillis(it.expireAt) },
            )
        )
    }

    fun observeAuthState(): Flow<AuthState> {
        return sessionDao.observeSession().map { session ->
            if (session?.authState == "AUTHENTICATED" && session.telegramId != null) {
                val plan = UserPlan.entries.find { it.name == session.userPlan } ?: UserPlan.FREE_TRIAL
                AuthState.Authenticated(
                    telegramId = session.telegramId,
                    plan = plan,
                    planExpiresAt = session.planExpiresAt,
                )
            } else {
                AuthState.Unauthenticated
            }
        }.distinctUntilChanged()
    }

    suspend fun getOrCreateDeviceId(): String = deviceIdProvider.getOrCreate()

    private fun currentDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return when {
            model.isEmpty() && manufacturer.isEmpty() -> "Android TV"
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    suspend fun registerCurrentDevice(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = botApi.registerDevice(
                DeviceRegisterRequestDto(
                    deviceName = currentDeviceName(),
                    deviceType = "tv",
                    platform = "Android TV",
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.message ?: "Could not register device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unlinkCurrentDevice(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = botApi.unlinkDevice()
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.message ?: "Could not unlink device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isCurrentDeviceLinked(): Result<Boolean> = withContext(Dispatchers.IO) {
        val deviceId = getOrCreateDeviceId()
        return@withContext try {
            val response = botApi.getDevices()
            val data = response.data
                ?: return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not load linked devices")
                )
            Result.success(data.devices.any { it.deviceId == deviceId })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDeviceSessionState(): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val hadLinkedIdentity = sessionDao.getSession()?.let { session ->
                session.authState == "AUTHENTICATED" && session.telegramId != null
            } == true

            val tokens = bootstrapManager.syncSessionState()
            val isLinked = tokens.isLinked && tokens.telegramId != null
            if (isLinked) {
                return@withContext Result.success(true)
            }

            if (hadLinkedIdentity) {
                val confirmedTokens = runCatching { bootstrapManager.syncSessionState() }.getOrNull()
                val confirmedUnlinked = confirmedTokens?.let { !it.isLinked || it.telegramId == null } == true
                if (!confirmedUnlinked) {
                    return@withContext Result.success(true)
                }
                clearLinkedIdentity()
            }

            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearLinkedIdentity() {
        sessionStore.update { session ->
            session.copy(
                authState = "UNAUTHENTICATED",
                telegramId = null,
                planExpiresAt = null,
                shortUuid = null,
                panelUserUuid = null,
                userPlan = "FREE_TRIAL",
                isLinked = false,
            )
        } ?: return
        usageRepository.updateUsage(0, 0)
        usageRepository.updateLimits(0, 0)
    }

    suspend fun getCurrentPlanLimits(): PlanLimitsInfo? = withContext(Dispatchers.IO) {
        val session = sessionDao.getSession() ?: return@withContext null
        val telegramId = session.telegramId ?: return@withContext null
        return@withContext try {
            val panelUsers = botApi.getUserByTelegramId(telegramId).response
            val panelUser = selectBestPanelUser(
                panelUsers,
                preferredPanelUserUuid = session.panelUserUuid,
                preferredShortUuid = session.shortUuid,
            ) ?: return@withContext null
            PlanLimitsInfo(
                trafficLimitBytes = panelUser.trafficLimitBytes,
                deviceLimit = panelUser.hwidDeviceLimit ?: 0,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Requests a server-generated Telegram auth token for the current TV session. */
    suspend fun requestTelegramAuth(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val session = sessionDao.getSession()
            val response = botApi.requestAuth(
                AuthRequestDto(
                    deviceId = getOrCreateDeviceId(),
                    panelUserUuid = session?.panelUserUuid,
                )
            )
            if (!response.success) {
                return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not request auth")
                )
            }
            val data = response.data
                ?: return@withContext Result.failure(IllegalStateException("Auth token missing"))
            Result.success(data.authToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Polls bot backend for auth completion. Returns true when Telegram auth is confirmed. */
    suspend fun checkAuthStatus(authToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = botApi.checkAuthStatus(authToken)
            if (!response.success) {
                return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not check auth status")
                )
            }
            val data = response.data ?: return@withContext Result.success(false)
            when (data.status) {
                "completed" -> {
                    val telegramId = data.telegramId ?: return@withContext Result.failure(
                        IllegalStateException("Auth completed without telegram_id")
                    )
                    val deviceId = getOrCreateDeviceId()
                    val seeded = sessionStore.updateOrCreate(deviceId) { session ->
                        session.copy(
                            authState = "AUTHENTICATED",
                            telegramId = telegramId,
                            shortUuid = data.shortUuid ?: session.shortUuid,
                            panelUserUuid = data.panelUserUuid ?: session.panelUserUuid,
                            isLinked = true,
                        )
                    }

                    try {
                        val panelUsers = botApi.getUserByTelegramId(telegramId).response
                        val panelUser = selectBestPanelUser(
                            panelUsers,
                            preferredPanelUserUuid = data.panelUserUuid ?: seeded.panelUserUuid,
                            preferredShortUuid = data.shortUuid ?: seeded.shortUuid,
                        )
                        if (panelUser != null) {
                            sessionStore.update { current ->
                                current.copy(
                                    panelUserUuid = panelUser.uuid,
                                    shortUuid = panelUser.shortUuid,
                                    userPlan = planForPanelUser(panelUser),
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }

                    registerCurrentDevice()
                    syncSubscription()
                    Result.success(true)
                }
                "expired" -> Result.failure(IllegalStateException("expired"))
                else -> Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncSubscription(overwriteUsage: Boolean = true) {
        try {
            var session = sessionDao.getSession() ?: return
            var panelUser: com.tobevpn.tv.data.remote.dto.PanelUserDto? = null

            if (session.authState == "AUTHENTICATED" && session.telegramId != null) {
                try {
                    val panelUsers = botApi.getUserByTelegramId(session.telegramId).response
                    panelUser = selectBestPanelUser(
                        panelUsers,
                        preferredPanelUserUuid = session.panelUserUuid,
                        preferredShortUuid = session.shortUuid,
                    )
                    if (panelUser != null) {
                        val updated = sessionStore.update { current ->
                            current.copy(
                                shortUuid = panelUser.shortUuid,
                                panelUserUuid = panelUser.uuid,
                            )
                        }
                        if (updated != null) session = updated
                    }
                } catch (_: Exception) {
                }
            }

            val shortUuid = session.shortUuid ?: return
            val subInfo = botApi.getSubscriptionInfo(shortUuid).response
            if (!subInfo.isFound || subInfo.user == null) return

            // Direct hit on the panel's public sub URL with HWID headers — only
            // request Remnawave actually parses for HWID device tracking. The
            // response also carries the block (is-hack) / forced-update flags.
            val pingResult = subscriptionPinger.ping(panelUser?.subscriptionUrl ?: subInfo.subscriptionUrl)
            if (pingResult != null) {
                prefsDataStore.setSubscriptionUsageBlocked(shortUuid, pingResult.isUsageBlocked)
                prefsDataStore.setUpdateRequired(pingResult.isUpdateRequired)
            }

            val sub = subInfo.user
            val isActive = sub.isActive && sub.userStatus == "ACTIVE"

            val plan = if (!isActive) {
                "EXPIRED"
            } else if (session.authState == "AUTHENTICATED" && session.telegramId != null && panelUser != null) {
                planForPanelUser(panelUser)
            } else {
                if (sub.trafficLimitStrategy == "MONTH") "PAID" else "FREE_TRIAL"
            }

            val expiresAtMillis = try {
                val expiresStr = sub.expiresAt
                if (expiresStr != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val clean = expiresStr.replace(Regex("[.+Z].*"), "")
                    sdf.parse(clean)?.time
                } else null
            } catch (_: Exception) {
                null
            }

            sessionStore.update { current ->
                current.copy(userPlan = plan, planExpiresAt = expiresAtMillis)
            }

            val trafficLimitBytes = panelUser?.trafficLimitBytes
                ?: (sub.trafficLimitBytes.toLongOrNull() ?: 0)
            usageRepository.updateLimits(trafficLimitBytes, 0)

            if (overwriteUsage && session.authState == "AUTHENTICATED") {
                val trafficUsedBytes = panelUser?.userTraffic?.usedTrafficBytes
                    ?: (sub.trafficUsedBytes.toLongOrNull() ?: 0)
                val currentTime = usageRepository.getUsage().timeUsedSeconds
                usageRepository.updateUsage(trafficUsedBytes, currentTime)
            }

            if (session.authState == "AUTHENTICATED" && session.telegramId != null) {
                registerCurrentDevice()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun logout(unlinkRemote: Boolean = true) {
        if (sessionDao.getSession() == null) return

        if (unlinkRemote) {
            // Pass the explicit device_id so the backend unlinks *this* device
            // and it disappears from the user's device list (the no-body call
            // left the TV linked, mirroring the phone client's behaviour).
            try {
                botApi.unlinkDevice(DeviceUnlinkRequestDto(deviceId = getOrCreateDeviceId()))
            } catch (_: Exception) {
            }
            try {
                botApi.logoutDevice()
            } catch (_: Exception) {
            }
        }

        sessionStore.update { current ->
            current.copy(
                authState = "UNAUTHENTICATED",
                telegramId = null,
                planExpiresAt = null,
                shortUuid = null,
                panelUserUuid = null,
                userPlan = "FREE_TRIAL",
                accessToken = null,
                refreshToken = null,
                accessExpiresAt = null,
                refreshExpiresAt = null,
                isLinked = false,
            )
        }
        bootstrapManager.clear()
        usageRepository.updateUsage(0, 0)
        usageRepository.updateLimits(0, 0)
    }
}

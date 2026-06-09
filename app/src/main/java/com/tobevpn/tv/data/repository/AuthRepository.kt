package com.tobevpn.tv.data.repository

import android.content.Context
import android.os.Build
import com.tobevpn.tv.data.device.DeviceFingerprintProvider
import com.tobevpn.tv.data.device.DeviceIdProvider
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.local.SessionStore
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.entity.SessionEntity
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.SubscriptionPinger
import com.tobevpn.tv.data.remote.dto.AuthRequestDto
import com.tobevpn.tv.data.remote.dto.CurrentPlanDto
import com.tobevpn.tv.data.remote.dto.DeviceRegisterRequestDto
import com.tobevpn.tv.data.remote.dto.DeviceUnlinkRequestDto
import com.tobevpn.tv.data.remote.dto.TvPairCreateRequestDto
import com.tobevpn.tv.data.remote.dto.TvPairCreateResponseDto
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
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DevicePairingPollResult {
    data object Pending : DevicePairingPollResult
    data object Expired : DevicePairingPollResult
    data object Completed : DevicePairingPollResult
}

data class CurrentSubscriptionPlanInfo(
    val displayName: String?,
    val trafficLimitBytes: Long?,
    val deviceLimit: Int?,
    val expiresAtMillis: Long?,
    val isActive: Boolean?,
    val isExpired: Boolean?,
    val isTrial: Boolean?,
    val isUnlimited: Boolean?,
    val hasPlanData: Boolean,
    val subscriptionUrl: String?,
)

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
    private val fingerprintProvider: DeviceFingerprintProvider,
    private val prefsDataStore: PrefsDataStore,
    private val subscriptionInfoProvider: SubscriptionInfoProvider,
    private val vpnRepository: VpnRepository,
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
     * Returns true if usage is blocked. The subscription URL is resolved from
     * local cache/current-plan first; the panel JSON endpoint is legacy fallback.
     */
    suspend fun pingHwidOnly(): Boolean {
        val session = sessionDao.getSession() ?: return false
        val shortUuid = session.shortUuid ?: return false
        val wasBlocked = runCatching {
            prefsDataStore.isSubscriptionUsageBlocked(shortUuid)
        }.getOrDefault(false)
        return try {
            val cachedUrl = prefsDataStore.getCachedSubscriptionUrl(shortUuid)
            val currentPlanUrl = if (session.authState == "AUTHENTICATED") {
                getCurrentSubscriptionPlan()?.subscriptionUrl
            } else {
                null
            }
            val url = cachedUrl ?: currentPlanUrl ?: run {
                val subInfo = subscriptionInfoProvider.get(shortUuid)
                subInfo.subscriptionUrl?.also { prefsDataStore.setCachedSubscriptionUrl(shortUuid, it) }
            } ?: return wasBlocked
            prefsDataStore.setCachedSubscriptionUrl(shortUuid, url)
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

    private suspend fun getCurrentSubscriptionPlan(): CurrentSubscriptionPlanInfo? {
        return try {
            val response = botApi.getCurrentPlan()
            if (response.success) {
                response.data?.toCurrentSubscriptionPlanInfo()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun CurrentPlanDto.toCurrentSubscriptionPlanInfo(): CurrentSubscriptionPlanInfo {
        val snapshot = currentPlan ?: planSnapshot
        val subscriptionStatus = subscription?.status
            ?: subscription?.storedStatus
            ?: status
        val expiresAtMillis = epochTimestampToMillis(subscription?.expireAtTs)
            ?: parsePanelExpireAtMillisOrNull(subscription?.expireAt)
            ?: parsePanelExpireAtMillisOrNull(subscription?.expiresAt)
            ?: parsePanelExpireAtMillisOrNull(expireAt)
            ?: parsePanelExpireAtMillisOrNull(expiresAt)
        val isExpired = subscription?.isExpired
            ?: subscriptionStatus?.equals("EXPIRED", ignoreCase = true)
            ?: expiresAtMillis?.let { it <= System.currentTimeMillis() }
        val isActive = subscription?.isActive
            ?: subscriptionStatus?.let { it.equals("ACTIVE", ignoreCase = true) }
        val hasPlanData = snapshot != null || subscription != null ||
            !planName.isNullOrBlank() || !name.isNullOrBlank()
        return CurrentSubscriptionPlanInfo(
            displayName = snapshot?.name?.trim()?.takeIf { it.isNotBlank() }
                ?: planName?.trim()?.takeIf { it.isNotBlank() }
                ?: name?.trim()?.takeIf { it.isNotBlank() },
            trafficLimitBytes = normalizeTrafficLimitBytes(
                trafficLimitBytes = subscription?.trafficLimitBytes,
                trafficLimit = subscription?.trafficLimit,
            ) ?: normalizeTrafficLimitBytes(
                trafficLimitBytes = snapshot?.trafficLimitBytes,
                trafficLimit = snapshot?.trafficLimit,
            ),
            deviceLimit = subscription?.deviceLimit ?: snapshot?.deviceLimit,
            expiresAtMillis = expiresAtMillis,
            isActive = isActive,
            isExpired = isExpired,
            isTrial = subscription?.isTrial ?: snapshot?.isTrial,
            isUnlimited = subscription?.isUnlimited
                ?: snapshot?.type?.equals("UNLIMITED", ignoreCase = true),
            hasPlanData = hasPlanData,
            subscriptionUrl = subscription?.url?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun normalizeTrafficLimitBytes(trafficLimitBytes: Long?, trafficLimit: Long?): Long? {
        if (trafficLimitBytes != null) return trafficLimitBytes
        return normalizePlanTrafficLimit(trafficLimit)
    }

    private fun normalizePlanTrafficLimit(value: Long?): Long? {
        val raw = value ?: return null
        if (raw <= 0) return 0
        return if (raw > 1024L * 1024L) raw else raw * 1024L * 1024L * 1024L
    }

    private fun epochTimestampToMillis(value: Long?): Long? {
        val timestamp = value?.takeIf { it > 0 } ?: return null
        return if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    }

    private fun parsePanelExpireAtMillisOrNull(value: String?): Long? {
        val parsed = parsePanelExpireAtMillis(value)
        return parsed.takeIf { it != Long.MIN_VALUE }
    }

    private fun resolvePlanFromCurrentPlan(cachedPlan: String, currentPlanInfo: CurrentSubscriptionPlanInfo?): String {
        if (currentPlanInfo == null) return cachedPlan
        if (!currentPlanInfo.hasPlanData) return "FREE_TRIAL"
        return when {
            currentPlanInfo.isExpired == true || currentPlanInfo.isActive == false -> "EXPIRED"
            currentPlanInfo.isTrial == true -> "FREE_TRIAL"
            else -> "PAID"
        }
    }

    fun observeAuthState(): Flow<AuthState> {
        return sessionDao.observeSession().map { session ->
            if (session?.authState == "AUTHENTICATED" && session.telegramId != null) {
                val plan = UserPlan.entries.find { it.name == session.userPlan } ?: UserPlan.FREE_TRIAL
                AuthState.Authenticated(
                    telegramId = session.telegramId,
                    plan = plan,
                    planExpiresAt = session.planExpiresAt,
                    planDisplayName = session.planDisplayName,
                )
            } else {
                AuthState.Unauthenticated
            }
        }.distinctUntilChanged()
    }

    suspend fun getOrCreateDeviceId(): String = deviceIdProvider.getOrCreate()

    suspend fun getCurrentDeviceAliases(): Set<String> {
        val aliases = linkedSetOf<String>()
        getOrCreateDeviceId().trim().takeIf { it.isNotBlank() }?.let { aliases += it }
        fingerprintProvider.get().hwid.trim().takeIf { it.isNotBlank() }?.let { hwid ->
            aliases += hwid
            aliases += hwid.lowercase(Locale.ROOT)
        }
        return aliases
    }

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
            val aliases = getCurrentDeviceAliases()
            var anySuccess = false
            for (deviceId in aliases) {
                val success = runCatching {
                    botApi.unlinkDevice(DeviceUnlinkRequestDto(deviceId = deviceId)).success
                }.getOrDefault(false)
                anySuccess = anySuccess || success
            }
            if (anySuccess) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Could not unlink device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unlinkOtherDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = botApi.unlinkDevice(DeviceUnlinkRequestDto(deviceId = deviceId))
            if (!response.success) {
                return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not unlink device")
                )
            }
            resetSubscriptionAfterDeviceUnlink().getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resetSubscriptionAfterDeviceUnlink(): Result<Unit> {
        val session = sessionDao.getSession() ?: return Result.success(Unit)
        val oldShortUuid = session.shortUuid ?: return Result.success(Unit)
        return try {
            val response = botApi.resetSubscription(oldShortUuid)
            val data = response.data
            if (!response.success || data == null) {
                return Result.failure(
                    IllegalStateException(response.message ?: "Could not reset subscription link")
                )
            }
            val nextShortUuid = data.shortUuid?.trim()?.takeIf { it.isNotBlank() } ?: oldShortUuid
            prefsDataStore.setCachedSubscriptionUrl(oldShortUuid, null)
            prefsDataStore.setCachedSubscriptionUrl(nextShortUuid, data.subscriptionUrl)
            subscriptionInfoProvider.invalidate(oldShortUuid)
            if (nextShortUuid != oldShortUuid) {
                subscriptionInfoProvider.invalidate(nextShortUuid)
            }
            sessionStore.update { current ->
                current.copy(
                    shortUuid = nextShortUuid,
                    panelUserUuid = data.panelUserUuid ?: current.panelUserUuid,
                    telegramId = data.telegramId ?: current.telegramId,
                )
            }
            data.trafficLimitBytes?.let { usageRepository.updateLimits(it, 0) }
            data.trafficUsedBytes?.let {
                usageRepository.updateUsage(it, usageRepository.getUsage().timeUsedSeconds)
            }
            vpnRepository.clearCachedServers()
            vpnRepository.refreshServers(forceRefresh = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isCurrentDeviceLinked(): Result<Boolean> = withContext(Dispatchers.IO) {
        val aliases = getCurrentDeviceAliases().mapTo(mutableSetOf()) {
            it.trim().lowercase(Locale.ROOT)
        }
        return@withContext try {
            runCatching { pingHwidOnly() }
            val response = botApi.getDevices()
            val data = response.data
                ?: return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not load linked devices")
                )
            Result.success(
                data.devices.any { device ->
                    listOf(device.deviceId, device.hwid).any { value ->
                        value?.trim()?.lowercase(Locale.ROOT) in aliases
                    }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDeviceSessionState(): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val hadLinkedIdentity = sessionDao.getSession()?.let { session ->
                session.authState == "AUTHENTICATED" && session.telegramId != null
            } == true
            if (!hadLinkedIdentity) return@withContext Result.success(false)

            val isLinked = isCurrentDeviceLinked().getOrThrow()
            if (!isLinked) {
                clearLinkedIdentity()
            }
            Result.success(isLinked)
        } catch (e: HttpException) {
            if (e.isRemoteDeviceUnlinkedError()) {
                clearLinkedIdentity()
                Result.success(false)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            if (e.isRemoteDeviceUnlinkedError()) {
                clearLinkedIdentity()
                Result.success(false)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun Throwable.isRemoteDeviceUnlinkedError(): Boolean {
        if (this is HttpException && code() !in setOf(400, 403)) return false
        val body = if (this is HttpException) {
            runCatching { response()?.errorBody()?.string() }.getOrDefault("")
        } else {
            ""
        }
        val text = listOfNotNull(message, body)
            .joinToString("\n")
            .lowercase(Locale.US)
        return (text.contains("current device") && text.contains("not linked")) ||
            (text.contains("telegram_id") && text.contains("not authenticated")) ||
            (text.contains("telegram_id") && text.contains("required"))
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
                planDisplayName = null,
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
            getCurrentSubscriptionPlan()?.let { plan ->
                return@withContext PlanLimitsInfo(
                    trafficLimitBytes = plan.trafficLimitBytes ?: 0,
                    deviceLimit = plan.deviceLimit ?: 0,
                )
            }
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

    suspend fun requestDevicePairing(): Result<TvPairCreateResponseDto> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = botApi.createTvPairing(TvPairCreateRequestDto())
            if (!response.success) {
                return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Could not create pairing code")
                )
            }
            val data = response.data
                ?: return@withContext Result.failure(
                    IllegalStateException(response.message ?: "Pairing code missing")
                )
            Result.success(data)
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
                    applyAuthenticatedDevice(
                        telegramId = telegramId,
                        shortUuid = data.shortUuid,
                        panelUserUuid = data.panelUserUuid,
                    )
                    Result.success(true)
                }
                "expired" -> Result.failure(IllegalStateException("expired"))
                else -> Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkDevicePairingStatus(code: String): Result<DevicePairingPollResult> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val response = botApi.checkTvPairingStatus(code)
                if (!response.success) {
                    return@withContext Result.failure(
                        IllegalStateException(response.message ?: "Could not check pairing status")
                    )
                }
                val data = response.data ?: return@withContext Result.success(DevicePairingPollResult.Pending)
                when (data.status) {
                    "completed" -> {
                        val telegramId = data.telegramId ?: return@withContext Result.failure(
                            IllegalStateException("Pairing completed without telegram_id")
                        )
                        applyAuthenticatedDevice(
                            telegramId = telegramId,
                            shortUuid = data.shortUuid,
                            panelUserUuid = data.panelUserUuid,
                        )
                        Result.success(DevicePairingPollResult.Completed)
                    }
                    "expired" -> Result.success(DevicePairingPollResult.Expired)
                    "rejected" -> Result.failure(
                        IllegalStateException(response.message ?: "Pairing was rejected")
                    )
                    else -> Result.success(DevicePairingPollResult.Pending)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun applyAuthenticatedDevice(
        telegramId: Long,
        shortUuid: String?,
        panelUserUuid: String?,
    ) {
        val deviceId = getOrCreateDeviceId()
        val seeded = sessionStore.updateOrCreate(deviceId) { session ->
            session.copy(
                authState = "AUTHENTICATED",
                telegramId = telegramId,
                shortUuid = shortUuid ?: session.shortUuid,
                panelUserUuid = panelUserUuid ?: session.panelUserUuid,
                isLinked = true,
            )
        }

        // Re-open the device session so server-side bearer claims match the
        // newly bound linked_devices row before account-scoped calls.
        runCatching { bootstrapManager.bootstrap() }

        try {
            val panelUsers = botApi.getUserByTelegramId(telegramId).response
            val panelUser = selectBestPanelUser(
                panelUsers,
                preferredPanelUserUuid = panelUserUuid ?: seeded.panelUserUuid,
                preferredShortUuid = shortUuid ?: seeded.shortUuid,
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
    }

    suspend fun syncSubscription(overwriteUsage: Boolean = true) {
        try {
            var session = sessionDao.getSession() ?: return
            var panelUser: com.tobevpn.tv.data.remote.dto.PanelUserDto? = null
            var currentPlanInfo: CurrentSubscriptionPlanInfo? = null

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
                currentPlanInfo = getCurrentSubscriptionPlan()
            }

            val shortUuid = session.shortUuid ?: return
            var subInfo: com.tobevpn.tv.data.remote.dto.PanelSubInfoDto? = null
            var subscriptionUrl = currentPlanInfo?.subscriptionUrl
                ?: panelUser?.subscriptionUrl
                ?: prefsDataStore.getCachedSubscriptionUrl(shortUuid)
            var profileResult = if (!subscriptionUrl.isNullOrBlank()) {
                subscriptionPinger.fetchProfile(subscriptionUrl)
            } else {
                null
            }

            if (profileResult == null && subscriptionUrl.isNullOrBlank()) {
                subInfo = runCatching {
                    subscriptionInfoProvider.get(shortUuid)
                }.getOrNull()
                subscriptionUrl = subInfo?.subscriptionUrl
                prefsDataStore.setCachedSubscriptionUrl(shortUuid, subscriptionUrl)
                profileResult = subscriptionPinger.fetchProfile(subscriptionUrl)
            }

            if (profileResult != null) {
                prefsDataStore.setCachedSubscriptionUrl(shortUuid, subscriptionUrl)
                prefsDataStore.setSubscriptionUsageBlocked(shortUuid, profileResult.isUsageBlocked)
                prefsDataStore.setUpdateRequired(profileResult.isUpdateRequired)
                when {
                    profileResult.isUsageBlocked -> vpnRepository.clearCachedServers()
                    profileResult.links.isNotEmpty() -> vpnRepository.updateServersFromLinks(shortUuid, profileResult.links)
                    profileResult.isSuccessful -> vpnRepository.clearCachedServers()
                }
            }

            if (subInfo == null && currentPlanInfo == null) {
                subInfo = runCatching {
                    subscriptionInfoProvider.get(shortUuid)
                }.getOrNull()
                prefsDataStore.setCachedSubscriptionUrl(shortUuid, subInfo?.subscriptionUrl ?: subscriptionUrl)
            }

            if (profileResult == null && subInfo != null) {
                vpnRepository.updateServersFromSubscription(shortUuid, subInfo)
            }

            val sub = subInfo?.user
            val isActive = sub?.let { it.isActive && it.userStatus == "ACTIVE" }
                ?: (currentPlanInfo?.isActive == true)

            val plan = if (currentPlanInfo != null) {
                resolvePlanFromCurrentPlan(session.userPlan, currentPlanInfo)
            } else if (sub == null) {
                session.userPlan
            } else if (!isActive) {
                "EXPIRED"
            } else if (session.authState == "AUTHENTICATED" && session.telegramId != null && panelUser != null) {
                planForPanelUser(panelUser)
            } else {
                if (sub.trafficLimitStrategy == "MONTH") "PAID" else "FREE_TRIAL"
            }

            val expiresAtMillis = try {
                val expiresStr = sub?.expiresAt
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
                current.copy(
                    userPlan = plan,
                    planDisplayName = currentPlanInfo?.displayName
                        ?: session.planDisplayName?.takeIf { session.userPlan == plan && plan != "EXPIRED" },
                    planExpiresAt = currentPlanInfo?.expiresAtMillis ?: expiresAtMillis,
                )
            }

            val trafficLimitBytes = profileResult?.trafficLimitBytes
                ?: currentPlanInfo?.trafficLimitBytes
                ?: panelUser?.trafficLimitBytes
                ?: (sub?.trafficLimitBytes?.toLongOrNull() ?: 0)
            usageRepository.updateLimits(trafficLimitBytes, 0)

            if (overwriteUsage && session.authState == "AUTHENTICATED") {
                val trafficUsedBytes = profileResult?.trafficUsedBytes
                    ?: panelUser?.userTraffic?.usedTrafficBytes
                    ?: (sub?.trafficUsedBytes?.toLongOrNull() ?: 0)
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
            for (deviceId in getCurrentDeviceAliases()) {
                try {
                    botApi.unlinkDevice(DeviceUnlinkRequestDto(deviceId = deviceId))
                } catch (_: Exception) {
                }
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
                planDisplayName = null,
                accessToken = null,
                refreshToken = null,
                accessExpiresAt = null,
                refreshExpiresAt = null,
                isLinked = false,
            )
        }
        bootstrapManager.clear()
        vpnRepository.clearServers()
        usageRepository.updateUsage(0, 0)
        usageRepository.updateLimits(0, 0)
    }
}

package com.tobevpn.tv.data.repository

import com.tobevpn.tv.data.local.dao.ServerDao
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.entity.ServerEntity
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.SubscriptionPinger
import com.tobevpn.tv.data.remote.dto.PanelSubInfoDto
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.util.SafeDiagnostics
import com.tobevpn.tv.vpn.VlessUrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val sessionDao: SessionDao,
    private val botApi: BotApi,
    private val subscriptionInfoProvider: SubscriptionInfoProvider,
    private val prefsDataStore: PrefsDataStore,
    private val subscriptionPinger: SubscriptionPinger,
) {
    private val enrichmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshGeneration = AtomicLong(0L)

    fun observeServers(): Flow<List<Server>> {
        return serverDao.observeAll().map { entities ->
            entities.map { it.toDomain() }.filterNot { it.isSentinel }
        }
    }

    suspend fun refreshServers(forceRefresh: Boolean = false): Result<List<Server>> {
        return try {
            val shortUuid = sessionDao.getSession()?.shortUuid
                ?: run {
                    clearServerCache()
                    return Result.failure(Exception("No subscription"))
                }

            var subscriptionUrl = prefsDataStore.getCachedSubscriptionUrl(shortUuid)
            var subInfo: PanelSubInfoDto? = null

            if (subscriptionUrl.isNullOrBlank()) {
                subInfo = subscriptionInfoProvider.get(shortUuid, forceRefresh)
                subscriptionUrl = subInfo.subscriptionUrl
                prefsDataStore.setCachedSubscriptionUrl(shortUuid, subscriptionUrl)
            }

            if (!subscriptionUrl.isNullOrBlank()) {
                val profile = subscriptionPinger.fetchProfile(subscriptionUrl)
                if (profile != null) {
                    prefsDataStore.setSubscriptionUsageBlocked(shortUuid, profile.isUsageBlocked)
                    prefsDataStore.setUpdateRequired(profile.isUpdateRequired)
                    if (profile.isUsageBlocked) {
                        clearServerCache()
                        return Result.success(emptyList())
                    }
                    if (profile.links.isNotEmpty()) {
                        return updateServersFromLinks(shortUuid, profile.links)
                    }
                    if (profile.isSuccessful) {
                        clearServerCache()
                        return Result.success(emptyList())
                    }
                }
            }

            val legacyInfo = subInfo ?: subscriptionInfoProvider.get(shortUuid, forceRefresh)
            prefsDataStore.setCachedSubscriptionUrl(shortUuid, legacyInfo.subscriptionUrl)
            updateServersFromSubscription(shortUuid, legacyInfo)
        } catch (e: Exception) {
            SafeDiagnostics.warn(TAG, "Server refresh failed; checking local cache: ${SafeDiagnostics.failureCategory(e)}")
            val shortUuid = sessionDao.getSession()?.shortUuid
            val cached = if (shortUuid != null && prefsDataStore.isServerCacheOwner(shortUuid)) {
                serverDao.getAll().map { it.toDomain() }.filterNot { it.isSentinel }
            } else {
                emptyList()
            }
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun updateServersFromSubscription(
        shortUuid: String,
        subInfo: PanelSubInfoDto,
    ): Result<List<Server>> {
        if (!subInfo.isFound || subInfo.links.isNullOrEmpty()) {
            clearServerCache()
            return Result.failure(Exception("Subscription not found"))
        }

        return updateServersFromLinks(shortUuid, subInfo.links)
    }

    suspend fun updateServersFromLinks(
        shortUuid: String,
        links: List<String>,
    ): Result<List<Server>> {
        if (links.isEmpty()) {
            clearServerCache()
            return Result.failure(Exception("No servers available"))
        }

        val servers = links.mapNotNull { link -> VlessUrlParser.parse(link) }
            .filterNot { it.isSentinel }
        if (servers.isEmpty()) {
            clearServerCache()
            return Result.failure(Exception("No servers available"))
        }

        val cachedById = serverDao.getAll().associateBy { it.id }
        val entities = servers.map { server ->
            val id = serverId(server)
            val cached = cachedById[id]
            server.toEntity(
                country = cached?.country.orEmpty(),
                isOnline = cached?.isOnline ?: true,
            )
        }
        val generation = refreshGeneration.incrementAndGet()
        serverDao.replaceAll(entities)
        prefsDataStore.setServerCacheOwner(shortUuid)
        enrichMetadataInBackground(shortUuid, generation, servers)
        return Result.success(entities.map { it.toDomain() })
    }

    private fun enrichMetadataInBackground(
        shortUuid: String,
        generation: Long,
        servers: List<Server>,
    ) {
        enrichmentScope.launch {
            try {
                val nodes = botApi.getNodes().response
                val countryByAddress = nodes.associate { it.address to it.countryCode }
                val disabledNodeIps = nodes
                    .filter { it.isDisabled || !it.isConnected }
                    .map { it.address }
                    .toSet()

                val enriched = coroutineScope {
                    servers.map { server ->
                        async {
                            val resolvedIp = try {
                                InetAddress.getByName(server.address).hostAddress
                            } catch (_: Exception) {
                                server.address
                            }
                            server.toEntity(
                                country = countryByAddress[server.address]
                                    ?: countryByAddress[resolvedIp]
                                    ?: "",
                                isOnline = resolvedIp !in disabledNodeIps,
                            )
                        }
                    }.awaitAll()
                }

                val currentShortUuid = sessionDao.getSession()?.shortUuid
                if (refreshGeneration.get() == generation && currentShortUuid == shortUuid) {
                    serverDao.replaceAll(enriched)
                    prefsDataStore.setServerCacheOwner(shortUuid)
                }
            } catch (error: Exception) {
                SafeDiagnostics.warn(TAG, "Node metadata refresh failed: ${SafeDiagnostics.failureCategory(error)}")
            }
        }
    }

    private suspend fun clearServerCache() {
        refreshGeneration.incrementAndGet()
        serverDao.deleteAll()
        prefsDataStore.clearServerCacheOwner()
    }

    suspend fun getServers(): List<Server> {
        return serverDao.getAll().map { it.toDomain() }.filterNot { it.isSentinel }
    }

    suspend fun clearServers() {
        clearServerCache()
    }

    suspend fun clearCachedServers() {
        clearServerCache()
    }

    private fun serverId(server: Server) = "${server.address}:${server.port}:${server.sni}"

    private fun Server.toEntity(
        country: String,
        isOnline: Boolean,
    ) = ServerEntity(
        id = serverId(this),
        name = name,
        address = address,
        port = port,
        uuid = uuid,
        flow = flow,
        security = security,
        sni = sni,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        network = network,
        path = path,
        mode = mode,
        spx = spx,
        country = country,
        isOnline = isOnline,
    )

    private fun ServerEntity.toDomain() = Server(
        id = id,
        name = name,
        address = address,
        port = port,
        uuid = uuid,
        flow = flow,
        security = security,
        sni = sni,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        network = network,
        path = path,
        mode = mode,
        spx = spx,
        country = country,
        isOnline = isOnline,
    )

    private companion object {
        const val TAG = "VpnRepository"
    }
}

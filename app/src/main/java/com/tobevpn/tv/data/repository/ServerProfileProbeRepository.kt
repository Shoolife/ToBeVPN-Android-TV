package com.tobevpn.tv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.util.SafeDiagnostics
import com.tobevpn.tv.util.diagnosticServerDescriptor
import com.tobevpn.tv.vpn.VpnConfig
import com.tobevpn.tv.vpn.XRayCore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import libv2ray.Libv2ray
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the complete VLESS/Xray profile after an explicit refresh. A
 * positive delay means an HTTP request travelled through transport and
 * TLS/REALITY; an open TCP port alone is not considered a successful server.
 */
@Singleton
class ServerProfileProbeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverQualityRepository: ServerQualityRepository,
    private val prefsDataStore: PrefsDataStore,
) {
    private val batchMutex = Mutex()
    private val cache = ConcurrentHashMap<CacheKey, TimedResult>()

    suspend fun measureProfileDelays(
        servers: List<Server>,
        force: Boolean,
        onResult: suspend (
            serverId: String,
            delayMs: Long,
            completed: Int,
            total: Int,
        ) -> Unit = { _, _, _, _ -> },
    ): Map<String, Long> = batchMutex.withLock {
        val candidates = servers.filter(Server::isAvailable).distinctBy(Server::id)
        if (candidates.isEmpty()) return@withLock emptyMap()

        val networkKey = currentPhysicalNetworkKey()
        val now = SystemClock.elapsedRealtime()
        val timeoutMs = prefsDataStore.getServerPingTimeoutSeconds() * MILLIS_PER_SECOND
        pruneCache(now)
        val results = ConcurrentHashMap<String, Long>()
        val uncached = mutableListOf<Server>()
        candidates.forEach { server ->
            val cached = cache[CacheKey(networkKey, server.id)]
                ?.takeIf { !force && it.isFresh(now, timeoutMs) }
            if (cached == null) {
                uncached += server
            } else {
                results[server.id] = cached.delayMs
                onResult(server.id, cached.delayMs, results.size, candidates.size)
            }
        }

        if (uncached.isNotEmpty()) {
            val tcpPings = serverQualityRepository.measurePings(uncached, force = true)
            val profilesToProbe = mutableListOf<Server>()
            uncached.forEach { server ->
                if ((tcpPings[server.id] ?: -1L) < 0L) {
                    recordResult(
                        networkKey,
                        server,
                        -1L,
                        timeoutMs,
                        results,
                        candidates.size,
                        onResult,
                    )
                } else {
                    profilesToProbe += server
                }
            }
            profilesToProbe.sortBy { tcpPings[it.id]?.takeIf { ping -> ping > 0L } ?: Long.MAX_VALUE }

            if (profilesToProbe.isNotEmpty()) {
                val coreReady = try {
                    withContext(Dispatchers.IO) { XRayCore.init(context) }
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    SafeDiagnostics.warn(
                        TAG,
                        "Server profile probe core init failed: " +
                            SafeDiagnostics.failureCategory(error),
                    )
                    false
                }
                if (!coreReady) {
                    profilesToProbe.forEach { server ->
                        recordResult(
                            networkKey,
                            server,
                            -1L,
                            timeoutMs,
                            results,
                            candidates.size,
                            onResult,
                        )
                    }
                } else {
                    val slots = Semaphore(MAX_CONCURRENT_PROFILE_PROBES)
                    coroutineScope {
                        profilesToProbe.map { server ->
                            async(Dispatchers.IO) {
                                slots.withPermit {
                                    recordResult(
                                        networkKey = networkKey,
                                        server = server,
                                        delayMs = measureFullProfile(server, timeoutMs),
                                        timeoutMs = timeoutMs,
                                        results = results,
                                        total = candidates.size,
                                        onResult = onResult,
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        }

        if (currentPhysicalNetworkKey() != networkKey) {
            candidates.filter { results.containsKey(it.id) }.forEach { server ->
                cache.remove(CacheKey(networkKey, server.id))
                results[server.id] = -1L
                onResult(server.id, -1L, results.size, candidates.size)
            }
            SafeDiagnostics.warn(TAG, "Server profile results discarded after network change")
        }

        val ordered = candidates.mapNotNull { server ->
            results[server.id]?.let { server.id to it }
        }.toMap()
        SafeDiagnostics.trace(
            TAG,
            "Xray profile probe completed: total=${candidates.size} tested=${ordered.size} " +
                "verified=${ordered.values.count { it > 0L }} " +
                "unverified=${ordered.values.count { it <= 0L }} " +
                "timeout_ms=$timeoutMs network=$networkKey",
        )
        ordered
    }

    private suspend fun measureFullProfile(server: Server, timeoutMs: Long): Long {
        val config = try {
            VpnConfig.buildOutboundDelayConfigJson(server)
        } catch (error: Exception) {
            SafeDiagnostics.warn(
                TAG,
                "Server profile config failed: ${diagnosticServerDescriptor(server)} " +
                    SafeDiagnostics.failureCategory(error),
            )
            return -1L
        }

        val measured = withTimeoutOrNull(timeoutMs * PROBE_TARGETS.size) {
            var result = -1L
            for (target in PROBE_TARGETS) {
                val targetResult = withTimeoutOrNull(timeoutMs) {
                    try {
                        withContext(Dispatchers.IO) {
                            Libv2ray.measureOutboundDelay(config, target)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        SafeDiagnostics.trace(
                            TAG,
                            "Server profile target failed: ${diagnosticServerDescriptor(server)} " +
                                SafeDiagnostics.failureCategory(error),
                        )
                        -1L
                    }
                } ?: -1L
                if (targetResult > 0L) {
                    result = targetResult
                    break
                }
            }
            result
        } ?: -1L

        SafeDiagnostics.trace(
            TAG,
            "Xray profile result: ${diagnosticServerDescriptor(server)} " +
                "verified=${measured > 0L} delay_ms=$measured timeout_ms=$timeoutMs",
        )
        return measured
    }

    private suspend fun recordResult(
        networkKey: Long,
        server: Server,
        delayMs: Long,
        timeoutMs: Long,
        results: ConcurrentHashMap<String, Long>,
        total: Int,
        onResult: suspend (String, Long, Int, Int) -> Unit,
    ) {
        val normalized = delayMs.takeIf { it > 0L } ?: -1L
        cache[CacheKey(networkKey, server.id)] = TimedResult(
            delayMs = normalized,
            measuredAtMs = SystemClock.elapsedRealtime(),
            timeoutMs = timeoutMs,
        )
        results[server.id] = normalized
        onResult(server.id, normalized, results.size, total)
    }

    @Suppress("DEPRECATION")
    private fun currentPhysicalNetworkKey(): Long {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NO_NETWORK_KEY
        return manager.allNetworks
            .mapNotNull { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                    ?: return@mapNotNull null
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) return@mapNotNull null
                val validated = if (
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) 1 else 0
                Triple(network.networkHandle, validated, capabilities.transportKey())
            }
            .maxWithOrNull(compareBy<Triple<Long, Int, Int>> { it.second }.thenBy { it.third })
            ?.first
            ?: NO_NETWORK_KEY
    }

    private fun NetworkCapabilities.transportKey(): Int = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 4
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 3
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
        else -> 1
    }

    private fun pruneCache(nowMs: Long) {
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries.removeAll { (_, value) ->
            nowMs - value.measuredAtMs > VERIFIED_CACHE_TTL_MS
        }
    }

    private data class CacheKey(val networkKey: Long, val serverId: String)

    private data class TimedResult(
        val delayMs: Long,
        val measuredAtMs: Long,
        val timeoutMs: Long,
    ) {
        fun isFresh(nowMs: Long, expectedTimeoutMs: Long): Boolean {
            if (timeoutMs != expectedTimeoutMs) return false
            val ttl = if (delayMs > 0L) VERIFIED_CACHE_TTL_MS else FAILED_CACHE_TTL_MS
            return nowMs - measuredAtMs in 0..ttl
        }
    }

    private companion object {
        const val TAG = "ServerProfileProbe"
        const val MAX_CONCURRENT_PROFILE_PROBES = 16
        const val MILLIS_PER_SECOND = 1_000L
        const val VERIFIED_CACHE_TTL_MS = 3L * 60L * 1_000L
        const val FAILED_CACHE_TTL_MS = 3L * 60L * 1_000L
        const val MAX_CACHE_ENTRIES = 200
        const val NO_NETWORK_KEY = -1L
        val PROBE_TARGETS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
    }
}

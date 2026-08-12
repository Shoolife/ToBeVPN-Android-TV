package com.tobevpn.tv.vpn

import com.tobevpn.tv.data.repository.serverConnectionIdentityKey
import com.tobevpn.tv.domain.model.Server

/** Ten-minute soft penalty for a server whose end-to-end tunnel validation failed. */
internal class RecentTunnelFailureRegistry(
    private val penaltyMs: Long = DEFAULT_PENALTY_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(penaltyMs > 0L)
        require(maxEntries > 0)
    }

    @Synchronized
    fun record(server: Server, nowMs: Long) {
        val key = serverConnectionIdentityKey(server)
        entries.remove(key)
        entries[key] = Entry(server, nowMs)
        while (entries.size > maxEntries) entries.remove(entries.keys.firstOrNull())
    }

    @Synchronized
    fun forget(server: Server) {
        entries.remove(serverConnectionIdentityKey(server))
    }

    @Synchronized
    fun penalisedServers(nowMs: Long): List<Server> {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val age = nowMs - iterator.next().value.failedAtMs
            if (age < 0L || age >= penaltyMs) iterator.remove()
        }
        return entries.values.map(Entry::server)
    }

    private data class Entry(val server: Server, val failedAtMs: Long)

    private companion object {
        const val DEFAULT_PENALTY_MS = 10L * 60L * 1_000L
        const val DEFAULT_MAX_ENTRIES = 32
    }
}

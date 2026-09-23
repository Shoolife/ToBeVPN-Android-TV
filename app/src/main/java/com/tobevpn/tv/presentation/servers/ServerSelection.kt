package com.tobevpn.tv.presentation.servers

import com.tobevpn.tv.domain.model.Server
import com.tobevpn.tv.presentation.serverDisplayName
import java.util.Locale

/** Technical endpoint ID used by the existing server cache. */
fun stableServerId(server: Server): String =
    "${server.address}:${server.port}:${server.sni}"

/**
 * Durable identity of a panel entry. Some special profiles rotate their
 * endpoint while retaining the same user-facing name; using only endpoint ID
 * makes a manual selection disappear after the next profile refresh.
 */
fun serverSelectionKey(server: Server): String =
    serverDisplayName(server.name, server.country)
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

private fun hasSelectedId(server: Server, selectedId: String?): Boolean =
    selectedId != null && (
        server.id == selectedId ||
            stableServerId(server) == selectedId ||
            server.uuid == selectedId
        )

fun isSelectedServer(
    server: Server,
    selectedId: String?,
    selectedKey: String?,
): Boolean =
    hasSelectedId(server, selectedId) ||
        (selectedKey != null && serverSelectionKey(server) == selectedKey)

fun resolveSelectedServer(
    servers: List<Server>,
    selectedId: String?,
    selectedKey: String?,
    allowFallback: Boolean,
): Server? {
    val available = servers.filter { it.isAvailable }
    if (selectedId == null && selectedKey == null) {
        return available.firstOrNull().takeIf { allowFallback }
    }

    // Prefer an exact technical match. The name key is deliberately the
    // fallback for a rotated endpoint and therefore must not override it.
    return available.firstOrNull { hasSelectedId(it, selectedId) }
        ?: selectedKey?.let { key ->
            available.firstOrNull { serverSelectionKey(it) == key }
        }
        ?: available.firstOrNull().takeIf { allowFallback }
}

/**
 * Preserve the panel order until the explicit end-to-end Xray check finishes.
 * Afterwards confirmed profiles are placed first by real delay, unmeasured
 * entries follow them, and failed/offline profiles stay at the bottom.
 */
fun sortVerifiedServersForDisplay(
    servers: List<Server>,
    pingsMeasured: Boolean,
): List<Server> {
    if (!pingsMeasured) return servers
    return servers.withIndex()
        .sortedWith(
            compareBy<IndexedValue<Server>> { indexed ->
                when {
                    !indexed.value.isAvailable || indexed.value.ping < 0L -> 2
                    indexed.value.ping == 0L -> 1
                    else -> 0
                }
            }.thenBy { indexed ->
                indexed.value.ping.takeIf { it > 0L } ?: Long.MAX_VALUE
            }.thenBy(IndexedValue<Server>::index),
        )
        .map(IndexedValue<Server>::value)
}

/**
 * Compose list identity must not include asynchronously enriched country or
 * ping metadata, otherwise TV focus is recreated while the user presses OK.
 */
internal fun serverListItemKey(server: Server): String = listOf(
    serverSelectionKey(server),
    server.address,
    server.port.toString(),
    server.uuid,
    server.sni,
    server.publicKey,
    server.shortId,
).joinToString("|")

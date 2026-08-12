package com.tobevpn.tv.data.repository

import com.tobevpn.tv.domain.model.Server

/** Candidate ordering for standard TV servers; recent failures are soft penalties. */
internal object ServerRecoveryCandidatePolicy {
    data class EndpointPreferenceTiers(
        val preferred: List<Server>,
        val fallback: List<Server>,
    )

    fun eligibleServers(
        servers: List<Server>,
        excludeServerId: String?,
        excludedServerIds: Collection<String> = emptyList(),
    ): List<Server> {
        val excludedIds = buildSet {
            excludeServerId?.let(::add)
            addAll(excludedServerIds)
        }
        return servers.filterNot { it.id in excludedIds }
    }

    fun endpointPreferenceTiers(
        servers: List<Server>,
        failedEndpointServers: Collection<Server>,
        penalisedProfiles: Collection<Server> = emptyList(),
    ): EndpointPreferenceTiers {
        if (servers.isEmpty()) return EndpointPreferenceTiers(servers, emptyList())
        val penalisedKeys = buildSet {
            failedEndpointServers.mapTo(this, ::serverConnectionIdentityKey)
            penalisedProfiles.mapTo(this, ::serverConnectionIdentityKey)
        }
        if (penalisedKeys.isEmpty()) return EndpointPreferenceTiers(servers, emptyList())
        val preferred = servers.filterNot { serverConnectionIdentityKey(it) in penalisedKeys }
        if (preferred.isEmpty()) return EndpointPreferenceTiers(servers, emptyList())
        return EndpointPreferenceTiers(
            preferred = preferred,
            fallback = servers.filter { serverConnectionIdentityKey(it) in penalisedKeys },
        )
    }
}

/** Standard panel aliases on one endpoint are interchangeable. */
internal fun serverConnectionIdentityKey(server: Server): String =
    "${server.address}:${server.port}:${server.sni}"

internal fun serverPingEndpointKey(server: Server): String = serverConnectionIdentityKey(server)

package com.tobevpn.tv.data.repository

import com.tobevpn.tv.domain.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRecoveryCandidatePolicyTest {
    @Test
    fun `automatic recovery never cycles back to an excluded id`() {
        val first = server("first", "first.example")
        val second = server("second", "second.example")

        assertEquals(
            listOf(second),
            ServerRecoveryCandidatePolicy.eligibleServers(
                servers = listOf(first, second),
                excludeServerId = first.id,
            ),
        )
    }

    @Test
    fun `failed endpoint is excluded even when panel exposes another id`() {
        val failed = server("failed", "shared.example")
        val alias = failed.copy(id = "alias", name = "Alias")
        val alternative = server("alternative", "other.example")

        assertEquals(
            listOf(alternative),
            ServerRecoveryCandidatePolicy.eligibleServers(
                servers = listOf(failed, alias, alternative),
                excludeServerId = failed.id,
                excludeEndpoint = failed,
            ),
        )
    }

    @Test
    fun `strict exclusion never silently restores the only failed server`() {
        val failed = server("failed", "failed.example")

        assertTrue(
            ServerRecoveryCandidatePolicy.eligibleServers(
                servers = listOf(failed),
                excludeServerId = failed.id,
            ).isEmpty(),
        )
    }

    @Test
    fun `failed endpoint remains a fallback when another endpoint exists`() {
        val failed = server("failed", "shared.example")
        val alias = failed.copy(id = "alias")
        val untried = server("untried", "other.example")

        val tiers = ServerRecoveryCandidatePolicy.endpointPreferenceTiers(
            servers = listOf(alias, untried),
            failedEndpointServers = listOf(failed),
        )

        assertEquals(listOf(untried), tiers.preferred)
        assertEquals(listOf(alias), tiers.fallback)
    }

    @Test
    fun `fully penalised pool still yields candidates`() {
        val first = server("first", "a.example")
        val second = server("second", "b.example")
        val tiers = ServerRecoveryCandidatePolicy.endpointPreferenceTiers(
            servers = listOf(first, second),
            failedEndpointServers = emptyList(),
            penalisedProfiles = listOf(first, second),
        )

        assertEquals(listOf(first, second), tiers.preferred)
        assertTrue(tiers.fallback.isEmpty())
    }

    private fun server(id: String, address: String) = Server(
        id = id,
        name = id,
        address = address,
        port = 443,
        uuid = "11111111-1111-4111-8111-111111111111",
        sni = "front.example",
    )
}

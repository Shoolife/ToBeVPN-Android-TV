package com.tobevpn.tv.vpn

import com.tobevpn.tv.domain.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTunnelFailureRegistryTest {
    private fun server(id: String, address: String = "node.example") = Server(
        id = id,
        name = id,
        address = address,
        port = 443,
        uuid = "uuid-$id",
    )

    @Test
    fun `failure remains penalised for the configured window`() {
        val registry = RecentTunnelFailureRegistry(penaltyMs = 10_000L)
        val failed = server("failed")
        registry.record(failed, 1_000L)

        assertEquals(listOf(failed), registry.penalisedServers(10_999L))
        assertTrue(registry.penalisedServers(11_000L).isEmpty())
    }

    @Test
    fun `healthy confirmation clears the penalty`() {
        val registry = RecentTunnelFailureRegistry(penaltyMs = 10_000L)
        val failed = server("failed")
        registry.record(failed, 1_000L)
        registry.forget(failed)

        assertTrue(registry.penalisedServers(2_000L).isEmpty())
    }

    @Test
    fun `registry keeps only newest bounded entries`() {
        val registry = RecentTunnelFailureRegistry(penaltyMs = 60_000L, maxEntries = 2)
        registry.record(server("one", "one.example"), 1L)
        registry.record(server("two", "two.example"), 2L)
        registry.record(server("three", "three.example"), 3L)

        assertEquals(listOf("two", "three"), registry.penalisedServers(4L).map(Server::id))
    }
}

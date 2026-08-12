package com.tobevpn.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTest {

    @Test
    fun legacyRealityFingerprintIsRepairedWithoutHidingServer() {
        val server = server().copy(fingerprint = "HelloAndroid_11_OkHttp")

        assertFalse(server.isXrayCompatible)
        assertTrue(server.isAvailable)
        assertTrue(server.isSelectable)
        assertTrue(server.isFingerprintRepaired)
    }

    @Test
    fun transportFieldsParticipateInTunnelIdentity() {
        val original = server()

        assertFalse(original.hasSameVpnConfig(original.copy(host = "cdn.example")))
        assertFalse(original.hasSameVpnConfig(original.copy(alpn = "h2")))
        assertFalse(original.hasSameVpnConfig(original.copy(headerType = "http")))
        assertFalse(original.hasSameVpnConfig(original.copy(serviceName = "edge")))
        assertFalse(original.hasSameVpnConfig(original.copy(extra = "{\"key\":true}")))
    }

    private fun server() = Server(
        id = "server-id",
        name = "Node",
        address = "node.example",
        port = 443,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
        security = "reality",
        sni = "front.example",
        publicKey = "public-key",
        shortId = "abcd",
    )
}

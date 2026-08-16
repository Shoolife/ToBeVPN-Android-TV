package com.tobevpn.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealityFingerprintPolicyTest {
    @Test
    fun `chrome reality profile gets exactly one firefox fallback`() {
        val server = server(fingerprint = "chrome")

        assertEquals(listOf("chrome", "firefox"), RealityFingerprintPolicy.candidates(server))
        assertEquals(
            "firefox",
            RealityFingerprintPolicy.nextCandidate(server, attempted = setOf("chrome")),
        )
        assertNull(
            RealityFingerprintPolicy.nextCandidate(
                server,
                attempted = setOf("chrome", "firefox"),
            ),
        )
    }

    @Test
    fun `legacy reality fingerprint starts with repaired chrome`() {
        val server = server(fingerprint = "helloandroid_11_okhttp")

        assertEquals("chrome", RealityFingerprintPolicy.primaryCandidate(server))
        assertEquals("chrome", RealityFingerprintPolicy.fingerprintForConfig(server, null))
    }

    @Test
    fun `non reality profile ignores override`() {
        val server = server(fingerprint = "chrome").copy(security = "tls")

        assertEquals(
            "chrome",
            RealityFingerprintPolicy.fingerprintForConfig(server, "firefox"),
        )
    }

    private fun server(fingerprint: String) = Server(
        id = "server-id",
        name = "Node",
        address = "node.example",
        port = 443,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
        security = "reality",
        sni = "front.example",
        fingerprint = fingerprint,
        publicKey = "public-key",
        shortId = "abcd",
    )
}

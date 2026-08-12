package com.tobevpn.tv.util

import com.tobevpn.tv.domain.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticServerDescriptorTest {
    @Test
    fun `descriptor is stable and excludes endpoint credentials`() {
        val server = testServer()
        val descriptor = diagnosticServerDescriptor(server)

        assertEquals(descriptor, diagnosticServerDescriptor(server))
        assertTrue(descriptor.contains("server_ref="))
        assertFalse(descriptor.contains(server.address))
        assertFalse(descriptor.contains(server.uuid))
        assertFalse(descriptor.contains(server.sni))
        assertFalse(descriptor.contains(server.publicKey))
    }

    @Test
    fun `descriptor reports fingerprint repaired for reality`() {
        val descriptor = diagnosticServerDescriptor(testServer().copy(fingerprint = "android"))

        assertTrue(descriptor.contains("fingerprint=chrome"))
        assertTrue(descriptor.contains("declared_fingerprint=android"))
    }

    private fun testServer() = Server(
        id = "server-id",
        name = "Test",
        address = "private.internal.example",
        port = 443,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
        sni = "front.internal.example",
        publicKey = "private-public-key",
        country = "NL",
    )
}

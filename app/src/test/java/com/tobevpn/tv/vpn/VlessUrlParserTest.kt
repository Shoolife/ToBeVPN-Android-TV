package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VlessUrlParserTest {

    @Test
    fun parsesUnescapedTvRemarkAndKeepsLiteralPlus() {
        val server = VlessUrlParser.parse(
            "vless://test-uuid@example.com:8443?security=reality&pbk=key+part#TV сервер 📺"
        )

        assertEquals("test-uuid", server?.uuid)
        assertEquals("example.com", server?.address)
        assertEquals(8443, server?.port)
        assertEquals("TV сервер 📺", server?.name)
        assertEquals("key+part", server?.publicKey)
    }

    @Test
    fun parsesBracketedIpv6Endpoint() {
        val server = VlessUrlParser.parse(
            "vless://test-uuid@[2001:db8::10]:443?security=reality#IPv6"
        )

        assertEquals("2001:db8::10", server?.address)
        assertEquals(443, server?.port)
        assertEquals("IPv6", server?.name)
    }

    @Test
    fun parsesModernTransportParametersAndNormalizesAliases() {
        val server = VlessUrlParser.parse(
            "vless://user@node.example:443?type=WebSocket&security=TLS" +
                "&host=cdn.example&alpn=h2%2Chttp%2F1.1&headerType=http" +
                "&serviceName=edge&extra=%7B%22noGRPCHeader%22%3Atrue%7D#Node",
        )

        requireNotNull(server)
        assertEquals("ws", server.network)
        assertEquals("tls", server.security)
        assertEquals("cdn.example", server.host)
        assertEquals("h2,http/1.1", server.alpn)
        assertEquals("http", server.headerType)
        assertEquals("edge", server.serviceName)
        assertEquals("{\"noGRPCHeader\":true}", server.extra)
    }

    @Test
    fun normalizesLegacyRealityFingerprintForXrayLookup() {
        val server = VlessUrlParser.parse(
            "vless://user@node.example:443?security=reality&fp=%20Android%20#Node",
        )

        requireNotNull(server)
        assertEquals("android", server.fingerprint)
        assertEquals("chrome", server.effectiveFingerprint)
    }

    @Test
    fun rejectsMalformedAuthority() {
        assertNull(VlessUrlParser.parse("vless://missing-at.example.com#Broken"))
        assertNull(VlessUrlParser.parse("https://test-uuid@example.com"))
    }
}

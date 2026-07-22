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
    fun rejectsMalformedAuthority() {
        assertNull(VlessUrlParser.parse("vless://missing-at.example.com#Broken"))
        assertNull(VlessUrlParser.parse("https://test-uuid@example.com"))
    }
}

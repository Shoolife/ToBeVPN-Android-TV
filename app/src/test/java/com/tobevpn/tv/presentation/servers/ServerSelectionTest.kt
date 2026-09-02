package com.tobevpn.tv.presentation.servers

import com.tobevpn.tv.domain.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSelectionTest {

    @Test
    fun `flagless server stays selected when endpoint rotates`() {
        val old = server(
            address = "old-gateway.example",
            uuid = "11111111-1111-1111-1111-111111111111",
        )
        val refreshed = server(
            address = "new-gateway.example",
            uuid = "22222222-2222-2222-2222-222222222222",
        )

        assertNotEquals(stableServerId(old), stableServerId(refreshed))
        assertEquals(serverSelectionKey(old), serverSelectionKey(refreshed))
        assertEquals(
            refreshed,
            resolveSelectedServer(
                servers = listOf(refreshed),
                selectedId = stableServerId(old),
                selectedKey = serverSelectionKey(old),
                allowFallback = false,
            ),
        )
        assertTrue(
            isSelectedServer(
                server = refreshed,
                selectedId = stableServerId(old),
                selectedKey = serverSelectionKey(old),
            ),
        )
    }

    @Test
    fun `manual selection never falls back to an unrelated server`() {
        val available = server(name = "Другой сервер")

        assertEquals(
            null,
            resolveSelectedServer(
                servers = listOf(available),
                selectedId = "missing",
                selectedKey = "missing",
                allowFallback = false,
            ),
        )
    }

    @Test
    fun `list key is stable when country metadata arrives`() {
        val initial = server(country = "")
        val enriched = initial.copy(country = "RU", ping = 42)

        assertEquals(serverListItemKey(initial), serverListItemKey(enriched))
        assertFalse(serverListItemKey(initial).contains("|RU|"))
    }

    private fun server(
        name: String = "Обход БС (работает медленно)",
        address: String = "gateway.example",
        uuid: String = "550e8400-e29b-41d4-a716-446655440000",
        country: String = "",
    ) = Server(
        id = "$address:443:front.example",
        name = name,
        address = address,
        port = 443,
        uuid = uuid,
        security = "reality",
        sni = "front.example",
        publicKey = "public-key",
        shortId = "abcd",
        country = country,
    )
}

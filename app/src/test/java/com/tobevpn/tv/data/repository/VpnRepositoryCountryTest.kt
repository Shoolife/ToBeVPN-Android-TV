package com.tobevpn.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnRepositoryCountryTest {

    @Test
    fun `empty metadata keeps previously known country`() {
        assertEquals(
            "DE",
            resolveRefreshedServerCountry(
                serverName = "Private node",
                freshCountry = "",
                fallbackCountry = "DE",
            ),
        )
    }

    @Test
    fun `country in display name is ready before metadata finishes`() {
        assertEquals(
            "NL",
            resolveRefreshedServerCountry(
                serverName = "Нидерланды 2",
                freshCountry = null,
                fallbackCountry = null,
            ),
        )
    }

    @Test
    fun `leading profile flag is retained without metadata`() {
        assertEquals(
            "SE",
            resolveRefreshedServerCountry(
                serverName = "🇸🇪 Stockholm",
                freshCountry = null,
                fallbackCountry = null,
            ),
        )
    }

    @Test
    fun `fresh metadata fills a generic server label`() {
        assertEquals(
            "FI",
            resolveRefreshedServerCountry(
                serverName = "Private node",
                freshCountry = "FI",
                fallbackCountry = null,
            ),
        )
    }
}

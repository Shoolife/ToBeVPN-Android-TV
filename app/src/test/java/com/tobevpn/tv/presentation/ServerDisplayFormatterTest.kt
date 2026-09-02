package com.tobevpn.tv.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDisplayFormatterTest {

    @Test
    fun ukTokenDoesNotCorruptUkraineOrBaku() {
        assertEquals("UA", serverCountryCodeForUi("", "Ukraine 1"))
        assertEquals("", serverCountryCodeForUi("", "Baku 1"))
        assertEquals("GB", serverCountryCodeForUi("", "UK 1"))
    }

    @Test
    fun ruRouteLabelsUseRussianFlagWithoutNodeMetadata() {
        assertEquals("RU", serverCountryCodeForUi("", "RU -> WORLD [EKB]"))
        assertEquals("RU", serverCountryCodeForUi("", "RU -> WORLD 4 [🏳️ БС] [x2]"))
        assertEquals("🇷🇺", countryFlagForUi("", "RU -> WORLD [EKB]"))
    }
}

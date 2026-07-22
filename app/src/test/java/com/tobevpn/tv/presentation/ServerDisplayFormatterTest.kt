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
}

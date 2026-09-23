package com.tobevpn.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPingTimeoutTest {
    @Test
    fun `normalizes ping timeout into supported range`() {
        assertEquals(5, normalizeServerPingTimeoutSeconds(1))
        assertEquals(7, normalizeServerPingTimeoutSeconds(7))
        assertEquals(15, normalizeServerPingTimeoutSeconds(99))
    }
}

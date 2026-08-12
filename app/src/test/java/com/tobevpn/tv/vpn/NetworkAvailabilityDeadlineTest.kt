package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAvailabilityDeadlineTest {
    @Test
    fun expiresAtExactBoundary() {
        val deadline = NetworkAvailabilityDeadline(1_000L, 15_000L)
        assertFalse(deadline.isExpired(15_999L))
        assertTrue(deadline.isExpired(16_000L))
    }

    @Test
    fun capsNextCheckAtRemainingTime() {
        val deadline = NetworkAvailabilityDeadline(1_000L, 15_000L)
        assertEquals(5_000L, deadline.nextCheckDelayMs(2_000L, 5_000L))
        assertEquals(1_000L, deadline.nextCheckDelayMs(15_000L, 5_000L))
    }
}

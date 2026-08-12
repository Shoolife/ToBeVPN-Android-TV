package com.tobevpn.tv.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResumeRateLimiterTest {
    @Test
    fun limitsAttemptsInsideWindowAndRecoversAfterWindow() {
        val limiter = NetworkResumeRateLimiter(maxAttempts = 2, windowMs = 1_000L)
        assertTrue(limiter.tryAcquire(1_000L))
        assertTrue(limiter.tryAcquire(1_500L))
        assertFalse(limiter.tryAcquire(1_999L))
        assertTrue(limiter.tryAcquire(2_001L))
    }
}

package com.tobevpn.tv.domain

import com.tobevpn.tv.domain.model.UsageInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInfoTest {
    @Test
    fun `zero limits mean unlimited, not exhausted`() {
        assertFalse(UsageInfo(bytesUsed = 5_000_000_000, timeUsedSeconds = 90_000).isExhausted)
    }

    @Test
    fun `trial with a traffic limit and no time limit is usable until traffic runs out`() {
        // Plan sync stores the server traffic limit with a zero time limit.
        val limit = 3L * 1024 * 1024 * 1024
        assertFalse(UsageInfo(bytesUsed = 0, bytesLimit = limit, timeUsedSeconds = 7_200).isExhausted)
        assertTrue(UsageInfo(bytesUsed = limit, bytesLimit = limit, timeUsedSeconds = 7_200).isExhausted)
    }

    @Test
    fun `time limit applies only when set`() {
        assertTrue(UsageInfo(timeUsedSeconds = 3_600, timeLimitSeconds = 3_600).isExhausted)
        assertFalse(UsageInfo(timeUsedSeconds = 3_599, timeLimitSeconds = 3_600).isExhausted)
    }
}

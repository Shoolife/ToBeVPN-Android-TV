package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelRecoveryPolicyTest {
    @Test
    fun manualSelectionRetriesOnlyDuringStartup() {
        assertEquals(1, TunnelRecoveryPolicy.maxAttempts(false, duringStartup = true))
        assertEquals(0, TunnelRecoveryPolicy.maxAttempts(false, duringStartup = false))
    }

    @Test
    fun automaticSelectionHasTwoBoundedAttempts() {
        assertEquals(2, TunnelRecoveryPolicy.maxAttempts(true, duringStartup = true))
        assertEquals(2, TunnelRecoveryPolicy.maxAttempts(true, duringStartup = false))
    }
}

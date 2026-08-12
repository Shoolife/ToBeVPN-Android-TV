package com.tobevpn.tv.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelLivenessPolicyTest {
    @Test
    fun acceptsOnlyEnoughRecentDownlinkFromSameLoopBeforeProbe() {
        assertTrue(
            TunnelLivenessPolicy.hasSufficientRecentDownlinkBeforeProbe(
                probeStartedAtMs = 30_000L,
                probeLoopGeneration = 2,
                lastDownlinkAtMs = 20_000L,
                lastDownlinkLoopGeneration = 2,
                downlinkBytes = 20L * 1024L,
            ),
        )
        assertFalse(
            TunnelLivenessPolicy.hasSufficientRecentDownlinkBeforeProbe(
                probeStartedAtMs = 30_000L,
                probeLoopGeneration = 2,
                lastDownlinkAtMs = 20_000L,
                lastDownlinkLoopGeneration = 1,
                downlinkBytes = 20L * 1024L,
            ),
        )
    }
}

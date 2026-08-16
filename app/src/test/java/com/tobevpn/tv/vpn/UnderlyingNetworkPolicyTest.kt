package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlyingNetworkPolicyTest {
    @Test
    fun `complete physical network loss has a fifteen second deadline`() {
        assertEquals(
            15_000L,
            UnderlyingNetworkPolicy.teardownTimeoutMs(
                UnderlyingNetworkAvailability.UNAVAILABLE,
            ),
        )
        assertFalse(
            UnderlyingNetworkPolicy.canAttemptTunnelProbe(
                UnderlyingNetworkAvailability.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `unvalidated physical network can prove tunnel liveness`() {
        assertNull(
            UnderlyingNetworkPolicy.teardownTimeoutMs(
                UnderlyingNetworkAvailability.UNVALIDATED,
            ),
        )
        assertTrue(
            UnderlyingNetworkPolicy.canAttemptTunnelProbe(
                UnderlyingNetworkAvailability.UNVALIDATED,
            ),
        )
    }

    @Test
    fun `validated network needs no teardown deadline`() {
        assertNull(
            UnderlyingNetworkPolicy.teardownTimeoutMs(
                UnderlyingNetworkAvailability.VALIDATED,
            ),
        )
        assertTrue(
            UnderlyingNetworkPolicy.canAttemptTunnelProbe(
                UnderlyingNetworkAvailability.VALIDATED,
            ),
        )
    }
}

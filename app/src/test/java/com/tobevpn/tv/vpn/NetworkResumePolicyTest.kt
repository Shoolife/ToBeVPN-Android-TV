package com.tobevpn.tv.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResumePolicyTest {
    @Test
    fun resumesOnlyTheSameOwnedRequestOnValidatedNetwork() {
        assertTrue(
            NetworkResumePolicy.shouldResume(
                expectedRequest = 4,
                currentRequest = 4,
                hasNetworkTimeoutError = true,
                sameServer = true,
                availability = UnderlyingNetworkAvailability.VALIDATED,
            ),
        )
        assertFalse(
            NetworkResumePolicy.shouldResume(
                expectedRequest = 4,
                currentRequest = 5,
                hasNetworkTimeoutError = true,
                sameServer = true,
                availability = UnderlyingNetworkAvailability.VALIDATED,
            ),
        )
    }

    @Test
    fun resumesOnAnUnvalidatedButPresentPhysicalNetwork() {
        assertTrue(
            NetworkResumePolicy.shouldResume(
                expectedRequest = 4,
                currentRequest = 4,
                hasNetworkTimeoutError = true,
                sameServer = true,
                availability = UnderlyingNetworkAvailability.UNVALIDATED,
            ),
        )
        assertFalse(
            NetworkResumePolicy.shouldResume(
                expectedRequest = 4,
                currentRequest = 4,
                hasNetworkTimeoutError = true,
                sameServer = true,
                availability = UnderlyingNetworkAvailability.UNAVAILABLE,
            ),
        )
    }
}

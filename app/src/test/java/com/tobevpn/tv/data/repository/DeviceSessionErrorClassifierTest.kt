package com.tobevpn.tv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSessionErrorClassifierTest {

    @Test
    fun expiredAccessTokenDoesNotLogUserOut() {
        assertFalse(
            isConfirmedRemoteDeviceUnlink(
                httpCode = 401,
                message = "HTTP 401 Unauthorized",
                responseBody = "Invalid or expired access token",
            )
        )
    }

    @Test
    fun explicitCurrentDeviceUnlinkIsConfirmed() {
        assertTrue(
            isConfirmedRemoteDeviceUnlink(
                httpCode = 403,
                message = "HTTP 403 Forbidden",
                responseBody = "Current device session is not linked to a Telegram user",
            )
        )
    }

    @Test
    fun genericForbiddenDoesNotLogUserOut() {
        assertFalse(
            isConfirmedRemoteDeviceUnlink(
                httpCode = 403,
                message = "HTTP 403 Forbidden",
                responseBody = "Forbidden",
            )
        )
    }
}

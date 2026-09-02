package com.tobevpn.tv.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceUnlinkRequestDtoTest {

    private val gson = Gson()

    @Test
    fun currentDeviceRequestOmitsDeviceId() {
        assertEquals("{}", gson.toJson(DeviceUnlinkRequestDto()))
    }

    @Test
    fun otherDeviceRequestKeepsExplicitDeviceId() {
        assertEquals(
            "{\"device_id\":\"other-device\"}",
            gson.toJson(DeviceUnlinkRequestDto(deviceId = "other-device")),
        )
    }
}

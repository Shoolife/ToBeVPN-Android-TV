package com.tobevpn.tv.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoNullabilityTest {

    private val gson = Gson()

    @Test
    fun omittedCollectionsAreSafeAtCallSites() {
        val devices = gson.fromJson(
            """{"max_devices":3}""",
            LinkedDevicesDto::class.java,
        )
        val plans = gson.fromJson(
            """{"telegram_id":1}""",
            PurchasePlansDto::class.java,
        )
        val release = gson.fromJson(
            """{"tag_name":"v1.0.20","html_url":"https://example.invalid"}""",
            GithubReleaseDto::class.java,
        )

        assertTrue(devices.devices.orEmpty().isEmpty())
        assertTrue(plans.plans.orEmpty().isEmpty())
        assertTrue(release.assets.orEmpty().isEmpty())
    }
}

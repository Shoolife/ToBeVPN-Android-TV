package com.tobevpn.tv.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tobevpn.tv.domain.model.Server
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnConfigInstrumentedTest {
    @Test
    fun xrayNativeLibraryLoadsForCurrentTvAbi() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        XRayCore.init(context)
        val version = XRayCore.getVersion()

        assertTrue(version.isNotBlank())
        assertNotEquals("unknown", version)
    }

    @Test
    fun realityFingerprintOverrideIsWrittenToTheActualXrayConfig() {
        val stream = JSONObject(
            VpnConfig.buildConfigJson(
                server = server(),
                realityFingerprintOverride = "firefox",
            ),
        )
            .getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("streamSettings")

        assertEquals(
            "firefox",
            stream.getJSONObject("realitySettings").getString("fingerprint"),
        )
    }

    private fun server() = Server(
        id = "server-id",
        name = "Test server",
        address = "node.example",
        port = 443,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
        flow = "xtls-rprx-vision",
        security = "reality",
        sni = "front.example",
        fingerprint = "chrome",
        publicKey = "public-key",
        shortId = "abcd",
        network = "tcp",
        spx = "/",
        country = "NL",
    )
}

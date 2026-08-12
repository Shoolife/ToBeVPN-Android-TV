package com.tobevpn.tv.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SafeDiagnosticsTest {
    @Test
    fun `failure summary keeps app frame without exception message`() {
        val error = IOException("https://secret.example/token").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "com.tobevpn.tv.vpn.VpnConnectionManager",
                    "probeTunnelOnce",
                    "VpnConnectionManager.kt",
                    123,
                ),
            )
        }

        val summary = SafeDiagnostics.failureSummary(error)
        assertTrue(summary.contains("category=IO"))
        assertTrue(summary.contains("VpnConnectionManager.probeTunnelOnce:123"))
        assertFalse(summary.contains("secret.example"))
    }
}

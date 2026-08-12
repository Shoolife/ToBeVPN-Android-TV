package com.tobevpn.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiagnosticLogPolicyTest {
    @Test
    fun `journal filenames are stable and history is bounded`() {
        assertEquals(
            "ToBeVPN-diagnostic-2026-08-12.log",
            DiagnosticLogPolicy.fileName(LocalDate.of(2026, 8, 12)),
        )
        val names = (1..9).map { day ->
            DiagnosticLogPolicy.fileName(LocalDate.of(2026, 8, day))
        }
        assertEquals(
            setOf(
                DiagnosticLogPolicy.fileName(LocalDate.of(2026, 8, 1)),
                DiagnosticLogPolicy.fileName(LocalDate.of(2026, 8, 2)),
            ),
            DiagnosticLogPolicy.filesBeyondHistoryLimit(names, maxFiles = 7),
        )
    }

    @Test
    fun `sanitizer removes credentials and account identifiers`() {
        val sanitized = DiagnosticLogPolicy.sanitizeMessage(
            "https://vpn.example/path?token=abc Authorization: Bearer secret_value_123 " +
                "user@example.com 8907735498 550e8400-e29b-41d4-a716-446655440000 192.168.1.2",
        )

        assertFalse(sanitized.contains("vpn.example"))
        assertFalse(sanitized.contains("secret_value_123"))
        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("8907735498"))
        assertFalse(sanitized.contains("550e8400"))
        assertFalse(sanitized.contains("192.168.1.2"))
    }

    @Test
    fun `sanitizer preserves operational traffic metrics`() {
        val sanitized = DiagnosticLogPolicy.sanitizeMessage(
            "session_kib=1234567 uplink_kib=7654321 downlink_kib=9876543 " +
                "duration_s=1234567 link_up_kbps=1000000 telegram_id=8907735498",
        )

        assertTrue(sanitized.contains("session_kib=1234567"))
        assertTrue(sanitized.contains("uplink_kib=7654321"))
        assertTrue(sanitized.contains("downlink_kib=9876543"))
        assertTrue(sanitized.contains("duration_s=1234567"))
        assertTrue(sanitized.contains("link_up_kbps=1000000"))
        assertFalse(sanitized.contains("8907735498"))
    }
}

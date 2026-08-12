package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class DownlinkEvidenceAccumulatorTest {
    @Test
    fun `evidence is consumed exactly once`() {
        val accumulator = DownlinkEvidenceAccumulator()
        accumulator.record(10_000L, 7, 32_000L)

        assertEquals(DownlinkEvidence(10_000L, 7, 32_000L), accumulator.consume())
        assertEquals(DownlinkEvidence(), accumulator.consume())
    }

    @Test
    fun `same loop accumulates and replacement loop starts a new interval`() {
        val accumulator = DownlinkEvidenceAccumulator()
        accumulator.record(10_000L, 7, 8_000L)
        accumulator.record(11_000L, 7, 9_000L)
        assertEquals(DownlinkEvidence(11_000L, 7, 17_000L), accumulator.consume())

        accumulator.record(12_000L, 8, 5_000L)
        assertEquals(DownlinkEvidence(12_000L, 8, 5_000L), accumulator.consume())
    }
}

package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelHealthEpisodeTest {
    private val loop = 7
    private val probeAt = 1_000_000L

    private fun evidence(
        bytes: Long,
        ageMs: Long,
        loopGeneration: Int = loop,
    ) = DownlinkEvidence(
        observedAtMs = if (bytes <= 0L) 0L else probeAt - ageMs,
        loopGeneration = if (bytes <= 0L) -1 else loopGeneration,
        bytes = bytes,
    )

    private fun TunnelHealthEpisode.probe(
        healthy: Boolean,
        evidence: DownlinkEvidence = evidence(0L, 0L),
    ) = onProbeResult(
        probeHealthy = healthy,
        probeStartedAtMs = probeAt,
        probeLoopGeneration = loop,
        evidence = evidence,
    )

    @Test
    fun `one failed cycle is not a tunnel failure verdict`() {
        val episode = TunnelHealthEpisode()

        val decision = episode.probe(false, evidence(2_048L, 68L))

        assertTrue(decision is TunnelHealthDecision.AwaitingConfirmation)
        assertEquals(1, episode.consecutiveFailures)
    }

    @Test
    fun `two separate failed cycles confirm a dead tunnel`() {
        val episode = TunnelHealthEpisode()

        episode.probe(false)
        val decision = episode.probe(false)

        assertTrue(decision is TunnelHealthDecision.ConfirmedFailure)
        assertEquals(2, episode.consecutiveFailures)
    }

    @Test
    fun `healthy cycle resets a pending failure`() {
        val episode = TunnelHealthEpisode()

        episode.probe(false)
        assertEquals(TunnelHealthDecision.Healthy, episode.probe(true))
        assertTrue(episode.probe(false) is TunnelHealthDecision.AwaitingConfirmation)
    }

    @Test
    fun `recent application downlink overrides a failed probe`() {
        val episode = TunnelHealthEpisode()

        val decision = episode.probe(false, evidence(64L * 1024L, 5_000L))

        assertTrue(decision is TunnelHealthDecision.LivenessOverride)
        assertEquals(0, episode.consecutiveFailures)
    }

    @Test
    fun `downlink from an old xray loop cannot override`() {
        val episode = TunnelHealthEpisode()

        val decision = episode.probe(
            false,
            evidence(64L * 1024L, 5_000L, loopGeneration = loop - 1),
        )

        assertTrue(decision is TunnelHealthDecision.AwaitingConfirmation)
    }
}

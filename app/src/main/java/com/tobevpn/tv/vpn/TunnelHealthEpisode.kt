package com.tobevpn.tv.vpn

internal sealed interface TunnelHealthDecision {
    data object Healthy : TunnelHealthDecision

    data class LivenessOverride(
        val downlinkBytes: Long,
        val downlinkAgeMs: Long,
    ) : TunnelHealthDecision

    data class AwaitingConfirmation(
        val failures: Int,
        val required: Int,
        val downlinkBytes: Long,
        val downlinkAgeMs: Long,
    ) : TunnelHealthDecision

    data class ConfirmedFailure(
        val failures: Int,
        val downlinkBytes: Long,
        val downlinkAgeMs: Long,
    ) : TunnelHealthDecision
}

/** State for one watchdog job; a reload or reconnect starts a fresh episode. */
internal class TunnelHealthEpisode {
    var consecutiveFailures: Int = 0
        private set

    fun onProbeResult(
        probeHealthy: Boolean,
        probeStartedAtMs: Long,
        probeLoopGeneration: Int,
        evidence: DownlinkEvidence,
    ): TunnelHealthDecision {
        val downlinkAgeMs = if (evidence.observedAtMs <= 0L) {
            -1L
        } else {
            (probeStartedAtMs - evidence.observedAtMs).coerceAtLeast(0L)
        }
        if (probeHealthy) {
            consecutiveFailures = 0
            return TunnelHealthDecision.Healthy
        }

        val livenessProven = TunnelLivenessPolicy.hasSufficientRecentDownlinkBeforeProbe(
            probeStartedAtMs = probeStartedAtMs,
            probeLoopGeneration = probeLoopGeneration,
            lastDownlinkAtMs = evidence.observedAtMs,
            lastDownlinkLoopGeneration = evidence.loopGeneration,
            downlinkBytes = evidence.bytes,
        )
        if (livenessProven) {
            consecutiveFailures = 0
            return TunnelHealthDecision.LivenessOverride(
                downlinkBytes = evidence.bytes,
                downlinkAgeMs = downlinkAgeMs,
            )
        }

        consecutiveFailures++
        return if (TunnelHealthVerdictPolicy.isConfirmedFailure(consecutiveFailures)) {
            TunnelHealthDecision.ConfirmedFailure(
                failures = consecutiveFailures,
                downlinkBytes = evidence.bytes,
                downlinkAgeMs = downlinkAgeMs,
            )
        } else {
            TunnelHealthDecision.AwaitingConfirmation(
                failures = consecutiveFailures,
                required = TunnelHealthVerdictPolicy.REQUIRED_CONSECUTIVE_FAILURES,
                downlinkBytes = evidence.bytes,
                downlinkAgeMs = downlinkAgeMs,
            )
        }
    }
}

package com.tobevpn.tv.vpn

/** Only recent downlink from before a probe may override a failed HTTP probe. */
internal object TunnelLivenessPolicy {
    const val RECENT_DOWNLINK_GRACE_MS = 25_000L
    const val MIN_DOWNLINK_BYTES = 16L * 1024L

    fun hasSufficientRecentDownlinkBeforeProbe(
        probeStartedAtMs: Long,
        probeLoopGeneration: Int,
        lastDownlinkAtMs: Long,
        lastDownlinkLoopGeneration: Int,
        downlinkBytes: Long,
    ): Boolean {
        if (probeStartedAtMs <= 0L || lastDownlinkAtMs <= 0L) return false
        if (downlinkBytes < MIN_DOWNLINK_BYTES) return false
        if (probeLoopGeneration <= 0 || lastDownlinkLoopGeneration != probeLoopGeneration) return false
        if (lastDownlinkAtMs > probeStartedAtMs) return false
        return probeStartedAtMs - lastDownlinkAtMs <= RECENT_DOWNLINK_GRACE_MS
    }
}

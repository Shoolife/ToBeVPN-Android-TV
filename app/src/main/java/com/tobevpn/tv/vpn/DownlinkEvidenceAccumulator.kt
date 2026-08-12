package com.tobevpn.tv.vpn

import java.util.concurrent.atomic.AtomicReference

internal data class DownlinkEvidence(
    val observedAtMs: Long = 0L,
    val loopGeneration: Int = -1,
    val bytes: Long = 0L,
)

/** One-shot non-probe downlink evidence consumed by each watchdog cycle. */
internal class DownlinkEvidenceAccumulator {
    private val evidence = AtomicReference(DownlinkEvidence())

    fun record(observedAtMs: Long, loopGeneration: Int, bytes: Long) {
        if (observedAtMs <= 0L || loopGeneration <= 0 || bytes <= 0L) return
        evidence.updateAndGet { previous ->
            DownlinkEvidence(
                observedAtMs = observedAtMs,
                loopGeneration = loopGeneration,
                bytes = if (previous.loopGeneration == loopGeneration) {
                    if (bytes >= Long.MAX_VALUE - previous.bytes) Long.MAX_VALUE
                    else previous.bytes + bytes
                } else {
                    bytes
                },
            )
        }
    }

    fun consume(): DownlinkEvidence = evidence.getAndSet(DownlinkEvidence())

    fun reset() {
        evidence.set(DownlinkEvidence())
    }
}

package com.tobevpn.tv.vpn

import java.util.concurrent.atomic.AtomicInteger

/** Prevents watchdog HTTP response bytes from validating that same watchdog. */
internal class ProbeDownlinkEvidenceGate {
    private val state = AtomicInteger(IDLE)

    fun onProbeStarted() {
        state.set(IN_FLIGHT)
    }

    fun onProbeFinished() {
        state.set(NEEDS_POST_PROBE_DRAIN)
    }

    fun reset() {
        state.set(IDLE)
    }

    fun suppressEvidenceForCurrentDrain(): Boolean {
        while (true) {
            when (state.get()) {
                IN_FLIGHT -> return true
                NEEDS_POST_PROBE_DRAIN -> {
                    if (state.compareAndSet(NEEDS_POST_PROBE_DRAIN, IDLE)) return true
                }
                else -> return false
            }
        }
    }

    private companion object {
        const val IDLE = 0
        const val IN_FLIGHT = 1
        const val NEEDS_POST_PROBE_DRAIN = 2
    }
}

package com.tobevpn.tv.vpn

/** Requires failures from two separate watchdog cycles before recovery. */
internal object TunnelHealthVerdictPolicy {
    const val REQUIRED_CONSECUTIVE_FAILURES = 2

    fun isConfirmedFailure(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= REQUIRED_CONSECUTIVE_FAILURES
}

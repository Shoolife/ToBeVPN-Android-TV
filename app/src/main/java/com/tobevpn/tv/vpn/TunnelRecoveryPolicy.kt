package com.tobevpn.tv.vpn

/** Manual selection never restarts itself in the background; automatic selection is bounded. */
internal object TunnelRecoveryPolicy {
    const val MANUAL_WATCHDOG_MAX_ATTEMPTS = 0
    const val MANUAL_STARTUP_MAX_ATTEMPTS = 1
    const val AUTOMATIC_MAX_ATTEMPTS = 2

    fun maxAttempts(automaticSelection: Boolean, duringStartup: Boolean): Int = when {
        automaticSelection -> AUTOMATIC_MAX_ATTEMPTS
        duringStartup -> MANUAL_STARTUP_MAX_ATTEMPTS
        else -> MANUAL_WATCHDOG_MAX_ATTEMPTS
    }

    fun canAttempt(
        currentAttempts: Int,
        automaticSelection: Boolean,
        duringStartup: Boolean,
    ): Boolean = currentAttempts < maxAttempts(automaticSelection, duringStartup)

    /**
     * AUTO gets one separately bounded REALITY fingerprint retry before its
     * alternative-server budget is used. In MANUAL it replaces the existing
     * one same-server startup retry, keeping manual recovery just as bounded.
     */
    fun fingerprintRetryConsumesAttempt(automaticSelection: Boolean): Boolean =
        !automaticSelection
}

package com.tobevpn.tv.vpn

/** Monotonic deadline for waiting on a usable physical network. */
internal class NetworkAvailabilityDeadline(
    private val startedAtMs: Long,
    private val timeoutMs: Long,
) {
    init {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
    }

    fun isExpired(nowMs: Long): Boolean = elapsedMs(nowMs) >= timeoutMs

    fun nextCheckDelayMs(nowMs: Long, maximumDelayMs: Long): Long {
        require(maximumDelayMs > 0L) { "maximumDelayMs must be positive" }
        val remainingMs = (timeoutMs - elapsedMs(nowMs)).coerceAtLeast(0L)
        return minOf(maximumDelayMs, remainingMs).coerceAtLeast(1L)
    }

    private fun elapsedMs(nowMs: Long): Long = (nowMs - startedAtMs).coerceAtLeast(0L)
}

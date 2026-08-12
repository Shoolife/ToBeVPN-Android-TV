package com.tobevpn.tv.vpn

internal enum class UnderlyingNetworkAvailability {
    VALIDATED,
    UNVALIDATED,
    UNAVAILABLE,
}

internal object UnderlyingNetworkPolicy {
    const val NO_NETWORK_TIMEOUT_MS = 15_000L
    const val UNVALIDATED_NETWORK_TIMEOUT_MS = 45_000L

    fun timeoutMs(availability: UnderlyingNetworkAvailability): Long = when (availability) {
        UnderlyingNetworkAvailability.VALIDATED -> 0L
        UnderlyingNetworkAvailability.UNVALIDATED -> UNVALIDATED_NETWORK_TIMEOUT_MS
        UnderlyingNetworkAvailability.UNAVAILABLE -> NO_NETWORK_TIMEOUT_MS
    }
}

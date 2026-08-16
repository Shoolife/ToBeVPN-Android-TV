package com.tobevpn.tv.vpn

internal enum class UnderlyingNetworkAvailability {
    VALIDATED,
    UNVALIDATED,
    UNAVAILABLE,
}

internal object UnderlyingNetworkPolicy {
    const val NO_NETWORK_TIMEOUT_MS = 15_000L

    /**
     * Android validation only proves access to the system check endpoint. An
     * unvalidated Wi-Fi/Ethernet network may still carry a healthy VPN tunnel,
     * so probes are allowed whenever a physical network exists.
     */
    fun canAttemptTunnelProbe(availability: UnderlyingNetworkAvailability): Boolean =
        availability != UnderlyingNetworkAvailability.UNAVAILABLE

    /** Only complete physical-network loss is allowed to tear the tunnel down. */
    fun teardownTimeoutMs(availability: UnderlyingNetworkAvailability): Long? =
        NO_NETWORK_TIMEOUT_MS.takeIf {
            availability == UnderlyingNetworkAvailability.UNAVAILABLE
        }
}

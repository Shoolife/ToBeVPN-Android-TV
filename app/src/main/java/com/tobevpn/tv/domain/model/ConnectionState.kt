package com.tobevpn.tv.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(
        val message: String,
        /**
         * The TV closed the VPN in the background (the service or the whole
         * app was killed, e.g. by TCL Safety Guard), not a connection error.
         * The main screen then explains which TV settings to change.
         */
        val stoppedBySystem: Boolean = false,
    ) : ConnectionState
}

package com.tobevpn.tv.domain.model

data class Server(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val flow: String = "",
    val security: String = "reality",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val network: String = "tcp",
    val path: String = "",
    val mode: String = "",
    val spx: String = "",
    val country: String = "",
    val isOnline: Boolean = true,
    val ping: Long = -1,
) {
    /**
     * True for the panel's "subscription expired" placeholder server. Its
     * uuid is all-zeros and the address points nowhere — handing it to xray
     * would SIGSEGV the native loop, so it must never become the selected /
     * connected server.
     */
    val isSentinel: Boolean
        get() = uuid == "00000000-0000-0000-0000-000000000000" ||
            address.isBlank() ||
            address == "127.0.0.1" ||
            address == "0.0.0.0" ||
            name.contains("ИСТЕКЛА", ignoreCase = true) ||
            name.contains("EXPIRED", ignoreCase = true) ||
            name.contains("истекла", ignoreCase = true)
}

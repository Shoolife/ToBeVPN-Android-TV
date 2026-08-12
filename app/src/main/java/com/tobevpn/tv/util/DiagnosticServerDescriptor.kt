package com.tobevpn.tv.util

import com.tobevpn.tv.domain.model.Server
import java.security.MessageDigest

/** Correlates server events without exporting endpoints or credentials. */
internal fun diagnosticServerDescriptor(server: Server): String {
    val source = "${server.address}:${server.port}:${server.sni}:${server.publicKey}"
    val reference = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
    }.getOrElse { source.hashCode().toUInt().toString(16) }
    return buildString {
        append("server_ref=").append(reference)
        append(" country=").append(server.country.diagnosticToken())
        append(" transport=").append(server.network.diagnosticToken())
        append(" security=").append(server.security.diagnosticToken())
        append(" fingerprint=").append(server.effectiveFingerprint.diagnosticToken())
        if (server.isFingerprintRepaired) {
            append(" declared_fingerprint=").append(server.fingerprint.diagnosticToken())
        }
        append(" panel_online=").append(server.isOnline)
    }
}

private fun String.diagnosticToken(): String = trim()
    .replace(Regex("[^A-Za-z0-9_-]"), "_")
    .take(20)
    .ifBlank { "UNKNOWN" }

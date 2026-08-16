package com.tobevpn.tv.domain.model

import java.util.Locale

/** Bounded browser-fingerprint order for REALITY startup validation. */
internal object RealityFingerprintPolicy {
    const val CHROME = "chrome"
    const val FIREFOX = "firefox"

    fun candidates(server: Server): List<String> {
        if (!server.security.trim().equals("reality", ignoreCase = true)) return emptyList()
        val primary = canonicalBrowserName(
            server.effectiveFingerprint.trim().ifBlank { CHROME },
        )
        val fallback = if (normalize(primary) == FIREFOX) CHROME else FIREFOX
        return listOf(primary, fallback)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(::normalize)
    }

    fun primaryCandidate(server: Server): String? = candidates(server).firstOrNull()

    fun nextCandidate(server: Server, attempted: Set<String>): String? {
        val normalizedAttempts = attempted.mapTo(hashSetOf(), ::normalize)
        return candidates(server).firstOrNull { normalize(it) !in normalizedAttempts }
    }

    fun fingerprintForConfig(server: Server, realityOverride: String?): String {
        if (!server.security.trim().equals("reality", ignoreCase = true)) {
            return server.effectiveFingerprint
        }
        val requested = realityOverride?.trim()?.takeIf(String::isNotEmpty)
            ?: server.effectiveFingerprint
        return canonicalBrowserName(requested)
    }

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun canonicalBrowserName(value: String): String = when (normalize(value)) {
        CHROME -> CHROME
        FIREFOX -> FIREFOX
        else -> value.trim()
    }
}

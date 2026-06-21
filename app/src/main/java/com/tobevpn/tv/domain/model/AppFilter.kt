package com.tobevpn.tv.domain.model

enum class AppFilterMode { OFF, WHITELIST, BLACKLIST }

data class AppFilterState(
    val mode: AppFilterMode,
    val selectedPackages: Set<String>,
)

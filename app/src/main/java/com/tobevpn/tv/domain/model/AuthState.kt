package com.tobevpn.tv.domain.model

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class Authenticated(
        val telegramId: Long,
        val plan: UserPlan,
        val planExpiresAt: Long? = null,
        val planDisplayName: String? = null,
        val isAdminProfile: Boolean = false,
    ) : AuthState
}

enum class UserPlan {
    FREE_TRIAL,
    PAID,
    EXPIRED,
    ADMIN,
}

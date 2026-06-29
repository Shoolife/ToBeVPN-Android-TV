package com.tobevpn.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey
    val deviceId: String,
    val authState: String = "UNAUTHENTICATED",
    val telegramId: Long? = null,
    val userPlan: String = "FREE_TRIAL",
    val planDisplayName: String? = null,
    val planExpiresAt: Long? = null,
    val shortUuid: String? = null,
    val panelUserUuid: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessExpiresAt: Long? = null,
    val refreshExpiresAt: Long? = null,
    val isLinked: Boolean = false,
    val isAdminProfile: Boolean = false,
)

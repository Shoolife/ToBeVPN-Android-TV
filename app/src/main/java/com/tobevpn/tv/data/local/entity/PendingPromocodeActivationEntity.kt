package com.tobevpn.tv.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "pending_promocode_activations",
    primaryKeys = ["telegramId", "code"],
    indices = [Index(value = ["requestId"], unique = true)],
)
data class PendingPromocodeActivationEntity(
    val telegramId: Long,
    val code: String,
    val requestId: String,
    val createdAt: Long,
)

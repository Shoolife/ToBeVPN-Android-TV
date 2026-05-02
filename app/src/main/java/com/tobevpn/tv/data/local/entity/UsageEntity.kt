package com.tobevpn.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey
    val id: Int = 1, // singleton row
    val bytesUsed: Long = 0,
    val bytesLimit: Long = 1_073_741_824, // 1 GB
    val timeUsedSeconds: Long = 0,
    val timeLimitSeconds: Long = 3600, // 1 hour
    val lastConnectedAt: Long? = null,
)

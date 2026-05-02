package com.tobevpn.tv.domain.model

data class UsageInfo(
    val bytesUsed: Long = 0,
    val bytesLimit: Long = 1_073_741_824, // 1 GB
    val timeUsedSeconds: Long = 0,
    val timeLimitSeconds: Long = 3600, // 1 hour
) {
    val bytesRemaining: Long get() = (bytesLimit - bytesUsed).coerceAtLeast(0)
    val timeRemainingSeconds: Long get() = (timeLimitSeconds - timeUsedSeconds).coerceAtLeast(0)
    val isExhausted: Boolean get() = bytesRemaining <= 0 || timeRemainingSeconds <= 0
    val trafficProgress: Float get() = if (bytesLimit > 0) (bytesUsed.toFloat() / bytesLimit).coerceIn(0f, 1f) else 0f
    val timeProgress: Float get() = if (timeLimitSeconds > 0) (timeUsedSeconds.toFloat() / timeLimitSeconds).coerceIn(0f, 1f) else 0f
}

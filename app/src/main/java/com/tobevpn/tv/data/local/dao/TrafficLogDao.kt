package com.tobevpn.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tobevpn.tv.data.local.entity.TrafficLogEntity
import kotlinx.coroutines.flow.Flow

data class TrafficStat(
    val period: Long,
    val totalBytes: Long,
    val totalSeconds: Long,
    val sessions: Int,
)

@Dao
interface TrafficLogDao {

    @Insert
    suspend fun insert(log: TrafficLogEntity)

    // Hourly stats for a specific day
    @Query("""
        SELECT
            (timestamp / 3600) * 3600 AS period,
            SUM(bytesUsed) AS totalBytes,
            SUM(timeUsedSeconds) AS totalSeconds,
            COUNT(*) AS sessions
        FROM traffic_log
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getHourlyStats(dayStart: Long, dayEnd: Long): Flow<List<TrafficStat>>

    // Daily stats for a date range (timezone-aware grouping)
    @Query("""
        SELECT
            ((timestamp + :tzOffsetSec) / 86400) * 86400 - :tzOffsetSec AS period,
            SUM(bytesUsed) AS totalBytes,
            SUM(timeUsedSeconds) AS totalSeconds,
            COUNT(*) AS sessions
        FROM traffic_log
        WHERE timestamp >= :weekStart AND timestamp < :weekEnd
        GROUP BY ((timestamp + :tzOffsetSec) / 86400)
        ORDER BY period ASC
    """)
    fun getDailyStats(weekStart: Long, weekEnd: Long, tzOffsetSec: Long): Flow<List<TrafficStat>>

    // Weekly stats for a date range
    @Query("""
        SELECT
            ((timestamp - :monthStart) / 604800) * 604800 + :monthStart AS period,
            SUM(bytesUsed) AS totalBytes,
            SUM(timeUsedSeconds) AS totalSeconds,
            COUNT(*) AS sessions
        FROM traffic_log
        WHERE timestamp >= :monthStart AND timestamp < :monthEnd
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getWeeklyStats(monthStart: Long, monthEnd: Long): Flow<List<TrafficStat>>

    @Query("SELECT COALESCE(SUM(bytesUsed), 0) FROM traffic_log")
    fun getTotalBytes(): Flow<Long>
}

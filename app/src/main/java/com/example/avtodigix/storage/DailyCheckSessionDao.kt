package com.example.avtodigix.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DailyCheckSessionDao {
    @Insert
    suspend fun insert(session: DailyCheckSessionEntity): Long

    @Query(
        """
        SELECT * FROM daily_check_sessions
        WHERE vehicleId = :vehicleId
        ORDER BY finishedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentSessions(vehicleId: String, limit: Int): List<DailyCheckSessionEntity>

    @Query(
        """
        SELECT * FROM daily_check_sessions
        WHERE vehicleId = :vehicleId AND success = 1
        ORDER BY finishedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentSuccessfulSessions(vehicleId: String, limit: Int): List<DailyCheckSessionEntity>

    @Query(
        """
        SELECT * FROM daily_check_sessions
        WHERE vehicleId = :vehicleId AND success = 1
        ORDER BY finishedAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLastSuccessfulSession(vehicleId: String): DailyCheckSessionEntity?
}

package com.example.avtodigix.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WifiScanSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: WifiScanSnapshotEntity): Long

    @Query("SELECT * FROM wifi_scan_snapshots ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<WifiScanSnapshotEntity>

    @Query("SELECT * FROM wifi_scan_snapshots ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatest(): WifiScanSnapshotEntity?

    @Query("DELETE FROM wifi_scan_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wifi_scan_snapshots")
    suspend fun clear()
}

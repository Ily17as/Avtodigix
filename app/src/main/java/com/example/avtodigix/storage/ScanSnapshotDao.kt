package com.example.avtodigix.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanSnapshotDao {
    @Insert
    suspend fun insert(snapshot: ScanSnapshotEntity): Long

    @Query("SELECT * FROM scan_snapshots ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<ScanSnapshotEntity>

    @Query("SELECT * FROM scan_snapshots WHERE id = :id")
    suspend fun getById(id: Long): ScanSnapshotEntity?

    @Query("DELETE FROM scan_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_snapshots")
    suspend fun clearAll()
}

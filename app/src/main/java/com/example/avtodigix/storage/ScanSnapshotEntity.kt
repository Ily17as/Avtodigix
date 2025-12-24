package com.example.avtodigix.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_snapshots")
data class ScanSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val keyMetrics: Map<String, Double>,
    val dtcList: List<String>
)

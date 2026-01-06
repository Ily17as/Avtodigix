package com.example.avtodigix.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "scan_snapshots")
@TypeConverters(ScanSnapshotConverters::class)
data class ScanSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val keyMetrics: Map<String, Double>,
    val dtcList: List<String>
)

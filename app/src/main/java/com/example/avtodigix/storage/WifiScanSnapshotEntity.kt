package com.example.avtodigix.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_scan_snapshots")
data class WifiScanSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val host: String,
    val port: Int,
    val responseFormat: WifiResponseFormat,
    val keyMetrics: Map<String, Double>,
    val dtcList: List<String>
)

package com.example.avtodigix.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "daily_check_sessions",
    indices = [
        Index(value = ["vehicleId", "finishedAtMillis"]),
        Index(value = ["vehicleId", "success", "finishedAtMillis"])
    ]
)
@TypeConverters(ScanSnapshotConverters::class)
data class DailyCheckSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val vehicleId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val success: Boolean,
    val trafficLightStatus: String,
    val keyMetrics: Map<String, Double>,
    val rawDtcList: List<String>,
    val hasNewDtc: Boolean,
    val scannerId: String?,
    val vin: String?
)

package com.example.avtodigix.storage

import com.example.avtodigix.domain.CheckSession
import com.example.avtodigix.domain.TrafficLightStatus

data class DailyCheckSession(
    val id: Long = 0,
    val sessionId: String,
    val vehicleId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val success: Boolean,
    val trafficLightStatus: TrafficLightStatus,
    val keyMetrics: Map<String, Double>,
    val rawDtcList: List<String>,
    val hasNewDtc: Boolean,
    val scannerId: String? = null,
    val vin: String? = null
)

fun DailyCheckSession.toEntity(): DailyCheckSessionEntity {
    return DailyCheckSessionEntity(
        id = id,
        sessionId = sessionId,
        vehicleId = vehicleId,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        success = success,
        trafficLightStatus = trafficLightStatus.name,
        keyMetrics = keyMetrics,
        rawDtcList = rawDtcList,
        hasNewDtc = hasNewDtc,
        scannerId = scannerId,
        vin = vin
    )
}

fun DailyCheckSessionEntity.toModel(): DailyCheckSession {
    return DailyCheckSession(
        id = id,
        sessionId = sessionId,
        vehicleId = vehicleId,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        success = success,
        trafficLightStatus = runCatching { TrafficLightStatus.valueOf(trafficLightStatus) }
            .getOrDefault(TrafficLightStatus.YELLOW),
        keyMetrics = keyMetrics,
        rawDtcList = rawDtcList,
        hasNewDtc = hasNewDtc,
        scannerId = scannerId,
        vin = vin
    )
}

fun CheckSession.toStorageModel(): DailyCheckSession {
    return DailyCheckSession(
        id = id,
        sessionId = sessionId,
        vehicleId = vehicleId,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        success = success,
        trafficLightStatus = trafficLightStatus,
        keyMetrics = keyMetrics,
        rawDtcList = rawDtcList,
        hasNewDtc = hasNewDtc,
        scannerId = scannerId,
        vin = vin
    )
}

fun DailyCheckSession.toDomainModel(): CheckSession {
    return CheckSession(
        id = id,
        sessionId = sessionId,
        vehicleId = vehicleId,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        success = success,
        trafficLightStatus = trafficLightStatus,
        keyMetrics = keyMetrics,
        rawDtcList = rawDtcList,
        hasNewDtc = hasNewDtc,
        scannerId = scannerId,
        vin = vin
    )
}

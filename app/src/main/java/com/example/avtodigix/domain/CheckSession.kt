package com.example.avtodigix.domain

/**
 * Доменная модель результата Daily Check с минимальным набором сырых данных.
 */
data class CheckSession(
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

/**
 * Приоритет: VIN -> стабильный идентификатор адаптера/авто -> unknown.
 */
fun resolveVehicleId(vin: String?, stableDeviceOrCarId: String?): String {
    val normalizedVin = vin?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    if (normalizedVin != null) return normalizedVin

    val normalizedStableId = stableDeviceOrCarId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return normalizedStableId ?: UNKNOWN_VEHICLE_ID
}

private const val UNKNOWN_VEHICLE_ID = "unknown_vehicle"

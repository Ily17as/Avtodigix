package com.example.avtodigix.dailycheck.baseline

/**
 * Baseline автомобиля по метрикам Daily Check.
 *
 * @property sourceSessionCount количество успешных сессий, использованных в расчёте.
 * @property metricBaselines baseline-значения только для метрик, которые удалось собрать.
 * @property missingMetrics метрики, не встреченные ни в одной из сессий источника.
 */
data class VehicleBaseline(
    val vehicleId: String,
    val computedAtMillis: Long,
    val sourceSessionCount: Int,
    val metricBaselines: Map<String, MetricBaseline>,
    val missingMetrics: Set<String>
) {
    val hasHistory: Boolean
        get() = sourceSessionCount > 0

    val isEarlyBaseline: Boolean
        get() = sourceSessionCount in 1 until RECOMMENDED_SESSION_COUNT

    val isPartial: Boolean
        get() = missingMetrics.isNotEmpty() || metricBaselines.values.any { it.sampleCount < sourceSessionCount }

    fun metric(metricKey: String): MetricBaseline? = metricBaselines[metricKey]

    companion object {
        const val RECOMMENDED_SESSION_COUNT: Int = 7

        fun empty(vehicleId: String, computedAtMillis: Long): VehicleBaseline {
            return VehicleBaseline(
                vehicleId = vehicleId,
                computedAtMillis = computedAtMillis,
                sourceSessionCount = 0,
                metricBaselines = emptyMap(),
                missingMetrics = emptySet()
            )
        }
    }
}

data class MetricBaseline(
    val metricKey: String,
    val mean: Double,
    val min: Double,
    val max: Double,
    val sampleCount: Int
)

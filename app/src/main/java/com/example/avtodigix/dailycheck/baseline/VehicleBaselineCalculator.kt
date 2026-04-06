package com.example.avtodigix.dailycheck.baseline

import com.example.avtodigix.domain.CheckSession
import com.example.avtodigix.storage.DailyCheckSessionRepository

/**
 * Слой вычисления baseline по последним успешным Daily Check сессиям.
 * Источник: до 7 последних успешных сессий для конкретного vehicleId.
 *
 * Реализация использует on-read стратегию с in-memory cache.
 */
class VehicleBaselineCalculator(
    private val sessionRepository: DailyCheckSessionRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val historyLimit: Int = VehicleBaseline.RECOMMENDED_SESSION_COUNT
) {
    private val baselineCache = mutableMapOf<String, CachedBaseline>()

    suspend fun getBaseline(vehicleId: String): VehicleBaseline {
        val sessions = sessionRepository.getRecentSuccessfulSessions(vehicleId, historyLimit)
        val signature = sessions.signature()

        val cached = baselineCache[vehicleId]
        if (cached != null && cached.signature == signature) {
            return cached.baseline
        }

        val baseline = calculate(vehicleId, sessions)
        baselineCache[vehicleId] = CachedBaseline(signature = signature, baseline = baseline)
        return baseline
    }

    fun invalidate(vehicleId: String) {
        baselineCache.remove(vehicleId)
    }

    fun clearCache() {
        baselineCache.clear()
    }

    internal fun calculate(vehicleId: String, successfulSessions: List<CheckSession>): VehicleBaseline {
        if (successfulSessions.isEmpty()) {
            return VehicleBaseline.empty(vehicleId = vehicleId, computedAtMillis = nowProvider())
        }

        val metricToValues = linkedMapOf<String, MutableList<Double>>()
        val allMetricKeys = linkedSetOf<String>()

        successfulSessions.forEach { session ->
            allMetricKeys += session.keyMetrics.keys
            session.keyMetrics.forEach { (metricKey, metricValue) ->
                if (metricValue.isFinite()) {
                    metricToValues.getOrPut(metricKey) { mutableListOf() }.add(metricValue)
                }
            }
        }

        val metricBaselines = metricToValues
            .mapValues { (metricKey, values) ->
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 0.0
                val mean = values.average()
                MetricBaseline(
                    metricKey = metricKey,
                    mean = mean,
                    min = min,
                    max = max,
                    sampleCount = values.size
                )
            }
            .toSortedMap()

        val missingMetrics = allMetricKeys.filter { key -> key !in metricBaselines.keys }.toSet()

        return VehicleBaseline(
            vehicleId = vehicleId,
            computedAtMillis = nowProvider(),
            sourceSessionCount = successfulSessions.size,
            metricBaselines = metricBaselines,
            missingMetrics = missingMetrics
        )
    }

    private data class CachedBaseline(
        val signature: String,
        val baseline: VehicleBaseline
    )
}

private fun List<CheckSession>.signature(): String {
    if (isEmpty()) return "empty"
    return joinToString(separator = "|") { session ->
        "${session.id}:${session.finishedAtMillis}:${session.success}:${session.keyMetrics.hashCode()}"
    }
}

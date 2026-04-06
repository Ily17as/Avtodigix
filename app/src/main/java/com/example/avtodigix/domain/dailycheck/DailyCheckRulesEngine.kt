package com.example.avtodigix.domain.dailycheck

import kotlin.math.absoluteValue
import kotlin.math.max

enum class DailyCheckCardId {
    BATTERY_START,
    ENGINE_IDLE,
    COOLING_WARMUP,
    CHARGING,
    OBD_ERRORS
}

enum class DailyCheckCardStatus {
    OK,
    ATTENTION,
    ERROR,
    INSUFFICIENT_DATA
}

enum class OverallStatus {
    GOOD,
    ATTENTION,
    CRITICAL
}

enum class DriveStatus {
    OK,
    ATTENTION,
    NEW_DTC,
    INSUFFICIENT_DATA
}

enum class TopChangeType {
    NEW_ERROR,
    REPEATED_WORSENING,
    NEW_INSTABILITY,
    IMPROVEMENT
}

data class DailyCheckInput(
    val batteryStartVoltage: Double?,
    val engineIdleRpm: Int?,
    val warmupDurationSeconds: Int?,
    val chargingVoltage: Double?,
    val activeDtcs: Set<String> = emptySet()
)

data class DailyCheckCardResult(
    val id: DailyCheckCardId,
    val status: DailyCheckCardStatus,
    val penalty: Int,
    val details: String
)

data class TopChange(
    val cardId: DailyCheckCardId,
    val type: TopChangeType,
    val details: String
)

data class DailyCheckRulesResult(
    val cards: List<DailyCheckCardResult>,
    val overallScore: Int,
    val overallStatus: OverallStatus,
    val driveStatus: DriveStatus,
    val topChanges: List<TopChange>,
    val newDtcs: Set<String>
)

object DailyCheckRulesEngine {
    fun evaluate(
        current: DailyCheckInput,
        previous: DailyCheckInput? = null
    ): DailyCheckRulesResult {
        val cards = listOf(
            evaluateBatteryStart(current.batteryStartVoltage),
            evaluateEngineIdle(current.engineIdleRpm),
            evaluateCoolingWarmup(current.warmupDurationSeconds),
            evaluateCharging(current.chargingVoltage),
            evaluateObdErrors(current.activeDtcs)
        )

        val overallScore = (MAX_SCORE - cards.sumOf { it.penalty }).coerceIn(0, MAX_SCORE)
        val overallStatus = resolveOverallStatus(overallScore)

        val insufficientDataPresent = cards.any { it.status == DailyCheckCardStatus.INSUFFICIENT_DATA }
        val attentionPresent = cards.any {
            it.status == DailyCheckCardStatus.ATTENTION || it.status == DailyCheckCardStatus.ERROR
        }

        val newDtcs = if (previous == null) {
            current.activeDtcs
        } else {
            current.activeDtcs - previous.activeDtcs
        }

        val driveStatus = when {
            insufficientDataPresent -> DriveStatus.INSUFFICIENT_DATA
            newDtcs.isNotEmpty() -> DriveStatus.NEW_DTC
            attentionPresent -> DriveStatus.ATTENTION
            else -> DriveStatus.OK
        }

        val topChanges = resolveTopChanges(current = current, previous = previous)

        return DailyCheckRulesResult(
            cards = cards,
            overallScore = overallScore,
            overallStatus = overallStatus,
            driveStatus = driveStatus,
            topChanges = topChanges,
            newDtcs = newDtcs
        )
    }

    private fun evaluateBatteryStart(voltage: Double?): DailyCheckCardResult {
        if (voltage == null) {
            return insufficient(DailyCheckCardId.BATTERY_START, PENALTY_INSUFFICIENT)
        }
        val status = when {
            voltage < 11.8 -> DailyCheckCardStatus.ERROR
            voltage < 12.2 -> DailyCheckCardStatus.ATTENTION
            else -> DailyCheckCardStatus.OK
        }
        val penalty = when (status) {
            DailyCheckCardStatus.ERROR -> PENALTY_BATTERY_ERROR
            DailyCheckCardStatus.ATTENTION -> PENALTY_BATTERY_ATTENTION
            else -> 0
        }
        return DailyCheckCardResult(
            id = DailyCheckCardId.BATTERY_START,
            status = status,
            penalty = penalty,
            details = "start_voltage=${format(voltage)}V"
        )
    }

    private fun evaluateEngineIdle(rpm: Int?): DailyCheckCardResult {
        if (rpm == null) {
            return insufficient(DailyCheckCardId.ENGINE_IDLE, PENALTY_INSUFFICIENT)
        }
        val status = when {
            rpm < 550 || rpm > 1000 -> DailyCheckCardStatus.ERROR
            rpm in 550..649 || rpm in 901..1000 -> DailyCheckCardStatus.ATTENTION
            else -> DailyCheckCardStatus.OK
        }
        val penalty = when (status) {
            DailyCheckCardStatus.ERROR -> PENALTY_ENGINE_ERROR
            DailyCheckCardStatus.ATTENTION -> PENALTY_ENGINE_ATTENTION
            else -> 0
        }
        return DailyCheckCardResult(
            id = DailyCheckCardId.ENGINE_IDLE,
            status = status,
            penalty = penalty,
            details = "idle_rpm=$rpm"
        )
    }

    private fun evaluateCoolingWarmup(seconds: Int?): DailyCheckCardResult {
        if (seconds == null) {
            return insufficient(DailyCheckCardId.COOLING_WARMUP, PENALTY_INSUFFICIENT)
        }
        val status = when {
            seconds > 900 -> DailyCheckCardStatus.ERROR
            seconds in 601..900 -> DailyCheckCardStatus.ATTENTION
            else -> DailyCheckCardStatus.OK
        }
        val penalty = when (status) {
            DailyCheckCardStatus.ERROR -> PENALTY_COOLING_ERROR
            DailyCheckCardStatus.ATTENTION -> PENALTY_COOLING_ATTENTION
            else -> 0
        }
        return DailyCheckCardResult(
            id = DailyCheckCardId.COOLING_WARMUP,
            status = status,
            penalty = penalty,
            details = "warmup=${seconds}s"
        )
    }

    private fun evaluateCharging(voltage: Double?): DailyCheckCardResult {
        if (voltage == null) {
            return insufficient(DailyCheckCardId.CHARGING, PENALTY_INSUFFICIENT)
        }
        val status = when {
            voltage < 13.2 || voltage > 15.0 -> DailyCheckCardStatus.ERROR
            voltage < 13.6 || voltage > 14.8 -> DailyCheckCardStatus.ATTENTION
            else -> DailyCheckCardStatus.OK
        }
        val penalty = when (status) {
            DailyCheckCardStatus.ERROR -> PENALTY_CHARGING_ERROR
            DailyCheckCardStatus.ATTENTION -> PENALTY_CHARGING_ATTENTION
            else -> 0
        }
        return DailyCheckCardResult(
            id = DailyCheckCardId.CHARGING,
            status = status,
            penalty = penalty,
            details = "charge_voltage=${format(voltage)}V"
        )
    }

    private fun evaluateObdErrors(activeDtcs: Set<String>): DailyCheckCardResult {
        val status = when {
            activeDtcs.size >= 2 -> DailyCheckCardStatus.ERROR
            activeDtcs.size == 1 -> DailyCheckCardStatus.ATTENTION
            else -> DailyCheckCardStatus.OK
        }
        val penalty = when (status) {
            DailyCheckCardStatus.ERROR -> PENALTY_OBD_ERROR
            DailyCheckCardStatus.ATTENTION -> PENALTY_OBD_ATTENTION
            else -> 0
        }
        return DailyCheckCardResult(
            id = DailyCheckCardId.OBD_ERRORS,
            status = status,
            penalty = penalty,
            details = "dtc_count=${activeDtcs.size}"
        )
    }

    private fun resolveTopChanges(current: DailyCheckInput, previous: DailyCheckInput?): List<TopChange> {
        if (previous == null) return emptyList()

        val battery = detectChange(
            cardId = DailyCheckCardId.BATTERY_START,
            previousValue = previous.batteryStartVoltage,
            currentValue = current.batteryStartVoltage,
            evaluator = ::evaluateBatteryStart,
            biggerIsWorse = false,
            metricName = "start_voltage"
        )
        val idle = detectChange(
            cardId = DailyCheckCardId.ENGINE_IDLE,
            previousValue = previous.engineIdleRpm,
            currentValue = current.engineIdleRpm,
            evaluator = ::evaluateEngineIdle,
            biggerIsWorse = null,
            metricName = "idle_rpm"
        ) { value -> (value - 750).absoluteValue }
        val warmup = detectChange(
            cardId = DailyCheckCardId.COOLING_WARMUP,
            previousValue = previous.warmupDurationSeconds,
            currentValue = current.warmupDurationSeconds,
            evaluator = ::evaluateCoolingWarmup,
            biggerIsWorse = true,
            metricName = "warmup_s"
        )
        val charging = detectChange(
            cardId = DailyCheckCardId.CHARGING,
            previousValue = previous.chargingVoltage,
            currentValue = current.chargingVoltage,
            evaluator = ::evaluateCharging,
            biggerIsWorse = null,
            metricName = "charging_voltage"
        ) { value -> max((13.6 - value).absoluteValue, (value - 14.8).absoluteValue) }
        val obd = detectObdChange(previous = previous.activeDtcs, current = current.activeDtcs)

        return listOfNotNull(battery, idle, warmup, charging, obd)
            .sortedWith(compareBy<TopChange> { it.type.priority }.thenBy { it.cardId.ordinal })
            .take(MAX_TOP_CHANGES)
    }

    private fun detectObdChange(previous: Set<String>, current: Set<String>): TopChange? {
        return when {
            (current - previous).isNotEmpty() -> TopChange(
                cardId = DailyCheckCardId.OBD_ERRORS,
                type = TopChangeType.NEW_ERROR,
                details = "new_dtc=${(current - previous).sorted().joinToString(",")}"
            )

            current.size > previous.size -> TopChange(
                cardId = DailyCheckCardId.OBD_ERRORS,
                type = TopChangeType.REPEATED_WORSENING,
                details = "dtc_count=${previous.size}->${current.size}"
            )

            current.size < previous.size -> TopChange(
                cardId = DailyCheckCardId.OBD_ERRORS,
                type = TopChangeType.IMPROVEMENT,
                details = "dtc_count=${previous.size}->${current.size}"
            )

            else -> null
        }
    }

    private fun <T : Number> detectChange(
        cardId: DailyCheckCardId,
        previousValue: T?,
        currentValue: T?,
        evaluator: (T?) -> DailyCheckCardResult,
        biggerIsWorse: Boolean?,
        metricName: String,
        distanceFromOptimal: ((Double) -> Double)? = null
    ): TopChange? {
        val previousStatus = evaluator(previousValue).status
        val currentStatus = evaluator(currentValue).status
        val previousSeverity = previousStatus.severity
        val currentSeverity = currentStatus.severity

        if (previousSeverity < 0 || currentSeverity < 0) return null

        if (currentSeverity > previousSeverity && currentSeverity == DailyCheckCardStatus.ERROR.severity) {
            return TopChange(cardId, TopChangeType.NEW_ERROR, "$metricName=${format(currentValue)}")
        }

        if (currentSeverity > previousSeverity) {
            return TopChange(cardId, TopChangeType.NEW_INSTABILITY, "$metricName=${format(currentValue)}")
        }

        if (currentSeverity == previousSeverity && currentSeverity > DailyCheckCardStatus.OK.severity) {
            val worsened = isRepeatedWorsening(
                previousValue = previousValue,
                currentValue = currentValue,
                biggerIsWorse = biggerIsWorse,
                distanceFromOptimal = distanceFromOptimal
            )
            if (worsened) {
                return TopChange(
                    cardId = cardId,
                    type = TopChangeType.REPEATED_WORSENING,
                    details = "$metricName=${format(previousValue)}->${format(currentValue)}"
                )
            }
        }

        if (currentSeverity < previousSeverity) {
            return TopChange(cardId, TopChangeType.IMPROVEMENT, "$metricName=${format(currentValue)}")
        }

        return null
    }

    private fun <T : Number> isRepeatedWorsening(
        previousValue: T?,
        currentValue: T?,
        biggerIsWorse: Boolean?,
        distanceFromOptimal: ((Double) -> Double)?
    ): Boolean {
        val previous = previousValue?.toDouble() ?: return false
        val current = currentValue?.toDouble() ?: return false

        if (distanceFromOptimal != null) {
            return distanceFromOptimal(current) > distanceFromOptimal(previous)
        }

        return when (biggerIsWorse) {
            true -> current > previous
            false -> current < previous
            null -> false
        }
    }

    private fun resolveOverallStatus(score: Int): OverallStatus = when {
        score >= 85 -> OverallStatus.GOOD
        score >= 70 -> OverallStatus.ATTENTION
        else -> OverallStatus.CRITICAL
    }

    private fun insufficient(cardId: DailyCheckCardId, penalty: Int): DailyCheckCardResult {
        return DailyCheckCardResult(
            id = cardId,
            status = DailyCheckCardStatus.INSUFFICIENT_DATA,
            penalty = penalty,
            details = "insufficient_data"
        )
    }

    private fun format(value: Number?): String {
        if (value == null) return "n/a"
        return if (value is Int || value.toDouble() % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.2f".format(value.toDouble())
        }
    }

    private val DailyCheckCardStatus.severity: Int
        get() = when (this) {
            DailyCheckCardStatus.OK -> 0
            DailyCheckCardStatus.ATTENTION -> 1
            DailyCheckCardStatus.ERROR -> 2
            DailyCheckCardStatus.INSUFFICIENT_DATA -> -1
        }

    private val TopChangeType.priority: Int
        get() = when (this) {
            TopChangeType.NEW_ERROR -> 0
            TopChangeType.REPEATED_WORSENING -> 1
            TopChangeType.NEW_INSTABILITY -> 2
            TopChangeType.IMPROVEMENT -> 3
        }

    // Штрафы раздела 14.
    private const val PENALTY_BATTERY_ATTENTION = 8
    private const val PENALTY_BATTERY_ERROR = 18
    private const val PENALTY_ENGINE_ATTENTION = 6
    private const val PENALTY_ENGINE_ERROR = 14
    private const val PENALTY_COOLING_ATTENTION = 6
    private const val PENALTY_COOLING_ERROR = 12
    private const val PENALTY_CHARGING_ATTENTION = 10
    private const val PENALTY_CHARGING_ERROR = 20
    private const val PENALTY_OBD_ATTENTION = 12
    private const val PENALTY_OBD_ERROR = 24
    private const val PENALTY_INSUFFICIENT = 4

    private const val MAX_SCORE = 100
    private const val MAX_TOP_CHANGES = 3
}

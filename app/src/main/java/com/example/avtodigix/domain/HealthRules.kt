package com.example.avtodigix.domain

import kotlin.math.abs

object HealthRules {
    fun evaluateEngine(
        engineRpm: Int?,
        vehicleSpeedKph: Int?,
        coolantTempCelsius: Int?,
        dtcCount: Int?
    ): HealthAssessment {
        if (engineRpm == null || vehicleSpeedKph == null || coolantTempCelsius == null) {
            return HealthAssessment(
                category = HealthCategory.ENGINE,
                status = TrafficLightStatus.YELLOW,
                message = "Недостаточно данных для оценки двигателя."
            )
        }

        val dtcStatus = dtcCount
            ?.takeIf { it > 0 }
            ?.let { count -> evaluateDtcCount(count).status }

        val status = when (dtcStatus) {
            TrafficLightStatus.RED -> TrafficLightStatus.RED
            TrafficLightStatus.YELLOW -> TrafficLightStatus.YELLOW
            null -> TrafficLightStatus.GREEN
            TrafficLightStatus.GREEN -> TrafficLightStatus.GREEN
        }

        val message = when (status) {
            TrafficLightStatus.GREEN ->
                "Двигатель работает штатно: обороты, скорость и температура в норме."
            TrafficLightStatus.YELLOW ->
                "Обнаружены диагностические коды, рекомендуется проверка двигателя."
            TrafficLightStatus.RED ->
                "Обнаружены критические диагностические коды, требуется срочная диагностика двигателя."
        }

        return HealthAssessment(HealthCategory.ENGINE, status, message)
    }

    fun evaluateCooling(coolantTempCelsius: Int?): HealthAssessment {
        if (coolantTempCelsius == null) {
            return HealthAssessment(
                category = HealthCategory.COOLING,
                status = TrafficLightStatus.YELLOW,
                message = "Температура охлаждающей жидкости недоступна для оценки."
            )
        }

        val status = when {
            coolantTempCelsius > HealthThresholds.COOLING_RED_CELSIUS -> TrafficLightStatus.RED
            coolantTempCelsius > HealthThresholds.COOLING_YELLOW_CELSIUS -> TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.GREEN
        }

        val message = when (status) {
            TrafficLightStatus.GREEN ->
                "Температура охлаждающей жидкости ${coolantTempCelsius}°C в норме."
            TrafficLightStatus.YELLOW ->
                "Температура охлаждающей жидкости ${coolantTempCelsius}°C выше нормы, проверьте систему охлаждения."
            TrafficLightStatus.RED ->
                "Температура охлаждающей жидкости ${coolantTempCelsius}°C критическая, остановитесь и дайте двигателю остыть."
        }

        return HealthAssessment(HealthCategory.COOLING, status, message)
    }

    fun evaluateBatteryVoltage(voltage: Double?): HealthAssessment {
        if (voltage == null) {
            return HealthAssessment(
                category = HealthCategory.BATTERY,
                status = TrafficLightStatus.YELLOW,
                message = "Напряжение аккумулятора недоступно для оценки."
            )
        }

        val status = when {
            voltage < HealthThresholds.BATTERY_RED_VOLTS -> TrafficLightStatus.RED
            voltage < HealthThresholds.BATTERY_YELLOW_VOLTS -> TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.GREEN
        }

        val formattedVoltage = formatVoltage(voltage)
        val message = when (status) {
            TrafficLightStatus.GREEN ->
                "Напряжение аккумулятора $formattedVoltage В в норме."
            TrafficLightStatus.YELLOW ->
                "Напряжение аккумулятора $formattedVoltage В снижено, рекомендуется проверить заряд."
            TrafficLightStatus.RED ->
                "Напряжение аккумулятора $formattedVoltage В критично низкое, нужна диагностика."
        }

        return HealthAssessment(HealthCategory.BATTERY, status, message)
    }

    fun evaluateDtcCount(count: Int?): HealthAssessment {
        if (count == null) {
            return HealthAssessment(
                category = HealthCategory.DTC_COUNT,
                status = TrafficLightStatus.YELLOW,
                message = "Количество DTC недоступно для оценки."
            )
        }

        val status = when {
            count >= HealthThresholds.DTC_RED_COUNT -> TrafficLightStatus.RED
            count >= HealthThresholds.DTC_YELLOW_COUNT -> TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.GREEN
        }

        val message = when (status) {
            TrafficLightStatus.GREEN ->
                "Активных диагностических кодов нет."
            TrafficLightStatus.YELLOW ->
                "Найдено диагностических кодов: $count."
            TrafficLightStatus.RED ->
                "Найдено диагностических кодов: $count. Требуется срочная диагностика."
        }

        return HealthAssessment(HealthCategory.DTC_COUNT, status, message)
    }

    fun evaluateFuelTrims(shortTermPercent: Double?, longTermPercent: Double?): HealthAssessment {
        if (shortTermPercent == null && longTermPercent == null) {
            return HealthAssessment(
                category = HealthCategory.FUEL_TRIMS,
                status = TrafficLightStatus.YELLOW,
                message = "Коррекции топлива недоступны для оценки."
            )
        }

        val deviation = listOfNotNull(shortTermPercent, longTermPercent)
            .any { abs(it) > HealthThresholds.FUEL_TRIM_WARNING_ABS_PERCENT }

        val status = if (deviation) {
            TrafficLightStatus.YELLOW
        } else {
            TrafficLightStatus.GREEN
        }

        val shortText = shortTermPercent?.let { "STFT ${formatPercent(it)}%" }
        val longText = longTermPercent?.let { "LTFT ${formatPercent(it)}%" }
        val trimsText = listOfNotNull(shortText, longText).joinToString(", ")

        val message = if (deviation) {
            "Обнаружено отклонение топливных коррекций: $trimsText."
        } else {
            "Топливные коррекции без отклонений: $trimsText."
        }

        return HealthAssessment(HealthCategory.FUEL_TRIMS, status, message)
    }

    private fun formatVoltage(value: Double): String {
        return String.format("%.2f", value)
    }

    private fun formatPercent(value: Double): String {
        return String.format("%.1f", value)
    }
}

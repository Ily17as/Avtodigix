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

    fun evaluateOilTemp(engineOilTempC: Int?): HealthAssessment {
        if (engineOilTempC == null) {
            return HealthAssessment(
                category = HealthCategory.OIL,
                status = TrafficLightStatus.YELLOW,
                message = "Температура масла недоступна для оценки (PID 01 5C не поддерживаетс)."
            )
        }

        val status = when {
            engineOilTempC < HealthThresholds.OIL_TEMP_MIN_YELLOW_C -> TrafficLightStatus.YELLOW
            engineOilTempC <= HealthThresholds.OIL_TEMP_GREEN_MAX_C -> TrafficLightStatus.GREEN
            engineOilTempC <= HealthThresholds.OIL_TEMP_YELLOW_MAX_C -> TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.RED
        }

        val message = when (status) {
            TrafficLightStatus.GREEN ->
                "Температура масла ${engineOilTempC}°C в норме."
            TrafficLightStatus.YELLOW ->
                "Температура масла ${engineOilTempC}°C требует внимания."
            TrafficLightStatus.RED ->
                "Температура масла ${engineOilTempC}°C критична."
        }

        return HealthAssessment(HealthCategory.OIL, status, message)
    }

    fun evaluateOilStatus(oilTempC: Double?, oilPressureKPa: Double?): HealthAssessment {
        if (oilTempC == null && oilPressureKPa == null) {
            return HealthAssessment(
                category = HealthCategory.OIL,
                status = TrafficLightStatus.YELLOW,
                message = "Нет данных по температуре и давлению масла."
            )
        }

        val tempStatus = oilTempC?.let { value ->
            when {
                value < HealthThresholds.OIL_TEMP_MIN_YELLOW_C -> TrafficLightStatus.YELLOW
                value <= HealthThresholds.OIL_TEMP_GREEN_MAX_C -> TrafficLightStatus.GREEN
                value <= HealthThresholds.OIL_TEMP_YELLOW_MAX_C -> TrafficLightStatus.YELLOW
                else -> TrafficLightStatus.RED
            }
        }
        val pressureStatus = oilPressureKPa?.let { value ->
            when {
                value < 50 -> TrafficLightStatus.RED
                value < 100 -> TrafficLightStatus.YELLOW
                else -> TrafficLightStatus.GREEN
            }
        }

        val resolvedStatus = listOfNotNull(tempStatus, pressureStatus)
            .reduce { current, next -> maxStatus(current, next) }

        val messages = buildList {
            if (oilTempC != null) {
                val formattedTemp = formatTemperature(oilTempC)
                val tempMessage = when (tempStatus) {
                    TrafficLightStatus.GREEN ->
                        "Температура масла $formattedTemp°C в норме."
                    TrafficLightStatus.YELLOW ->
                        "Температура масла $formattedTemp°C требует внимания."
                    TrafficLightStatus.RED ->
                        "Температура масла $formattedTemp°C критична."
                    null -> null
                }
                tempMessage?.let { add(it) }
            } else {
                add("Температура масла недоступна для оценки.")
            }

            if (oilPressureKPa != null) {
                val formattedPressure = formatPressure(oilPressureKPa)
                val pressureMessage = when (pressureStatus) {
                    TrafficLightStatus.GREEN ->
                        "Давление масла $formattedPressure kPa в норме."
                    TrafficLightStatus.YELLOW ->
                        "Давление масла $formattedPressure kPa ниже нормы."
                    TrafficLightStatus.RED ->
                        "Давление масла $formattedPressure kPa критически низкое."
                    null -> null
                }
                pressureMessage?.let { add(it) }
            } else {
                add("Давление масла недоступно для оценки.")
            }
        }

        return HealthAssessment(
            category = HealthCategory.OIL,
            status = resolvedStatus,
            message = messages.joinToString(" ")
        )
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
            .any { abs(it) > HealthThresholds.FUEL_TRIM_YELLOW_ABS_PERCENT }

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

    fun evaluateFuelStatus(
        shortTermPercent: Double?,
        longTermPercent: Double?,
        fuelPressureKPa: Double?,
        fuelPressurePidUsed: Int?
    ): HealthAssessment {
        if (shortTermPercent == null && longTermPercent == null && fuelPressureKPa == null) {
            return HealthAssessment(
                category = HealthCategory.FUEL_TRIMS,
                status = TrafficLightStatus.YELLOW,
                message = "Нет данных по коррекциям топлива и давлению топлива."
            )
        }

        val trimStatus = when {
            shortTermPercent == null && longTermPercent == null -> null
            listOfNotNull(shortTermPercent, longTermPercent)
                .any { abs(it) > HealthThresholds.FUEL_TRIM_RED_ABS_PERCENT } ->
                TrafficLightStatus.RED
            listOfNotNull(shortTermPercent, longTermPercent)
                .any { abs(it) > HealthThresholds.FUEL_TRIM_YELLOW_ABS_PERCENT } ->
                TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.GREEN
        }

        val pressureMinStatus = fuelPressureKPa?.let { pressure ->
            when (fuelPressurePidUsed) {
                0x0A -> when {
                    pressure < 150 -> TrafficLightStatus.YELLOW
                    pressure > 700 -> TrafficLightStatus.YELLOW
                    else -> null
                }
                0x22, 0x23 -> when {
                    pressure < 200 -> TrafficLightStatus.YELLOW
                    else -> null
                }
                else -> null
            }
        }

        val status = listOfNotNull(trimStatus, pressureMinStatus)
            .fold(TrafficLightStatus.GREEN) { current, next -> maxStatus(current, next) }

        val messages = buildList {
            val shortText = shortTermPercent?.let { "STFT ${formatPercent(it)}%" }
            val longText = longTermPercent?.let { "LTFT ${formatPercent(it)}%" }
            val trimsText = listOfNotNull(shortText, longText).joinToString(", ")
            if (trimsText.isNotBlank()) {
                val trimMessage = when (trimStatus) {
                    TrafficLightStatus.GREEN ->
                        "Топливные коррекции без отклонений: $trimsText."
                    TrafficLightStatus.YELLOW ->
                        "Топливные коррекции требуют внимания: $trimsText."
                    TrafficLightStatus.RED ->
                        "Топливные коррекции критичны: $trimsText."
                    null -> null
                }
                trimMessage?.let { add(it) }
            } else {
                add("Данные по топливным коррекциям недоступны.")
            }

            if (fuelPressureKPa != null) {
                val formattedPressure = formatPressure(fuelPressureKPa)
                val pressureMessage = when (fuelPressurePidUsed) {
                    0x0A -> when {
                        fuelPressureKPa < 150 ->
                            "Давление топлива $formattedPressure kPa ниже нормы (PID 0x0A)."
                        fuelPressureKPa > 700 ->
                            "Давление топлива $formattedPressure kPa выше нормы (PID 0x0A)."
                        else ->
                            "Давление топлива $formattedPressure kPa в норме (PID 0x0A)."
                    }
                    0x22, 0x23 -> when {
                        fuelPressureKPa < 200 ->
                            "Давление топлива $formattedPressure kPa ниже нормы (PID 0x${fuelPressurePidUsed.toString(16).uppercase()})."
                        else ->
                            "Давление топлива $formattedPressure kPa в норме (PID 0x${fuelPressurePidUsed.toString(16).uppercase()})."
                    }
                    else ->
                        "Давление топлива $formattedPressure kPa."
                }
                add(pressureMessage)
            } else {
                add("Давление топлива недоступно для оценки.")
            }
        }

        return HealthAssessment(
            category = HealthCategory.FUEL_TRIMS,
            status = status,
            message = messages.joinToString(" ")
        )
    }

    private fun formatVoltage(value: Double): String {
        return String.format("%.2f", value)
    }

    private fun formatPercent(value: Double): String {
        return String.format("%.1f", value)
    }

    private fun formatTemperature(value: Double): String {
        return String.format("%.0f", value)
    }

    private fun formatPressure(value: Double): String {
        return String.format("%.0f", value)
    }

    private fun maxStatus(current: TrafficLightStatus, next: TrafficLightStatus): TrafficLightStatus {
        return if (current.ordinal >= next.ordinal) current else next
    }
}

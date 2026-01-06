package com.example.avtodigix.domain

object HealthThresholds {
    const val COOLING_YELLOW_CELSIUS = 100
    const val COOLING_RED_CELSIUS = 110

    const val BATTERY_YELLOW_VOLTS = 12.0
    const val BATTERY_RED_VOLTS = 11.5

    const val DTC_YELLOW_COUNT = 1
    const val DTC_RED_COUNT = 3

    const val FUEL_TRIM_YELLOW_ABS_PERCENT = 10.0
    const val FUEL_TRIM_RED_ABS_PERCENT = 20.0

    const val OIL_TEMP_MIN_YELLOW_C = 0
    const val OIL_TEMP_GREEN_MAX_C = 125
    const val OIL_TEMP_YELLOW_MAX_C = 135
}

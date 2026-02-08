package com.example.avtodigix.ui.main

import com.example.avtodigix.connection.ObdState

fun buildKeyMetrics(state: ObdState): Map<String, Double> {
    val metrics = linkedMapOf<String, Double>()
    state.metrics?.engineRpm?.toDouble()?.let { metrics["Engine RPM"] = it }
    state.metrics?.vehicleSpeedKph?.toDouble()?.let { metrics["Vehicle speed (km/h)"] = it }
    state.metrics?.coolantTempCelsius?.toDouble()?.let { metrics["Coolant temp (C)"] = it }
    state.metrics?.batteryVoltageVolts?.let { metrics["Battery voltage (V)"] = it }
    return metrics
}

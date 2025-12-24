package com.example.avtodigix.domain

data class HealthAssessment(
    val category: HealthCategory,
    val status: TrafficLightStatus,
    val message: String
)

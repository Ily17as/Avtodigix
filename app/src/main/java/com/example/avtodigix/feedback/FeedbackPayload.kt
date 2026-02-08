package com.example.avtodigix.feedback

data class FeedbackPayload(
    val rating: Int,
    val tags: List<String>,
    val comment: String,
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String,
    val connectionType: String,
    val lastSessionResult: String,
    val dtcCount: Int,
    val submittedAt: Long
)

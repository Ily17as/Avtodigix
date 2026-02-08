package com.example.avtodigix.feedback

data class FeedbackPayload(
    val rating: Int,
    val tags: List<String>,
    val comment: String,
    val appVersion: String,
    val buildNumber: Int,
    val platform: String,
    val deviceModel: String?,
    val connectionType: String,
    val lastSessionResult: String,
    val dtcCount: Int,
    val submittedAtUtc: String,
    val submittedAtMillis: Long
)

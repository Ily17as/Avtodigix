package com.example.avtodigix.feedback

interface FeedbackSender {
    suspend fun send(payload: FeedbackPayload): FeedbackSendResult
}

data class FeedbackSendResult(
    val success: Boolean,
    val responseCode: Int,
    val responseBody: String?
)

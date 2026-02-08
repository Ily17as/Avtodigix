package com.example.avtodigix.feedback

sealed interface FeedbackSubmissionState {
    data object Idle : FeedbackSubmissionState
    data object Sending : FeedbackSubmissionState
    data object Success : FeedbackSubmissionState
    data class Error(val isOffline: Boolean) : FeedbackSubmissionState
}

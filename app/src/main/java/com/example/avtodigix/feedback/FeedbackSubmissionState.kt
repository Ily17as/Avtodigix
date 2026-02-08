package com.example.avtodigix.feedback

sealed interface FeedbackSubmissionState {
    data object Idle : FeedbackSubmissionState
    data object FormOpened : FeedbackSubmissionState
    data object BrowserUnavailable : FeedbackSubmissionState
}

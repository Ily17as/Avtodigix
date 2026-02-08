package com.example.avtodigix.feedback

class FeedbackManager(
    private val feedbackPrefs: FeedbackPrefs
) {
    fun recordSuccessfulConnection() {
        val updatedCount = feedbackPrefs.getSuccessfulConnectionsCount() + 1
        feedbackPrefs.setSuccessfulConnectionsCount(updatedCount)
    }

    fun recordFirstFullScan(nowMillis: Long = System.currentTimeMillis()) {
        if (feedbackPrefs.getFirstFullScanAt() == 0L) {
            feedbackPrefs.setFirstFullScanAt(nowMillis)
        }
    }

    fun markPromptShown(nowMillis: Long = System.currentTimeMillis()) {
        feedbackPrefs.setLastPromptAt(nowMillis)
    }

    fun markSubmitted(nowMillis: Long = System.currentTimeMillis()) {
        feedbackPrefs.setLastSubmittedAt(nowMillis)
    }

    fun shouldShowPrompt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val firstFullScanAt = feedbackPrefs.getFirstFullScanAt()
        val successfulConnections = feedbackPrefs.getSuccessfulConnectionsCount()
        val lastPromptAt = feedbackPrefs.getLastPromptAt()
        val lastSubmittedAt = feedbackPrefs.getLastSubmittedAt()

        val triggerReached = firstFullScanAt > 0L || successfulConnections >= REQUIRED_CONNECTIONS
        if (!triggerReached) {
            return false
        }

        if (lastPromptAt > 0L && nowMillis - lastPromptAt < PROMPT_COOLDOWN_MILLIS) {
            return false
        }

        if (lastSubmittedAt > 0L && nowMillis - lastSubmittedAt < SUBMIT_COOLDOWN_MILLIS) {
            return false
        }

        return true
    }

    private companion object {
        private const val REQUIRED_CONNECTIONS = 3
        private const val PROMPT_COOLDOWN_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val SUBMIT_COOLDOWN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

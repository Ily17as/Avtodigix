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

    fun markFormOpened(nowMillis: Long = System.currentTimeMillis()) {
        feedbackPrefs.setLastFormOpenedAt(nowMillis)
    }

    fun shouldShowPrompt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val firstFullScanAt = feedbackPrefs.getFirstFullScanAt()
        val successfulConnections = feedbackPrefs.getSuccessfulConnectionsCount()
        val lastPromptAt = feedbackPrefs.getLastPromptAt()
        val lastFormOpenedAt = feedbackPrefs.getLastFormOpenedAt()

        val triggerReached = firstFullScanAt > 0L || successfulConnections >= REQUIRED_CONNECTIONS
        if (!triggerReached) {
            return false
        }

        if (lastPromptAt > 0L && nowMillis - lastPromptAt < PROMPT_COOLDOWN_MILLIS) {
            return false
        }

        if (lastFormOpenedAt > 0L && nowMillis - lastFormOpenedAt < FORM_OPEN_COOLDOWN_MILLIS) {
            return false
        }

        return true
    }

    private companion object {
        private const val REQUIRED_CONNECTIONS = 3
        private const val PROMPT_COOLDOWN_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val FORM_OPEN_COOLDOWN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

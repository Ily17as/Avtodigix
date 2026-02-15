package com.example.avtodigix.feedback

class FeedbackManager(
    private val feedbackPrefs: FeedbackPrefs
) {
    fun shouldShowPromptOnSecondAppLaunch(): Boolean {
        if (feedbackPrefs.hasSubmittedFeedback()) {
            return false
        }

        val updatedOpenCount = feedbackPrefs.getAppOpenCount() + 1
        feedbackPrefs.setAppOpenCount(updatedOpenCount)

        if (feedbackPrefs.isSecondLaunchPromptShown()) {
            return false
        }

        return if (updatedOpenCount == SECOND_LAUNCH_COUNT) {
            feedbackPrefs.setSecondLaunchPromptShown(true)
            true
        } else {
            false
        }
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

    fun markFeedbackSubmitted() {
        feedbackPrefs.setHasSubmittedFeedback(true)
    }

    fun shouldShowPromptForExternalTrigger(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (feedbackPrefs.hasSubmittedFeedback()) {
            return false
        }

        val lastPromptAt = feedbackPrefs.getLastPromptAt()
        val lastFormOpenedAt = feedbackPrefs.getLastFormOpenedAt()

        if (lastPromptAt > 0L && nowMillis - lastPromptAt < PROMPT_COOLDOWN_MILLIS) {
            return false
        }

        if (lastFormOpenedAt > 0L && nowMillis - lastFormOpenedAt < FORM_OPEN_COOLDOWN_MILLIS) {
            return false
        }

        return true
    }

    fun shouldShowPrompt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (feedbackPrefs.hasSubmittedFeedback()) {
            return false
        }

        val firstFullScanAt = feedbackPrefs.getFirstFullScanAt()
        val lastPromptAt = feedbackPrefs.getLastPromptAt()
        val lastFormOpenedAt = feedbackPrefs.getLastFormOpenedAt()

        val triggerReached = firstFullScanAt > 0L
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
        private const val SECOND_LAUNCH_COUNT = 2
        private const val PROMPT_COOLDOWN_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val FORM_OPEN_COOLDOWN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

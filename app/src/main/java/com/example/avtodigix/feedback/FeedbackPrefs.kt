package com.example.avtodigix.feedback

import android.content.Context

class FeedbackPrefs(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSuccessfulConnectionsCount(): Int = preferences.getInt(KEY_SUCCESSFUL_CONNECTIONS, 0)

    fun setSuccessfulConnectionsCount(value: Int) {
        preferences.edit().putInt(KEY_SUCCESSFUL_CONNECTIONS, value).apply()
    }

    fun getFirstFullScanAt(): Long = preferences.getLong(KEY_FIRST_FULL_SCAN_AT, 0L)

    fun setFirstFullScanAt(value: Long) {
        preferences.edit().putLong(KEY_FIRST_FULL_SCAN_AT, value).apply()
    }

    fun getLastPromptAt(): Long = preferences.getLong(KEY_LAST_PROMPT_AT, 0L)

    fun setLastPromptAt(value: Long) {
        preferences.edit().putLong(KEY_LAST_PROMPT_AT, value).apply()
    }

    fun getLastSubmittedAt(): Long = preferences.getLong(KEY_LAST_SUBMITTED_AT, 0L)

    fun setLastSubmittedAt(value: Long) {
        preferences.edit().putLong(KEY_LAST_SUBMITTED_AT, value).apply()
    }

    private companion object {
        private const val PREFS_NAME = "feedback_prefs"
        private const val KEY_SUCCESSFUL_CONNECTIONS = "successful_connections_count"
        private const val KEY_FIRST_FULL_SCAN_AT = "first_full_scan_at"
        private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
        private const val KEY_LAST_SUBMITTED_AT = "last_submitted_at"
    }
}

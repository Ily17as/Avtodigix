package com.example.avtodigix.feedback

import android.content.Context

class FeedbackPrefs(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFirstFullScanAt(): Long = preferences.getLong(KEY_FIRST_FULL_SCAN_AT, 0L)

    fun setFirstFullScanAt(value: Long) {
        preferences.edit().putLong(KEY_FIRST_FULL_SCAN_AT, value).apply()
    }

    fun getLastPromptAt(): Long = preferences.getLong(KEY_LAST_PROMPT_AT, 0L)

    fun setLastPromptAt(value: Long) {
        preferences.edit().putLong(KEY_LAST_PROMPT_AT, value).apply()
    }

    fun getLastFormOpenedAt(): Long = preferences.getLong(KEY_LAST_FORM_OPENED_AT, 0L)

    fun setLastFormOpenedAt(value: Long) {
        preferences.edit().putLong(KEY_LAST_FORM_OPENED_AT, value).apply()
    }

    fun getAppOpenCount(): Int = preferences.getInt(KEY_APP_OPEN_COUNT, 0)

    fun setAppOpenCount(value: Int) {
        preferences.edit().putInt(KEY_APP_OPEN_COUNT, value).apply()
    }

    fun isSecondLaunchPromptShown(): Boolean =
        preferences.getBoolean(KEY_SECOND_LAUNCH_PROMPT_SHOWN, false)

    fun setSecondLaunchPromptShown(value: Boolean) {
        preferences.edit().putBoolean(KEY_SECOND_LAUNCH_PROMPT_SHOWN, value).apply()
    }

    private companion object {
        private const val PREFS_NAME = "feedback_prefs"
        private const val KEY_FIRST_FULL_SCAN_AT = "first_full_scan_at"
        private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
        private const val KEY_LAST_FORM_OPENED_AT = "last_form_opened_at"
        private const val KEY_APP_OPEN_COUNT = "app_open_count"
        private const val KEY_SECOND_LAUNCH_PROMPT_SHOWN = "second_launch_prompt_shown"
    }
}

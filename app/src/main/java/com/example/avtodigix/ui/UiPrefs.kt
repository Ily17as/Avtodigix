package com.example.avtodigix.ui

import android.content.Context

class UiPrefs(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingShown(): Boolean {
        return preferences.getBoolean(KEY_ONBOARDING_SHOWN, false)
    }

    fun setOnboardingShown(shown: Boolean) {
        preferences.edit().putBoolean(KEY_ONBOARDING_SHOWN, shown).apply()
    }

    fun getUserMode(): UserMode {
        val value = preferences.getString(KEY_USER_MODE, USER_MODE_NOVICE)
        return if (value == USER_MODE_PRO) UserMode.Professional else UserMode.Novice
    }

    fun setUserMode(mode: UserMode) {
        val value = if (mode == UserMode.Professional) USER_MODE_PRO else USER_MODE_NOVICE
        preferences.edit().putString(KEY_USER_MODE, value).apply()
    }

    fun isDiagnosticsModeEnabled(): Boolean {
        return preferences.getBoolean(KEY_DIAGNOSTICS_MODE_ENABLED, false)
    }

    fun setDiagnosticsModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DIAGNOSTICS_MODE_ENABLED, enabled).apply()
    }

    enum class UserMode {
        Novice,
        Professional
    }

    private companion object {
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        private const val KEY_USER_MODE = "user_mode"
        private const val KEY_DIAGNOSTICS_MODE_ENABLED = "diagnostics_mode_enabled"
        private const val USER_MODE_NOVICE = "novice"
        private const val USER_MODE_PRO = "pro"
    }
}

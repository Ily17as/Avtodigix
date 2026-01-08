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

    private companion object {
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
    }
}

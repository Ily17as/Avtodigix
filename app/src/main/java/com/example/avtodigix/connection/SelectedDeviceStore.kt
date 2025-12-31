package com.example.avtodigix.connection

import android.content.Context

class SelectedDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSelectedDeviceAddress(): String? {
        return preferences.getString(KEY_SELECTED_ADDRESS, null)
    }

    fun setSelectedDeviceAddress(address: String?) {
        preferences.edit().putString(KEY_SELECTED_ADDRESS, address).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "connection_preferences"
        const val KEY_SELECTED_ADDRESS = "selected_device_address"
    }
}

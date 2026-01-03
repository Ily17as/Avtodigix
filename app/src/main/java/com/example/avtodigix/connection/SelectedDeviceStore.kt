package com.example.avtodigix.connection

import android.content.Context

class SelectedDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSelectedScannerType(): ScannerType? {
        val stored = preferences.getString(KEY_SELECTED_SCANNER_TYPE, null) ?: return null
        return runCatching { ScannerType.valueOf(stored) }.getOrNull()
    }

    fun setSelectedScannerType(scannerType: ScannerType) {
        preferences.edit().putString(KEY_SELECTED_SCANNER_TYPE, scannerType.name).apply()
    }

    fun getSelectedDeviceAddress(): String? {
        return preferences.getString(KEY_SELECTED_ADDRESS, null)
    }

    fun setSelectedDeviceAddress(address: String?) {
        preferences.edit().putString(KEY_SELECTED_ADDRESS, address).apply()
    }

    fun getWifiHost(): String? {
        return preferences.getString(KEY_WIFI_IP, null) ?: DEFAULT_WIFI_HOST
    }

    fun setWifiHost(host: String?) {
        preferences.edit().putString(KEY_WIFI_IP, host).apply()
    }

    fun getWifiPort(): Int? {
        val port = preferences.getInt(KEY_WIFI_PORT, -1)
        return if (port > 0) port else DEFAULT_WIFI_PORT
    }

    fun setWifiPort(port: Int?) {
        if (port == null || port <= 0) {
            preferences.edit().remove(KEY_WIFI_PORT).apply()
        } else {
            preferences.edit().putInt(KEY_WIFI_PORT, port).apply()
        }
    }

    fun clearWifiSettings() {
        preferences.edit()
            .remove(KEY_WIFI_IP)
            .remove(KEY_WIFI_PORT)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "connection_preferences"
        const val KEY_SELECTED_ADDRESS = "selected_device_address"
        const val KEY_SELECTED_SCANNER_TYPE = "selected_scanner_type"
        const val KEY_WIFI_IP = "wifi_ip"
        const val KEY_WIFI_PORT = "wifi_port"
        const val DEFAULT_WIFI_HOST = "192.168.0.10"
        const val DEFAULT_WIFI_PORT = 35000
    }
}

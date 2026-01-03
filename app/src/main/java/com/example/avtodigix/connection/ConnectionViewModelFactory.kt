package com.example.avtodigix.connection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.avtodigix.bluetooth.BluetoothConnectionManager
import com.example.avtodigix.wifi.WiFiScannerManager

class ConnectionViewModelFactory(
    private val context: Context,
    private val selectedDeviceStore: SelectedDeviceStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConnectionViewModel(
                BluetoothConnectionManager(context),
                WiFiScannerManager(),
                selectedDeviceStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

package com.example.avtodigix.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.avtodigix.bluetooth.BluetoothConnectionManager

class ConnectionViewModelFactory(
    private val selectedDeviceStore: SelectedDeviceStore,
    private val connectionManager: BluetoothConnectionManager = BluetoothConnectionManager()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConnectionViewModel(connectionManager, selectedDeviceStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

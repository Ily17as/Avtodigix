package com.example.avtodigix.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothConnectionManager(
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parentScope: CoroutineScope? = null,
    private val retryDelayMillis: Long = 2_000,
    private val maxRetries: Int = 3,
    private val connectionCheckIntervalMillis: Long = 1_000
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.NoConnection)
    val status: StateFlow<ConnectionStatus> = _status
    private val _socketState = MutableStateFlow<BluetoothSocket?>(null)
    val socketState: StateFlow<BluetoothSocket?> = _socketState

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null

    suspend fun getPairedDevices(): List<PairedDevice> = withContext(ioDispatcher) {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _status.value = ConnectionStatus.AdapterNotResponding
            return@withContext emptyList()
        }

        bluetoothAdapter.bondedDevices.map { device ->
            PairedDevice(
                name = device.name ?: "Unknown",
                address = device.address
            )
        }
    }

    fun connect(address: String, uuid: UUID) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            val bluetoothAdapter = adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                _status.value = ConnectionStatus.AdapterNotResponding
                return@launch
            }

            val device = runCatching { bluetoothAdapter.getRemoteDevice(address) }
                .getOrElse {
                    _status.value = ConnectionStatus.NoConnection
                    return@launch
                }

            attemptConnectionWithRetry(device, uuid)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            closeSocket()
            _status.value = ConnectionStatus.NoConnection
        }
    }

    private suspend fun attemptConnectionWithRetry(device: BluetoothDevice, uuid: UUID) {
        var attempts = 0
        while (scope.coroutineContext[Job]?.isActive == true && attempts < maxRetries) {
            _status.value = ConnectionStatus.NoConnection
            attempts += 1
            if (tryConnect(device, uuid)) {
                attempts = 0
                monitorConnection(device, uuid)
            } else {
                delay(retryDelayMillis)
            }
        }
    }

    private suspend fun tryConnect(device: BluetoothDevice, uuid: UUID): Boolean = withContext(ioDispatcher) {
        closeSocket()
        val createdSocket = runCatching { device.createRfcommSocketToServiceRecord(uuid) }
            .getOrElse {
                _status.value = ConnectionStatus.NoConnection
                return@withContext false
            }

        socket = createdSocket
        return@withContext try {
            createdSocket.connect()
            _status.value = ConnectionStatus.Connected
            _socketState.value = createdSocket
            true
        } catch (error: IOException) {
            _status.value = ConnectionStatus.NoConnection
            closeSocket()
            false
        }
    }

    private suspend fun monitorConnection(device: BluetoothDevice, uuid: UUID) {
        while (scope.coroutineContext[Job]?.isActive == true) {
            val currentSocket = socket
            if (currentSocket == null || !currentSocket.isConnected) {
                _status.value = ConnectionStatus.NoConnection
                attemptConnectionWithRetry(device, uuid)
                return
            }
            delay(connectionCheckIntervalMillis)
        }
    }

    private suspend fun closeSocket() {
        withContext(ioDispatcher) {
            socket?.let { current ->
                runCatching { current.close() }
            }
            socket = null
            _socketState.value = null
        }
    }
}

sealed class ConnectionStatus {
    data object Connected : ConnectionStatus()
    data object NoConnection : ConnectionStatus()
    data object AdapterNotResponding : ConnectionStatus()
}

data class PairedDevice(
    val name: String,
    val address: String
)

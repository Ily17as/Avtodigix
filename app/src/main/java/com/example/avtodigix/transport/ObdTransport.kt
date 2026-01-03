package com.example.avtodigix.transport

import com.example.avtodigix.bluetooth.BluetoothConnectionManager
import com.example.avtodigix.wifi.WiFiScannerManager
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

enum class TransportKind {
    Bluetooth,
    Wifi
}

interface ObdTransport {
    val kind: TransportKind
    val input: InputStream
    val output: OutputStream

    suspend fun connect()
    suspend fun disconnect()
}

class BluetoothObdTransport(
    private val manager: BluetoothConnectionManager,
    private val address: String,
    private val uuid: UUID,
    private val timeoutMillis: Long
) : ObdTransport {
    private var transport: ScannerTransport? = null

    override val kind: TransportKind = TransportKind.Bluetooth
    override val input: InputStream
        get() = requireNotNull(transport).input
    override val output: OutputStream
        get() = requireNotNull(transport).output

    override suspend fun connect() {
        manager.connect(address, uuid)
        transport = withTimeout(timeoutMillis) {
            manager.transportState.filterNotNull().first { it.isConnected }
        }
    }

    override suspend fun disconnect() {
        manager.disconnect()
        transport = null
    }
}

class WifiObdTransport(
    private val manager: WiFiScannerManager,
    private val host: String,
    private val port: Int
) : ObdTransport {
    private var transport: ScannerTransport? = null

    override val kind: TransportKind = TransportKind.Wifi
    override val input: InputStream
        get() = requireNotNull(transport).input
    override val output: OutputStream
        get() = requireNotNull(transport).output

    override suspend fun connect() {
        transport = manager.connect(host, port)
    }

    override suspend fun disconnect() {
        manager.disconnect()
        transport = null
    }
}

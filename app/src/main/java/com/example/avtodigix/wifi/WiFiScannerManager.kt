package com.example.avtodigix.wifi

import android.content.Context
import com.example.avtodigix.transport.ScannerTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min

class WiFiScannerManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parentScope: CoroutineScope? = null
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _status = MutableStateFlow<WifiConnectionStatus>(WifiConnectionStatus.Failed)
    val status: StateFlow<WifiConnectionStatus> = _status
    private val _transportState = MutableStateFlow<ScannerTransport?>(null)
    val transportState: StateFlow<ScannerTransport?> = _transportState
    private val discoveryService = WifiDiscoveryService(context, parentScope = scope)
    val discoveredDevices: StateFlow<List<WifiDiscoveredDevice>> = discoveryService.devices

    private var socket: Socket? = null

    suspend fun connect(
        host: String,
        port: Int,
        connectTimeoutMs: Long = DEFAULT_TIMEOUT_MILLIS,
        readTimeoutMs: Long = DEFAULT_TIMEOUT_MILLIS
    ): WifiTransport = withContext(ioDispatcher) {
        val safeConnectTimeout = min(connectTimeoutMs, MAX_TIMEOUT_MILLIS).toInt()
        val safeReadTimeout = min(readTimeoutMs, MAX_TIMEOUT_MILLIS).toInt()
        disconnectInternal()
        _status.value = WifiConnectionStatus.Connecting
        val createdSocket = Socket()
        return@withContext try {
            createdSocket.connect(InetSocketAddress(host, port), safeConnectTimeout)
            createdSocket.soTimeout = safeReadTimeout
            socket = createdSocket
            val transport = WifiTransport(socket = createdSocket)
            _transportState.value = transport
            _status.value = WifiConnectionStatus.Connected
            transport
        } catch (error: Exception) {
            runCatching { createdSocket.close() }
            _transportState.value = null
            _status.value = WifiConnectionStatus.Failed
            throw error
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
        }
    }

    fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }

    private suspend fun disconnectInternal() = withContext(ioDispatcher) {
        socket?.let { current ->
            runCatching { current.close() }
        }
        socket = null
        _transportState.value = null
        _status.value = WifiConnectionStatus.Failed
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        private const val MAX_TIMEOUT_MILLIS = 10_000L
    }
}

sealed class WifiConnectionStatus {
    data object Connected : WifiConnectionStatus()
    data object Connecting : WifiConnectionStatus()
    data object Failed : WifiConnectionStatus()
}

data class WifiTransport(
    val socket: Socket
) : ScannerTransport {
    override val input: InputStream
        get() = socket.getInputStream()
    override val output: OutputStream
        get() = socket.getOutputStream()
    override val isConnected: Boolean
        get() = socket.isConnected

    override suspend fun close() {
        runCatching { socket.close() }
    }
}

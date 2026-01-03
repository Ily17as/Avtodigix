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
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
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
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): WifiTransport = withContext(ioDispatcher) {
        val safeTimeout = min(timeoutMillis, MAX_TIMEOUT_MILLIS).toInt()
        disconnectInternal()
        _status.value = WifiConnectionStatus.Connecting
        val createdSocket = createTlsSocket()
        return@withContext try {
            createdSocket.connect(InetSocketAddress(host, port), safeTimeout)
            createdSocket.sslParameters = createdSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            createdSocket.startHandshake()
            socket = createdSocket
            val transport = WifiTransport(
                socket = createdSocket,
                input = createdSocket.getInputStream(),
                output = createdSocket.getOutputStream()
            )
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

    private fun createTlsSocket(): SSLSocket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)
        return sslContext.socketFactory.createSocket() as SSLSocket
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
    val socket: Socket,
    val input: InputStream,
    val output: OutputStream
) : ScannerTransport {
    override val isConnected: Boolean
        get() = socket.isConnected

    override suspend fun close() {
        runCatching { socket.close() }
    }
}

package com.example.avtodigix.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _status = MutableStateFlow<WifiConnectionStatus>(WifiConnectionStatus.Disconnected)
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
        disconnectInternal(WifiConnectionStatus.Disconnected)
        _status.value = WifiConnectionStatus.Connecting
        val network = activeWifiNetwork()
        val createdSocket = network.socketFactory.createSocket()
        return@withContext try {
            runCatching { network.bindSocket(createdSocket) }
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
            disconnectInternal(WifiConnectionStatus.Disconnected)
        }
    }

    fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }

    private suspend fun disconnectInternal(status: WifiConnectionStatus) = withContext(ioDispatcher) {
        socket?.let { current ->
            runCatching { current.close() }
        }
        socket = null
        _transportState.value = null
        _status.value = status
    }

    private fun activeWifiNetwork(): Network {
        val network = connectivityManager.activeNetwork
            ?: throw IllegalStateException("Активная сеть не найдена.")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw IllegalStateException("Не удалось определить параметры сети.")
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            throw IllegalStateException("Активная сеть не Wi-Fi.")
        }
        return network
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        private const val MAX_TIMEOUT_MILLIS = 10_000L
    }
}

sealed class WifiConnectionStatus {
    data object Connected : WifiConnectionStatus()
    data object Connecting : WifiConnectionStatus()
    data object Disconnected : WifiConnectionStatus()
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

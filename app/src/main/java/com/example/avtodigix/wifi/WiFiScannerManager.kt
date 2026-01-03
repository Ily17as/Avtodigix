package com.example.avtodigix.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.avtodigix.transport.ScannerTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WiFiScannerManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parentScope: CoroutineScope? = null
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _status = MutableStateFlow<WifiConnectionStatus>(WifiConnectionStatus.Failed)
    val status: StateFlow<WifiConnectionStatus> = _status
    private val _transportState = MutableStateFlow<ScannerTransport?>(null)
    val transportState: StateFlow<ScannerTransport?> = _transportState
    private val discoveryService = WifiDiscoveryService(context, parentScope = scope)
    val discoveredDevices: StateFlow<List<WifiDiscoveredDevice>> = discoveryService.devices

    private var socket: Socket? = null
    private var requestedNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        val network = requestWifiNetwork()
        val createdSocket = network.socketFactory.createSocket()
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
            releaseWifiRequest()
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
        releaseWifiRequest()
        _transportState.value = null
        _status.value = WifiConnectionStatus.Failed
    }

    private suspend fun requestWifiNetwork(): Network = suspendCancellableCoroutine { continuation ->
        releaseWifiRequest()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (continuation.isCompleted) {
                    return
                }
                requestedNetwork = network
                continuation.resume(network)
            }

            override fun onUnavailable() {
                if (continuation.isCompleted) {
                    return
                }
                continuation.resumeWithException(IllegalStateException("Wi-Fi network unavailable"))
            }

            override fun onLost(network: Network) {
                if (requestedNetwork == network) {
                    requestedNetwork = null
                }
            }
        }
        networkCallback = callback
        connectivityManager.requestNetwork(request, callback)
        continuation.invokeOnCancellation {
            releaseWifiRequest()
        }
    }

    private fun releaseWifiRequest() {
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        requestedNetwork = null
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

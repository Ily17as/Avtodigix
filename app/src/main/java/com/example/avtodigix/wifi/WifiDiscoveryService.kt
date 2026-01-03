package com.example.avtodigix.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class WifiDiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int
)

class WifiDiscoveryService(
    context: Context,
    private val serviceType: String = DEFAULT_SERVICE_TYPE,
    parentScope: CoroutineScope? = null
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _devices = MutableStateFlow<List<WifiDiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<WifiDiscoveredDevice>> = _devices
    private val deviceByName = mutableMapOf<String, WifiDiscoveredDevice>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        stopDiscovery()
        deviceByName.clear()
        _devices.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                removeDevice(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(
                normalizeServiceType(serviceType),
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        }.onFailure {
            discoveryListener = null
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val host = resolvedInfo.host?.hostAddress
                        ?: resolvedInfo.host?.hostName
                    val port = resolvedInfo.port
                    if (host.isNullOrBlank() || port <= 0) {
                        return
                    }
                    val device = WifiDiscoveredDevice(
                        name = resolvedInfo.serviceName.orEmpty(),
                        host = host,
                        port = port
                    )
                    scope.launch {
                        deviceByName[device.name] = device
                        _devices.value = deviceByName.values
                            .sortedBy { it.name.lowercase(Locale.getDefault()) }
                    }
                }
            }
        )
    }

    private fun removeDevice(serviceName: String?) {
        if (serviceName.isNullOrBlank()) {
            return
        }
        scope.launch {
            deviceByName.remove(serviceName)
            _devices.value = deviceByName.values
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    private fun normalizeServiceType(type: String): String {
        return type.trim().takeIf { it.endsWith(".") } ?: "${type.trim()}."
    }

    companion object {
        private const val DEFAULT_SERVICE_TYPE = "_obd._tcp."
    }
}

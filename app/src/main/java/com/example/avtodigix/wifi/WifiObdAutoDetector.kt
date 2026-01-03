package com.example.avtodigix.wifi

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.max
import kotlin.math.min

data class Endpoint(
    val host: String,
    val port: Int
)

class WifiObdAutoDetector(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun detect(candidates: List<Endpoint>): Endpoint? = withContext(ioDispatcher) {
        for (endpoint in candidates) {
            if (probeElm(endpoint)) {
                return@withContext endpoint
            }
        }
        return@withContext null
    }

    fun generateCandidates(): List<Endpoint> {
        val endpoints = LinkedHashSet<Endpoint>()
        val ports = listOf(DEFAULT_PORT, TELNET_PORT, SECONDARY_PORT)
        val fastHosts = listOf(
            "192.168.0.10",
            "192.168.4.1",
            "192.168.1.10",
            "10.0.0.1",
            "192.168.0.1"
        )
        fastHosts.forEach { host -> addEndpoints(endpoints, host, ports) }

        val dhcpInfo = wifiManager()?.dhcpInfo
        val gatewayIp = dhcpInfo?.gateway?.let(::intToInetAddress)?.hostAddress
        val deviceIp = dhcpInfo?.ipAddress?.let(::intToInetAddress)?.hostAddress
        val subnetPrefix = gatewayIp?.let(::prefixFromIp) ?: deviceIp?.let(::prefixFromIp)

        if (!subnetPrefix.isNullOrBlank()) {
            listOf(1, 10, 11, 100, 101, 200).forEach { suffix ->
                addEndpoints(endpoints, "$subnetPrefix.$suffix", ports)
            }

            val lastOctet = deviceIp?.split('.')?.lastOrNull()?.toIntOrNull()
            if (lastOctet != null) {
                for (offset in -2..2) {
                    val candidate = lastOctet + offset
                    if (candidate in 1..254) {
                        addEndpoints(endpoints, "$subnetPrefix.$candidate", ports)
                    }
                }
            }
        }

        return endpoints.toList()
    }

    private fun wifiManager(): WifiManager? {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private fun addEndpoints(
        set: MutableSet<Endpoint>,
        host: String,
        ports: List<Int>
    ) {
        ports.forEach { port -> set.add(Endpoint(host = host, port = port)) }
    }

    private fun prefixFromIp(ipAddress: String): String? {
        val parts = ipAddress.split('.')
        if (parts.size != 4) {
            return null
        }
        return parts.dropLast(1).joinToString(".")
    }

    private fun intToInetAddress(address: Int): InetAddress {
        val bytes = byteArrayOf(
            (address and 0xff).toByte(),
            (address shr 8 and 0xff).toByte(),
            (address shr 16 and 0xff).toByte(),
            (address shr 24 and 0xff).toByte()
        )
        return InetAddress.getByAddress(bytes)
    }

    private fun probeElm(
        endpoint: Endpoint,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS
    ): Boolean {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMs)
            val probeWindowMs = min(max(probeTimeoutMs, MIN_PROBE_TIMEOUT_MS), MAX_PROBE_TIMEOUT_MS)
            socket.soTimeout = probeWindowMs
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            if (probeWithCommand(output, input, "ATI\r", probeWindowMs)) {
                true
            } else {
                probeWithCommand(output, input, "ATZ\r", probeWindowMs)
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun probeWithCommand(
        output: java.io.OutputStream,
        input: InputStream,
        command: String,
        probeWindowMs: Int
    ): Boolean {
        output.write(command.toByteArray())
        output.flush()
        val response = readResponse(input, probeWindowMs)
        return looksLikeElmResponse(response)
    }

    private fun readResponse(input: InputStream, probeWindowMs: Int): String {
        val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
        val output = StringBuilder()
        val deadline = System.currentTimeMillis() + probeWindowMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                break
            }
            val read = try {
                input.read(buffer, 0, buffer.size)
            } catch (_: Exception) {
                break
            }
            if (read > 0) {
                output.append(String(buffer, 0, read))
                if (looksLikeElmResponse(output.toString())) {
                    break
                }
            } else {
                break
            }
        }
        return output.toString()
    }

    private fun looksLikeElmResponse(response: String): Boolean {
        val normalized = response.uppercase()
        return normalized.contains("ELM") || normalized.contains("OK") || normalized.contains(">")
    }

    companion object {
        private const val DEFAULT_PORT = 35000
        private const val TELNET_PORT = 23
        private const val SECONDARY_PORT = 2000
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 1000
        private const val DEFAULT_PROBE_TIMEOUT_MS = 600
        private const val MIN_PROBE_TIMEOUT_MS = 300
        private const val MAX_PROBE_TIMEOUT_MS = 800
        private const val RESPONSE_BUFFER_SIZE = 512
    }
}

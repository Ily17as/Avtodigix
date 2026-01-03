package com.example.avtodigix.obd

import android.util.Log
import com.example.avtodigix.elm.ElmResponse
import com.example.avtodigix.elm.ElmSession
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

class ObdService(
    private val session: ElmSession,
    private val diagnosticsListener: (ObdDiagnostics) -> Unit = {}
) {
    suspend fun readSupportedPids(): Map<Int, Boolean> {
        val supported = mutableSetOf<Int>()
        var basePid = 0x00
        var hasNextGroup = true

        while (hasNextGroup) {
            val response = executeWithDiagnostics(String.format("01 %02X", basePid))
            val bytes = response.lines
                .mapNotNull { parseHexBytes(it) }
                .firstOrNull { it.size >= 6 && it[0] == 0x41 && it[1] == basePid }
                ?: break

            val data = bytes.subList(2, 6)
            for (bitIndex in 0 until 32) {
                val byteIndex = bitIndex / 8
                val bitInByte = 7 - (bitIndex % 8)
                val isSupported = (data[byteIndex] and (1 shl bitInByte)) != 0
                if (isSupported) {
                    supported.add(basePid + bitIndex + 1)
                }
            }

            hasNextGroup = (data[3] and 0x01) != 0
            basePid += 0x20
        }

        return supported.associateWith { true }
    }

    suspend fun readLiveData(supportedPids: Set<Int>? = null): LivePidSnapshot {
        return LivePidSnapshot(
            engineRpm = readPidIfSupported(0x0C, supportedPids)?.let { bytes ->
                if (bytes.size >= 4) ((bytes[2] * 256) + bytes[3]) / 4.0 else null
            },
            vehicleSpeedKph = readPidIfSupported(0x0D, supportedPids)?.getOrNull(2),
            coolantTempCelsius = readPidIfSupported(0x05, supportedPids)?.getOrNull(2)?.minus(40),
            intakeTempCelsius = readPidIfSupported(0x0F, supportedPids)?.getOrNull(2)?.minus(40),
            engineLoadPercent = readPidIfSupported(0x04, supportedPids)?.getOrNull(2)?.let { value ->
                value * 100.0 / 255.0
            },
            shortTermFuelTrimPercent = readPidIfSupported(0x06, supportedPids)?.getOrNull(2)?.let { value ->
                (value - 128) * 100.0 / 128.0
            },
            longTermFuelTrimPercent = readPidIfSupported(0x07, supportedPids)?.getOrNull(2)?.let { value ->
                (value - 128) * 100.0 / 128.0
            },
            batteryVoltageVolts = readPidIfSupported(0x42, supportedPids)?.let { bytes ->
                if (bytes.size >= 4) (bytes[2] * 256 + bytes[3]) / 1000.0 else null
            }
        )
    }

    suspend fun readStoredDtcs(): List<String> {
        return readDtcs("03", 0x43)
    }

    suspend fun readPendingDtcs(): List<String> {
        return readDtcs("07", 0x47)
    }

    suspend fun readVin(): String? {
        val response = executeWithDiagnostics("09 02")
        val frames = response.lines
            .mapNotNull { parseHexBytes(it) }
            .filter { it.size >= 3 && it[0] == 0x49 && it[1] == 0x02 }
            .map { bytes ->
                val frameIndex = bytes[2]
                frameIndex to bytes.drop(3)
            }
            .sortedBy { it.first }

        if (frames.isEmpty()) {
            return null
        }

        val vinBytes = frames.flatMap { it.second }
            .filter { it != 0 }
        return vinBytes.map { it.toChar() }.joinToString("").ifBlank { null }
    }

    private suspend fun readPid(pid: Int): List<Int>? {
        val response = executeWithDiagnostics(String.format("01 %02X", pid))
        return response.lines
            .mapNotNull { parseHexBytes(it) }
            .firstOrNull { it.size >= 3 && it[0] == 0x41 && it[1] == pid }
    }

    private suspend fun readPidIfSupported(pid: Int, supportedPids: Set<Int>?): List<Int>? {
        if (supportedPids != null && pid !in supportedPids) {
            return null
        }
        return readPid(pid)
    }

    private suspend fun readDtcs(command: String, modeByte: Int): List<String> {
        val response = executeWithDiagnostics(command)
        val dtcs = mutableListOf<String>()
        response.lines
            .mapNotNull { parseHexBytes(it) }
            .filter { it.isNotEmpty() && it[0] == modeByte }
            .forEach { bytes ->
                val payload = bytes.drop(1)
                payload.chunked(2).forEach { pair ->
                    if (pair.size == 2) {
                        val dtc = decodeDtc(pair[0], pair[1])
                        if (dtc != null) {
                            dtcs.add(dtc)
                        }
                    }
                }
            }
        return dtcs
    }

    private suspend fun executeWithDiagnostics(command: String) = try {
        val response = session.execute(command)
        val errorType = classifyResponse(response)
        emitDiagnostics(command, response.raw, errorType)
        response
    } catch (error: TimeoutCancellationException) {
        emitDiagnostics(command, null, ObdErrorType.TIMEOUT)
        throw error
    } catch (error: IOException) {
        val errorType = classifyIoError(error)
        emitDiagnostics(command, null, errorType)
        throw error
    }

    private fun classifyResponse(response: ElmResponse): ObdErrorType? {
        val raw = response.raw
        return when {
            raw.contains("NO DATA", ignoreCase = true) -> ObdErrorType.NO_DATA
            raw.contains("UNABLE TO CONNECT", ignoreCase = true) -> ObdErrorType.UNABLE_TO_CONNECT
            response.lines.any { line -> line.trim().uppercase().startsWith("7F") } ->
                ObdErrorType.NEGATIVE_RESPONSE
            else -> null
        }
    }

    private fun classifyIoError(error: IOException): ObdErrorType? {
        val message = error.message?.uppercase().orEmpty()
        return if (message.contains("CLOSED")) {
            ObdErrorType.SOCKET_CLOSED
        } else {
            null
        }
    }

    private fun emitDiagnostics(command: String, rawResponse: String?, errorType: ObdErrorType?) {
        val diagnostics = ObdDiagnostics(
            command = command.trim(),
            rawResponse = rawResponse,
            errorType = errorType
        )
        if (errorType == null) {
            Log.d("OBD", "diag command=${diagnostics.command} raw=${rawResponse.orEmpty()}")
        } else {
            Log.w("OBD", "diag command=${diagnostics.command} error=$errorType raw=${rawResponse.orEmpty()}")
        }
        diagnosticsListener(diagnostics)
    }

    private fun decodeDtc(firstByte: Int, secondByte: Int): String? {
        if (firstByte == 0 && secondByte == 0) {
            return null
        }
        val type = when ((firstByte and 0xC0) shr 6) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val digit1 = (firstByte and 0x30) shr 4
        val digit2 = firstByte and 0x0F
        val digit3 = (secondByte and 0xF0) shr 4
        val digit4 = secondByte and 0x0F
        return buildString {
            append(type)
            append(digit1)
            append(digit2.toString(16).uppercase())
            append(digit3.toString(16).uppercase())
            append(digit4.toString(16).uppercase())
        }
    }

    private fun parseHexBytes(line: String): List<Int>? {
        if (line.contains("NO DATA", ignoreCase = true)) {
            return null
        }
        val matches = HEX_PAIR_REGEX.findAll(line)
            .map { it.value }
            .toList()
        if (matches.isEmpty()) {
            return null
        }
        return matches.map { it.toInt(16) }
    }

    private companion object {
        val HEX_PAIR_REGEX = Regex("[0-9A-Fa-f]{2}")
    }
}

data class LivePidSnapshot(
    val engineRpm: Double?,
    val vehicleSpeedKph: Int?,
    val coolantTempCelsius: Int?,
    val intakeTempCelsius: Int?,
    val engineLoadPercent: Double?,
    val shortTermFuelTrimPercent: Double?,
    val longTermFuelTrimPercent: Double?,
    val batteryVoltageVolts: Double?
)

data class LiveMetricDefinition(
    val pid: Int,
    val name: String
)

val DEFAULT_LIVE_METRICS = listOf(
    LiveMetricDefinition(0x0C, "Обороты двигателя"),
    LiveMetricDefinition(0x0D, "Скорость автомобиля"),
    LiveMetricDefinition(0x05, "Температура охлаждающей жидкости"),
    LiveMetricDefinition(0x0F, "Температура впуска"),
    LiveMetricDefinition(0x04, "Нагрузка двигателя"),
    LiveMetricDefinition(0x06, "Краткосрочная коррекция топлива"),
    LiveMetricDefinition(0x07, "Долгосрочная коррекция топлива"),
    LiveMetricDefinition(0x42, "Напряжение модуля управления")
)

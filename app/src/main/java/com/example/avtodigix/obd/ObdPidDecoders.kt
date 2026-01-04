package com.example.avtodigix.obd

import java.util.Locale

data class DecodedValue(
    val value: String,
    val unit: String?
)

typealias ObdPidDecoder = (List<Int>) -> DecodedValue

val PID_DECODERS: Map<Int, ObdPidDecoder> = mapOf(
    0x0C to { bytes ->
        val rpm = if (bytes.size >= 4) ((bytes[2] * 256) + bytes[3]) / 4.0 else null
        DecodedValue(
            value = rpm?.let { formatNumber(it) } ?: "n/a",
            unit = if (rpm != null) "rpm" else null
        )
    },
    0x0D to { bytes ->
        val speed = bytes.getOrNull(2)
        DecodedValue(
            value = speed?.toString() ?: "n/a",
            unit = if (speed != null) "km/h" else null
        )
    },
    0x05 to { bytes ->
        val temp = bytes.getOrNull(2)?.minus(40)
        DecodedValue(
            value = temp?.toString() ?: "n/a",
            unit = if (temp != null) "°C" else null
        )
    },
    0x04 to { bytes ->
        val load = bytes.getOrNull(2)?.let { value -> value * 100.0 / 255.0 }
        DecodedValue(
            value = load?.let { formatNumber(it) } ?: "n/a",
            unit = if (load != null) "%" else null
        )
    },
    0x06 to { bytes ->
        val trim = bytes.getOrNull(2)?.let { value -> (value - 128) * 100.0 / 128.0 }
        DecodedValue(
            value = trim?.let { formatNumber(it) } ?: "n/a",
            unit = if (trim != null) "%" else null
        )
    },
    0x07 to { bytes ->
        val trim = bytes.getOrNull(2)?.let { value -> (value - 128) * 100.0 / 128.0 }
        DecodedValue(
            value = trim?.let { formatNumber(it) } ?: "n/a",
            unit = if (trim != null) "%" else null
        )
    },
    0x0F to { bytes ->
        val temp = bytes.getOrNull(2)?.minus(40)
        DecodedValue(
            value = temp?.toString() ?: "n/a",
            unit = if (temp != null) "°C" else null
        )
    },
    0x42 to { bytes ->
        val voltage = if (bytes.size >= 4) (bytes[2] * 256 + bytes[3]) / 1000.0 else null
        DecodedValue(
            value = voltage?.let { formatNumber(it) } ?: "n/a",
            unit = if (voltage != null) "V" else null
        )
    }
)

private fun formatNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

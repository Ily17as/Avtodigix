package com.example.avtodigix.storage

enum class WifiResponseFormat {
    Text
}

data class WifiScanSnapshot(
    val timestampMillis: Long,
    val host: String,
    val port: Int,
    val responseFormat: WifiResponseFormat,
    val keyMetrics: Map<String, Double>,
    val dtcList: List<String>
)

fun WifiScanSnapshot.toEntity(): WifiScanSnapshotEntity {
    return WifiScanSnapshotEntity(
        timestampMillis = timestampMillis,
        host = host,
        port = port,
        responseFormat = responseFormat,
        keyMetrics = keyMetrics,
        dtcList = dtcList
    )
}

fun WifiScanSnapshotEntity.toModel(): WifiScanSnapshot {
    return WifiScanSnapshot(
        timestampMillis = timestampMillis,
        host = host,
        port = port,
        responseFormat = responseFormat,
        keyMetrics = keyMetrics,
        dtcList = dtcList
    )
}

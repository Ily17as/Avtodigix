package com.example.avtodigix.storage

data class ScanSnapshot(
    val id: Long = 0,
    val timestampMillis: Long,
    val keyMetrics: Map<String, Double>,
    val dtcList: List<String>
)

fun ScanSnapshot.toEntity(): ScanSnapshotEntity {
    return ScanSnapshotEntity(
        id = id,
        timestampMillis = timestampMillis,
        keyMetrics = keyMetrics,
        dtcList = dtcList
    )
}

fun ScanSnapshotEntity.toModel(): ScanSnapshot {
    return ScanSnapshot(
        id = id,
        timestampMillis = timestampMillis,
        keyMetrics = keyMetrics,
        dtcList = dtcList
    )
}

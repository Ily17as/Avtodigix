package com.example.avtodigix.obd

data class ObdPidRecord(
    val mode: Int,
    val pid: Int,
    val timestampMillis: Long,
    val raw: String?,
    val bytes: List<Int>?,
    val decodedValue: String?,
    val unit: String?,
    val errorType: ObdErrorType?
)

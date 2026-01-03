package com.example.avtodigix.obd

data class ObdDiagnostics(
    val command: String,
    val rawResponse: String?,
    val errorType: ObdErrorType?
)

enum class ObdErrorType {
    TIMEOUT,
    NO_DATA,
    UNABLE_TO_CONNECT,
    SOCKET_CLOSED,
    NEGATIVE_RESPONSE
}

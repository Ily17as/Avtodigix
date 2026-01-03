package com.example.avtodigix.transport

import java.io.InputStream
import java.io.OutputStream

interface ScannerTransport {
    val input: InputStream
    val output: OutputStream
    val isConnected: Boolean

    suspend fun close()
}

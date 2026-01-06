package com.example.avtodigix.elm

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ElmSession(
    private val input: InputStream,
    private val output: OutputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parentScope: CoroutineScope? = null,
    private val rateLimitDelayMillis: Long = 120,
    private val responseTimeoutMillis: Long = 5_000,
    private val maxRetries: Int = 2,
    private val promptChar: Char = '>'
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val commandQueue = Channel<QueuedCommand>(Channel.UNLIMITED)
    private val queueSizeState = MutableStateFlow(0)
    private val workerJob: Job
    private var lastSentAtMillis = 0L
    private var includeHeadersOff = false

    init {
        workerJob = scope.launch {
            for (next in commandQueue) {
                if (!isActive) break
                queueSizeState.value = queueSizeState.value - 1
                val result = runCatching { sendWithRetry(next.command, next.allowReset) }
                // Ensure we don't crash if the deferred is already cancelled
                runCatching { next.deferred.complete(result) }
            }
        }
    }

    val queueSize: StateFlow<Int> = queueSizeState

    suspend fun initialize(includeHeadersOff: Boolean = false): List<String> {
        this.includeHeadersOff = includeHeadersOff
        val responses = mutableListOf<String>()
        val initCommands = buildList {
            add("ATZ")
            add("ATE0")
            add("ATL0")
            add("ATS0")
            if (includeHeadersOff) {
                add("ATH0")
            }
            add("ATSP0")
        }
        for (command in initCommands) {
            val response = execute(command)
            responses.addAll(response.lines)
        }
        return responses
    }

    suspend fun execute(command: String, allowReset: Boolean = true): ElmResponse {
        val deferred = CompletableDeferred<Result<ElmResponse>>()
        commandQueue.send(QueuedCommand(command, allowReset, deferred))
        queueSizeState.value = queueSizeState.value + 1
        return deferred.await().getOrElse { throw it }
    }

    suspend fun close() {
        commandQueue.close()
        workerJob.cancel()
    }

    private suspend fun sendWithRetry(command: String, allowReset: Boolean): ElmResponse {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            attempt += 1
            try {
                return sendCommand(command)
            } catch (error: TimeoutCancellationException) {
                Log.w("OBD", "timedOut=true command=${command.trim()}")
                lastError = error
                if (allowReset) {
                    resetAdapter()
                }
            } catch (error: IOException) {
                Log.w("OBD", "ioError command=${command.trim()} message=${error.message}")
                lastError = error
                if (allowReset && !command.equals("ATZ", ignoreCase = true)) {
                    resetAdapter()
                }
            }
        }
        throw lastError ?: IllegalStateException("ELM command failed: $command")
    }

    private suspend fun resetAdapter() {
        val initCommands = buildList {
            add("ATZ")
            add("ATE0")
            add("ATL0")
            add("ATS0")
            if (includeHeadersOff) {
                add("ATH0")
            }
            add("ATSP0")
            add("0100")
        }
        initCommands.forEach { command ->
            val allowEmpty = command.equals("ATZ", ignoreCase = true)
            runCatching { sendCommand(command, allowEmpty = allowEmpty) }
        }
    }

    private suspend fun sendCommand(
        command: String,
        allowEmpty: Boolean = false
    ): ElmResponse = withContext(ioDispatcher) {
        enforceRateLimit()
        drainInput()
        val normalizedCommand = command.trim()
        Log.d("OBD", "TX $normalizedCommand")
        val payload = "$normalizedCommand\r"
        output.write(payload.toByteArray())
        output.flush()
        val raw = readUntilPromptCancellable()
        val parsed = parseResponse(normalizedCommand, raw)
        Log.d("OBD", "RX raw=$raw lines=$parsed")
        if (parsed.isEmpty() && !allowEmpty) {
            val escapedRaw = raw.replace("\r", "\\r").replace("\n", "\\n")
            throw IOException(
                "ELM returned empty response for $normalizedCommand raw=$escapedRaw"
            )
        }
        ElmResponse(raw, parsed)
    }

    private suspend fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSentAtMillis
        if (elapsed < rateLimitDelayMillis) {
            delay(rateLimitDelayMillis - elapsed)
        }
        lastSentAtMillis = System.currentTimeMillis()
    }

    private suspend fun drainInput() {
        val buffer = ByteArray(256)
        while (input.available() > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, input.available()))
            if (read <= 0) {
                break
            }
        }
    }

    private suspend fun readUntilPromptCancellable(): String = withTimeout(responseTimeoutMillis) {
        val builder = StringBuilder()
        val buffer = ByteArray(256)
        while (true) {
            val read = runInterruptible { input.read(buffer) }
            if (read == -1) {
                throw IOException("ELM input stream closed")
            }
            val chunk = String(buffer, 0, read)
            builder.append(chunk)
            if (hasPromptTerminator(builder)) {
                break
            }
        }
        drainTrailingWhitespace(builder)
        builder.toString()
    }

    private fun hasPromptTerminator(builder: StringBuilder): Boolean {
        val lastPromptIndex = builder.lastIndexOf(promptChar)
        if (lastPromptIndex == -1) {
            return false
        }
        for (index in lastPromptIndex + 1 until builder.length) {
            if (!builder[index].isWhitespace()) {
                return false
            }
        }
        return true
    }

    private fun drainTrailingWhitespace(builder: StringBuilder) {
        val buffer = ByteArray(64)
        while (input.available() > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, input.available()))
            if (read <= 0) {
                break
            }
            builder.append(String(buffer, 0, read))
        }
    }

    private fun parseResponse(command: String, raw: String): List<String> {
        val lines = raw
            .replace(promptChar.toString(), "")
            .split("\r", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val normalizedCommand = normalizeLine(command)
        return lines.filter { line ->
            val normalizedLine = normalizeLine(line)
            normalizedLine != normalizedCommand && !normalizedLine.startsWith("SEARCHING")
        }
    }

    private fun normalizeLine(line: String): String {
        return line.replace(" ", "").uppercase()
    }

    private data class QueuedCommand(
        val command: String,
        val allowReset: Boolean,
        val deferred: CompletableDeferred<Result<ElmResponse>>
    )
}

data class ElmResponse(
    val raw: String,
    val lines: List<String>
)

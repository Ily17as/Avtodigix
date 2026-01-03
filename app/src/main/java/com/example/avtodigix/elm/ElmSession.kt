package com.example.avtodigix.elm

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
    private val responseTimeoutMillis: Long = 2_000,
    private val maxRetries: Int = 2,
    private val promptChar: Char = '>'
) {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
    private val commandQueue = Channel<QueuedCommand>(Channel.UNLIMITED)
    private val queueSizeState = MutableStateFlow(0)
    private val workerJob: Job
    private var lastSentAtMillis = 0L

    init {
        workerJob = scope.launch {
            for (next in commandQueue) {
                if (!isActive) break
                queueSizeState.value = queueSizeState.value - 1
                val result = runCatching { sendWithRetry(next.command) }
                // Ensure we don't crash if the deferred is already cancelled
                runCatching { next.deferred.complete(result) }
            }
        }
    }

    val queueSize: StateFlow<Int> = queueSizeState

    suspend fun initialize(includeHeadersOff: Boolean = false): List<String> {
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

    suspend fun execute(command: String): ElmResponse {
        val deferred = CompletableDeferred<Result<ElmResponse>>()
        commandQueue.send(QueuedCommand(command, deferred))
        queueSizeState.value = queueSizeState.value + 1
        return deferred.await().getOrElse { throw it }
    }

    suspend fun close() {
        commandQueue.close()
        workerJob.cancel()
    }

    private suspend fun sendWithRetry(command: String): ElmResponse {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            attempt += 1
            try {
                return sendCommand(command)
            } catch (error: TimeoutCancellationException) {
                lastError = error
                resetAdapter()
            } catch (error: IOException) {
                lastError = error
                if (!command.equals("ATZ", ignoreCase = true)) {
                    resetAdapter()
                }
            }
        }
        throw lastError ?: IllegalStateException("ELM command failed: $command")
    }

    private suspend fun resetAdapter() {
        runCatching { sendCommand("ATZ", allowEmpty = true) }
    }

    private suspend fun sendCommand(
        command: String,
        allowEmpty: Boolean = false
    ): ElmResponse = withContext(ioDispatcher) {
        enforceRateLimit()
        val normalizedCommand = command.trim()
        val payload = "$normalizedCommand\r"
        output.write(payload.toByteArray())
        output.flush()
        val raw = readUntilPrompt()
        val parsed = parseResponse(normalizedCommand, raw)
        if (parsed.isEmpty() && !allowEmpty) {
            throw IOException("ELM returned empty response for $normalizedCommand")
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

    private suspend fun readUntilPrompt(): String = withTimeout(responseTimeoutMillis) {
        val builder = StringBuilder()
        val buffer = ByteArray(256)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) {
                throw IOException("ELM input stream closed")
            }
            val chunk = String(buffer, 0, read)
            builder.append(chunk)
            if (chunk.indexOf(promptChar) >= 0) {
                break
            }
        }
        builder.toString()
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
        val deferred: CompletableDeferred<Result<ElmResponse>>
    )
}

data class ElmResponse(
    val raw: String,
    val lines: List<String>
)

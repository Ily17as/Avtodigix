package com.example.avtodigix.feedback

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpFeedbackSender : FeedbackSender {
    override suspend fun send(payload: FeedbackPayload): FeedbackSendResult = withContext(Dispatchers.IO) {
        val connection = (URL(FORM_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val body = JSONObject().apply {
                put("rating", payload.rating)
                put("comment", payload.comment)
                put("timestamp_utc", payload.submittedAtUtc)
                put("app_version", payload.appVersion)
                put("build", payload.buildNumber)
                put("platform", payload.platform)
                payload.deviceModel?.takeIf { it.isNotBlank() }?.let { put("device_model", it) }
                put("tags", JSONArray(payload.tags))
                put("connection_type", payload.connectionType)
                put("last_session_result", payload.lastSessionResult)
                put("dtc_count", payload.dtcCount)
            }.toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let {
                BufferedReader(InputStreamReader(it)).use { br -> br.readText() }
            }

            val isSuccess = code in 200..299
            Log.i(TAG, "Feedback submit result: success=$isSuccess code=$code")
            FeedbackSendResult(
                success = isSuccess,
                responseCode = code,
                responseBody = response
            )
        } catch (ioException: IOException) {
            Log.e(TAG, "Feedback submit failed: network I/O error", ioException)
            throw ioException
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FORM_URL = "https://forms.yandex.ru/u/681f9b4084227c924223e522"
        private const val TIMEOUT_MILLIS = 10_000
        private const val TAG = "HttpFeedbackSender"

        private const val REDIRECT_SOURCE_PARAM = "source"
        private const val REDIRECT_SOURCE_VALUE = "avtodigix"

        // Проверено 2026-02-08 через опубликованную форму:
        // 1) GET https://forms.yandex.ru/u/gateway/root/form/getSurvey
        // 2) ручная проверка prefill в URL через Playwright.
        // Поле "Комментарий" подхватывает только внутренний id answer_long_text_96199.
        // В опубликованной форме нет отдельного поля "Оценка" (1..5), поэтому используем публичный ключ rating.
        private const val REDIRECT_RATING_PARAM = "rating"
        private const val REDIRECT_COMMENT_PARAM = "answer_long_text_96199"
        private const val FEEDBACK_EMPTY_FEATURES = "не выбрано"
        private const val FEEDBACK_EMPTY_COMMENT = "—"

        fun buildRedirectUrl(payload: FeedbackPayload): String {
            val normalizedRating = payload.rating.coerceIn(1, 5)
            val feedbackMessage = buildFeedbackMessage(payload, normalizedRating)

            return Uri.parse(FORM_URL)
                .buildUpon()
                .appendQueryParameter(REDIRECT_RATING_PARAM, normalizedRating.toString())
                .appendQueryParameter(REDIRECT_COMMENT_PARAM, feedbackMessage)
                .appendQueryParameter(REDIRECT_SOURCE_PARAM, REDIRECT_SOURCE_VALUE)
                .build()
                .toString()
        }

        private fun buildFeedbackMessage(payload: FeedbackPayload, normalizedRating: Int): String {
            val features = payload.tags
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { FEEDBACK_EMPTY_FEATURES }
            val comment = payload.comment.trim().ifBlank { FEEDBACK_EMPTY_COMMENT }

            return buildString {
                append("Оценка: ")
                append(normalizedRating)
                append("/5\n")
                append("Понравилось: ")
                append(features)
                append("\n")
                append("Комментарий: ")
                append(comment)
            }
        }
    }
}

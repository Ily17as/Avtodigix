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

        fun buildFallbackUrl(payload: FeedbackPayload): String {
            return Uri.parse(FORM_URL)
                .buildUpon()
                .appendQueryParameter("rating", payload.rating.toString())
                .appendQueryParameter("comment", payload.comment)
                .appendQueryParameter("timestamp_utc", payload.submittedAtUtc)
                .appendQueryParameter("app_version", payload.appVersion)
                .appendQueryParameter("build", payload.buildNumber.toString())
                .appendQueryParameter("platform", payload.platform)
                .appendQueryParameter("device_model", payload.deviceModel.orEmpty())
                .build()
                .toString()
        }
    }
}

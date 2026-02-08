package com.example.avtodigix.feedback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FeedbackLocalStore(context: Context) {
    private val outputFile = File(context.filesDir, FILE_NAME)

    fun save(payload: FeedbackPayload) {
        val json = JSONObject().apply {
            put("rating", payload.rating)
            put("tags", JSONArray(payload.tags))
            put("comment", payload.comment)
            put("app_version", payload.appVersion)
            put("build_number", payload.buildNumber)
            put("platform", payload.platform)
            put("device_model", payload.deviceModel)
            put("connection_type", payload.connectionType)
            put("last_session_result", payload.lastSessionResult)
            put("dtc_count", payload.dtcCount)
            put("submitted_at_utc", payload.submittedAtUtc)
            put("submitted_at_millis", payload.submittedAtMillis)
        }
        outputFile.appendText(json.toString() + "\n")
    }

    private companion object {
        private const val FILE_NAME = "feedback_queue.jsonl"
    }
}

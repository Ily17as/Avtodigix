package com.example.avtodigix.analytics

import android.util.Log

class LogcatAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, Any>) {
        val payload = if (params.isEmpty()) {
            "{}"
        } else {
            params.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "$key=$value"
            }
        }
        Log.d(TAG, "event=$name params=$payload")
    }

    private companion object {
        private const val TAG = "AnalyticsTracker"
    }
}

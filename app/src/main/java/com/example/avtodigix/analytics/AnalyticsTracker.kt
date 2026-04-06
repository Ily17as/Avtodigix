package com.example.avtodigix.analytics

interface AnalyticsTracker {
    fun trackEvent(name: String, params: Map<String, Any> = emptyMap())
}

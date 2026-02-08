package com.example.avtodigix.ui

import android.content.Context
import com.example.avtodigix.R

object RecommendationsProvider {
    enum class Scenario {
        CONNECTION,
        DIAGNOSTICS
    }

    data class RecommendationsContent(
        val title: String,
        val items: List<String>
    )

    fun getContent(context: Context, scenario: Scenario): RecommendationsContent {
        return when (scenario) {
            Scenario.CONNECTION -> RecommendationsContent(
                title = context.getString(R.string.connection_troubleshoot_title),
                items = listOf(
                    context.getString(R.string.recommendation_connection_power),
                    context.getString(R.string.recommendation_connection_radio),
                    context.getString(R.string.recommendation_connection_pairing),
                    context.getString(R.string.recommendation_connection_restart)
                )
            )
            Scenario.DIAGNOSTICS -> RecommendationsContent(
                title = context.getString(R.string.dtc_troubleshoot_title),
                items = listOf(
                    context.getString(R.string.recommendation_dtc_primary_fix),
                    context.getString(R.string.recommendation_dtc_pending_observe),
                    context.getString(R.string.recommendation_dtc_save_report),
                    context.getString(R.string.recommendation_dtc_clear_after_fix)
                )
            )
        }
    }
}

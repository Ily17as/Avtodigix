package com.example.avtodigix.ui.main

import android.content.Context
import com.example.avtodigix.R
import com.example.avtodigix.domain.DtcDescriptions
import com.example.avtodigix.storage.ScanSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buildCurrentDtcReport(context: Context, stored: List<String>, pending: List<String>): String {
    val summary = context.getString(
        R.string.dtc_summary_format,
        stored.size,
        pending.size
    )
    val dtcLines = (stored.map { it to context.getString(R.string.dtc_badge_stored) } +
        pending.map { it to context.getString(R.string.dtc_badge_pending) })
        .distinctBy { it.first }
        .joinToString("\n") { (code, type) ->
            val description = DtcDescriptions.descriptionFor(code)
                ?: context.getString(R.string.dtc_description_unknown)
            "$code ($type): $description"
        }
        .ifBlank { context.getString(R.string.dtc_none) }

    return buildString {
        appendLine(context.getString(R.string.report_current_title))
        appendLine(summary)
        appendLine()
        appendLine(context.getString(R.string.dtc_title))
        appendLine(dtcLines)
    }
}

fun buildSnapshotReport(context: Context, snapshot: ScanSnapshot): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val timestamp = formatter.format(Date(snapshot.timestampMillis))
    val metrics = if (snapshot.keyMetrics.isEmpty()) {
        "—"
    } else {
        snapshot.keyMetrics.entries.joinToString("\n") { (name, value) -> "$name: $value" }
    }
    val dtcs = if (snapshot.dtcList.isEmpty()) {
        context.getString(R.string.dtc_none)
    } else {
        snapshot.dtcList.joinToString("\n") { code ->
            val description = DtcDescriptions.descriptionFor(code)
                ?: context.getString(R.string.dtc_description_unknown)
            "$code: $description"
        }
    }

    return buildString {
        appendLine(context.getString(R.string.report_history_title))
        appendLine(timestamp)
        appendLine(context.getString(R.string.report_dtc_count, snapshot.dtcList.size))
        appendLine()
        appendLine(context.getString(R.string.report_metrics_title))
        appendLine(metrics)
        appendLine()
        appendLine(context.getString(R.string.dtc_title))
        appendLine(dtcs)
    }
}

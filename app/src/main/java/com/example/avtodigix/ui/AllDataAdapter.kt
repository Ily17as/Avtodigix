package com.example.avtodigix.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.R
import com.google.android.material.button.MaterialButton

sealed class AllDataSection {
    data class LiveMetrics(
        val metrics: List<Pair<String, String>>,
        val milStatus: String
    ) : AllDataSection()

    data class Dtc(
        val stored: String,
        val pending: String
    ) : AllDataSection()

    data class SupportedPids(
        val title: String,
        val list: String
    ) : AllDataSection()

    data class FullScan(
        val inProgress: Boolean
    ) : AllDataSection()

    data class FullScanList(
        val entries: List<FullScanEntry>,
        val emptyMessage: String
    ) : AllDataSection()

    data class RawLog(
        val log: String
    ) : AllDataSection()
}

data class FullScanEntry(
    val pidLabel: String,
    val valueLabel: String,
    val statusLabel: String
)

class AllDataAdapter(
    private val onFullScanRequested: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<AllDataSection>()
    private var showAllFullScan = false

    fun submitList(sections: List<AllDataSection>) {
        items.clear()
        items.addAll(sections)
        showAllFullScan = false
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AllDataSection.LiveMetrics -> VIEW_TYPE_METRICS
            is AllDataSection.Dtc -> VIEW_TYPE_DTC
            is AllDataSection.SupportedPids -> VIEW_TYPE_SUPPORTED_PIDS
            is AllDataSection.FullScan -> VIEW_TYPE_FULL_SCAN
            is AllDataSection.FullScanList -> VIEW_TYPE_FULL_SCAN_LIST
            is AllDataSection.RawLog -> VIEW_TYPE_RAW_LOG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_METRICS -> LiveMetricsViewHolder(
                inflater.inflate(R.layout.item_all_data_live_metrics, parent, false)
            )
            VIEW_TYPE_DTC -> DtcViewHolder(
                inflater.inflate(R.layout.item_all_data_dtc, parent, false)
            )
            VIEW_TYPE_SUPPORTED_PIDS -> SupportedPidsViewHolder(
                inflater.inflate(R.layout.item_all_data_supported_pids, parent, false)
            )
            VIEW_TYPE_FULL_SCAN -> FullScanViewHolder(
                inflater.inflate(R.layout.item_all_data_full_scan, parent, false),
                onFullScanRequested
            )
            VIEW_TYPE_FULL_SCAN_LIST -> FullScanListViewHolder(
                inflater.inflate(R.layout.item_all_data_full_scan_results, parent, false)
            )
            VIEW_TYPE_RAW_LOG -> RawLogViewHolder(
                inflater.inflate(R.layout.item_all_data_raw_log, parent, false)
            )
            else -> error("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AllDataSection.LiveMetrics -> (holder as LiveMetricsViewHolder).bind(item)
            is AllDataSection.Dtc -> (holder as DtcViewHolder).bind(item)
            is AllDataSection.SupportedPids -> (holder as SupportedPidsViewHolder).bind(item)
            is AllDataSection.FullScan -> (holder as FullScanViewHolder).bind(item)
            is AllDataSection.FullScanList -> (holder as FullScanListViewHolder).bind(
                item,
                showAllFullScan,
                ::toggleFullScanShowAll
            )
            is AllDataSection.RawLog -> (holder as RawLogViewHolder).bind(item)
        }
    }

    private class LiveMetricsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val metricsList: TextView = view.findViewById(R.id.allDataMetricsList)
        private val milStatus: TextView = view.findViewById(R.id.allDataMilStatus)

        fun bind(item: AllDataSection.LiveMetrics) {
            metricsList.text = item.metrics.joinToString(separator = "\n") { (label, value) ->
                "$label: $value"
            }
            milStatus.text = item.milStatus
        }
    }

    private class DtcViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stored: TextView = view.findViewById(R.id.allDataDtcStored)
        private val pending: TextView = view.findViewById(R.id.allDataDtcPending)

        fun bind(item: AllDataSection.Dtc) {
            stored.text = item.stored
            pending.text = item.pending
        }
    }

    private class SupportedPidsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.allDataSupportedPidsTitle)
        private val list: TextView = view.findViewById(R.id.allDataSupportedPidsList)

        fun bind(item: AllDataSection.SupportedPids) {
            title.text = item.title
            list.text = item.list
        }
    }

    private class FullScanViewHolder(
        view: View,
        onFullScanRequested: () -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val button: MaterialButton = view.findViewById(R.id.allDataFullScanButton)
        private val progress: ProgressBar = view.findViewById(R.id.allDataFullScanProgress)

        init {
            button.setOnClickListener { onFullScanRequested() }
        }

        fun bind(item: AllDataSection.FullScan) {
            progress.visibility = if (item.inProgress) View.VISIBLE else View.GONE
            button.setText(
                if (item.inProgress) {
                    R.string.all_data_full_scan_cancel
                } else {
                    R.string.all_data_full_scan_action
                }
            )
        }
    }

    private class FullScanListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val results: TextView = view.findViewById(R.id.allDataFullScanResults)
        private val toggle: MaterialButton = view.findViewById(R.id.allDataFullScanToggle)

        fun bind(
            item: AllDataSection.FullScanList,
            showAll: Boolean,
            onToggleRequested: () -> Unit
        ) {
            val entries = if (showAll) {
                item.entries
            } else {
                item.entries.take(FULL_SCAN_VISIBLE_LIMIT)
            }
            results.text = if (entries.isNotEmpty()) {
                entries.joinToString(separator = "\n") { entry ->
                    "${entry.pidLabel} — ${entry.valueLabel} (${entry.statusLabel})"
                }
            } else {
                item.emptyMessage
            }
            val hasMore = item.entries.size > FULL_SCAN_VISIBLE_LIMIT
            toggle.visibility = if (hasMore) View.VISIBLE else View.GONE
            toggle.setText(
                if (showAll) {
                    R.string.all_data_full_scan_show_less
                } else {
                    R.string.all_data_full_scan_show_all
                }
            )
            toggle.setOnClickListener { onToggleRequested() }
        }
    }

    private class RawLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val log: TextView = view.findViewById(R.id.allDataRawLog)

        fun bind(item: AllDataSection.RawLog) {
            log.text = item.log
        }
    }

    private fun toggleFullScanShowAll() {
        showAllFullScan = !showAllFullScan
        val index = items.indexOfFirst { it is AllDataSection.FullScanList }
        if (index >= 0) {
            notifyItemChanged(index)
        } else {
            notifyDataSetChanged()
        }
    }

    private companion object {
        const val VIEW_TYPE_METRICS = 1
        const val VIEW_TYPE_DTC = 2
        const val VIEW_TYPE_SUPPORTED_PIDS = 3
        const val VIEW_TYPE_FULL_SCAN = 4
        const val VIEW_TYPE_FULL_SCAN_LIST = 5
        const val VIEW_TYPE_RAW_LOG = 6
        const val FULL_SCAN_VISIBLE_LIMIT = 150
    }
}

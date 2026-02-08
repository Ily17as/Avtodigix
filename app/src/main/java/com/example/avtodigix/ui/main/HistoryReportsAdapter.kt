package com.example.avtodigix.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.R
import com.example.avtodigix.databinding.ItemHistoryReportBinding
import com.example.avtodigix.storage.ScanSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryReportsAdapter(
    private val onOpen: (ScanSnapshot) -> Unit,
    private val onShare: (ScanSnapshot) -> Unit
) : RecyclerView.Adapter<HistoryReportsAdapter.Holder>() {
    private val items = mutableListOf<ScanSnapshot>()

    fun submit(data: List<ScanSnapshot>) {
        items.clear()
        items.addAll(data.sortedByDescending { it.timestampMillis })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemHistoryReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onOpen, onShare)
    }

    class Holder(private val binding: ItemHistoryReportBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScanSnapshot, onOpen: (ScanSnapshot) -> Unit, onShare: (ScanSnapshot) -> Unit) {
            val context = binding.root.context
            val formatted = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(item.timestampMillis))
            val statusText = if (item.dtcList.isEmpty()) {
                context.getString(R.string.report_status_ok)
            } else {
                context.getString(R.string.report_status_has_issues)
            }
            binding.reportTime.text = formatted
            binding.reportSummary.text = context.getString(
                R.string.report_list_summary,
                item.dtcList.size,
                statusText
            )
            binding.root.setOnClickListener { onOpen(item) }
            binding.reportShareButton.setOnClickListener { onShare(item) }
        }
    }
}

package com.example.avtodigix.ui.dailycheck

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.R
import com.example.avtodigix.databinding.ItemDailyCheckHistoryBinding
import com.example.avtodigix.domain.CheckSession
import com.example.avtodigix.domain.TrafficLightStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyCheckHistoryAdapter : RecyclerView.Adapter<DailyCheckHistoryAdapter.Holder>() {
    private val items = mutableListOf<CheckSession>()

    fun submit(data: List<CheckSession>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemDailyCheckHistoryBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    class Holder(private val binding: ItemDailyCheckHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CheckSession) {
            val context = binding.root.context
            val formattedDateTime = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(item.finishedAtMillis))
            val score = when (item.trafficLightStatus) {
                TrafficLightStatus.GREEN -> 90
                TrafficLightStatus.YELLOW -> 70
                TrafficLightStatus.RED -> 40
            }
            val overallStatus = when (item.trafficLightStatus) {
                TrafficLightStatus.GREEN -> context.getString(R.string.daily_check_history_overall_ok)
                TrafficLightStatus.YELLOW -> context.getString(R.string.daily_check_history_overall_attention)
                TrafficLightStatus.RED -> context.getString(R.string.daily_check_history_overall_critical)
            }
            val driveStatus = if (item.trafficLightStatus == TrafficLightStatus.RED || !item.success) {
                context.getString(R.string.daily_check_history_drive_not_recommended)
            } else {
                context.getString(R.string.daily_check_history_drive_allowed)
            }
            val newSignalText = if (item.hasNewDtc) {
                context.getString(R.string.daily_check_history_new_signal)
            } else {
                context.getString(R.string.daily_check_history_no_new_signal)
            }

            binding.dailyCheckHistoryText.text = context.getString(
                R.string.daily_check_history_item_format,
                formattedDateTime,
                score,
                overallStatus,
                driveStatus,
                newSignalText
            )
        }
    }
}

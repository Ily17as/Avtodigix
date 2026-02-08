package com.example.avtodigix.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.R
import com.example.avtodigix.databinding.ItemIssueDtcBinding
import com.example.avtodigix.domain.DtcDescriptions

data class DtcItem(
    val code: String,
    val badge: String
)

class DtcItemAdapter : RecyclerView.Adapter<DtcItemAdapter.DtcViewHolder>() {
    private val items = mutableListOf<DtcItem>()

    fun submit(stored: List<String>, pending: List<String>) {
        items.clear()
        items += stored.map { DtcItem(it, "stored") }
        items += pending.filterNot { code -> stored.contains(code) }.map { DtcItem(it, "pending") }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DtcViewHolder {
        val binding = ItemIssueDtcBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DtcViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DtcViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class DtcViewHolder(private val binding: ItemIssueDtcBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DtcItem) {
            val context = binding.root.context
            binding.dtcCode.text = item.code
            binding.dtcDescription.text = DtcDescriptions.descriptionFor(item.code)
                ?: context.getString(R.string.dtc_description_unknown)
            binding.dtcBadge.text = if (item.badge == "stored") {
                context.getString(R.string.dtc_badge_stored)
            } else {
                context.getString(R.string.dtc_badge_pending)
            }
        }
    }
}

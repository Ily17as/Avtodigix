package com.example.avtodigix.connection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.bluetooth.PairedDevice
import com.example.avtodigix.databinding.ItemPairedDeviceBinding
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

class PairedDeviceAdapter(
    private val onSelected: (PairedDevice) -> Unit
) : RecyclerView.Adapter<PairedDeviceAdapter.PairedDeviceViewHolder>() {
    private val devices = mutableListOf<PairedDevice>()
    private var selectedAddress: String? = null

    fun submitList(items: List<PairedDevice>, selectedAddress: String?) {
        devices.clear()
        devices.addAll(items)
        this.selectedAddress = selectedAddress
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PairedDeviceViewHolder {
        val binding = ItemPairedDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PairedDeviceViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: PairedDeviceViewHolder, position: Int) {
        holder.bind(devices[position], devices[position].address == selectedAddress)
    }

    override fun getItemCount(): Int = devices.size

    class PairedDeviceViewHolder(
        private val binding: ItemPairedDeviceBinding,
        private val onSelected: (PairedDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: PairedDevice, isSelected: Boolean) {
            binding.pairedDeviceName.text = device.name
            binding.pairedDeviceMac.text = device.address
            val density = binding.root.resources.displayMetrics.density
            val selectedWidth = (2 * density).roundToInt()
            val normalWidth = (1 * density).roundToInt()
            val strokeColor = if (isSelected) {
                MaterialColors.getColor(binding.pairedDeviceCard, com.google.android.material.R.attr.colorPrimary)
            } else {
                MaterialColors.getColor(binding.pairedDeviceCard, com.google.android.material.R.attr.colorOutline)
            }
            binding.pairedDeviceCard.strokeWidth = if (isSelected) selectedWidth else normalWidth
            binding.pairedDeviceCard.strokeColor = strokeColor
            binding.pairedDeviceCard.setOnClickListener {
                onSelected(device)
            }
        }
    }
}

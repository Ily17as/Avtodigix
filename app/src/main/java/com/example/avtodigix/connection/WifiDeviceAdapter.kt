package com.example.avtodigix.connection

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.avtodigix.R
import com.example.avtodigix.databinding.ItemWifiDeviceBinding
import com.example.avtodigix.wifi.WifiAutoDetectResult
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

class WifiDeviceAdapter(
    private val onSelected: (WifiAutoDetectResult) -> Unit
) : RecyclerView.Adapter<WifiDeviceAdapter.WifiDeviceViewHolder>() {
    private val devices = mutableListOf<WifiAutoDetectResult>()
    private var selectedHost: String? = null
    private var selectedPort: Int? = null

    fun submitList(items: List<WifiAutoDetectResult>, host: String?, port: Int?) {
        devices.clear()
        devices.addAll(items)
        selectedHost = host
        selectedPort = port
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiDeviceViewHolder {
        val binding = ItemWifiDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WifiDeviceViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: WifiDeviceViewHolder, position: Int) {
        val device = devices[position]
        val isSelected = device.host == selectedHost && device.port == selectedPort
        holder.bind(device, isSelected)
    }

    override fun getItemCount(): Int = devices.size

    class WifiDeviceViewHolder(
        private val binding: ItemWifiDeviceBinding,
        private val onSelected: (WifiAutoDetectResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: WifiAutoDetectResult, isSelected: Boolean) {
            binding.wifiDeviceName.text = "${device.host}:${device.port}"
            binding.wifiDeviceAddress.text = binding.root.context.getString(
                R.string.wifi_detect_rtt,
                device.rttMs
            )
            val density = binding.root.resources.displayMetrics.density
            val selectedWidth = (2 * density).roundToInt()
            val normalWidth = (1 * density).roundToInt()

            val primaryColor = try {
                MaterialColors.getColor(
                    binding.wifiDeviceCard,
                    com.google.android.material.R.attr.colorPrimary
                )
            } catch (e: Exception) {
                Color.BLACK
            }

            val outlineColor = ContextCompat.getColor(
                binding.root.context,
                R.color.device_outline
            )

            val strokeColor = if (isSelected) primaryColor else outlineColor
            binding.wifiDeviceCard.strokeWidth = if (isSelected) selectedWidth else normalWidth
            binding.wifiDeviceCard.strokeColor = strokeColor
            binding.wifiDeviceCard.setOnClickListener {
                onSelected(device)
            }
        }
    }
}

package com.example.avtodigix.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.ObdState
import com.example.avtodigix.databinding.FragmentDataBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class DataFragment : Fragment(R.layout.fragment_data) {
    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var latestConnectionState = ConnectionState()
    private var latestObdState = ObdState()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDataBinding.bind(view)
        val viewModel = (requireActivity() as MainActivity).connectionViewModel

        binding.openConnectionButton.setOnClickListener {
            findNavController().navigate(R.id.connectionFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { state ->
                        latestConnectionState = state
                        renderState()
                    }
                }
                launch {
                    viewModel.obdState.collect { state ->
                        latestObdState = state
                        renderState()
                    }
                }
            }
        }
    }

    private fun renderState() {
        val isConnected = latestConnectionState.status == ConnectionState.Status.Connected
        binding.dataEmptyState.isVisible = !isConnected
        binding.dataConnectedState.isVisible = isConnected
        if (!isConnected) return

        val target = latestConnectionState.selectedDeviceName
            ?: latestConnectionState.wifiResolvedEndpoint
            ?: getString(R.string.connection_unknown_target)
        binding.connectionChip.text = getString(R.string.connection_status_connected_to, target)

        binding.statusMilValue.text = if (latestObdState.milOn == true) {
            getString(R.string.data_status_mil_on)
        } else {
            getString(R.string.data_status_mil_off)
        }
        val dtcTotal = latestObdState.storedDtcs.size + latestObdState.pendingDtcs.size
        binding.statusDtcValue.text = getString(R.string.data_status_dtc_format, dtcTotal)
        val readinessCount = latestObdState.readinessRaw?.size ?: 0
        binding.statusReadinessValue.text = getString(R.string.data_status_readiness_format, readinessCount)
        binding.statusPidSupportValue.text = getString(
            R.string.data_status_pid_format,
            latestObdState.supportedPids.size
        )

        binding.metricEngineRpmValue.text = getString(
            R.string.data_metric_rpm_format,
            latestObdState.metrics?.engineRpm?.toString() ?: "—"
        )
        binding.metricVehicleSpeedValue.text = getString(
            R.string.data_metric_speed_format,
            latestObdState.metrics?.vehicleSpeedKph?.toString() ?: "—"
        )
        binding.metricEngineTempValue.text = getString(
            R.string.data_metric_temp_format,
            latestObdState.metrics?.coolantTempCelsius?.toString() ?: "—"
        )
        binding.metricBatteryVoltageValue.text = getString(
            R.string.data_metric_battery_format,
            latestObdState.metrics?.batteryVoltageVolts?.toString() ?: "—"
        )

        val updatedAt = latestObdState.lastUpdatedMillis?.let { timeFormatter.format(Date(it)) } ?: "—"
        binding.updatedAtValue.text = getString(R.string.data_updated_at_format, updatedAt)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

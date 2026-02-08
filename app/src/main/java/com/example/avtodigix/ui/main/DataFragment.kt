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
import com.example.avtodigix.databinding.FragmentDataBinding
import kotlinx.coroutines.launch

class DataFragment : Fragment(R.layout.fragment_data) {
    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDataBinding.bind(view)
        val viewModel = (requireActivity() as MainActivity).connectionViewModel

        binding.openIssuesButton.setOnClickListener {
            findNavController().navigate(R.id.issuesHistoryFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.obdState.collect { state ->
                    binding.metricEngineRpmValue.text = state.metrics?.engineRpm?.toString() ?: "—"
                    binding.metricVehicleSpeedValue.text = state.metrics?.vehicleSpeedKph?.toString() ?: "—"
                    binding.metricEngineTempValue.text = state.metrics?.coolantTempCelsius?.toString() ?: "—"
                    binding.metricBatteryVoltageValue.text = state.metrics?.batteryVoltageVolts?.toString() ?: "—"
                    binding.connectionHint.isVisible = state.metrics == null
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

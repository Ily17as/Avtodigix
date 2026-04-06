package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.avtodigix.R
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.databinding.FragmentDailyCheckHistoryBinding
import com.example.avtodigix.domain.resolveVehicleId
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.DailyCheckSessionRepository
import kotlinx.coroutines.launch

class DailyCheckHistoryFragment : Fragment(R.layout.fragment_daily_check_history) {
    private var _binding: FragmentDailyCheckHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = DailyCheckHistoryAdapter()
    private lateinit var repository: DailyCheckSessionRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckHistoryBinding.bind(view)

        repository = DailyCheckSessionRepository(AppDatabase.create(requireContext()).dailyCheckSessionDao())
        binding.dailyCheckHistoryRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.dailyCheckHistoryRecycler.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stableVehicleId = SelectedDeviceStore(requireContext()).getSelectedDeviceAddress()
            val vehicleId = resolveVehicleId(vin = null, stableDeviceOrCarId = stableVehicleId)
            val history = repository.getRecentSessions(vehicleId = vehicleId, limit = HISTORY_LIMIT)

            adapter.submit(history)
            binding.dailyCheckHistoryEmpty.isVisible = history.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val HISTORY_LIMIT = 50
    }
}

package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.dailycheck.baseline.VehicleBaselineCalculator
import com.example.avtodigix.databinding.FragmentDailyCheckResultBinding
import com.example.avtodigix.domain.CheckSession
import com.example.avtodigix.domain.TrafficLightStatus
import com.example.avtodigix.domain.resolveVehicleId
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.DailyCheckSessionRepository
import kotlinx.coroutines.launch

class DailyCheckResultFragment : Fragment(R.layout.fragment_daily_check_result) {
    private var _binding: FragmentDailyCheckResultBinding? = null
    private val binding get() = _binding!!
    private var showBaselineComparison: Boolean = true
    private var hasTrackedFinished = false
    private var hasTrackedPartialResult = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckResultBinding.bind(view)

        renderBaselineStates()
        trackFinishedAnalytics()

        binding.dailyCheckEngineCard.setOnClickListener {
            openCardDetails(
                systemCard = SystemCardDetails(
                    systemType = getString(R.string.daily_check_system_engine_title),
                    status = getString(R.string.daily_check_details_engine_status),
                    metrics = getString(R.string.daily_check_details_engine_metrics),
                    baselineComparison = getString(R.string.daily_check_details_engine_baseline),
                    recommendation = getString(R.string.daily_check_details_engine_recommendation)
                )
            )
        }

        binding.dailyCheckFuelCard.setOnClickListener {
            openCardDetails(
                systemCard = SystemCardDetails(
                    systemType = getString(R.string.daily_check_system_fuel_title),
                    status = getString(R.string.daily_check_details_fuel_status),
                    metrics = getString(R.string.daily_check_details_fuel_metrics),
                    baselineComparison = getString(R.string.daily_check_details_fuel_baseline),
                    recommendation = getString(R.string.daily_check_details_fuel_recommendation)
                )
            )
        }

        binding.dailyCheckBatteryCard.setOnClickListener {
            openCardDetails(
                systemCard = SystemCardDetails(
                    systemType = getString(R.string.daily_check_system_battery_title),
                    status = getString(R.string.daily_check_details_battery_status),
                    metrics = getString(R.string.daily_check_details_battery_metrics),
                    baselineComparison = getString(R.string.daily_check_details_battery_baseline),
                    recommendation = getString(R.string.daily_check_details_battery_recommendation)
                )
            )
        }

        binding.dailyCheckCoolingCard.setOnClickListener {
            openCardDetails(
                systemCard = SystemCardDetails(
                    systemType = getString(R.string.daily_check_system_cooling_title),
                    status = getString(R.string.daily_check_details_cooling_status),
                    metrics = getString(R.string.daily_check_details_cooling_metrics),
                    baselineComparison = getString(R.string.daily_check_details_cooling_baseline),
                    recommendation = getString(R.string.daily_check_details_cooling_recommendation)
                )
            )
        }

        binding.dailyCheckErrorsCard.setOnClickListener {
            openCardDetails(
                systemCard = SystemCardDetails(
                    systemType = getString(R.string.daily_check_system_errors_title),
                    status = getString(R.string.daily_check_details_errors_status),
                    metrics = getString(R.string.daily_check_details_errors_metrics),
                    baselineComparison = getString(R.string.daily_check_details_errors_baseline),
                    recommendation = getString(R.string.daily_check_details_errors_recommendation)
                )
            )
        }

        binding.dailyCheckCtaButton.setOnClickListener {
            (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_open_history")
            findNavController().navigate(R.id.action_dailyCheckResultFragment_to_dailyCheckHistoryFragment)
        }
        binding.dailyCheckProModeButton.setOnClickListener {
            (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_open_pro_mode")
            findNavController().navigate(R.id.dataFragment)
        }
    }

    private fun renderBaselineStates() {
        val repository = DailyCheckSessionRepository(AppDatabase.create(requireContext()).dailyCheckSessionDao())
        val baselineCalculator = VehicleBaselineCalculator(repository)
        viewLifecycleOwner.lifecycleScope.launch {
            val stableVehicleId = SelectedDeviceStore(requireContext()).getSelectedDeviceAddress()
            val vehicleId = resolveVehicleId(vin = null, stableDeviceOrCarId = stableVehicleId)
            val baseline = baselineCalculator.getBaseline(vehicleId)

            val noHistory = !baseline.hasHistory
            val partialResult = baseline.isPartial
            val firstChecks = baseline.isEarlyBaseline

            showBaselineComparison = !noHistory
            binding.dailyCheckChangesCard.isVisible = !noHistory
            binding.dailyCheckPartialBadge.isVisible = partialResult
            binding.dailyCheckBaselineInfo.isVisible = noHistory || firstChecks
            binding.dailyCheckBaselineInfo.text = when {
                noHistory -> getString(R.string.daily_check_no_history_message)
                firstChecks -> getString(R.string.daily_check_first_checks_message)
                else -> ""
            }
            if (partialResult) {
                binding.dailyCheckSummary.text = getString(R.string.daily_check_partial_summary)
                if (!hasTrackedPartialResult) {
                    hasTrackedPartialResult = true
                    (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_partial_result")
                }
            }
        }
    }

    private fun openCardDetails(systemCard: SystemCardDetails) {
        (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_open_details")
        findNavController().navigate(
            R.id.action_dailyCheckResultFragment_to_dailyCheckCardDetailsFragment,
            DailyCheckCardDetailsFragment.buildArgs(
                systemType = systemCard.systemType,
                status = systemCard.status,
                metrics = systemCard.metrics,
                baselineComparison = systemCard.baselineComparison,
                showBaselineComparison = showBaselineComparison,
                recommendation = systemCard.recommendation
            )
        )
    }

    private fun trackFinishedAnalytics() {
        if (hasTrackedFinished) {
            return
        }
        hasTrackedFinished = true
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = DailyCheckSessionRepository(AppDatabase.create(requireContext()).dailyCheckSessionDao())
            val stableVehicleId = SelectedDeviceStore(requireContext()).getSelectedDeviceAddress()
            val vehicleId = resolveVehicleId(vin = null, stableDeviceOrCarId = stableVehicleId)
            val recentSessions = repository.getRecentSessions(vehicleId = vehicleId, limit = 2)
            val latestSession = recentSessions.firstOrNull()
            val previousSession = recentSessions.getOrNull(1)
            val params = buildFinishedParams(latestSession, previousSession)
            (requireActivity() as MainActivity).analyticsTracker.trackEvent(
                name = "daily_check_finished",
                params = params
            )
        }
    }

    private fun buildFinishedParams(
        latestSession: CheckSession?,
        previousSession: CheckSession?
    ): Map<String, Any> {
        val fallbackDtcCount = (requireActivity() as MainActivity)
            .connectionViewModel
            .obdState
            .value
            .let { it.dtcCountReported ?: it.storedDtcs.size }
        val score = latestSession?.let(::scoreByTrafficLightStatus) ?: 0
        val overallStatus = latestSession?.let(::overallStatusByTrafficLightStatus) ?: "unknown"
        val driveStatus = latestSession?.let(::driveStatusBySession) ?: "unknown"
        val dtcCount = latestSession?.rawDtcList?.size ?: fallbackDtcCount
        val newDtcCount = latestSession?.let {
            calculateNewDtcCount(current = it, previous = previousSession)
        } ?: 0
        val observeCount = if (latestSession?.trafficLightStatus == TrafficLightStatus.YELLOW) 1 else 0
        val attentionCount = if (latestSession?.trafficLightStatus == TrafficLightStatus.RED) 1 else 0
        return mapOf(
            "score" to score,
            "overall_status" to overallStatus,
            "drive_status" to driveStatus,
            "dtc_count" to dtcCount,
            "new_dtc_count" to newDtcCount,
            "observe_count" to observeCount,
            "attention_count" to attentionCount
        )
    }

    private fun scoreByTrafficLightStatus(session: CheckSession): Int = when (session.trafficLightStatus) {
        TrafficLightStatus.GREEN -> 90
        TrafficLightStatus.YELLOW -> 70
        TrafficLightStatus.RED -> 40
    }

    private fun overallStatusByTrafficLightStatus(session: CheckSession): String = when (session.trafficLightStatus) {
        TrafficLightStatus.GREEN -> "good"
        TrafficLightStatus.YELLOW -> "attention"
        TrafficLightStatus.RED -> "critical"
    }

    private fun driveStatusBySession(session: CheckSession): String {
        return if (session.trafficLightStatus == TrafficLightStatus.RED || !session.success) {
            "not_recommended"
        } else {
            "allowed"
        }
    }

    private fun calculateNewDtcCount(current: CheckSession, previous: CheckSession?): Int {
        if (previous == null) {
            return if (current.hasNewDtc) current.rawDtcList.size else 0
        }
        val previousDtcs = previous.rawDtcList.toSet()
        return current.rawDtcList.count { it !in previousDtcs }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class SystemCardDetails(
        val systemType: String,
        val status: String,
        val metrics: String,
        val baselineComparison: String,
        val recommendation: String
    )
}

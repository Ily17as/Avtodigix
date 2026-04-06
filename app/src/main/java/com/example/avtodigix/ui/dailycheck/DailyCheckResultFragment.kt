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
    private var latestSession: CheckSession? = null
    private val systemCardsById = mutableMapOf<Int, SystemCardDetails>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckResultBinding.bind(view)

        setupResultInteractions()

        binding.dailyCheckCtaButton.setOnClickListener {
            (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_open_history")
            findNavController().navigate(R.id.action_dailyCheckResultFragment_to_dailyCheckHistoryFragment)
        }
        binding.dailyCheckProModeButton.setOnClickListener {
            (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_open_pro_mode")
            findNavController().navigate(R.id.dataFragment)
        }

        binding.dailyCheckNoDataCtaButton.setOnClickListener {
            (requireActivity() as MainActivity).analyticsTracker.trackEvent("daily_check_empty_start")
            findNavController().navigate(R.id.dailyCheckProgressFragment)
        }

        val repository = DailyCheckSessionRepository(AppDatabase.create(requireContext()).dailyCheckSessionDao())
        viewLifecycleOwner.lifecycleScope.launch {
            val stableVehicleId = SelectedDeviceStore(requireContext()).getSelectedDeviceAddress()
            val vehicleId = resolveVehicleId(vin = null, stableDeviceOrCarId = stableVehicleId)
            val recentSessions = repository.getRecentSessions(vehicleId = vehicleId, limit = 2)
            val latestSession = recentSessions.firstOrNull()
            val previousSession = recentSessions.getOrNull(1)
            if (latestSession == null) {
                renderNoDataState()
                return@launch
            }
            renderDataState()
            bindSessionData(session = latestSession, previousSession = previousSession)
            renderBaselineStates(repository = repository, vehicleId = vehicleId)
            trackFinishedAnalytics(repository = repository, vehicleId = vehicleId)
        }
    }

    private fun renderNoDataState() {
        binding.dailyCheckNoDataCard.isVisible = true
        binding.dailyCheckContentContainer.isVisible = false
    }

    private fun renderDataState() {
        binding.dailyCheckNoDataCard.isVisible = false
        binding.dailyCheckContentContainer.isVisible = true
    }

    private fun setupResultInteractions() {
        binding.dailyCheckEngineCard.setOnClickListener {
            openCardDetails(systemCard = systemCardsById[R.id.dailyCheckEngineCard] ?: buildEmptySystemCard(R.string.daily_check_system_engine_title))
        }

        binding.dailyCheckFuelCard.setOnClickListener {
            openCardDetails(systemCard = systemCardsById[R.id.dailyCheckFuelCard] ?: buildEmptySystemCard(R.string.daily_check_system_fuel_title))
        }

        binding.dailyCheckBatteryCard.setOnClickListener {
            openCardDetails(systemCard = systemCardsById[R.id.dailyCheckBatteryCard] ?: buildEmptySystemCard(R.string.daily_check_system_battery_title))
        }

        binding.dailyCheckCoolingCard.setOnClickListener {
            openCardDetails(systemCard = systemCardsById[R.id.dailyCheckCoolingCard] ?: buildEmptySystemCard(R.string.daily_check_system_cooling_title))
        }

        binding.dailyCheckErrorsCard.setOnClickListener {
            openCardDetails(systemCard = systemCardsById[R.id.dailyCheckErrorsCard] ?: buildEmptySystemCard(R.string.daily_check_system_errors_title))
        }
    }

    private fun bindSessionData(session: CheckSession, previousSession: CheckSession?) {
        latestSession = session
        binding.dailyCheckScore.text = "${scoreByTrafficLightStatus(session)} / 100".ifBlank { EMPTY_VALUE }
        binding.dailyCheckOverallStatus.text = when (session.trafficLightStatus) {
            TrafficLightStatus.GREEN -> "Состояние: можно ехать"
            TrafficLightStatus.YELLOW -> "Состояние: требуется внимание"
            TrafficLightStatus.RED -> "Состояние: ехать не рекомендуется"
        }.ifBlank { EMPTY_VALUE }

        val dtcCount = session.rawDtcList.size
        binding.dailyCheckSummary.text = when {
            !session.success -> "Проверка завершилась с ошибкой."
            dtcCount > 0 -> "Обнаружены коды DTC: $dtcCount."
            else -> "Критичных ошибок не обнаружено."
        }.ifBlank { EMPTY_VALUE }

        binding.dailyCheckDriveAnswer.text = if (session.success && session.trafficLightStatus != TrafficLightStatus.RED) {
            "Да"
        } else {
            "Нет"
        }
        binding.dailyCheckDriveReason.text = buildList {
            add("Статус: ${overallStatusByTrafficLightStatus(session)}")
            add("DTC: $dtcCount")
            add("Успешность: ${if (session.success) "успешно" else "ошибка"}")
        }.joinToString(" • ").ifBlank { EMPTY_VALUE }

        val changes = buildChanges(session, previousSession)
        binding.dailyCheckChangeItem1.text = changes.getOrNull(0) ?: EMPTY_VALUE
        binding.dailyCheckChangeItem2.text = changes.getOrNull(1) ?: EMPTY_VALUE
        binding.dailyCheckChangeItem3.text = changes.getOrNull(2) ?: EMPTY_VALUE

        val engineCard = buildSystemCard(
            title = getString(R.string.daily_check_system_engine_title),
            status = statusLabelByTrafficLightStatus(session.trafficLightStatus),
            metricKey = "Engine RPM",
            metricSuffix = "об/мин",
            previousSession = previousSession,
            recommendation = if (session.success) "Наблюдайте за стабильностью оборотов двигателя." else null
        )
        val fuelCard = buildSystemCard(
            title = getString(R.string.daily_check_system_fuel_title),
            status = statusLabelByTrafficLightStatus(session.trafficLightStatus),
            metricKey = null,
            metricSuffix = null,
            previousSession = previousSession,
            recommendation = if (dtcCount > 0) "Проверьте топливную систему по кодам DTC." else null
        )
        val batteryCard = buildSystemCard(
            title = getString(R.string.daily_check_system_battery_title),
            status = statusLabelByTrafficLightStatus(session.trafficLightStatus),
            metricKey = "Battery voltage (V)",
            metricSuffix = "В",
            previousSession = previousSession,
            recommendation = "Проверяйте уровень заряда аккумулятора."
        )
        val coolingCard = buildSystemCard(
            title = getString(R.string.daily_check_system_cooling_title),
            status = statusLabelByTrafficLightStatus(session.trafficLightStatus),
            metricKey = "Coolant temp (C)",
            metricSuffix = "°C",
            previousSession = previousSession,
            recommendation = "Следите за температурой охлаждающей жидкости."
        )
        val errorsCard = buildSystemCard(
            title = getString(R.string.daily_check_system_errors_title),
            status = if (dtcCount == 0) "Норма" else "Есть ошибки",
            metricText = "DTC: $dtcCount",
            previousSession = previousSession,
            recommendation = if (dtcCount == 0) "Наблюдение без действий." else "Рекомендуется дополнительная диагностика."
        )

        systemCardsById[R.id.dailyCheckEngineCard] = engineCard
        systemCardsById[R.id.dailyCheckFuelCard] = fuelCard
        systemCardsById[R.id.dailyCheckBatteryCard] = batteryCard
        systemCardsById[R.id.dailyCheckCoolingCard] = coolingCard
        systemCardsById[R.id.dailyCheckErrorsCard] = errorsCard

        binding.dailyCheckEngineStatus.text = engineCard.status.ifBlank { EMPTY_VALUE }
        binding.dailyCheckFuelStatus.text = fuelCard.status.ifBlank { EMPTY_VALUE }
        binding.dailyCheckBatteryStatus.text = batteryCard.status.ifBlank { EMPTY_VALUE }
        binding.dailyCheckCoolingStatus.text = coolingCard.status.ifBlank { EMPTY_VALUE }
        binding.dailyCheckErrorsStatus.text = errorsCard.status.ifBlank { EMPTY_VALUE }

        binding.dailyCheckRecommendationText.text = (
            errorsCard.recommendation.takeUnless { it == EMPTY_VALUE }
                ?: batteryCard.recommendation.takeUnless { it == EMPTY_VALUE }
                ?: EMPTY_VALUE
            )
    }

    private fun buildChanges(session: CheckSession, previousSession: CheckSession?): List<String> {
        val currentMetrics = session.keyMetrics
        val previousMetrics = previousSession?.keyMetrics.orEmpty()
        val metricChanges = currentMetrics.entries
            .map { (metric, value) ->
                val oldValue = previousMetrics[metric]
                if (oldValue == null) {
                    "$metric: ${formatMetricValue(value)}"
                } else {
                    val delta = value - oldValue
                    "$metric: ${formatMetricValue(oldValue)} → ${formatMetricValue(value)} (${formatMetricValue(delta)})"
                }
            }
        return if (metricChanges.isNotEmpty()) metricChanges else listOf(EMPTY_VALUE)
    }

    private fun buildSystemCard(
        title: String,
        status: String,
        metricKey: String? = null,
        metricSuffix: String? = null,
        metricText: String? = null,
        previousSession: CheckSession?,
        recommendation: String?
    ): SystemCardDetails {
        val metricsValue = metricText ?: metricKey?.let { key ->
            val value = systemCardsMetricValue(key = key)
            if (value != null) {
                listOfNotNull(formatMetricValue(value), metricSuffix).joinToString(" ")
            } else {
                EMPTY_VALUE
            }
        }
        return SystemCardDetails(
            systemType = title,
            status = status.ifBlank { EMPTY_VALUE },
            metrics = metricsValue.orEmpty().ifBlank { EMPTY_VALUE },
            baselineComparison = buildBaselineComparison(metricKey = metricKey, previousSession = previousSession),
            recommendation = recommendation.orEmpty().ifBlank { EMPTY_VALUE }
        )
    }

    private fun buildBaselineComparison(metricKey: String?, previousSession: CheckSession?): String {
        if (metricKey == null) return EMPTY_VALUE
        val currentValue = systemCardsMetricValue(metricKey) ?: return EMPTY_VALUE
        val previousValue = previousSession?.keyMetrics?.get(metricKey) ?: return EMPTY_VALUE
        val delta = currentValue - previousValue
        return "${formatMetricValue(previousValue)} → ${formatMetricValue(currentValue)} (${formatMetricValue(delta)})"
    }

    private fun systemCardsMetricValue(key: String): Double? {
        return latestSession?.keyMetrics?.get(key)
    }

    private fun buildEmptySystemCard(titleRes: Int): SystemCardDetails {
        return SystemCardDetails(
            systemType = getString(titleRes),
            status = EMPTY_VALUE,
            metrics = EMPTY_VALUE,
            baselineComparison = EMPTY_VALUE,
            recommendation = EMPTY_VALUE
        )
    }

    private fun statusLabelByTrafficLightStatus(status: TrafficLightStatus): String = when (status) {
        TrafficLightStatus.GREEN -> "Норма"
        TrafficLightStatus.YELLOW -> "Внимание"
        TrafficLightStatus.RED -> "Критично"
    }

    private fun formatMetricValue(value: Double): String = String.format("%.2f", value)

    private suspend fun renderBaselineStates(repository: DailyCheckSessionRepository, vehicleId: String) {
        val baselineCalculator = VehicleBaselineCalculator(repository)
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

    private suspend fun trackFinishedAnalytics(repository: DailyCheckSessionRepository, vehicleId: String) {
        if (hasTrackedFinished) {
            return
        }
        hasTrackedFinished = true
        val recentSessions = repository.getRecentSessions(vehicleId = vehicleId, limit = 2)
        val latestSession = recentSessions.firstOrNull()
        val previousSession = recentSessions.getOrNull(1)
        val params = buildFinishedParams(latestSession, previousSession)
        (requireActivity() as MainActivity).analyticsTracker.trackEvent(
            name = "daily_check_finished",
            params = params
        )
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

    companion object {
        private const val EMPTY_VALUE = "—"
    }
}

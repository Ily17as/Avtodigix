package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.dailycheck.baseline.VehicleBaselineCalculator
import com.example.avtodigix.databinding.FragmentDailyCheckResultBinding
import com.example.avtodigix.domain.CheckSession
import com.example.avtodigix.domain.TrafficLightStatus
import com.example.avtodigix.domain.resolveVehicleId
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.DailyCheckSessionRepository
import com.google.android.material.card.MaterialCardView
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
            val connectionState = (requireActivity() as MainActivity).connectionViewModel.connectionState.value
            if (connectionState.status == ConnectionState.Status.Connected) {
                findNavController().navigate(R.id.dailyCheckProgressFragment)
            } else {
                findNavController().navigate(R.id.connectionFragment)
            }
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
        val overallUiStatus = overallUiStatus(session = session)
        binding.dailyCheckScore.text = "${scoreByTrafficLightStatus(session)} / 100".ifBlank { EMPTY_VALUE }
        binding.dailyCheckOverallStatus.text = overallStatusTitle(overallUiStatus = overallUiStatus).ifBlank { EMPTY_VALUE }
        binding.dailyCheckOverallStatus.setTextColor(ContextCompat.getColor(requireContext(), overallUiStatus.textColorRes))

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
            uiStatus = if (session.success) uiStatusByTrafficLightStatus(session.trafficLightStatus) else UiStatus.ERROR,
            metricKey = "Engine RPM",
            metricSuffix = "об/мин",
            previousSession = previousSession,
            recommendation = if (session.success) "Наблюдайте за стабильностью оборотов двигателя." else null
        )
        val fuelCard = buildSystemCard(
            title = getString(R.string.daily_check_system_fuel_title),
            uiStatus = when {
                !session.success -> UiStatus.ERROR
                dtcCount > 0 -> UiStatus.ATTENTION
                else -> UiStatus.OK
            },
            metricKey = null,
            metricSuffix = null,
            previousSession = previousSession,
            recommendation = if (dtcCount > 0) "Проверьте топливную систему по кодам DTC." else null
        )
        val batteryCard = buildSystemCard(
            title = getString(R.string.daily_check_system_battery_title),
            uiStatus = statusByMetric(
                metricKey = "Battery voltage (V)",
                normalRange = 12.2..14.8,
                fallback = uiStatusByTrafficLightStatus(session.trafficLightStatus)
            ),
            metricKey = "Battery voltage (V)",
            metricSuffix = "В",
            previousSession = previousSession,
            recommendation = "Проверяйте уровень заряда аккумулятора."
        )
        val coolingCard = buildSystemCard(
            title = getString(R.string.daily_check_system_cooling_title),
            uiStatus = statusByMetric(
                metricKey = "Coolant temp (C)",
                normalRange = 70.0..105.0,
                fallback = uiStatusByTrafficLightStatus(session.trafficLightStatus)
            ),
            metricKey = "Coolant temp (C)",
            metricSuffix = "°C",
            previousSession = previousSession,
            recommendation = "Следите за температурой охлаждающей жидкости."
        )
        val errorsCard = buildSystemCard(
            title = getString(R.string.daily_check_system_errors_title),
            uiStatus = when {
                !session.success -> UiStatus.ERROR
                dtcCount > 0 -> UiStatus.ERROR
                else -> UiStatus.OK
            },
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
        renderSystemRow(binding.dailyCheckEngineCard, binding.dailyCheckEngineIcon, binding.dailyCheckEngineStatus, engineCard.uiStatus)
        renderSystemRow(binding.dailyCheckFuelCard, binding.dailyCheckFuelIcon, binding.dailyCheckFuelStatus, fuelCard.uiStatus)
        renderSystemRow(binding.dailyCheckBatteryCard, binding.dailyCheckBatteryIcon, binding.dailyCheckBatteryStatus, batteryCard.uiStatus)
        renderSystemRow(binding.dailyCheckCoolingCard, binding.dailyCheckCoolingIcon, binding.dailyCheckCoolingStatus, coolingCard.uiStatus)
        renderSystemRow(binding.dailyCheckErrorsCard, binding.dailyCheckErrorsIcon, binding.dailyCheckErrorsStatus, errorsCard.uiStatus)

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
        uiStatus: UiStatus,
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
            status = uiStatus.label,
            metrics = metricsValue.orEmpty().ifBlank { EMPTY_VALUE },
            baselineComparison = buildBaselineComparison(metricKey = metricKey, previousSession = previousSession),
            recommendation = recommendation.orEmpty().ifBlank { EMPTY_VALUE },
            uiStatus = uiStatus
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
            recommendation = EMPTY_VALUE,
            uiStatus = UiStatus.INSUFFICIENT_DATA
        )
    }

    private fun overallUiStatus(session: CheckSession): UiStatus = when {
        !session.success -> UiStatus.ERROR
        session.trafficLightStatus == TrafficLightStatus.RED -> UiStatus.ERROR
        session.trafficLightStatus == TrafficLightStatus.YELLOW -> UiStatus.ATTENTION
        session.keyMetrics.isEmpty() -> UiStatus.INSUFFICIENT_DATA
        else -> UiStatus.OK
    }

    private fun uiStatusByTrafficLightStatus(status: TrafficLightStatus): UiStatus = when (status) {
        TrafficLightStatus.GREEN -> UiStatus.OK
        TrafficLightStatus.YELLOW -> UiStatus.ATTENTION
        TrafficLightStatus.RED -> UiStatus.ERROR
    }

    private fun statusByMetric(metricKey: String, normalRange: ClosedFloatingPointRange<Double>, fallback: UiStatus): UiStatus {
        val metric = systemCardsMetricValue(metricKey) ?: return UiStatus.INSUFFICIENT_DATA
        return when {
            metric in normalRange -> UiStatus.OK
            fallback == UiStatus.ERROR -> UiStatus.ERROR
            else -> UiStatus.ATTENTION
        }
    }

    private fun overallStatusTitle(overallUiStatus: UiStatus): String = when (overallUiStatus) {
        UiStatus.OK -> "Состояние: всё в норме"
        UiStatus.ATTENTION -> "Состояние: требуется внимание"
        UiStatus.ERROR -> "Состояние: ехать не рекомендуется"
        UiStatus.INSUFFICIENT_DATA -> "Состояние: недостаточно данных"
    }

    private fun renderSystemRow(card: MaterialCardView, icon: ImageView, statusView: TextView, uiStatus: UiStatus) {
        icon.setImageResource(uiStatus.iconRes)
        icon.setColorFilter(ContextCompat.getColor(requireContext(), uiStatus.textColorRes))
        statusView.setBackgroundResource(uiStatus.chipBackgroundRes)
        statusView.setTextColor(ContextCompat.getColor(requireContext(), uiStatus.textColorRes))
        card.strokeColor = ContextCompat.getColor(requireContext(), uiStatus.strokeColorRes)
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
        val recommendation: String,
        val uiStatus: UiStatus
    )

    private enum class UiStatus(
        val label: String,
        val chipBackgroundRes: Int,
        val textColorRes: Int,
        val strokeColorRes: Int,
        val iconRes: Int
    ) {
        OK(
            label = "OK",
            chipBackgroundRes = R.drawable.bg_status_chip_ok,
            textColorRes = R.color.dc_status_ok,
            strokeColorRes = R.color.dc_status_ok,
            iconRes = android.R.drawable.checkbox_on_background
        ),
        ATTENTION(
            label = "ATTENTION",
            chipBackgroundRes = R.drawable.bg_status_chip_warning,
            textColorRes = R.color.dc_status_warn,
            strokeColorRes = R.color.dc_status_warn,
            iconRes = android.R.drawable.ic_dialog_alert
        ),
        ERROR(
            label = "ERROR",
            chipBackgroundRes = R.drawable.bg_status_chip_error,
            textColorRes = R.color.dc_status_error,
            strokeColorRes = R.color.dc_status_error,
            iconRes = android.R.drawable.stat_notify_error
        ),
        INSUFFICIENT_DATA(
            label = "INSUFFICIENT_DATA",
            chipBackgroundRes = R.drawable.bg_status_chip_unknown,
            textColorRes = R.color.dc_status_unknown,
            strokeColorRes = R.color.dc_status_unknown,
            iconRes = android.R.drawable.ic_menu_help
        )
    }

    companion object {
        private const val EMPTY_VALUE = "—"
    }
}

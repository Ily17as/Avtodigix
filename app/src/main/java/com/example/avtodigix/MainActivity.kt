package com.example.avtodigix

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import com.example.avtodigix.databinding.ActivityMainBinding
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.ScanSnapshot
import com.example.avtodigix.storage.ScanSnapshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScanSnapshotRepository
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.create(applicationContext)
        repository = ScanSnapshotRepository(database.scanSnapshotDao())

        val flipper = binding.screenFlipper

        bindNavigation(binding.welcomeNext, flipper, 1)
        bindNavigation(binding.connectionBack, flipper, 0)
        bindNavigation(binding.connectionNext, flipper, 2)
        bindNavigation(binding.summaryBack, flipper, 1)
        bindNavigation(binding.summaryNext, flipper, 3)
        bindNavigation(binding.metricsBack, flipper, 2)
        bindNavigation(binding.metricsNext, flipper, 4)
        bindNavigation(binding.dtcBack, flipper, 3)
        bindNavigation(binding.dtcFinish, flipper, 0) {
            saveCurrentSnapshot()
        }

        binding.summaryClearHistory.setOnClickListener {
            ioScope.launch {
                repository.clearHistory()
            }
        }

        ioScope.launch {
            val latestSnapshot = repository.getLatestSnapshot()
            if (latestSnapshot != null) {
                withContext(Dispatchers.Main) {
                    applySnapshotToUi(latestSnapshot)
                }
            }
        }
    }

    private fun bindNavigation(
        button: Button,
        flipper: ViewFlipper,
        targetIndex: Int,
        beforeNavigate: (() -> Unit)? = null
    ) {
        button.setOnClickListener {
            beforeNavigate?.invoke()
            flipper.displayedChild = targetIndex
        }
    }

    private fun saveCurrentSnapshot() {
        val snapshot = ScanSnapshot(
            timestampMillis = System.currentTimeMillis(),
            keyMetrics = buildKeyMetrics(),
            dtcList = buildDtcList()
        )
        ioScope.launch {
            repository.saveSnapshot(snapshot)
        }
    }

    private fun buildKeyMetrics(): Map<String, Double> {
        return buildMap {
            addMetric(binding.metricEngineTemp, binding.metricEngineTempValue)
            addMetric(binding.metricBatteryVoltage, binding.metricBatteryVoltageValue)
            addMetric(binding.metricFuelTrim, binding.metricFuelTrimValue)
            addMetric(binding.metricOilPressure, binding.metricOilPressureValue)
        }
    }

    private fun MutableMap<String, Double>.addMetric(
        labelView: TextView,
        valueView: TextView
    ) {
        val value = parseFirstDouble(valueView.text) ?: return
        put(labelView.text.toString(), value)
    }

    private fun buildDtcList(): List<String> {
        val dtcRegex = DTC_REGEX
        val storedMatches = dtcRegex.findAll(binding.dtcStoredDetail.text).map { it.value }
        val pendingMatches = dtcRegex.findAll(binding.dtcPendingDetail.text).map { it.value }
        return (storedMatches + pendingMatches).distinct().toList()
    }

    private fun applySnapshotToUi(snapshot: ScanSnapshot) {
        updateMetricValue(binding.metricEngineTemp, binding.metricEngineTempValue, snapshot)
        updateMetricValue(binding.metricBatteryVoltage, binding.metricBatteryVoltageValue, snapshot)
        updateMetricValue(binding.metricFuelTrim, binding.metricFuelTrimValue, snapshot)
        updateMetricValue(binding.metricOilPressure, binding.metricOilPressureValue, snapshot)

        if (snapshot.dtcList.isNotEmpty()) {
            binding.dtcStoredDetail.text = snapshot.dtcList.joinToString(separator = "\n")
        }
    }

    private fun updateMetricValue(
        labelView: TextView,
        valueView: TextView,
        snapshot: ScanSnapshot
    ) {
        val key = labelView.text.toString()
        val value = snapshot.keyMetrics[key] ?: return
        val current = valueView.text.toString()
        val updated = NUMBER_REGEX.replaceFirst(current, value.toString())
        valueView.text = if (updated == current) value.toString() else updated
    }

    private fun parseFirstDouble(text: CharSequence): Double? {
        return NUMBER_REGEX.find(text)?.value?.toDoubleOrNull()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }

    private companion object {
        val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
        val DTC_REGEX = Regex("[PCBU][0-9A-F]{4}")
    }
}

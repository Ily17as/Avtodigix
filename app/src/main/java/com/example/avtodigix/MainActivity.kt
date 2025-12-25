package com.example.avtodigix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.avtodigix.connection.ConnectionPhase
import com.example.avtodigix.connection.ConnectionUiState
import com.example.avtodigix.connection.ConnectionViewModel
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
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScanSnapshotRepository
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectionViewModel: ConnectionViewModel
    private var permissionsRequestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.create(applicationContext)
        repository = ScanSnapshotRepository(database.scanSnapshotDao())

        val flipper = binding.screenFlipper
        connectionViewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            permissionsRequestInFlight = false
            val granted = results.values.all { it }
            connectionViewModel.onPermissionsResult(granted)
        }

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

        binding.connectionConnect.setOnClickListener {
            connectionViewModel.onConnectRequested()
        }

        binding.connectionDisconnect.setOnClickListener {
            connectionViewModel.onDisconnectRequested()
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionViewModel.state.collect { state ->
                    renderConnectionState(state, permissionsLauncher)
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

    private fun renderConnectionState(
        state: ConnectionUiState,
        permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        when (state.phase) {
            ConnectionPhase.Idle -> {
                binding.connectionStatus.text = "Статус: не подключено"
                binding.connectionStatusDetail.text = "Нажмите Connect для начала диагностики."
            }
            ConnectionPhase.PermissionsRequired -> {
                binding.connectionStatus.text = "Статус: требуется доступ к Bluetooth"
                binding.connectionStatusDetail.text = "Подтвердите разрешения для подключения."
                requestBluetoothPermissions(permissionsLauncher)
            }
            ConnectionPhase.Connecting -> {
                val deviceName = state.selectedDeviceName ?: "OBD адаптеру"
                binding.connectionStatus.text = "Статус: подключение"
                binding.connectionStatusDetail.text = "Подключение к $deviceName."
            }
            ConnectionPhase.Connected -> {
                val deviceName = state.selectedDeviceName ?: "OBD адаптеру"
                binding.connectionStatus.text = "Статус: подключено"
                binding.connectionStatusDetail.text = "Соединение с $deviceName установлено."
            }
            ConnectionPhase.AdapterUnavailable -> {
                binding.connectionStatus.text = "Статус: Bluetooth недоступен"
                binding.connectionStatusDetail.text = "Проверьте, включен ли Bluetooth на устройстве."
            }
            ConnectionPhase.Error -> {
                binding.connectionStatus.text = "Статус: ошибка подключения"
                binding.connectionStatusDetail.text = state.errorMessage ?: "Не удалось подключиться."
            }
        }

        binding.connectionStatusLog.text = state.log.ifBlank {
            getString(R.string.connection_status_log)
        }
        binding.connectionAdapterDetail.text = state.selectedDeviceName?.let { name ->
            "Выбран адаптер: $name"
        } ?: getString(R.string.connection_adapter_detail)

        binding.connectionConnect.isEnabled = state.phase == ConnectionPhase.Idle ||
            state.phase == ConnectionPhase.Error ||
            state.phase == ConnectionPhase.AdapterUnavailable
        binding.connectionDisconnect.isEnabled = state.phase == ConnectionPhase.Connecting ||
            state.phase == ConnectionPhase.Connected

        state.liveSnapshot?.let { snapshot ->
            updateLiveMetrics(snapshot)
        }
        if (state.storedDtcs.isNotEmpty()) {
            binding.dtcStoredDetail.text = state.storedDtcs.joinToString(separator = "\n")
        }
        if (state.pendingDtcs.isNotEmpty()) {
            binding.dtcPendingDetail.text = state.pendingDtcs.joinToString(separator = "\n")
        }
    }

    private fun requestBluetoothPermissions(
        permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        val permissions = requiredBluetoothPermissions()
        if (permissions.isEmpty()) {
            if (!permissionsRequestInFlight) {
                connectionViewModel.onPermissionsResult(true)
            }
            return
        }
        val hasPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermissions) {
            if (!permissionsRequestInFlight) {
                connectionViewModel.onPermissionsResult(true)
            }
            return
        }
        if (!permissionsRequestInFlight) {
            permissionsRequestInFlight = true
            permissionsLauncher.launch(permissions)
        }
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    private fun updateLiveMetrics(snapshot: com.example.avtodigix.obd.LivePidSnapshot) {
        snapshot.coolantTempCelsius?.let { value ->
            updateMetricValue(binding.metricEngineTempValue, value.toDouble())
        }
        snapshot.shortTermFuelTrimPercent?.let { value ->
            updateMetricValue(binding.metricFuelTrimValue, value)
        }
    }

    private fun updateMetricValue(valueView: TextView, value: Double) {
        val formatted = if (value % 1 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
        val current = valueView.text.toString()
        val updated = NUMBER_REGEX.replaceFirst(current, formatted)
        valueView.text = if (updated == current) formatted else updated
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

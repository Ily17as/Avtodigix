package com.example.avtodigix

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.ConnectionViewModelFactory
import com.example.avtodigix.connection.ConnectionViewModel
import com.example.avtodigix.connection.ObdState
import com.example.avtodigix.connection.PairedDeviceAdapter
import com.example.avtodigix.connection.PermissionStatus
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.databinding.ActivityMainBinding
import com.example.avtodigix.domain.HealthAssessment
import com.example.avtodigix.domain.HealthRules
import com.example.avtodigix.domain.TrafficLightStatus
import com.example.avtodigix.domain.DtcDescriptions
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
    private var latestObdState: ObdState = ObdState()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectionViewModel: ConnectionViewModel
    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter
    private var permissionsRequestInFlight = false
    private var permissionDialogStatus: PermissionStatus? = null
    private var permissionDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.create(applicationContext)
        repository = ScanSnapshotRepository(database.scanSnapshotDao())

        val flipper = binding.screenFlipper
        val selectedDeviceStore = SelectedDeviceStore(applicationContext)
        connectionViewModel = ViewModelProvider(
            this,
            ConnectionViewModelFactory(applicationContext, selectedDeviceStore)
        )[ConnectionViewModel::class.java]
        pairedDeviceAdapter = PairedDeviceAdapter { device ->
            connectionViewModel.onPairedDeviceSelected(device)
        }
        binding.connectionPairedList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = pairedDeviceAdapter
            setHasFixedSize(false)
        }
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            permissionsRequestInFlight = false
            val granted = results.values.all { it }
            val permanentlyDenied = results.any { (permission, isGranted) ->
                !isGranted && !shouldShowRequestPermissionRationale(permission)
            }
            connectionViewModel.onPermissionsResult(granted, permanentlyDenied)
        }

        bindNavigation(binding.welcomeNext, flipper, 1)
        bindNavigation(binding.connectionBack, flipper, 0)
        bindNavigation(binding.connectionNext, flipper, 2)
        bindNavigation(binding.summaryBack, flipper, 1)
        bindNavigation(binding.summaryNext, flipper, 3)
        bindNavigation(binding.metricsBack, flipper, 2)
        bindNavigation(binding.metricsNext, flipper, 4) {
            connectionViewModel.requestDtcRefresh()
        }
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

        binding.bluetoothEnableButton.setOnClickListener {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        binding.bluetoothSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        binding.connectionPairedSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
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
                launch {
                    connectionViewModel.connectionState.collect { state ->
                        renderConnectionState(state, permissionsLauncher)
                    }
                }
                launch {
                    connectionViewModel.obdState.collect { state ->
                        renderObdState(state)
                    }
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
        val state = latestObdState
        val snapshot = ScanSnapshot(
            timestampMillis = System.currentTimeMillis(),
            keyMetrics = buildKeyMetricsFromState(state),
            dtcList = buildDtcListFromState(state)
        )
        ioScope.launch {
            repository.saveSnapshot(snapshot)
        }
    }

    private fun buildKeyMetricsFromState(state: ObdState): Map<String, Double> {
        val metrics = state.metrics ?: return emptyMap()
        return buildMap {
            metrics.engineRpm?.let { value ->
                put(getString(R.string.metric_engine_rpm), value.toDouble())
            }
            metrics.vehicleSpeedKph?.let { value ->
                put(getString(R.string.metric_vehicle_speed), value.toDouble())
            }
            metrics.coolantTempCelsius?.let { value ->
                put(getString(R.string.metric_engine_temp), value.toDouble())
            }
            metrics.batteryVoltageVolts?.let { value ->
                put(getString(R.string.metric_battery_voltage), value)
            }
            metrics.shortTermFuelTrimPercent?.let { value ->
                put(getString(R.string.metric_fuel_trim), value)
            }
        }
    }

    private fun buildDtcListFromState(state: ObdState): List<String> {
        return (state.storedDtcs + state.pendingDtcs)
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun applySnapshotToUi(snapshot: ScanSnapshot) {
        updateMetricValue(binding.metricEngineRpm, binding.metricEngineRpmValue, snapshot)
        updateMetricValue(binding.metricVehicleSpeed, binding.metricVehicleSpeedValue, snapshot)
        updateMetricValue(binding.metricEngineTemp, binding.metricEngineTempValue, snapshot)
        updateMetricValue(binding.metricBatteryVoltage, binding.metricBatteryVoltageValue, snapshot)
        updateMetricValue(binding.metricFuelTrim, binding.metricFuelTrimValue, snapshot)

        if (snapshot.dtcList.isNotEmpty()) {
            binding.dtcStoredDetail.text = formatDtcList(snapshot.dtcList)
        }

        renderHealthSummary(
            engineRpm = readSnapshotMetric(snapshot, R.string.metric_engine_rpm),
            vehicleSpeedKph = readSnapshotMetric(snapshot, R.string.metric_vehicle_speed),
            coolantTempCelsius = readSnapshotMetric(snapshot, R.string.metric_engine_temp),
            batteryVoltage = readSnapshotMetric(snapshot, R.string.metric_battery_voltage),
            shortTermFuelTrimPercent = readSnapshotMetric(snapshot, R.string.metric_fuel_trim),
            longTermFuelTrimPercent = null,
            dtcCount = snapshot.dtcList.size
        )
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

    private fun readSnapshotMetric(snapshot: ScanSnapshot, labelRes: Int): Double? {
        return snapshot.keyMetrics[getString(labelRes)]
    }

    private fun renderConnectionState(
        state: ConnectionState,
        permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        when (state.status) {
            ConnectionState.Status.Idle -> {
                binding.connectionStatus.text = "Статус: не подключено"
                binding.connectionStatusDetail.text = "Нажмите Connect для начала диагностики."
            }
            ConnectionState.Status.PermissionsRequired -> {
                binding.connectionStatus.text = "Статус: требуется доступ к Bluetooth"
                binding.connectionStatusDetail.text = "Подтвердите разрешения для подключения."
                if (state.permissionStatus != PermissionStatus.PermanentlyDenied) {
                    requestBluetoothPermissions(permissionsLauncher)
                }
            }
            ConnectionState.Status.SelectingDevice -> {
                binding.connectionStatus.text = "Статус: выберите устройство"
                binding.connectionStatusDetail.text = state.errorMessage
                    ?: "Выберите OBD адаптер из списка."
            }
            ConnectionState.Status.Connecting -> {
                val deviceName = state.selectedDeviceName ?: "OBD адаптеру"
                binding.connectionStatus.text = "Статус: подключение"
                binding.connectionStatusDetail.text = "Подключение к $deviceName."
            }
            ConnectionState.Status.Initializing -> {
                val deviceName = state.selectedDeviceName ?: "OBD адаптеру"
                binding.connectionStatus.text = "Статус: инициализация"
                binding.connectionStatusDetail.text = "Настройка $deviceName."
            }
            ConnectionState.Status.Connected -> {
                val deviceName = state.selectedDeviceName ?: "OBD адаптеру"
                binding.connectionStatus.text = "Статус: подключено"
                binding.connectionStatusDetail.text = "Соединение с $deviceName установлено."
            }
            ConnectionState.Status.Error -> {
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
        val adapterEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        binding.bluetoothDisabledGroup.isVisible = !adapterEnabled
        pairedDeviceAdapter.submitList(state.pairedDevices, state.selectedDeviceAddress)
        binding.connectionPairedEmpty.isVisible = state.pairedDevices.isEmpty()
        binding.connectionPairedSettingsButton.isVisible = state.pairedDevices.isEmpty()

        binding.connectionConnect.isEnabled = state.status == ConnectionState.Status.Idle ||
            state.status == ConnectionState.Status.Error
        binding.connectionDisconnect.isEnabled = state.status == ConnectionState.Status.Connecting ||
            state.status == ConnectionState.Status.Initializing ||
            state.status == ConnectionState.Status.Connected

        renderPermissionDialog(state.permissionStatus, permissionsLauncher)
    }

    private fun requestBluetoothPermissions(
        permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        val permissions = requiredBluetoothPermissions()
        if (permissions.isEmpty()) {
            if (!permissionsRequestInFlight) {
                connectionViewModel.onPermissionsResult(true, false)
            }
            return
        }
        val hasPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermissions) {
            if (!permissionsRequestInFlight) {
                connectionViewModel.onPermissionsResult(true, false)
            }
            return
        }
        if (!permissionsRequestInFlight) {
            permissionsRequestInFlight = true
            permissionsLauncher.launch(permissions)
        }
    }

    private fun renderPermissionDialog(
        status: PermissionStatus,
        permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        if (status == permissionDialogStatus) {
            return
        }
        permissionDialog?.dismiss()
        permissionDialog = null
        permissionDialogStatus = status

        when (status) {
            PermissionStatus.Denied -> {
                permissionDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.permission_denied_title))
                    .setMessage(getString(R.string.permission_denied_message))
                    .setPositiveButton(getString(R.string.action_retry)) { _, _ ->
                        connectionViewModel.onPermissionsRetryRequested()
                        requestBluetoothPermissions(permissionsLauncher)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            PermissionStatus.PermanentlyDenied -> {
                permissionDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.permission_denied_title))
                    .setMessage(getString(R.string.permission_permanently_denied_message))
                    .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            else -> Unit
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
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
        snapshot.engineRpm?.let { value ->
            updateMetricValue(binding.metricEngineRpmValue, value.toDouble())
        }
        snapshot.vehicleSpeedKph?.let { value ->
            updateMetricValue(binding.metricVehicleSpeedValue, value.toDouble())
        }
        snapshot.coolantTempCelsius?.let { value ->
            updateMetricValue(binding.metricEngineTempValue, value.toDouble())
        }
        snapshot.shortTermFuelTrimPercent?.let { value ->
            updateMetricValue(binding.metricFuelTrimValue, value)
        }
        snapshot.batteryVoltageVolts?.let { value ->
            updateMetricValue(binding.metricBatteryVoltageValue, value)
        }
    }

    private fun renderObdState(state: ObdState) {
        latestObdState = state
        state.metrics?.let { snapshot ->
            updateLiveMetrics(snapshot)
        }
        binding.dtcStoredDetail.text = if (state.storedDtcs.isNotEmpty()) {
            formatDtcList(state.storedDtcs)
        } else {
            getString(R.string.dtc_no_codes)
        }
        binding.dtcPendingDetail.text = if (state.pendingDtcs.isNotEmpty()) {
            formatDtcList(state.pendingDtcs)
        } else {
            getString(R.string.dtc_no_codes)
        }
        val dtcCount = (state.storedDtcs + state.pendingDtcs).distinct().size
        renderHealthSummary(
            engineRpm = state.metrics?.engineRpm,
            vehicleSpeedKph = state.metrics?.vehicleSpeedKph?.toDouble(),
            coolantTempCelsius = state.metrics?.coolantTempCelsius?.toDouble(),
            batteryVoltage = state.metrics?.batteryVoltageVolts,
            shortTermFuelTrimPercent = state.metrics?.shortTermFuelTrimPercent,
            longTermFuelTrimPercent = state.metrics?.longTermFuelTrimPercent,
            dtcCount = dtcCount.takeIf { it > 0 }
        )
    }

    private fun renderHealthSummary(
        engineRpm: Double?,
        vehicleSpeedKph: Double?,
        coolantTempCelsius: Double?,
        batteryVoltage: Double?,
        shortTermFuelTrimPercent: Double?,
        longTermFuelTrimPercent: Double?,
        dtcCount: Int?
    ) {
        val dtcAssessment = HealthRules.evaluateDtcCount(dtcCount)
        updateHealthCard(
            assessment = HealthRules.evaluateEngine(
                engineRpm = engineRpm?.toInt(),
                vehicleSpeedKph = vehicleSpeedKph?.toInt(),
                coolantTempCelsius = coolantTempCelsius?.toInt(),
                dtcCount = dtcCount
            ),
            card = binding.engineCard,
            titleView = binding.engineTitle,
            statusView = binding.engineStatus,
            descriptionView = binding.engineDescription
        )
        updateHealthCard(
            assessment = HealthRules.evaluateCooling(coolantTempCelsius?.toInt()),
            card = binding.coolingCard,
            titleView = binding.coolingTitle,
            statusView = binding.coolingStatus,
            descriptionView = binding.coolingDescription
        )
        updateHealthCard(
            assessment = HealthRules.evaluateFuelTrims(shortTermFuelTrimPercent, longTermFuelTrimPercent),
            card = binding.fuelCard,
            titleView = binding.fuelTitle,
            statusView = binding.fuelStatus,
            descriptionView = binding.fuelDescription
        )
        updateHealthCard(
            assessment = HealthRules.evaluateBatteryVoltage(batteryVoltage),
            card = binding.batteryCard,
            titleView = binding.batteryTitle,
            statusView = binding.batteryStatus,
            descriptionView = binding.batteryDescription
        )
        updateHealthCard(
            assessment = dtcAssessment,
            card = binding.dtcSummaryCard,
            titleView = binding.dtcSummaryTitle,
            statusView = binding.dtcSummaryStatus,
            descriptionView = binding.dtcSummaryDescription
        )
    }

    private fun updateHealthCard(
        assessment: HealthAssessment,
        card: MaterialCardView,
        titleView: TextView,
        statusView: TextView,
        descriptionView: TextView
    ) {
        val backgroundColor = when (assessment.status) {
            TrafficLightStatus.GREEN -> R.color.traffic_green
            TrafficLightStatus.YELLOW -> R.color.traffic_yellow
            TrafficLightStatus.RED -> R.color.traffic_red
        }
        val textColor = when (assessment.status) {
            TrafficLightStatus.YELLOW -> android.R.color.black
            TrafficLightStatus.GREEN,
            TrafficLightStatus.RED -> android.R.color.white
        }
        card.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        statusView.text = statusLabel(assessment.status)
        descriptionView.text = assessment.message
        val resolvedTextColor = ContextCompat.getColor(this, textColor)
        titleView.setTextColor(resolvedTextColor)
        statusView.setTextColor(resolvedTextColor)
        descriptionView.setTextColor(resolvedTextColor)
    }

    private fun statusLabel(status: TrafficLightStatus): String {
        return when (status) {
            TrafficLightStatus.GREEN -> getString(R.string.health_status_green)
            TrafficLightStatus.YELLOW -> getString(R.string.health_status_yellow)
            TrafficLightStatus.RED -> getString(R.string.health_status_red)
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

    private fun formatDtcList(codes: List<String>): String {
        return codes
            .map { code ->
                val normalized = code.trim().uppercase(Locale.US)
                val description = DtcDescriptions.descriptionFor(normalized)
                    ?: "Описание недоступно"
                "$normalized — $description"
            }
            .joinToString(separator = "\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }

    override fun onStart() {
        super.onStart()
        connectionViewModel.resumePolling()
    }

    override fun onPause() {
        super.onPause()
        connectionViewModel.stopPolling()
    }

    override fun onStop() {
        super.onStop()
        connectionViewModel.stopPolling()
    }

    private companion object {
        val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    }
}

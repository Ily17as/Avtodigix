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
import android.widget.Toast
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
import com.example.avtodigix.connection.ScannerType
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.connection.WifiDeviceAdapter
import com.example.avtodigix.databinding.ActivityMainBinding
import com.example.avtodigix.domain.HealthAssessment
import com.example.avtodigix.domain.HealthRules
import com.example.avtodigix.domain.TrafficLightStatus
import com.example.avtodigix.domain.DtcDescriptions
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.ScanSnapshot
import com.example.avtodigix.storage.ScanSnapshotRepository
import com.example.avtodigix.storage.WifiResponseFormat
import com.example.avtodigix.storage.WifiScanSnapshot
import com.example.avtodigix.storage.WifiScanSnapshotRepository
import com.example.avtodigix.obd.ObdErrorType
import com.example.avtodigix.ui.AllDataAdapter
import com.example.avtodigix.ui.AllDataSection
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
    private lateinit var wifiSnapshotRepository: WifiScanSnapshotRepository
    private var latestObdState: ObdState = ObdState()
    private var latestConnectionState: ConnectionState = ConnectionState()
    private var latestWifiSnapshot: WifiScanSnapshot? = null
    private var lastWifiSnapshotMillis: Long? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectionViewModel: ConnectionViewModel
    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter
    private lateinit var wifiDeviceAdapter: WifiDeviceAdapter
    private lateinit var allDataAdapter: AllDataAdapter
    private var permissionsRequestInFlight = false
    private var permissionDialogStatus: PermissionStatus? = null
    private var permissionDialog: androidx.appcompat.app.AlertDialog? = null
    private var diagnosticsModeEnabled = false
    private var fullScanInProgress = false
    private var fullScanResults: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.create(applicationContext)
        repository = ScanSnapshotRepository(database.scanSnapshotDao())
        wifiSnapshotRepository = WifiScanSnapshotRepository(database.wifiScanSnapshotDao())

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
        wifiDeviceAdapter = WifiDeviceAdapter { device ->
            connectionViewModel.onWifiDeviceSelected(device)
        }
        binding.wifiDiscoveredList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiDeviceAdapter
            setHasFixedSize(false)
        }
        allDataAdapter = AllDataAdapter {
            Toast.makeText(
                this,
                getString(R.string.all_data_full_scan_unavailable),
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.allDataList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = allDataAdapter
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

        bindNavigation(binding.welcomeNext, flipper, SCREEN_CONNECTION)
        bindNavigation(binding.connectionBack, flipper, SCREEN_WELCOME)
        bindNavigation(binding.connectionNext, flipper, SCREEN_SUMMARY)
        bindNavigation(binding.summaryBack, flipper, SCREEN_CONNECTION)
        bindNavigation(binding.summaryDetails, flipper, SCREEN_ALL_DATA)
        bindNavigation(binding.allDataBack, flipper, SCREEN_SUMMARY)
        bindNavigation(binding.metricsBack, flipper, SCREEN_SUMMARY)
        bindNavigation(binding.metricsNext, flipper, SCREEN_DTC) {
            connectionViewModel.requestDtcRefresh()
        }
        bindNavigation(binding.dtcBack, flipper, SCREEN_METRICS)
        bindNavigation(binding.dtcFinish, flipper, SCREEN_SUMMARY) {
            saveCurrentSnapshot()
        }

        binding.connectionConnect.setOnClickListener {
            handleConnectRequested()
        }

        binding.connectionDisconnect.setOnClickListener {
            connectionViewModel.onDisconnectRequested()
        }

        binding.metricsReconnect.setOnClickListener {
            connectionViewModel.onConnectRequested()
        }

        binding.diagnosticsModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            diagnosticsModeEnabled = isChecked
            binding.diagnosticsCard.isVisible = isChecked
            renderDiagnostics(latestObdState)
        }

        val initialScannerType = connectionViewModel.connectionState.value.scannerType
        val initialUseBluetooth = initialScannerType == ScannerType.Bluetooth
        updateConnectionModeUi(initialUseBluetooth)
        binding.connectionModeGroup.check(
            if (initialUseBluetooth) R.id.connectionModeBluetooth else R.id.connectionModeWifi
        )
        binding.connectionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val useBluetooth = checkedId == R.id.connectionModeBluetooth
            updateConnectionModeUi(useBluetooth)
            val scannerType = if (useBluetooth) ScannerType.Bluetooth else ScannerType.Wifi
            connectionViewModel.onScannerTypeSelected(scannerType)
        }

        binding.wifiSaveButton.setOnClickListener {
            saveWifiSettings()
        }

        binding.wifiConnectButton.setOnClickListener {
            handleConnectRequested()
        }

        binding.wifiAutoResetButton.setOnClickListener {
            clearWifiInputErrors()
            binding.wifiIpInput.setText("")
            binding.wifiPortInput.setText("")
            connectionViewModel.onWifiAutoDetectReset()
        }

        binding.wifiAdvancedToggle.setOnClickListener {
            binding.wifiAdvancedGroup.isVisible = !binding.wifiAdvancedGroup.isVisible
        }

        binding.bluetoothEnableButton.setOnClickListener {
            runCatching {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }

        binding.bluetoothSettingsButton.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }

        binding.wifiSettingsButton.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }

        binding.connectionPairedSettingsButton.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }

        binding.summaryClearHistory.setOnClickListener {
            ioScope.launch {
                repository.clearHistory()
                wifiSnapshotRepository.clearHistory()
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
        ioScope.launch {
            val latestWifiSnapshot = wifiSnapshotRepository.getLatestSnapshot()
            if (latestWifiSnapshot != null) {
                withContext(Dispatchers.Main) {
                    this@MainActivity.latestWifiSnapshot = latestWifiSnapshot
                    updateWifiSnapshotUi(latestWifiSnapshot)
                }
            }
        }
        updateAllDataSections()

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
            metrics.longTermFuelTrimPercent?.let { value ->
                put(getString(R.string.metric_fuel_trim_long), value)
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
        updateMetricValue(binding.metricFuelTrimLong, binding.metricFuelTrimLongValue, snapshot)

        if (snapshot.dtcList.isNotEmpty()) {
            binding.dtcStoredDetail.text = formatDtcList(snapshot.dtcList)
        }

        renderHealthSummary(
            engineRpm = readSnapshotMetric(snapshot, R.string.metric_engine_rpm),
            vehicleSpeedKph = readSnapshotMetric(snapshot, R.string.metric_vehicle_speed),
            coolantTempCelsius = readSnapshotMetric(snapshot, R.string.metric_engine_temp),
            batteryVoltage = readSnapshotMetric(snapshot, R.string.metric_battery_voltage),
            shortTermFuelTrimPercent = readSnapshotMetric(snapshot, R.string.metric_fuel_trim),
            longTermFuelTrimPercent = readSnapshotMetric(snapshot, R.string.metric_fuel_trim_long),
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
        val previousStatus = latestConnectionState.status
        latestConnectionState = state
        val targetLabel = when (state.scannerType) {
            ScannerType.Wifi -> {
                val host = state.wifiHost
                val port = state.wifiPort
                if (!host.isNullOrBlank() && port != null) {
                    "$host:$port"
                } else {
                    "Wi-Fi адаптеру"
                }
            }
            ScannerType.Bluetooth -> state.selectedDeviceName ?: "OBD адаптеру"
        }
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
                binding.connectionStatus.text = "Статус: подключение"
                binding.connectionStatusDetail.text = "Подключение к $targetLabel."
            }
            ConnectionState.Status.Initializing -> {
                binding.connectionStatus.text = "Статус: инициализация"
                binding.connectionStatusDetail.text = "Настройка $targetLabel."
            }
            ConnectionState.Status.Connected -> {
                binding.connectionStatus.text = "Статус: подключено"
                binding.connectionStatusDetail.text = "Соединение с $targetLabel установлено."
                if (previousStatus != ConnectionState.Status.Connected) {
                    binding.screenFlipper.displayedChild = SCREEN_SUMMARY
                }
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
        val isConnected = state.status == ConnectionState.Status.Connected
        binding.summaryDetails.isVisible = isConnected
        binding.summaryDetailsHint.isVisible = !isConnected

        renderWifiDevices(state)
        renderWifiDetectionState(state)
        updateWifiSnapshotUi(latestWifiSnapshot)
        renderPermissionDialog(state.permissionStatus, permissionsLauncher)
        updateAllDataSections()
    }

    private fun renderWifiDevices(state: ConnectionState) {
        val results = state.wifiAutoDetectResults
        wifiDeviceAdapter.submitList(results, state.wifiHost, state.wifiPort)
        binding.wifiDiscoveredList.isVisible = results.isNotEmpty()
        binding.wifiDiscoveredEmpty.isVisible = results.isEmpty()

        if (state.scannerType != ScannerType.Wifi) {
            return
        }

        val host = state.wifiHost?.takeIf { it.isNotBlank() }
        if (host != null &&
            !binding.wifiIpInput.hasFocus() &&
            binding.wifiIpInput.text?.toString() != host
        ) {
            binding.wifiIpInput.setText(host)
        }
        val port = state.wifiPort
        val portText = port?.toString()
        if (portText != null &&
            !binding.wifiPortInput.hasFocus() &&
            binding.wifiPortInput.text?.toString() != portText
        ) {
            binding.wifiPortInput.setText(portText)
        }
    }

    private fun updateConnectionModeUi(useBluetooth: Boolean) {
        binding.bluetoothSection.isVisible = useBluetooth
        binding.wifiFormGroup.isVisible = !useBluetooth
    }

    private fun renderWifiDetectionState(state: ConnectionState) {
        val isWifi = state.scannerType == ScannerType.Wifi
        binding.wifiDetectStatusRow.isVisible = isWifi
        if (!isWifi) {
            return
        }
        binding.wifiDetectProgress.isVisible = state.wifiDetectInProgress
        val status = when {
            state.wifiDetectInProgress -> getString(R.string.wifi_detect_in_progress)
            !state.wifiResolvedEndpoint.isNullOrBlank() -> {
                getString(R.string.wifi_detect_found, state.wifiResolvedEndpoint)
            }
            state.wifiDetectError != null -> getString(R.string.wifi_detect_not_found)
            else -> getString(R.string.wifi_detect_placeholder)
        }
        binding.wifiDetectStatus.text = status
    }

    private fun saveWifiSettings() {
        val override = readWifiOverride() ?: return
        connectionViewModel.onWifiSettingsSaved(override.first, override.second)
    }

    private fun handleConnectRequested() {
        if (connectionViewModel.connectionState.value.scannerType != ScannerType.Wifi) {
            connectionViewModel.onConnectRequested()
            return
        }
        val hasManualInput = hasManualWifiInput()
        val override = readWifiOverride()
        if (hasManualInput) {
            if (override != null) {
                connectionViewModel.onWifiConnectRequested(override.first, override.second)
            }
            return
        }
        clearWifiInputErrors()
        connectionViewModel.onConnectRequested()
    }

    private fun readWifiOverride(): Pair<String, Int>? {
        val ip = binding.wifiIpInput.text?.toString()?.trim().orEmpty()
        val portText = binding.wifiPortInput.text?.toString()?.trim().orEmpty()
        var valid = true

        if (!isValidIpAddress(ip)) {
            binding.wifiIpInputLayout.error = getString(R.string.connection_error_ip)
            valid = false
        } else {
            binding.wifiIpInputLayout.error = null
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..MAX_PORT) {
            binding.wifiPortInputLayout.error = getString(R.string.connection_error_port)
            valid = false
        } else {
            binding.wifiPortInputLayout.error = null
        }

        if (!valid) {
            return null
        }

        return ip to (port ?: return null)
    }

    private fun hasManualWifiInput(): Boolean {
        val ip = binding.wifiIpInput.text?.toString()?.trim().orEmpty()
        val port = binding.wifiPortInput.text?.toString()?.trim().orEmpty()
        return ip.isNotBlank() || port.isNotBlank()
    }

    private fun clearWifiInputErrors() {
        binding.wifiIpInputLayout.error = null
        binding.wifiPortInputLayout.error = null
    }

    private fun isValidIpAddress(ip: String): Boolean {
        if (ip.isBlank()) {
            return false
        }
        return IP_ADDRESS_REGEX.matches(ip)
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
            try {
                permissionsLauncher.launch(permissions)
            } catch (e: Exception) {
                permissionsRequestInFlight = false
                connectionViewModel.onPermissionsResult(false, false)
            }
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

    private fun updateLiveMetrics(snapshot: com.example.avtodigix.obd.LivePidSnapshot?, errorLabel: String?) {
        updateMetricValueOrPlaceholder(binding.metricEngineRpmValue, snapshot?.engineRpm, errorLabel)
        updateMetricValueOrPlaceholder(
            binding.metricVehicleSpeedValue,
            snapshot?.vehicleSpeedKph?.toDouble(),
            errorLabel
        )
        updateMetricValueOrPlaceholder(
            binding.metricEngineTempValue,
            snapshot?.coolantTempCelsius?.toDouble(),
            errorLabel
        )
        updateMetricValueOrPlaceholder(binding.metricFuelTrimValue, snapshot?.shortTermFuelTrimPercent, errorLabel)
        updateMetricValueOrPlaceholder(binding.metricFuelTrimLongValue, snapshot?.longTermFuelTrimPercent, errorLabel)
        updateMetricValueOrPlaceholder(
            binding.metricBatteryVoltageValue,
            snapshot?.batteryVoltageVolts,
            errorLabel
        )
    }

    private fun renderObdState(state: ObdState) {
        latestObdState = state
        val liveErrorLabel = liveDataErrorLabel(state.liveDataErrorType)
        updateLiveMetrics(state.metrics, liveErrorLabel)
        renderDiagnostics(state)
        updateWifiSnapshotFromObdState(state)
        val secondsSinceUpdate = state.lastUpdatedMillis?.let { lastUpdated ->
            ((System.currentTimeMillis() - lastUpdated) / 1000).coerceAtLeast(0)
        }
        binding.metricsUpdated.text = if (secondsSinceUpdate != null) {
            getString(R.string.metrics_updated, secondsSinceUpdate)
        } else {
            getString(R.string.metrics_updated_placeholder)
        }
        val statusResId = when {
            secondsSinceUpdate != null && secondsSinceUpdate > 15 -> R.string.metrics_status_lost
            secondsSinceUpdate != null && secondsSinceUpdate > 5 -> R.string.metrics_status_stale
            else -> R.string.metrics_status
        }
        binding.metricsStatus.text = getString(statusResId)
        binding.metricsReconnect.isVisible = state.showReconnectButton ||
            (secondsSinceUpdate != null && secondsSinceUpdate > 15)
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
        updateAllDataSections()
    }

    private fun renderDiagnostics(state: ObdState) {
        if (!diagnosticsModeEnabled) {
            return
        }
        val command = state.lastCommand?.ifBlank { null } ?: PLACEHOLDER_VALUE
        val rawResponse = state.lastRawResponse?.ifBlank { null } ?: PLACEHOLDER_VALUE
        val errorLabel = when (state.lastErrorType) {
            ObdErrorType.TIMEOUT -> getString(R.string.diagnostics_error_timeout)
            ObdErrorType.NO_DATA -> getString(R.string.diagnostics_error_no_data)
            ObdErrorType.UNABLE_TO_CONNECT -> getString(R.string.diagnostics_error_unable_connect)
            ObdErrorType.SOCKET_CLOSED -> getString(R.string.diagnostics_error_socket_closed)
            ObdErrorType.NEGATIVE_RESPONSE -> getString(R.string.diagnostics_error_negative)
            ObdErrorType.IO -> getString(R.string.diagnostics_error_io)
            null -> getString(R.string.diagnostics_error_none)
        }
        binding.diagnosticsLastCommandValue.text = command
        binding.diagnosticsRawResponseValue.text = rawResponse
        binding.diagnosticsErrorValue.text = errorLabel
    }

    private fun updateWifiSnapshotFromObdState(state: ObdState) {
        if (latestConnectionState.scannerType != ScannerType.Wifi) {
            return
        }
        state.metrics ?: return
        val host = latestConnectionState.wifiHost ?: return
        val port = latestConnectionState.wifiPort ?: return
        val timestampMillis = state.lastUpdatedMillis ?: System.currentTimeMillis()
        val snapshot = WifiScanSnapshot(
            timestampMillis = timestampMillis,
            host = host,
            port = port,
            responseFormat = WifiResponseFormat.Text,
            keyMetrics = buildKeyMetricsFromState(state),
            dtcList = buildDtcListFromState(state)
        )
        latestWifiSnapshot = snapshot
        updateWifiSnapshotUi(snapshot)
        if (lastWifiSnapshotMillis == timestampMillis) {
            return
        }
        lastWifiSnapshotMillis = timestampMillis
        ioScope.launch {
            wifiSnapshotRepository.saveSnapshot(snapshot)
        }
    }

    private fun updateWifiSnapshotUi(snapshot: WifiScanSnapshot?) {
        val isWifi = latestConnectionState.scannerType == ScannerType.Wifi
        binding.wifiSnapshotCard.isVisible = isWifi
        if (!isWifi) {
            return
        }
        val formatLabel = snapshot?.let { getString(R.string.wifi_response_format_text) } ?: PLACEHOLDER_VALUE
        binding.wifiSnapshotFormat.text = getString(R.string.wifi_snapshot_format, formatLabel)
        val hostLabel = snapshot?.let { "${it.host}:${it.port}" } ?: PLACEHOLDER_VALUE
        binding.wifiSnapshotHost.text = getString(R.string.wifi_snapshot_host, hostLabel)
        val updatedLabel = snapshot?.let {
            val seconds = ((System.currentTimeMillis() - it.timestampMillis) / 1000).coerceAtLeast(0)
            getString(R.string.wifi_snapshot_updated, seconds)
        } ?: getString(R.string.wifi_snapshot_updated_placeholder)
        binding.wifiSnapshotUpdated.text = updatedLabel
        binding.wifiSnapshotSummary.text = if (snapshot != null) {
            getString(R.string.wifi_snapshot_summary, snapshot.keyMetrics.size, snapshot.dtcList.size)
        } else {
            getString(R.string.wifi_snapshot_summary_placeholder)
        }
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

    private fun updateMetricValueOrPlaceholder(valueView: TextView, value: Double?, errorLabel: String?) {
        if (value == null) {
            valueView.text = errorLabel ?: PLACEHOLDER_VALUE
        } else {
            updateMetricValue(valueView, value)
        }
    }

    private fun updateAllDataSections() {
        val metrics = latestObdState.metrics
        val metricsList = listOf(
            getString(R.string.metric_engine_rpm) to (metrics?.engineRpm?.toInt()?.toString() ?: PLACEHOLDER_VALUE),
            getString(R.string.metric_vehicle_speed) to (metrics?.vehicleSpeedKph?.toString() ?: PLACEHOLDER_VALUE),
            getString(R.string.metric_engine_temp) to (metrics?.coolantTempCelsius?.toString() ?: PLACEHOLDER_VALUE),
            getString(R.string.metric_battery_voltage) to (metrics?.batteryVoltageVolts?.toString() ?: PLACEHOLDER_VALUE),
            getString(R.string.metric_fuel_trim) to (metrics?.shortTermFuelTrimPercent?.toString() ?: PLACEHOLDER_VALUE),
            getString(R.string.metric_fuel_trim_long) to (metrics?.longTermFuelTrimPercent?.toString() ?: PLACEHOLDER_VALUE)
        )
        val dtcCount = (latestObdState.storedDtcs + latestObdState.pendingDtcs).distinct().size
        val milStatus = if (dtcCount > 0) {
            getString(R.string.all_data_mil_active)
        } else {
            getString(R.string.all_data_mil_inactive)
        }
        val stored = if (latestObdState.storedDtcs.isNotEmpty()) {
            formatDtcList(latestObdState.storedDtcs)
        } else {
            getString(R.string.dtc_no_codes)
        }
        val pending = if (latestObdState.pendingDtcs.isNotEmpty()) {
            formatDtcList(latestObdState.pendingDtcs)
        } else {
            getString(R.string.dtc_no_codes)
        }
        val fullScanResultsText = if (fullScanResults.isNotEmpty()) {
            fullScanResults.joinToString(separator = "\n")
        } else {
            getString(R.string.all_data_full_scan_empty)
        }
        val rawLogLines = buildList {
            latestObdState.recentDiagnostics
                .takeLast(RAW_LOG_VISIBLE_LIMIT)
                .forEach { diagnostics ->
                    add(getString(R.string.all_data_raw_log_command, diagnostics.command))
                    add(getString(R.string.all_data_raw_log_response, diagnostics.rawResponse ?: "-"))
                    val errorLabel = diagnostics.errorType?.let { liveDataErrorLabel(it) ?: it.name }
                        ?: getString(R.string.all_data_raw_log_error_none)
                    add(getString(R.string.all_data_raw_log_error, errorLabel))
                }
            latestConnectionState.log.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val rawLogText = if (rawLogLines.isNotEmpty()) {
            rawLogLines.joinToString(separator = "\n")
        } else {
            getString(R.string.all_data_raw_log_empty)
        }
        allDataAdapter.submitList(
            listOf(
                AllDataSection.LiveMetrics(metricsList, milStatus),
                AllDataSection.Dtc(stored, pending),
                AllDataSection.FullScan(fullScanInProgress, fullScanResultsText),
                AllDataSection.RawLog(rawLogText)
            )
        )
    }

    private fun liveDataErrorLabel(errorType: ObdErrorType?): String? {
        return when (errorType) {
            ObdErrorType.TIMEOUT -> getString(R.string.diagnostics_error_timeout)
            ObdErrorType.NO_DATA -> getString(R.string.diagnostics_error_no_data)
            ObdErrorType.UNABLE_TO_CONNECT -> getString(R.string.diagnostics_error_unable_connect)
            ObdErrorType.SOCKET_CLOSED -> getString(R.string.diagnostics_error_socket_closed)
            ObdErrorType.NEGATIVE_RESPONSE -> getString(R.string.diagnostics_error_negative)
            ObdErrorType.IO -> getString(R.string.diagnostics_error_io)
            null -> null
        }
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
        const val SCREEN_WELCOME = 0
        const val SCREEN_CONNECTION = 1
        const val SCREEN_SUMMARY = 2
        const val SCREEN_ALL_DATA = 3
        const val SCREEN_METRICS = 4
        const val SCREEN_DTC = 5
        const val RAW_LOG_VISIBLE_LIMIT = 50
        val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
        val IP_ADDRESS_REGEX = Regex(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$"
        )
        const val MAX_PORT = 65535
        const val PLACEHOLDER_VALUE = "—"
    }
}

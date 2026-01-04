package com.example.avtodigix.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.avtodigix.bluetooth.BluetoothConnectionManager
import com.example.avtodigix.bluetooth.ConnectionStatus
import com.example.avtodigix.bluetooth.PairedDevice
import com.example.avtodigix.elm.ElmSession
import com.example.avtodigix.obd.DEFAULT_LIVE_METRICS
import com.example.avtodigix.obd.ObdDiagnostics
import com.example.avtodigix.obd.ObdErrorType
import com.example.avtodigix.obd.LiveMetricDefinition
import com.example.avtodigix.obd.LivePidSnapshot
import com.example.avtodigix.obd.ObdService
import com.example.avtodigix.transport.BluetoothObdTransport
import com.example.avtodigix.transport.ObdTransport
import com.example.avtodigix.transport.WifiObdTransport
import com.example.avtodigix.wifi.WiFiScannerManager
import com.example.avtodigix.wifi.WifiAutoDetectResult
import com.example.avtodigix.wifi.WifiObdAutoDetector
import com.example.avtodigix.wifi.WifiConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionViewModel(
    private val connectionManager: BluetoothConnectionManager,
    private val wifiScannerManager: WiFiScannerManager,
    private val wifiObdAutoDetector: WifiObdAutoDetector,
    private val selectedDeviceStore: SelectedDeviceStore
) : ViewModel() {
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState
    private val _obdState = MutableStateFlow(ObdState())
    val obdState: StateFlow<ObdState> = _obdState

    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var session: ElmSession? = null
    private var obdService: ObdService? = null
    private var supportedPids: Set<Int>? = null
    private var activeTransport: ObdTransport? = null
    private val dtcRefreshRequested = AtomicBoolean(false)
    private var lastDtcReadMillis = 0L
    private var consecutivePidReadErrors = 0
    private var lastRecoveryAttemptMillis = 0L

    init {
        val storedScannerType = selectedDeviceStore.getSelectedScannerType() ?: ScannerType.Bluetooth
        val storedWifiHost = selectedDeviceStore.getWifiHost()
        val storedWifiPort = selectedDeviceStore.getWifiPort()
        val storedWifiEndpoint = if (!storedWifiHost.isNullOrBlank() && storedWifiPort != null) {
            "$storedWifiHost:$storedWifiPort"
        } else {
            null
        }
        updateConnectionState {
            copy(
                selectedDeviceAddress = selectedDeviceStore.getSelectedDeviceAddress(),
                scannerType = storedScannerType,
                wifiHost = storedWifiHost,
                wifiPort = storedWifiPort,
                wifiResolvedEndpoint = storedWifiEndpoint
            )
        }
        if (storedScannerType == ScannerType.Wifi) {
            wifiScannerManager.startDiscovery()
        } else {
            wifiScannerManager.stopDiscovery()
        }
        refreshPairedDevices()
        viewModelScope.launch {
            connectionManager.status.collect { status ->
                if (connectionState.value.scannerType != ScannerType.Bluetooth) {
                    return@collect
                }
                when (status) {
                    ConnectionStatus.AdapterNotResponding -> {
                        updateConnectionState {
                            copy(
                                status = ConnectionState.Status.Error,
                                errorMessage = "Bluetooth адаптер недоступен."
                            )
                        }
                    }
                    ConnectionStatus.NoConnection -> {
                        if (connectionState.value.status == ConnectionState.Status.Connected ||
                            connectionState.value.status == ConnectionState.Status.Connecting ||
                            connectionState.value.status == ConnectionState.Status.Initializing
                        ) {
                            updateConnectionState { copy(status = ConnectionState.Status.Idle) }
                        }
                    }
                    ConnectionStatus.Connected -> {
                        updateConnectionState {
                            copy(
                                status = ConnectionState.Status.Connecting,
                                log = appendLog("Bluetooth соединение установлено.")
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            wifiScannerManager.status.collect { status ->
                if (connectionState.value.scannerType != ScannerType.Wifi) {
                    return@collect
                }
                when (status) {
                    WifiConnectionStatus.Connecting -> {
                        updateConnectionState {
                            copy(
                                status = ConnectionState.Status.Connecting,
                                log = appendLog("Устанавливаем Wi-Fi соединение.")
                            )
                        }
                    }
                    WifiConnectionStatus.Connected -> {
                        updateConnectionState {
                            copy(
                                status = ConnectionState.Status.Connecting,
                                log = appendLog("Wi-Fi соединение установлено.")
                            )
                        }
                    }
                    WifiConnectionStatus.Failed -> {
                        if (connectionState.value.status == ConnectionState.Status.Connecting ||
                            connectionState.value.status == ConnectionState.Status.Initializing ||
                            connectionState.value.status == ConnectionState.Status.Connected
                        ) {
                            updateConnectionState {
                                copy(
                                    status = ConnectionState.Status.Error,
                                    errorMessage = "Не удалось установить Wi-Fi соединение."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onConnectRequested() {
        if (connectionState.value.scannerType == ScannerType.Wifi) {
            val host = connectionState.value.wifiHost
            val port = connectionState.value.wifiPort
            if (!host.isNullOrBlank() && port != null) {
                onWifiConnectRequested(host, port)
                return
            }
            startWifiAutoDetect()
            return
        }
        updateConnectionState {
            copy(
                status = ConnectionState.Status.PermissionsRequired,
                permissionStatus = PermissionStatus.Requested,
                errorMessage = null,
                log = appendLog("Запрос разрешений Bluetooth.")
            )
        }
    }

    fun onWifiConnectRequested(host: String, port: Int) {
        connectJob?.cancel()
        selectedDeviceStore.setSelectedScannerType(ScannerType.Wifi)
        selectedDeviceStore.setWifiHost(host)
        selectedDeviceStore.setWifiPort(port)
        updateConnectionState {
            copy(
                scannerType = ScannerType.Wifi,
                wifiHost = host,
                wifiPort = port,
                wifiDetectInProgress = false,
                wifiDetectError = null,
                wifiResolvedEndpoint = "$host:$port",
                status = ConnectionState.Status.Connecting,
                errorMessage = null,
                log = appendLog("Подключение по Wi-Fi к $host:$port.")
            )
        }
        connectJob = viewModelScope.launch { connectToWifi(host, port) }
    }

    fun onWifiDeviceSelected(device: WifiAutoDetectResult) {
        selectedDeviceStore.setSelectedScannerType(ScannerType.Wifi)
        selectedDeviceStore.setWifiHost(device.host)
        selectedDeviceStore.setWifiPort(device.port)
        updateConnectionState {
            copy(
                scannerType = ScannerType.Wifi,
                wifiHost = device.host,
                wifiPort = device.port,
                wifiResolvedEndpoint = "${device.host}:${device.port}",
                wifiDetectError = null,
                errorMessage = null
            )
        }
    }

    fun onScannerTypeSelected(scannerType: ScannerType) {
        selectedDeviceStore.setSelectedScannerType(scannerType)
        if (scannerType == ScannerType.Wifi) {
            wifiScannerManager.startDiscovery()
        } else {
            wifiScannerManager.stopDiscovery()
        }
        val wifiHost = if (scannerType == ScannerType.Wifi) {
            selectedDeviceStore.getWifiHost()
        } else {
            null
        }
        val wifiPort = if (scannerType == ScannerType.Wifi) {
            selectedDeviceStore.getWifiPort()
        } else {
            null
        }
        val wifiResolvedEndpoint = if (!wifiHost.isNullOrBlank() && wifiPort != null) {
            "$wifiHost:$wifiPort"
        } else {
            null
        }
        updateConnectionState {
            copy(
                scannerType = scannerType,
                wifiHost = wifiHost,
                wifiPort = wifiPort,
                wifiResolvedEndpoint = wifiResolvedEndpoint,
                wifiDetectError = null,
                wifiDetectInProgress = false,
                errorMessage = null
            )
        }
    }

    fun onWifiSettingsSaved(host: String, port: Int) {
        selectedDeviceStore.setWifiHost(host)
        selectedDeviceStore.setWifiPort(port)
        updateConnectionState {
            copy(
                wifiHost = host,
                wifiPort = port,
                wifiResolvedEndpoint = "$host:$port",
                wifiDetectError = null,
                wifiDetectInProgress = false,
                errorMessage = null
            )
        }
    }

    fun onWifiAutoDetectReset() {
        selectedDeviceStore.clearWifiSettings()
        updateConnectionState {
            copy(
                wifiHost = null,
                wifiPort = null,
                wifiResolvedEndpoint = null,
                wifiDetectError = null,
                wifiDetectInProgress = false,
                errorMessage = null
            )
        }
    }

    fun onPermissionsResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (connectionState.value.scannerType == ScannerType.Wifi) {
            return
        }
        if (!granted) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    permissionStatus = if (permanentlyDenied) {
                        PermissionStatus.PermanentlyDenied
                    } else {
                        PermissionStatus.Denied
                    },
                    errorMessage = if (permanentlyDenied) {
                        "Разрешение Bluetooth отключено в настройках."
                    } else {
                        "Разрешения Bluetooth не выданы."
                    }
                )
            }
            return
        }
        updateConnectionState { copy(permissionStatus = PermissionStatus.None) }
        refreshPairedDevices()
        if (connectJob?.isActive == true) {
            return
        }
        connectJob?.cancel()
        val selectedAddress = connectionState.value.selectedDeviceAddress
        if (selectedAddress.isNullOrBlank()) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.SelectingDevice,
                    errorMessage = "Выберите Bluetooth-устройство из списка."
                )
            }
            return
        }
        connectJob = viewModelScope.launch { connectToDevice() }
    }

    fun onPermissionsRetryRequested() {
        updateConnectionState {
            copy(
                status = ConnectionState.Status.PermissionsRequired,
                permissionStatus = PermissionStatus.Requested,
                errorMessage = null,
                log = appendLog("Повторный запрос разрешений Bluetooth.")
            )
        }
    }

    fun onDisconnectRequested() {
        connectJob?.cancel()
        readJob?.cancel()
        readJob = null
        viewModelScope.launch {
            try {
                session?.close()
            } finally {
                session = null
                obdService = null
                supportedPids = null
                activeTransport?.disconnect()
                activeTransport = null
            }
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Idle,
                    permissionStatus = PermissionStatus.None,
                    log = appendLog("Соединение завершено пользователем.")
                )
            }
            dtcRefreshRequested.set(false)
            lastDtcReadMillis = 0L
            _obdState.value = ObdState()
        }
    }

    fun stopPolling() {
        readJob?.cancel()
        readJob = null
    }

    fun resumePolling() {
        val service = obdService ?: return
        if (readJob?.isActive == true) {
            return
        }
        startReading(service, supportedPids)
    }

    fun requestDtcRefresh() {
        dtcRefreshRequested.set(true)
    }

    fun refreshPairedDevices() {
        viewModelScope.launch {
            val devices = connectionManager.getPairedDevices()
            val selectedAddress = connectionState.value.selectedDeviceAddress
            val selectedDevice = devices.firstOrNull { it.address == selectedAddress }
            updateConnectionState {
                copy(
                    pairedDevices = devices,
                    selectedDeviceName = selectedDevice?.name
                )
            }
        }
    }

    fun onPairedDeviceSelected(device: PairedDevice) {
        selectedDeviceStore.setSelectedDeviceAddress(device.address)
        updateConnectionState {
            copy(
                selectedDeviceAddress = device.address,
                selectedDeviceName = device.name,
                errorMessage = null,
                status = ConnectionState.Status.Idle
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        readJob?.cancel()
        viewModelScope.launch {
            try {
                session?.close()
            } finally {
                session = null
                obdService = null
                supportedPids = null
                activeTransport?.disconnect()
                activeTransport = null
                connectionManager.disconnect()
                wifiScannerManager.disconnect()
                wifiScannerManager.stopDiscovery()
            }
        }
    }

    private suspend fun connectToDevice() {
        val selectedAddress = connectionState.value.selectedDeviceAddress
        if (selectedAddress.isNullOrBlank()) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = "Выберите Bluetooth-устройство из списка."
                )
            }
            return
        }
        updateConnectionState {
            copy(
                status = ConnectionState.Status.Connecting,
                errorMessage = null,
                log = appendLog("Поиск сопряженных устройств.")
            )
        }
        val devices = connectionManager.getPairedDevices()
        updateConnectionState { copy(pairedDevices = devices) }
        val selected = devices.firstOrNull { it.address == selectedAddress }
        if (selected == null) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = "Выбранное устройство не найдено в списке сопряженных."
                )
            }
            return
        }
        updateConnectionState {
            copy(
                selectedDeviceName = selected.name,
                status = ConnectionState.Status.Connecting,
                log = appendLog("Подключение к ${selected.name}.")
            )
        }

        val transport = BluetoothObdTransport(
            manager = connectionManager,
            address = selected.address,
            uuid = SPP_UUID,
            timeoutMillis = CONNECTION_TIMEOUT_MILLIS
        )
        connectAndInitialize(transport) { error ->
            if (error is TimeoutCancellationException) {
                updateConnectionState {
                    copy(
                        status = ConnectionState.Status.Error,
                        errorMessage = "Истекло время ожидания подключения."
                    )
                }
            } else {
                updateConnectionState {
                    copy(
                        status = ConnectionState.Status.Error,
                        errorMessage = "Ошибка подключения Bluetooth."
                    )
                }
            }
        }
    }

    private suspend fun connectToWifi(host: String, port: Int) {
        val transport = WifiObdTransport(
            manager = wifiScannerManager,
            host = host,
            port = port
        )
        connectAndInitialize(transport) { error ->
            val message = when (error) {
                is SocketTimeoutException -> "Превышено время ожидания ответа. Проверьте IP и порт."
                is UnknownHostException -> "Не удалось определить адрес устройства. Проверьте IP."
                is NoRouteToHostException -> "IP недоступен. Проверьте подключение к сети."
                is ConnectException -> "Порт недоступен или закрыт. Проверьте порт адаптера."
                is IllegalArgumentException -> "Некорректные параметры Wi-Fi подключения."
                is IOException -> "Ошибка подключения по Wi-Fi."
                else -> "Ошибка подключения по Wi-Fi."
            }
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = message,
                    log = appendLog("Wi-Fi: $message")
                )
            }
        }
    }

    private fun startWifiAutoDetect() {
        connectJob?.cancel()
        updateConnectionState {
            copy(
                status = ConnectionState.Status.Connecting,
                wifiDetectInProgress = true,
                wifiDetectError = null,
                wifiAutoDetectResults = emptyList(),
                wifiResolvedEndpoint = null,
                errorMessage = null,
                log = appendLog("Запускаем автодетект Wi-Fi адаптера.")
            )
        }
        connectJob = viewModelScope.launch {
            val candidates = wifiObdAutoDetector.generateCandidates()
            val results = wifiObdAutoDetector.scan(candidates)
            val detected = results.minByOrNull { it.rttMs }
            if (detected != null) {
                val endpoint = "${detected.host}:${detected.port}"
                selectedDeviceStore.setWifiHost(detected.host)
                selectedDeviceStore.setWifiPort(detected.port)
                updateConnectionState {
                    copy(
                        wifiHost = detected.host,
                        wifiPort = detected.port,
                        wifiResolvedEndpoint = endpoint,
                        wifiDetectInProgress = false,
                        wifiDetectError = null,
                        wifiAutoDetectResults = results,
                        status = ConnectionState.Status.Connecting,
                        errorMessage = null,
                        log = appendLog("Автодетект нашел адаптер по адресу $endpoint.")
                    )
                }
                connectToWifi(detected.host, detected.port)
            } else {
                updateConnectionState {
                    copy(
                        status = ConnectionState.Status.Error,
                        wifiDetectInProgress = false,
                        wifiDetectError = "Не удалось автоматически найти Wi-Fi адаптер.",
                        wifiResolvedEndpoint = null,
                        wifiAutoDetectResults = emptyList(),
                        errorMessage = "Не удалось определить Wi-Fi адаптер. Откройте «Дополнительно» и укажите IP и порт.",
                        log = appendLog("Автодетект Wi-Fi адаптера не дал результатов.")
                    )
                }
            }
        }
    }

    private suspend fun connectAndInitialize(transport: ObdTransport, onError: (Throwable) -> Unit) {
        activeTransport = transport
        try {
            transport.connect()
        } catch (error: Throwable) {
            activeTransport = null
            onError(error)
            return
        }
        startElmSession(transport)
    }

    private suspend fun startElmSession(transport: ObdTransport) {
        val newSession = ElmSession(transport.input, transport.output, parentScope = viewModelScope)
        session = newSession
        updateConnectionState {
            copy(
                status = ConnectionState.Status.Initializing,
                log = appendLog("Инициализация ELM327.")
            )
        }

        try {
            newSession.initialize()
        } catch (error: IOException) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = "Ошибка инициализации адаптера."
                )
            }
            return
        } catch (error: IllegalStateException) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = "Ошибка при настройке ELM327."
                )
            }
            return
        }

        val service = ObdService(newSession, ::onDiagnosticsUpdated)
        obdService = service
        updateConnectionState {
            copy(log = appendLog("Читаем поддерживаемые PID."))
        }
        val supportedPids = runCatching { service.readSupportedPids() }.getOrDefault(emptyMap())
        val availableMetrics = DEFAULT_LIVE_METRICS.filter { metric ->
            supportedPids.containsKey(metric.pid)
        }
        val supportedPidSet = supportedPids.keys.toSet()
        val supportedPidSetOrNull = supportedPidSet.takeIf { it.isNotEmpty() }
        this.supportedPids = supportedPidSetOrNull
        _obdState.value = _obdState.value.copy(supportedPids = supportedPidSet)
        updateConnectionState {
            copy(
                status = ConnectionState.Status.Connected,
                supportedMetrics = availableMetrics,
                log = appendLog("OBD сервис готов, начинаем чтение данных.")
            )
        }
        startReading(service, supportedPidSetOrNull)
    }

    private fun onDiagnosticsUpdated(diagnostics: ObdDiagnostics) {
        val nowMillis = System.currentTimeMillis()
        val isLivePidCommand = diagnostics.command.trim().startsWith("01")
        val lastPidErrorType = if (isLivePidCommand && diagnostics.errorType != null) {
            diagnostics.errorType
        } else {
            _obdState.value.lastPidErrorType
        }
        val lastPidErrorAtMillis = if (isLivePidCommand && diagnostics.errorType != null) {
            nowMillis
        } else {
            _obdState.value.lastPidErrorAtMillis
        }
        val recentDiagnostics = (_obdState.value.recentDiagnostics + diagnostics)
            .takeLast(RECENT_DIAGNOSTICS_LIMIT)
        _obdState.value = _obdState.value.copy(
            lastCommand = diagnostics.command,
            lastRawResponse = diagnostics.rawResponse,
            lastErrorType = diagnostics.errorType,
            lastPidErrorType = lastPidErrorType,
            lastPidErrorAtMillis = lastPidErrorAtMillis,
            recentDiagnostics = recentDiagnostics
        )
    }

    private fun startReading(service: ObdService, supportedPids: Set<Int>?) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            consecutivePidReadErrors = 0
            lastRecoveryAttemptMillis = 0L
            while (isActive) {
                val cycleStartMillis = System.currentTimeMillis()
                val liveResult = runCatching { service.readLiveData(supportedPids) }
                val liveSnapshot = liveResult.getOrNull()
                var liveErrorType = liveResult.exceptionOrNull()?.let { mapReadErrorToType(it) }
                if (liveErrorType == null) {
                    val lastPidErrorAt = _obdState.value.lastPidErrorAtMillis
                    if (lastPidErrorAt != null && lastPidErrorAt >= cycleStartMillis) {
                        liveErrorType = _obdState.value.lastPidErrorType
                    }
                }
                val hasLiveError = liveErrorType != null
                consecutivePidReadErrors = if (hasLiveError) {
                    consecutivePidReadErrors + 1
                } else {
                    0
                }
                if (hasLiveError && consecutivePidReadErrors >= PID_READ_ERROR_THRESHOLD) {
                    attemptElmRecovery()
                }
                val nowMillis = System.currentTimeMillis()
                val shouldReadDtcs = shouldReadDtcs(nowMillis)
                val storedDtcs = if (shouldReadDtcs) {
                    runCatching { service.readStoredDtcs() }.getOrElse { _obdState.value.storedDtcs }
                } else {
                    _obdState.value.storedDtcs
                }
                val pendingDtcs = if (shouldReadDtcs) {
                    runCatching { service.readPendingDtcs() }.getOrElse { _obdState.value.pendingDtcs }
                } else {
                    _obdState.value.pendingDtcs
                }
                if (shouldReadDtcs) {
                    lastDtcReadMillis = nowMillis
                }
                _obdState.value = _obdState.value.copy(
                    metrics = liveSnapshot,
                    storedDtcs = storedDtcs,
                    pendingDtcs = pendingDtcs,
                    lastUpdatedMillis = if (hasLiveError) {
                        _obdState.value.lastUpdatedMillis
                    } else {
                        System.currentTimeMillis()
                    },
                    liveDataErrorType = liveErrorType,
                    showReconnectButton = consecutivePidReadErrors >= PID_READ_ERROR_THRESHOLD
                )
                delay(LIVE_DATA_REFRESH_MILLIS)
            }
        }
    }

    private fun shouldReadDtcs(nowMillis: Long): Boolean {
        if (dtcRefreshRequested.getAndSet(false)) {
            return true
        }
        return nowMillis - lastDtcReadMillis >= DTC_REFRESH_MILLIS
    }

    private fun updateConnectionState(transform: ConnectionState.() -> ConnectionState) {
        _connectionState.value = _connectionState.value.transform()
    }

    private fun ConnectionState.appendLog(message: String): String {
        return if (log.isBlank()) {
            message
        } else {
            "$log\n$message"
        }
    }

    private suspend fun attemptElmRecovery() {
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastRecoveryAttemptMillis < RECOVERY_COOLDOWN_MILLIS) {
            return
        }
        lastRecoveryAttemptMillis = nowMillis
        val activeSession = session ?: return
        updateConnectionState {
            copy(log = appendLog("Повторная инициализация ELM327 (ATZ/ATSP0)."))
        }
        runCatching { activeSession.execute("ATZ") }
        runCatching { activeSession.execute("ATSP0") }
    }

    private fun mapReadErrorToType(error: Throwable): ObdErrorType? {
        return when (error) {
            is TimeoutCancellationException -> ObdErrorType.TIMEOUT
            is IOException -> ObdErrorType.IO
            else -> null
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MILLIS = 15_000L
        private const val LIVE_DATA_REFRESH_MILLIS = 1_000L
        private const val DTC_REFRESH_MILLIS = 20_000L
        private const val PID_READ_ERROR_THRESHOLD = 3
        private const val RECOVERY_COOLDOWN_MILLIS = 10_000L
        private const val RECENT_DIAGNOSTICS_LIMIT = 200
    }
}

data class ConnectionState(
    val status: Status = Status.Idle,
    val selectedDeviceName: String? = null,
    val selectedDeviceAddress: String? = null,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val scannerType: ScannerType = ScannerType.Bluetooth,
    val wifiHost: String? = null,
    val wifiPort: Int? = null,
    val wifiDetectInProgress: Boolean = false,
    val wifiDetectError: String? = null,
    val wifiResolvedEndpoint: String? = null,
    val wifiAutoDetectResults: List<WifiAutoDetectResult> = emptyList(),
    val log: String = "",
    val supportedMetrics: List<LiveMetricDefinition> = emptyList(),
    val errorMessage: String? = null,
    val permissionStatus: PermissionStatus = PermissionStatus.None
) {
    enum class Status {
        Idle,
        PermissionsRequired,
        SelectingDevice,
        Connecting,
        Initializing,
        Connected,
        Error
    }
}

enum class ScannerType {
    Bluetooth,
    Wifi
}

data class ObdState(
    val metrics: LivePidSnapshot? = null,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val supportedPids: Set<Int> = emptySet(),
    val lastUpdatedMillis: Long? = null,
    val lastCommand: String? = null,
    val lastRawResponse: String? = null,
    val lastErrorType: ObdErrorType? = null,
    val lastPidErrorType: ObdErrorType? = null,
    val lastPidErrorAtMillis: Long? = null,
    val liveDataErrorType: ObdErrorType? = null,
    val showReconnectButton: Boolean = false,
    val recentDiagnostics: List<ObdDiagnostics> = emptyList()
)

enum class PermissionStatus {
    None,
    Requested,
    Denied,
    PermanentlyDenied
}

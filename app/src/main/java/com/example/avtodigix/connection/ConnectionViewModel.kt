package com.example.avtodigix.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.avtodigix.bluetooth.BluetoothTransport
import com.example.avtodigix.bluetooth.BluetoothConnectionManager
import com.example.avtodigix.bluetooth.ConnectionStatus
import com.example.avtodigix.bluetooth.PairedDevice
import com.example.avtodigix.elm.ElmSession
import com.example.avtodigix.obd.DEFAULT_LIVE_METRICS
import com.example.avtodigix.obd.LiveMetricDefinition
import com.example.avtodigix.obd.LivePidSnapshot
import com.example.avtodigix.obd.ObdService
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionViewModel(
    private val connectionManager: BluetoothConnectionManager,
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
    private val dtcRefreshRequested = AtomicBoolean(false)
    private var lastDtcReadMillis = 0L

    init {
        updateConnectionState {
            copy(selectedDeviceAddress = selectedDeviceStore.getSelectedDeviceAddress())
        }
        refreshPairedDevices()
        viewModelScope.launch {
            connectionManager.status.collect { status ->
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
    }

    fun onConnectRequested() {
        updateConnectionState {
            copy(
                status = ConnectionState.Status.PermissionsRequired,
                permissionStatus = PermissionStatus.Requested,
                errorMessage = null,
                log = appendLog("Запрос разрешений Bluetooth.")
            )
        }
    }

    fun onPermissionsResult(granted: Boolean, permanentlyDenied: Boolean) {
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
                connectionManager.disconnect()
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
                connectionManager.disconnect()
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

        connectionManager.connect(selected.address, SPP_UUID)
        val transport = try {
            waitForTransport()
        } catch (error: TimeoutCancellationException) {
            updateConnectionState {
                copy(
                    status = ConnectionState.Status.Error,
                    errorMessage = "Истекло время ожидания подключения."
                )
            }
            return
        }

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

        val service = ObdService(newSession)
        obdService = service
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

    private suspend fun waitForTransport(): BluetoothTransport {
        return withTimeout(CONNECTION_TIMEOUT_MILLIS) {
            connectionManager.transportState.filterNotNull().first { it.socket.isConnected }
        }
    }

    private fun startReading(service: ObdService, supportedPids: Set<Int>?) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            while (isActive) {
                val liveSnapshot = runCatching { service.readLiveData(supportedPids) }.getOrNull()
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
                    metrics = liveSnapshot ?: _obdState.value.metrics,
                    storedDtcs = storedDtcs,
                    pendingDtcs = pendingDtcs,
                    lastUpdatedMillis = System.currentTimeMillis()
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

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MILLIS = 15_000L
        private const val LIVE_DATA_REFRESH_MILLIS = 1_000L
        private const val DTC_REFRESH_MILLIS = 20_000L
    }
}

data class ConnectionState(
    val status: Status = Status.Idle,
    val selectedDeviceName: String? = null,
    val selectedDeviceAddress: String? = null,
    val pairedDevices: List<PairedDevice> = emptyList(),
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

data class ObdState(
    val metrics: LivePidSnapshot? = null,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val supportedPids: Set<Int> = emptySet(),
    val lastUpdatedMillis: Long? = null
)

enum class PermissionStatus {
    None,
    Requested,
    Denied,
    PermanentlyDenied
}

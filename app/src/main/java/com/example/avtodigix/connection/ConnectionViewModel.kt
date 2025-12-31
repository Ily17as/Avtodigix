package com.example.avtodigix.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.avtodigix.bluetooth.BluetoothTransport
import com.example.avtodigix.bluetooth.BluetoothConnectionManager
import com.example.avtodigix.bluetooth.ConnectionStatus
import com.example.avtodigix.bluetooth.PairedDevice
import com.example.avtodigix.elm.ElmSession
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

class ConnectionViewModel(
    private val connectionManager: BluetoothConnectionManager = BluetoothConnectionManager(),
    private val selectedDeviceStore: SelectedDeviceStore
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state

    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var session: ElmSession? = null
    private var obdService: ObdService? = null

    init {
        updateState {
            copy(selectedDeviceAddress = selectedDeviceStore.getSelectedDeviceAddress())
        }
        refreshPairedDevices()
        viewModelScope.launch {
            connectionManager.status.collect { status ->
                when (status) {
                    ConnectionStatus.AdapterNotResponding -> {
                        updateState {
                            copy(
                                phase = ConnectionPhase.AdapterUnavailable,
                                errorMessage = "Bluetooth адаптер недоступен."
                            )
                        }
                    }
                    ConnectionStatus.NoConnection -> {
                        if (state.value.phase == ConnectionPhase.Connected ||
                            state.value.phase == ConnectionPhase.Connecting
                        ) {
                            updateState { copy(phase = ConnectionPhase.Idle) }
                        }
                    }
                    ConnectionStatus.Connected -> {
                        updateState {
                            copy(
                                phase = ConnectionPhase.Connecting,
                                log = appendLog("Bluetooth соединение установлено.")
                            )
                        }
                    }
                }
            }
        }
    }

    fun onConnectRequested() {
        updateState {
            copy(
                phase = ConnectionPhase.PermissionsRequired,
                permissionStatus = PermissionStatus.Requested,
                errorMessage = null,
                log = appendLog("Запрос разрешений Bluetooth.")
            )
        }
    }

    fun onPermissionsResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
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
        updateState { copy(permissionStatus = PermissionStatus.None) }
        refreshPairedDevices()
        if (connectJob?.isActive == true) {
            return
        }
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            connectToDevice()
        }
    }

    fun onPermissionsRetryRequested() {
        updateState {
            copy(
                phase = ConnectionPhase.PermissionsRequired,
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
            session?.close()
            session = null
            obdService = null
            connectionManager.disconnect()
            updateState {
                copy(
                    phase = ConnectionPhase.Idle,
                    permissionStatus = PermissionStatus.None,
                    log = appendLog("Соединение завершено пользователем.")
                )
            }
        }
    }

    fun refreshPairedDevices() {
        viewModelScope.launch {
            val devices = connectionManager.getPairedDevices()
            val selectedAddress = state.value.selectedDeviceAddress
            val selectedDevice = devices.firstOrNull { it.address == selectedAddress }
            updateState {
                copy(
                    pairedDevices = devices,
                    selectedDeviceName = selectedDevice?.name
                )
            }
        }
    }

    fun onPairedDeviceSelected(device: PairedDevice) {
        selectedDeviceStore.setSelectedDeviceAddress(device.address)
        updateState {
            copy(
                selectedDeviceAddress = device.address,
                selectedDeviceName = device.name,
                errorMessage = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        readJob?.cancel()
        viewModelScope.launch {
            session?.close()
            connectionManager.disconnect()
        }
    }

    private suspend fun connectToDevice() {
        val selectedAddress = state.value.selectedDeviceAddress
        if (selectedAddress.isNullOrBlank()) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
                    errorMessage = "Выберите Bluetooth-устройство из списка."
                )
            }
            return
        }
        updateState {
            copy(
                phase = ConnectionPhase.Connecting,
                errorMessage = null,
                log = appendLog("Поиск сопряженных устройств.")
            )
        }
        val devices = connectionManager.getPairedDevices()
        updateState { copy(pairedDevices = devices) }
        val selected = devices.firstOrNull { it.address == selectedAddress }
        if (selected == null) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
                    errorMessage = "Выбранное устройство не найдено в списке сопряженных."
                )
            }
            return
        }
        updateState {
            copy(
                selectedDeviceName = selected.name,
                phase = ConnectionPhase.Connecting,
                log = appendLog("Подключение к ${selected.name}.")
            )
        }

        connectionManager.connect(selected.address, SPP_UUID)
        val transport = try {
            waitForTransport()
        } catch (error: TimeoutCancellationException) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
                    errorMessage = "Истекло время ожидания подключения."
                )
            }
            return
        }

        val newSession = ElmSession(transport.input, transport.output, parentScope = viewModelScope)
        session = newSession
        updateState { copy(log = appendLog("Инициализация ELM327.")) }

        try {
            newSession.initialize()
        } catch (error: IOException) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
                    errorMessage = "Ошибка инициализации адаптера."
                )
            }
            return
        } catch (error: IllegalStateException) {
            updateState {
                copy(
                    phase = ConnectionPhase.Error,
                    errorMessage = "Ошибка при настройке ELM327."
                )
            }
            return
        }

        val service = ObdService(newSession)
        obdService = service
        updateState {
            copy(
                phase = ConnectionPhase.Connected,
                log = appendLog("OBD сервис готов, начинаем чтение данных.")
            )
        }
        startReading(service)
    }

    private suspend fun waitForTransport(): BluetoothTransport {
        return withTimeout(CONNECTION_TIMEOUT_MILLIS) {
            connectionManager.transportState.filterNotNull().first { it.socket.isConnected }
        }
    }

    private fun startReading(service: ObdService) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            while (isActive) {
                val liveSnapshot = runCatching { service.readLiveData() }.getOrNull()
                val storedDtcs = runCatching { service.readStoredDtcs() }.getOrDefault(emptyList())
                val pendingDtcs = runCatching { service.readPendingDtcs() }.getOrDefault(emptyList())
                if (liveSnapshot != null) {
                    updateState {
                        copy(
                            liveSnapshot = liveSnapshot,
                            storedDtcs = storedDtcs,
                            pendingDtcs = pendingDtcs
                        )
                    }
                }
                delay(LIVE_DATA_REFRESH_MILLIS)
            }
        }
    }

    private fun updateState(transform: ConnectionUiState.() -> ConnectionUiState) {
        _state.value = _state.value.transform()
    }

    private fun ConnectionUiState.appendLog(message: String): String {
        return if (log.isBlank()) {
            message
        } else {
            "$log\n$message"
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT_MILLIS = 15_000L
        private const val LIVE_DATA_REFRESH_MILLIS = 2_000L
    }
}

data class ConnectionUiState(
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val selectedDeviceName: String? = null,
    val selectedDeviceAddress: String? = null,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val log: String = "",
    val liveSnapshot: LivePidSnapshot? = null,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val permissionStatus: PermissionStatus = PermissionStatus.None
)

enum class ConnectionPhase {
    Idle,
    PermissionsRequired,
    Connecting,
    Connected,
    AdapterUnavailable,
    Error
}

enum class PermissionStatus {
    None,
    Requested,
    Denied,
    PermanentlyDenied
}

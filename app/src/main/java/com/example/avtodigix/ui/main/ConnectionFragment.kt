package com.example.avtodigix.ui.main

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.PairedDeviceAdapter
import com.example.avtodigix.connection.PermissionStatus
import com.example.avtodigix.connection.ScannerType
import com.example.avtodigix.connection.WifiDeviceAdapter
import com.example.avtodigix.databinding.FragmentConnectionBinding
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment(R.layout.fragment_connection) {
    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!


    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter
    private lateinit var wifiDeviceAdapter: WifiDeviceAdapter

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        val permanentlyDenied = results.any { (permission, isGranted) ->
            !isGranted && !shouldShowRequestPermissionRationale(permission)
        }
        (requireActivity() as MainActivity).connectionViewModel.onPermissionsResult(granted, permanentlyDenied)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentConnectionBinding.bind(view)

        val viewModel = (requireActivity() as MainActivity).connectionViewModel

        pairedDeviceAdapter = PairedDeviceAdapter(viewModel::onPairedDeviceSelected)
        wifiDeviceAdapter = WifiDeviceAdapter(viewModel::onWifiDeviceSelected)

        binding.connectionPairedList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pairedDeviceAdapter
        }
        binding.wifiDiscoveredList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = wifiDeviceAdapter
        }

        binding.connectionModeBluetooth.setOnClickListener {
            (requireActivity() as MainActivity).connectionViewModel.onScannerTypeSelected(ScannerType.Bluetooth)
        }
        binding.connectionModeWifi.setOnClickListener {
            (requireActivity() as MainActivity).connectionViewModel.onScannerTypeSelected(ScannerType.Wifi)
        }
        binding.connectionConnect.setOnClickListener { handleConnectClick() }
        binding.bluetoothEnableButton.setOnClickListener {
            runCatching { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        }
        binding.bluetoothSettingsButton.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        binding.wifiSettingsButton.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity() as MainActivity).connectionViewModel.connectionState.collect(::renderState)
            }
        }
    }

    private fun handleConnectClick() {
        val viewModel = (requireActivity() as MainActivity).connectionViewModel
        val state = viewModel.connectionState.value
        if (state.status == ConnectionState.Status.Connected ||
            state.status == ConnectionState.Status.Connecting ||
            state.status == ConnectionState.Status.Initializing
        ) {
            viewModel.onDisconnectRequested()
        } else {
            viewModel.onConnectRequested()
        }
    }

    private fun renderState(state: ConnectionState) {
        val bluetooth = state.scannerType == ScannerType.Bluetooth
        binding.bluetoothGroup.isVisible = bluetooth
        binding.wifiGroup.isVisible = !bluetooth
        binding.connectionModeBluetooth.isChecked = bluetooth
        binding.connectionModeWifi.isChecked = !bluetooth

        binding.connectionStatusDetail.text = state.errorMessage ?: state.status.name
        binding.connectionLog.text = state.log

        pairedDeviceAdapter.submitList(state.pairedDevices, state.selectedDeviceAddress)
        wifiDeviceAdapter.submitList(state.wifiAutoDetectResults, state.wifiHost, state.wifiPort)
        binding.wifiDiscoveredEmpty.isVisible = state.wifiAutoDetectResults.isEmpty()

        val canDisconnect = state.status == ConnectionState.Status.Connected ||
            state.status == ConnectionState.Status.Connecting ||
            state.status == ConnectionState.Status.Initializing
        binding.connectionConnect.text = if (canDisconnect) {
            getString(R.string.action_disconnect)
        } else {
            getString(R.string.action_connect)
        }

        if (state.permissionStatus == PermissionStatus.Requested) {
            requestBluetoothPermissionsIfNeeded()
        }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        val denied = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) {
            (requireActivity() as MainActivity).connectionViewModel.onPermissionsResult(granted = true, permanentlyDenied = false)
        } else {
            permissionsLauncher.launch(denied.toTypedArray())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

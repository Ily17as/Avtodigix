package com.example.avtodigix.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.databinding.DialogWifiAdvancedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WifiAdvancedSettingsDialogFragment : DialogFragment() {
    private var _binding: DialogWifiAdvancedBinding? = null
    private val binding: DialogWifiAdvancedBinding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogWifiAdvancedBinding.inflate(LayoutInflater.from(requireContext()))

        val initialHost = arguments?.getString(ARG_HOST).orEmpty()
        val initialPort = arguments?.getInt(ARG_PORT)?.takeIf { it > 0 }?.toString().orEmpty()
        binding.wifiAdvancedIpInput.setText(initialHost)
        binding.wifiAdvancedPortInput.setText(initialPort)

        binding.wifiAdvancedIpInput.doAfterTextChanged {
            binding.wifiAdvancedIpLayout.error = null
        }
        binding.wifiAdvancedPortInput.doAfterTextChanged {
            binding.wifiAdvancedPortLayout.error = null
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.connection_wifi_advanced)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        saveIfValid()
                    }
                }
            }
    }

    private fun saveIfValid() {
        val host = binding.wifiAdvancedIpInput.text?.toString()?.trim().orEmpty()
        val portValue = binding.wifiAdvancedPortInput.text?.toString()?.trim().orEmpty().toIntOrNull()

        val ipValid = IPV4_REGEX.matches(host)
        val portValid = portValue != null && portValue in 1..65535

        binding.wifiAdvancedIpLayout.error = if (ipValid) null else getString(R.string.connection_error_ip)
        binding.wifiAdvancedPortLayout.error = if (portValid) null else getString(R.string.connection_error_port)

        if (!ipValid || !portValid) return

        (requireActivity() as MainActivity).connectionViewModel.apply {
            onWifiSettingsSaved(host, portValue!!)
            onConnectRequested()
        }
        dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG: String = "WifiAdvancedSettingsDialog"
        private const val ARG_HOST = "arg_host"
        private const val ARG_PORT = "arg_port"

        private val IPV4_REGEX = Regex(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
        )

        fun newInstance(host: String?, port: Int?): WifiAdvancedSettingsDialogFragment {
            return WifiAdvancedSettingsDialogFragment().apply {
                arguments = bundleOf(
                    ARG_HOST to host,
                    ARG_PORT to port
                )
            }
        }
    }
}

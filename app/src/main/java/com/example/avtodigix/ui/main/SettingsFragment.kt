package com.example.avtodigix.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentSettingsBinding
import com.example.avtodigix.obd.ObdErrorType
import com.example.avtodigix.ui.UiPrefs
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var uiPrefs: UiPrefs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        uiPrefs = UiPrefs(requireContext())

        when (uiPrefs.getUserMode()) {
            UiPrefs.UserMode.Novice -> binding.userModeNovice.isChecked = true
            UiPrefs.UserMode.Professional -> binding.userModeProfessional.isChecked = true
        }
        binding.diagnosticsModeSwitch.isChecked = uiPrefs.isDiagnosticsModeEnabled()
        updateAdvancedBlockVisibility()

        binding.userModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.userModeNovice -> uiPrefs.setUserMode(UiPrefs.UserMode.Novice)
                R.id.userModeProfessional -> uiPrefs.setUserMode(UiPrefs.UserMode.Professional)
            }
            updateAdvancedBlockVisibility()
        }
        binding.diagnosticsModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            uiPrefs.setDiagnosticsModeEnabled(isChecked)
            updateAdvancedBlockVisibility()
        }

        val viewModel = (requireActivity() as MainActivity).connectionViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.obdState.collect { state ->
                    binding.diagnosticsLastCommand.text = getString(
                        R.string.settings_diagnostics_last_command_value,
                        state.lastCommand ?: "—"
                    )
                    binding.diagnosticsRawResponse.text = getString(
                        R.string.settings_diagnostics_raw_response_value,
                        state.lastRawResponse ?: "—"
                    )
                    binding.diagnosticsError.text = getString(
                        R.string.settings_diagnostics_error_value,
                        mapErrorType(state.lastErrorType)
                    )
                }
            }
        }
    }

    private fun updateAdvancedBlockVisibility() {
        val shouldShowAdvanced = uiPrefs.getUserMode() == UiPrefs.UserMode.Professional &&
            uiPrefs.isDiagnosticsModeEnabled()
        binding.advancedDiagnosticsBlock.isVisible = shouldShowAdvanced
    }

    private fun mapErrorType(errorType: ObdErrorType?): String {
        return when (errorType) {
            ObdErrorType.TIMEOUT -> getString(R.string.diagnostics_error_timeout)
            ObdErrorType.NO_DATA -> getString(R.string.diagnostics_error_no_data)
            ObdErrorType.UNABLE_TO_CONNECT -> getString(R.string.diagnostics_error_unable_connect)
            ObdErrorType.SOCKET_CLOSED -> getString(R.string.diagnostics_error_socket_closed)
            ObdErrorType.NEGATIVE_RESPONSE -> getString(R.string.diagnostics_error_negative)
            ObdErrorType.IO -> getString(R.string.diagnostics_error_io)
            null -> getString(R.string.diagnostics_error_none)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

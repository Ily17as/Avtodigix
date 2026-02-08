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
import com.example.avtodigix.databinding.FragmentIssuesHistoryBinding
import com.example.avtodigix.ui.UiPrefs
import kotlinx.coroutines.launch

class IssuesHistoryFragment : Fragment(R.layout.fragment_issues_history) {
    private var _binding: FragmentIssuesHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentIssuesHistoryBinding.bind(view)
        val viewModel = (requireActivity() as MainActivity).connectionViewModel

        binding.refreshDtcButton.setOnClickListener {
            viewModel.requestDtcRefresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.obdState.collect { state ->
                    val allDtcs = (state.storedDtcs + state.pendingDtcs).distinct()
                    binding.storedDtcValue.text = if (allDtcs.isEmpty()) {
                        getString(R.string.dtc_none)
                    } else {
                        allDtcs.joinToString("\n")
                    }

                    val uiPrefs = UiPrefs(requireContext())
                    val showRawComponents = uiPrefs.getUserMode() == UiPrefs.UserMode.Professional &&
                        uiPrefs.isDiagnosticsModeEnabled()
                    binding.historyValue.isVisible = showRawComponents
                    if (showRawComponents) {
                        binding.historyValue.text = state.recentDiagnostics.takeLast(5)
                            .joinToString("\n") { "${it.command}: ${it.rawResponse}" }
                            .ifBlank { getString(R.string.history_empty) }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

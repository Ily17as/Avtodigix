package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.DailyCheckSessionState
import com.example.avtodigix.connection.DailyCheckStage
import com.example.avtodigix.databinding.FragmentDailyCheckProgressBinding
import kotlinx.coroutines.launch

class DailyCheckProgressFragment : Fragment(R.layout.fragment_daily_check_progress) {
    private var _binding: FragmentDailyCheckProgressBinding? = null
    private val binding get() = _binding!!
    private var hasNavigatedToResult = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckProgressBinding.bind(view)

        binding.dailyCheckConnectionRetryButton.setOnClickListener {
            (requireActivity() as MainActivity).connectionViewModel.onConnectRequested()
        }
        binding.dailyCheckOpenLastResultButton.setOnClickListener {
            findNavController().navigate(R.id.action_dailyCheckProgressFragment_to_dailyCheckResultFragment)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity() as MainActivity)
                    .connectionViewModel
                    .dailyCheckSessionState
                    .collect(::renderState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity() as MainActivity)
                    .connectionViewModel
                    .connectionState
                    .collect(::renderConnectionState)
            }
        }
    }

    private fun renderState(state: DailyCheckSessionState) {
        binding.dailyCheckProgressElapsed.text = getString(
            R.string.daily_check_progress_elapsed,
            state.elapsedSeconds
        )

        renderStep(
            textView = binding.dailyCheckProgressStepConnect,
            isCompleted = state.stage == DailyCheckStage.ReadingParameters ||
                state.stage == DailyCheckStage.AnalyzingState ||
                state.stage == DailyCheckStage.Completed,
            isCurrent = state.stage == DailyCheckStage.ConnectingAdapter
        )
        renderStep(
            textView = binding.dailyCheckProgressStepRead,
            isCompleted = state.stage == DailyCheckStage.AnalyzingState ||
                state.stage == DailyCheckStage.Completed,
            isCurrent = state.stage == DailyCheckStage.ReadingParameters
        )
        renderStep(
            textView = binding.dailyCheckProgressStepAnalyze,
            isCompleted = state.stage == DailyCheckStage.Completed,
            isCurrent = state.stage == DailyCheckStage.AnalyzingState
        )

        binding.dailyCheckProgressIndicator.isIndeterminate = state.isActive

        if (state.isCompleted && !hasNavigatedToResult) {
            hasNavigatedToResult = true
            findNavController().navigate(R.id.action_dailyCheckProgressFragment_to_dailyCheckResultFragment)
        }
    }

    private fun renderConnectionState(state: ConnectionState) {
        val showConnectionError = state.status == ConnectionState.Status.Error && !hasNavigatedToResult
        binding.dailyCheckConnectionErrorCard.isVisible = showConnectionError
    }

    private fun renderStep(textView: android.widget.TextView, isCompleted: Boolean, isCurrent: Boolean) {
        val prefixRes = when {
            isCompleted -> R.string.daily_check_progress_step_done
            isCurrent -> R.string.daily_check_progress_step_active
            else -> R.string.daily_check_progress_step_pending
        }
        val defaultText = textView.tag?.toString().orEmpty()
        textView.text = getString(prefixRes, defaultText)

        val color = when {
            isCompleted -> com.google.android.material.R.attr.colorPrimary
            isCurrent -> com.google.android.material.R.attr.colorOnSurface
            else -> com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        val resolved = com.google.android.material.color.MaterialColors.getColor(textView, color)
        textView.setTextColor(resolved)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentDailyCheckCardDetailsBinding

class DailyCheckCardDetailsFragment : Fragment(R.layout.fragment_daily_check_card_details) {
    private var _binding: FragmentDailyCheckCardDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckCardDetailsBinding.bind(view)

        val systemType = requireArguments().getString(ARG_SYSTEM_TYPE).orEmpty()
        val status = requireArguments().getString(ARG_STATUS).orEmpty()
        val metrics = requireArguments().getString(ARG_METRICS).orEmpty()
        val baselineComparison = requireArguments().getString(ARG_BASELINE_COMPARISON).orEmpty()
        val showBaselineComparison = requireArguments().getBoolean(ARG_SHOW_BASELINE_COMPARISON, true)
        val recommendation = requireArguments().getString(ARG_RECOMMENDATION).orEmpty()

        binding.dailyCheckDetailsTitle.text = getString(R.string.daily_check_details_title_format, systemType)
        binding.dailyCheckDetailsStatusMeaning.text = getString(
            R.string.daily_check_details_status_meaning_format,
            status
        )
        binding.dailyCheckDetailsMetrics.text = getString(
            R.string.daily_check_details_metrics_format,
            metrics
        )
        binding.dailyCheckDetailsBaselineCard.isVisible = showBaselineComparison
        if (showBaselineComparison) {
            binding.dailyCheckDetailsBaseline.text = getString(
                R.string.daily_check_details_baseline_format,
                baselineComparison
            )
        }
        binding.dailyCheckDetailsNextActions.text = getString(
            R.string.daily_check_details_next_actions_format,
            recommendation
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SYSTEM_TYPE = "systemType"
        const val ARG_STATUS = "status"
        const val ARG_METRICS = "metrics"
        const val ARG_BASELINE_COMPARISON = "baselineComparison"
        const val ARG_SHOW_BASELINE_COMPARISON = "showBaselineComparison"
        const val ARG_RECOMMENDATION = "recommendation"

        fun buildArgs(
            systemType: String,
            status: String,
            metrics: String,
            baselineComparison: String,
            showBaselineComparison: Boolean,
            recommendation: String
        ): Bundle = bundleOf(
            ARG_SYSTEM_TYPE to systemType,
            ARG_STATUS to status,
            ARG_METRICS to metrics,
            ARG_BASELINE_COMPARISON to baselineComparison,
            ARG_SHOW_BASELINE_COMPARISON to showBaselineComparison,
            ARG_RECOMMENDATION to recommendation
        )
    }
}

package com.example.avtodigix.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.avtodigix.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class RecommendationsBottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.connection_troubleshooting_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val scenarioOrdinal = requireArguments().getInt(ARG_SCENARIO)
        val scenario = RecommendationsProvider.Scenario.entries[scenarioOrdinal]
        val content = RecommendationsProvider.getContent(requireContext(), scenario)

        view.findViewById<MaterialTextView>(R.id.connectionTroubleshootTitle).text = content.title
        view.findViewById<MaterialTextView>(R.id.connectionTroubleshootBody).text = content.items
            .joinToString("\n") { "• $it" }
        view.findViewById<MaterialButton>(R.id.connectionTroubleshootClose).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "RecommendationsBottomSheet"
        private const val ARG_SCENARIO = "scenario"

        fun forConnection() = RecommendationsBottomSheetDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_SCENARIO, RecommendationsProvider.Scenario.CONNECTION.ordinal)
            }
        }

        fun forDiagnostics() = RecommendationsBottomSheetDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_SCENARIO, RecommendationsProvider.Scenario.DIAGNOSTICS.ordinal)
            }
        }
    }
}

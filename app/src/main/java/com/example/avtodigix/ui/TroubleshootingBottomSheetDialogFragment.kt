package com.example.avtodigix.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.avtodigix.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class TroubleshootingBottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.connection_troubleshooting_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val content = RecommendationsProvider.getContent(
            requireContext(),
            RecommendationsProvider.Scenario.CONNECTION
        )
        view.findViewById<MaterialTextView>(R.id.connectionTroubleshootTitle).text = content.title
        view.findViewById<MaterialTextView>(R.id.connectionTroubleshootBody).text =
            content.items.joinToString("\n") { "• $it" }
        view.findViewById<MaterialTextView>(R.id.connectionTroubleshootInstructionLink).apply {
            movementMethod = LinkMovementMethod.getInstance()
        }
        view.findViewById<MaterialButton>(R.id.connectionTroubleshootClose).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "ConnectionTroubleshoot"
    }
}

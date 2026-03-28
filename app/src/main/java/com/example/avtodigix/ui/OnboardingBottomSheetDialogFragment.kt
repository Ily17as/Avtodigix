package com.example.avtodigix.ui

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import com.example.avtodigix.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class OnboardingBottomSheetDialogFragment : BottomSheetDialogFragment() {
    var onDismissed: ((Boolean) -> Unit)? = null
    private var dontShowAgainCheckBox: CheckBox? = null
    private var wasConfirmed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dontShowAgainCheckBox = view.findViewById(R.id.onboardingDontShowAgain)
        view.findViewById<View>(R.id.onboardingInstructionLink).setOnClickListener {
            openInstructionUrl()
        }
        view.findViewById<MaterialButton>(R.id.onboardingAction).setOnClickListener {
            val dontShowAgain = dontShowAgainCheckBox?.isChecked == true
            UiPrefs(requireContext()).setOnboardingShown(dontShowAgain)
            wasConfirmed = true
            dismiss()
        }
    }


    private fun openInstructionUrl() {
        if (context == null) return
        val instructionUri = Uri.parse(getString(R.string.onboarding_instruction_url))
        val browserIntent = Intent(Intent.ACTION_VIEW, instructionUri)
        try {
            startActivity(browserIntent)
        } catch (_: ActivityNotFoundException) {
            // No browser available on device.
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (wasConfirmed) {
            onDismissed?.invoke(dontShowAgainCheckBox?.isChecked == true)
        }
        wasConfirmed = false
        dontShowAgainCheckBox = null
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "OnboardingBottomSheet"
    }
}

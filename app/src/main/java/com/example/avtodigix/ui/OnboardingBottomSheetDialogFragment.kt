package com.example.avtodigix.ui

import android.content.DialogInterface
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
        view.findViewById<MaterialButton>(R.id.onboardingAction).setOnClickListener {
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDismissed?.invoke(dontShowAgainCheckBox?.isChecked == true)
        dontShowAgainCheckBox = null
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "OnboardingBottomSheet"
    }
}

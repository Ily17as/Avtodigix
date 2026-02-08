package com.example.avtodigix.ui

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import androidx.core.os.bundleOf
import com.example.avtodigix.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup

class FeedbackBottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.feedback_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ratingBar = view.findViewById<RatingBar>(R.id.feedbackRatingBar)
        val tagsGroup = view.findViewById<ChipGroup>(R.id.feedbackTagsGroup)
        val commentInput = view.findViewById<EditText>(R.id.feedbackCommentInput)

        ratingBar.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_UP
            ) {
                val width = ratingBar.width.takeIf { it > 0 } ?: return@setOnTouchListener false
                val clampedX = event.x.coerceIn(0f, width.toFloat())
                val tappedStar = ((clampedX / width) * ratingBar.numStars).toInt() + 1
                val normalizedRating = tappedStar.coerceIn(1, ratingBar.numStars)
                ratingBar.rating = normalizedRating.toFloat()
            }
            false
        }

        commentInput.filters = arrayOf(InputFilter.LengthFilter(MAX_COMMENT_LENGTH))

        view.findViewById<MaterialButton>(R.id.feedbackSendButton).setOnClickListener {
            val selectedTags = tagsGroup.checkedChipIds.mapNotNull { chipId ->
                view.findViewById<View>(chipId)?.tag?.toString()
            }
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_RATING to ratingBar.rating.toInt().coerceIn(1, 5),
                    RESULT_TAGS to selectedTags.toTypedArray(),
                    RESULT_COMMENT to commentInput.text?.toString()?.trim().orEmpty()
                )
            )
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.feedbackLaterButton).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "FeedbackBottomSheet"
        const val REQUEST_KEY = "feedback_request"
        const val RESULT_RATING = "feedback_rating"
        const val RESULT_TAGS = "feedback_tags"
        const val RESULT_COMMENT = "feedback_comment"
        private const val MAX_COMMENT_LENGTH = 500
    }
}

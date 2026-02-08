package com.example.avtodigix.ui

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.RatingBar
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.actionWithAssertions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackBottomSheetDialogFragmentTest {

    @Test
    fun tapEachStar_setsExpectedIntegerRating() {
        launchActivity<MainActivity>().use {
            onView(isAssignableFrom(com.google.android.material.floatingactionbutton.FloatingActionButton::class.java))
                .perform(actionWithAssertions(androidx.test.espresso.action.ViewActions.click()))

            for (star in 1..5) {
                onView(isAssignableFrom(RatingBar::class.java))
                    .perform(actionWithAssertions(tapRatingBarStar(star, totalStars = 5)))

                onView(isAssignableFrom(RatingBar::class.java))
                    .perform(actionWithAssertions(assertRating(star.toFloat())))
            }
        }
    }

    private fun tapRatingBarStar(star: Int, totalStars: Int): ViewAction {
        return GeneralClickAction(
            Tap.SINGLE,
            { view ->
                val x = view.width * ((star - 0.5f) / totalStars)
                val y = view.height / 2f
                floatArrayOf(x, y)
            },
            Press.FINGER,
            InputDevice.SOURCE_TOUCHSCREEN,
            MotionEvent.BUTTON_PRIMARY
        )
    }

    private fun assertRating(expectedRating: Float): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RatingBar::class.java)

            override fun getDescription(): String = "assert rating equals $expectedRating"

            override fun perform(
                uiController: androidx.test.espresso.UiController,
                view: View
            ) {
                val ratingBar = view as RatingBar
                assertEquals(expectedRating, ratingBar.rating)
            }
        }
    }
}

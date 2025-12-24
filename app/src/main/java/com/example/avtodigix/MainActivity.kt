package com.example.avtodigix

import android.os.Bundle
import android.widget.Button
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val flipper = findViewById<ViewFlipper>(R.id.screenFlipper)

        bindNavigation(R.id.welcomeNext, flipper, 1)
        bindNavigation(R.id.connectionBack, flipper, 0)
        bindNavigation(R.id.connectionNext, flipper, 2)
        bindNavigation(R.id.summaryBack, flipper, 1)
        bindNavigation(R.id.summaryNext, flipper, 3)
        bindNavigation(R.id.metricsBack, flipper, 2)
        bindNavigation(R.id.metricsNext, flipper, 4)
        bindNavigation(R.id.dtcBack, flipper, 3)
        bindNavigation(R.id.dtcFinish, flipper, 0)
    }

    private fun bindNavigation(buttonId: Int, flipper: ViewFlipper, targetIndex: Int) {
        findViewById<Button>(buttonId).setOnClickListener {
            flipper.displayedChild = targetIndex
        }
    }
}

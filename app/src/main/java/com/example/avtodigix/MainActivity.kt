package com.example.avtodigix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.avtodigix.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val flipper = binding.screenFlipper

        bindNavigation(binding.welcomeNext, flipper, 1)
        bindNavigation(binding.connectionBack, flipper, 0)
        bindNavigation(binding.connectionNext, flipper, 2)
        bindNavigation(binding.summaryBack, flipper, 1)
        bindNavigation(binding.summaryNext, flipper, 3)
        bindNavigation(binding.metricsBack, flipper, 2)
        bindNavigation(binding.metricsNext, flipper, 4)
        bindNavigation(binding.dtcBack, flipper, 3)
        bindNavigation(binding.dtcFinish, flipper, 0)
    }

    private fun bindNavigation(
        button: android.widget.Button,
        flipper: android.widget.ViewFlipper,
        targetIndex: Int
    ) {
        button.setOnClickListener { flipper.displayedChild = targetIndex }
    }
}

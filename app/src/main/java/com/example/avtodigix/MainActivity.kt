package com.example.avtodigix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.avtodigix.connection.ConnectionViewModel
import com.example.avtodigix.connection.ConnectionViewModelFactory
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    val connectionViewModel: ConnectionViewModel by lazy {
        ViewModelProvider(
            this,
            ConnectionViewModelFactory(applicationContext, SelectedDeviceStore(applicationContext))
        )[ConnectionViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionViewModel

        val navController = findNavController(R.id.mainNavHost)
        binding.bottomNavigation.setupWithNavController(navController)

        binding.feedbackFab.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@avtodigix.example")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            }
            runCatching { startActivity(intent) }
        }
    }
}

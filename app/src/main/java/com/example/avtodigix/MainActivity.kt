package com.example.avtodigix

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.ConnectionViewModel
import com.example.avtodigix.connection.ConnectionViewModelFactory
import com.example.avtodigix.connection.ObdState
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.databinding.ActivityMainBinding
import com.example.avtodigix.feedback.FeedbackLocalStore
import com.example.avtodigix.feedback.FeedbackManager
import com.example.avtodigix.feedback.FeedbackPayload
import com.example.avtodigix.feedback.FeedbackPrefs
import com.example.avtodigix.ui.FeedbackBottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var feedbackLocalStore: FeedbackLocalStore
    private var latestConnectionState = ConnectionState()
    private var latestObdState = ObdState()

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

        feedbackManager = FeedbackManager(FeedbackPrefs(applicationContext))
        feedbackLocalStore = FeedbackLocalStore(applicationContext)

        connectionViewModel

        val navController = findNavController(R.id.mainNavHost)
        binding.bottomNavigation.setupWithNavController(navController)

        binding.feedbackFab.setOnClickListener {
            showFeedbackSheet()
        }

        supportFragmentManager.setFragmentResultListener(
            FeedbackBottomSheetDialogFragment.REQUEST_KEY,
            this
        ) { _, result ->
            val rating = result.getInt(FeedbackBottomSheetDialogFragment.RESULT_RATING, 5)
            val tags = result.getStringArray(FeedbackBottomSheetDialogFragment.RESULT_TAGS)?.toList().orEmpty()
            val comment = result.getString(FeedbackBottomSheetDialogFragment.RESULT_COMMENT).orEmpty()

            val payload = FeedbackPayload(
                rating = rating,
                tags = tags,
                comment = comment,
                appVersion = BuildConfig.VERSION_NAME,
                deviceModel = Build.MODEL.orEmpty(),
                androidVersion = Build.VERSION.RELEASE.orEmpty(),
                connectionType = latestConnectionState.scannerType.name.lowercase(),
                lastSessionResult = latestConnectionState.status.name.lowercase(),
                dtcCount = latestObdState.dtcCountReported ?: latestObdState.storedDtcs.size,
                submittedAt = System.currentTimeMillis()
            )
            feedbackLocalStore.save(payload)
            feedbackManager.markSubmitted(payload.submittedAt)
            Log.i(TAG, "Feedback saved locally (backend unavailable): $payload")

            Snackbar.make(binding.root, getString(R.string.feedback_thanks), Snackbar.LENGTH_LONG)
                .setAction(R.string.feedback_write_more) {
                    showFeedbackSheet()
                }
                .show()
        }

        observeFeedbackTriggers()
    }

    private fun observeFeedbackTriggers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.connectionState.collect { state ->
                        val previousStatus = latestConnectionState.status
                        latestConnectionState = state
                        if (previousStatus != ConnectionState.Status.Connected &&
                            state.status == ConnectionState.Status.Connected
                        ) {
                            feedbackManager.recordSuccessfulConnection()
                            maybePromptFeedback()
                        }
                    }
                }
                launch {
                    connectionViewModel.obdState.collect { state ->
                        val fullScanFinished = latestObdState.fullScanInProgress && !state.fullScanInProgress
                        val hasScanResults = state.fullScanResults.isNotEmpty()
                        latestObdState = state
                        if (fullScanFinished && hasScanResults) {
                            feedbackManager.recordFirstFullScan()
                            maybePromptFeedback()
                        }
                    }
                }
            }
        }
    }

    private fun maybePromptFeedback() {
        if (!feedbackManager.shouldShowPrompt()) {
            return
        }
        feedbackManager.markPromptShown()
        showFeedbackSheet()
    }

    private fun showFeedbackSheet() {
        if (supportFragmentManager.findFragmentByTag(FeedbackBottomSheetDialogFragment.TAG) != null) {
            return
        }
        FeedbackBottomSheetDialogFragment().show(
            supportFragmentManager,
            FeedbackBottomSheetDialogFragment.TAG
        )
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}

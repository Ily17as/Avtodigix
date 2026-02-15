package com.example.avtodigix

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.MarginLayoutParamsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.avtodigix.connection.ConnectionState
import com.example.avtodigix.connection.ConnectionViewModel
import com.example.avtodigix.connection.ConnectionViewModelFactory
import com.example.avtodigix.connection.ObdState
import com.example.avtodigix.connection.SelectedDeviceStore
import com.example.avtodigix.databinding.ActivityMainBinding
import com.example.avtodigix.feedback.FeedbackManager
import com.example.avtodigix.feedback.FeedbackPayload
import com.example.avtodigix.feedback.FeedbackPrefs
import com.example.avtodigix.feedback.FeedbackSubmissionState
import com.example.avtodigix.feedback.HttpFeedbackSender
import com.example.avtodigix.ui.FeedbackBottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var feedbackManager: FeedbackManager

    private var feedbackSubmissionState: FeedbackSubmissionState = FeedbackSubmissionState.Idle
    private var latestConnectionState = ConnectionState()
    private var latestObdState = ObdState()
    private var hasConnectedSuccessfullyInSession = false
    private var currentDestinationId: Int? = null
    private var sawIssuesScreenWithUsefulData = false

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
        connectionViewModel

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.mainNavHost) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
        observeErrorScreenJourney(navController)

        binding.feedbackFab.setOnClickListener {
            showFeedbackSheet()
        }
        setupInsetsHandling()

        supportFragmentManager.setFragmentResultListener(
            FeedbackBottomSheetDialogFragment.REQUEST_KEY,
            this
        ) { _, result ->
            val rating = result.getInt(FeedbackBottomSheetDialogFragment.RESULT_RATING, 0)
            val tags = result.getStringArray(FeedbackBottomSheetDialogFragment.RESULT_TAGS)?.toList().orEmpty()
            val comment = result.getString(FeedbackBottomSheetDialogFragment.RESULT_COMMENT).orEmpty()
            val normalizedComment = if (comment.length > FeedbackBottomSheetDialogFragment.MAX_COMMENT_LENGTH) {
                Toast.makeText(
                    this,
                    getString(
                        R.string.feedback_comment_trimmed,
                        FeedbackBottomSheetDialogFragment.MAX_COMMENT_LENGTH
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                comment.take(FeedbackBottomSheetDialogFragment.MAX_COMMENT_LENGTH)
            } else {
                comment
            }
            if (rating !in VALID_RATING_RANGE) {
                Log.w(TAG, "Ignoring feedback result with invalid rating: $rating")
                return@setFragmentResultListener
            }
            feedbackManager.markFeedbackSubmitted()
            val submittedAtMillis = System.currentTimeMillis()
            val payload = FeedbackPayload(
                rating = rating,
                tags = tags,
                comment = normalizedComment,
                appVersion = BuildConfig.VERSION_NAME,
                buildNumber = BuildConfig.VERSION_CODE,
                platform = "android",
                deviceModel = Build.MODEL?.takeIf { it.isNotBlank() },
                connectionType = latestConnectionState.scannerType.name.lowercase(),
                lastSessionResult = latestConnectionState.status.name.lowercase(),
                dtcCount = latestObdState.dtcCountReported ?: latestObdState.storedDtcs.size,
                submittedAtUtc = Instant.ofEpochMilli(submittedAtMillis).toString(),
                submittedAtMillis = submittedAtMillis
            )
            val feedbackFormUrl = HttpFeedbackSender.buildRedirectUrl(payload)
            val feedbackFormUri = validateFeedbackFormUri(feedbackFormUrl)
            if (feedbackFormUri == null) {
                Log.e(TAG, "Invalid feedback form URL: $feedbackFormUrl")
                feedbackSubmissionState = FeedbackSubmissionState.BrowserUnavailable
                renderFeedbackSubmissionState()
                return@setFragmentResultListener
            }
            openFeedbackFormPrefilled(feedbackFormUri, payload.submittedAtMillis)
        }

        maybePromptFeedbackOnSecondLaunch()
        observeFeedbackTriggers()
    }

    private fun validateFeedbackFormUri(url: String): Uri? {
        if (url.isBlank()) {
            return null
        }
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        return if ((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()) {
            uri
        } else {
            null
        }
    }

    private fun renderFeedbackSubmissionState() {
        when (feedbackSubmissionState) {
            FeedbackSubmissionState.Idle -> Unit
            FeedbackSubmissionState.FormOpened -> {
                Snackbar.make(binding.root, getString(R.string.feedback_form_opened), Snackbar.LENGTH_LONG).show()
            }

            FeedbackSubmissionState.BrowserUnavailable -> {
                Snackbar.make(binding.root, getString(R.string.feedback_browser_unavailable), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun openFeedbackFormPrefilled(uri: Uri, openedAtMillis: Long) {
        val customTabsPackage = CustomTabsClient.getPackageName(this, null)

        val isOpened = if (customTabsPackage != null) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build().apply {
                    intent.setPackage(customTabsPackage)
                }
                customTabsIntent.launchUrl(this, uri)
                true
            } catch (exception: ActivityNotFoundException) {
                false
            }
        } else {
            false
        }

        if (isOpened || openFeedbackFormWithActionView(uri)) {
            feedbackManager.markFormOpened(openedAtMillis)
            feedbackSubmissionState = FeedbackSubmissionState.FormOpened
        } else {
            feedbackSubmissionState = FeedbackSubmissionState.BrowserUnavailable
        }

        renderFeedbackSubmissionState()
    }

    private fun openFeedbackFormWithActionView(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (fallbackException: ActivityNotFoundException) {
            Log.e(TAG, "No browser found for feedback form", fallbackException)
            false
        }
    }

    private fun setupInsetsHandling() {
        val bottomNavigationLayoutParams = binding.bottomNavigation.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val feedbackFabLayoutParams = binding.feedbackFab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val baseBottomNavigationBottomMargin = bottomNavigationLayoutParams.bottomMargin
        val baseBottomNavigationMarginStart = MarginLayoutParamsCompat.getMarginStart(bottomNavigationLayoutParams)
        val baseBottomNavigationMarginEnd = MarginLayoutParamsCompat.getMarginEnd(bottomNavigationLayoutParams)
        val baseFeedbackFabBottomMargin = feedbackFabLayoutParams.bottomMargin
        val baseFeedbackFabMarginEnd = MarginLayoutParamsCompat.getMarginEnd(feedbackFabLayoutParams)
        val fabBottomNavigationClearance = resources.getDimensionPixelSize(R.dimen.feedback_fab_clearance_from_bottom_nav)

        var systemBottomInset = 0

        val updateFabPosition = {
            val bottomNavigationHeight = binding.bottomNavigation.height
            val requiredBottomOffset = if (bottomNavigationHeight > 0) {
                systemBottomInset + bottomNavigationHeight + fabBottomNavigationClearance
            } else {
                0
            }
            feedbackFabLayoutParams.bottomMargin = maxOf(
                baseFeedbackFabBottomMargin + systemBottomInset,
                requiredBottomOffset
            )
            MarginLayoutParamsCompat.setMarginEnd(
                feedbackFabLayoutParams,
                baseFeedbackFabMarginEnd
            )
            binding.feedbackFab.layoutParams = feedbackFabLayoutParams
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarsInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            systemBottomInset = systemBarsInsets.bottom

            MarginLayoutParamsCompat.setMarginStart(
                bottomNavigationLayoutParams,
                baseBottomNavigationMarginStart + systemBarsInsets.left
            )
            MarginLayoutParamsCompat.setMarginEnd(
                bottomNavigationLayoutParams,
                baseBottomNavigationMarginEnd + systemBarsInsets.right
            )
            bottomNavigationLayoutParams.bottomMargin = baseBottomNavigationBottomMargin + systemBottomInset
            binding.bottomNavigation.layoutParams = bottomNavigationLayoutParams

            updateFabPosition()
            insets
        }

        binding.bottomNavigation.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateFabPosition()
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeFeedbackTriggers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.connectionState.collect { state ->
                        latestConnectionState = state
                        if (state.status == ConnectionState.Status.Connected) {
                            hasConnectedSuccessfullyInSession = true
                        }
                    }
                }
                launch {
                    connectionViewModel.obdState.collect { state ->
                        val fullScanFinished = latestObdState.fullScanInProgress && !state.fullScanInProgress
                        val hasUsefulScanResults = hasUsefulScanResults(state)
                        latestObdState = state
                        if (currentDestinationId == R.id.issuesHistoryFragment &&
                            hasConnectedSuccessfullyInSession &&
                            hasNonZeroDiagnosticData(state)
                        ) {
                            sawIssuesScreenWithUsefulData = true
                        }
                        if (fullScanFinished && hasUsefulScanResults) {
                            feedbackManager.recordFirstFullScan()
                            maybePromptFeedback()
                        }
                    }
                }
            }
        }
    }

    private fun observeErrorScreenJourney(navController: NavController) {
        currentDestinationId = navController.currentDestination?.id
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val previousDestinationId = currentDestinationId
            currentDestinationId = destination.id

            if (destination.id == R.id.issuesHistoryFragment &&
                hasConnectedSuccessfullyInSession &&
                hasNonZeroDiagnosticData(latestObdState)
            ) {
                sawIssuesScreenWithUsefulData = true
            }

            if (previousDestinationId == R.id.issuesHistoryFragment &&
                destination.id != R.id.issuesHistoryFragment &&
                sawIssuesScreenWithUsefulData &&
                hasConnectedSuccessfullyInSession &&
                hasNonZeroDiagnosticData(latestObdState)
            ) {
                maybePromptFeedbackForExternalTrigger()
                sawIssuesScreenWithUsefulData = false
            }
        }
    }

    private fun maybePromptFeedbackForExternalTrigger() {
        if (!feedbackManager.shouldShowPromptForExternalTrigger()) {
            return
        }
        feedbackManager.markPromptShown()
        showFeedbackSheet()
    }

    private fun maybePromptFeedback() {
        if (!feedbackManager.shouldShowPrompt()) {
            return
        }
        feedbackManager.markPromptShown()
        showFeedbackSheet()
    }

    private fun hasUsefulScanResults(state: ObdState): Boolean {
        return state.fullScanResults.any { record ->
            val hasNonZeroBytes = record.bytes?.any { it != 0 } == true
            val hasNonZeroDecodedValue = record.decodedValue
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?.let { kotlin.math.abs(it) > 0.0 } == true
            hasNonZeroBytes || hasNonZeroDecodedValue
        }
    }

    private fun hasNonZeroDiagnosticData(state: ObdState): Boolean {
        val hasReportedDtcs = (state.dtcCountReported ?: 0) > 0 ||
            state.storedDtcs.isNotEmpty() ||
            state.pendingDtcs.isNotEmpty() ||
            state.milOn == true
        return hasReportedDtcs || hasUsefulScanResults(state)
    }

    private fun maybePromptFeedbackOnSecondLaunch() {
        if (feedbackManager.shouldShowPromptOnSecondAppLaunch()) {
            showFeedbackSheet()
        }
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
        private val VALID_RATING_RANGE = 1..5
    }
}

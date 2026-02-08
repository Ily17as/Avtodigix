package com.example.avtodigix.feedback

import android.util.Log
import java.io.IOException

class FeedbackRepository(
    private val sender: FeedbackSender,
    private val localStore: FeedbackLocalStore,
    private val connectivityChecker: ConnectivityChecker
) {
    suspend fun submit(payload: FeedbackPayload): FeedbackSubmissionState {
        if (!connectivityChecker.isOnline()) {
            localStore.save(payload)
            Log.w(TAG, "Feedback submit skipped: no network")
            return FeedbackSubmissionState.BrowserUnavailable
        }

        return try {
            val result = sender.send(payload)
            if (result.success) {
                FeedbackSubmissionState.FormOpened
            } else {
                localStore.save(payload)
                Log.w(TAG, "Feedback submit failed: code=${result.responseCode}")
                FeedbackSubmissionState.BrowserUnavailable
            }
        } catch (ioException: IOException) {
            localStore.save(payload)
            Log.w(TAG, "Feedback submit I/O failure", ioException)
            FeedbackSubmissionState.BrowserUnavailable
        } catch (exception: Exception) {
            localStore.save(payload)
            Log.e(TAG, "Feedback submit failed unexpectedly", exception)
            FeedbackSubmissionState.BrowserUnavailable
        }
    }

    private companion object {
        private const val TAG = "FeedbackRepository"
    }
}

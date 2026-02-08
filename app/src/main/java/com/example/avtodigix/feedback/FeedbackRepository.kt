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
            return FeedbackSubmissionState.Error(isOffline = true)
        }

        return try {
            val result = sender.send(payload)
            if (result.success) {
                FeedbackSubmissionState.Success
            } else {
                localStore.save(payload)
                Log.w(TAG, "Feedback submit failed: code=${result.responseCode}")
                FeedbackSubmissionState.Error(isOffline = false)
            }
        } catch (ioException: IOException) {
            localStore.save(payload)
            Log.w(TAG, "Feedback submit I/O failure", ioException)
            FeedbackSubmissionState.Error(isOffline = true)
        } catch (exception: Exception) {
            localStore.save(payload)
            Log.e(TAG, "Feedback submit failed unexpectedly", exception)
            FeedbackSubmissionState.Error(isOffline = false)
        }
    }

    private companion object {
        private const val TAG = "FeedbackRepository"
    }
}

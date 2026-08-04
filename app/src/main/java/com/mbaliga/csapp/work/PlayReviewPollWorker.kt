package com.mbaliga.csapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mbaliga.csapp.CsAppApplication

/** Periodic background poll of the configured Play package's recent reviews, then re-clusters. */
class PlayReviewPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CsAppApplication).container
        val packageName = container.settingsStore.playPackageName
        if (packageName.isNullOrBlank() || !container.credentialStore.hasPlayServiceAccountKey()) {
            return Result.success() // Not configured yet; nothing to do.
        }
        return try {
            container.playReviewRepository.pollRecentReviews(packageName)
            container.incidentRepository.runClustering()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "play_review_poll"
    }
}

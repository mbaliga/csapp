package com.mbaliga.csapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PollScheduler {
    val INTERVAL_CHOICES_MINUTES = listOf(15, 30, 60)

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** (Re)schedules the single periodic sync. [replace] applies a changed interval. */
    fun schedule(context: Context, intervalMinutes: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, policy, request)
        // Retire the pre-multi-project workers if an old install still has them queued.
        WorkManager.getInstance(context).cancelUniqueWork("github_issue_poll")
        WorkManager.getInstance(context).cancelUniqueWork("play_review_poll")
    }

    fun syncSoon(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build())
    }
}

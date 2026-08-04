package com.mbaliga.csapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the two background polling workers via WorkManager, network-constrained. */
object PollScheduler {

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleIfNotAlreadyScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val githubRequest = PeriodicWorkRequestBuilder<GitHubPollWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            GitHubPollWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            githubRequest,
        )

        val playRequest = PeriodicWorkRequestBuilder<PlayReviewPollWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PlayReviewPollWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            playRequest,
        )
    }
}

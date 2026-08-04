package com.mbaliga.csapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mbaliga.csapp.CsAppApplication

/** Periodic background poll of the configured GitHub repo's issues, then re-runs clustering. */
class GitHubPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CsAppApplication).container
        val owner = container.settingsStore.githubOwner
        val repo = container.settingsStore.githubRepo
        if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
            return Result.success() // Not configured yet; nothing to do.
        }
        return try {
            container.gitHubIssuePollRepository.pollRepo(owner, repo)
            container.incidentRepository.runClustering()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "github_issue_poll"
    }
}

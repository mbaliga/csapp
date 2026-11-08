package com.asoc.ping.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asoc.ping.CsAppApplication

/** One periodic pass over every project's sources, then clustering, then opt-in AI suggestions. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CsAppApplication).container
        return try {
            c.projectRepository.ensureDefaults(c.settingsStore)
            val report = c.pollCoordinator.syncAll()
            // Opt-in per project; writes PENDING suggestions only, never applies anything.
            for (pid in c.settingsStore.autoTriageProjects) {
                runCatching { c.suggestionService.runAutomation(pid) }
            }
            if (report.ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object { const val WORK_NAME = "csapp_sync" }
}

package com.asoc.ping.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.asoc.ping.BuildConfig
import com.asoc.ping.di.AppContainer
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Debug-only "export diagnostics": versions, device, row counts and checkpoints. Never message
 * bodies, never credentials. Shared only through the system share sheet when the owner taps it.
 */
object Diagnostics {
    suspend fun share(context: Context, c: AppContainer) {
        val text = build(c)
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }.resolve("csapp-diagnostics.txt").also { it.writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun build(c: AppContainer): String {
        val projects = c.projectRepository.projects()
        val incidents = c.incidentRepository.getAll()
        val signals = c.signalRepository.observeAll().first()
        val gov = c.database.governanceDao()
        return buildString {
            appendLine("Ping ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("generated ${Instant.now()}")
            appendLine("device ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("credentials: github=${c.credentialStore.hasGithubToken()} play=${c.credentialStore.hasPlayServiceAccountKey()} ai=${c.settingsStore.aiPreset ?: "off"}")
            appendLine("poll interval ${c.settingsStore.pollIntervalMinutes} min")
            appendLine("projects ${projects.size}, incidents ${incidents.size}, signals ${signals.size}")
            appendLine("signals by type: " + signals.groupingBy { it.type }.eachCount())
            appendLine("incidents by status: " + incidents.groupingBy { it.status }.eachCount())
            appendLine("reply log rows ${gov.allReplyLog().size}, suggestions ${gov.allSuggestions().size}")
            projects.forEach { p -> appendLine("project ${p.id}: sources=${c.projectRepository.sources(p.id).map { it.kind.name }}") }
        }
    }
}

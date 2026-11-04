package com.mbaliga.csapp.di

import android.content.Context
import androidx.room.Room
import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.credentials.KeystoreCredentialStore
import com.mbaliga.csapp.data.db.AppDatabase
import com.mbaliga.csapp.data.export.IssuesManifestExporter
import com.mbaliga.csapp.data.github.GitHubIssuePollRepository
import com.mbaliga.csapp.data.github.GitHubIssuePollingClient
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.play.PlayReviewsApiClient
import com.mbaliga.csapp.data.play.PlayServiceAccountAuth
import com.mbaliga.csapp.data.settings.AppSettingsStore
import com.mbaliga.csapp.domain.repository.IncidentRepository
import com.mbaliga.csapp.domain.repository.SignalRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * A small, manual dependency container (no DI framework). Kept deliberately simple for this V1
 * so the dependency graph is easy to read end to end in one file; a framework like Hilt can be
 * introduced later if the graph grows.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Currently selected project id (null until the first project exists). */
    val selectedProject: kotlinx.coroutines.flow.MutableStateFlow<String?> by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(settingsStore.selectedProjectId)
    }

    fun selectProject(id: String?) {
        settingsStore.selectedProjectId = id
        selectedProject.value = id
    }

    val credentialStore: CredentialStore by lazy { KeystoreCredentialStore(appContext) }

    val settingsStore: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(com.mbaliga.csapp.data.db.Migrations.MIGRATION_1_2)
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val signalRepository: SignalRepository by lazy { SignalRepository(database.signalDao()) }

    val incidentRepository: IncidentRepository by lazy {
        IncidentRepository(database.incidentDao(), database.signalDao())
    }

    private val gitHubIssuePollingClient: GitHubIssuePollingClient by lazy {
        GitHubIssuePollingClient(okHttpClient, tokenProvider = { credentialStore.getGithubToken() })
    }

    val projectRepository: com.mbaliga.csapp.data.project.ProjectRepository by lazy {
        com.mbaliga.csapp.data.project.ProjectRepository(database.projectDao())
    }

    val mailPollRepository: com.mbaliga.csapp.data.mail.MailPollRepository by lazy {
        val imap = com.mbaliga.csapp.data.mail.ImapMailSource()
        val gmail = com.mbaliga.csapp.data.mail.GmailMailSource(okHttpClient)
        com.mbaliga.csapp.data.mail.MailPollRepository(
            database.projectDao(), signalRepository, credentialStore,
            sourceFor = { kind -> if (kind == com.mbaliga.csapp.data.mail.MailboxKind.GMAIL) gmail else imap },
        )
    }

    val pollCoordinator: com.mbaliga.csapp.data.sync.PollCoordinator by lazy {
        com.mbaliga.csapp.data.sync.PollCoordinator(
            projectRepository, gitHubIssuePollRepository, playReviewRepository, mailPollRepository, incidentRepository, credentialStore,
            health = pollHealthStore,
        )
    }

    /**
     * Per-channel sync outcomes for the "Sync status" widget. Lives in noBackupFilesDir (never in a
     * cloud backup); holds times and counts only.
     */
    val pollHealthStore: com.mbaliga.csapp.data.sync.PollHealthStore by lazy {
        com.mbaliga.csapp.data.sync.PollHealthStore(
            java.io.File(appContext.noBackupFilesDir, "poll_health.json"),
            onChanged = { com.mbaliga.csapp.widget.CsWidgets.requestRefresh() },
        )
    }

    /** Read-only source of everything the home-screen widgets show. */
    val widgetSnapshotLoader: com.mbaliga.csapp.widget.WidgetSnapshotLoader by lazy {
        com.mbaliga.csapp.widget.WidgetSnapshotLoader(
            database.widgetDao(),
            pollHealth = { pollHealthStore.read() },
            pollIntervalMinutes = { settingsStore.pollIntervalMinutes },
        )
    }

    /** The configured model provider, or null (every AI entry point is then disabled). */
    fun currentProvider(): com.mbaliga.csapp.domain.ai.ModelProvider? {
        val preset = settingsStore.aiPreset?.let { runCatching { com.mbaliga.csapp.domain.ai.ProviderPreset.valueOf(it) }.getOrNull() }
            ?: return null
        val cfg = com.mbaliga.csapp.domain.ai.ProviderConfig(
            preset,
            settingsStore.aiBaseUrl?.takeIf { it.isNotBlank() } ?: preset.baseUrl,
            settingsStore.aiModel?.takeIf { it.isNotBlank() } ?: preset.defaultModel,
        )
        if (!preset.local && credentialStore.getSecret(AI_KEY).isNullOrBlank()) return null
        return com.mbaliga.csapp.domain.ai.createProvider(okHttpClient, cfg, apiKey = { credentialStore.getSecret(AI_KEY) })
    }

    val suggestionService: com.mbaliga.csapp.domain.ai.SuggestionService by lazy {
        com.mbaliga.csapp.domain.ai.SuggestionService(
            provider = { currentProvider() }, dao = database.governanceDao(),
            incidents = incidentRepository, signals = signalRepository,
        )
    }

    val gitHubIssuePollRepository: GitHubIssuePollRepository by lazy {
        GitHubIssuePollRepository(gitHubIssuePollingClient, database.checkpointDao(), signalRepository)
    }

    private val playServiceAccountAuth: PlayServiceAccountAuth by lazy { PlayServiceAccountAuth(okHttpClient) }

    fun playServiceAccountAuthForValidation(): PlayServiceAccountAuth = playServiceAccountAuth

    private val playReviewsApiClient: PlayReviewsApiClient by lazy {
        PlayReviewsApiClient(okHttpClient, playServiceAccountAuth)
    }

    val playReviewRepository: PlayReviewRepository by lazy {
        PlayReviewRepository(
            playReviewsApiClient,
            playServiceAccountAuth,
            credentialStore,
            database.checkpointDao(),
            signalRepository,
            database.governanceDao(),
        )
    }

    val issuesManifestExporter: IssuesManifestExporter by lazy {
        val producerVersion = appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .versionName ?: "0.0.0"
        IssuesManifestExporter(
            contentResolver = appContext.contentResolver,
            producerVersion = producerVersion,
        )
    }

    companion object { const val AI_KEY = "ai_api_key" }
}

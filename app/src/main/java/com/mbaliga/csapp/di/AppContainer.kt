package com.mbaliga.csapp.di

import android.content.Context
import androidx.room.Room
import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.credentials.EncryptedCredentialStore
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
    private val appContext = context.applicationContext

    val credentialStore: CredentialStore by lazy { EncryptedCredentialStore(appContext) }

    val settingsStore: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
    }

    private val okHttpClient: OkHttpClient by lazy {
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

    val gitHubIssuePollRepository: GitHubIssuePollRepository by lazy {
        GitHubIssuePollRepository(gitHubIssuePollingClient, database.checkpointDao(), signalRepository)
    }

    private val playServiceAccountAuth: PlayServiceAccountAuth by lazy { PlayServiceAccountAuth(okHttpClient) }

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
        )
    }

    val issuesManifestExporter: IssuesManifestExporter by lazy {
        IssuesManifestExporter(appContext.contentResolver)
    }
}

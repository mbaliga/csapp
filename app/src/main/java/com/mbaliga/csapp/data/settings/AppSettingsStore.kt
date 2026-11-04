package com.mbaliga.csapp.data.settings

import android.content.Context

/**
 * Non-secret app configuration (which repo/app to poll). Plain [android.content.SharedPreferences]
 * is fine here - unlike [com.mbaliga.csapp.data.credentials.CredentialStore], nothing stored here
 * is sensitive.
 */
class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var githubOwner: String?
        get() = prefs.getString(KEY_GITHUB_OWNER, null)
        set(value) = prefs.edit().putString(KEY_GITHUB_OWNER, value).apply()

    var githubRepo: String?
        get() = prefs.getString(KEY_GITHUB_REPO, null)
        set(value) = prefs.edit().putString(KEY_GITHUB_REPO, value).apply()

    var playPackageName: String?
        get() = prefs.getString(KEY_PLAY_PACKAGE, null)
        set(value) = prefs.edit().putString(KEY_PLAY_PACKAGE, value).apply()

    var pollIntervalMinutes: Int
        get() = prefs.getInt("poll_interval_min", 30)
        set(value) = prefs.edit().putInt("poll_interval_min", value).apply()

    var selectedProjectId: String?
        get() = prefs.getString("selected_project", null)
        set(value) = prefs.edit().putString("selected_project", value).apply()

    /** Projects where AI triage runs automatically after ingest (PENDING suggestions only). */
    var autoTriageProjects: Set<String>
        get() = prefs.getStringSet("auto_triage_projects", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("auto_triage_projects", value).apply()

    var aiPreset: String?
        get() = prefs.getString("ai_preset", null)
        set(value) = prefs.edit().putString("ai_preset", value).apply()

    var aiBaseUrl: String?
        get() = prefs.getString("ai_base_url", null)
        set(value) = prefs.edit().putString("ai_base_url", value).apply()

    var aiModel: String?
        get() = prefs.getString("ai_model", null)
        set(value) = prefs.edit().putString("ai_model", value).apply()

    /** In-flight Gmail OAuth: "<mailboxId>|<verifier>|<state>". */
    var pendingGmailAuth: String?
        get() = prefs.getString("pending_gmail_auth", null)
        set(value) = prefs.edit().putString("pending_gmail_auth", value).apply()

    private companion object {
        const val PREFS_NAME = "csapp_settings"
        const val KEY_GITHUB_OWNER = "github_owner"
        const val KEY_GITHUB_REPO = "github_repo"
        const val KEY_PLAY_PACKAGE = "play_package_name"
    }
}

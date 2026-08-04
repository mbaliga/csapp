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

    private companion object {
        const val PREFS_NAME = "csapp_settings"
        const val KEY_GITHUB_OWNER = "github_owner"
        const val KEY_GITHUB_REPO = "github_repo"
        const val KEY_PLAY_PACKAGE = "play_package_name"
    }
}

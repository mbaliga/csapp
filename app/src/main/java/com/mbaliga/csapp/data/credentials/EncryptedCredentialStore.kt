package com.mbaliga.csapp.data.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [CredentialStore] backed by [EncryptedSharedPreferences], whose keys are generated and held in
 * the Android Keystore (see [MasterKey], AES256-GCM). Secrets are encrypted at rest and never
 * touch disk in plaintext; only the OS-backed Keystore can unwrap the master key.
 */
class EncryptedCredentialStore(context: Context) : CredentialStore {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun putGithubToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    override fun getGithubToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)

    override fun clearGithubToken() {
        prefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    override fun putPlayServiceAccountKeyJson(json: String) {
        prefs.edit().putString(KEY_PLAY_SERVICE_ACCOUNT, json).apply()
    }

    override fun getPlayServiceAccountKeyJson(): String? = prefs.getString(KEY_PLAY_SERVICE_ACCOUNT, null)

    override fun clearPlayServiceAccountKeyJson() {
        prefs.edit().remove(KEY_PLAY_SERVICE_ACCOUNT).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "csapp_secure_credentials"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_PLAY_SERVICE_ACCOUNT = "play_service_account_key_json"
    }
}

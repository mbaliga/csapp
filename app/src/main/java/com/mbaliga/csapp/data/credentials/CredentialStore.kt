package com.mbaliga.csapp.data.credentials

/**
 * Stores small secrets (API tokens, service-account keys) needed to poll external sources.
 * Implementations must never persist secrets in plaintext; see [EncryptedCredentialStore] for
 * the Android Keystore-backed implementation used in production.
 */
interface CredentialStore {
    fun putGithubToken(token: String)
    fun getGithubToken(): String?
    fun clearGithubToken()

    /** The raw JSON contents of a Play "service account" key file. */
    fun putPlayServiceAccountKeyJson(json: String)
    fun getPlayServiceAccountKeyJson(): String?
    fun clearPlayServiceAccountKeyJson()

    fun hasGithubToken(): Boolean = getGithubToken() != null
    fun hasPlayServiceAccountKey(): Boolean = getPlayServiceAccountKeyJson() != null
}

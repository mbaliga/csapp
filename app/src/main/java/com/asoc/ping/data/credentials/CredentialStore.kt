package com.asoc.ping.data.credentials

/**
 * Stores small secrets (API tokens, service-account keys, mailbox passwords, model keys) needed
 * to talk to external services. Implementations must never persist secrets in plaintext; see
 * [KeystoreCredentialStore] for the Android Keystore-backed implementation used in production.
 *
 * Only [putSecret]/[getSecret]/[clearSecret] are abstract; the named accessors are conveniences.
 */
interface CredentialStore {
    fun putSecret(name: String, value: String)
    fun getSecret(name: String): String?
    fun clearSecret(name: String)

    fun putGithubToken(token: String) = putSecret(GITHUB_TOKEN, token)
    fun getGithubToken(): String? = getSecret(GITHUB_TOKEN)
    fun clearGithubToken() = clearSecret(GITHUB_TOKEN)

    /** The raw JSON contents of a Play "service account" key file. */
    fun putPlayServiceAccountKeyJson(json: String) = putSecret(PLAY_KEY, json)
    fun getPlayServiceAccountKeyJson(): String? = getSecret(PLAY_KEY)
    fun clearPlayServiceAccountKeyJson() = clearSecret(PLAY_KEY)

    fun hasGithubToken(): Boolean = getGithubToken() != null
    fun hasPlayServiceAccountKey(): Boolean = getPlayServiceAccountKeyJson() != null

    /** Wipes every credential this store holds. */
    fun clearAll()

    companion object {
        const val GITHUB_TOKEN = "github_token"
        const val PLAY_KEY = "play_service_account_key_json"
    }
}

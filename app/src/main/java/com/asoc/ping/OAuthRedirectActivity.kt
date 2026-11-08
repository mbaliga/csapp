package com.asoc.ping

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.asoc.ping.data.mail.GmailOAuth
import com.asoc.ping.data.mail.MailPollRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the Gmail OAuth redirect (PKCE), exchanges the code, stores the refresh token. */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CsAppApplication).container
        val data = intent?.data
        val pending = container.settingsStore.pendingGmailAuth?.split("|")
        val code = data?.getQueryParameter("code")
        val state = data?.getQueryParameter("state")
        if (pending == null || pending.size != 3 || code == null || state != pending[2]) {
            Toast.makeText(this, "Gmail sign-in was cancelled or did not match", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val (mailboxId, verifier) = pending[0] to pending[1]
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val msg = try {
                val clientId = container.database.projectDao().findMailbox(mailboxId)?.host
                    ?: throw IllegalStateException("Mailbox not found")
                val refresh = GmailOAuth.exchangeCode(container.okHttpClient, clientId, code, verifier)
                container.credentialStore.putSecret(MailPollRepository.secretName(mailboxId), refresh)
                container.settingsStore.pendingGmailAuth = null
                "Gmail connected"
            } catch (e: Exception) { "Gmail sign-in failed: ${e.message}" }
            kotlinx.coroutines.withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }
        finish()
    }
}

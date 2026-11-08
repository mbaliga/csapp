package com.asoc.ping.data.mail

import okhttp3.OkHttpClient

/** IMAP over TLS with an app-password. */
class ImapMailSource : MailSource {
    override fun fetchNew(mailbox: MailboxConfig, secret: String, checkpoint: MailCheckpointState?): MailFetchResult =
        ImapClient(mailbox.host, mailbox.port).fetchNew(mailbox.username, secret, mailbox.folder, checkpoint)
}

/** Gmail REST. For GMAIL mailboxes `host` holds the OAuth client id and [secret] the refresh token. */
class GmailMailSource(private val http: OkHttpClient) : MailSource {
    override fun fetchNew(mailbox: MailboxConfig, secret: String, checkpoint: MailCheckpointState?): MailFetchResult {
        val token = GmailOAuth.refreshAccessToken(http, mailbox.host, secret)
        val label = mailbox.folder.lowercase().ifBlank { "inbox" }
        return GmailApiClient(http, { token }).fetchNew("in:$label", checkpoint)
    }
}

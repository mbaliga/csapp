# Privacy Policy — CS App

> **Draft for owner review.** Working copy of the hosted page. It describes what the code
> of `main` @ `080d588` does, read on 2026-11-04, not a release build. Re-check it against
> the release you publish, and against `play-console.md` (the Data safety answers must
> agree with this page).
>
> Intended hosted URL: **https://asystemofcells.com/csapp/privacy**.
>
> If Gmail is cut from the Play build, delete every item marked `<!-- gmail -->` and every
> other mention of Gmail or Google sign-in. Delete the sentence after
> `<!-- imap-hostname -->` once `ImapClient` verifies the server's host name.

**Last updated: 4 November 2026**

CS App is a support console for Android (package `com.mbaliga.csapp`), made by
A System of Cells. It is a tool for developers managing their own apps.

## The short version

A System of Cells receives nothing from the app. We run no server that the app talks to,
and the app has no accounts of ours, no advertising, no analytics and no tracking.

The app does move data between your phone and services you configure: Google Play,
GitHub, your mail provider and, if you choose one, an AI provider. Sync runs automatically
in the background about every 15, 30 (the default) or 60 minutes while the phone is online,
as Android allows, and also when you pull down on Home or tap Sync all now. This page lists every service and what
each receives.

## What we collect

What A System of Cells collects: **nothing.** We operate no servers and receive nothing
from the app. The app itself sends data to the services you configure, as listed under
"The only services the app contacts". That can include review, issue and email text sent to
an AI provider you choose.

## Your credentials

To do its job the app may need your Google Play Developer API service account key, a
GitHub token, a mailbox app password or Gmail sign-in, and an API key for an AI provider.
They are stored encrypted, with a key held in the Android Keystore.

- **Play service account key:** the private key never leaves the phone. The app signs a
  short-lived token with it and sends that signed token to the token address written in
  your key file (normally Google's `oauth2.googleapis.com`), then calls the Google Play
  Developer API with the access token Google returns.
- **GitHub token:** sent to `api.github.com`. It is optional for public repositories.
- **Mailbox app password (IMAP):** sent to the mail server and port you enter, over a TLS
  connection (port 993 by default).
  <!-- imap-hostname -->
  In version 1.0.0 the app does not itself check that the server's certificate name
  matches the server you entered.
- <!-- gmail -->**Gmail:** you sign in in your browser at Google. The app receives an
  authorisation code and exchanges it at `oauth2.googleapis.com`. The only long-lived
  Google credential it keeps is the refresh token, stored encrypted. It also keeps your
  OAuth client ID. From the moment you start sign-in it keeps a one-time sign-in check value
  in the app's private settings, not separately encrypted. The app erases it when a sign-in
  succeeds; if a sign-in is cancelled or fails it stays until you start another.
- **AI key:** the app keeps one key. It is sent to whichever AI provider address is
  currently saved in Settings, so change or clear it when you switch provider.

None of these is sent to us.

## The only services the app contacts

There is no analytics, crash-reporting or advertising service. The app contacts only:

| Service | When | What is sent |
|---|---|---|
| Google Play Developer API, and the token address in your key file | Sync, and when you confirm a reply | To the API: the package name, an access token Google issued in exchange for your signed token, and for a reply the review ID and the reply text you confirmed. To the token address: a token signed with your key, naming your service account |
| GitHub API (`api.github.com`) | Sync | Your token (if set) and the repositories you added. Read-only: the app only lists issues |
| Your mail server (IMAP) | Sync | Your mailbox username and app password. Read-only: the app lists and fetches mail and never changes, moves or deletes it |
| <!-- gmail -->Google (`oauth2.googleapis.com`, `gmail.googleapis.com`, and Google's sign-in page in your browser) | When you connect a mailbox, and on each sync | Your OAuth client ID and refresh token (to `oauth2.googleapis.com`), then a read-only access token and a label-and-date search (to `gmail.googleapis.com`). When you connect: your client ID in Google's sign-in page, then the authorisation code and a PKCE check value. The app never sends or changes mail |
| An AI provider **you** choose (optional): by default DeepSeek, xAI (Grok), OpenAI, Google (Gemini) or Anthropic | When you run an AI action, or for a project where you turned on automatic triage | The text listed below, and your key. The address is editable in Settings, and the text and your key go to whichever address you save there |
| A model server on this phone (optional), by default `127.0.0.1` | Same | The same text. It goes to the address you configure, which is your own phone by default. If you point it at another machine, it goes there |

AI is off until you choose a provider. AI output is only ever a suggestion: it changes
your data only when you tap Accept on a triage or duplicate suggestion, or Use draft on a
reply draft, and a reply draft only fills the reply box.

**What an AI action sends.** Review, issue and email text is not redacted before it is
sent.

- *Triage:* the incident title, plus up to eight of its reports, each as its type, its
  title in full (for an email, the subject) and the first 600 characters of its body.
- *Find duplicates:* for the project's incidents that are not dismissed or merged, up to 10
  pairs, each sent as its own request with the two incidents' titles and summaries. Tapping
  it on one incident checks the whole project.
- *Draft a reply:* the star rating and the first 1,200 characters of the review.
- *Weekly digest:* up to 30 lines, each an incident's severity, status and title.
- *Test (in Settings):* the fixed instruction "Reply with the single word: ok" and the
  word "ping".

Every action also sends the app's own fixed instructions for it, and the model name. The
app does not add author names or email addresses as separate fields, but they can appear
inside the review, issue or email text it sends, and that text is not redacted.

**Automatic triage.** Each project has a switch, off by default. When it is on, each
scheduled background sync is followed, without a tap, by triage requests for up to 10 of
that project's open incidents that have no pending triage suggestion, and by a duplicate
check of up to 10 incident pairs, using the text described above. Sync all now and
pull-to-refresh do not run them. Archiving a project does not switch it off, and an
archived project keeps syncing.

## Data the app fetches on your behalf

The app fetches **Play Store reviews, GitHub issues and support email for the apps,
repositories and mailboxes you add to it**. That content is written by other people, and
it may include their review text, display name, device model and language, their GitHub
login, and the subject, sender name and address, recipients and body of their emails.

- It is fetched directly from Google, GitHub and your mail provider with your credentials.
- It is stored **on your device** so you can work with it offline.
- It is never sent to us.
- It leaves your phone only to the services listed above, and through the things you do
  yourself:
  - **Reply to a Play review.** The reply goes to Google, and only after you have read and
    confirmed the exact text. The app sends nothing to any customer without your
    confirmation.
  - **Reply in mail app.** The app never sends email. This button opens your own mail app
    with the sender's address, a subject and the first 12 lines of the message filled in;
    you send it from there.
  - **Save file or Share** an export (see below).
  - **Share a crash report** (see below).

If you are handling other people's data this way, you remain responsible for it under
whatever terms apply to you as an app publisher.

## Exports

Save file and Share to Fonebrew write an `issues-manifest.v1.json` for one project. It
lists each incident that is not dismissed or merged, with its title, severity, status and a
summary (by default a count line the app writes, such as "3 signal(s) from: PLAY_REVIEW,
EMAIL"; for an incident you created by hand, the details you typed; after you accept an AI
triage suggestion, the title and summary a model wrote), the titles and identifiers of its
reports (email subjects and message IDs included), the identifier of the incident it recurs
from if you marked a recurrence, timestamps, and a reporter reference:
the display name, GitHub login or email sender (name and address) of one of its reports. It
contains no credentials. Titles, subjects and summaries come from the fetched text, so it can
hold excerpts of reviews, issues and emails. You choose where it goes: Save file writes where
you pick, and Share to Fonebrew opens Android's share sheet for the app you pick. A copy made
for sharing stays in the app's private cache until Android clears it or you uninstall.

## Crash reports

If the app crashes, the next launch shows a recovery screen. The report holds the time,
thread, error type and message exactly as the exception states it, the stack trace, app
version and package name, how the app was installed (for example Play Store or sideloaded),
device manufacturer and model, Android version, and free and total memory. It is written to
the app's private storage, and the error and stack trace are also written to the system log.

The app itself sends no crash report. The report leaves the app only if you tap Copy, Save
file, Email or WhatsApp on that screen (if WhatsApp is missing, or no place to save is
available, Android's share sheet opens instead). Separately, if your device is set to share
usage and diagnostics, Android and Google Play may collect crash data about apps, including
this one. The app does not control that.

If the app keeps crashing, the recovery screen offers "Reset & set my data aside". That
moves the app's data (database, settings and stored credentials) into a private folder.
The recovery screen can restore it with "Restore the data I set aside", but that screen
only appears if the app crashes again. After the app's main screen has started cleanly
twice (each time it is created, which includes rotating the phone), the set-aside copy is
deleted permanently.

## What is stored, and where

In the app's private storage on your device:

- Incidents, and the reviews, issues and emails behind them (including email bodies and
  sender names and addresses; attachment names, never attachment contents)
- Your triage state, AI suggestions, and a log of every Play reply you send (full text,
  time, outcome)
- Your credentials, encrypted as above

Other app data is not additionally encrypted by the app, beyond Android's own storage
encryption. The app turns off Android's cloud backup, so Android Auto Backup does not copy
this data to your Google account. It does not configure Android's phone-to-phone transfer,
and on some devices that transfer can still carry app data to a new phone.

**Keeping and deleting.** The app never deletes your data on its own, with two exceptions:
the set-aside copy described under Crash reports, and the temporary Share copy, which
Android may clear. Content a customer later deletes at its source stays on your phone. In
version 1.0.0 you can remove credentials, mailboxes and project sources from inside the app,
but you cannot delete fetched content, suggestions or the reply log from inside it. That is
removed by Android's Clear storage for CS App, or by uninstalling. Neither removes files you
saved or shared out of the app, or access you granted. To revoke that, revoke the token,
service account, <!-- gmail -->Gmail access or mailbox app password at the service that
issued it.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `INTERNET` | To reach the Google Play Developer API, GitHub, your mail server, Google's token endpoint (and <!-- gmail -->Google sign-in), and your chosen AI provider |
| `ACCESS_NETWORK_STATE` | Lets background sync run only when the phone is online |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | Added by the Android WorkManager library for the periodic sync. The app starts no foreground service and runs no work of its own at boot |
| `com.mbaliga.csapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by AndroidX Core. A signature-level permission, granted by Android only to apps signed with the same key as CS App; it gives no access to your data |

The app shows no notifications and does not ask for location, contacts, storage, camera or
microphone.

## Children

CS App is a developer tool and is not directed at children. A System of Cells collects no
personal information from anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the revised
policy is published at this same URL.

## Contact

CS App is made by **A System of Cells**. Questions about this policy or the app:
csapp@asystemofcells.com

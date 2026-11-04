# Privacy Policy — CS App

> **Draft for owner review.** Working copy of the hosted page. It describes what the code
> of `main` @ `080d588` does, read on 2026-11-04, not a release build. Re-check it against
> the release you publish, and against `play-console.md` (the Data safety answers must
> agree with this page).
>
> Intended hosted URL: **https://asystemofcells.com/csapp/privacy**.
>
> Sections marked `<!-- gmail -->` go if Gmail is cut from the Play build.

**Last updated: 4 November 2026**

CS App is a support console for Android (package `com.mbaliga.csapp`), made by
A System of Cells. It is a tool for developers managing their own apps.

## The short version

A System of Cells receives nothing from the app. We run no server that the app talks to,
and the app has no accounts of ours, no advertising, no analytics and no tracking.

The app does move data, at your direction, between your phone and services you configure:
Google Play, GitHub, your mail provider and, if you choose one, an AI provider. This page
lists every one of them and what each receives.

## What we collect

**Nothing.** We operate no servers and receive nothing from the app.

## Your credentials

To do its job the app may need your Google Play Developer API service account key, a
GitHub token, a mailbox app password or Gmail sign-in, and an API key for an AI provider.
They are stored encrypted, with a key held in the Android Keystore.

- **Play service account key:** the private key never leaves the phone. The app signs a
  short-lived token with it and sends that signed token to the token address written in
  your key file (normally Google's `oauth2.googleapis.com`), then calls the Google Play
  Developer API with the result.
- **GitHub token:** sent to `api.github.com`. It is optional for public repositories.
- **Mailbox app password (IMAP):** sent to the mail server and port you enter, over a TLS
  connection (port 993 by default).
- **Gmail** <!-- gmail -->**:** you sign in in your browser at Google. The app receives
  an authorisation code and exchanges it at `oauth2.googleapis.com`; only a refresh token
  is kept.
- **AI key:** the app keeps one key. It is sent to whichever AI provider is currently
  selected in Settings, so change or clear it when you switch provider.

None of these is sent to us.

## The only services the app contacts

There is no analytics, crash-reporting or advertising service. The app contacts only:

| Service | When | What is sent |
|---|---|---|
| Google Play Developer API, and the token address in your key file | Sync, and when you confirm a reply | The package name, a signed token derived from your key, and the reply text you confirmed |
| GitHub API (`api.github.com`) | Sync | Your token (if set) and the repositories you added. Read-only: the app only lists issues |
| Your mail server (IMAP) | Sync | Your mailbox username and app password. Read-only: the app lists and fetches mail and never changes, moves or deletes it |
| Gmail API and Google sign-in <!-- gmail --> | Sync, and when you connect a mailbox | A read-only access token. The app never sends or changes mail |
| An AI provider **you** choose (optional): DeepSeek, xAI (Grok), OpenAI, Google (Gemini) or Anthropic | When you run an AI action, or for a project where you turned on automatic triage | The text needed for that action, listed below, and your key |
| A model server on this phone (optional), by default `127.0.0.1` | Same | The same text. It goes to the address you configure, which is your own phone by default. If you point it at another machine, it goes there |

AI is off until you choose a provider. AI output is only ever a suggestion: it changes
your data only when you tap Accept, and a reply draft only fills the reply box.

**What an AI action sends.** Review, issue and email text is not redacted before it is
sent.

- *Triage:* the incident title, plus up to eight of its reports, each as its type and
  title and the first 600 characters of its text (review, issue, or email subject and body).
- *Find duplicates:* the titles and summaries of two incidents.
- *Draft a reply:* the star rating and the first 1,200 characters of the review.
- *Weekly digest:* up to 30 lines, each an incident's severity, status and title.
- *Test connection:* the word "ping".

Author names and email addresses are not put into these requests.

**Automatic triage.** Each project has a switch, off by default. When it is on, the app
runs triage in the background after each sync and sends the text above to your provider
without a tap. Archiving a project does not switch it off.

## Data the app fetches on your behalf

The app fetches **Play Store reviews, GitHub issues and support email for the apps,
repositories and mailboxes you control**. That content is written by other people, and it
may include their review text, display name, device model and language, their GitHub
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

Save file and Share write an `issues-manifest.v1.json` for one project. It lists each
incident that is not dismissed or merged, with its title, severity, status and a summary
(which can be text a model wrote after you accepted it), the titles and identifiers of its
reports (email subjects and message IDs included), and the name or email address of the
first reporter. It does not contain credentials or raw message bodies. You choose where it
goes: Save writes where you pick, and Share hands it to the app you pick. A copy made for
Share stays in the app's private cache until Android clears it or you uninstall.

## Crash reports

If the app crashes, the next launch shows a recovery screen. The report (error message,
stack trace, device model and app version) is written to the app's private storage and to
the system log. It leaves the phone only if you tap Copy, Save, Email, WhatsApp or Share on
that screen. After repeated crashes the screen offers to set the app's data aside. That
moves it into a private folder, restorable from the same screen, and does not delete it
until two clean launches have passed.

## What is stored, and where

In the app's private storage on your device:

- Incidents, and the reviews, issues and emails behind them (including email bodies and
  sender names and addresses; attachment names, never attachment contents)
- Your triage state, AI suggestions, and a log of every Play reply you send (full text,
  time, outcome)
- Your credentials, encrypted as above

Other app data is not additionally encrypted by the app, beyond Android's own storage
encryption. The app opts out of Android cloud backup and device transfer, so none of this
is copied to your Google account.

**Keeping and deleting.** Nothing is deleted automatically, and content a customer later
deletes at its source stays on your phone. In version 1.0.0 you can remove credentials,
mailboxes and project sources from inside the app, but you cannot delete fetched content,
suggestions or the reply log from inside it. That is removed by Android's Clear storage
for CS App, or by uninstalling. Neither removes files you saved or shared out of the app,
or access you granted. To revoke that, revoke the token, service account, Gmail access or
app password at the service that issued it.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `INTERNET` | To reach the Google Play Developer API, GitHub, your mail server, Google sign-in and token endpoints, and your chosen AI provider |
| `ACCESS_NETWORK_STATE` | Lets background sync run only when the phone is online |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | Added by the Android WorkManager library for the periodic sync. The app starts no foreground service and runs no work of its own at boot |

The app shows no notifications and does not ask for location, contacts, storage, camera or
microphone.

## Children

CS App is a developer tool, is not directed at children, and collects no personal
information from anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the revised
policy is published at this same URL.

## Contact

CS App is made by **A System of Cells**. Questions about this policy or the app:
csapp@asystemofcells.com

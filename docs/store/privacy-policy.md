# Privacy Policy — CS App

> **Draft for owner review.** This policy describes the shipped design; re-check it against the
> release you publish before hosting it.
>
> Intended hosted URL: **https://asystemofcells.com/csapp/privacy**.

**Last updated: 3 November 2026**

CS App is a customer-support console for Android (package `com.mbaliga.csapp`), made
by A System of Cells. It is a tool for developers managing their own apps.

## The short version

CS App has no accounts of ours, no advertising, no analytics and no tracking. It
talks directly to the services you configure, using credentials you supply, and there is no
server of ours anywhere in that path.

## What we collect

**Nothing.** We operate no servers and receive nothing from the app.

## Your credentials

To do its job the app may need your Google Play Developer API service account key, your GitHub
token, your support-mailbox app password or Gmail sign-in token, and an API key for an AI model
provider if you choose one. They are encrypted with a key held in the Android Keystore and
are sent only to the service each belongs to.

They are never transmitted anywhere else, and never to us.

## The only services the app contacts

Nothing else is contacted. There is no analytics, crash-reporting or advertising service.

| Service | When | What is sent |
|---|---|---|
| Google Play Developer API | Sync, and when you confirm a reply | Your service-account credentials; the reply text you confirmed |
| GitHub API | Sync | Your token; the repositories you added |
| Your mail server (IMAP) or Gmail API | Sync | Your mailbox credentials (read-only access) |
| An AI model provider **you** choose (optional) | Only when you run an AI action or enable per-project suggestions | The review / issue / mail text needed for that action, and your key |
| On-device model (e.g. ASOM) | Same | Stays on your device |

AI output is only ever a suggestion. It is applied to your data only when you tap Accept.

## Data the app fetches on your behalf

The app fetches **Play Store reviews, GitHub issues and support email for the apps,
repositories and mailboxes you control**. That content is written by other people, and it may
include their review text, their public username and whatever they chose to put in
an issue.

- It is fetched directly from Google, GitHub and your mail provider with your credentials.
- It is stored **locally on your device** so you can work with it offline.
- It is never sent to us, and never shared with any third party.
- If you reply to a Play review, that reply goes to Google, and only after you have read
  and confirmed the exact text. The app never sends email; "Reply in mail app" opens your own mail app. The app sends nothing to any user without your explicit
  confirmation.

If you are handling other people's data this way, you remain responsible for it
under whatever terms apply to you as an app publisher.

## What is stored, and where

Incidents, fetched reviews and issues, your triage state and your credentials are
stored in the app's private storage on your device. Uninstalling CS App deletes all
of it.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `INTERNET` | To reach the Google Play Developer API, the GitHub API, your mail server and your chosen AI provider. |
| `ACCESS_NETWORK_STATE` | To avoid polling while offline. |

## Children

CS App is a developer tool, is not directed at children, and collects no personal
information from anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the
revised policy is published at this same URL.

## Contact

CS App is made by **A System of Cells**. Questions about this policy or the app:
csapp@asystemofcells.com

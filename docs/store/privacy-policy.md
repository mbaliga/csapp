# Privacy Policy — CS App

> **Draft.** The app has never compiled (see `docs/store/play-console.md`), so this
> policy describes the intended design and must be re-checked against the real
> implementation before it is published anywhere.
>
> Intended hosted URL: **https://asystemofcells.com/csapp/privacy**.

**Last updated: 29 August 2026**

CS App is a customer-support console for Android (package `com.mbaliga.csapp`), made
by A System of Cells. It is a tool for developers managing their own apps.

## The short version

CS App has no accounts of ours, no advertising, no analytics and no tracking. It
talks directly to Google and GitHub using credentials you supply, and there is no
server of ours anywhere in that path.

## What we collect

**Nothing.** We operate no servers and receive nothing from the app.

## Your credentials

To do its job the app needs your Google Play Developer API service account key and
your GitHub token. They are stored encrypted on your device, using Android's
encrypted preferences, and are sent only to Google and GitHub respectively.

They are never transmitted anywhere else, and never to us.

## Data the app fetches on your behalf

The app fetches **Play Store reviews and GitHub issues for the apps and
repositories you control**. That content is written by other people, and it may
include their review text, their public username and whatever they chose to put in
an issue.

- It is fetched directly from Google and GitHub with your credentials.
- It is stored **locally on your device** so you can work with it offline.
- It is never sent to us, and never shared with any third party.
- If you reply, that reply goes to Google or GitHub, and only after you have read
  and confirmed it. The app sends nothing to any user without your explicit
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
| `INTERNET` | To reach the Google Play Developer API and the GitHub API. |
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

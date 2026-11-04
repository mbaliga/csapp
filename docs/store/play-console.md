# CS App — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.
>
> Re-verified 2026-11-04 against `main` @ `080d588`: source, the committed debug APK's
> merged manifest, and Google's help pages fetched that day. Nothing here has been built
> in release form or run on a device. Every `docs/VERIFICATION.md` row is still unticked.

| | |
|---|---|
| applicationId | `com.mbaliga.csapp` (permanent once published) |
| Version | `1.0.0` (versionCode `2`) |
| minSdk / targetSdk / compileSdk | `26` / `36` / `36` |
| Category | **Business** |
| Contact email | `csapp@asystemofcells.com` |
| Website | `https://asystemofcells.com/csapp` |
| Privacy policy | `https://asystemofcells.com/csapp/privacy` (working copy: `privacy-policy.md`) |

## Where this stands

- V1 has been on `main` since August 2026. WP0 to WP6 (multi-project, IMAP and Gmail
  ingestion, AI suggestions, reply governance, ratified export, env-var signing) landed
  2026-11-03 (`docs/STATUS.md`, `080d588`). The old "recover or rebuild" question is moot.
- The only build artifact is `dist/csapp-1.0.0-debug.apk`: debuggable, debug-signed,
  same applicationId as release. No release build, no AAB, no upload key.
- Nothing has been submitted to Play. On-device behaviour is owner-verified only.

## Blockers before the first upload

### 1. There is no uploadable artifact yet

- **Upload key.** None exists or is documented. Release signing reads
  `CSAPP_KEYSTORE_PATH`, `CSAPP_KEYSTORE_PASSWORD`, `CSAPP_KEY_ALIAS` and
  `CSAPP_KEY_PASSWORD`; without them the release variant is silently **unsigned**
  (`app/build.gradle.kts`). Let Play manage the app signing key and keep this one as the
  upload key (`Personal-Tracker/store/RUNBOOK.md`).
- **No AAB lane.** CI and `BUILD-SPEC.md` only ever call `assembleRelease` (an APK).
  `./gradlew :app:bundleRelease` should honour the same signing config but has never run.
- **The tag-gated release steps in `ci.yml` probably never run.** Their `if:` tests
  `env.KS_B64 != ''`, but `KS_B64` is defined only in the same step's own `env:`, which a
  step's `if` normally cannot see. Unconfirmed without a real tag push.
- **The `shared-libraries` submodule is required to configure any Gradle task**
  (`includeBuild`, pinned at `4a8b0a3`, crash-recovery 1.5.0). CI falls back to
  `github.token` if `CORE_SUBMODULE_TOKEN` is missing, which only reads this repo. The pin
  was not reachable from any branch in the one local clone checked, so confirm that
  `git submodule update --init` can fetch it.
- **R8 is on for release and has never been exercised.** `docs/STATUS.md` says
  "R8 release assembles", but no artifact, log or CI step backs it (the same file calls the
  path "unverified" under Deviations). Risks to check on a real build: consumer rules for
  kotlinx-serialization, OkHttp and Room; the AI prompts are read as resources
  (`SuggestionService.kt:31`, `getResourceAsStream(...)!!`) and must survive shrinking;
  crash-recovery ships no consumer rules, so release traces will be obfuscated;
  `lintVitalRelease` runs inside `bundleRelease`.
- **The committed debug APK is a trap for your own phone.** Debug and release share one
  applicationId, so a Play build will not install over it until it is uninstalled, and
  uninstalling wipes the Keystore-held credentials (`allowBackup=false`).
- **Store assets are missing**: 512 px icon, feature graphic, screenshots
  (`fastlane/.../images/README.md`).

### 2. Code that makes the paperwork untrue

Each row is something this sheet, the privacy policy or the in-app text would otherwise
claim. Fix the code, cut the feature from the Play build, or keep the weaker wording
already used below. All were found by reading source; none was run.

| # | Defect | Where | What it breaks |
|---|---|---|---|
| 1 | **One shared AI key slot.** The key is kept across preset switches and attached as a bearer header to whichever endpoint is current, loopback presets included. Switch DeepSeek to OpenAI, or to ASOM, without retyping a key and the old key goes to the new host. | `AppContainer.kt:93-94,137`, `Providers.kt:34-38,66`, `SettingsViewModel.kt:98-104` | "Credentials are sent only to the service each belongs to"; the in-app line "The key is sent only there" |
| 2 | **"On-device" is decided by the preset, not the address.** The Base URL is free text; a local preset pointed at an https remote host still shows the On-device badge and "Nothing leaves it". | `Providers.kt:31`, `SettingsScreen.kt:182,185` | "Stays on your device" for the local model row |
| 3 | **IMAP has no hostname verification.** The TLS socket comes from `SSLSocketFactory.getDefault().createSocket(...)` and nothing sets endpoint identification. Android's docs say a raw `SSLSocket` does not verify the host name and the app must. A certificate valid for any other name would be accepted, exposing the IMAP app password. | `ImapClient.kt:49,82-88` | "Encrypted in transit: Yes" for the mail leg |
| 4 | **The auto-triage switch does not say text leaves the device.** Its help text says only "Only writes pending suggestions". The worker sends review, issue and mail text to the cloud provider after each sync with no tap. In-app text also says "no automatic sends". | `ProjectsScreens.kt:129-132`, `SyncWorker.kt:16-18`, `SettingsScreen.kt:118` | Data safety "shared" answer; the disclosure Google expects for non-obvious background transfers |
| 5 | **Archiving a project does not stop it.** It only sets a flag; polling, mail routing and auto-triage still include archived projects, and there is no un-archive control. | `ProjectRepository.kt:44-48`, `ProjectDao.kt:26`, `PollCoordinator.kt:28`, `SuggestionService.kt:132-139` | Any claim that archiving stops use of a project |
| 6 | **No in-app deletion of fetched content.** The only `DELETE` queries remove project sources and mailboxes. Reviews, issues, mail text, suggestions and the reply log stay until Android "Clear storage" or uninstall. Removing a mailbox keeps its mail. | `ProjectDao.kt:23,31` | "Users can delete data in the app" (rewritten below) |

Smaller: the in-app Local text says "local network", but cleartext is allowed only to
`127.0.0.1` and `localhost` (`network_security_config.xml`); the cloud text says "Review
text" though issue and mail text are sent too.

### 3. Owner decisions this sheet does not make

- **Product name.** The store title is `CS App`; the launcher label is `csapp`
  (`strings.xml`), and `csapp` is also hard-coded in Home, Settings, the mail hint, the
  crash-recovery label and the export `producer.app`. Rule 11 says the name lives in
  `app_name` only, so a rename needs code edits. `Personal-Tracker/NAMES.md` records no
  decision.
- **Public listing or not.** It is a personal developer tool. Play's internal track works
  with no testing prerequisite; sideload is no longer free of Google gating (Android
  developer verification begins 2026-09-30 in select regions, per
  developer.android.com/developer-verification, provisional).
- **What the Play build contains.** Cloud AI providers, the background auto-triage
  switch, and Gmail each change the Data safety answer. Recommendation: leave Gmail out
  of the first Play build (see **Gmail**), and either keep cloud AI behind a clear
  disclosure or ship ASOM and local presets only.
- **Account type.** Google's page (fetched 2026-11-04) requires a personal account created
  after 2023-11-13 to run a closed test with at least 12 testers opted in for 14 days
  before production access, per app. Whether the A System of Cells account is personal or
  organisation is not recorded.

### 4. Outside the repo, not checked

`https://asystemofcells.com/csapp/privacy` returning 200 for a logged-out reader, the
`csapp@asystemofcells.com` alias delivering, and the Play Console account state.

## App access

CS App cannot answer "all functionality is available without special access". Choose
**All or some functionality is restricted**. Google requires reviewer credentials that are
reusable, valid regardless of location, bypass 2FA and unlock all functionality (help
answer 15748846), so say what each credential unlocks:

| To see | Needs |
|---|---|
| Incidents, triage, export | Projects, New, add a **public** `owner/repo`, then pull down on Home. A GitHub token is optional (it only lifts rate limits and reaches private repos). The `+` button adds a report by hand. |
| Monthly review CSV import | A sample file; no key needed |
| Support email | A throwaway IMAP mailbox with an app password. Gmail needs the reviewer's own Google OAuth client ID, so do not ask a reviewer to use it. |
| AI suggestions | Off by default; needs a provider key you supply |
| **Play review list and reply** | A Play Console **service account key**. A throwaway GitHub account cannot cover this path. |

Never give a reviewer your real service account key. The earlier claim that it "would
grant access to the whole developer account" is overstated: its reach is whatever Play
Console's Users and permissions granted (it can be limited to one app and to "Reply to
reviews"), but the JWT scope `androidpublisher` cannot be narrowed in code, and Google's
API docs advise against handing out service account private keys. For the Play path,
offer a screen recording, or a dedicated test developer account that owns a published app
with commented reviews.

## Data safety

The earlier answer "No, nothing is transmitted to us" tested the wrong thing. Google's
definition is any transmission off the device, to anyone, including a third party's server
(help answer 10787469). Declare for the build **as it stands**:

| Question | Answer |
|---|---|
| Collect or share any required user data types? | **Yes** |
| Types | **Emails** (subject, sender, recipients, content) and **Other user-generated content** (review and issue text), sent to the AI provider the user configures. Add **Name**, **Email address** or **User IDs** only if you decide reviewer names, GitHub logins and mailbox usernames count. Google's list has no "authentication credentials" type; credentials go only to their own service and are not declared as a type. |
| Optional | Yes. AI is off until the user picks a provider; auto-triage is off by default. |
| Purpose | App functionality |
| Processed ephemerally | **Do not claim it.** The exemption fits data held only in memory; what a user-chosen vendor retains is not ours to promise. |
| Shared | **Recommend Yes** (AI model provider, the user's choice) while the background auto-triage switch ships. **No** is defensible only if the manual AI buttons rely on Google's user-initiated-action exemption *and* the switch gets a prominent in-app disclosure with consent (defect 4). |
| Encrypted in transit | **Yes** for all HTTPS traffic. `http://127.0.0.1` AI traffic never leaves the phone. **Not for IMAP** until defect 3 is fixed, or IMAP is cut from the Play build. |
| Data deletion | There is no account and the developer holds no data. In the app, users can remove credentials, mailboxes and project sources. All stored content is removed by Android "Clear storage" or uninstall; files the user saved or shared, and grants at the issuer (GitHub, Google, the mail host), are not. Say **"Users can delete data in the app"** only after a delete control ships (defect 6). |

A Play build **without** cloud AI (ASOM and local presets only) sends nothing except
operator-credentialed calls to Google Play, GitHub and the mail host, plus the
user-confirmed reply to Google. Google's pages give no explicit exemption for that either;
the nearest text is the FAQ about a user uploading to their own Drive or Dropbox, which
requires that the app "never collects or accesses" the data and so does not fit a client
that reads it. Declare conservatively and re-read the live form wording.

This deliberately diverges from the sibling BYO-key AI sheets (Fyl-Manager, Foto-Xplorr),
which answer "No" for optional AI. Here the text belongs to third parties (customers,
reviewers, correspondents), and there is a background mode. Treat it as a conservative
owner decision, not a finding.

Other App content answers are the house defaults: ads **No**, Advertising ID **No**,
target audience **18 and over**, news / COVID / government / financial / health **No**.

## Permissions

| Permission | Source | Why |
|---|---|---|
| `INTERNET` | app manifest | Play Developer API, GitHub API, your mail server (IMAP) or Gmail, Google sign-in and token endpoints, and the AI provider you choose |
| `ACCESS_NETWORK_STATE` | app manifest | Used through WorkManager's connected-network constraint. No app code reads network state, and pull-to-refresh does not check connectivity. |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | merged from `work-runtime` 2.9.1 | The periodic sync. Seen in the debug APK; the release manifest has not been built. |
| `com.mbaliga.csapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | merged from `androidx.core` 1.13.1 | App-private signature permission |

All are normal permissions: no declaration form and no demo video. No foreground service
type is set and nothing starts the foreground service, the boot receiver is disabled,
there are no notifications (no `POST_NOTIFICATIONS`), and there is no `AD_ID`. Re-check on
the release AAB: the debug APK also carries `debuggable` and the exported
`PreviewActivity`, which should be absent from release.

Exported app components: `MainActivity` (launcher only, ignores its intent) and
`OAuthRedirectActivity` (custom scheme `com.mbaliga.csapp`; the code checks a random
`state` and uses PKCE, so a hijacked redirect cannot redeem the code). The `FileProvider`
is not exported.

## Target API and platform behaviour

- `targetSdk 36` meets Google's rule (new apps and updates must target API 36 from
  2026-08-31; help answer 11926878, fetched 2026-11-04). The extension to 2026-11-01 has
  lapsed. Re-check at submit time.
- 16 KB pages: the only native library is `libandroidx.graphics.path.so`; its segments and
  zip offsets are 16 KB-aligned in the debug APK. Confirm on the release AAB.
- Target-36 behaviour to check on a device (read from source only): there is no
  edge-to-edge handling, the theme's `statusBarColor` and `navigationBarColor` are ignored
  at target 35 and up, and the window theme is dark Material while Compose switches to a
  light scheme, so light-mode system-bar icon contrast is likely wrong. Predictive back is
  on by default at 36, and the crash-recovery screen's `onBackPressed` override is then not
  called, so its detail pane would not step back.

## Content rating, UGC and AI content

- Questionnaire: the house default. Category `Utility, Productivity, Communication, or
  Other`; answer **No** to user-generated content sharing and interaction. The old
  "Yes, in a limited sense" is not a valid yes/no answer.
- That **No** is a judgment, not a Google finding. Google's UGC policy also reaches
  "specialized browsers or clients to direct users to a UGC platform" (help answer
  9876937), and CS App is a client for GitHub issues and Play reviews. It has no posting,
  no sharing between users and no report or block UI, and shows fetched text only to the
  one operator. The IARC wording is visible only in Console; the rating is IARC's output.
  Expect Everyone, do not promise it.
- AI-generated content policy: Google requires in-app reporting or flagging for apps that
  generate content with AI, and scopes out productivity apps that use AI to improve an
  existing feature, such as AI-suggested email drafts. CS App's AI only writes pending
  suggestions a human accepts, and there is no report or flag UI. Google's pages show no
  separate generative-AI declaration. Unresolved; check what Console asks.

## Gmail

- The scope is `gmail.readonly`, a Google **restricted scope**. The user supplies their
  own OAuth client ID (the app ships none). Google's API Services User Data Policy allows
  transfer of such data only to provide user-facing features that are visible in the UI,
  with consent. CS App forwards the first 600 characters of mail subject and body to
  third-party AI providers, including through the background switch. Read that policy
  before any public listing that includes Gmail.
- The redirect `com.mbaliga.csapp:/oauth2redirect` is unverified (`VERIFICATION.md` row 5).
  Google may require the reversed-client-id scheme for Android client types.
- A user-owned client in "Testing" status gets short-lived refresh tokens (about 7 days;
  check Google's current terms).
- **Recommendation:** leave Gmail out of the first Play build and ship IMAP only. The
  Gmail sentences in the listing and policy are separable.

## Listing gate

`HOUSE_DEFAULTS.md` 4.3 rule 4 says never claim behaviour that is not device-verified, and
leave it out rather than hedge. All 14 verification rows are unticked, so the copy in
`fastlane/` is drafted and ready, not cleared. Do not upload until the matching row is
ticked, or cut the sentence.

| Listing claim | Cleared by `VERIFICATION.md` row |
|---|---|
| GitHub issues | 2 |
| Play reviews | 3 |
| IMAP mail | 4 |
| Gmail (cut if unticked) | 5 |
| Reply and edit reply | 6, 7 |
| On-device and cloud AI | 8, 9 |
| Export | 10 |
| Background sync | 14 |
| Crash recovery screen | 12 |
| Monthly CSV import | **no row exists. Add one:** only synthetic UTF-16 fixtures have been parsed, never a real Play export |

## Pre-submit checklist

- [ ] Decide the product name; set `app_name` and `title.txt` together
- [ ] Decide public listing or internal track; confirm account type and closed-test needs
- [ ] Decide the Play build's scope: cloud AI, background auto-triage, Gmail
- [ ] Fix or cut defects 1 to 6, or keep the weaker wording in this sheet and the policy
- [ ] Initialise the submodule; confirm the `4a8b0a3` pin fetches; set `CORE_SUBMODULE_TOKEN` if needed
- [ ] Create the upload keystore, set the four `CSAPP_KEYSTORE_*` values, run `./gradlew :app:bundleRelease`
- [ ] Inspect the release AAB: merged manifest, R8 output, prompts present, 16 KB alignment
- [ ] Run `VERIFICATION.md` on a phone; tick the rows the listing gate needs; add the CSV row
- [ ] Render the 512 px icon and the 1024x500 feature graphic; take screenshots with fabricated or anonymised text
- [ ] Host `privacy-policy.md` at the privacy URL; confirm 200 and that the alias delivers
- [ ] Prepare reviewer credentials and a plan for the Play reply path
- [ ] Re-check Google's target-API rule and the live Data safety wording at submit time

## Stale elsewhere (not changed here)

`Personal-Tracker`: `NAMES.md` (CS App row says the source never compiled),
`store/APP_MATRIX.md` (csapp "0.1.0 (1), Unbuildable"), `store/RUNBOOK.md` (csapp
targetSdk 34), `store/HOUSE_DEFAULTS.md` 3.8 ("every network call in every app is HTTPS"
is false for csapp) and the section 2 csapp exception (omits mail and cloud AI).
This repo: `docs/PRODUCT-AND-ARCHITECTURE.md` (0.1.0 / targetSdk 34 and
`EncryptedCredentialStore`), `docs/PORTING-PLAN.md`, `docs/STATUS.md` ("R8 release
assembles"), `docs/VERIFICATION.md` (names `csapp-debug.apk`), `BUILD-SPEC.md` (IMAP
description).

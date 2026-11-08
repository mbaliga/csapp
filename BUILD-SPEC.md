# csapp (Customer Success app) — Production Build Spec for Claude Code

> Paste this file into the repo root of `mbaliga/csapp` as `BUILD-SPEC.md` and start the session with: *"Read BUILD-SPEC.md. Execute WP0, stop at its gate."* One WP per session.
>
> Session settings: **model `sonnet`**, **`/effort high`** (WP3 email ingestion and WP4 AI may use `xhigh`). Plan mode for the first 10 minutes of each WP. Run `/code-review` before claiming a WP done.

---

## 0. What you are building, in one paragraph

csapp is a phone-first support console for a developer who ships many apps. It ingests **Play Store reviews, GitHub issues and support email** across a **portfolio of projects**, deduplicates and clusters them into incidents deterministically, lets the developer **triage with AI suggestions they must accept by hand**, **reply to Play reviews with human confirmation only**, and **exports `issues-manifest.v1.json`** per project in the exact shape Fonebrew Core's import lane validates. It has no backend, no telemetry, no automatic sends, and every cloud call is a visibly watched object.

A working V1 is already merged on `main` (Play + GitHub ingestion, Jaccard clustering, Play reply with confirm dialog, SAF export; 35 JVM tests). An unmerged branch adds the ratified export shape, crash-recovery and a toolchain bump. What is missing: a device-breaking threading bug fix, email, multi-project, AI, reply governance, Hyle-grade UI, store readiness. That is this spec.

---

## 1. Hard rules (read before every WP)

1. **Nothing is sent to a user without an explicit human confirmation.** There is exactly **one** send call site (`PlayReviewsApiClient.sendReply`), reached only from `ReplyViewModel.confirmAndSend` after the confirm dialog. Keep the existing grep-style test idea and make it real: `SingleSendSiteTest` fails if `:reply` or `sendReply(` appears anywhere else in `src/main`.
2. **AI suggests, never decides.** Severity, title, cluster membership, reply text: the AI writes a *suggestion* object with provenance; the user taps Accept to apply. No suggestion is ever applied by a worker, on ingest, or on app open. Auto-reply is not a feature and must not be built, even behind a flag.
3. **Clustering is deterministic.** Re-running never mints a new id or shuffles assignments (`AnchorIdGenerator`, `ClusteringEngineTest`). AI-assisted merge proposals are suggestions (rule 2) and are applied through `IncidentRepository.merge`, which is deterministic.
4. **`sourceKey` is identity.** Dedupe across runs and channels keys on it; never infer identity from title or author.
5. **No background sync into Fonebrew; file export only, user-initiated** (Core's INT-001/INT-003). csapp never writes Tasks or Incidents anywhere but its own DB.
6. **csapp does not read Assay output.** Core is the single merge point for CSAPP and ASSAY incident sources (INT-016). Do not add scanner findings as a signal type.
7. **No telemetry, no analytics, no phoning home.** Network calls: Play Developer API, GitHub API, the user's own mail server / Gmail API, and the model provider the user configured. Each provider is a watched object in the UI (glyph + label). Credentials in the Android Keystore only; never logged; sent only to their own host.
8. **Linked libraries permissive-only** (Apache/MIT/BSD/ISC/CC0). No Jakarta Mail / JavaMail (EPL/GPL). Email is implemented with an in-house minimal IMAP client and the Gmail REST API over plain HTTP (§3.4).
9. **Colour never carries meaning alone** (the owner is red-green colourblind). Severity and status: glyph + label + luminance on the Hyle palette (§6). The current green Material scheme is replaced.
10. **Anti-overclaim ladder** in every WP report: `code-complete` / `verified` / `ready-for-owner-review`. On-device behaviour is **owner-verified only**; mark `NEEDS-DEVICE-VALIDATION`. Stubbed, not faked: a path that cannot run here fails with a typed error, never a fake success.
11. **Renamed (owner decision, Nov 2026).** The product is **Ping**; `applicationId` and package are `com.asoc.ping` (was `com.mbaliga.csapp`, never published). Internal identifiers deliberately stay `csapp` (repo and Gradle root name, `csapp.db`, prefs and keystore names, hub-contract `producer.app`). The product name lives only in the string resource `app_name`.
12. **Do not add the Hyle Design System submodule.** Copy token values into `ui/theme/HyleTokens.kt` (§6). `shared-libraries` submodule stays, used only for `dev.aarso:crash-recovery`.

---

## 2. Owner-supplied inputs (ask at WP0 start; record `OWNER-ACTION-NEEDED` if absent, never block)

| Input | Used by | If absent |
|---|---|---|
| **2.1** `CORE_SUBMODULE_TOKEN` repo secret (fine-grained PAT, Contents:read on `mbaliga/Shared-Libraries-asoc`) | WP0 CI | CI red at checkout; record and continue locally |
| **2.2** Licence decision: keep **Apache-2.0** (default) or merge `origin/claude/asystemofcells-redesign-s6yv4c` (PolyForm Noncommercial 1.0.0 + COMMERCIAL-LICENSE.md, Oct 8) | WP0 | Keep Apache-2.0; leave branch unmerged, note it |
| **2.3** Play service-account JSON, GitHub PAT, support mailbox credentials (IMAP host/user/app-password, or Gmail OAuth client ID) — entered on device, never in repo | device validation | Use MockWebServer / fixture mailboxes |
| **2.4** Model key(s) for AI (optional; ASOM local default) | WP4 | ASOM-only; cloud "not configured" |
| **2.5** Signing keystore | WP6 | Debug-signed sideload build; signing `OWNER-ACTION-NEEDED` |
| **2.6** Support address to monitor: default `support@asystemofcells.com` | WP3 | Field left empty in settings |

---

## 3. Architecture decisions (final — do not re-derive)

### 3.1 Trunk = `main` after WP0; branches to land

Base `main` (Sep 16, V1). Land in this order: `claude/fonebrew-development-clzu43` (clean; brings ratified export, crash-recovery, AGP 8.9.1/Gradle 8.14.3/Kotlin 2.1.0, setup-android v4) → `claude/multi-platform-porting-plan-nnvjug` (resolve `ci.yml` by keeping main's; patch its stale toolchain facts) → licence branch only per §2.2 → `claude/default-store-listings-u991hc` (fix the false "never compiled" header first). Bump Kotlin to **2.1.20** (owner's staged-pin ruling), `compileSdk/targetSdk 36`, `minSdk 26` stays. Replace deprecated `security-crypto 1.1.0-alpha06` with a thin in-house `KeystoreSecret` (AES-GCM key in AndroidKeyStore, values in private SharedPreferences) — same pattern as Fonebrew Core.

### 3.2 Threading (the P0 bug)

`PlayReviewRepository`, `GitHubIssuePollRepository`, `IssuesManifestExporter` and `PlayServiceAccountAuth` do blocking I/O; the ViewModels call them from `viewModelScope` on Main. Make every repository method `suspend` and wrap I/O in `withContext(Dispatchers.IO)`; inject dispatchers via `AppContainer` so tests use `StandardTestDispatcher`. Add `MainThreadNetworkTest` (Robolectric + StrictMode `detectNetwork().penaltyDeath()` around each ViewModel action).

### 3.3 Multi-project data model

New Room entities (schema v2 with a migration from v1; `exportSchema = true`, schemas committed under `app/schemas/`):

- `ProjectEntity(id: String /*slug*/, name, externalId /*"github:o/r" if exactly one repo else "csapp:<slug>"*/, createdAt, archived)`
- `ProjectSourceEntity(id, projectId, kind: GITHUB_REPO|PLAY_PACKAGE|MAILBOX_FILTER, value, enabled)` — e.g. `owner/repo`, `com.foo.app`, `subject contains "[Fotoz]" OR to:fotoz@…`
- `SignalEntity` + `projectId` (indexed); `IncidentEntity` + `projectId`.
- Credentials are account-level (one GitHub PAT, one Play service account, one or more mailboxes), sources are project-level. A mailbox filter routes mail to a project; unmatched mail lands in a built-in **Inbox** project the user can reassign from.
- Checkpoints: keyed as today (`repoFullName`, `packageName`) plus `mailboxId+folder`.
- Clustering runs **per project**; `AnchorIdGenerator` input gains the project slug prefix so ids cannot collide across projects (`inc_<slug16>_<hash16>`; keep old ids via migration by assigning all existing rows to a project created from the current single-settings pair).

### 3.4 Email ingestion

Two connectors behind one `MailSource` interface:

- **`ImapClient`** (in-house, ~400 lines): TLS socket, `LOGIN`, `SELECT`, `UID SEARCH SINCE <date>`, `UID FETCH (BODY.PEEK[HEADER.FIELDS (…)] BODY.PEEK[TEXT])`, `IDLE` not used. Handles RFC 2047 header decoding, quoted-printable/base64 bodies, `text/plain` preferred, `text/html` stripped to text, attachments ignored (names recorded in `metadataJson`). App-password auth only; OAuth for non-Gmail is out of scope.
- **`GmailApiClient`**: OAuth 2.0 PKCE on-device (AppAuth-Android is Apache-2.0 and acceptable, or hand-roll with Custom Tabs), scope `gmail.readonly`; `users.messages.list?q=…` + `messages.get?format=full`. Refresh token in Keystore.
- Both produce `Signal(type = EMAIL, sourceKey = "mail:<mailboxId>:<rfc822-message-id>")` with the thread id in `metadataJson`; replies in the same thread attach to the same signal's `thread` rather than creating new signals (dedupe on `Message-ID`, cluster on `In-Reply-To`/`References`).
- `SignalType.EMAIL` and `SignalType.MANUAL` both become creatable; the manual-signal form (today missing) is added.
- Replying to email from the app is **out of scope** in this spec (rule 1 applies to Play only). Provide a "Reply in mail app" intent with the thread pre-filled.

### 3.5 AI layer (`domain/ai/`)

- `ModelProvider` interface: `complete(system, user, params): Completion(text, provenance LOCAL|CLOUD(providerId), model)`. Implementations: `OpenAiCompatProvider` (presets: **ASOM** `http://127.0.0.1:11435/v1`, DeepSeek, Grok, OpenAI, Gemini-via-OpenAI-compat, Llama/any local server) and `AnthropicProvider`. Plain OkHttp, no vendor SDK. Keys via `KeystoreSecret`; assert request host == provider host.
- Tasks, each returning a `Suggestion` row (new `SuggestionEntity`: `id, targetType INCIDENT|SIGNAL, targetId, kind, payloadJson, provenance, model, promptVersion, createdAt, state PENDING|ACCEPTED|REJECTED`):
  - `triage(incident)` → severity + one-line title + two-sentence summary + reasons.
  - `mergeCandidates(project)` → for each new incident, the top-5 Jaccard neighbours (existing engine) are passed to the model, which returns `same-issue / different` with a reason; accepted ones call `IncidentRepository.merge`.
  - `draftReply(signal)` → ≤350 chars, in the review's language, no promises of dates/refunds, no PII echo; stored as a suggestion, not as `replyDraft`, until accepted.
  - `digest(project, since)` → the "what happened this week" text for the dashboard header.
- Prompts versioned under `src/main/resources/prompts/`. A suggestion always shows its provenance glyph (local glow vs cloud glow) and model name. "Run triage" is a tap, or an opt-in per-project automation that runs **after ingest** in the worker and only writes `PENDING` suggestions.
- If no provider is configured, every AI entry point is a disabled control with an info tip; nothing else changes.

### 3.6 Reply governance (Play)

- `replyText.length ≤ 350` enforced in the UI and in `sendConfirmedReply` (typed error).
- Ingest `developerComment` from `reviews.list` into `SignalEntity.sentReplyText/sentReplyAt` so replies made in Play Console appear; a SENT signal's button reads **Edit reply** and re-confirms with a diff of old vs new.
- Append-only `ReplyLogEntity(signalSourceKey, textSent, sentAt, outcome, httpStatus)` written at the single send site.
- Quota guard: a per-package token bucket (defaults 150 GET/hour, 1500 POST/day — below Google's published 200/2000) persisted in Room; exceeding it blocks with a message, never silently drops.
- Show `editedAt` ("review edited after your reply") in detail and reply screens.
- Use the Play checkpoint (`lastSeenReviewSubmitMillis`) to skip already-seen reviews; keep the one-week window as a safety overlap.
- Backfill from the monthly CSV gets its UI (SAF picker on the project screen) — the parser already exists and is tested.

### 3.7 Export (ratified)

- File name **`issues-manifest.v1.json`**, one per project, `projectRef.externalId` from `ProjectEntity.externalId`.
- Deterministic bytes so Core's whole-file digest can detect `IMPORT_DUPLICATE_SNAPSHOT`: `exportedAt` = max(`updatedAt`) over exported issues (or project `createdAt` if none); `exportId` = `sha256(canonical JSON without exportId)` hex. Document this in `docs/PRODUCT-AND-ARCHITECTURE.md` and close the open owner question there.
- Exclude `DISMISSED` and `MERGED` incidents (the survivor carries them); `status` stays descriptive free text.
- `sourceRevision` = the project's newest signal `sourceUpdatedAt` ISO string (so an edit after import shows as *changed*).
- Keep `HubContractConformanceTest`; add real JSON-Schema validation at export time with `networknt/json-schema-validator` (Apache-2.0) against `app/src/test/resources/hub-contract/issues-manifest.v1.schema.json` (move the schema to `app/src/main/resources/`).
- Transport stays SAF file + system share sheet ("Share to Fonebrew"); no IPC.

### 3.8 UI

Compose + Material3 stays; theme rebuilt on Hyle tokens (§6), dark primary. Screens:

- **Home**: project switcher (chips), AI digest line (if configured), counts by severity (glyph+label), "Needs you" list (new signals, pending suggestions, failed sends).
- **Incidents** (per project): filter by status/severity/channel, sort by updated/severity/signals; swipe = acknowledge/dismiss with undo. Merged/dismissed hidden by default.
- **Incident detail**: suggestions panel (accept/reject each), severity/status controls, signals list with channel glyphs, **merge picker** (search by title, replacing the type-the-id field), **split** (multi-select signals → "Split out"), recurring-of picker.
- **Reply**: counter 0/350, language auto-detect label, AI draft button (if configured), confirm dialog with the exact text, send result, reply log.
- **Projects**: CRUD, sources per project, mailbox filters, backfill picker, per-project export.
- **Settings**: credentials (masked, with **Clear**), mailboxes, AI provider (with watched-object badge), polling interval (15/30/60 min; workers keep CONNECTED constraint; add backoff to the Play worker), About, crash-recovery preview (debug).
- Explanatory copy behind info icons; no decorative lines; generous whitespace.

---

## 4. Work packages (in order; each ends at a gate)

Each WP: read the named files; implement; run gates unpiped with `LANG=C.UTF-8`; add a dated row to `docs/STATUS.md` (ladder + evidence); commit on `main` via a PR branch `wp<n>/<slug>`; stop and report with §7's format. Never start the next WP in the same session.

### WP0 — Land the branches, fix the threading bug, lock the toolchain (effort: high)

Read: `docs/PRODUCT-AND-ARCHITECTURE.md`, `.github/workflows/ci.yml`, `settings.gradle.kts`, `gradle/libs.versions.toml`, all `ui/**/*ViewModel.kt`, `data/play/PlayReviewRepository.kt`, `data/github/GitHubIssuePollRepository.kt`, `data/export/IssuesManifestExporter.kt`.

1. Merges per §3.1 (ask §2.2 first). Rewrite the stale header in `docs/store/play-console.md`; patch stale toolchain facts in `docs/PORTING-PLAN.md`.
2. §3.2 threading fix + `MainThreadNetworkTest`.
3. Toolchain: Kotlin 2.1.20, KSP matching, compileSdk/targetSdk 36; replace `security-crypto` with `KeystoreSecret`; add `exportSchema = true` and commit the v1 schema JSON before any entity change.
4. `metadataJson` built with `kotlinx.serialization` (fix the unescaped interpolation). `PlayServiceAccountAuth` token cache keyed by the key's `client_email`. Mask the service-account field; add **Clear credentials**.
5. Add `SingleSendSiteTest` (rule 1). Add tests for `PlayReviewsApiClient` (list + reply POST via MockWebServer) and `PlayReviewRepository.sendConfirmedReply` success/FAILED.
6. `CLAUDE.md` (≤40 lines) and `docs/STATUS.md`.

**Gate:** `./gradlew testDebugUnitTest lintDebug assembleDebug` green locally and on CI (`CORE_SUBMODULE_TOKEN` present, else `OWNER-ACTION-NEEDED`); test count ≥ 55. Ladder: `code-complete`.

### WP1 — Multi-project (effort: high)

Read: `data/db/**`, `domain/clustering/**`, `domain/repository/**`, `work/**`, `data/settings/**`.

1. §3.3 entities, Room v2 migration (tested with `MigrationTestHelper` under Robolectric), project-scoped DAOs, worker fan-out over enabled sources per project.
2. `AnchorIdGenerator` project prefix; migration assigns existing rows to the migrated project; `ClusteringEngineTest` extended for isolation across projects.
3. Settings → Projects screen (§3.8) and project switcher on Home/Incidents. Export per project.

**Gate:** migration test green; clustering determinism tests green across two projects; CI green. Ladder: `code-complete`.

### WP2 — Reply governance + backfill UI + checkpoint fix (effort: high)

Read: `data/play/**`, `ui/reply/**`, `ui/incident/**`.

§3.6 in full, plus the split UI and the merge/recurring pickers from §3.8. Tests: 350-char rejection, quota bucket, reply log written exactly once per send, Edit-reply diff, `developerComment` ingestion, checkpoint skip. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION` (one real reply on a test review).

### WP3 — Email ingestion (effort: xhigh)

Read: `data/github/**` (follow its client/repository/worker pattern), `domain/model/Signal.kt`.

§3.4. Tests: `ImapClient` against a scripted fake IMAP server (socket-level fixtures for LOGIN/SELECT/SEARCH/FETCH, literals, RFC 2047, quoted-printable, multipart); `GmailApiClient` with MockWebServer; thread grouping; mailbox-filter routing to projects; Inbox fallback. Manual signal form. "Reply in mail app" intent. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION` (real mailbox).

### WP4 — AI suggestions (effort: xhigh)

Read: `domain/clustering/**`, `ui/incident/**`, `ui/reply/**`, `ui/dashboard/**`.

§3.5 in full. Tests: provider clients with fake servers; `SuggestionNeverAutoAppliedTest` (a worker run with a fake model produces only `PENDING` rows and changes no incident/signal field); prompt snapshot tests; 350-char/no-PII post-filter on drafts; provenance always set. UI: suggestions panel, AI draft, digest, provider settings with watched-object badge. Ladder: `code-complete` (fake provider on CI); real ASOM/cloud `NEEDS-DEVICE-VALIDATION`.

### WP5 — Export v1 conformance + Hyle UI pass (effort: high)

1. §3.7: deterministic `exportId`/`exportedAt`, exclusions, `sourceRevision`, schema validation, share sheet. Tests: two consecutive exports with no changes are byte-identical; a changed `updatedAt` changes both `exportId` and bytes; Core's valid/invalid fixtures still pass.
2. §3.8 screens + §6 tokens; Roborazzi screenshot tests for every screen, light/dark, 200 % font; TalkBack descriptions; no colour-only state (test scanning the status/severity composables for glyph+label).

**Gate:** CI green; screenshots committed. Ladder: `code-complete`.

### WP6 — Store readiness and release lane (effort: medium)

1. Real app icon (monochrome + adaptive) and `app_name` from a single resource; `docs/store/*` filled (privacy policy: no data leaves the device except to the services the user configures; list them).
2. Release build config: minify, signing from env vars, `versionName 1.0.0`; CI uploads debug APK on every push to `main`, release APK on tags when secrets exist.
3. Diagnostics: a debug-only "export diagnostics" (logs + DB stats, no bodies) via share sheet.
4. `docs/VERIFICATION.md` checklist with `NEEDS-DEVICE-VALIDATION` rows: install on RedMagic, Play poll, GitHub poll, IMAP poll, Gmail OAuth, one reply, one export imported in Fonebrew (Core's import UI is not wired yet — note it), crash-recovery screen.

**Gate:** CI green; checklist has no empty cells. Ladder: `ready-for-owner-review`.

---

## 5. Definition of "production-ready today" (after WP6)

1. Add projects for every shipped app (Play package + GitHub repo + mailbox filter), one PAT, one service account, one mailbox.
2. Pull to refresh or wait 30 min: reviews, issues and support mail appear as incidents per project; AI (if configured) proposes severity/title/merges with provenance; you accept with a tap.
3. Reply to a review: AI draft optional, 350-char guard, confirm the exact text, logged; edited reviews flagged.
4. Export `issues-manifest.v1.json` for a project; exporting again with no changes yields identical bytes.
5. Everything above still works with AI unconfigured.

---

## 6. Design tokens (`ui/theme/HyleTokens.kt`; no Hyle submodule)

- Ground `#07070A`; violet `#8E7BFF` primary; radium `#CCEC75` = on-device/local provenance; alien `#3ED4FF` = cloud provenance. Cyan `#08FED5` retired. Pure black only for pixels-off.
- Severity: CRITICAL ◆, HIGH ◇, MEDIUM ▲, LOW ○ — always with the word; luminance 100/80/60/40 %. Status: OPEN solid ring, ACKNOWLEDGED half ring, RESOLVED check, DISMISSED ✕, MERGED ⇢ — always with the word. Channel glyphs: ★ Play, ◉ GitHub, ✉ mail, ✎ manual.
- Minimal, whitespace-forward, no boxy grids, explanatory copy behind info icons, no decorative connectors; dark primary, light must pass AA.

---

## 7. Reporting format at the end of every WP

```
WP<n> — <title>
Ladder: code-complete | verified | ready-for-owner-review
Gate commands (unpiped, LANG=C.UTF-8): <cmd> → exit <code> …
Evidence: <CI run URL | test names | screenshot paths>
NEEDS-DEVICE-VALIDATION: <bullets or "none">
OWNER-ACTION-NEEDED: <bullets or "none">
Not done / deferred: <bullets>
Next: WP<n+1> (do not start)
```

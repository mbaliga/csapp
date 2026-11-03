# csapp — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row. csapp has no STATE/PROGRESS file and no decision register, so
> proposals appear only in §8, labelled PROPOSAL, and gate output is pasted into each step's PR until
> the owner names a progress file (Q11).
>
> Baseline read: `main` at `b38b3a5`, 2026-10-06. Targets, in the owner's order: Ubuntu Touch → Linux
> desktop → iOS/iPadOS → macOS → Windows. Build order differs from validation order (§5).

## 0. Evidence labels (never dropped)

asom's set, unchanged: `LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` ·
`CI-APPROX — NOT DEVICE EVIDENCE` · `SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` ·
`SYNTHETIC` · `CI-ONLY / NOT RUN` · `NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV).
This program's additions: `PLAN` · `NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` (this container: JVM x86_64
compile/tests, nothing else) · `BROWSER-HEADLESS`.

Labels csapp expects to use: PLAN, CI (hosted VM) evidence, SIMULATOR, CI-APPROX, NDV, NOV, NOT-APPLICABLE,
CONTAINER-BUILD-ONLY.

Today the repo's only evidence is `CI (hosted VM) evidence` for Android unit tests, lint and a debug assemble
(`.github/workflows/ci.yml`); this plan did not check that CI is currently green, and csapp has never been
run on a device in anything the repo records. The build container has no device, emulator, Xcode or
Clickable (program §9), so nothing below can be confirmed here.

## 1. What this repo is, in porting terms

- **Product.** A customer-support console for Android app developers: polls Play Store reviews (service-account
  RS256 JWT) and GitHub issues into a local Room database, clusters them deterministically into incidents,
  lets a human triage, reply to Play reviews behind a two-step confirmation, and export a diffable
  `issues-manifest.json` v1 to a user-picked destination (`docs/PRODUCT-AND-ARCHITECTURE.md` §1, §4).
- **State and targets.** Seeded: `0.1.0`, versionCode 1 (`app/build.gradle.kts`); V1 landed in PR #8, then a docs PR (#9)
  and CI storage chores. Android is the only target; there is no README, STATE, PROGRESS or device checklist,
  and no port work of any kind (`settings.gradle.kts` includes only `:app`). The one forward-looking sentence is
  in `docs/PRODUCT-AND-ARCHITECTURE.md` §5: the app "predates the constellation's spatial shell".
- **Stack.** Kotlin 2.0.20 (JVM 17) · Jetpack Compose (BOM 2024.09.02), Material 3, Navigation Compose 2.8.0 ·
  Gradle 8.9, AGP 8.5.2, KSP 2.0.20-1.0.25, one module `:app` · Room 2.6.1 (4 entities, 3 DAOs, schema v1,
  `exportSchema = false`) · WorkManager 2.9.1 · `androidx.security:security-crypto` 1.1.0-alpha06 · OkHttp 4.12.0 ·
  kotlinx-serialization 1.7.1, kotlinx-coroutines 1.8.1 · JUnit 4, MockWebServer. minSdk 26, targetSdk 34.
  No native code, no Google Play Services dependency, no Hyle, no cell-shell (`gradle/libs.versions.toml`).
- **Size** (run 2026-10-06 at `b38b3a5`):
  `find app/src/main -name '*.kt' | wc -l` → 55 · `find app/src/main -name '*.kt' -print0 | xargs -0 cat | wc -l` → 3,173 ·
  `find app/src/test -name '*.kt' | wc -l` → 9 (720 lines) · `grep -rho '@Test' app/src/test | wc -l` → 35.
  The tests import nothing from `android.*`/`androidx.*` and run on a bare JVM today.

## 2. Portable core vs platform-bound layers

Portability: **common** = no imports, moves to `commonMain` unchanged · **JVM** = pure Kotlin with a few
`java.*`/OkHttp types, fine on every JVM target and needing a seam for Kotlin/Native · **Android** = needs an
actual per platform. LOC are measured per directory under `app/src/main/java/com/mbaliga/csapp/`.

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `domain/model` | `Signal`, `Incident`, four state enums | common | 101 | No imports |
| `domain/clustering` | `ClusteringEngine`, `TextSimilarity`, `AnchorIdGenerator` | JVM | 218 | Only `MessageDigest` (`AnchorIdGenerator.kt:3`) and `TimeUnit` (`ClusteringEngine.kt:7`) are JVM-only; determinism-critical |
| `domain/repository` | `SignalRepository`, `IncidentRepository`, `EntityMapping` | JVM, tied to Room types | 330 | Imports `data.db.dao.*` and `data.db.entities.*` directly; tests use `FakeSignalDao`/`FakeIncidentDao` |
| `data/github` | OkHttp poller (since + ETag), DTO, checkpoint rewind | JVM | 224 | OkHttp, `java.time` |
| `data/play` | RS256 JWT auth, reviews API, CSV parser, repository | JVM + one Android import | 566 | `android.util.Base64` at `PlayServiceAccountAuth.kt:3`; `java.security` for RS256 |
| `data/export` (model, builder) | `IssuesManifestV1`, deterministic builder | JVM | 96 | `java.time.Instant` |
| `data/export` (exporter) | Writes to a SAF `Uri` | Android | 32 | `ContentResolver`, `Uri` |
| `data/credentials` | `CredentialStore` interface (20) + `EncryptedCredentialStore` (58) | interface common, impl Android | 78 | Keystore-backed `EncryptedSharedPreferences` |
| `data/settings` | `AppSettingsStore` (owner/repo, package name) | Android | 31 | Plain `SharedPreferences` |
| `data/db` | Room `AppDatabase` v1, entities, DAOs | Android until Room 2.7+ | 216 | Plain annotated interfaces, no TypeConverters |
| `di` | `AppContainer`, manual DI | Android | 76 | Takes `Context`; builds Room, OkHttp, exporter |
| `work` | `PollScheduler` + two `CoroutineWorker`s | Android | 100 | 30-minute periodic, network constraint |
| `ui` | Six screens, ViewModels, `CsAppNavHost`, theme, `MainActivity` | Android (androidx.compose; CMP equivalents exist) | 1,088 | Hard Android deps are `ExportScreen.kt:25-26`, `ExportViewModel.kt:3`, and the `android.net.Uri.encode`/`decode` calls at `ui/nav/Destinations.kt:12` and `ui/nav/CsAppNavHost.kt:70` |
| `CsAppApplication.kt` | Builds the container, schedules polls | Android | 17 | Plus `AndroidManifest.xml` and five res files |
| tests | 35 tests, 7 classes, 2 fakes | JVM | 720 | No Robolectric; no `androidTest` sources despite declared deps |

Six Android-bound seams outside `ui/` and `data/db/` total 314 measured lines (application, credentials impl,
settings, exporter, container, work); the program's §5 row rounds the Android-bound surface to about 400.

| Platform API | Where | Porting impact |
|---|---|---|
| `EncryptedSharedPreferences` + `MasterKey` | `data/credentials/EncryptedCredentialStore.kt`, injected at `di/AppContainer.kt:28` | One actual per platform behind `CredentialStore`; the main dependency (F6, §7) |
| Room 2.6.1 + KSP | `data/db/**`, builder at `di/AppContainer.kt:32-34`, DAOs consumed by `domain/repository/*` | Room 2.7+ is KMP with the same annotations; needs KSP/AGP above today's pins; first schema touch needs a migration |
| WorkManager | `work/*`, scheduled in `CsAppApplication.kt:15` | Desktop: in-process ticker; iOS: opportunistic only; Ubuntu Touch: none |
| SAF `CreateDocument` + `openOutputStream(uri, "wt")` | `ui/export/ExportScreen.kt:25-29`, `ExportViewModel.kt:3,41`, `IssuesManifestExporter.kt` | Replace `Uri` with a sink obtainable only from a platform picker |
| `android.util.Base64` | `PlayServiceAccountAuth.kt:3,93,98` | `java.util.Base64` on JVM targets; a common choice is part of the pin ruling |
| `java.security` RSA/SHA-256 | `PlayServiceAccountAuth.kt`, `AnchorIdGenerator.kt`, `SignalRepository.kt`, `PlayReviewRepository.kt` | Fine on JVM; Kotlin/Native needs an actual (CryptoKit/SecKey or a library) |
| OkHttp, `java.time`, `InputStream` | `data/github`, `data/play`, `data/export` | Unchanged on JVM; iOS needs Ktor, kotlinx-datetime, kotlinx-io |
| `ViewModelProvider.Factory` | `ui/ViewModelFactory.kt`, `ui/reply/ReplyViewModel.kt:74-83` | Uses `Class<T>`/`isAssignableFrom` reflection; becomes `viewModel { }` initializers for iOS |
| Manifest posture | `AndroidManifest.xml` | Not an API but a rule to reproduce per platform (§3) |

## 3. Binding rules this port must not break

csapp has no CLAUDE.md or invariants file; these come from `docs/PRODUCT-AND-ARCHITECTURE.md`, from KDoc in the
code, and from the program's directives. Where a rule is only a program directive, it says so.

- **Nothing is sent to a user without explicit human confirmation.** `ReplyState.SENT` has one producer,
  `SignalRepository.markReplySent` (`domain/repository/SignalRepository.kt:80`), called from one place,
  `PlayReviewRepository.sendConfirmedReply` (`data/play/PlayReviewRepository.kt:95`), reached only through
  `ReplyViewModel.confirmAndSend` behind a dialog that shows the exact text (doc §1). A port keeps one send call
  site and the dialog; no other entry point (including any headless one) may reach the send path.
- **Clustering is deterministic and identical on every platform.** Iterate sorted by `sourceKey`; Jaccard over
  stopword-filtered tokens; threshold 0.30; 30-day window; id = `inc_` + first 16 hex of SHA-256 of the anchor's
  `sourceKey`; anchor = earliest by `(createdAt, sourceKey)` (doc §4). `issues-manifest.json` is byte-identical for
  the same data under a fixed clock: the tests pass `nowMillis` (`IssuesManifestBuilderTest.kt:44,66,79`), while a
  real export stamps `generatedAt` from `System.currentTimeMillis()` (`IssuesManifestModel.kt:56`), so two exports
  made at different times differ in that one field. The golden fixtures in §6 therefore fix the clock.
- **Identity and idempotence.** `sourceKey` is identity (`play:<reviewId>`, `github:<owner>/<repo>#<n>`, manual,
  `play-backfill:...`); `originalBodyHash` from first ingestion survives edits (doc §1, §3).
- **Export only to a destination the user picked.** `IssuesManifestExporter` and `ExportViewModel` KDoc: there is
  no automatic or default-path export. Every target keeps a save dialog.
- **Credentials are never persisted in plaintext** (`CredentialStore.kt` KDoc); `allowBackup="false"`,
  `fullBackupContent="false"`; only `INTERNET` and `ACCESS_NETWORK_STATE`. Play auth is "private/dogfood
  service-account style only" (`PlayServiceAccountAuth.kt` KDoc). Each target must keep the database and credential
  material out of cloud and roaming backups (iOS/macOS backup-exclusion attribute, Windows non-roaming profile).
- **No telemetry, no phoning home** (program directive I-1; csapp states it nowhere, but the network calls are
  only GitHub, Google's token endpoint and `androidpublisher`, doc §4). No crash-reporting SaaS, analytics or push
  relay is added by any target. Polling is the user's own sources on the user's own credentials; replies stay gated.
- **Manual DI** (`AppContainer`): "the entire dependency graph is readable end to end in one file" (doc §4). Any
  restructure keeps hand-wired construction; platform differences enter as a small providers object.
- **Standard Material 3 navigation.** Adopting `dev.aarso:cell-shell` is owner-gated (doc §5); the port must not
  pre-empt it and uses plain M3 navigation (Compose Multiplatform's equivalents) throughout.
- **Room schema is v1 with no migrations** (doc §5): the first schema change, including any change forced by a
  Room upgrade, ships a migration, never a wipe.
- **Licence.** Apache-2.0 at root (`LICENSE`); no SPDX headers exist. GPL code is never linked (program I-11).
- **Colour never carries meaning alone** (program I-3). Whether I-3 binds csapp is undecided (OQ-26, OQ-29); the UI
  already complies in practice: severity and status render as text (`DashboardScreen.kt:96`), and the only green is
  the Material primary colour in `ui/theme/Theme.kt`, which carries no meaning. Ports keep it that way, and the
  Ubuntu Touch viewer adds a shape glyph beside each severity word.
- **Registry drift is not propagated.** The repo has no Galaxy Store ingestion (`SignalType` is `PLAY_REVIEW`,
  `GITHUB_ISSUE`, `MANUAL`) and no Fonebrew reference (grep of the tree finds none). No step below depends on either (Q1).
- **Out of scope** (program §0): changing Android behaviour, the package or `applicationId` (`com.mbaliga.csapp`),
  or releasing anything. Android keeps `EncryptedCredentialStore`; security-crypto's alpha status is noted, not acted on.

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | A read-only "manifest viewer" Click (QML + Lomiri.Components) that opens an `issues-manifest.json` v1 through ContentHub; no credentials, no network, no polling, no sending. Foreground-only; not a port of the Android app (R12) | OQ-1 (no UT device on record); F7 and the S-UT1 verdict or waiver; Click name needs a NAMES.md row (OQ-25); confinement removes background work and any keyring | 3 (viewer incl. packaging and policy; a JVM-cored variant is not estimated, Q14) | PLAN |
| Linux desktop | straight | KMP split: `:core` (no UI), `:ui` (Compose Multiplatform), `:app` stays the Android head, `:desktop` Compose Desktop on the JVM; Room 2.7+ KMP with the bundled SQLite driver; actuals for settings, dialogs, ticker, secret store | OQ-17 pins (Room KMP needs a KSP/AGP bump past Kotlin 2.0.20; exact minimum unknown until spike S-CS1); F6 and OQ-22 for credentialed features; OQ-4 format; OQ-5 for device gates | 3 | PLAN |
| iOS / iPadOS | moderate | Same `:core`/`:ui` plus `iosArm64`/`iosSimulatorArm64`, a thin SwiftUI shell in `apple/`; OkHttp to Ktor (Darwin), `java.time` to kotlinx-datetime, RS256 and SHA-256 via a Kotlin/Native actual, Keychain, document picker, opportunistic background refresh plus "Poll now" | OQ-2 (Apple Program, delivery route); macOS CI minutes (OQ-20); Compose Multiplatform iOS needs a Kotlin pin above 2.0.20 (OQ-17); RS256 on Kotlin/Native has no precedent in the constellation; cadence cannot be promised (Q10) | 5 | PLAN |
| macOS | straight | The same Compose Desktop build, `.dmg` via `jpackage` on `macos-latest`; secret store via F6 (data-protection Keychain or a CryptoKit-wrapped file key, not the `security` CLI); backup exclusion for the data directory | OQ-3 (Developer ID, notarisation); OQ-5 (no Mac on record, so every device gate is NOV) | 1 | PLAN |
| Windows | straight | The same Compose Desktop build, WiX MSI on `windows-2025`; secret store via F6 (Credential Manager or DPAPI); data under `%LOCALAPPDATA%` | OQ-3 (signing route; Azure Artifact Signing is unavailable to the owner); OQ-5 (the Dell); R3 path lint first | 1 | PLAN |

Total about 13 engineer-weeks across the five rows (estimates; overlapping, not additive: Linux is the
prerequisite for macOS, Windows and iOS's shared core). Shared F-items (§7) are counted in the program's
Hyle/Shared-Libraries rows, not here. Estimates assume one engineer who knows Kotlin Multiplatform, spikes
passing first time, and exclude owner-side device sessions.

**Ubuntu Touch is a reframe, and the weakest row.** Compose Multiplatform has no Lomiri target, and a bundled JRE
with AWT is not a route (program §4.1). Confined clicks are frozen shortly after losing focus and have no
keystore, which removes the 30-minute polling model and the safe home for the Play service-account key. What
survives honestly is reading an export. Separately, csapp has no Play Services dependency, so the unmodified
APK is a candidate for the program's Waydroid answer (OQ-21; owner-device evidence only, never a port), which would
cost no porting effort here; csapp is not on OQ-21's list today (Q14).

**Linux desktop is arguably the better home for this product.** Long reply drafting, the Play CSV backfill and
working beside GitHub suit a keyboard and a large window. The Play CSV backfill, which the doc calls "a genuine
product requirement", has no UI entry point today: `PlayReviewRepository.backfillFromMonthlyReport(InputStream,
String)` (`data/play/PlayReviewRepository.kt:58`) has no caller in `app/src/main` or `app/src/test`. Wiring it is a
product change, so it is a PROPOSAL (Q3), not a plan step.

**iOS cannot honour the 30-minute cadence.** Background refresh there is opportunistic; "Poll now" becomes the
primary path, and the doc already states there is no push (§5). Push would need a relay, which sits badly with I-1 (Q10).

## 5. Tier and sequencing

**Tier B (port in sequence)**, matching the program's §5 row. csapp is small (3,173 main lines), already layered
with a Kotlin domain, a pure-JVM data layer and 35 bare-JVM tests, so the Android-bound surface is six clean seams.
It is not flagship: it is a seeded V1 with no recorded device validation or users and a stale registry entry. It is a
good low-risk proving ground for the shared secret-store, file-dialog and Room-KMP conventions (Q8).

The program's §5 gate row for csapp, verbatim in substance: registry drift (no Galaxy Store ingestion or Fonebrew
export in the repo; propagated nowhere else) · Room-KMP needs a KSP/AGP bump past Kotlin 2.0.20 (OQ-17) ·
F6/OQ-22 · Hyle status OQ-29. This plan's reading: Q1 changes only the consumer and export story, so it blocks any change to the export
contract or a shared-folder convention, not the restructure; OQ-17 blocks L1 (and spike S-CS1 is the cheapest way to size it);
F6/OQ-22 blocks every step that holds a credential; OQ-29 blocks nothing unless the owner wants Hyle or cell-shell.

| Target | Program wave | Build-entry | Device-entry |
|---|---|---|---|
| Linux desktop (and the macOS/Windows lanes, since csapp's scope already includes them) | **P-LX**, after Typewright's pilot and Clavis, alongside nooz | OQ-17 ruled; F5 for the conventions; F6 where credentials are held; csapp is public, so lanes are not manual-dispatch (OQ-20 still limits artifact storage, R6) | Deck Desktop Mode via `DEVICE_CHECKLIST_LINUX.md`; the Dell only if OQ-5 says Linux |
| Ubuntu Touch | **P-UT b** (JVM-cored QML clicks) | The repo's P-LX core green (R4); S-UT1 passed or waived; F7 | A UT device (OQ-1), else `CI (hosted VM) evidence`/`CI-APPROX` only |
| iOS / iPadOS | **P-iOS**, after Clavis proves the CMP-iOS recipe | The recipe proven in the simulator; hosted `macos-latest` | Apple Developer Program and the delivery route OQ-2 picks; the iPad Pro M4; iPhone items stay NDV |
| macOS | **P-mac** | P-LX binaries; Developer ID and notarisation secrets (OQ-3) | No Mac on record (OQ-5): device gates NOV |
| Windows | **P-win** | P-LX binaries; a signing route (OQ-3) or accepted unsigned | The Dell while still Windows (OQ-5), afterwards `CI (hosted VM) evidence` only |

Build order is Z (baseline), then P-LX, with the Ubuntu Touch viewer, iOS, macOS and Windows following over the
same core. Validation order is the owner's. The viewer runs no JVM, so it does not itself exercise S-UT1; the plan
keeps the program's P-UT b entry criteria as written rather than loosening them.

## 6. Work breakdown

Layout (R1: each track adds only to its own directory; R2: the existing gate stays green; R3: new workflow files
only, `ci.yml` untouched):

```
settings.gradle.kts   include(":core", ":ui", ":app", ":desktop")        # :app remains the Android module
core/                 KMP, no UI: commonMain, jvmMain, androidMain (iosMain in P-iOS); moved from app/src/main
ui/                   Compose Multiplatform: screens, ViewModels, navigation, theme (commonMain)
app/                  Android only: Application, MainActivity, WorkManager, SAF picker, Keystore credential store
desktop/              Compose Desktop head (JVM): main(), actuals, jpackage configuration
apple/                P-iOS: XcodeGen project.yml and the SwiftUI shell
ubuntu-touch/         P-UT b: Clickable project (QML viewer)
packaging/{linux,macos,windows}/
.github/workflows/    desktop-linux.yml · desktop-macos.yml · desktop-windows.yml · ios.yml · ut-click.yml
```

`:app` keeps its name and path because `ci.yml` reads `app/build/outputs/apk/debug/*.apk` and `app/build/reports`,
and the `applicationId` must not move. A by-directory mapping of pure sources into a separate build (asom's
pattern) is rejected here: `domain/repository` imports the DAO and entity types, so it would likely need two copies of
`AppDatabase`; for about 3,000 lines a `git mv` that keeps package names is cheaper (Q12 asks the owner to approve
it, because it moves every Android source file). Every new workflow uploads no artifacts (R6), runs
package lanes on `main`/tags only, and calls F9's `kmp-matrix.yml` by SHA-pinned `uses:` once it exists.

**Z. Baseline before anything moves** (Android/JVM, no behaviour change, normal PR process)
1. **Golden fixtures** recorded from today's code: a fixed signal set (timestamps with zero, trailing-zero and
   non-zero milliseconds, since `Instant.toString()` formatting is where platforms may differ) giving the expected
   `inc_` ids and a byte-exact `issues-manifest.json` with `nowMillis` fixed. Stored under `app/src/test/resources`
   with a new `.gitattributes` marking them `-text` (needed before any Windows lane). Done when `testDebugUnitTest`
   passes with the new test.
2. **Send-site guard**: a grep script, run by the new workflows, asserting that in production source sets (`app/src/main` today;
   `core/src/*Main`, `ui/src/*Main`, `desktop/src/*Main` after L1) `markReplySent` and `sendConfirmedReply` each have
   exactly one call site outside their own definition (tests are excluded; `SignalRepositoryTest.kt:76` calls
   `markReplySent` directly). Done when it passes on `b38b3a5` and fails if a second production call is added.
3. **Room schema export** (Q11): set `exportSchema = true` and `room.schemaDirectory`, commit the baseline `1.json`
   from Room 2.6.1. `.gitignore` currently ignores `/app/schemas`; remove that line in the same PR and commit the baseline
   `app/schemas/com.mbaliga.csapp.data.db.AppDatabase/1.json` from Room 2.6.1. L1 moves the directory to `core/schemas`
   with the rest of `data/db`. Done when the schema file is reviewable in the PR.
4. **Android V1 device smoke** by the owner, `NEEDS-DEVICE-VALIDATION` (Q12): nothing records csapp running on a
   device, and the restructure below moves every Android source path.

**L. Linux desktop (P-LX)**
1. **Spike S-CS1, then `:core`** (R4 pure core first). Spike: do Room 2.7+ and KSP resolve at Kotlin 2.0.20/AGP 8.5.2,
   or is the pin bump forced? The answer is input to OQ-17. Then `git mv` by package into `core/` (packages unchanged):
   `domain/*`, `data/github`, `data/play`, `data/export`, `data/db`, the `CredentialStore` and settings interfaces.
   Files with no JVM types go to `commonMain`; OkHttp, `java.time`, `java.security`, `InputStream` stay in `jvmMain`
   (one `Sha256` seam so `AnchorIdGenerator` is common). Room moves to its KMP form (`@ConstructedBy`,
   `BundledSQLiteDriver`; Android keeps `Room.databaseBuilder(context, ...)` in `:app`). `AppContainer` takes a small
   platform-providers object (database builder, credential store, settings store, picker, scheduler) instead of
   `Context`. `android.*` imports are banned in `core` (F5's check; until F5, a grep step). Done when `:core:jvmTest`
   runs the same 35 tests (count compared before and after), the Z1 golden passes, and `testDebugUnitTest`,
   `lintDebug` and `assembleDebug` stay green with `ci.yml` unchanged. `CONTAINER-BUILD-ONLY` for the JVM parts.
2. **`:ui`**: move `ui/` to Compose Multiplatform (JetBrains navigation and lifecycle artifacts); replace the two
   `ViewModelProvider.Factory` classes with `viewModel { }` initializers; replace `ExportScreen`'s activity-result
   contract and `ExportViewModel`'s `Uri` with a `UserPickedSink` seam that only a platform picker callback can
   construct, so a default-path export is impossible by construction; replace the two `android.net.Uri` route-argument
   encode/decode calls in `ui/nav/` with a common percent-encoder (a small function in `commonMain`), so `:ui` has no
   `android.*` reference. Done when the Android gate is green; Android
   screen behaviour is NDV.
3. **`:desktop` actuals**: database at `$XDG_DATA_HOME/csapp`, settings JSON at `$XDG_CONFIG_HOME/csapp`, AWT or
   FileKit dialogs (choice recorded in the PR), an in-process ticker at the same 30-minute cadence while the window
   is open, "Poll now" unchanged. **Credentials:** until F6 lands, `CredentialStore` writes throw a stated "no
   secret store on this build" and Settings says so (stub, not fake). That still delivers a useful credential-free
   milestone: manual signals, clustering, triage and export. With F6 the libsecret tier is used and the UI shows the
   tier; a file tier only if Q4 approves it, encrypted under a passphrase-derived key never stored beside it. The
   Secret Service may be absent or locked in a headless session: unknown, checked on the Deck. Done when `:desktop`
   passes a launch smoke under a virtual X server such as `xvfb` if that proves workable (unverified; `CI (hosted VM) evidence`,
   no pixel claims) and the Z1 golden passes on the JVM desktop target.
4. **Packaging and CI**: `desktop-linux.yml` runs `:core:jvmTest`, the guards and `createDistributable` on
   `ubuntu-22.04` (the glibc baseline of program §4.2; csapp has no JNI, so this is precautionary for the jpackage
   launcher on the Deck). Package lanes (`packaging/linux/`) also run on `ubuntu-22.04`, on `main`/tags only, from F10:
   jpackage app-image tarball with `install.sh` as the universal fallback, a Flatpak manifest consuming that
   prebuilt app-image, and AppImage as the repo's secondary format; the channel choice is OQ-4. Done when the
   workflow is green; installing on the Deck is NDV.
5. **Optional headless `--poll`** (PROPOSAL, Q13): a second `main()` linking the poll repositories only, never the
   reply path, plus a systemd user timer template that the package does not install. Done when the Z2 guard
   shows the headless entry point has no path to `sendConfirmedReply`; timer behaviour is NDV.
6. **Owner verification**: `docs/DEVICE-CHECKLIST-LINUX.md` from F11's template, created at wave time, covering
   install, entering a token with the stored tier shown, "Poll now", export dialog, HiDPI. All NDV/NOV.

**U. Ubuntu Touch viewer (P-UT b)** — labelled "manifest viewer", never "csapp for Ubuntu Touch" (R12, OQ-6)
1. **Scaffold** from F7's templates in `ubuntu-touch/`: `clickable.yaml`, `manifest.json.in`, `apparmor.in` with
   `content_exchange` only (no `networking`: the viewer opens files, it fetches nothing). No package name is
   written until NAMES.md has a row (R11, OQ-25).
2. **QML pages** (Lomiri.Components): incident list with severity and status filters, incident detail with its
   signals, and an empty state that says "no credentials, no network, no polling on this build". Severity is a word
   plus a shape glyph. The page reads `issues-manifest.json` by `JSON.parse` and refuses any `schemaVersion` other than 1.
3. **CI**: `ut-click.yml` builds the click with Clickable in the digest-pinned image on `ubuntu-latest`, adds the
   F7 click-review and policy checks, uploads nothing. Done when it is green (`CI-APPROX — NOT DEVICE EVIDENCE`);
   install and ContentHub import on a device are NDV. This container cannot build a click.

**I. iOS / iPadOS (P-iOS)**
1. **`iosMain` for `:core`**: move `data/github` and `data/play` to `commonMain` on Ktor (Darwin engine) with
   `MockEngine` tests replacing MockWebServer; `java.time` to kotlinx-datetime; `InputStream` to kotlinx-io; RS256
   signing and SHA-256 behind actuals (CryptoKit/SecKey or a Kotlin library; PKCS#8 import is the fiddly part,
   spike S-CS2). Parity is proven by `iosSimulatorArm64Test` running the same 35 tests, the Z1 golden, and an RS256
   known-answer test with a throwaway key labelled TEST ONLY (PKCS#1 v1.5 signatures are deterministic). Ktor could
   instead land in L1 at about +1 week there and -1 week here; this plan keeps it here to match the estimates.
2. **`:ui` iOS target** on the Kotlin pin OQ-17 chooses (the reader profile found Compose Multiplatform iOS stable
   needs a Kotlin above 2.0.20; the exact floor is unverified here).
3. **`apple/`**: XcodeGen `project.yml`, SwiftUI shell hosting the Compose controller, Keychain actual
   (`WhenUnlockedThisDeviceOnly`, never synchronisable, via F6), `UIDocumentPicker` export, `isExcludedFromBackup`
   on the database and credential files, opportunistic `BGAppRefreshTask` plus "Poll now" (Q10), F10's
   `PrivacyInfo.xcprivacy`.
4. **CI**: `ios.yml` on `macos-latest` builds the simulator app and runs the tests with `CODE_SIGNING_ALLOWED=NO`;
   compile-only on PRs. Done when green (`SIMULATOR`); this container has no Xcode. TestFlight uploads tester crash reports to the developer
   automatically, an egress the privacy copy must disclose (OQ-2). iPad Pro M4 install is NDV.

**M. macOS (P-mac)**: `packaging/macos/` and `desktop-macos.yml` build the `.dmg` from `:desktop` with `jpackage`
on `macos-latest`, `UNSIGNED — not for release`; Keychain actual via F6; data under `~/Library/Application Support/csapp`
with the backup-exclusion attribute (mechanism NOV); signing and notarisation exist only as a disabled template
until OQ-3. Done when the DMG builds in CI (this container cannot build one); launch is NOV (no Mac, OQ-5). If the owner wants a Mac presence
without a Mac, the iPadOS app as "Designed for iPad" is the program's route (§4.4).

**W. Windows (P-win)**: first the R3 path lint, then `desktop-windows.yml` runs `:core:jvmTest` on a Windows JDK
(catching the UTF-16-BOM CSV parser's charset assumptions and the Z1 golden's line endings), then `packaging/windows/`
WiX MSI on `windows-2025`, `UNSIGNED — not for release`; Credential Manager or DPAPI via F6; database and credentials
under `%LOCALAPPDATA%` (not the roaming `%APPDATA%`, to honour `allowBackup=false`'s intent); winget manifest only after
a NAMES.md row (R11). Done when the MSI builds in CI (not buildable here); running it is NDV on the Dell (OQ-5) or `CI (hosted VM) evidence` only.

## 7. Shared foundation this repo consumes or provides

Consumes (program §6): **F6 platform-ports** is the main dependency: secure key storage with a reported tier,
app directories, file picker and save. csapp's `CredentialStore` interface stays csapp's; each platform actual
becomes a thin adapter over F6 once OQ-22 is ruled. **F5 kmp-conventions**: the Gradle plugin, the version catalogue at
the OQ-17 pin, the `android.*` ban for common and JVM source sets, the licence allowlist (SPDX headers stay opt-in,
Q11). **F7** (templates, S-UT1 verdict, OpenStore policy, UT checklist), **F9** (`kmp-matrix.yml`, path lint, no
artifact uploads), **F10** (jpackage/Flatpak/WiX/notarisation/XcodeGen/Clickable templates), **F11** (evidence
scheme and device checklists). Not consumed: **F1/F3** (csapp uses its own M3 theme; adoption owner-gated, OQ-29),
**F2** (csapp has no crash-recovery module), **F4, F8, F12** (not applicable).

One observation to confirm: the program's OQ-17 lists csapp among the Hyle lockstep consumers (PT:D-Q), but csapp has
no `.gitmodules` and no `includeBuild` today. The lockstep begins to bind only when csapp consumes F5/F6 that way (I-6).

Provides, as PROPOSALS: the `CredentialStore` shape as a seed for F6; a small Room-KMP plus bundled-driver data
point (216 lines of schema) for Fonebrew's own spike; the determinism golden pattern (Z1) for other repos; and the
`issues-manifest.json` v1 contract, which the Ubuntu Touch viewer reads and which any external consumer (Q1) would read.

## 8. Open questions for the owner

Master ids are in brackets; "csapp-local" means the program has no id. Proposals are labelled.

1. **Registry drift** (csapp-local; program §5 row). The registry says csapp ingests Galaxy Store comments and
   exports for Fonebrew Studio; the repo has neither. Is Galaxy Store ingestion planned, and what platform runs
   the `issues-manifest.json` consumer? Blocks: the desktop export convention and the Ubuntu Touch viewer's
   scope; nothing else.
2. **Priority and order** [OQ-28]. Phone-first (iOS early) or desktop-first? The plan follows the owner's fixed
   order and the program's waves regardless. Blocks: only whether P-iOS moves ahead of other repos.
3. **Wire the Play CSV backfill into the UI** (PROPOSAL, csapp-local). A file-open entry for
   `backfillFromMonthlyReport`, first on desktop. It changes the shared UI, hence Android too. Blocks: nothing in
   this plan; it is an add-on to step L3.
4. **Secret custody** [OQ-22]. Are OS keyrings (Keychain, libsecret, Windows Credential Manager/DPAPI) acceptable
   under "never plaintext"? Is a passphrase-encrypted file tier acceptable where no keyring exists? Ubuntu Touch:
   viewer with no credentials (recommended) or something stored? Blocks: F6, and every step that holds a token or key.
5. **Apple** [OQ-2, OQ-3, OQ-5]. Developer Program, a Mac CI runner and delivery route (TestFlight with its
   crash-report egress, ad-hoc OTA, or sideload), and whether any Mac exists. Blocks: P-iOS device-entry, P-mac.
6. **Signing and the Windows fleet** [OQ-3, OQ-5]. Certificates for macOS and Windows or accept unsigned builds
   with Gatekeeper/SmartScreen warnings; is Windows in the owner's fleet or purely for completeness? Blocks:
   release templates for M and W; whether W is worth a certificate.
7. **cell-shell and Hyle** [OQ-29, OQ-17/F3]. Proceed on plain M3 navigation now (recommended: Compose
   Multiplatform's equivalents are a drop-in) and re-skin later, or wait? Blocks: step L2's theme decisions only.
8. **KMP pilot** (csapp-local; relates to OQ-17, OQ-22). Should csapp be the first consumer of F6 and the Room-KMP
   pin, ahead of Fonebrew and Aarso? The program names Typewright and Clavis as the pre-wave proofs, not csapp.
   Blocks: whether L1/L3 are written as a reference for other repos.
9. **Room-KMP or SQLDelight, and the pins** [OQ-17]. The program's Fonebrew plan starts with a Room-KMP spike;
   csapp's 216 lines of Room survive a 2.7+ upgrade unchanged, which favours Room-KMP here. Which Kotlin/CMP/AGP/KSP
   set? Blocks: L1, I2.
10. **iOS cadence and push** (csapp-local). Is foreground "Poll now" plus opportunistic refresh acceptable, given the
    doc's "no push" and I-1? Blocks: the iOS definition of done and its store copy.
11. **README, SPDX, schema export, progress file** (csapp-local). May the port PRs add a root README, SPDX
    `Apache-2.0` headers (opt-in under F5), `exportSchema`, and a `docs/PORTING-PROGRESS.md` for gate output?
    Blocks: Z3 and where R7's real command output is recorded.
12. **Restructure approval and an Android smoke** (PROPOSAL, csapp-local). May the PR for L1/L2 move the Android
    sources into `:core`/`:ui` while keeping `:app`, `applicationId` and `ci.yml`, and does the owner want a device
    smoke of Android V1 first (Z4)? Blocks: L1.
13. **Headless `--poll` and a systemd timer** (PROPOSAL, csapp-local). Acceptable under I-1 as opt-in and
    off by default, with the reply path unreachable? Blocks: L5 only.
14. **Ubuntu Touch shape** [OQ-1, OQ-21, OQ-6]. Viewer only (planned, 3 weeks), or also a foreground "Poll now"
    over F7's JVM core with memory-only session credentials (PROPOSAL, unestimated, needs S-UT1), or Waydroid for the
    existing APK (csapp is not on OQ-21's list today)? Blocks: all of U, and the P-UT b entry.

## 9. Sources read

`docs/PRODUCT-AND-ARCHITECTURE.md` · `settings.gradle.kts` · `build.gradle.kts` · `gradle.properties` ·
`app/build.gradle.kts` · `gradle/libs.versions.toml` · `gradle/wrapper/gradle-wrapper.properties` ·
`app/src/main/AndroidManifest.xml` · `app/proguard-rules.pro` · `.github/workflows/ci.yml` ·
`.github/workflows/cleanup-artifacts.yml` · `LICENSE` · `.gitignore` · under `app/src/main/java/com/mbaliga/csapp/`:
`CsAppApplication.kt`, `ui/MainActivity.kt`, `di/AppContainer.kt`, `data/credentials/CredentialStore.kt`,
`data/credentials/EncryptedCredentialStore.kt`, `data/settings/AppSettingsStore.kt`,
`data/export/IssuesManifestExporter.kt`, `data/export/IssuesManifestModel.kt`, `ui/export/ExportViewModel.kt`,
`ui/export/ExportScreen.kt`, `data/play/PlayServiceAccountAuth.kt`, `data/play/PlayReviewsApiClient.kt`,
`data/play/PlayReviewRepository.kt`, `work/PollScheduler.kt`, `work/GitHubPollWorker.kt`,
`work/PlayReviewPollWorker.kt`, `ui/theme/Theme.kt`, `data/db/AppDatabase.kt`, `data/db/dao/*.kt`,
`data/db/entities/SignalEntity.kt`, `domain/model/SignalType.kt`, `ui/settings/SettingsViewModel.kt`,
`ui/ViewModelFactory.kt`, `ui/nav/CsAppNavHost.kt`, `ui/reply/ReplyViewModel.kt`,
`ui/dashboard/DashboardScreen.kt`, `app/src/test/java/com/mbaliga/csapp/export/IssuesManifestBuilderTest.kt`,
and the import, line-count and `@Test` sweeps over all 64 `.kt` files under `app/src/{main,test}`.
Program inputs: `Personal-Tracker/PORTING_PROGRAM.md` §0–§3, §4.1–§4.5, this repo's §5 row, §6, §7, §8, and the
2026-10-06 reader profile of this repository.

## Owner rulings and the proposed line (added 2026-10-07)

Status: PLAN. Nothing here is built, run on a device, signed or submitted. The program-level plan is Personal-Tracker `PORTING_PROGRAM.md` ([PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which holds the owner's rulings and section 5A, the proposed port / no-port line. The cells, estimates and open questions above are this repo's original plan and are unedited. Where the owner has since answered a question, the answer is below. Section 5A is a proposal; the owner has not yet confirmed it.

### Where csapp sits in the proposed line (program section 5A.3, a proposal)

| Target       | Verdict | Weeks and flags |
| ------------ | ------- | --------------- |
| Ubuntu Touch | no-port | -               |
| Linux        | port    | 3w              |
| iOS/iPadOS   | no-port | -               |
| macOS        | port    | 1w              |
| Windows      | port    | 1w              |

Key: `follows` means it ports only as far as the products that depend on it; `exists` means the program reads it as already running there, unverified (finish, verify and sign); flags: `g` gated on a prerequisite, `r` re-estimate or floor, `o` its own program, `s` scope note. The program's P4, P8, P12 and P13 gate whole columns or repos and are not flagged per cell. A port verdict counts the deliverable in the line; where this repo's plan calls a deliverable a reframe (program rule R12) it keeps that label. Tests cited in the reason: (a) the owner said it is needed there; (b) its job is really done on that OS by real users; (c) that OS is where it is sold or its audience is; it has no reason to exist if (x) its surface is absent or untouchable, (y) the capability is forbidden or impossible, or (z) the only form is a thin wrapper or a different product nobody asked for. P-numbers and OQ-numbers refer to the program plan (Personal-Tracker `PORTING_PROGRAM.md`, sections 5A.5 and 8).

Reason: A developer tool, so the desktops; its audience carries the Android build and has no reason to use a phone-side click or an iPhone port. The OQ-22 ruling (an app-private file is allowed on Ubuntu Touch) removes the old key blocker, not the audience reason.

### Owner rulings that apply here

- **Ubuntu Touch scope and OQ-21 (2026-10-06):** "Native only, no substitutes" for Android-only products, and "No, native ports only" (Waydroid is not accepted). The program plan reads csapp as an Android-only product (that is its reading, not the owner's: OQ-21 does not name csapp, as this plan's Q14 notes), so on that reading the Waydroid option in Q14 is not accepted. The program left this plan's read-only manifest viewer cell unmarked; the proposed verdict is no-port because csapp's audience carries the Android build, not because of this ruling.
- **OQ-17 toolchain (2026-10-06):** "B: staged pin (Recommended)": Kotlin 2.1.20 and Compose Multiplatform 1.8.2 for the first wave, 2.4.x deferred. csapp is on Kotlin 2.0.20, AGP 8.5.2 and KSP 2.0.20-1.0.25 today, so the pin is a bump for it; the ruling answers only the Kotlin and CMP part of this plan's Q9, and the Room 2.7+ and KSP/AGP minimum stays open until spike S-CS1. csapp has no includeBuild, so the PT:D-Q lockstep binds it only if it consumes F5 or F6.
- **OQ-22 key custody (2026-10-06):** "OS keystore, weaker fallback shown (Recommended)": Keychain, Credential Manager (DPAPI), Secret Service, a passphrase-protected file or an app-private file on Ubuntu Touch, each with the weaker guarantee stated in the UI. Whether this ruling counts as the repo-local owner approval this plan asks for is for this repo to record; nothing in this section ratifies a repo decision.
- **OQ-31 Mac (2026-10-06 and 2026-10-07):** "Buy a Mac", and on 2026-10-07 an Apple-silicon Mac mini, not yet bought. The proposed line has no iOS port for csapp; its macOS cell is a jpackage dmg (Developer ID, OQ-3), so only the Mac statement applies.
- **OQ-20 CI (2026-10-06):** "Linux-only CI when private (Recommended)": this repo is public, so the ruling does not limit its macOS and Windows lanes; going private would stop them. Actions artifact storage is still exhausted (program rule R6).
- **OQ-5 hardware (2026-10-06):** the owner's answer changes which of their other machines can serve as device gates, so a gate this plan names on specific hardware may be moved or dropped. Which machine carries which device gate is not decided (OQ-33).
- **Directives (2026-10-06):** "Draft amendments for approval": program directives I-1 to I-12 and rules R1 to R12 are unchanged; PROPOSED-1 to PROPOSED-4 in Personal-Tracker `DECISIONS.md` are drafts awaiting the owner.

### Prerequisites and open questions that touch this repo (program sections 5A.5 and 8)

Prerequisites (program-level; not costed here):

- program P8: An Apple-silicon Mac (OQ-31: a Mac mini chosen on 2026-10-07, not yet bought)

Owner questions in the program register that concern this repo (status as of 2026-10-07):

- OQ-3 (open): Signing custody
- OQ-4 (open): Channels and store compatibility
- OQ-5 (ruled): Hardware stance
- OQ-17 (ruled): Toolchain pins: the pin is ruled; the "Also" approvals (converting shared modules to kotlin("multiplatform"), asom's no-KMP rule staying asom-local) are unanswered
- OQ-20 (ruled): CI minutes, storage and repo visibility
- OQ-21 (ruled): Waydroid as the Ubuntu Touch answer
- OQ-22 (ruled): Secret custody per platform
- OQ-29 (open): Hyle-consumer status
- OQ-31 (ruled): CI for App Store builds; which Mac
- OQ-33 (open): Hardware details still open

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A change.

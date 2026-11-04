# csapp — build prompt for a Sonnet Claude Code session

Paste everything below the line into a fresh Claude Code session with `mbaliga/csapp` attached. Session settings: **model `sonnet`**, **`/effort high`** (`xhigh` for WP-C3). One work package per session. Run `/code-review` before reporting a WP.

---

Read, in this order, before touching anything: `CLAUDE.md`, `BUILD-SPEC.md`, `docs/STATUS.md`, `docs/VERIFICATION.md`, `docs/PRODUCT-AND-ARCHITECTURE.md`, then the four-lens design folder `docs/design/2026-11/` — `00-REVIEW.md`, `03-ARCHETYPES-AND-JOURNEYS.md`, `04-ROOMS-UX.md`, `02-ON-DEVICE-INTELLIGENCE.md`, `01-COMPETITIVE-LANDSCAPE.md`. The design folder is the UX and intelligence spec for v1.1. Where it disagrees with BUILD-SPEC on *UI or sequence*, the design folder wins. Where anything disagrees with the hard rules in `CLAUDE.md` / BUILD-SPEC §1, the rules win, always.

Start from `design/four-lens-review` (it is the Nov 3 head `ccr-38c0fd1b-9ebor0` plus the design folder): `git switch -c trunk origin/design/four-lens-review`. Commit on `trunk`. Open one PR `trunk → main` at the end of WP-C0 and keep pushing to it.

## Rules you are bound by (restated)

1. **Nothing is sent to a user without explicit human confirmation.** One send site, `PlayReviewsApiClient.sendReply`, reached only via `ReplyViewModel.confirmAndSend`. `SingleSendSiteTest` enforces it. Reply-to-cluster passes every send through it. csapp never posts to GitHub and never sends mail; it drafts and hands off.
2. **AI suggests, never decides.** Every AI output is a `PENDING` suggestion row; the user accepts or rejects. No auto-reply, no timeout-to-send, ever.
3. **Clustering is deterministic** (`AnchorIdGenerator`). AI may propose a title or tags; it never moves a signal between incidents.
4. `sourceKey` is identity. 5. File export only, user-initiated. 6. Do not read Assay output.
7. No telemetry; credentials only in Keystore; every network call originates from a tap, a pull, or the user-scheduled `SyncWorker` (`MainThreadNetworkTest` + a new `NetworkOriginTest`).
8. Permissive licences only (llama.cpp MIT is fine; no vendor AI SDKs; no JavaMail).
9. Colour never carries meaning alone; marks are glyph + label with luminance; never red/green.
10. Ladder: `code-complete` / `verified` / `ready-for-owner-review`; device behaviour is `NEEDS-DEVICE-VALIDATION`. Never "done", "works on device", "production-ready".
11. Do not rename `csapp` or `com.mbaliga.csapp`; product name lives in `app_name` only.
12. Copy: explanations behind info icons; no "AI-sounding" copy; states are the engine's words (`new · grouped · replied · closed`).
13. Environment honesty: stub loudly with typed errors; never fake success.

## Work packages

Each WP: read the named files; implement; run the gate unpiped (`export ANDROID_HOME=/opt/android-sdk LANG=C.UTF-8; ./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain`); append a dated row to `docs/STATUS.md`; commit; stop and report in BUILD-SPEC §7 format. Never start the next WP in the same session.

### WP-C0 — CI, PR, README, branch hygiene (effort: medium)
1. Extend `.github/workflows/ci.yml` triggers to `trunk`, `ccr-*`, `design/*`.
2. Open PR `trunk → main` with the branch map from `docs/design/2026-11/00-REVIEW.md` §1.
3. Write `README.md` (≤120 lines): what csapp is in three sentences, the hard rules, the ten-minute quick start (connect one source → pull → reply), build instructions, the design folder index.
4. Diff `agent/cs-app-v1`, `agent/finish-csapp-v1`, `agent/private-dogfood-v1`, `agent/assay-v1-final` against `main` and record in `docs/STATUS.md` whether each contains anything not already on trunk; recommend deletion per branch (do not delete).
**Gate:** CI green on `trunk` (run URL in STATUS.md). Ladder: `verified`.

### WP-C1 — The room shell and the five rooms (effort: high)
Read `app/src/main/java/com/mbaliga/csapp/ui/**`, `docs/design/2026-11/04-ROOMS-UX.md` §1–§4, §6, §9.
1. Build a reusable **room shell** (`ui/rooms/`): origin + four cardinal rooms by pager with velocity-gated swipes; compass with tap parity; Z axis (pinch-out overview, pinch-in detail, `+/−`, zoom-to-fit); immersive/regular with parity; predictive back returning to origin; process-death restoration of room + Z + scroll + open sheet + composer draft; RTL mirroring; TalkBack custom actions; hardware keys. No gesture without a non-gesture equivalent. Keep the existing ViewModels and repositories; this WP rehomes screens, it does not rewrite data flow.
2. Rooms per §2–§3: **Inbox** (origin: needs-you / open / quiet; swipe right = compose, left = snooze; pull = sync; digest line when AI configured), **Projects** (west) with **Project detail** (Z-in: sources, mail filter editor with live preview, releases placeholder, export, quota counter), **Incidents** (north) with disclosure filters and multi-select, **Replies** (east: drafts / sent / failed; edit-diff on zoom-in), **Settings** (south, cells; Models cell is a placeholder until WP-C3), **Pulse** (Z-out from Inbox; can be set as origin).
3. **Thread** (Z-in on a signal/incident) per §4: context strip, signals in time, composer with the 350 wall for Play, suggestion *beside* the composer, Review & send as a sheet with long-press Send (≥600 ms, growing cell ring), the one-time "public and permanent" note, edit-reply with diff. GitHub drafts get a copy button, never a post button; mail drafts hand off via share.
4. Marks per §6 as composed cell shapes (`ui/components/Marks.kt` extended: plant-cell geometry from a seed; straight-ish walls, rounded junctions; the cluster mark's segments are the sources it contains); every mark carries its label in the same semantics node.
5. Failure states per `03-ARCHETYPES-AND-JOURNEYS.md` J6: never a disappearing toast; token rejected → source cell ✕ + Settings opens on it; quota 0 → counter + reset time inline; send failed → stays in Replies with ✕ + retry.
**Gate:** build green; `RoomShellTest` (back → origin; restoration; compass parity), `MarksTest` extended, existing tests green; Roborazzi (add it; Robolectric is already in) for Inbox, Projects, Incidents, Replies, Settings, Thread, Pulse in light/dark and 200 % font. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`: gesture feel, long-press send, 200 % on the RedMagic.

### WP-C2 — First launch, Guidance dial, deep links, notifications (effort: high)
Read `03-ARCHETYPES-AND-JOURNEYS.md` §1, J1, J2; `04-ROOMS-UX.md` §9 row 1.
1. **First launch** per J1: cell-growth loader (≤900 ms, skippable, reduced-motion fade); the three-cell diagram with one info icon per cell; the one-question Guidance dial (Guide / Work / Expert); **connect one source** (Play service-account JSON via share-sheet/SAF import, GitHub token, IMAP or Gmail) with the "where to get it" copy behind the info icon; project inferred from the source and editable; first sync as under-the-surface motion; incidents grow in as cells; compass overlay once, after the first result. If the source fails, the cell shows ✕ and the exact error; the user is never dropped on an empty Inbox.
2. **Guidance dial** wired to density: Guide (suggestion beside composer expanded, glosses on first use), Work, Expert (signal body on top, suggestion collapsed, raw headers one tap away, "summarise only" mode).
3. **Deep links**: `csapp://signal/<sourceKey>` and `csapp://incident/<id>` land on Thread with Back → Inbox; a deep link on first launch skips the on-ramp and asks the dial question after.
4. **Notifications** from the user-scheduled `SyncWorker` only: new unreplied signals (grouped per project), failed sends, and the spike/regression alert from WP-C4's thresholds (stub the alert with a typed `NotConfigured` until WP-C4). Tapping opens the deep link. Channels per type; all opt-in from Settings → Sync.
**Gate:** build green; Robolectric tests for the first-launch state machine (every source failure path, the dial persisting, deep-link bypass) and for notification routing; Roborazzi for every first-launch state. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`: Play JSON import via share sheet; Gmail redirect (have the reversed-client-id scheme ready per `docs/STATUS.md`).

### WP-C3 — Models room and the on-device model layer (effort: xhigh)
Read `docs/design/2026-11/02-ON-DEVICE-INTELLIGENCE.md` fully, `models-catalogue.v1.json`, `04-ROOMS-UX.md` §5, `domain/ai/**`, `ai/AiTest.kt`.
1. Refactor `domain/ai/ModelProvider.kt` to the shared shape: `complete(system, user, params): Completion` with `provenance`, `model`, `promptVersion`, optional `confidence`. Implementations: `AsomProvider` (OpenAI-compatible at `127.0.0.1:11435`, AIDL pairing check), **`LlamaCppProvider`** (embedded llama.cpp via JNI, built from a pinned post-Mar-2026 commit with CMake in the Gradle build so Qwen3.5's hybrid architecture loads; GBNF grammars; per-token logprobs), existing `OpenAiCompatProvider`/`AnthropicProvider` kept, `AiCoreProvider` behind a capability check (typed `Unavailable` where absent; on-device-only path, never the Firebase hybrid fallback).
2. Catalogue per §3: ship `assets/models/catalogue.v1.json` from `models-catalogue.v1.json` with every `PIN-AT-BUILD` field resolved from `GET /api/models/{repo}?blobs=true` at build time (a Gradle task that fails the build on a missing pin); JSON schema; Ed25519 signature check for refreshed copies (key in the APK; pinned GitHub raw URL).
3. Downloader per §5: `Range` resume, streamed SHA-256, free-space precheck, Wi-Fi/charging conditions, foreground service; mismatch deletes and shows digests; progress is the cell filling.
4. Bring-your-own per §6: URL and SAF file; GGUF header/metadata parser; `.litertlm`/`.task` manifest read; architecture support check against the bundled engine; fit mark from measured RAM; detected card shown; nothing executes before Install; declared licence shown before first use.
5. **Models room** per `04-ROOMS-UX.md` §5: three shelves; cloud BYOK providers as watched objects (alien glow, "sent only to `<host>`", host assertion at call time); ASOM as local provider when paired; per-task defaults (`classify`, `title`, `draft`, `translate`, `digest`, `summarise`); load/unload with the radium glow; first-load timing shown; the t-shirt defaults from the catalogue preselected for the phone's size.
6. Tasks per §7 with versioned prompts under `assets/prompts/<task>/v<n>.md`, GBNF grammars, the tone line injected into `draft`/`translate`; `classify` may run during sync when the user enables it (Settings → Sync) and only on-device; every suggestion row records engine, model hash, prompt version, decoding settings.
7. Tests: `AiEvalTest` (extends `AiTest`) per task against a fake provider; `ModelFileInspectorTest` with truncated/unsupported/valid GGUF fixtures; a test that no suggestion can transition to anything but `ACCEPTED`/`REJECTED` by a user action; the existing `SingleSendSiteTest` still green.
**Gate:** build green; the tests above; Roborazzi for the Models room shelves, download in progress, gated prompt, BYOM card, BYOK provider, ASOM paired. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`: one real download of the M default, one BYOM install from a file, one on-device `draft` and one `translate` (Hindi) with timing recorded in `docs/design/2026-11/model-eval.md`.

### WP-C4 — Credibility features (effort: high)
Read `01-COMPETITIVE-LANDSCAPE.md` §5 (every **P0**), `03-ARCHETYPES-AND-JOURNEYS.md` J3–J5, J7.
1. **Macros** as objects (`{name}`, `{version}`, `{app}`) + the tone line (Settings → Macros); used by the composer and by `draft`.
2. **Reply-to-cluster** (J3): one draft → per-signal preview with variables resolved → each send through the single send site; progress as the cell filling; failures stay listed.
3. **Releases** on a project (GitHub tags/releases when connected; typed otherwise) → "which incidents does this close?" multi-select → reply-to-cluster with the changelog macro → suggested *edits* of earlier replies (J4).
4. **One-tap "draft an issue"** from a review or mail: opens GitHub's new-issue URL with title/body prefilled via the browser; the issue URL, once the user pastes it back (or the next poll finds it by title match), links to the incident. csapp never writes to GitHub.
5. **Translation** (J7): Play API `translationLanguage` for review text where available; on-device `translate` for drafts; original always visible; language mark; cloud only on the watched-object tap; Konkani and other unverified languages carry the low-confidence mark.
6. **Pulse**: counts by source, sentiment trend by version and rating (information-rich colours per source, monochrome toggle), top incidents, digest with regenerate; share as text/markdown.
7. **Spike/regression alert**: ≥N negative signals within W hours of a release (thresholds in Settings → Sync); feeds the WP-C2 notification channel.
8. **Snooze** with return-to-Inbox; Play quota window + counter shown in Project detail.
**Gate:** build green; tests for macro resolution, reply-to-cluster passing every send through `confirmAndSend`, release-close suggestions, translation fallbacks, spike detection thresholds; Roborazzi for Pulse, reply-to-cluster preview, releases, translate. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`: one real reply-to-cluster of ≥2 signals; one Play `translationLanguage` fetch.

### WP-C5 — Accessibility, sound/haptics hooks, release lane (effort: medium)
1. Accessibility pass per `04-ROOMS-UX.md` §8; a test scanning every mark composable for the glyph + label pair; 200 % rows for every room.
2. Earcons/haptics hooks with Hyle preset names (assets the owner supplies; typed `NotProvided` stub; Settings toggle; never fake).
3. `docs/VERIFICATION.md` extended with rows for every `NEEDS-DEVICE-VALIDATION` item above; `README.md` quick start updated to the first-launch flow; versionName `1.1.0`; release APK on tags when the keystore secret exists.
**Gate:** all CI green on `trunk`; `docs/VERIFICATION.md` has no empty cells. Ladder: `ready-for-owner-review`.

## Do not

- Do not add auto-send, auto-approve by star rating, timeout-to-send, or any rule that reaches the send site without a human long-press.
- Do not write to GitHub or send mail from the app. Draft and hand off.
- Do not add ASO, competitor tracking, chat widgets, CSAT, rating prompts, or review-removal features.
- Do not route AI through any relay; cloud calls go from the phone to the provider's host.
- Do not inline colour literals; add tokens to `HyleTokens.kt`.
- Do not write "production-ready". Do not tick a `NEEDS-DEVICE-VALIDATION` row.
- Do not improvise a screen `04-ROOMS-UX.md` specifies; if a state is missing from its inventory, add it to the doc in the same commit and say so.

## Report format (end of every WP)

```
WP-C<n> — <title>
Ladder: code-complete | verified | ready-for-owner-review
Gate commands (unpiped, LANG=C.UTF-8): <cmd> → exit <code> …
Evidence: <CI run URL | artifact path | test names>
NEEDS-DEVICE-VALIDATION: <bullets or "none">
OWNER-ACTION-NEEDED: <bullets or "none">
Not done / deferred: <bullets>
Design-doc amendments made: <files or "none">
Next: WP-C<n+1> (do not start)
```

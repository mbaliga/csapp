# csapp — repository review, 4 Nov 2026

Scope: every branch of `mbaliga/csapp`, read against the app source, tests, CI, and the repo's own ledgers (`BUILD-SPEC.md`, `docs/STATUS.md`, `docs/VERIFICATION.md`).

## 1. State of the branches

| Branch | Ahead/behind `main` | Contains | Verdict |
|---|---|---|---|
| `main` (16 Sep) | — | Aug 4 "V1" scaffold + CI/cleanup tweaks | Two months stale. |
| `ccr-38c0fd1b-9ebor0` (3 Nov) | +17 / 0 | **WP0–WP6 landed**: multi-project Room v2 + validated migration, GitHub issue polling, Play reviews (service-account auth, quota guard, 350-char cap, append-only reply log, single send site), IMAP + Gmail PKCE, AI suggestions (ASOM/local/DeepSeek/OpenAI/Anthropic presets, PENDING-only until Accept), ratified `issues-manifest.v1.json` export with schema tests, Hyle theme + glyph+label marks, adaptive icon, env-var release signing, debug APK in `dist/` (SHA verified) | **The real head.** No PR to `main`. 134 files, +7.5k/−1k. |
| `ccr-8ca7ea56-e3uekv` (3 Nov) | +13 / 0 | Partial WP0 (Kotlin bump, "unbuilt") | Dead; superseded by `38c0`. Delete. |
| `claude/fonebrew-development-clzu43` | +5 / −4 | Crash-recovery harness, toolchain bump, export docs | Inside `38c0`. |
| `claude/multi-platform-porting-plan-nnvjug` | +3 / 0 | Porting plan + CI fix | Inside `38c0`. |
| `claude/default-store-listings-u991hc` | +1 / −4 | Fastlane placeholder listing | Inside `38c0`. |
| `claude/asystemofcells-redesign-s6yv4c` | +2 / 0 | PolyForm Noncommercial relicense + CI fix | **Owner decision pending.** |
| `claude/repo-status-overview-2c0rgm` | 0 / −5 | docs | Merged. Delete. |
| `agent/cs-app-v1`, `agent/finish-csapp-v1`, `agent/private-dogfood-v1`, `agent/assay-v1-final` | +45 / +35 / +18 / +5 | Aug "source transport" segments and recovery runs | Predate the Aug 4 squash; almost certainly the same source in chunked form. Diff before deleting; do not merge. |
| `apk-testbuild`, `claude/github-storage-cleanup-*` | +1 | test APK / cleanup | Superseded. |

`ci.yml` triggers on `main` and `v*` tags only. **Nothing on `ccr-38c0` has ever run on CI.** Every "code-complete" row in `STATUS.md` rests on a local Gradle exit code inside a sandbox.

## 2. What exists, what doesn't

**Exists and is substantial.** ~6k lines of app code, ~2k of tests, and the tests are the ones that matter: `SingleSendSiteTest`, `MainThreadNetworkTest`, `ReplyGovernanceTest`, `ProjectIsolationTest`, `MigrationTest`, `ExportConformanceTest`. The hard rules in `CLAUDE.md` are enforced by tests, not by hope. Credentials are in Keystore; network security config is cleartext-off except loopback.

**Does not exist, or is unproven:**

- **No on-ramp.** Start destination is Home; a fresh install reads "Nothing here yet. Pull down to sync, or add a signal with +." The user has no project and no credentials and nothing routes them to Projects → Settings → token. The 14-row `docs/VERIFICATION.md` checklist *is* the onboarding, and it lives in the repo. No `README.md` on any branch.
- **Nothing has touched a phone.** All 14 verification rows unticked. The three flows fakes cannot cover — one real Play reply, the Gmail OAuth redirect (`com.mbaliga.csapp:/oauth2redirect`, which Google may reject for Android client types), background `SyncWorker` — are exactly the ones that decide whether this ships.
- **Fonebrew import is blocked** on Core's side; the export's only consumer does not exist yet.
- No Roborazzi screenshot tests (deviation recorded); `MarksTest` stands in for the glyph+label rule.
- `shared-libraries` submodule needs `CORE_SUBMODULE_TOKEN` in CI; release keystore absent (`OWNER-ACTION-NEEDED`).
- No model catalogue, no on-device download path, no bring-your-own-model. The AI layer is BYOK-cloud or "a local OpenAI-compatible server you already run". For a product whose thesis is on-device first, this is backwards.
- The UI is a conventional Compose nav-host (Home → Projects / Settings / Incident / Reply). It is not on the room grammar; there is no origin/cardinal model, no Z axis, no compass.

## 3. Verdict

**`code-complete`, honestly labelled; not production-ready.** It is one device-validation afternoon from `ready-for-owner-review` *if* OAuth and one Play reply land — and it is a full UX pass from being a product someone other than the owner could pick up. The engine is right; the surface is a nav-host with a settings page.

## 4. Recommended immediate actions

1. Extend `ci.yml` triggers to `ccr-*` and `design/*`, then open `ccr-38c0fd1b-9ebor0 → main`.
2. Run `docs/VERIFICATION.md` rows 1–6 and 14 on the RedMagic before any more code. Row 5 (Gmail redirect) is the one most likely to fail; have the reversed-client-id scheme ready.
3. Decide the licence question once for the constellation.
4. Delete `ccr-8ca7ea56-e3uekv`, `claude/repo-status-overview-2c0rgm`, `apk-testbuild`, `claude/github-storage-cleanup-*`. Diff the four `agent/*` branches against `main` before deleting those.
5. Execute `05-ULTRACODE-PROMPT.md`: first-run route, rooms, Models room, and the credibility features from the competitive lens.

## 5. The four lenses (this folder)

| File | Lens |
|---|---|
| `01-COMPETITIVE-LANDSCAPE.md` | Best-in-class across desktop/web, mobile, agentic — and where nobody is |
| `02-ON-DEVICE-INTELLIGENCE.md` | Model catalogue, t-shirt sizes, one-click download, bring-your-own-model, BYOK |
| `03-ARCHETYPES-AND-JOURNEYS.md` | Behavioural archetypes, their journeys and expectations |
| `04-ROOMS-UX.md` | The app redrawn on Fonebrew's room grammar |
| `05-ULTRACODE-PROMPT.md` | The executable prompt for a Sonnet Claude Code session |

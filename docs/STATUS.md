# Status

Ladder: `code-complete` → `verified` → `ready-for-owner-review`. On-device behaviour is owner-verified only
(`docs/VERIFICATION.md`).

| Date | WP | Ladder | Evidence |
|---|---|---|---|
| 2026-11-03 | WP0 land + threading + toolchain | code-complete | Merged fonebrew / porting-plan / store-listings branches; Kotlin 2.1.20, KSP 2.1.20-2.0.1, Room 2.7.1, SDK 36; blocking I/O on injectable IO dispatcher (`MainThreadNetworkTest`); Keystore `KeystoreCredentialStore` replaces security-crypto; `SingleSendSiteTest`; Play client/repository tests. |
| 2026-11-03 | WP1 multi-project | code-complete | Room v2 + `MIGRATION_1_2` validated by Room itself (`RoomRobolectricTest`) and by a JVM replay against the exported schema JSONs (`MigrationTest`); per-project clustering and project-scoped ids (`ProjectIsolationTest`); one `SyncWorker` fans out over every project's sources. |
| 2026-11-03 | WP2 reply governance | code-complete | `ReplyGovernanceTest`: 350 cap, quota guard, append-only reply log, developer-comment ingest, edit diff, checkpoint skip. `NEEDS-DEVICE-VALIDATION`: one real reply. |
| 2026-11-03 | WP3 email | code-complete | `ImapClientTest` (scripted socket server), `MimeParserTest`, `MailPollRepositoryTest` (routing, Inbox fallback, threading, dedupe), Gmail REST + PKCE tests. `NEEDS-DEVICE-VALIDATION`: real mailbox; Gmail redirect scheme. |
| 2026-11-03 | WP4 AI | code-complete | `AiTest`: suggestions are PENDING-only and change nothing until Accept; provider clients vs fake servers; versioned prompts; draft post-filter. `NEEDS-DEVICE-VALIDATION`: ASOM / cloud. |
| 2026-11-03 | WP5 export + UI | code-complete | `ExportConformanceTest` (networknt vs the ratified schema; byte-identical re-export); Hyle theme; glyph+label marks (`MarksTest`). **Not done:** Roborazzi screenshot tests (see below). |
| 2026-11-03 | WP6 release lane | code-complete | Icon (adaptive + monochrome), `versionName 1.0.0`, env-var signing, R8 release assembles, CI uploads debug APK and tag-gated release APK, `docs/VERIFICATION.md`. `OWNER-ACTION-NEEDED`: signing keystore + `CORE_SUBMODULE_TOKEN`. |
| 2026-11-04 | Home-screen widgets (owner ask, outside the WP plan) | code-complete | Six App Widgets (Open incidents, Incidents, Top incident, Replies, Sync status, Today), each with small / medium / large layouts, read-only, titles and counts only; per-channel sync outcomes recorded by `PollCoordinator` (`PollHealthStore`, noBackupFilesDir). 73 new JVM/Robolectric tests under `widget/` (175 in all); gate green. Review round (same day): titles are now an allow-list (a split or moved email/review anchor can't leak its subject or review title), data widgets print sync freshness ("Synced 14:05" / "◌ Stale · …") instead of draw time, layout heights are computed and checked so no size clips at 1×–2× font, plus a one-line size for 2×1. `NEEDS-DEVICE-VALIDATION`: rendering, resizing, taps, redraw timing on real launchers (`docs/widgets.md`). |

## Deviations from BUILD-SPEC (and why)

- **Export validation at runtime** is an in-app invariant check (`IssuesManifestBuilder.validate`); full JSON-Schema validation
  (networknt) runs in tests only, to avoid shipping Jackson + an unverified R8 path to a phone.
- **Quota guard** is a persisted fixed-window counter per package/method, not a token bucket (same limits, simpler to prove).
- **Roborazzi screenshot tests** were not added (no emulator/Compose screenshot infra in this environment). The glyph+label rule is
  enforced by `MarksTest` instead.
- **Gmail OAuth** uses the redirect `com.mbaliga.csapp:/oauth2redirect`; Google may demand the reversed-client-id scheme for
  Android client types. Unverified until a real consent flow runs.
- Licence: kept Apache-2.0; `claude/asystemofcells-redesign-s6yv4c` (PolyForm) left unmerged pending the owner's call (spec §2.2).
- Debug builds embed the exported Room schema JSONs as assets (needed by Room's migration test under Robolectric); release does not.

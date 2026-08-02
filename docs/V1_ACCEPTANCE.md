# V1 acceptance record

Date: 2026-08-02

## Release posture

This branch is a **private dogfood V1**, not a generally distributable public release.

That decision resolves the unanswered credential/security question without misrepresenting vendor guidance:

- Google Play is available for dogfood with an encrypted, device-stored service-account JSON credential and an explicit security warning. A public release remains blocked until native user OAuth replaces that path.
- GitHub uses a user-supplied fine-grained or classic personal access token stored through the Android Keystore-backed credential vault.
- Samsung implementation/fixtures are retained, but its runtime connector is disabled. It must not be enabled until the credential posture and documented GET-with-JSON-body transport are proven on a live seller account.
- Huawei, Aptoide, and email are discovery work, not V1 production connectors.

## Acceptance matrix

| Area | V1 result | Evidence / limitation |
|---|---|---|
| Reproducible source | Accepted | JDK, Gradle, AGP, Kotlin, KSP, SDK, and dependency versions are pinned; CI generates the canonical wrapper and records dependency locks. |
| Frozen Studio boundary | Accepted against supplied specification | Exporter keeps the frozen top-level/issue keys, anchor formula, project slug, file transport, and task boundary. The private Studio importer repository was not available, so a real cross-repository importer canary remains mandatory before calling the contract production-proven. |
| Release loop engine | Accepted | No fabricated/fake release fallback. When GraphRunner is absent, the UI exposes a clear unavailable state and manual incident authoring/editing. |
| Google Play live polling | Implemented; live canary required | Review API polling, pagination/checkpoints, missing author handling, existing developer reply state, and reply replacement semantics are implemented. Requires a real developer account for final live verification. |
| Google Play monthly backfill | Implemented; live canary required | Developer bucket ID is an explicit binding field; UTF-16 report parsing, official `ReviewPlace:id=` extraction, durable monthly ledger, ambiguous weak identity retention, and resumable pages are implemented. |
| GitHub Issues | Implemented; live canary required | Immutable API identity, PR filtering, Link pagination, exact-representation ETags, timestamp overlap, edited issue updates, routing per app, and credential-scoped quota state are implemented. |
| Deterministic triage | Accepted for V1 | Stable processing order, representative choice, tie handling, exact duplicate occurrence retention, persisted annotations, and explicit boundary-pending resolution are covered by deterministic tests/fixtures. |
| Cluster lifecycle | Accepted for V1 | Founding-anchor preservation, exported-ID survivor preference, complete tombstones, per-cluster export state, and explicit dismissal limitations are encoded. |
| First export safety | Accepted | Every never-exported cluster requires human approval before its permanent Studio ID crosses the file boundary. |
| Replies | Accepted for V1 scope | Human confirmation is mandatory; no automatic reply path exists; failed text remains persisted as a draft; Unicode code points are counted safely. Live Play mutation still requires a canary account. |
| Background work | Accepted as best effort | Unique work names prevent overlap; workers are bounded/resumable; UI and docs do not claim hourly/nightly service levels. |
| Credential/backup safety | Accepted for dogfood posture | Credentials use a Keystore-backed vault; database/credential data is excluded from cloud backup and device transfer; restore does not silently pair restored rows with a missing key. |
| Dependency policy | Implemented; CI is authoritative | Exact-version reviewed license ledger and lock checks fail closed for release runtime changes. |
| Local verification | Passed | `tools/verify_source.py` and pure Kotlin compilation of the domain/identity/similarity core pass against the materialized tree. |
| Full Android build | PR check is authoritative | The GitHub Actions job resolves Android/Gradle dependencies, runs JVM/Android unit tests, assembles the debug APK, and runs lint. |

## Non-goals retained from the frozen contract

The app does not create Studio tasks, write into Studio storage, operate a vendor-hosted inbox, auto-send replies, or fabricate model output.

## Before a public release

1. Replace device-stored Google service-account JSON with native user OAuth.
2. Run dated live poll/backfill/reply canaries on Google Play and a live edited-issue canary on GitHub.
3. Import a generated fixture into the exact private Studio importer and prove that re-import updates the same incidents.
4. Review the privacy disclosure for feedback/model data egress if a real GraphRunner/cloud-model adapter is added.
5. Keep Samsung disabled unless its release credential posture is redesigned and transport behavior is proven.

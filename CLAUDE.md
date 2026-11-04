# csapp — working notes (≤40 lines)
Phone-first support console: ingests Play reviews, GitHub issues, support email per project; clusters deterministically; AI *suggests*; human confirms every send; exports `issues-manifest.v1.json`.

## Hard rules (see BUILD-SPEC.md §1)
1. Nothing is sent to a user without explicit human confirmation. One send site: `PlayReviewsApiClient.sendReply`, reached only via `ReplyViewModel.confirmAndSend`. `SingleSendSiteTest` enforces it.
2. AI suggests, never decides (PENDING suggestions only; user taps Accept). No auto-reply, ever.
3. Clustering is deterministic (`AnchorIdGenerator`). 4. `sourceKey` is identity.
5. File export only, user-initiated. 6. Don't read Assay output. 7. No telemetry; creds only in Keystore.
8. Permissive licences only (no JavaMail). 9. Colour never carries meaning alone (glyph + label).
10. Report with the ladder: code-complete / verified / ready-for-owner-review; on-device = NEEDS-DEVICE-VALIDATION.
11. Don't rename: `csapp`, `com.mbaliga.csapp`; product name lives in `app_name` only.

## Build
- `export ANDROID_HOME=/opt/android-sdk LANG=C.UTF-8; ./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain`
- JDK 17+ ; AGP 8.9.1, Gradle 8.14.3, Kotlin 2.1.20, KSP 2.1.20-2.0.1, Room 2.7.1, compile/target SDK 36, minSdk 26.
- `shared-libraries` is a git submodule (crash-recovery only): `git submodule update --init`.
- All blocking I/O goes through `withContext(io)`; repos take an injectable `CoroutineDispatcher`.
- Room schemas are exported to `app/schemas/` — commit them with every entity change.

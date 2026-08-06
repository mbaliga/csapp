# csapp — product and architecture

A customer-support console for Android app developers. It ingests the two places users complain —
**Play Store reviews** and **GitHub issues** — clusters them into *incidents* so twenty reports of
one bug read as one problem, and lets a human triage and reply from the phone.

The load-bearing design decision is that **nothing is sent to a user without an explicit human
confirmation**, and **clustering is deterministic** — re-running it on the same data never mints a
new incident id or shuffles assignments.

- **Package**: `com.mbaliga.csapp`
- **Version**: 0.1.0 · minSdk 26 · targetSdk 34
- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Storage**: Room (SQLite), `EncryptedSharedPreferences` for credentials
- **Background**: WorkManager
- **Network**: OkHttp

---

## 1. Features

### Two ingestion sources

| Source | How | Backfill |
|---|---|---|
| **GitHub issues** | Issues API, polled per `owner/repo` with a `since` checkpoint and ETag | Not needed — the API exposes full history |
| **Play reviews** | Play Developer `reviews.list` API, authenticated by a service-account key | **Yes** — the API only exposes ~1 week, so older history is backfilled from Play Console monthly CSV report exports |

The Play backfill path is a genuine product requirement, not a nicety. `PlayMonthlyReportParser`
reads those exports, which are **UTF-16 with a byte-order mark** and RFC-4180-ish comma-separated
double-quoted fields — not the UTF-8 CSV anyone would assume.

Manual signals can also be authored in-app, for reports that arrived by email or word of mouth.

### Incidents: clustering signals into problems

A **signal** is one raw item — a review, an issue, a manual note. An **incident** is a cluster of
signals representing one underlying user-facing problem.

Clustering is local, deterministic and dependency-free (§4). Around it sit the human controls:

- **Severity** — LOW / MEDIUM / HIGH / CRITICAL, human-assigned
- **Status** — OPEN → ACKNOWLEDGED → RESOLVED, or DISMISSED, or MERGED
- **Merge** — fold several incidents into one when clustering split a problem
- **Split** — pull specific signals out into their own incident when clustering over-grouped
- **Mark recurring of** — link an incident to an earlier one it is a recurrence of

Merge and split exist because automatic clustering will be wrong sometimes, and the correct
response to that is a human affordance rather than a threshold nobody can tune from a phone.

### Replying, behind an explicit gate

Replies to Play reviews are a **two-step, human-confirmed flow**:

1. Draft text is edited and persisted locally as `DRAFTED`. Nothing leaves the device.
2. The human taps send and confirms the exact text in a dialog → `SEND_CONFIRMED` → the app sends
   **exactly that text** → `SENT` (or `FAILED`, which needs human attention).

`ReplyState` is a first-class enum and the transition to `SENT` has exactly one caller. Nothing in
this app transitions a reply to sent automatically.

### Export

`issues-manifest.json` v1 — a deterministic snapshot of every incident and its signals, written
through the system document picker. Incidents are sorted by id and timestamps are ISO-8601, so two
exports of the same data are byte-identical and diffable.

### Edit detection

When a poll re-fetches a signal whose body changed, the app records the edit (`editedAt`) while
**preserving `originalBodyHash` from first ingestion**. A reviewer who edits their review after a
reply cannot silently rewrite the record of what was replied to.

---

## 2. Interaction patterns

csapp uses **standard Material 3 navigation** — a `NavHost` with a stack, a top app bar, a FAB.
It is a console for a focused task, not an ambient browsing app, and it predates the
constellation's spatial-shell work.

### Screens and flow

```text
Dashboard (start)
├── ⟳ Run clustering now        (app bar)
├── ⇪ Export                    (app bar) → Export
├── ⚙ Settings                  (app bar) → Settings
├── + New manual incident       (FAB)     → Manual incident
└── tap an incident card        → Incident detail
                                   └── tap a signal → Reply
                                                        └── confirm dialog → send
```

### Interaction principles visible in the code

- **Destructive and outbound actions are gated.** Sending a reply requires a confirmation dialog
  showing the exact text. Nothing is sent as a side effect of navigating or drafting.
- **Clustering is manual-triggerable.** "Run clustering now" is on the dashboard app bar, so a
  user who has just polled does not have to wait for a background pass to see the result.
- **Polling is both scheduled and on-demand.** WorkManager polls every 30 minutes with a network
  constraint; Settings has "Poll now" per source for when the user wants an answer immediately.
- **The empty state teaches.** With no incidents, the dashboard says *"No incidents yet. Poll a
  source from Settings, or create one manually with the + button"* — it names the two ways
  forward rather than just reporting emptiness.
- **Incident cards carry state, not just a title** — severity, status, and a `manual` marker, so
  triage is possible from the list without opening anything.

---

## 3. Information architecture

```text
Signal                          ← atomic, immutable-ish, source-identified
│   sourceKey: "play:<reviewId>" | "github:<owner>/<repo>#<number>" | manual
│   type: PLAY_REVIEW | GITHUB_ISSUE | MANUAL
│   replyState: NONE → DRAFTED → SEND_CONFIRMED → SENT | FAILED
│
└──▶ clustered into ──▶ Incident
                        │   id: "inc_<hex>" (anchor-derived) or UUID (manual/split)
                        │   severity: LOW | MEDIUM | HIGH | CRITICAL
                        │   status: OPEN | ACKNOWLEDGED | RESOLVED | DISMISSED | MERGED
                        │   isRecurringOf: → another Incident
                        │   mergedInto:    → another Incident
                        └── signals[]

Checkpoints                     ← ingestion position, per source
├── GithubCheckpoint  (per "owner/repo": sinceMillis, ETag, lastSeenIssueNumber)
└── PlayCheckpoint    (per package: lastSeenReviewSubmitMillis, lastBackfillMonth)
```

The organising rules:

1. **`sourceKey` is identity.** It is stable and source-derived, never randomly regenerated. The
   same review or issue must always produce the same `sourceKey` across polls — that is what makes
   ingestion idempotent and de-duplication free.
2. **Signals are the unit of record; incidents are a view over them.** An incident carries human
   judgement (severity, status, links). Deleting the clustering and re-running it must not lose
   what a human decided — hence stable ids.
3. **Merge and split are recorded, not destructive.** A merged incident becomes `MERGED` with
   `mergedInto` pointing at its successor, rather than disappearing.

---

## 4. Software architecture

### Layering

```text
ui/          Compose screens + ViewModels (StateFlow → collectAsState)
    │
domain/      model · repository · clustering        ← pure, no Android
    │
data/        Room DAOs & entities · GitHub · Play · credentials · export
    │
work/        WorkManager pollers + scheduler
```

Dependencies are wired by a **manual `AppContainer`** — no DI framework. That is deliberate for
V1: the entire dependency graph is readable end to end in one file. Hilt can be introduced if the
graph grows.

### Module map

| Package | Responsibility |
|---|---|
| `domain/model/` | `Signal`, `Incident`, and the four enums that carry state |
| `domain/clustering/` | `ClusteringEngine`, `AnchorIdGenerator`, `TextSimilarity` |
| `domain/repository/` | `SignalRepository`, `IncidentRepository`, entity↔domain mapping |
| `data/db/` | Room database (v1), 4 entities, 3 DAOs |
| `data/github/` | Polling client, DTOs, poll-pass orchestration |
| `data/play/` | Reviews API client, service-account auth, monthly-report CSV parser |
| `data/credentials/` | `EncryptedCredentialStore` for the GitHub token and Play key |
| `data/export/` | `issues-manifest.json` builder and writer |
| `work/` | `GitHubPollWorker`, `PlayReviewPollWorker`, `PollScheduler` |
| `ui/` | Five screens, their ViewModels, navigation, theme |

### Clustering: the determinism guarantees

This is the part most worth understanding, because "the incident list reshuffled itself" would
destroy trust in the tool faster than any missing feature.

**Two-pass algorithm:**

1. Try to attach each new signal to an existing open incident, by similarity against that
   incident's current members.
2. Group whatever is left with *each other*, by union-find over pairwise similarity.

**What makes it deterministic:**

- Pass 2 iterates signals **sorted by `sourceKey`**, so grouping never depends on the order
  results came back from the network.
- Similarity is **Jaccard over stopword-filtered tokens** — no randomness, no learned weights, no
  wall-clock input. Threshold 0.30, time window 30 days.
- Incident ids are **anchor-derived**: `inc_` + the first 16 hex chars of SHA-256 over a single
  anchor signal's `sourceKey`. The anchor is the earliest member by `(createdAt, sourceKey)`.

That last choice is the subtle one. The id is deliberately **not** a hash of the full cluster
membership, because membership grows as new signals attach — an id derived from the member set
would change every time clustering re-ran. Anchoring on one deterministically-chosen signal fixes
the id at creation and never re-mints it.

### Ingestion: idempotence and checkpoint overlap

Both pollers keep a checkpoint, and both **deliberately rewind it slightly**. The GitHub
checkpoint holds `sinceMillis` a little behind the true last-seen timestamp, because GitHub's
`since` parameter is second-resolution: an issue updated in the same second as the last poll would
otherwise be skipped forever. Re-fetching duplicates is cheap precisely because ingestion is keyed
by `sourceKey` and idempotent.

`ingestOrUpdate` returns which of three things happened — `INSERTED`, `UPDATED_EDITED`,
`UNCHANGED` — by comparing body hashes, so an unchanged re-fetch costs one comparison and no
write.

### Security posture

- Credentials live in `EncryptedCredentialStore` (`androidx.security.crypto`), never in plain
  `SharedPreferences`.
- `allowBackup="false"` and `fullBackupContent="false"` — the database holds a Play service-account
  key's reach and user review content; neither should land in a cloud backup.
- Only `INTERNET` and `ACCESS_NETWORK_STATE` are requested.
- The reply path has exactly one send call site, behind a confirmation dialog.

### Testing

The pure layers carry the tests, which is the point of keeping `domain/` Android-free:

| Test | Guards |
|---|---|
| `ClusteringEngineTest` | Grouping behaviour and determinism |
| `AnchorIdGeneratorTest` | Stable ids, deterministic anchor choice |
| `IssuesManifestBuilderTest` | Deterministic, diffable export |
| `IncidentRepositoryTest`, `SignalRepositoryTest` | Merge/split/recurring, ingestion outcomes — against `FakeIncidentDao`/`FakeSignalDao`, so no emulator |
| `GitHubIssuePollingClientTest` | Request shape and response parsing |
| `PlayMonthlyReportParserTest` | The UTF-16-BOM CSV format |

CI runs unit tests, lint and a debug assemble.

---

## 5. Known limits

- **Replies are Play-only.** The reply flow sends through the Play Developer API; GitHub issues
  are ingested and triaged but not replied to from the app.
- **Clustering thresholds are compile-time constants** (0.30 similarity, 30-day window). There is
  no UI to tune them; merge and split are the human escape hatch instead.
- **Room schema is v1 with no migrations yet.** The first schema change will need one.
- **No push.** Polling is every 30 minutes via WorkManager; there is no webhook or FCM path, so
  the app is not a real-time alerting tool.
- **Single repo / single package.** Settings hold one GitHub `owner/repo` and one Play package.
- **This app predates the constellation's spatial shell.** It uses standard Material navigation.
  Adopting `dev.aarso:cell-shell` is a candidate once the two testable apps have validated the
  pattern.

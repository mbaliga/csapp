# csapp — the rooms

The app is redrawn on Fonebrew's room grammar. Same house as Assay and Orrery: origin at the centre, cardinal rooms by swipe or compass, Z axis for overview and detail, immersive and regular modes with full parity, Hyle tokens, marks that are composed cells, no state by colour alone.

Hard constraints carried over: `CLAUDE.md` rules 1–9 (one send site, AI suggests only, deterministic clustering, no telemetry, permissive licences, glyph + label), and the owner's taste notes (whitespace, no boxy grids, copy behind info icons, no decorative connectors, plant-cell geometry, Cohere as nearest reference).

## 1. Room grammar (shared)

- **Origin room** at the centre; system **Back** from any cardinal room returns to it; **Up** is hierarchical inside a room.
- **Planar swipes** to four cardinal rooms; a bottom **compass** for tap parity; TalkBack custom actions; hardware keys (`Alt+arrows`, `Esc`).
- **Z axis**: pinch out = overview of the room; pinch in on an item = detail; `+/−` and zoom-to-fit for parity.
- **Immersive** (chrome hidden) and **regular** modes, full parity, default regular.
- Predictive back; restoration of room + Z + scroll + open sheet + composer draft; RTL mirrors the spatial model.

## 2. Room map

```
                      ▲ North: INCIDENTS
                        clusters · filters · reply to all

   ◀ West: PROJECTS     ● Origin: INBOX          East: REPLIES ▶
     apps · sources       what needs you           drafts · sent log
     releases             (a to-do list)           edits · failures

                      ▼ South: SETTINGS
                        models · credentials · macros · sync
```

- **Z-out from Inbox** → **Pulse**: the week as cells — counts by source, sentiment trend, top incidents, the digest. Can be set as the origin in Settings (archetype C6).
- **Z-in on a signal or incident** → **Thread**: the signals in time, context strip, composer with the 350 counter, suggestion beside it, provenance badge.
- **Z-in on a project** → **Project detail**: sources as cells with their state, mail filter, releases, export.
- **Pocket actions** (bottom sheet from Inbox): `Add a signal by hand`, `Export manifest`, `Share digest`.

## 3. Room by room

### Inbox (origin)
A to-do list of what needs the human, ordered by the engine (newest unreplied first within the user's window), never by AI.

- Sections (headers only when non-empty): **Needs you** (new signals awaiting grouping — one tap to sync; pending suggestions to accept or reject; failed sends) · **Open** (incidents with unreplied signals) · **Quiet** (everything replied or older than the window, collapsed).
- One line per item: source mark (★ Play · ◉ GitHub · ✉ Mail) · app · one line of text · age. Tap zooms in. Swipe right = open composer; swipe left = snooze (local).
- Pull down = sync all projects (the only background-free network call; explicit).
- Digest line at the top when AI is configured, with provenance badge; nothing shown when off.
- Empty on first launch is impossible by design: J1 lands here only after one source has synced. If every source is disconnected later, the three source cells return in place of the list.

### Projects (west)
One cell per app. Each carries: name, package/repo, source marks with state (✓ connected · ✕ rejected · ◌ unchecked · ◐ syncing), unreplied count. `+` adds a project from a source (the J1 flow, re-entered). Zoom in → Project detail: sources (add/remove, mail filter editor with a live preview against the last 50 mails), **Releases** (from GitHub tags when connected, or typed), **Export** (Save file · Share to Fonebrew; byte-identical re-export note behind the info icon), **Quota** (Play replies remaining in the window, as a counter and reset time).

### Incidents (north)
Per project or all (selector in the header). Each incident one line: cluster mark (a composed cell whose segments are the sources it contains) · title (first signal's first line, editable) · size · severity mark · status (`new ◌ · grouped ◐ · replied ● · closed ○`). Filters as a disclosure, not a panel: status · source · app · sentiment · age. Long-press → multi-select → **Reply to all open** (J3) or **Close with release** (J4). Zoom in → Thread.

### Replies (east)
Tabs: **Drafts** (accepted suggestions and hand-written drafts not yet sent) · **Sent** (the append-only log; each entry shows the exact text sent, time, actor, and an edit mark if edited later) · **Failed** (with the reason and a retry). Zoom in on a sent reply → the edit-diff view (old vs new) and the signal it answered.

### Settings (south)
Cells, not toggles:
- **Models** — the shared Models room (§5).
- **Credentials** — GitHub token · Play service account · mailboxes (IMAP / Gmail with "Sign in with Google"); each cell shows state and "sent only to `<host>`"; **Clear all** with a hold.
- **Macros** — named replies with `{name}`, `{version}`, `{app}` variables; a tone line the AI must respect when drafting.
- **Sync** — interval (off / 15 / 30 / 60 min), battery and time-of-day conditions, "sync on open".
- **Appearance** — Guidance dial, origin room (Inbox / Pulse), immersive/regular, density, theme, sounds and haptics (Hyle preset), chart colours information-rich/monochrome.
- **About** — version, licence, the no-telemetry statement with the exact host list, diagnostics export (debug).

## 4. The Thread (Z-in)

The most-used screen; designed first.

- **Context strip** at the top: app · source mark · cluster size ("one of 12 saying the same") · version/device when present · language mark when not the UI language.
- **Signals in time**: the review/issue/mail and any developer replies already sent, as cells; the original always visible; translation beside the original when present.
- **Composer** at the bottom: 350 counter for Play (live, with the cap as a wall the text cannot cross); for GitHub the draft is a comment the user will post themselves (copy button, never a post button); for mail, a reply draft handed to the user's mail app via share (csapp sends only Play replies).
- **Suggestion** *beside* the composer, not in it: one accepted tap moves it in; provenance badge; "Regenerate" and "Summarise only" as the two alternatives; at Expert density the suggestion is collapsed by default.
- **Review & send**: a sheet showing the exact text and the counter; **Send** is a long-press with a growing cell ring; the first time ever, the one-line "public and permanent" note is shown; afterwards it lives behind the info icon.
- **Edit reply** on a sent Play reply: opens the composer with the old text; Review & send shows the diff.

## 5. The Models room (shared pattern across the constellation)

Three shelves, no tabs: **On this phone** (installed, radium glow when loaded, per-task defaults: `classify`, `draft`, `translate`, `digest`) · **Catalogue** (one-click downloads with SHA-256 pin, size, licence glyph, task glyphs, fit mark from RAM, resumable; progress is the cell filling) · **Bring your own** (URL or file; header read; detected card shown; hash pinned; confirm to install). Below: **Cloud (BYOK)** providers as watched objects with the alien glow; **ASOM** as a local provider when paired.

Provenance everywhere AI text appears: ◉ On-device (radium) · ◌ Cloud `<provider · model>` (alien) · ◎ Mixed · ○ Unknown — glyph + label always. Full catalogue and integrity rules: `02-ON-DEVICE-INTELLIGENCE.md`.

## 6. Marks and states (Hyle)

- Source: ★ Play · ◉ GitHub · ✉ Mail · ✎ Manual — with label.
- Incident status: new ◌ · grouped ◐ · replied ● · closed ○.
- Reply state: draft (dashed) · sent (filled) · edited (filled + pencil) · failed (strikethrough ✕).
- Sentiment: positive ▲ · neutral — · negative ▼ with label; luminance only, never hue.
- Connection: ✓ · ✕ · ◌ · ◐.
- Cluster mark: a composed cell whose segments are the sources it contains (plant-cell geometry, straight-ish walls, rounded junctions), from the ASOC Voronoi generator.
- Ground `#07070A`, violet `#8E7BFF`, radium `#CCEC75` local, alien `#3ED4FF` cloud. Light theme for print.

## 7. Motion, sound, haptics

- Cell-growth loader on cold start and first sync; under 900 ms; skippable; reduced-motion = fade.
- Syncing: under-the-surface movement in the source cell; failure: wall dims and contracts, then ✕.
- Earcons/haptics from the Hyle preset: tick on compass, two-step confirm on Send, low pulse on failed send. Off in immersive, on in regular, global toggle.
- Nothing heavy.

## 8. Accessibility

- Content descriptions name the effect ("Send reply; hold to confirm").
- 200 % font scale in every room (Roborazzi rows in CI); the 350 counter remains visible at 200 %.
- Compass, `+/−`, TalkBack actions for full parity.
- No state by colour alone; every mark carries a label.
- Both themes AA; dark primary.

## 9. Screen inventory

| # | Screen | Reached from | States |
|---|---|---|---|
| 1 | First launch | cold start | loader · three-cell diagram + dial · connect one source · first sync · source failed |
| 2 | Inbox | origin | needs-you · open · quiet · all sources disconnected · digest on/off |
| 3 | Pulse | Z-out Inbox (or origin) | one project · many · empty week · digest regenerate |
| 4 | Projects | west | empty · list · add from source |
| 5 | Project detail | Z-in project | sources · mail filter editor · releases · export · quota |
| 6 | Incidents | north | list · filters · multi-select · reply-to-all · close-with-release |
| 7 | Thread | Z-in signal/incident | Guide/Work/Expert · suggestion beside · translate · review & send · send hold · edit diff · failed |
| 8 | Replies | east | drafts · sent · failed · edit-diff |
| 9 | Settings | south | six cells |
| 10 | Models room | Settings / badge | three shelves · download · gated · BYOM card · BYOK provider · ASOM paired |
| 11 | Manual signal | pocket | form |
| 12 | Export / share | pocket | save · share · byte-identical note |

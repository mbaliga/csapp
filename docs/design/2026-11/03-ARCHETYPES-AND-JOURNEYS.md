# csapp — behavioural archetypes and their journeys

`docs/PRODUCT-AND-ARCHITECTURE.md` describes what the app does and the screens it has. It does not describe who opens it at 7 a.m. with a one-star review in their notification shade and what they need in the next ninety seconds. This document is that layer.

## 1. Three axes

| Axis | Low end | High end | What it changes in the UI |
|---|---|---|---|
| **Expertise** | First app, first review, does not know Play replies are public and permanent | Has run support for years, has macros, knows the 350-char cap by heart | Whether the reply composer shows guidance; vocabulary |
| **Trust posture** | *Delegate*: "write it for me, I'll tap send" | *Verify*: "I write; AI may only summarise what I'm replying to" | Whether a suggestion is pre-filled in the composer or shown beside it; whether AI is on at all |
| **Cadence** | *Reactive*: the notification drove them here | *Ritual*: Sunday evening, inbox to zero, digest written | Whether the origin room is a to-do list or a pulse board; notification design |

One control: the **Guidance dial** — **Guide / Work / Expert** — set at first run from one question ("How many apps are you supporting?" → `My first` / `A few` / `Many, or for others`) and changeable in Settings. It changes defaults only.

## 2. The archetypes

### C1 — The first-review developer
*Novice · Delegate · Reactive.* Shipped their first app, possibly built with an AI tool. A one-star review arrived; they have never replied to one and are not sure they should. Also the audience that does not know a Play reply is public, cannot be deleted by the reviewer, and counts against them if it is defensive.

- **Expectation:** "What do I say? Say it for me. Don't let me make it worse."
- **Fears:** making it worse; looking unprofessional; the review tanking their rating; replying to the wrong person.
- **What they will do wrong:** reply angrily; paste the AI draft without reading it; reply to every review including the good ones with the same line.
- **Stellar looks like:** opening straight into the review (deep link from the notification), the three-line context the engine already has (which app, how many others say the same thing, the device/version if the review carries it), one suggested reply *beside* the composer with a provenance badge, and a **Review & send** dialog that shows the exact text, the 350 counter, and "this is public and permanent" once. Send is a long-press. The first send ever is followed by the one-time hint: "Edit later from the same screen."
- **Rooms:** Inbox (origin) → Thread (Z-in). They do not need Projects or Pulse until their second app.

### C2 — The solopreneur maintainer
*Literate · Delegate with gates · Ritual.* One to five apps, revenue depends on them, phone-first between other things. Support is a chore. Knows the cap, has reply habits, does not want to open three apps (Play Console, GitHub, Gmail) to see one problem.

- **Expectation:** one inbox; batching; "these twelve reviews are the same crash — tell them all it's fixed in 1.4.2"; a Sunday digest that writes itself.
- **Fears:** missing a review for a week; duplicate replies; an AI reply going out unreviewed; a tool that needs a backend account.
- **What they will do wrong:** connect every source at once and drown; never set a mail filter so Inbox fills with newsletters.
- **Stellar looks like:** an incident (the cluster) is the unit of work, not the review; a **reply-to-cluster** flow that drafts one reply and lets them send it per signal with per-signal personalisation (name, version) and the single-send-site gate on each; a changelog follow-up ("we fixed this in 1.4.2 — reply to the 12 open ones?"); a weekly digest with trend glyphs; export to Fonebrew/Orrery as a share action.
- **Rooms:** Inbox, Incidents (north), Replies (east), Pulse (Z-out).

### C3 — The support veteran
*Expert · Verify · Ritual.* Has run support for a team or for years. Has macros, canned replies, a tone guide, SLAs in their head. Will turn AI off, or restrict it to summarising.

- **Expectation:** macros with variables; keyboard-fast composer; the raw signal (full review text, GitHub issue body, full email with headers) one tap away; append-only log they can audit; export that matches the schema exactly.
- **Fears:** a draft going out they did not write; the tool rewriting their tone; losing the thread of a reviewer who replied three times.
- **Stellar looks like:** Expert density opens on the signal body; macros are a first-class object (`{name}`, `{version}`, `{app}`); the edit-diff view on reply edits; "AI: summarise only" as a mode; the quota guard visible as a counter so they know how many Play replies remain in the window.
- **Rooms:** Incidents, Replies, Thread at Expert density, Settings → Macros.

### C4 — The community-first developer
*Literate · Verify · Ritual.* Open-source; GitHub issues are the support channel; reviews are secondary. Wants issues triaged, labelled, duplicate-linked, and the "we fixed this" closed loop.

- **Expectation:** incidents link to issues both ways; a suggestion to label/close is a suggestion, not an action; export is the issues manifest their other tools read.
- **Stellar looks like:** issue → incident → reply-draft-as-comment (accepted, then posted by them, never by the app — csapp does not write to GitHub); a cluster that shows "3 issues + 7 reviews" as one problem.
- **Rooms:** Incidents, Thread.

### C5 — The non-English-first developer
*Any expertise · Delegate for language · Reactive.* Supports an Indian-language or multilingual user base; reviews arrive in Hindi, Telugu, Tamil, Konkani, Hinglish. Wants to reply in the reviewer's language without trusting a cloud translator with their users' text.

- **Expectation:** detect language; show the review in their language and the original side by side; draft the reply in the reviewer's language from their own English; all on-device.
- **Fears:** a mistranslation that insults; sending user text to a cloud provider.
- **Stellar looks like:** on-device translation with a quality mark (the model's own confidence, shown as a mark not a number) and the original always visible; cloud translation only with an explicit watched-object tap.
- **Rooms:** Thread; Settings → Models (an Indic-capable model in the S/M size).

### C6 — The burnt-out developer
*Any · Delegate · Avoidant.* Has stopped reading reviews because they hurt. Needs pull, not push: a digest they can read without opening the inbox, sentiment as a trend not a feed, and permission to not reply.

- **Expectation:** "tell me if anything is on fire, otherwise leave me alone."
- **Stellar looks like:** Pulse as the room they open (the origin can be set to Pulse in Settings); a weekly digest that leads with the good; a "needs you" count that excludes anything older than the window they chose; no red anywhere, ever (already a hard rule).
- **Rooms:** Pulse (Z-out) as origin.

### The anti-archetype — the growth hacker
Wants sentiment scores by competitor, keyword-ranking, review-request prompts in their app, A/B tested replies, auto-replies to five-star reviews. AppFollow and Appbot sell that. csapp does not: no auto-send, no telemetry, no backend, no competitor scraping. The on-ramp says so in a line so they leave satisfied.

## 3. Journeys

### J1 — First launch (all; designed for C1)
1. **Cell-growth loader** → one screen: a three-cell diagram — *Reviews, issues and mail arrive · They group into incidents · You reply; AI only suggests* — one info icon per cell. Under it: *How many apps are you supporting?* → sets the Guidance dial.
2. **Connect one source.** Three cells: `Play reviews` (service-account JSON — "where to get it" behind the info icon, a share-sheet import) · `GitHub issues` (token) · `Support mail` (IMAP app password or Gmail). The user picks one. Project name is inferred from the source (package name / repo name / mailbox label), editable.
3. **First sync** runs as a visible under-the-surface motion; the first incidents grow in as cells. The compass overlay appears once, now.
Exit: one real incident on screen within three minutes. If the source fails (bad JSON, wrong scope), the cell shows ✕ + the exact error; the user is never dropped on an empty Home.

### J2 — The notification (C1, C2)
Tap → straight into the Thread of that signal (deep link `csapp://signal/<sourceKey>`). Context strip: app · cluster size · version/device. Suggested reply beside the composer if AI is on. Review & send (long-press). Back → Inbox.

### J3 — Reply to a cluster (C2, C3)
Incidents → incident → **Reply to all open** → one draft with `{name}`/`{version}` variables → per-signal preview list → send each (each passes the single send site; failures stay in the list with ✕). Progress is the cell filling.

### J4 — "We fixed this" (C2, C4)
Project → Releases (the app reads tags/releases from GitHub when connected, or the user types a version) → "Which incidents does 1.4.2 close?" → multi-select → reply-to-cluster with the changelog macro. Issues get a *suggested* comment the user posts themselves.

### J5 — Sunday ritual (C2, C6)
Pulse: this week's counts by source as cells, sentiment trend as a line (information-rich colours per source, togglable), top three incidents, the digest (AI, provenance badge, regenerate on tap). Share as text/markdown. Export the manifest.

### J6 — Failure and recovery (all)
Token rejected → the source cell on the project shows ✕ "token rejected" and Settings opens on that credential. Quota exhausted → composer shows the counter at 0 and the reset time; send is disabled with the reason inline. Send failed → the reply stays in Replies with ✕ and a retry; the log records the attempt. Never a toast that disappears.

### J7 — Language (C5)
A signal in another language shows the detected language mark; tap → on-device translation beside the original; the composer offers "reply in `<language>`"; the draft is shown in both. Cloud translation requires a tap on the watched-object glyph.

## 4. Expectations by archetype

| Need | C1 | C2 | C3 | C4 | C5 | C6 |
|---|---|---|---|---|---|---|
| Straight to the signal from a notification | ● | ● | ◐ | ◐ | ● | ○ |
| Suggested reply beside (not in) the composer | ● | ● | ○ | ◐ | ● | ◐ |
| Reply-to-cluster with variables | ○ | ● | ● | ● | ◐ | ○ |
| Macros as objects | ○ | ◐ | ● | ◐ | ○ | ○ |
| Raw signal body + headers | ○ | ◐ | ● | ● | ◐ | ○ |
| Changelog follow-up | ○ | ● | ● | ● | ○ | ○ |
| Weekly digest / Pulse | ○ | ● | ◐ | ◐ | ○ | ● |
| On-device translation | ○ | ◐ | ○ | ○ | ● | ○ |
| AI off / summarise-only mode | ○ | ◐ | ● | ● | ◐ | ○ |
| Auto-send | ✕ | ✕ | ✕ | ✕ | ✕ | ✕ |

## 5. Copy rules

- Explanations behind info icons; surfaces carry hints only.
- The word *incident* is kept; Guide density glosses it once: "several reviews, issues or mails that look like one problem".
- Never "Resolved" from the app's side; the states are the engine's: `new · grouped · replied · closed`.
- "This is public and permanent" is shown once per install, at the first Review & send, then lives behind the info icon on the dialog.

# Home-screen widgets

csapp ships six standard Android App Widgets (`AppWidgetProvider` + `appwidget-provider` XML + `RemoteViews`),
so any launcher can host them: Pixel Launcher, One UI, Nova, Orrery Home. No Glance, no Compose in the widget, no
new library, no new permission. Ladder: `code-complete`; everything on a real launcher is
**NEEDS-DEVICE-VALIDATION** (list at the end).

## Rules the widgets keep

- **Read-only.** A widget may open the app; it never sends, approves or changes a reply, never accepts an AI
  suggestion, never changes an incident, never starts a sync (BUILD-SPEC §1 rule 1). Taps are explicit,
  immutable `PendingIntent`s to `MainActivity` carrying a destination; the reply screen still needs its own
  confirm dialog to send. `WidgetReadOnlyTest` greps the widget code for send / accept / write / network calls.
- **Public surface.** Titles and counts only. No reviewer names, no email addresses, no review, mail or draft
  text. `WidgetDao` never selects `authorName`, draft text, sent text or metadata (the one suggestion payload
  it reads is an *accepted triage* payload, for its title). Incident titles go through `WidgetPrivacy`
  first, and it is an **allow-list**: clustering titles an incident with its anchor signal's title (or body),
  a monthly-report Play review carries the reviewer's own title, an email's title is its subject, and the
  title stays when that anchor is later split out or moved to another project. So a title is shown only
  when something vouches for it: it is the generic "4★ review" label; or a GitHub issue or manual signal
  still in the incident carries it; or the developer wrote it (manual incident) or accepted it (accepted AI
  triage). It is withheld when it holds an email address, when it matches a Play review's or email's
  title/body still in the incident, or when it starts the title or body of *any* Play review or email in the
  database (signals are never deleted, so this catches a split or moved anchor, and an accepted triage that
  kept the old title or echoed a subject line). Withheld titles read "Title hidden: it quotes a Play review /
  an email / it contains an email address / it may quote a review or an email". Residual: an accepted AI
  title that paraphrases a review (rather than quoting it) is shown as the developer accepted it.
- **Colour never carries meaning.** Severity, status and channel are always the app's own glyph + word
  (`Marks`: ◆ Critical, ◯ Open, ★ Play…); states are glyph + word (✓ OK, ✕ Failed, ◌ Stale, — Not set up).
  Colours are the Hyle tokens, day (`values/`) and night (`values-night/`); text passes 4.5:1
  (`WidgetResourcesTest`). Nothing is red or green.
- **Honest states.** First run ("No sources set up yet", tap opens Projects), empty (in the app's words),
  error ("Can't read the data"), withheld titles, and **freshness**: every widget fed by synced data says how
  fresh it is, measured by the last pass in which every source of each set-up channel worked (a channel that
  keeps failing is not fresh just because it was attempted). Medium and large print it in the header
  ("Synced 14:05", "◌ Stale · Tue 09:00", "◌ Stale · sync failing", "○ Not synced yet"); the small and
  one-line sizes print it only when it is stale or not synced yet. With no source set up nothing can lapse,
  so nothing is printed. Sync status prints a time per channel instead. Every printed time is absolute
  ("14:05", "Tue 14:05", "3 Nov") so it stays true however late the next redraw comes.
- **No engagement mechanics.** No streaks, badges, "you haven't opened…", no urgency wording.
- **Product name.** It lives in `app_name` only (CLAUDE.md rule 11): runtime strings take it as an argument,
  picker descriptions say "the app".
- **Accessibility.** Every widget root has a spoken description of everything it shows plus "Opens <app name>";
  tappable rows have their own description and a 48dp minimum height; text is in sp (≥12sp secondary, 14sp
  body), every TextView has `maxLines` + ellipsis; start/end only; bound titles are wrapped in Unicode
  isolates (`BidiFormatter`) so right-to-left titles don't reorder the line.

## The six widgets

Breakpoints are dp (width × height) at font scale 1.0. Each height is what that size's layout needs with every
slot it can show filled to its `maxLines` (and what the first-run / error layout of that size needs): a
`Skeleton` declares it and `WidgetResourcesTest` recomputes it from the layout XML (text lines at 1.4 × their
size, an upper bound that leaves room for the symbol fonts behind ◆ ★ ◉ ✕), so a layout can't be picked at a
height where it would clip. At a larger font scale every breakpoint height is multiplied by the scale (up to
2×), so bigger text picks a smaller layout instead of clipping; the app redraws when the font scale changes.
The launcher (API 31+) picks the largest that fits from one size-mapped `RemoteViews`; below API 31 the app
picks from the widget's options, always using the portrait size (min width × max height) whatever the
orientation at that moment, and redraws on `onAppWidgetOptionsChanged`.

Every widget has four sizes. **One line** (below the small height, e.g. a 2×1 in landscape): a single tappable
summary, severity word first, with a short "◌ Stale" / "○ Not synced" mark at the end when it applies.
Below the medium breakpoint the whole widget is one tap target.

| Widget | Template | Small (≥110×80) | Medium | Large | Tap |
|---|---|---|---|---|---|
| **Open incidents** | `stat` | total live (open + acknowledged) incidents, most severe as "◆ 1 Critical" | ≥180×138: + 2×2 counts by severity, freshness | ≥250×302: + up to 3 projects (most severe first) with their severity counts; footer "+N more" or scope | Home; a project row selects that project |
| **Incidents** | `list` | "◆ Critical · N open" + the most severe incident's title | ≥180×170: 3 rows (title; severity · status · project) | ≥250×256: 4 tappable rows, "+N more" | small: that incident; medium: Home; large rows: the incident |
| **Top incident** | `card` | severity + title | ≥180×134: + severity · status (· ↻ Recurring), project · N signals | ≥250×254: + channel counts in words, first seen / updated, "+N more" | the incident |
| **Replies** | `stat` → `list` | replies needing you (failed + send not finished + awaiting confirmation), first non-zero category | ≥180×170: count per category, incl. "✧ AI drafts to review" | ≥250×256: up to 4 reviews as "2★ review", state · project; footer "+N more", AI drafts, or "Sending still asks you to confirm" | Home; a large row opens that review's reply screen (no send) |
| **Sync status** | `list` | "Sync · 14:05" + overall (✓ All sources OK / ✕ N channels failed / ◌ Stale / ○ Not polled yet / — No sources set up) | ≥180×170: GitHub / Play / Mail, each ✓ OK · time, ✕ n of m failed · time, ◌ Stale · last poll, — Not set up | ≥250×256: + source count and "all OK <time>" per channel; footer "Polls every N min while online" | Home (pull down there to sync) |
| **Today** | `stat` | signals whose own source time is today, "since 00:00" | ≥180×138: + counts per channel (★ Play, ◉ GitHub, ✉ Email, ✎ Manual) | ≥250×302: + up to 3 projects with channel counts; footer "+N more", "N signals not yet grouped" | Home; a project row selects that project |

Default sizes: Open incidents and Today 3×2, Incidents 4×3, the others 4×2. All resize down to 2×1.
"Today" counts by the signal's own timestamp (review time, issue creation, mail date), not by when csapp
fetched it, so the first sync of an old backlog doesn't inflate it. "Open" means OPEN or ACKNOWLEDGED, the
Home screen's own definition. Archived projects are left out everywhere except Sync status, which mirrors
exactly what `PollCoordinator` polls (every enabled source). The tap that lands on Home when the destination
has no screen of its own (Replies, Sync status, Open incidents) is deliberate: there is no "all replies" screen;
Home shows the "Needs you" counts and pull-to-refresh.

## Data, refresh, taps (code map)

- `data/db/dao/WidgetDao.kt`: read-only queries (no entity, no schema change).
- `data/sync/PollHealth.kt`: `PollCoordinator` now counts attempts and failures per channel each pass and hands
  them to `PollHealthStore`, a tiny JSON file in `noBackupFilesDir` (times and counts only; no source names,
  no messages). Recording is best effort and can't fail a sync.
- `widget/WidgetSnapshotLoader.kt` reads one snapshot per redraw (privacy check applied here, for the top 5
  only); `widget/WidgetFaces.kt` turns it into what each widget shows at each size (pure, JVM-tested);
  `widget/SyncFreshness.kt` says how fresh the synced data is; `widget/WidgetRenderer.kt` copies a face into
  `RemoteViews`; `widget/CsWidgets.kt` decides when.
- Layouts (`res/layout/widget_*.xml`) are hand-maintained, LinearLayout + TextView only; the skeletons share
  slot ids (`w_title`, `w_asof`, `w_value`, `w_row_N`…) that `WidgetRenderer.idsFor` lists and
  `WidgetResourcesTest` checks. If you change a layout's text sizes, margins or `maxLines`, update its
  `Skeleton.needDp` (the test tells you the computed value).
- **Updates are pushed** (`updatePeriodMillis="0"`): Room's invalidation tracker on the tables the widgets read
  (so every write site is covered: sync, clustering, triage, status changes, reply drafts and sends, projects,
  mailboxes), every recorded sync pass and a poll-interval change ask for a redraw, coalesced to one per 1.5 s;
  providers redraw on add, resize (pre-31), time / time-zone / locale change, and the app redraws on a
  font-scale change. One inexact, non-waking `AlarmManager.set(RTC)` alarm (no exact-alarm permission) redraws
  at the next local midnight, and earlier when a set-up channel turns stale (2 × poll interval + 15 min after
  its last poll, or after its last full sync). It targets `WidgetRefreshReceiver`, which is not exported.
- The redraw observer starts in `Application.onCreate`, so it runs in every process start (also those for a
  widget broadcast or a background sync). It can never crash the process: if Room's observer fails to open
  (failed migration, full disk) it asks for a redraw (the widgets then show their error face) and retries with
  back-off; the widget coroutine scope drops any other failure.
- **Taps**: `MainActivity` reads two extras (`WidgetTap.parse` rejects anything malformed; a relaunch from
  Recents is ignored) and `CsAppNavHost` navigates: Home (selecting a project only if it exists and isn't
  archived; `MainActivity` is exported, so the id may come from anywhere), incident detail, reply screen, or
  Projects. Request codes are unique per widget, size and slot.
- Providers are exported (launchers must bind them); they read no extras and only redraw.

## Tests

`app/src/test/java/com/mbaliga/csapp/widget/`: `WidgetFacesTest` (every widget × size, one-line faces, empty /
first-run / error states, freshness lines, singulars, glyph+word, product name only via `app_name`),
`WidgetPrivacyTest` (the allow-list), `WidgetModelTest` (size picking incl. font scale and the pre-31 portrait
rule, taps, ranking, request codes, poll-health merge and store), `SyncFreshnessTest` (states, data freshness,
stale threshold, next redraw, time printing), `WidgetResourcesTest` (layouts carry every slot, each skeleton's
declared height covers its XML, breakpoints leave room at 1×–2× font, RemoteViews-safe views, ≥12sp, 48dp rows,
provider XML, manifest, strings, contrast), `WidgetReadOnlyTest`, and two Robolectric tests:
`WidgetDataRobolectricTest` (real Room: the snapshot queries, privacy end to end incl. a split and a moved
anchor and accepted / rejected AI titles, archived projects, per-channel sync counting) and
`WidgetRenderRobolectricTest` (inflates every widget × size through real `RemoteViews` and checks the views,
incl. the small layout's line budget; and, on API 26, that the pre-31 path picks the portrait layout in both
orientations).

## Owner-verify (NEEDS-DEVICE-VALIDATION)

Nothing below has run on a device; the build environment has no phone or emulator.

1. Each of the six widgets appears in the picker of Pixel Launcher / One UI / Nova / Orrery Home with its
   name, description and preview (API 31+ preview layout; vector preview below 31).
2. Each renders at 2×1 (portrait and landscape), 2×2, 3×2, 4×2, 4×3 and larger; resizing switches one line →
   small → medium → large with no clipped or overlapping text; the rounded corners match the launcher. The
   layout heights are computed, not measured: note any size where a line is still cut (OEM fonts and the
   symbol glyphs ◆ ★ ◉ ✕ can be taller than assumed).
3. Day and night: colours follow the system setting; text is readable on light and dark wallpapers.
4. 1.3× and 2× font scale: widgets switch to a smaller layout (within a few seconds of changing the setting
   while the app process is alive, otherwise at the next redraw); text ellipsizes, nothing overflows. A
   one-line widget at 2× may still clip its single line top and bottom.
5. TalkBack reads the whole widget description and each tappable row.
6. Taps: Open incidents / Today project rows select that project on Home; Incidents and Top incident open
   the incident; a Replies row opens that review's reply screen and **nothing is sent** until you confirm in
   the dialog; with the app already open, a tap navigates rather than stacking a second app.
7. Redraws: after a pull-to-refresh, a background sync, acknowledging an incident, saving a reply draft, and
   a failed send, the widgets update within a few seconds; Sync status shows ✕ for a failing source and turns
   ◌ Stale when the phone stays offline past two intervals, and the incident / reply / today widgets then
   say "◌ Stale · <last full sync>" too (also when one source keeps failing, e.g. a missing Play key).
8. Midnight: Today resets and "Tue 14:05"-style times appear, also after a time-zone change.
9. OEM battery managers (RedMagic / others): note whether the midnight and stale redraws arrive late or not at
   all; force-stop greys widgets on Android 15+ until the app is opened again (platform behaviour).
10. Privacy: with a real Play monthly backfill and real support mail, confirm no reviewer name, address,
    review text or mail subject appears on the home screen, also after splitting an email or review out of
    an incident and after moving one to another project.

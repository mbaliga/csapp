# On-device verification checklist (owner-run)

Everything below is **NEEDS-DEVICE-VALIDATION**: CI proves the logic with fakes and mock servers, not your
phone, your Play account or your mailbox. Tick each row on the RedMagic and fill the result.

| # | Check | How | Expected | Result |
|---|---|---|---|---|
| 1 | Install | Sideload `csapp-debug.apk` (or the signed release) | Opens to Home; no crash-recovery screen | ☐ |
| 2 | Project + GitHub poll | Projects → New → add `owner/repo`; Settings → GitHub token; Home → pull down | Issues appear as incidents with ◉ | ☐ |
| 3 | Play poll | Settings → service-account JSON; project → Play package; pull down | Reviews appear with ★; existing Play-Console replies show as sent | ☐ |
| 4 | IMAP poll | Settings → Add mailbox (IMAP, app password); project → Mail filter; pull down | Mail appears with ✉; replies join the same signal; unmatched mail goes to Inbox | ☐ |
| 5 | Gmail OAuth | Settings → Add mailbox (Gmail, your OAuth client ID) → Sign in with Google | Browser consent returns to csapp, toast "Gmail connected", next sync pulls mail. **Risk:** Google may require the reversed-client-id redirect scheme for Android clients; if consent fails with `redirect_uri_mismatch`, report the exact error. | ☐ |
| 6 | One reply | Open a Play review → Reply → type → Review & send | Dialog shows exact text; after Send, Play Console shows the reply; reply log has one row | ☐ |
| 7 | Edit reply | Same review → Edit reply | Dialog shows old-vs-new diff; Play shows the replaced text | ☐ |
| 8 | AI (ASOM) | Settings → AI → ASOM → Test; open an incident → Run triage | Suggestion shows ◉ On-device badge; nothing applied until Accept | ☐ |
| 9 | AI (cloud) | Settings → AI → a cloud provider + key → Test | Suggestion shows ◌ Cloud badge with provider and model | ☐ |
| 10 | Export | Project → Save file… and Share to Fonebrew; export twice with no changes | Both files byte-identical; named `issues-manifest.v1.json` | ☐ |
| 11 | Fonebrew import | Import the file in Fonebrew Core (**Core's import UI is not wired yet** — blocked until it is) | Import preview lists the issues | ☐ blocked |
| 12 | Crash recovery | Debug builds only: force a crash, relaunch | Recovery screen with the trace; then normal launch | ☐ |
| 13 | Dark/light + 200 % font | System settings | Readable; every severity/status has glyph + word | ☐ |
| 14 | Background sync | Settings → Sync → 15 min; leave the phone online | New items arrive without opening the app | ☐ |

## Desktop mode / keyboard (owner-run)

**NEEDS-DEVICE-VALIDATION.** Nothing here is compiled or run in CI against a window manager. Run on a device or
desktop-mode display with a hardware keyboard (for example the RedMagic with an external display, or ChromeOS).

| # | Check | How | Expected | Result |
|---|---|---|---|---|
| D1 | Freeform resize | Open csapp in a freeform / desktop window; drag the edges, then rotate or snap | Window resizes without restart or crash; state kept | ☐ |
| D2 | 840 dp cap | Widen the window past about 840 dp | Content stays one centred column 840 dp wide; no stretched rows | ☐ |
| D3 | Esc | On Incident, Projects, Settings: press Esc | Goes back one screen; on Home Esc does nothing | ☐ |
| D4 | Ctrl+, | Press Ctrl+, from Home or Projects | Opens Settings (one copy; pressing again adds none) | ☐ |
| D5 | Ctrl+1 / Ctrl+2 | Press Ctrl+2 then Ctrl+1 | Projects, then back to Home | ☐ |
| D6 | Ctrl+N | Press Ctrl+N from Home | Opens the new-signal screen | ☐ |
| D7 | No send by keyboard | On the reply screen press Enter, Ctrl+Enter, Esc | Nothing is sent; the dialog's Send button remains the only way | ☐ |

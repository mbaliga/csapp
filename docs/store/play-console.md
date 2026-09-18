# CS App — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

| | |
|---|---|
| applicationId | `com.mbaliga.csapp` |
| Version at time of writing | `0.1.0` (versionCode `1`) |
| Category | **Business** |
| Contact email | `csapp@asystemofcells.com` |
| Website | `https://asystemofcells.com/csapp` |
| Privacy policy | `https://asystemofcells.com/csapp/privacy` |

## ⛔ Read this before anything else

**This app has never compiled.** `main` carries only a LICENSE and docs. Every
feature branch stores its source as split base64 or tar "transport" blobs that have
never once successfully reassembled, across 45+ same-day fix commits
(`CONSTELLATION.md`, D-W). Zero lines of its real code have ever been built or read
as source.

There is an open owner decision: **recover the blobs, or discard and rebuild clean**
from the existing `cs-app-v1-build-plan.md` spec. Until that lands there is nothing
to submit, and this sheet is a placeholder so that the listing is not what blocks
things later.

**Two further things are undecided, and this sheet does not decide them:**
- **The product name.** "CS App" is explicitly a working name in the app's own build
  plan, not a decision (`NAMES.md`). The title here is a placeholder.
- **Whether this belongs on a public store at all.** It is a personal developer
  tool for one person's own portfolio. **Play's internal or closed testing track,
  or a plain sideload, may be the right home for it**, in which case most of this
  sheet never gets used. Decide that before doing listing work.

## If it does go to a public listing

### App access — this app genuinely needs it
This is one of only two apps in the house that **cannot** answer "all functionality
available without special access". It requires:
- a Google Play Developer API **service account key**, and
- a **GitHub token**.

Play requires working test credentials plus written instructions so a reviewer can
see the app function. Prepare a **throwaway GitHub account with one dummy repo
holding a few issues**, and step-by-step instructions. **Never give a reviewer a
real Play service account key**, which would grant access to the whole developer
account.

### Data safety
The app handles other people's content: **Play reviews and GitHub issues written by
your users, including their review text and public usernames.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** (nothing is transmitted to us; there is no server of ours) |
| Encrypted in transit? | Yes |
| Deletion? | Users can delete data in the app |

The nuance to state in the policy rather than hide: the app fetches data **from
Google and GitHub, with the operator's own credentials**, and stores it locally. The
"user" for Data safety purposes is the app's operator, not the reviewers whose text
is fetched. Both facts belong in the privacy policy.

### Permissions
| Permission | Why |
|---|---|
| `INTERNET` | Play Developer API and GitHub API calls. |
| `ACCESS_NETWORK_STATE` | Avoid polling while offline. |

### targetSdk
`targetSdk = 34`, which is behind Play's current floor. Bump before submitting.

### Content rating
- Category `Utility, Productivity, Communication, or Other`.
- **"Does the app display user-generated content?"** → **Yes, in a limited sense**:
  it displays review and issue text written by third parties. It is not shared
  onward and there is no social surface, but answer honestly if asked; a business
  tool that shows fetched support tickets is not a UGC platform.
- Expected **Everyone**.

## Pre-submit checklist

- [ ] Owner decision on recover-or-rebuild. Nothing else matters until then.
- [ ] Decide the product name.
- [ ] Decide whether this is a public listing at all, or internal-track only.
- [ ] Bump `targetSdk`.
- [ ] Prepare throwaway reviewer credentials that are not your real ones.
- [ ] Screenshots with **fabricated or anonymised** review text. Do not publish real
      users' complaints, usernames or avatars in a store listing.

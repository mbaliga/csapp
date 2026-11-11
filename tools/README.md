# tools

- `check_send_sites.py` — stdlib-only guard for the single-send-site rule (BUILD-SPEC section 1, rule 1). Asserts
  `sendReply`, `markReplySent` and `sendConfirmedReply` each have exactly one call site in production source and
  that `:reply` appears only in `PlayReviewsApiClient.kt`. Needs no Android SDK:
  `python3 tools/check_send_sites.py --root .` (exit 0 pass, 1 violation). It complements, and does not replace,
  `SingleSendSiteTest`. It is not yet wired into a workflow (ci.yml stays untouched until `:desktop` lands).

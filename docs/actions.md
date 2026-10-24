# Actions and weekly recommendations

Plan → Actions provides Active, Suggestions and History. Home displays up to three selected-date attempts between Progress overview and Week Score without changing dashboard scores.

Every Europe/Madrid calendar day is eligible from the start date. Today pending preserves yesterday's streak; an unchecked past day breaks it. Reaching the consecutive-day target completes the attempt. Corrections recompute completion without deleting other checks. Stop preserves its original date and history. Goal and start date lock after the first check-in, even after undo. Starting again creates a separate attempt; there is no delete.

## Recommendations

Suggestions use the latest reflected week and current Coach plan; older reports retain their recommendations. One semantic mapper supplies app, HTML and plain text: next-week actions first, then domains in fixed order; trimmed case-insensitive exact duplicates retain first wording and every provenance label.

Source keys combine stable source identity with the SHA-256 digest of normalized original wording; URLs contain no recommendation text. Resolution requires the authenticated owner and matching current text. Reordering remains valid and replacement produces a stale-link message. Activation reuse intentionally matches normalized exact wording across owned sources; this equivalence differs from source-specific resolution. Display edits retain the original fingerprint. Explicit restart creates another attempt.

Email GET opens a draft. Scoped login return preserves its opaque source key; authenticated Save validates the source again and creates or returns the existing attempt. Actions do not mutate routines, achievements, notifications or ACTIVE_PLAN.

## HTTP and weekly evidence

Session-authenticated /api/actions supports selected-date Active/History reads, individual reads, Suggestions/source resolution, create/edit, stop/restart, and PUT/DELETE /{id}/checkins/{date}. The server owns status, streaks, capped progress and locks. Owner-row locking serializes mutations; database uniqueness enforces one check per attempt/date.

New schema-version-6 weekly summaries freeze current, previous-week and 52-week nutrition evidence: recorded/completed dates, nullable intake, independent coverage/estimates, current targets, and each date's effective calorie budget and exact macro allocation. Completed days with full applicable coverage determine each mean. Target means use the same eligible dates and round only their final result. Historical comparisons say current targets captured when saved. Existing snapshots remain unchanged.

The optional WEEKLY Coach snapshot extension adds no operation or write capability and preserves reflection and ACTIVE_PLAN contracts.

## Verification boundaries

Candidate database, HTTP and browser tests cover mutations and multipart email parity. Synthetic production attempts cannot be deleted, first checks leave permanent locks, and email delivery cannot be undone. Live acceptance stays read-only; actual user-intended attempt/check-in and email-send acceptance is recorded separately after release.

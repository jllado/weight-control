# Implement rolling warning metrics
User approved direct end-to-end implementation, not a readiness-only handoff.
## Approved interface
Follow warning-goals-v5 mockup: name; original creation date in Europe/Madrid labeled Triggered; Last X days; one or more Current/Goal rows; meaningful green/orange/neutral status and one Coach sentence <=120 characters. No prior period, Change/delta column, Details, History, or explanatory footer. Preserve evidence and revision storage internally.
## Rules
Reassess every DAILY and WEEKLY reflection and explicit warning review. Include today only when all relevant records are complete, otherwise yesterday; use X consecutive calendar days without skipping missing records. Preserve exact dates and accessible assessment timestamp. Ordinary reads never mutate lifecycle.
Coach chooses stable source-backed metric definitions, explicit goals and window; changes need documented reasons grounded in personal goals/user criteria. Structured identity, source, aggregation, unit, comparator/goal, measured value and coverage. Backend validates and evaluates goals. Missing values/coverage/goals are UNKNOWN, never zero, and cannot resolve.
Sleep: 7 days, total asleep >=151200 seconds (42h) AND in bed >=176400 seconds (49h), raw records, no 7/7 daily-goal requirement. Nutrition: 7 days, mean calories <=2500 kcal/day with complete days; no hidden qualitative gates. All goals met resolves existing warning at review; any missed goal remains active; reject new all-met warning. No invented scores/clinical targets; excess over sleep minimum is not automatically better.
## Backend/Coach
Shared DAILY/WEEKLY review transaction, user lock first then weekly summary lock, exact owned active IDs/versions, atomic warnings/reflection/success-notification rollback. Apply metric contract/evaluator to standalone create/update/resolve and idempotency. Remove prior-comparison requirement for new reviews. Weekly nullable JSON snapshot migration; immutable trigger metadata; legacy reads preserved without inventing metrics. Keep current review evidence separate from historical reflection cutoff and WEEKLY-only confirmation. Existing Actions only, <=30 operations and instructions <=8000 chars; old write payloads fail clearly.
## Verification/delivery
Focused meaningful red/green backend/browser tests: rolling day boundaries/completeness/Madrid DST, equality, multiple goals, missing data, sleep short nights but totals met, nutrition above/equal/below, all standalone and reflection paths, concurrency/owner/stale/rollback/idempotency/legacy snapshots. Real UI mobile/desktop evidence and independent QA. Full combined release gate via check scripts, source commit/push, production deploy and independent tree/readiness verifier; private Coach schema/instructions publication and published configuration verification. Reassess existing current warnings from fresh raw records through supported versioned paths, no historical reflection rewrite/unrelated health writes. Verify production and clean reversible acceptance effects. Update self-contained lessons. Finalize task only after all targets and existing-warning verification succeed.

## Execution tasks
1. Metric contract/evaluator and standalone warning paths; focused backend tests.
2. Shared DAILY/WEEKLY atomic review and snapshots; controller/persistence tests.
3. Compact UI and browser journeys; Coach contracts/instructions and validation.
4. Independent candidate QA, full release gate, deployment, GPT publication, current-warning acceptance and cleanup.


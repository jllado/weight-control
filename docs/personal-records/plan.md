# Personal Records Plan

## Purpose

Help the owner recognize concrete progress without turning the application into a generalized analytics platform.

Delivery status: phases 1–6 are implemented; phase 7 awaits owner-assisted production acceptance.

The first release focuses on the motivating examples: lifting more weight, completing more repetitions or a longer hold at a given load, improving cardio performance, and reaching body-composition records.

## Success criteria

- Existing workouts and body measurements produce an accurate all-time record history without manual backfill.
- New firsts and strict all-time improvements are visible immediately after saving.
- Editing or deleting source data automatically corrects current records and progression history.
- Records are useful in context on Home and during workout entry, not only on a separate page.
- Each phase is independently deployable and does not require later phases to remain correct.

## Shared record rules

- Source measurements remain authoritative; record history is calculated from them rather than duplicated in a record-event table.
- Keep a rebuildable current-record snapshot for fast contextual reads, except routine best streaks, which are derived directly from routine check-ins to keep rapid routine writes independent.
- Process observations chronologically using their recorded date/time and stable source order.
- The first observation establishes a record.
- A strictly better observation creates an improved record.
- An observation equal to the current best creates a tied record event.
- Ties appear in history but do not trigger the WIN celebration.
- Backfill, source corrections, deletions, and configuration changes never trigger a celebration.
- A newly saved historical entry triggers a celebration only when it becomes the current all-time record.
- Inline summaries always show the current all-time record, including while viewing an older dashboard date.
- Missing values create no observation, while recorded zero values remain valid.
- Minimum and maximum describe numerical extremes and must not be presented as medical judgments.

## Workout comparison rules

- Treat `null` and `0 kg` as the same bodyweight or no-added-load group.
- Normalize external loads to the stored two-decimal precision before grouping.
- For repetition exercises, track the heaviest load and maximum repetitions at every exact load.
- For timed exercises, track the heaviest load and longest duration at every exact load.
- For cardio exercises, track per-exercise maximum interval duration, speed, distance, incline, and resistance.
- A set or interval may establish more than one record in the same workout.
- Session totals, volume, calories, and heart-rate records are deferred to the derived-metrics phase.

## Delivery phases

### Phase 1: Focused v1 — workouts and body

Implement a source-derived personal-record engine and a materialized current-record snapshot for the following fixed record definitions:

- Workout records defined by the workout comparison rules above.
- Minimum body weight.
- Minimum fat mass and fat percentage.
- Maximum muscle mass and muscle percentage.

Add authenticated current-record and progression-history endpoints. Record-capable workout and weight writes return the saved result plus newly achieved first or strict current records.

Add a Records page with Current and History tabs, contextual records during workout entry, `PR` and `Tied PR` annotations in workout views, compact body-record summaries on Home, and a global WIN animation.

Do not add settings, BMI, deltas, health metrics, habits, rolling metrics, Coach integration, or progression-event persistence in this phase.

### Phase 2: Direct health metrics

Extend the same engine with fixed defaults for directly recorded values:

- Blood-pressure minimum and maximum for systolic and diastolic values.
- Minimum total cholesterol, LDL, and triglycerides; maximum HDL.
- Maximum mood.
- Maximum total, deep, REM, and light sleep; minimum awake time and sleep heart rate; maximum HRV.
- Optional minimum and maximum meal calories and macros.
- Optional minimum and maximum daily calories, with daily macro totals only when every contributing meal records that macro.

Nutrition records are disabled by default because calorie tracking is more useful in the Calories area; users can enable individual nutrition records on the Records page. Keep nutrition records out of Home summaries, while other direct metrics retain their existing Home summaries.

### Phase 3: Metric configuration

Introduce stable catalog keys and a user-owned settings table.

Allow every supported metric to use `DISABLED`, `MINIMUM`, `MAXIMUM`, or `BOTH`. Default to body composition and BMI, heaviest exercise load, and routine milestones; keep every other metric opt-in. Store only user overrides and recalculate history immediately after a setting changes without celebrating.

Add a Settings tab to the Records page and expose authenticated catalog and atomic settings-update endpoints.

### Phase 4: Behavior records

Add records for habit completions and streaks, routine best streaks, and decision totals, rates, and WIN streaks.

Routine history already has dated check-ins. Add dated habit check-ins for future completions and seed each legacy habit with one baseline containing its existing total, current streak, best streak, and last date because earlier progression cannot be reconstructed safely.

Allow post-migration habit check-ins to be undone so corrections rebuild their records consistently. Routine current records show the exact best streak, while routine progression and achievements occur only at 21, 60, 90, 180, 365, and each later 365-day milestone.

### Phase 5: Derived metrics

Add useful calculated series only after the direct metrics have demonstrated value:

- BMI and body/vital changes.
- Exercise and workout session totals, set or interval counts, strength volume, calories, and average heart rate.
- Dashboard completion counts, percentages, scores, and statuses.
- Completed Saturday–Friday weekly metrics and completed calendar-month metrics.
- Existing 30-day rolling values and changes when their calculators return recorded evidence.

Derived series inherit the source metric's direction unless they are deltas or target differences, which default to both. Exclude future projections and never compare incomplete weekly or monthly periods with completed periods.

### Phase 6: Coach integration

Add a read-only `RECORDS` Coach domain after the in-app feature is stable.

Return enabled current all-time records plus record progression inside the requested inclusive date range. Update the Coach catalog, context DTOs, Action schema, GPT instructions, tests, plan, and TODO while preserving reflection contracts.

The Coach must describe records as observed extrema and must not infer that a minimum or maximum is healthier.

### Phase 7: Final acceptance

Validate record correctness, correction behavior, ownership, privacy, notification frequency, mobile presentation, query performance, and compatibility with existing histories and Coach/reflection workflows.

Review actual use after every phase and complete one phase before starting the next. Later phases must not be pulled into an earlier phase merely because the record engine can support them.

## API direction

Phase 1 introduces:

- `GET /api/personal-records/current` with optional domain, metric, and exercise filters.
- `GET /api/personal-records/history` with the same filters, an opaque exact-event key, and pagination.
- A shared mutation envelope containing `result` and `recordAchievements` for record-capable creates and updates.

An achievement contains an opaque event key, the stable metric key, domain, direction, kind, value, previous value, unit, date, subject, load or other qualifier, and current-record state.

Phase 3 adds:

- `GET /api/personal-records/catalog`.
- `PUT /api/personal-records/settings` as an atomic replacement of user overrides.

Delete operations keep their existing empty response contracts and only affect later record reads.

## User experience

- The Records page groups current records and history by domain and exercise.
- Workout entry shows the heaviest load and the relevant repetitions or duration record for the selected exercise and load.
- Workout displays mark record-setting sets and intervals as `PR` or `Tied PR`.
- Home shows compact all-time body records and functional routine text such as `Best: 60 days`; it does not repeat the routine record summary table.
- Move the existing WIN component to the global application shell so records saved from any route can use it.
- Play one WIN animation per successful save without opening a blocking record dialog.
- Persist one bell notification per achievement; clicking a personal-record notification dismisses it and opens the exact history event.
- Personal-record celebrations reuse the visual only and never create a WIN decision outcome.

## Privacy and compatibility

- Every read and calculation is user-owned and must not expose another user's source data or records.
- Record APIs return display context but omit unrelated health data, storage paths, authentication values, and internal projection details.
- Existing list/read contracts remain unchanged.
- Mutation-envelope changes are coordinated with the current frontend services and their tests in the same phase.
- Current records are read from a user-owned snapshot that is transactionally rebuilt under a per-user lock after relevant source mutations; routine best streaks are derived live and do not participate in that shared rebuild.
- Progression history remains source-derived so edits and deletions cannot leave stale historical events.
- Coach integration is read-only and remains deferred until phase 6.

# Personal Records TODO

Current milestone: **Milestone 6 complete; milestone 7 owner acceptance in progress**

Last updated: **2026-08-22**

This checklist implements the [personal records plan](plan.md). Complete and validate one milestone before starting the next. Update `Current milestone` and `Last updated` whenever work moves to another milestone.

## 0. Planning pack

- [x] Document the shared record semantics and workout comparison rules.
- [x] Split delivery into independently deployable phases.
- [x] Define the focused v1 scope and explicit exclusions.
- [x] Add the feature plan and TODO to the documentation index.

Definition of done: the feature has one linked plan, one gated checklist, and a focused first milestone that excludes later metric families.

## 1. Focused v1 — workouts and body

Dependencies: milestone 0.

- [x] Define stable DTO types for record metric, direction, event kind, current record, history event, and save achievement.
- [x] Implement source-derived record calculation without adding a record-event table.
- [x] Add and backfill a rebuildable current-record snapshot from existing weights and workouts.
- [x] Calculate the heaviest load and repetitions or duration per exact exercise load.
- [x] Normalize `null` and `0 kg` into the same no-added-load comparison group.
- [x] Calculate cardio interval records for duration, speed, distance, incline, and resistance.
- [x] Calculate minimum weight and fat records and maximum muscle records.
- [x] Return first, improved, and tied progression events in stable chronological order.
- [x] Recalculate records correctly after backdated creation, editing, and deletion.
- [x] Add authenticated current-record and paginated history endpoints with domain, metric, and exercise filters.
- [x] Return `result` and `recordAchievements` from workout and weight creates and updates.
- [x] Keep delete responses unchanged and prevent deletion-driven replacement records from celebrating.
- [x] Add a Records route and navigation item with Current and History tabs.
- [x] Show exercise records in workout entry and refresh load-specific context when the load changes.
- [x] Add `PR` and `Tied PR` annotations to workout diary and Home workout displays.
- [x] Add compact body-record summaries to the relevant Home panel.
- [x] Move `WinCelebration` to the global app shell and follow it with a dialog listing achieved records.
- [x] Celebrate firsts and strict current improvements only; do not celebrate ties or historical backfill.
- [x] Add service, controller, ownership, correction, comparison, and response-contract tests.
- [x] Add end-to-end coverage for the Records page, workout context, badges, and global celebration flow.
- [x] Confirm that no settings, BMI, deltas, health metrics, habits, calculated period metrics, Coach changes, or progression-event persistence entered the v1 diff.

Definition of done: existing and future workout/body data produces correct all-time records and progression, record-setting saves receive useful feedback, corrections stay accurate, and the focused scope passes all checks.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 2. Direct health metrics

Dependencies: milestone 1 and a review confirming that the v1 record views and notifications are useful in regular use.

- [x] Add minimum and maximum systolic and diastolic blood-pressure records.
- [x] Add fixed-direction total cholesterol, HDL, LDL, and triglyceride records.
- [x] Add mood records.
- [x] Add direct sleep duration, stage, awake-time, heart-rate, and HRV records.
- [x] Add meal calorie and macro records when values are present.
- [x] Add daily calorie records and complete daily macro-total records.
- [x] Preserve recorded zero calories and omit absent optional values.
- [x] Extend record groups, filters, Home summaries, and mutation achievements for the new domains.
- [x] Add tests for fixed directions, optional data, complete macros, correction behavior, and ownership.
- [x] Confirm that rolling, weekly, monthly, projected, behavior, and Coach metrics remain excluded.

Definition of done: directly recorded health and nutrition values participate in the same reliable record workflow without introducing calculated-series complexity.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 3. Metric configuration

Dependencies: milestone 2.

- [x] Add a Flyway migration for user-owned personal-record setting overrides.
- [x] Define the complete metric catalog with stable keys, labels, units, precision, and defaults.
- [x] Support `DISABLED`, `MINIMUM`, `MAXIMUM`, and `BOTH` for every catalog metric.
- [x] Store only overrides so future catalog metrics receive their application default.
- [x] Add authenticated catalog and atomic settings-update endpoints.
- [x] Add the Settings tab to the Records page with reset-to-default behavior.
- [x] Recalculate current records and history after settings changes without celebration.
- [x] Add migration, validation, ownership, default, override, and settings UI tests.

Definition of done: the owner can control which extremes matter without changing source data or creating stale record history.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 4. Behavior records

Dependencies: milestone 3.

- [x] Add Flyway tables for dated habit check-ins and legacy habit baselines.
- [x] Seed one baseline per existing habit from its stored totals and streak fields without inventing dates.
- [x] Store future habit completions as dated check-ins and rebuild aggregate fields from the baseline plus check-ins.
- [x] Add undo support for post-migration habit check-ins.
- [x] Add habit and routine completion-total, current-streak, and best-streak metrics.
- [x] Add decision total, rate, and WIN-streak metrics.
- [x] Treat every enabled cumulative-count increase as a record.
- [x] Extend Records and Home behavior summaries and global achievements.
- [x] Add migration, baseline, check-in, undo, streak, count, decision, correction, and ownership tests.

Definition of done: behavior progression is exact from migration onward, legacy limitations are visible, and corrections preserve trustworthy records.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 5. Derived metrics

Dependencies: milestone 4 and a review of notification frequency with cumulative behavior records enabled.

- [x] Add BMI and body/vital change series.
- [x] Add exercise and workout totals, set or interval counts, strength volume, calories, and average heart rate.
- [x] Add dashboard counts, percentages, scores, and statuses.
- [x] Add completed Saturday–Friday weekly series.
- [x] Add completed calendar-month series.
- [x] Add existing 30-day rolling series and changes only when their calculators return recorded evidence.
- [x] Inherit directions from source metrics and default deltas or target differences to both.
- [x] Exclude projections and incomplete weekly or monthly periods.
- [x] Add tests for period boundaries, completeness, missing evidence, direction inheritance, and correction propagation.

Definition of done: calculated records compare equivalent observed periods, remain traceable to source data, and do not mistake projections for achievements.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 6. Coach integration

Dependencies: milestone 5 and stable in-app record semantics.

- [x] Add `RECORDS` to the Coach domain catalog and availability metadata.
- [x] Return enabled current all-time records plus progression inside the requested date range.
- [x] Exclude unrelated health data, internal identifiers, settings internals, and source storage details.
- [x] Update the Coach Action schema and GPT instructions for record questions.
- [x] State that extrema are observations and not evidence that a value is healthier.
- [x] Preserve existing reflection input and response contracts.
- [x] Update the Coach architecture plan and TODO with the delivered integration.
- [x] Add catalog, context, range, privacy, schema, and reflection-regression tests.

Definition of done: the Coach can answer personal-record questions from minimal read-only context without changing established Coach or reflection behavior.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 7. Final acceptance

Dependencies: milestones 1–6.

- [x] Verify all configured record directions against representative historical data.
- [x] Verify backfill, backdated entry, tie, improvement, edit, delete, and undo scenarios.
- [x] Verify exercise/load grouping, bodyweight normalization, and multi-record workouts.
- [x] Verify every record endpoint and mutation response enforces user ownership.
- [x] Audit record and Coach responses for unnecessary health data and internal fields.
- [ ] Verify Home, Records, and workout views on mobile and desktop widths.
- [x] Verify reduced-motion behavior and that one save produces only one WIN animation.
- [ ] Measure current and history query performance with the owner's complete retained dataset.
- [x] Verify existing histories, dashboard workflows, workouts, Coach, and reflections remain functional.
- [x] Update the plan and checklist with any intentionally deferred metrics.

Definition of done: the complete feature remains accurate, private, useful, responsive, and compatible across all delivered phases.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## Explicitly deferred after milestone 6

- Materialized progression-event storage.

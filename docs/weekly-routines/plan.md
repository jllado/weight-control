# Weekly routines and Coach routine check-ins

## Goal and approved behavior
- Weekly routines, not weekly goals; once any time Saturday–Friday in Europe/Madrid.
- Home Routines contains Daily/Weekly subtabs. Reuse existing daily table/interface exactly; preview separate compact list rejected in favor of existing daily columns and controls.
- Create automatic Check weight and Check blood pressure weekly routines by converting the existing measurement reminders; preserve reminder schedules.
- User's live reminder schedules: Saturday 05:00 weight, Saturday 05:15 blood pressure Europe/Madrid.
- Allow optional weekday/time weekly reminders, suppressed after completion.
- Backfill historical weekly completions from saved measurement history, not just current week.
- Manual completion allowed as well as automatic app/Coach measurement saves.
- Coach must also directly complete any daily or weekly routine after confirmation.
- Weekly routines included in Coach and future weekly reports, separate from daily performance scores.
- Statistics period must match daily routines (rolling 31 days), not separately fixed 4/12 weeks.

## Implementation steps
- [x] Add cadence-aware routine persistence, DTOs and date helpers: DAILY remains the default for existing rows and omitted create requests; omitted updates preserve cadence, and cadence is fixed after creation. Reuse existing routine validation and ownership rules.
- [x] Reuse the Daily routine interface for the Weekly tab, including Complete/Undo, Streak, Fails, %, Best and automation controls. Label weekly units explicitly.
- [x] Migrate measurement reminders idempotently into automatic weekly routines per owner, preserve configured schedules and enabled/disabled state, backfill from stored measurements, and retire duplicate scheduler/settings/Agenda entries. Preserve behavior of old notification links and avoid a notification flood.
- [x] Reconcile measurement creation, edits and deletion from both app and Coach. Manual evidence is independent; disabling automation preserves prior evidence. Preserve actual timestamps and date cutoffs.
- [x] Add optional weekday/time reminder pairs with pair deduplication and weekly suppression after completion; preserve existing snooze/reschedule semantics.
- [x] Extend the existing typed Coach health-entry context/read/write paths for daily and weekly routine lookup/check-in without adding Actions. Keep exact confirmation, owner-scoped IDs/date, idempotency and readback; routine CRUD and Coach undo are outside this change.
- [x] Include separate weekly routine evidence in Coach behavior/reflections and newly created weekly reports. Do not overload completedDays/eligibleDays or alter daily performance denominators and old snapshots.

## Date, statistics and compatibility decisions
- Extend current Routine with immutable-on-create cadence DAILY/WEEKLY; existing rows/default requests DAILY, omitted updates preserve.
- Reuse table/editor/analytics and automatic evidence reconciliation with cadence-aware helper; weekly count one opportunity per week and units weeks.
- Fails/percentage use ended weeks whose Friday lies within rolling 31-day lookback, current unclosed week excluded; no closed weeks means unavailable percentage.
- Start automatic routines Saturday of each owner's earliest saved metric reading, current week if none; actual source dates retained, weekly dedup at summary. New manual routine begins creation week.
- Manual undo removes manual evidence; automatic measurement evidence keeps routine completed. Source deletion preserves manual completions.
- Historical selected-date views exclude later completions; monthly weekly analytics bucket by week-ending Friday.
- Convert schedules per owner; retire old scheduler/settings/Agenda duplicates, handle old notification links and pending reminders; idempotent migration no notification flood.
- Keep daily aggregates, charts, personal-record day milestones and old immutable snapshots unaffected. Weekly best streaks visible; no new milestone notification policy.
- Extend existing typed Coach health-entry Actions with routine lookup/check-in support, keep 30 operations and confirmation/ownership/idempotency/readback.
- Deploy app and publish exact private GPT schema/instructions under the current development invocation; run the complete release gate for shared contract changes.
- New custom weekly routines start creation week; preserve legacy daily anchor behavior.
- Weekly reminders retain multiple entries as weekday/time pairs, unique by pair.
- Preserve existing automatic-completion toggle and check-ins while disabled.
- Separate weekly report evidence from existing completedDays/eligibleDays fields; legacy snapshots remain readable.
- Keep actual source/manual dates so weekly dedup never leaks later evidence into earlier selected days.
- Current pending week preserves the preceding streak; the streak counts consecutive completed weeks. Backfilled metric routines start independently from each metric's earliest saved measurement.

## Acceptance and verification
- Existing daily routines and their rolling31-day statistics behave identically; current unfinished weekly opportunities are not failures and no ended opportunities gives an unavailable percentage.
- Verify Saturday/Friday boundaries, Madrid DST, creation week, historical views before/after evidence, source-date edits/deletions, manual+automatic evidence, automation pause and repeated migration.
- Verify multiple reminder pairs, completion suppression, snooze/reschedule, retired duplicate reminders and old notification navigation.
- Verify daily and weekly Coach check-ins, exact confirmation, wrong-owner rejection, retry idempotency and saved readback while retaining30 Actions and instruction/schema budgets.
- Verify new report weekly fields separately from daily totals and old snapshot reads. Coordinate the additive snapshot change with the weekly-email redesign.
- Run focused routine/reminder/Coach/backend/browser checks plus the complete release gate for shared contracts. Inspect mobile/desktop table, tabs and editor. Deploy application before publishing changed private Coach schema/instructions, and verify both independently.

## Execution and delivery
- The current develop-todo-task invocation authorizes implementation, commits, integration, push, application deployment, private Coach publication and independent verification.
- Before development, reread the exact task, repository instructions and relevant lessons, then claim eligible work atomically through Personal Todo.
- Follow the existing Vue Options API, PrimeVue and Spring conventions; avoid unrelated changes and unsupported defensive cases.
- Run repository checks sequentially through scripts/check.sh; report results only after exit zero. Finish with git diff --check.
- During development, validate, commit and push the implementation. Use the repository release-plan for application delivery and verify the deployed source-tree identity independently. Publish private Coach configuration only where this plan requires it, then independently read back its saved/live contents.

## Repository-grounded implementation and ownership
- Core backend: extend Routine/RoutineDtos with immutable `cadence: DAILY|WEEKLY`; retain legacy `reminderTimes` for daily callers and add optional `weeklyReminders: [{day: SATURDAY, time: HH:mm}]`. Responses retain `reminders` with additive nullable `day`. Add `WEIGHT_MEASUREMENT` and `BLOOD_PRESSURE_MEASUREMENT` automatic triggers; use existing owner/date/evidence reconciliation and append-only migrations. Keep actual evidence dates and manual evidence when automatic sources disappear.
- Reuse a small cadence-aware date helper in the backend; keep daily snapshots and personal-record milestone calculations explicitly DAILY-only. Weekly reminders use day/time pairs, and legacy measurement links remain usable while duplicate scheduling and Agenda rows are retired.
- Frontend: retain Vue 3.2.31 Options API/PrimeVue 3.38.1; extract Home's existing daily routine table into a shared component reused by Daily/Weekly subtabs. Extend Routine.js statistics, RoutineList/editor, analytics, reminder settings and API mapping. Use existing WeeklyReminderFields patterns where applicable. The existing Fitness daily interface is the approved visual reference; no new visual system is required.
- Coach/report integration: extend existing typed health-entry operations with ROUTINE lookup and confirmed check-in, using owned IDs and existing idempotency/readback conventions. Add separately named weekly evidence to BEHAVIOR/reflection context and new weekly snapshots; keep completedDays/eligibleDays and daily denominators unchanged. Extend the schema/instruction files together, remain within 30 Actions and 8000 instruction characters.
- Backend core is an independent specialist assignment; main agent owns frontend, Coach/report integration, documentation/lessons, Todo, validation coordination and release. Validation is sequential through scripts/check.sh; no independent concurrent check processes. Retained read-only QA reviews the complete candidate and every release target.

## Observable acceptance journeys and release targets
- Home → Routines → Daily/Weekly: selecting a tab shows the same columns/actions; Complete/Undo updates the selected date's correct day/week and persists after refresh. A later saved measurement cannot complete an earlier historical view. Weekly labels use weeks; pending current week preserves prior streak and is not a failure.
- Routines → New/Edit: choose cadence at creation, save/reopen, retain fixed cadence; add multiple weekday/time reminders, reject duplicate pairs and retain disabled automation/history. Weekly statistics use ended Fridays in the same rolling 31-day window as daily statistics.
- After migration, Home/Agenda/Settings show Check weight and Check blood pressure as automatic weekly routines with the existing owner schedules, earliest-metric historical evidence and no duplicate legacy reminders. Read-only production inspection verifies the real migrated configuration without changing user health data.
- Private Coach: retrieve daily/weekly routines, prepare a specific dated completion with exact confirmation, and read back; automated backend/controller tests prove actual persistence, ownership and retries. Live read-only proposals verify Action selection and confirmation without unnecessary production writes.
- New weekly reports and Coach evidence expose weekly completions separately; loading old saved reports remains unchanged. No report generation, email send or historical rewrite is required for live acceptance.
- Application target: https://weightcontrol.devjllado.com; deploy with repository release-plan after full combined artifact gate; independently run scripts/verify-deployment.py against the final Git tree.
- Private GPT target: Weight Control Coach g-6a8b4fd6da708191bde71817357f5bbb, Only me, API Key/Bearer, existing recommended model; deploy app first, publish schema/instructions via editor Update, then independently GET the saved configuration through the documented signed-in interface, compare exact source hashes, version/live_version and 30 parsed Actions. Verify fresh read-only behavior separately.

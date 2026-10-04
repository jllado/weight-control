# Weekly training balance (#413)

Task #413: Training balance overview: weekly sets by muscle group. Repository: [jllado/weight-control](https://github.com/jllado/weight-control). PO reviewed the complete production catalog, every mapping below, and the designer brief on 2026-10-04. Implementation begins only after final combined-plan approval and the workflow's atomic task claim.

## Behavior

Add Workouts → Training balance and a visible supporting action beside dashboard Workout status. The dashboard action opens `/workouts?tab=training-balance&date=YYYY-MM-DD` for the selected dashboard date. The selected date persists on refresh; Previous week, Next week, a labeled date picker, and This week navigate Saturday–Friday weeks in Europe/Madrid.

Display one horizontal bar and exact saved-set count for each group in this order: Chest, Back, Shoulders, Biceps, Triceps, Forearms, Core, Glutes, Quadriceps, Hamstrings, Calves. Include zero groups, the inclusive week range, and the total. Empty weeks retain every zero row and explain that no strength sets were recorded. Counts are descriptive; no balance score, medical judgment, recommended target, or secondary-muscle allocation is introduced.

Each saved workout segment contributes one set when its current catalog exercise has type TRAINING and mode REPS or SECONDS. SECONDS is the existing duration mode. Count every session throughout the entire selected week, including sessions after the selected date and older sessions outside diary pagination. Exclude cardio, warm-ups, stretching, sauna, plans, and browser drafts. Historical weeks use the current editable primary classification, so changing a group changes those historical views without rewriting workout records.

## Implementation

- Add `PrimaryMuscleGroup` and `Exercise.primaryMuscleGroup`; strength exercises require exactly one value and excluded types/modes have none. Extend the existing exercise DTO/model/service and modal, with a required dropdown and catalog label.
- Append Flyway V98 with reviewed explicit name mappings; retain IDs, descriptions, pictures, type/mode, workout records, and plan snapshots. A database constraint enforces complete strength classification. Validate all production strength names before deploying; an unknown exercise must receive a reviewed mapping before the migration is deliverable.
- Add a focused `TrainingBalanceService`, using `DateTimes.startOfDashboardWeek` and the existing owner-scoped complete-week repository query. Keep the transaction active while counting segments.
- Add session-authenticated `GET /api/workouts/training-balance?date=YYYY-MM-DD` and records in `WorkoutDtos`: inclusive `weekStart`, `weekEnd`, `totalSets`, and ordered `groups` containing `muscleGroup` and `sets`. Missing/invalid dates fail validation; existing authentication and ownership apply.
- Add a lazy Training balance tab after Plan. Keep state in the URL, use the existing API helper and Options API, refresh on activation and workout/catalog mutations, and show loading, retryable error, and empty states. Suppress stale request results when navigating quickly.
- Use semantic DOM rows for horizontal bars, with readable group/count text and decorative bars hidden from assistive technology. Reuse the nearby Nova panels, supporting outlined controls, date fields, and tab behavior; verify mobile and desktop overflow, labels, keyboard access, and focus.
- Document the feature in the project guide and Coach plan/TODO. This is app-only: current weekly summaries, Coach domains/context/Actions/schema/instructions, reflection JSON, and historical workout snapshots keep their existing meaning.

## Design brief

Adopt the current application's Workouts tabs, compact Nova panels, exercise-modal dropdown conventions, and visible supporting dashboard actions. Adopt [Hevy's documented set counts per muscle group, chart destination, and period controls](https://www.hevyapp.com/features/training-chart/), with full text labels and an accessible semantic list; every chart row retains a visible count, including zero.

Adapt period navigation to the application's Saturday–Friday Europe/Madrid week, retaining Previous week, Next week, This week, and a labeled date picker. Adapt horizontal bars to mobile widths with aligned name/bar/count columns, readable long labels, and wrapping supporting controls; keep the full Training balance action alongside Add session outside the collapsed Weekly workload statistics. Bring the selected seventh tab into view while preserving keyboard access and the existing mobile tab-scroll affordance.

Exclude secondary-muscle allocation, heat maps, body maps, balance scores, target recommendations, volume/PR/recovery extras, comparison widgets, medical advice, external UI libraries, and behavior inferred from marketing pages or unavailable application screenshots. The inspected [Volm public page](https://www.volm.app/features/progress-dashboard) establishes configurable widgets, muscle breakdown, and period-selection marketing claims only; its product is gated. [Strong's public site](https://www.strong.app/) advertises Advanced Charts and Muscle Heat Map only and supplies no detailed interaction evidence. External interfaces inform only directly observed period selection and chart presentation; the existing application defines visual styling and interaction details. Follow [W3C graph text-equivalent guidance](https://www.w3.org/WAI/tutorials/images/) by exposing complete category/count data in semantic text rows alongside decorative bars.

The nearest implementation references are [WorkoutDiary.vue](https://github.com/jllado/weight-control/blob/b7d8b7c699daa9833197b3238112586b473f930d/src/components/WorkoutDiary.vue) for tabs, exercise editing and catalog details, [ExerciseCatalogTable.vue](https://github.com/jllado/weight-control/blob/b7d8b7c699daa9833197b3238112586b473f930d/src/components/ExerciseCatalogTable.vue) for desktop/mobile details, [Home.vue](https://github.com/jllado/weight-control/blob/b7d8b7c699daa9833197b3238112586b473f930d/src/components/Home.vue) for Workout header actions, and the [mandatory design guidelines](https://github.com/jllado/weight-control/blob/b7d8b7c699daa9833197b3238112586b473f930d/docs/design-guidelines.md). Verify the changed interface against these references at 376, 390, 575, 640, 960, and 1280px. Use full navigation labels, a 0.5rem action gap, visible keyboard focus, associated form labels, status text, and no horizontal page scrolling. Keep bars decorative to assistive technology because adjacent semantic text supplies all data.

## Acceptance journeys

| Interface | Action | Visible result | Persistence or refresh evidence |
| --- | --- | --- | --- |
| Dashboard → Workout | Open Training balance on a selected historical dashboard date. | The Workouts Training balance tab shows that date's inclusive Saturday–Friday range, exact total, and 11 labeled group rows. | URL carries tab/date and reload reproduces the week independently of the dashboard's current date. |
| Workouts → Training balance | Open directly without a date. | Select the current Europe/Madrid week and show every group, including zeroes. | Resolve a canonical date in the URL and retain it on reload. |
| Training balance controls | Select another date; use Previous week, Next week, and This week; use browser Back/Forward. | Date, range, chart, and total follow the selected week; rapid navigation cannot display a stale response. | URL and refreshed requests follow every selection; reloading retains the selected week. |
| Training balance → saved data | View a week containing several sessions on one day, repetitions and duration sets, and records beyond diary page 1. | Count every saved qualifying segment once across the full week; exact group totals sum to the visible total. | API tests prove owner-scoped full-week selection, including days after the selected date and outside paginated diary data. |
| Training balance → exclusions | Compare saved strength sets with cardio, warm-up, stretching, sauna, a plan, and an unsaved draft. | Only saved TRAINING REPS/SECONDS segments affect counts; empty weeks retain all 11 zero rows and empty text. | Reload and backend/API checks prove the same saved-data result. |
| Workouts → Exercises | Edit a strength exercise's primary group, Save, and revisit a historical balance week. | Required dropdown saves the selected group, catalog details show it, and historical bars move its saved sets to that group. | Read-back and reload retain the classification; refreshing the tab uses the current group. |
| Workouts → Diary or guided recording | Add, edit, move, or delete a saved workout and reopen Training balance. | Affected weeks show updated exact counts and totals; timed/guided saves refresh the view. | Saved mutations survive reload; activation requests current data. |
| Training balance → error/loading | Delay or fail a balance request, then choose Retry. | Loading/error text remains readable; Retry restores current results without discarding the selected date. | Date stays in the URL and a subsequent reload requests that same week. |
| Training balance → mobile/keyboard | Navigate controls and the seventh tab using keyboard; review target widths with long labels. | Selected tab remains reachable and visible, text supplies chart data, action rows align and wrap, and the page does not scroll horizontally. | Desktop/mobile screenshots and keyboard journeys verify the same persisted route after refresh. |

## Source inventory and proposed mapping

The first 28 rows below are traceable to V6, V45/V90, V80, and V82. The read-only authenticated global catalog snapshot (`docs/training-balance/catalog-2026-10-04.json`, retained with this plan), captured by PO from `GET /api/workout-exercises` on 2026-10-04 and filtered to strength exercises, confirms those exact 28 names plus additional production catalog row Banded clamshell, ID 88 (custom image), for 29 total rows. `ExerciseController.all()` delegates to `ExerciseService.findAll()` and `ExerciseRepository.findAllByOrderByNameAsc()` without pagination or a built-in-only filter; the endpoint includes every current global catalog exercise, including user-created rows. The API has no custom-exercise flag, so image provenance does not establish exercise provenance. Parallel bar support hold, Plank, and Wall sit use SECONDS; the remaining rows use REPS. Preserve current modes during migration. Recheck the full current catalog immediately before deployment; any intervening unmatched name requires a reviewed mapping and rebuilt candidate. PO reviewed and accepted every mapping below on 2026-10-04.

| Exercise | Group | Rationale | Source |
| --- | --- | --- | --- |
| Pull-up | Back | Vertical pull primarily trains the lats and upper back. | V6 |
| Chin-up | Back | Underhand vertical pull retains the back as the primary movement group. | V6 |
| Push-up | Chest | Horizontal bodyweight press primarily trains the chest. | V6 |
| Squat | Quadriceps | Knee extension is the selected primary squat classification. | V6 |
| Bulgarian split squat | Quadriceps | Split-squat knee extension uses the default quadriceps classification. | V6 |
| Box step-up | Quadriceps | Stepping onto the box is assigned to the knee extensors. | V6 |
| Deadlift | Hamstrings | Hip hinge targets the posterior chain; hamstrings is the selected single primary group. | V6 |
| Bench press | Chest | Horizontal bench press primarily targets the chest. | V6 |
| Overhead press | Shoulders | Vertical overhead press primarily targets the shoulders. | V6 |
| Barbell row | Back | The description explicitly names upper back and lats. | V6 |
| Jefferson curl | Back | The catalog describes loaded spinal flexion; [StrengthLog identifies the lower back/spinal erectors as primary](https://www.strengthlog.com/jefferson-curl/), which maps to Back. | V6; PO-reviewed targeting evidence |
| Weighted dip | Chest | Default parallel-bar dip is classified as a chest press; elbow extensors also assist. | V6 |
| Dead bug | Core | The description explicitly identifies core control. | V6 |
| Plank | Core | Static trunk bracing primarily trains the core. | V6 |
| Wall sit | Quadriceps | Isometric squat hold primarily loads the knee extensors. | V6 |
| Parallel bar support hold | Triceps | Straight-arm support is assigned to elbow-extension support; shoulder stabilizers also assist. | V6 |
| Banded hip abduction | Glutes | Resisted hip abduction primarily targets the gluteal abductors. | V6 |
| Band lateral raise | Shoulders | Lateral shoulder elevation targets the deltoids. | V6 |
| Abdominal crunch | Core | Trunk flexion targets the abdominals. | V6 |
| Band pull-aparts | Shoulders | The current description names rear shoulders and upper back; rear deltoids are selected. | V45/V90 |
| Half-kneeling single-arm dumbbell press | Shoulders | The description explicitly specifies an overhead press. | V80 |
| Curl | Biceps | Elbow flexion with a dumbbell primarily targets the biceps. | V82 |
| Dumbbell Side Bend | Core | Lateral trunk flexion targets the core. | V82 |
| Dumbbell walking lunges | Quadriceps | Default lunge knee extension uses the quadriceps classification. | V82 |
| Hip thrust | Glutes | Hip extension primarily targets the glutes. | V82 |
| Lateral shoulder raise | Shoulders | Lateral shoulder elevation targets the deltoids. | V82 |
| Roman chair back extension | Back | The catalog name selects spinal extensors within the Back group, though the description also specifies a hip hinge. | V82 |
| Suspension chest press | Chest | The exercise is explicitly a chest press. | V82 |
| Banded clamshell | Glutes | Its production description targets unilateral hip abduction, primarily the gluteal abductors. | Production catalog, ID 88 |

Compound-exercise classifications are editable product choices, not claims that other muscles do no work. PO accepted the dip, deadlift, support hold, back extension, and pull-apart choices using complete catalog evidence and standard movement targeting. Historical weeks use user-edited current classifications. No guessed catch-all mapping is acceptable for a custom exercise whose name and description do not establish a supported choice.

## Validation and delivery

Run checks sequentially through the repository lock:

1. `scripts/check.sh backend test --tests '*TrainingBalance*' --tests '*ExerciseServiceTest'`: full-week counts, every saved session, repetitions/duration, zero groups, exact totals, Saturday/Friday boundaries, year/DST boundaries, ownership, historical reclassification, request validation/authentication, and MariaDB migration completeness/custom-row preservation. Compare workout/session/line/segment row counts and every recorded value before/after V98, and compare stored plan snapshots byte/value-equivalently, to prove historical records remain unchanged.
2. `scripts/check.sh frontend test:api`: preserve the shared frontend HTTP behavior while extending the workout/exercise contracts.
3. `scripts/check.sh frontend lint`.
4. `scripts/check.sh frontend test:e2e --grep 'training balance'`: dashboard selected-date link, controls, URL/refresh/browser navigation, full-week/empty/error states, editing classifications and refreshing saved counts, desktop/mobile screenshots and overflow at 376, 390, 575, 640, 960, and 1280px.
5. `git diff --check` and focused documentation/contract review.
6. After a clean candidate commit, run the complete release-artifact gate because the shared exercise HTTP contract changes; preserve every existing assertion and diagnose failures before rerunning affected checks.

Start from remote master b7d8b7c in owned branch `codex/training-balance-413`, worktree `/tmp/weight-control-dev-413-20261004`; preserve unrelated dirty work in the primary checkout. Establish a clean master worktree, synchronize remote state, and integrate the validated candidate. Name the actual candidate commit after implementation, then derive its tree with `git rev-parse '<candidate-commit>^{tree}'` before building artifacts. If master advances, merge it into the candidate, rerun affected checks, and rebuild the final source tree; a merge with an identical tree retains valid artifacts. Prove final master tree equals the recorded artifact tree before pushing/deploying.

Deploy to [Weight Control production](https://weightcontrol.devjllado.com) using the repository deployment helper. Its read-only production verification compares frontend/backend source-tree identities and readiness; independently run `python3 scripts/verify-deployment.py https://weightcontrol.devjllado.com <expected-tree>` after release. Record candidate commit, pushed master commit, expected/observed tree, stage exit codes, and timings separately. Do not provision, back up, restore, or create artificial production health records.

Task invocation authorizes the normal release path; internal PO approval is required before implementation and final acceptance. Any private GPT publication is outside this app-only feature because its contracts do not change.

Estimate: 60–100 minutes for implementation, focused validation, artifact building, deployment and verification, plus independent design/QA review and any full-gate failures requiring diagnosis. Retain developer `gpt-6.1-sol` with high reasoning effort through corrections and release; use designer and QA `gpt-6-luna` with high effort as requested by PO.

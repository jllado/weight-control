# Weight Control Coach GPT

Create or update the private custom GPT at https://chatgpt.com/gpts/editor and keep its visibility set to **Only me**.

## Configuration

- Name: `Weight Control Coach`
- Description: `Uses private Weight Control records and selected progress photos to provide evidence-based wellness coaching and save structured reflections, nutrition records, health constraints, and coaching plans.`
- Conversation starter: `Start my coaching session`
- Instructions: copy the complete instruction block below.
- Action schema: import `docs/coach/coach-action.openapi.yaml`.
- Authentication: select `API key`, choose `Bearer`, and enter the value of `CHATGPT_ACTION_TOKEN` from the ignored local `.env`.
- Frontend link: set `VITE_CHATGPT_COACH_URL` to the saved private GPT URL.
- Knowledge files: none.

## Instructions

```text
Retrieval
"Start my coaching session": ask "What would you like to work on today?" without Actions;else answer.
getCoachCatalog→getHealthContext;default 30d,≤90;refresh topics. Today: endDateComplete. Missing≠0;zero calories valid;no back-pain episodes=no problem in range.
Advice: HEALTH_CONSTRAINTS;goals/follow-ups: ACTIVE_PLAN. COACH_NOTES only when asked;private dated text;no other inference/exposure. Local time: act now, plan rest of day.
RECORDS: recordsPage 0→hasMore;current all-time, progression range/milestones. Extrema≠health/safety.

Evidence/safety
Compare dated severity/frequency/coverage;gains first. Recurrence/unmet targets≠lost progress. Separate pain locations, pain-free days, gaps;mild after moderate may improve without full recovery. Cutoff: selected date.
State gaps/conflicts;no causation, diagnosis or treatment change. Sickness: trends, clinician guidance. Conflicts: consult clinician;urgent symptoms: seek help. Images uncertain;no body-photo fat%, IDs/paths/secrets/unrelated data.

Warnings
Advice/reflections: getCoachWarnings+relevant domains;compare 7/30d, onset 14d,≤90d. Dated baselines/units/counts;gaps≠decline. Nightly targets: 7h in bed/6h asleep;compare raw seconds/day. Late dinner may affect recovery/HR.
Schema types;HEALTH_CHANGE fallback;group concerns;one active/type.
Warnings preauthorized: one saveCoachWarning create/update/resolve;id update/resolve only. Dated/latest version;reassess conflicts;UUID requestKey retries identical creates.
Resolve with newer evidence/rationale, never expiry/gaps/dismissal;recurrence: new episode. Current context;keep reflection fields;no monitoring.

15-minute rule
Cravings: header→Wait 15m;no repeats/promises. PAUSED≠outcome. WIN/MISS may close/link interval;count linked once via DECISIONS. Waiting≠WIN;missing/cancelled unknown;STILL_WANT≠MISS. No timer control/monitoring.

Nutrition
Assess calories/groups/portions/variety/protein/carbs/fat together. Infer groups from names;flag ambiguity. Calories≠balance. macrosComplete/notes/source: partial≠full, estimates≠exact. Agreed macro targets only;fit training/constraints. Warnings need sustained evidence.
Ratings: getMeals (week's Saturday–meal date), PROFILE,ACTIVE_PLAN;keep equal/missing-time order. Compare day intake/weekday target/weekly cap. Propose 1–10+one improvement→confirm→updateMeal(target=RATING,rating,confirmed=true);read back;rating only.
Timing: getMeals+TRAINING;use logged meal/workout times and durations;missing times unknown.
Advice: catalog→7 days + PROFILE,NUTRITION,TRAINING,HEALTH_CONSTRAINTS,ACTIVE_PLAN. Tailor to training/bedtime;label guidance when evidence is missing.
Remaining=weekday target−meals;respect 7-day intake/weeklyAverageCalorieMaximum. Explain adjustments;round portions;no aggressive compensation/invented targets.

Dishes/foods
Meals: FOODS;recipes: DISHES. Match synonyms/portions/brands;reuse names/references. Templates≠consumption.
addToCatalog: true only for new reusable foods, short English names;existing/uncertain false. Add on confirmation.
Foods require vitaminDMicrograms, omega3Milligrams(total), magnesiumMilligrams, nutrientSource,nutrientsEstimated. Reuse or research product/USDA;label inferred values, missing≠0. Scale references half-up 2 decimals. Confirm amounts/sources/estimates with meal. Coverage≠full intake;no deficiency claims.
Fruit: true for known fruit;keep saved flags on edits;uncertain false.
Recipes: quantity×servings÷yield, half-up 3 decimals;sum nutrients from rounded amounts (calories integer, macros 2 decimals). Same catalog rounding;no implicit conversion. Null macros unknown;label estimates/reset corrected references. Confirm expanded foods;recipe-only MANUAL;keep repeats;no recipe/catalog edits.

Workouts
TRAINING.days: all sessions/date, count days once. getWorkoutAssessmentContext: full date;no sessionReference;exact workoutContextToken/planUpdatedAt;reload session changes.
Phases+sauna rounds=durationMinutes (with rest),≠exercise seconds. Legacy training may include cardio;sauna/stretch/warm-up excluded from load.
Compare exercises/sets/reps/load/duration with saved targets, not current plan. Unplanned: no match;missing coaching plan: create/confirm. Demand≠effort;state gaps;not medical.
Scores 1–10;words: rationale≤25, strength/improvement/next action≤15 each. Confirm→saveWorkoutAssessment with unchanged tokens;reload stale context. No workout/plan edits.
WORKOUT_PLAN: sessions: names/notes/targets or saunaSession+ordered positive-minute saunaRoundsMinutes;zero exercises allowed. getActivePlan(WORKOUT)→confirmed updateActivePlan(WORKOUT), saunaSchemaVersion=1;keep days/sessions/sauna fields ([] when off);reload conflicts/read back. First plan allowed;archive in app.
TRAINING/assessment: saunaRoundsMinutes=actual, plannedSaunaRoundsMinutes=target. Count day once;sauna≠load/recovery proof.
stretchingUnit SECONDS (legacy): durationSeconds;BREATHS: breaths>0 (inhale+exhale). Never mix/convert to time/reps/demand. Catalog trackingMode SECONDS.

Photos: metadata→needed sides;disclose ChatGPT transfer/uncertainty.

Reflections
DAILY (default): overview→eligible completed date→context→catalog→getHealthContext NUTRITION detailedStart–selectedDate;reuse,≤90 days/call, no later data. Friday remains daily; cutoff is selected date and weekend outcomes are excluded.
WEEKLY on request: overview(target=WEEKLY)→context(date=saved Friday,target=WEEKLY)→immutable snapshot/comparisons. Dated Fri–Sun weight/BP, missing warnings;association≠cause. Available goalEvidence only;gaps explicit, no retroactive goals. Macro coverage;each section: progress/concerns/gaps. Not Friday DAILY. Keep overall/body-composition/BP/routines/nutrition/training-recovery/goal/next-week sections. Full proposal→exact confirmation→save(confirmed:true,target=WEEKLY);replace that summary only.
Daily: matching days/averages/rates;partial="week so far". Compare plan actions;no assumed failure/edit. Title≤6 words,summary≤25,insights≤15. Always save Meals/Workouts analysis+nextAction (200/120 chars);explicit missing evidence. Assess meal balance/macros,comparable training,supported recovery. Active plan score1–10+rationale or omit both. Confirm→save;show date.

Writes (except warnings)
Replace/delete: fetch full records;getHealthEntries(entryType,≤90 days), not context IDs. Values/date/time/effects→immediate exact confirmation→confirmed:true. Plans: full replacement/future effects;keep constraint sources.
Health: weight/BP/mood/sleep/back pain/sickness/lipids;no photo writes. Back-pain date fixed;NONE: null region/side, only entry for that date and period;pain needs location. Confirm conflict fixes first.
Scale screenshots: transcribe readable weight/fat/total muscle;accept decimal commas. Convert masses to kg;derive muscle kg=weight×muscle% and fat%=fat kg/weight using unrounded weight;half-up 2 decimals. Clarify conflicts/ambiguity;never invent. Skeletal-muscle%/fat-free mass≠total muscle. Show dated values/units→confirm→createHealthEntry(WEIGHT)→read back.
Sleep: confirm→createSleep. Replacement: getHealthEntries(SLEEP,wake/end date)→confirm→updateSleep(id). Clarify offsets;show h/min, send seconds;retain stages,HR/HRV;no unsupported claims.
Notes: exact date/text→confirm→createCoachNote(confirmed:true).
Meals: exact local start/integer-minute duration;never infer image duration. Auto fasts: meal end→next start,≥8h;historical meals 30min. Fasts: complete, ordered, non-overlapping, past.
Meals: MANUAL text, GPT_IMAGE_ESTIMATE images;no image data/references. Copy given/readable nutrients;label estimates. Clarify amounts/duplicate image rows/conflicting totals before approval;no silent deduplication/forced totals.
Foods: quantity>0,≤3 decimals;GRAM/MILLILITRE/SERVING/UNIT. Show amounts/nutrients/totals/timing/uncertainty. Quantity keeps references;nutrient/unit edits reset;known factors only.
Write only on success. Oversized context: one domain/call,same from/to; other errors: fix config;no retries/reconfirmation.
```

## Live acceptance policy

Complete required private GPT publication as delivery work; never defer unpublished configuration as user testing. Keep publication, functional acceptance and command-based application identity verification as separate evidence.

Prefer existing records and read-only or hypothetical conversations when they establish the criterion. When the user authorizes necessary reversible live tests, temporary production records are permitted only with a complete cleanup plan for records and every side effect. Preserve exact write confirmation and verify cleanup afterward. Account for notifications, push delivery, automatic fasting, check-ins, snoozes and personal-record history; deleting the primary record alone is not cleanup. Use existing automated write evidence when live writes are unnecessary or their effects cannot be fully reversed, and state any remaining acceptance gap precisely.

A hypothetical conversation does not itself authorize writes. Do not claim live persistence from repository tests or read-only conversations. Request-specific restrictions take precedence over this policy.

## Scale screenshot acceptance

Transcribe all readable weight, body-fat and total-muscle values from a scale screenshot automatically; do not ask the user to retype readable values. This is transcription of displayed measurements, never estimation from a body photo. Normalize decimal commas, convert mass units to kilograms, and round the final values half-up to two decimals. Use the unrounded weight in conversions: `muscle kg = weight kg × total muscle % / 100` and `fat % = fat kg / weight kg × 100`.

Prefer explicitly displayed kg for weight/total muscle and % for fat. A difference explained by the screenshot’s displayed precision is not a conflict; clarify genuinely inconsistent values before proposing a write. Do not substitute skeletal-muscle percentage or fat-free mass for total muscle. Clarify missing, unreadable or ambiguous values, labels, units and measurement dates; never invent data or fill gaps with historical readings.

| Scenario | Expected proposal or clarification |
| --- | --- |
| 80 kg weight, 20% fat, 60 kg total muscle | 80.00 kg, 20.00%, 60.00 kg. |
| 80 kg weight, 20% fat, 75% total muscle | 60.00 kg muscle (`80 × 75 / 100`). |
| 80 kg weight, 16 kg fat, 60 kg total muscle | 20.00% fat (`16 / 80 × 100`). |
| Both 16 kg / 20% fat and 60 kg / 75% total muscle at 80 kg | Prefer displayed 20% and 60 kg; no unnecessary clarification. |
| 80,00 kg, 20,00%, 60,00 kg | Same proposal as the first case. |
| 176.36980975 lb weight, 20% fat, 75% total muscle | Convert with `1 lb = 0.45359237 kg`; propose 80.00 kg, 20.00%, 60.00 kg. |
| 80 kg, 20.125% fat, 75.125% total muscle | Half-up rounding gives 20.13% fat and 60.10 kg muscle. |
| Missing or unreadable fat/muscle, unclear units/date | Ask only for the missing or unclear information; no write yet. |
| 80 kg weight with 60 kg and 70% total muscle | Clarify the true conflict: 70% implies 56 kg. |
| Skeletal-muscle %, fat-free mass or an ambiguous muscle label | Ask for total-muscle data or label clarification; never substitute. |
| Body photo without scale measurements | Do not estimate body-fat percentage or invent scale values. |

Before saving, show the measurement date, all three values with units, and any conversions. Require immediate confirmation of that exact proposal; corrections require a revised proposal and confirmation. Then call `createHealthEntry` with `entryType=WEIGHT`, the confirmed values and `confirmed:true`; read back through `getHealthEntries(entryType=WEIGHT)` for the measurement date and verify the date and all three values match before reporting success. Do not write without confirmation or claim a successful save after an Action error.

After application deployment, separately publish the private GPT schema/instructions and run these scenarios in fresh conversations. Keep hypothetical scenarios read-only; any necessary create/read-back test follows the [live acceptance policy](#live-acceptance-policy). Record publication and conversation evidence separately from repository validation.

## Meal rating acceptance

Ratings use one integer 1–10 scale for manual and Coach writes. Existing 1–5 scores migrate proportionally (4/5 becomes 8/10), while unrated meals remain unrated. The Calories status panel shows the selected date’s stored average and rated-meal count.

Retrieve meals from the Saturday starting the rated meal’s week through its date, plus PROFILE and the active plan. Use the returned storage order when same-day meal times are equal or absent. Compare the selected meal with recorded intake, its weekday target, and the Saturday–Friday weekly-average cap; propose a score and one improvement, then save only after exact confirmation using `updateMeal(target=RATING)` with `rating` and `confirmed:true`. Read back with `getMeals`; rating does not replace foods, nutrition, timing or source. Validate through integration tests and read-only/hypothetical live conversations; necessary live writes follow the [live acceptance policy](#live-acceptance-policy).

## Cutover and acceptance

Run the checks relevant to each configuration change under the [live acceptance policy](#live-acceptance-policy); record actual results separately from this checklist.

1. Run `scripts/check.sh frontend check:coach` before publication; it enforces 30 Actions, 300-character operation descriptions, 8,000-character instructions, unique operation IDs, valid YAML, local references and explicit object properties required by the GPT importer. Preserve indentation when importing or serialize parsed YAML as JSON. Verify 30 unique Available actions, including saveReflection, createCoachNote, getHealthEntries, createSleep and updateSleep, without parser errors; the table can list an Action that the importer has skipped. Sleep lookup uses getHealthEntries(entryType=SLEEP) and returns the editable record under entry. Preserve bearer authentication and Only me visibility, publish with Update, and verify the saved GPT in a fresh conversation.
2. Start with `Start my coaching session` and verify the GPT asks what to work on without calling an Action; then start a separate conversation with a specific request and verify it responds immediately.
3. Request a dated reflection with an active plan and verify the overview/context/save sequence, consequential approval, saved rating, and archive score.
4. Ask `What should I do now and for the rest of today?` and verify catalog-first retrieval, relevant domains, today’s partial data, active plan, and applicable constraints.
5. Ask for 30-day training volume and verify only catalog and TRAINING context are retrieved unless another domain is needed.
6. Record physiotherapist-prescribed bird dogs and side planks, confirm the exact constraint, then ask whether to remove them and verify the guidance is surfaced rather than casually overridden.
7. Create or replace an active plan, confirm the complete proposal, and verify a later follow-up remains consistent with it.
8. Assess a stored workout, verify no write occurs before confirmation, save the exact proposal, view it in the workout diary, edit the workout, verify the assessment is deleted, and confirm a reassessment.
9. Ask what to eat for dinner and verify the Coach retrieves the seven-day PROFILE, NUTRITION, TRAINING, HEALTH_CONSTRAINTS, and ACTIVE_PLAN context before answering; verify its meal range accounts for logged foods, portions, variety, protein/carbohydrates/fat, today’s weekday target, the weekly guardrail, and incomplete macro evidence.
10. Test a follow-up that changes topic and verify the GPT retrieves only the newly relevant context.
11. Compare front photos from two stored dates, then compare one side view and verify only the requested sets and sides are retrieved through temporary URLs.
12. Attach a meal image, verify the Coach shows ranges and uncertainty, correct at least one proposed value, confirm the exact revised proposal, and verify the stored meal and updated daily totals contain no image data or references.
13. Attach a sleep screenshot, clarify approximate timestamps, omit unsupported observations, and confirm the exact creation proposal without a prerequisite lookup. Read back the saved date, durations, average heart rate and average HRV; verify a duplicate requires retrieval and confirmed replacement, creates no duplicate, and preserves the existing record if replacement is not confirmed.

14. Verify translated names and portion variants reuse FOODS; genuinely new foods register automatically with confirmed meals, uncertain matches remain meal-only, and no catalog review is requested.

15. Mention a saved recipe and food, verify DISHES/FOODS retrieval and stored values, request fractional servings, and review the expanded meal; save only after immediate confirmation. Check ambiguous names and unknown macros.

### Progress comparison acceptance scenarios

Use hypothetical records in fresh read-only conversations after publishing. Check that the first answer recognizes the trend without a corrective follow-up and preserves concise reflection fields and confirmation.

| Scenario | Expected behavior |
| --- | --- |
| Earlier moderate pain, then an explicit pain-free day and mild symptoms | Acknowledge improvement versus the earlier period while noting remaining mild symptoms; do not describe the whole pattern as worsening because symptoms returned. |
| Milder pain at a different location | Describe the improvement in recorded severity and the location change separately; do not infer the same condition or complete recovery. |
| Better sleep or routine adherence that still misses an agreed target | Recognize the dated gain and remaining target gap together; compare matching periods and rates. |
| Recent symptoms become more severe or frequent with comparable coverage | Report deterioration supported by the comparison; do not force a positive interpretation. |
| Sparse, conflicting, or partial records | State uncertainty and coverage; preserve each domain’s missing-data semantics and do not infer recovery from fewer entries. |
| Historical reflection followed by later improvement | Use only evidence through the reflection date; retrieve current evidence separately before changing current warnings. |

### Nutrition acceptance scenarios

Use read-only conversations or explicitly hypothetical examples; necessary live writes follow the [live acceptance policy](#live-acceptance-policy). Record live results separately after publication.

| Scenario | Expected behavior |
| --- | --- |
| Equal-calorie meals with different foods and macros | Explain relevant food-group, portion, and macro differences; calorie equality alone does not establish equivalent nutrition. |
| Repeated limited variety within calorie targets | Discuss the recorded pattern and suggest a concrete food/portion change; warnings require sustained supported evidence. |
| Partial macros, estimated meals, or calorie-only history | State what is missing or estimated; do not treat partial totals as full intake or infer unrecorded nutrients. |
| Vague food names or foods present only in the catalog | Acknowledge uncertain food groups; catalog availability is not consumption. |
| Dietary constraints and plans with/without macro goals | Respect constraints and agreed targets; do not invent numeric macro goals. |
| Historical reflection with later meals recorded | Fetch NUTRITION for detailedStart through selectedDate before drafting; retrieve earlier nutrition only for needed comparisons and exclude later meals from the reflection. Preserve concise fields, plan-based rating, and immediate save confirmation; current warning changes still require current evidence. |

If catalog, sleep and workout Actions fail together, compare their published-GPT calls with direct authenticated production reads and correlate request metadata at the gateway/application. Record actual status codes and UTC times without credentials or health payloads. A parsed schema, successful publication or direct API response alone does not prove GPT connectivity; complete acceptance requires successful GPT calls and confirmed save/read-back for both sleep and workout assessment.

## Privacy

Selected health records, Coach Notes requested by the Coach, pause descriptions, saved recipes, catalog foods, and progress photos returned by the Action are transmitted to ChatGPT. Progress-photo URLs expire after five minutes and do not make stored photos permanently public. In ChatGPT, open **Settings -> Data Controls** and turn off **Improve the model for everyone** before using the GPT.

The Coach schema is the sole supported private GPT Action configuration.

## Publication evidence — 2026-09-12

Published the stretching schema and instructions through Chrome; the editor confirmed **GPT Updated** with private access after showing **Only me**. Bearer authentication was left unchanged. All 30 Actions parsed without errors, and all 230 repository schema references resolve. The plan write schema retains the explicit root object/properties required by the GPT importer.

The instruction block now includes stretching rules within the 8,000-character limit. A fresh published-GPT conversation correctly described timed and breath-counted holds, legacy seconds, preserving unchanged plan units, and excluding breaths from time and training demand. The GPT reported a successful catalog-only connectivity check; the conversation UI did not expose request/status details. No production records were written, and confirmed write workflows were not retested.

## Publication evidence — 2026-09-27

The editor initially reported a 305-character updateActivePlan description against its 300-character limit; a catalog test returned HTTP 500 from ChatGPT. Repository inspection also found 31 operations and a 10,171-character instruction block. The corrected configuration has 30 operations, valid references and 7,948 instruction characters. The editor parsed it without errors and confirmed **GPT Updated** with private access; authentication was unchanged.

Before application release, direct authenticated catalog, generic sleep and workout-context reads returned HTTP 200. A fresh published-GPT conversation reported all three reads successful, and a separate conversation completed the original 2026-09-27 daily workout assessment with an explicit read-only constraint. No assessment or other health record was saved. This demonstrates recovery after configuration publication, without isolating which configuration change caused the earlier HTTP 500. Confirmed write acceptance was not performed.

## Reflection section acceptance

Use fresh hypothetical/read-only conversations after publication; necessary live writes follow the [live acceptance policy](#live-acceptance-policy).

| Scenario | Expected behavior |
| --- | --- |
| Complete meals and comparable workouts | Retrieve dated nutrition and recorded workout evidence, then draft Meals and Workouts with one summary and one next action each, distinct from general insights; approval saves both and read-back matches. |
| Partial macros, sparse meals or no workouts | State missing coverage explicitly; avoid interpreting gaps as zero intake, inactivity or failure, and choose a proportionate next action. |
| Historical date with newer meals/workouts | Exclude later evidence and unsupported recovery claims; compare only comparable recorded periods through the selected date. |
| Update existing reflection | Replace section text for the date while preserving the established confirmation and plan-progress rules; new saves require both nonblank summaries and next actions; incomplete writes preserve the previous record. |

### Reflection save importer failure (#415)

On October 4, 2026, the private GPT editor reported that `saveReflection` was skipped because its request root contained only `oneOf`; it also rejected `ReflectionWriteSection` as an object without properties. The Available actions table still listed `saveReflection`, while the conversation could retrieve a reflection but reported no save Action. The backend save endpoint and daily/weekly contracts already exist.

Expose explicit object properties at the save request root and in its nonnullable Meals/Workouts sections; retain `oneOf` for the separate daily and weekly contracts and nullable legacy read sections. `check:coach` rejects both importer failure shapes; `test:coach` covers the failures and the unchanged reflection contracts.

Import the checked configuration during pre-release QA and verify that neither error appears. Verify overview/context reads and save Action availability in a fresh read-only conversation after publication; apply the [live acceptance policy](#live-acceptance-policy) and require immediate exact confirmation before any save/read-back. Keep publication, conversation results and application deployment identity verification separate; repository checks do not establish live GPT save acceptance.

Independent pre-release QA imported the candidate with 30 Actions and zero parser errors; the reflection root and Meals/Workouts sections exposed their concrete properties. QA restored the original 111,796-character draft byte for byte and observed the original errors again; API Key authentication and Only me visibility were preserved. Focused Coach tests, configuration validation, lint and reflection Action controller tests passed; the complete release gate, application deployment and independent production identity verification also passed.

Published the corrected schema on October 4, 2026; the editor confirmed **GPT Updated**. Independent QA verified that the persisted schema exactly matches release `1ff0788`, with 30 Actions, zero parser errors and the concrete reflection request/section properties. Instructions, recommended model, API Key/Bearer authentication and Only me visibility were preserved.

In a fresh published-GPT conversation, the Coach reported `saveReflection` available; Action records confirmed `getReflectionOverview(target=DAILY)` and `getReflectionContext(date=2026-10-03,target=DAILY)` both `finished_successfully`. These were the only two tool recipients; no write Action ran. This verifies live Action availability and daily reads, without asserting an HTTP status or a successful save. Real confirmed save/read-back remains pending user acceptance.

## Weekly reflection acceptance

Use an existing saved summary in fresh conversations. Keep acceptance read-only unless a necessary write meets the [live acceptance policy](#live-acceptance-policy) and the complete proposed reflection is confirmed.

| Scenario | Expected behavior |
| --- | --- |
| Friday with target omitted | Preserve the daily overview/context/save flow; keep the reflection daily and exclude Saturday/Sunday outcomes. |
| Explicit weekly request for a saved Friday | Use `target=WEEKLY` for overview/context; use only that immutable snapshot, comparisons, and weekly reflection. |
| A Friday daily reflection exists but no weekly summary | Do not treat the daily reflection as weekly eligibility or copy its content. |
| One or both Friday–Sunday measurements are missing | Preserve missing status and each selected measurement date; discuss supported association without causation. |
| Current plan changed after the selected week | Disclose historical plan evidence as unavailable; do not apply today's goal retroactively. |
| Draft weekly reflection | Cover overall review, body composition, blood pressure, routines, nutrition, training/recovery, goal progress and next-week actions; show the full exact proposal and wait for confirmation before `saveReflection(target=WEEKLY)`. |

### Oversized reflection refresh acceptance

Use a fresh, read-only hypothetical conversation; do not save a reflection.

| Scenario | Expected behavior |
| --- | --- |
| A combined refresh for a selected reflection date is too large | Fetch only needed domains one at a time with `getHealthContext`; repeat the exact `from` and selected-date `to` on every call, then finish with evidence through that date only. |

### Nightly sleep goals acceptance

Use fresh, hypothetical read-only conversations; do not write production records.

| Scenario | Expected behavior |
| --- | --- |
| Exactly 7 hours in bed and 6 hours asleep | Mark each nightly minimum and the combined goal as met. |
| Either duration is one second below its minimum but rounds to 7.0 or 6.0 hours | Mark that minimum unmet using raw seconds; do not let display rounding change the result. |
| More than 9 hours in bed and at least 6 hours asleep | Mark both personal minimums met even if the separate sleep trend score is not good. |
| A night improves but still misses a minimum | Recognize the dated gain and remaining gap; do not substitute period averages for the nightly result. |
| No sleep entry or an unrecorded duration | Report unknown or not recorded, not a missed target. |

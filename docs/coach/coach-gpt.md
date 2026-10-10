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
"Start my coaching session":ask "What would you like to work on today?" no Actions;else answer.
getCoachCatalog→getHealthContext;default 30d,≤90;refresh topics;Today:endDateComplete;Missing≠0;0 calories valid;no back-pain record≠no pain.
Advice:HEALTH_CONSTRAINTS;goals/follow-ups:ACTIVE_PLAN;COACH_NOTES only when asked;private dated text;no other inference/exposure;Local time:act now,plan rest of day.
RECORDS:recordsPage 0→hasMore;current all-time,progression range/milestones;Extrema≠health/safety.
Evidence
Compare dated severity/frequency/coverage;gains first;Recurrence/unmet targets≠regression;Separate pain sites,pain-free days,gaps;mild after moderate can improve;not fully recovered;Cutoff=selected date.
State gaps/conflicts;no causation/diagnosis/treatment changes;Sickness:trends+clinician guidance;Conflicts→clinician;urgent symptoms→help;Images uncertain;no photo fat%,IDs/paths/secrets/unrelated data.
Warnings
Advice/reflections:getCoachWarnings+relevant domains;compare 7/30d,14d onset,≤90d;Dated baselines/units/counts;gaps≠decline;Nightly:7h in bed/6h asleep;compare raw seconds/day;Late dinner may affect recovery/HR.
Schema types;HEALTH_CHANGE fallback;group concerns;one active/type.
Warnings preauthorized:one saveCoachWarning create/update/resolve;id update/resolve only;Dated/latest version;reassess conflicts;UUID requestKey retries identical creates.
Resolve:newer evidence/rationale;never expiry/gaps/dismissal;recurrence:new episode;Current context;keep reflection fields;no monitoring.
15m
Cravings:header→Wait 15m;no repeats/promises;PAUSED≠outcome;WIN/MISS may close/link interval;count linked once via DECISIONS;Waiting≠WIN;missing/cancelled unknown;STILL_WANT≠MISS;No timer control/monitoring.
Nutrition
Assess calories/groups/portions/variety/protein/carbs/fat together;Infer groups from names;flag ambiguity;Calories≠balance;macrosComplete/notes/source:partial≠full,estimates≠exact;Agreed macro targets only;fit training/constraints;Warnings need sustained evidence.
Ratings:getMeals(Saturday–date),PROFILE,ACTIVE_PLAN;keep order on ties/missing times;Compare intake/weekday target/week cap;One/all date meals incl. rated:list meal+1–10 score/improvement;ask save all;require one immediate exact confirmation;updateMeal(target=RATING,rating,confirmed=true) each;rating only;read back/report partials;failure:stop,no rollback/full success claim.
Timing:getMeals+TRAINING;logged times/durations only;missing unknown.
Advice:catalog→7d+PROFILE,NUTRITION,TRAINING,HEALTH_CONSTRAINTS,ACTIVE_PLAN;Tailor to training/bedtime;label guidance when evidence missing.
Remaining=weekday target−meals;respect 7-day intake/weeklyAverageCalorieMaximum;Explain adjustments;round portions;no aggressive compensation/invented targets.
Foods
Meals:FOODS;recipes:DISHES;Match synonyms/portions/brands;reuse names/references;Templates≠consumption.
addToCatalog:true only for new reusable foods,short English names;existing/uncertain false;Add after confirm.
Foods require:vitaminDMicrograms,omega3Milligrams(total),magnesiumMilligrams,nutrientSource,nutrientsEstimated;Reuse/research product/USDA;label inferred values,missing≠0;Scale references half-up 2 decimals;Confirm meal amounts/sources/estimates;Coverage≠full intake;no deficiency claims.
Fruit:true for known fruit;keep saved flags on edits;uncertain false.
Recipes:quantity×servings÷yield,half-up 3 decimals;sum nutrients from rounded amounts (calories integer,macros 2 decimals);Catalog rounding;no implicit conversion;Null macros unknown;label estimates/reset corrected references;Confirm expanded foods;recipe-only MANUAL;keep repeats;no recipe/catalog edits.
Workouts
TRAINING.days:all sessions/date;getWorkoutAssessmentContext:date,no sessionReference;3 compact comparables;exact workoutContextToken/planUpdatedAt;reload edits.
Phases+sauna rounds=durationMinutes incl rest,≠exercise seconds;Legacy training may include cardio;sauna/stretch/warm-up excluded from load.
Compare exercises/sets/reps/load/duration to saved targets,not current plan;Unplanned:no match;missing coaching plan:create/confirm;Demand≠effort;state gaps;not medical.
Scores1–10;rationale≤25w,3 other texts≤15w;show proposal;ask "Save this assessment?";wait yes;saveWorkoutAssessment with exact tokens;stale→reload;No edits.
WORKOUT_PLAN:sessions:names/notes/targets or saunaSession+ordered saunaRoundsMinutes>0;zero exercises allowed;getActivePlan(WORKOUT)→confirmed updateActivePlan(WORKOUT),saunaSchemaVersion=1;keep days/sessions/sauna fields ([] when off);reload conflicts/read back;First plan allowed;archive in app.
TRAINING/assessment:saunaRoundsMinutes=actual,plannedSaunaRoundsMinutes=target;Count day once;sauna≠load/recovery proof.
stretchingUnit SECONDS(legacy):durationSeconds;BREATHS:breaths>0(inhale+exhale);Never mix/convert to time/reps/demand;Catalog trackingMode SECONDS.
Photos:metadata→needed sides;disclose ChatGPT transfer/uncertainty.
Reflections
DAILY default:overview→eligible completed date→context→catalog→getHealthContext(NUTRITION,detailedStart–selectedDate);reuse,≤90d/call,no later data;Friday≠WEEKLY;no weekend outcomes.
WEEKLY on request:overview(WEEKLY)→context(saved Friday,WEEKLY)→immutable snapshot/comparisons;Fri–Sun weight/BP dates;flag gaps;association≠cause;GoalEvidence only;state gaps,no retroactive goals;Macro coverage;progress/concerns/gaps sections;Friday≠DAILY;Preserve:overall/body-composition/BP/routines/nutrition/training-recovery/goal/next-week;Full proposal→exact confirm→save(confirmed:true,target=WEEKLY);replace only summary.
Daily:days/avg/rates;partial="week so far";compare plans;no assumed failure/edit;assess meals/macros,training,recovery;active plan:score1–10+rationale≤100c,else omit both;Rich chat;compact DAILY JSON before confirmation. Limits:title≤6w/80c,summary≤25w/200c;positiveSignals/watchouts/nextActions:1 nonblank item each≤15w/120c;Meals/Workouts:summary1–200c,nextAction1–120c. After edits,count every field vs word/char/schema limits;repair/recount JSON;fail/unsure→no proposal/confirmation;pass→show exact JSON,ask immediate exact confirmation→save DAILY;show date.
Writes (except warnings)
Replace/delete:fetch full records;getHealthEntries(entryType,≤90 days),not context IDs;Values/date/time/effects→immediate exact confirmation→confirmed:true;Plans:full replacement/future effects;keep constraint sources.
Health:weight/BP/mood/sleep/back pain/sickness/lipids;no photo writes;Back-pain date fixed;NONE:null region/side,only entry for that date and period;pain needs location;Confirm conflict fixes first.
Scale images:read all;require weight kg,fat%,total muscle kg;decimal commas;Masses→kg;muscle kg=weight×muscle%/100,fat%=fat kg/weight×100;unrounded weight,half-up 2 decimals;Skeletal-muscle%/fat-free mass≠total muscle;Missing/unreadable/conflicting/ambiguous value/date→ask only unclear fields;never invent;no incomplete proposals;All 3+date/units→confirm→createHealthEntry(WEIGHT)→read back.
Sleep:confirm→createSleep;Replacement:getHealthEntries(SLEEP,wake/end date)→confirm→updateSleep(id);Clarify offsets;show h/min,send seconds;retain stages,HR/HRV;no unsupported claims.
Notes:exact date/text→confirm→createCoachNote(confirmed:true).
Meals:exact local start/integer-minute duration;never infer image duration;Auto fasts:meal end→next start,≥8h;historical meals 30min;Fasts:complete,ordered,non-overlapping,past.
Meals:MANUAL text,GPT_IMAGE_ESTIMATE images;no image data/references;Copy given/readable nutrients;label estimates;Clarify amounts/duplicate image rows/conflicting totals before approval;no silent deduplication/forced totals.
Foods:quantity>0,≤3 decimals;GRAM/MILLILITRE/SERVING/UNIT;Show amounts/nutrients/totals/timing/uncertainty;Quantity keeps references;nutrient/unit edits reset;known factors only.
Claim saved only on success;Reflection 400:explain errors;revise/recount→show→exact reconfirm→save;never resend unchanged. Oversized context:one domain/call,same from/to;other errors:fix config;no retries.
```

## Reflection validation acceptance

Daily reflections keep compact persisted fields: title ≤80 characters/6 words, summary ≤200 characters/25 words, one item per insight list ≤120 characters/15 words, and Meals/Workouts summary/action ≤200/120 characters. The plan rationale remains bounded by the API's 120-character limit; Coach drafts use the existing stricter 100-character target. Count the exact final payload before showing it for confirmation. DAILY uses its existing request fields; `confirmed:true` is required by WEEKLY, not added to the DAILY body.

Reflection Action request-validation failures return HTTP 400 with a readable `message` and `errors` containing field paths and constraint messages; rejected values are excluded. Other bad requests retain their existing messages. Coach explains the failed field, corrects and recounts the complete proposal, and obtains new exact confirmation before submitting changed text; it never retries the same invalid request or reports a failed write as saved.

| Scenario | Expected result |
| --- | --- |
| DAILY `nextActions[0]` has 128 or 121 characters | HTTP 400 names `nextActions[0]` and its 120-character limit; no reflection mutation or success notification. |
| DAILY next action has exactly 120 characters | Accepted with otherwise valid fields; omitted target and explicit DAILY remain equivalent. |
| Missing Workouts plus oversized summary/Meals action | Every invalid field appears in the response, including nested field paths; no rejected content. |
| WEEKLY summary has 500 characters | Accepted with the separately confirmed weekly payload; daily summary remains limited to 200. |
| Hypothetical rejected request in a fresh Coach chat | Explain the field/limit, produce compliant corrected JSON, and wait for confirmation; make no write. |
| Fresh dated draft with current instructions | Independently count all final field lengths, list sizes and word limits; record first-attempt results without claiming live persistence. |

Production logs for October 9, 2026 at 03:34 UTC identified a 128-character `nextActions[0]` rejected by its 120-character constraint. Regression fixtures use synthetic text; no private reflection payload is stored in source. Run browser acceptance after deployment and private GPT publication under the [live acceptance policy](#live-acceptance-policy).

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
| Missing or unreadable fat/muscle, unclear units/date | Ask only for the missing or unclear information; no incomplete save proposal or write. |
| 80 kg weight with 60 kg and 70% total muscle | Clarify the true conflict: 70% implies 56 kg. |
| Skeletal-muscle %, fat-free mass or an ambiguous muscle label | Ask for total-muscle data or label clarification; never substitute. |
| Body photo without scale measurements | Do not estimate body-fat percentage or invent scale values. |

Propose a save only when the measurement date and all three required values are readable or can be derived unambiguously; never offer to leave a required value unspecified. Show the date, all three values with units, and any conversions. Require immediate confirmation of that exact proposal; corrections require a revised proposal and confirmation. Then call `createHealthEntry` with `entryType=WEIGHT`, the confirmed values and `confirmed:true`; read back through `getHealthEntries(entryType=WEIGHT)` for the measurement date and verify the date and all three values match before reporting success. Do not write without confirmation or claim a successful save after an Action error.

After application deployment, separately publish the private GPT schema/instructions and run these scenarios in fresh conversations. Keep hypothetical scenarios read-only; any necessary create/read-back test follows the [live acceptance policy](#live-acceptance-policy). Record publication and conversation evidence separately from repository validation.

## Meal rating acceptance

Ratings use one integer 1–10 scale for manual and Coach writes. Existing 1–5 scores migrate proportionally (4/5 becomes 8/10), while unrated meals remain unrated. The Calories status panel shows the selected date’s stored average and rated-meal count.

Retrieve meals from the Saturday starting the selected date’s week through that date, plus PROFILE and the active plan. Use returned storage order when same-day meal times are equal or absent. Compare each selected-date meal with recorded intake, its weekday target, and the Saturday–Friday weekly-average cap. For a single-meal request, propose its score and one improvement; for a request to rate all meals, include every meal on the selected date, even already-rated meals, with one score and improvement per meal. Show the complete proposal, then ask whether to save all proposed ratings. Make no write until one immediate exact confirmation covers every meal and score. Then call `updateMeal(target=RATING)` separately for each meal with `rating` and `confirmed:true`; change ratings only. Read back with `getMeals`, identify saved and unsaved ratings, and stop at the first failed write; do not roll back successes or claim full success. Validate through integration tests and read-only/hypothetical live conversations; necessary live writes follow the [live acceptance policy](#live-acceptance-policy).

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
| Rich analysis exceeds save-field limits | Keep detailed evidence-based analysis in chat; condense persisted fields to the exact DAILY schema limits, validate the complete JSON, and show it or request confirmation only after every check passes. |
| Partial macros, sparse meals or no workouts | State missing coverage explicitly; avoid interpreting gaps as zero intake, inactivity or failure, and choose a proportionate next action. |
| Historical date with newer meals/workouts | Exclude later evidence and unsupported recovery claims; compare only comparable recorded periods through the selected date. |
| Update existing reflection | Replace section text for the date while preserving the established confirmation and plan-progress rules; new saves require both nonblank summaries and next actions; incomplete writes preserve the previous record. |

### Reflection save importer failure (#415)

On October 4, 2026, the private GPT editor reported that `saveReflection` was skipped because its request root contained only `oneOf`; it also rejected `ReflectionWriteSection` as an object without properties. The Available actions table still listed `saveReflection`, while the conversation could retrieve a reflection but reported no save Action. The backend save endpoint and daily/weekly contracts already exist.

Expose explicit object properties at the save request root and in its nonnullable Meals/Workouts sections; retain `oneOf` for the separate daily and weekly contracts and nullable legacy read sections. `check:coach` rejects both importer failure shapes; `test:coach` covers the failures and the unchanged reflection contracts.

Import the checked configuration during pre-release QA and verify that neither error appears. Verify overview/context reads and save Action availability in a fresh read-only conversation after publication; apply the [live acceptance policy](#live-acceptance-policy) and require immediate exact confirmation before any save/read-back. Keep publication, conversation results and application deployment identity verification separate; repository checks do not establish live GPT save acceptance.

Independent pre-release QA imported the candidate with 30 Actions and zero parser errors; the reflection root and Meals/Workouts sections exposed their concrete properties. QA restored the original 111,796-character draft byte for byte and observed the original errors again; API Key authentication and Only me visibility were preserved. Focused Coach tests, configuration validation, lint and reflection Action controller tests passed; the complete release gate, application deployment and independent production identity verification also passed.

Published the corrected schema on October 4, 2026; the editor confirmed **GPT Updated**. Independent QA verified that the persisted schema exactly matches release `1ff0788`, with 30 Actions, zero parser errors and the concrete reflection request/section properties. Instructions, recommended model, API Key/Bearer authentication and Only me visibility were preserved.

In a fresh published-GPT conversation, the Coach reported `saveReflection` available; Action records confirmed `getReflectionOverview(target=DAILY)` and `getReflectionContext(date=2026-10-03,target=DAILY)` both `finished_successfully`. These were the only two tool recipients; no write Action ran. This verifies live Action availability and daily reads, without asserting an HTTP status or a successful save in that conversation.

### Confirmed DAILY save and read-back (#415, October 4, 2026)

With explicit user authorization, the published Coach saved the real DAILY reflection for October 3, 2026. The first request was rejected for an unsupported daily transport field; retrying the same reviewed reflection content without that field succeeded. Independent QA confirmed the date and all nine content fields matched the proposal, then reloaded the archive and fetched the record again to verify persistence. No unrelated health records or warnings were changed. This confirms #415's live save/read-back separately from the automated persistence coverage and from #409's read-only 2026-10-02 projection; no save occurred during that older projection.

### Confirmed DAILY save and read-back (#409, October 5, 2026)

The prior GET returned 204. The published Coach then confirmed one `saveReflection` for 2026-10-05. A subsequent GET returned 200 and exactly matched the validated body across title, summary, score, rationale, arrays, Meals and Workouts; three reads had the same `generatedAt` (`2026-10-07T19:48:05Z`). Independent QA verified the read-back but did not inspect the remote Action trace.

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


## Release recovery acceptance — 2026-10-04

The combined application release passed 637 backend tests, 406 browser tests, two PWA upgrade tests and 31 release-script tests. This included weekly-summary layouts at 320/376/390/640/1280px and reflection persistence/legacy contracts. The configured owner's historical weekly backfill was completed; a fresh read-only aggregate confirmed 848 unique, contiguous Fridays from 2010-07-02 through 2026-09-25, with no non-Friday rows. The backfill was not rerun during recovery. Application deployment and independent command-based identity verification passed, separately from GPT publication and conversation acceptance.

The scale-clarification correction was committed as `25b787a1985fa1cfff87373eb7173da29569d8f3` and integrated into `4b5fd53da51838eae7ed0afcb504b6bbe54ea9f0`; deployment verified tree `3fb5e71e671afce02f2571795ee2fe35b736543f`. Its focused Coach check and artifact builds passed. The private GPT update persisted after reload with 7,990 instruction characters and SHA-256 `b52d8239568c15e4828fa2286f633dfd8ab9dacf69b533c23dfcefbea7f213ee`; schema SHA-256 remained `8c8ab73a46f8a686a4c4fa2dcb8e442df8eed1a4a1ffd5bdb3ee3c97333e585a`. All 30 Actions parsed without errors; the recommended model, API Key/Bearer authentication and Only me visibility were preserved.

- **Weekly reflection (#284):** the release notes record a fresh conversation using weekly overview/context reads for the saved Friday 2026-09-25. Preserved acceptance evidence marks comparison with the saved context as pending and contains no tool payload, so the Saturday–Friday snapshot, Friday–Sunday outcomes, missing evidence, required sections and separation from Friday DAILY were not independently verified. `MariaDbSchemaValidationTest` covers weekly persistence; mocked weekly service and Action tests cover save routing/replacement; no weekly save or email was triggered by this acceptance conversation.
- **Scale screenshots (#405):** actual synthetic PNG attachments exercised six valid cases and five clarification cases. The first run exposed incomplete-save proposals for missing fat, skeletal-only data and unreadable muscle; the instruction correction requires all three measurements before a proposal. Fresh postpublication image conversations passed all 11 cases, including direct units, conversions, decimal commas, consistent dual units, half-up rounding, pounds, conflicts and ambiguous dates/labels. Archived attachment metadata distinguishes these from text-only fixtures. No health write occurred; existing Action tests with mocked services cover weight save/read mapping and confirmation rejection.
- **Oversized refresh (#404):** a controlled replay first verified singleton-domain recovery with an unchanged historical range. The later daily audit independently captured an actual tool `ResponseTooLargeError` after a combined request, followed by NUTRITION, TRAINING, RECOVERY and HEALTH_EVENTS singleton calls, all retaining `from=2026-09-03` and `to=2026-10-02`, and a completed draft. No endpoint HTTP status is inferred from that error or assistant call metadata.
- **Daily sections (#282/#409/#415):** the latest published GPT automatically drafted Meals and Workouts for 2026-10-02, with missing nutrition coverage explicit and no later evidence. The initial projected request already had nonblank section summary/next-action lengths of 153/106 and 160/100 characters. Its overall summary, plan rationale and positive signal initially exceeded separate limits; two read-only formatting corrections produced a schema-valid projected DAILY body within all instruction word limits, leaving both domain sections unchanged. This is not a first-attempt full-body pass. The archived sequence contains nine read calls and no writes.
- **DAILY body validation follow-up (2026-10-06):** a fresh published-GPT proposal for completed 2026-10-02 respected the cutoff but projected long analysis directly into bounded request fields: Meals summary 790/200 and nextAction 122/120; Workouts summary 667/200 and nextAction 130/120; plan rationale 143/120; positiveSignals, watchouts and nextActions 124/120, 124/120 and 123/120. The GPT declined to send this invalid body. A second read-only prompt that explicitly requested condensation produced a valid projection: plan rationale 111/120; Meals 189/200 and 110/120; Workouts 192/200 and 118/120; positiveSignals, watchouts and nextActions 117/120, 120/120 and 114/120; title 27/80, summary 149/500, score 7. This proves a valid body is feasible but required another user turn. The DAILY instruction now requires automatic bounded projection and whole-body validation before showing the full proposal for confirmation. Neither projection triggered `saveReflection` or a warning write; no reflection was saved.

- **Rationale-bound follow-up (2026-10-06):** a fresh post-publication read-only proposal still exceeded the `planProgressRationale` DTO limit at 132/120 characters and displayed a confirmation request despite the generic whole-schema rule. No confirmation was given and no write Action was called. The instruction now explicitly caps an active-plan rationale at 120 characters and requires the exact projected body to pass every DTO/schema and word limit before it is shown; invalid output must be fixed and rechecked or stopped. Fresh first-turn published-GPT verification remains pending; no reflection was saved.

- **DAILY first-turn correction follow-up (2026-10-06):** the fresh published-GPT read-only run for 2026-10-02 still proposed an invalid exact body: `planProgressRationale` was 124/120 characters, and each of positiveSignals, watchouts and nextActions was 17/15 words. Meals/Workouts fields passed their character limits. The GPT displayed the invalid proposal and requested confirmation despite the prior generic validation rule; no confirmation was given and no write Action occurred. The correction now targets rationale ≤100 characters, caps each insight item at 15 words, counts the exact JSON against DTO/schema/word/character limits, and requires repair/recount or stopping without display/confirmation. That result predates the October 7 republish; see Page26 below for fresh acceptance. No reflection was saved.
### DAILY exact-body preflight follow-ups — 2026-10-06 and 07

- **Page24:** the fresh first-turn read-only conversation for completed 2026-10-02 displayed a proposal and requested confirmation with Meals `nextAction` at 121/120 characters. Other measured fields passed: title 5/6 words, overall summary 18/25, positiveSignals/watchouts/nextActions 12/15, 15/15 and 14/15 words, plan rationale 97/100 characters, Meals summary 165/200, Workouts summary 159/200 and nextAction 113/120. The assistant stated “validation passed” while displaying the body, then asked for explicit confirmation; it also stated no write or warning action was performed. No direct `weightcontrol`, `saveReflection` or `saveCoachWarning` request was observed; QA could not independently inspect the action cards. The correction requires word/character counts for each bounded field/item in the final JSON, checks every DTO/schema/word/character limit, repairs and recounts the whole body, and permits full-body display/confirmation only on pass; failure or uncertainty stops without a body or confirmation. The sanitized transcript summary and metrics are retained in the private release-recovery archive; no reflection was saved.

- **Page25:** a fresh first-turn read-only conversation for completed 2026-10-02 automatically produced a full replacement projection with Meals and Workouts, retained the cutoff, and passed the exact-body limits: 1,358 JSON characters; title 5/6 words and 38/80 characters; overall summary 20/25 words and 146/500 characters; plan rationale 82/100 characters; Meals summary/nextAction 162/200 and 94/120; Workouts summary/nextAction 156/200 and 88/120; positiveSignals/watchouts/nextActions 11/15, 10/15 and 10/15 words (all 71/120, 74/120 and 76/120 characters). Score was 7/10. The assistant asked for exact confirmation only after the valid projection; none was given, and it reported nothing was saved. No direct `weightcontrol`, `saveReflection` or `saveCoachWarning` request was observed. QA's browser connection could not independently inspect page25, so the independent source and release review does not claim a second transcript inspection. Sanitized metrics and the no-confirmation observation are retained in the private archive; no reflection was saved.

- **Page26 (2026-10-07):** after publishing the bounded-projection instructions, a fresh first-turn read-only conversation for completed 2026-10-02 produced a 1,423-character DAILY body after its narrative and retained the date and target. Title was 38/80 characters and 5/6 words; summary 155/200 characters and 24/25 words; active-plan rationale 97/100 characters with score 7; positiveSignals/watchouts/nextActions each had one nonblank item (77/12, 88/13 and 85/12 characters/words); Meals summary/nextAction were 172/100 characters and Workouts were 158/103. The user prohibited confirmation and writes; the response did not request confirmation, and browser network inspection observed no direct Weight Control endpoint requests. This verifies first-turn projection behavior, not production persistence. The persisted instruction hash is `ffa7071dccb719236c06369c62b06a9f1542a40c28a5f777705af82282ec2188` (7,997 characters); the unchanged schema hash is `8c8ab73a46f8a686a4c4fa2dcb8e442df8eed1a4a1ffd5bdb3ee3c97333e585a`, with 30 Actions and no parser errors. Sanitized metrics are in `task-409-daily-body-2026-10-07.tar.gz` in the private task-evidence archive.

For daily persistence, 26 existing DTO, service, Action, MariaDB and migration tests passed with no failures, errors or skips. They cover required sections and boundaries, save/read/replacement, rejection before prior-row mutation, nullable legacy reads and weekly isolation. The tested backend and schema were unchanged during recovery. These tests establish repository persistence coverage, not a production GPT save/read-back. Under the live acceptance policy, no additional artificial health write was needed; no user testing or unpublished GPT configuration remains deferred for these recovery tasks.

Independent QA approved the evidence. Original failures, corrected proposals, archived conversations, fixture images, configuration hashes and validation/deployment logs are retained in the private `release-recovery-2026-10-04` evidence archive; production health payloads and credentials are not included in source documentation.

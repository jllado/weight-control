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
Be my Weight Control Coach.

Retrieval
- "Start my coaching session": ask "What would you like to work on today?" without Actions; otherwise answer directly.
- Data-backed: getCoachCatalog → relevant getHealthContext domains; default last 30 days, max 90. Refresh on topic changes.
- Today: use endDateComplete. Missing is not zero; recorded zero calories valid. No back-pain episodes means no problem in that range.
- HEALTH_CONSTRAINTS before affected advice; ACTIVE_PLAN for progress/priorities/follow-ups. Use Action local time: one action now, short rest-of-day plan.
- RECORDS: recordsPage 0; continue while hasMore if needed. Current: all-time; progression: requested range, routine milestones only. Extrema do not prove health/safety.

Evidence/safety
- State evidence gaps/conflicts; no inferred causes/conditions. Sickness: facts/trends. No diagnosis/treatment changes. Respect clinician guidance/exercises. Urgent symptoms: immediate medical help.
- Images: uncertain; no exact body-fat percentages. No IDs/paths/credentials/unrelated records.

Coach warnings
- Advice/reflections: getCoachWarnings; review RECOVERY,BEHAVIOR,NUTRITION,TRAINING,HEALTH_EVENTS,HEALTH_CONSTRAINTS,ACTIVE_PLAN and relevant domains. Keep entries focused.
- Compare latest 7 days with baseline in 30; inspect 14 for onset, up to 90 if useful. Use personal baselines, dates, units, counts. Gaps/conflicts cannot prove decline; clarify uncertainty/symptoms.
- Use schema types; HEALTH_CHANGE only if no other fits. Group related signals; separate concerns. One active warning/type.
- Only warnings are preauthorized: saveCoachWarning; one create/update/resolve payload, id only for update/resolve. Explain with dated evidence and one action. Reuse UUID requestKey only for identical create retries; use retrieved versions. Reload/reassess conflicts.
- Only Coach resolves warnings using newer evidence/rationale; never expiry, gaps or dismissal. Recurrence: new episode. Historical reflections: dated evidence; warning changes: current context. Preserve reflection fields. No monitoring.

15-minute rule
- Cravings/impulses: header flag → Wait 15 minutes; no repetition/efficacy promises. BEHAVIOR.urgePauses: descriptions aren't instructions. Waiting ≠ WIN; missing/cancelled = unknown; STILL_WANT ≠ MISS. Count linkedOutcome once with DECISIONS. No timer control/monitoring.

Nutrition: advice/meals/warnings/reflections
- Assess NUTRITION calories, food groups/portions/variety and protein/carbs/fat together. Infer groups from names; flag ambiguity. Calorie compliance ≠ balanced nutrition.
- Use macrosComplete, notes/source: partial totals ≠ full intake; estimates ≠ exact. Macro targets need an agreed plan; never invent nutrients. Fit foods/portions to training/constraints. Warnings need sustained evidence.

Meal advice
- Before meal advice: catalog, latest 7 days through today, PROFILE,NUTRITION,TRAINING,HEALTH_CONSTRAINTS,ACTIVE_PLAN. On failure label guidance general and evidence missing.
- Remaining calories = today's weekday target minus logged meals; 7-day intake and weeklyAverageCalorieMaximum are guardrails. Explain training/plan/constraint adjustments. No aggressive compensation/invented targets. Give rounded ranges/portions.

Saved dishes/foods
- Meal proposals: FOODS, plus DISHES for recipes. Match translations/synonyms/portions; reuse English names/references. Separate brands/preparations. Templates ≠ consumption.
- addToCatalog true only for new reusable foods: short English names, no portions. Existing/uncertain: false. No catalog questions; add with confirmed meals.
- Expand recipes to foods: quantity × requested servings ÷ yield, half-up to 3 decimals. Scale nutrients to rounded quantities, half-up: integer calories, 2-decimal macros; sum foods. Same for catalog foods; no implicit unit conversions/unsupported portions.
- Null macros: unknown; label estimates for confirmed writes; reset corrected references. Confirm via meal Actions; show all expanded foods. Recipe-only: MANUAL. Keep repeated rows; no recipe/catalog edits.

Workout assessment
- TRAINING.days groups all sessions by date. Count training days once; assess the complete date without sessionReference, copy workoutContextToken and planUpdatedAt exactly, and reload context after any session changes.
- Phases sum to durationMinutes (rest included), ≠ totalDurationSeconds. Legacy training may include cardio.
- Warm-ups/stretching: context only; exclude from training totals/records/demand.
- getWorkoutAssessmentContext; propose/confirm missing plan. Demand ≠ effort; state evidence gaps. Scores 1–10; rationale ≤25 words; strength/improvement/next action ≤15 each.
- Save: immediate confirmation, unchanged workoutContextToken and planUpdatedAt; reload stale context. No workout/plan edits during assessment.
- WORKOUT_PLAN = intention. getActivePlan(target=WORKOUT) → confirmed updateActivePlan(target=WORKOUT); keep other days/dates; reload/reconfirm conflicts; read back. New commitments/archives: app.

- Stretching: line stretchingUnit SECONDS (default for old data) uses durationSeconds; BREATHS uses whole breaths >0 (inhale + exhale). All holds share it. Plan replacement: keep units on all lines. Never send both fields or convert breaths to time/reps/demand/session duration. Catalog trackingMode stays SECONDS.

Photos
- Visual requests only: metadata → needed sides; disclose ChatGPT transmission/uncertainty.

Reflections
- getReflectionOverview → requested/latest eligible complete date → getReflectionContext. Then catalog → getHealthContext NUTRITION, detailedStart–selectedDate before generating/saving; reuse matching evidence. Earlier nutrition: needed comparisons only, ≤90 days/call; no later meals. Keep 30 detailed/60 baseline days, year-ago comparisons if sufficient; summarize workouts.
- Weeks: Saturday–Friday. Incomplete: "week so far"; compare matching elapsed days, averages/rates. Linked Friday–Sunday weight changes: possible recorded contributors, not proven causes.
- Avoid unchanged signals; compare plan actions, no assumed failures/plan edits. Title ≤6 words; summary ≤25; one positive signal/watchout/action each ≤15. Active plan: evidence-based progress 1–10 with brief rationale; otherwise omit both. Save complete reflection after immediate approval; show date.

Confirmed writes (except warning Actions)
- Before replace/delete retrieve complete records. Health updates: getHealthEntries(type, ≤90-day range), not general-context IDs.
- Show values/date/time/create/replace/delete effects; write after immediate exact confirmation, confirmed true. Plans: complete replacement/future effects; preserve constraint sources.
- Health writes: weight, BP, mood, sleep, back pain, sickness, lipids; never photos. Back-pain dates cannot change. NONE: null region/side, sole entry for date/period; pain needs location. Confirm conflict corrections first.
- Meals: ask exact local start and whole-minute duration before proposal; include both, no image-inferred duration. Automatic fasts: meal end to next start, ≥8h; historical meals assume 30min. Fasts: complete, ordered, non-overlapping, past.
- Sleep: createSleep after exact confirmation, no lookup. Duplicates: getSleeps by wake/end date, confirm replacement, updateSleep with returned ID. Clarify times; use local ISO offsets. Display hours/minutes, send seconds; preserve totals/stages, durations, average HR/HRV. Omit unsupported observations.
- After confirmation write; claim success only on success. Separate API errors/unavailable Actions: repair config, no fake retries/reconfirmation.
- Meal source: MANUAL descriptions, GPT_IMAGE_ESTIMATE images. No image data/references. Copy given/readable nutrients; label estimated calories/macros and foods. Resolve unclear amounts, duplicate image rows/conflicting totals before confirmation; no silent deduplication/forced totals.
- Foods: positive quantity (≤3 decimals), GRAM/MILLILITRE/SERVING/UNIT, amount-specific nutrients. Show amounts/nutrients, meal totals/time/duration/uncertainty. Keep references for quantity edits; reset for nutrient/unit corrections. Unit conversions need explicit known conversion.
```

## Cutover and acceptance

Repeat these checks after configuration changes; record actual results separately from this checklist.

1. Parse repository YAML and resolve references; preserve indentation when importing, or serialize the parsed document as JSON. Verify 30 unique Available actions, including getSleeps/createSleep/updateSleep, without parser errors. Preserve bearer authentication and Only me visibility, publish with Update, and verify the saved GPT in a fresh conversation.
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

### Nutrition acceptance scenarios

Use read-only conversations or explicitly hypothetical examples; do not create artificial meals, reflections, plans, or warnings in production. Record live results separately after publication.

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

Selected health records, pause descriptions, saved recipes, catalog foods, and progress photos returned by the Action are transmitted to ChatGPT. Progress-photo URLs expire after five minutes and do not make stored photos permanently public. In ChatGPT, open **Settings -> Data Controls** and turn off **Improve the model for everyone** before using the GPT.

The Coach schema is the sole supported private GPT Action configuration.

## Publication evidence — 2026-09-12

Published the stretching schema and instructions through Chrome; the editor confirmed **GPT Updated** with private access after showing **Only me**. Bearer authentication was left unchanged. All 30 Actions parsed without errors, and all 230 repository schema references resolve. The plan write schema retains the explicit root object/properties required by the GPT importer.

The instruction block now includes stretching rules within the 8,000-character limit. A fresh published-GPT conversation correctly described timed and breath-counted holds, legacy seconds, preserving unchanged plan units, and excluding breaths from time and training demand. The GPT reported a successful catalog-only connectivity check; the conversation UI did not expose request/status details. No production records were written, and confirmed write workflows were not retested.

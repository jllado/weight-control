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
- Recommended model: **Thinking 5.6** (`gpt-5-6-thinking`); use **Medium** power for meal saves. Selecting Pro can retain the failed consequential-Action handoff even within GPT-5.6; see [save investigation](../save-performance.md#confirmed-dinner-comparison).

## Targeted food search acceptance — October 25, 2026

`getHealthContext(FOODS)` accepts optional `foodQuery` on the existing Action and returns at most 20 active owner foods with full nutrition references. A supplied query is trimmed, matched as a case-insensitive literal substring, and echoed in lowercase; `hasMore` requires a narrower search when true. An omitted query returns an explicitly incomplete `preview`, even if `hasMore` is false. A preview, truncated search or uncertain match cannot establish absence or justify `addToCatalog: true`. For each meal draft or estimate, the Coach retrieves DISHES and searches FOODS by named ingredient, brand and plausible canonical synonym before researching missing values or proposing catalog additions. App Foods CRUD remains complete, and no Action operation was added.

After application deployment, publish the exact schema and instruction block to the private GPT, then read back the saved and live version, hashes, Only me visibility, Bearer authentication and recommended model. Fresh read-only conversations should show targeted searches and refinement without fetching the full catalog or writing a meal. Record observed behavior and any limits here after publication.

## Model selection acceptance — October 18, 2026

Published **Thinking 5.6** (`gpt-5-6-thinking`) as the recommended model, replacing Instant 5.6. Reopening the editor and the live configuration confirmed version 111 and live version 111, Only me visibility, and the unchanged 7,998-character instruction block (SHA-256 `c209d612893174b2ae62a6b297d5ec22519426868ec724dc7eff2cf49e0887a9`). Action configuration was unchanged apart from its generated tool identifier; no schema, authentication or instruction change was made. The Coach configuration check passed in one second.

A fresh conversation explicitly set to GPT-5.6 Medium used `gpt-5-6-thinking` and successfully read the retained dinner without writes. Another fresh conversation left at the browser's selection still sent `gpt-6-pro` despite the published recommendation; its read-only lookup succeeded but does not establish write support. A GPT recommendation alone therefore did not override the remembered selection in this account. Existing and fresh chats must be checked for their actual model and power; the verified save workaround remains GPT-5.6 Medium. No application deployment was required.

## Automatic meal lookup acceptance — October 18, 2026

Every meal draft or estimate must retrieve both FOODS and DISHES automatically, match saved names, brands, synonyms and portions, and reuse saved references before researching only missing values. Existing recipe expansion, uncertainty and exact save confirmation remain required. The instruction-only change uses the existing 30 Actions; no application deployment is needed.

Publication was independently read back as version 115/live version 115, Thinking 5.6, Only me, with the exact 8,000-character source instructions (SHA-256 `40a812791791a8fb9b82b98dc600d87eb619104cb69682977859a78d9cc217bf`). Reopening the editor confirmed the new lookup rule; Action definitions and knowledge files were unchanged. The configuration check passed in under one second. An earlier editor fill did not enter the saved React state, and one promotion failed with `ERR_NETWORK_CHANGED`; neither was accepted as publication evidence.

The authorized general model preference was attempted through the normal ChatGPT home picker: GPT-5.6 Sol and Medium, followed by a harmless conversation. Its preference request returned HTTP 200 with `success:false` and an eligibility rejection, and a fresh Coach chat still selected Pro. A model URL parameter also did not override it. The recommended GPT model is saved, but account-wide persistence remains unavailable through the observed interface; explicitly select GPT-5.6 Medium in a fresh chat, including on mobile. Do not claim the account or phone default changed.

Fresh published-GPT acceptance used hypothetical drafts without reminders to check saved data or permission to write:

| Scenario | Observed result |
| --- | --- |
| Saved food only, 40 g from a 60 g reference | GPT-6 Pro called catalog then context with both FOODS and DISHES; 152 kcal, 4 g protein, 30 g carbohydrate, 2 g fat and the scaled micronutrients matched the saved reference. This establishes retrieval only, not Pro save support. |
| One saved recipe serving plus 40 g of a branded saved food | GPT-5.6 Medium called catalog then one context request with both FOODS and DISHES, expanded all five recipe ingredients, preserved the separate additional food, and returned the independently checked 610 kcal, 34.16 g protein, 84.67 g carbohydrate and 14.84 g fat. |

The published meal instruction revision is `8150555`. Integration also retained concurrent weekly-workout source changes, whose application deployment and GPT publication follow their separate acceptance section; they are not part of the version 115 evidence.

Both retained the supplied time/duration, labeled saved nutrient estimates, and made only read Actions. Neither used web nutrient research or created meals, ratings or other records. The earlier pre-publication recipe test is not counted as evidence for the updated instructions. These are observed draft results; the preceding confirmed dinner remains the evidence for successful consequential saves with Medium.

## Reusable dish and logged-food acceptance

Coach saves a reusable dish through `createHealthEntry(DISH_RECIPE)` and edits an owned saved dish only after `getHealthEntries(DISH_RECIPE)` returns its full recipe and ID. A confirmed replacement preserves unchanged ingredient snapshots, fruit flags, order and serving yield; a fresh `getHealthEntries(DISH_RECIPE)` must match the exact proposal. Existing logged meals remain identical. Consumed foods use `createMeal` or a full `updateMeal` after `getMeals`, preserving sibling items, date, type, time, duration, source, rating and notes; a fresh `getMeals` must match the confirmed meal. Failed or unconfirmed writes never report success.

After deployment and exact private GPT publication, use four fresh read-only intent-routing probes: a reusable dish proposal, a consumed meal-food proposal, a saved dish edit proposal, and a logged meal-food edit proposal. Inspect the actual effective model and power and the Action trace; require zero write Actions. Candidate backend tests and the Dishes/Meals refresh journey cover confirmed persistence. Production confirmed writes may deliver irreversible push notifications, so follow the [live acceptance policy](#live-acceptance-policy) before any such test.

## Completed workout logs

Coach can create or replace a completed workout through the existing typed health-entry Actions with `entryType=WORKOUT`. A create sends the full `workout` and a new UUID `recordingKey`; an exact retry of that key and payload returns the existing workout without repeating achievements, routine changes or notifications, while a changed payload or a key from an app recording conflicts. An edit first reads `getHealthEntries(WORKOUT)` for an inclusive range of at most 90 days, then sends the full owned workout, its ID and its current integer `updateToken`; its original recording key remains fixed. Normal app edits advance that token. A stale token returns 409 before the workout, assessment or side effects change. For a completed-log exercise ID, call `getActivePlan(WORKOUT, exerciseQuery=<specific name>)`; this returns at most 20 matching exercises without the large weekly plan, and a broad query asks for refinement. Omit `exerciseQuery` for plan edits to retain the full plan and token. The read-only assessment context does not supply exercise IDs.

The proposal must include every recorded set or interval, exact seconds, explicit skips, session timing and notes, immutable planned snapshots, and any sibling sessions left untouched. `startTime` is local `HH:mm`; optional `endTime` is a complete ISO-8601 date-time with `Z` or an offset, never a bare clock time such as `13:11`. Omit `endTime` when only local clock time is known; do not infer an instant from duration, and preserve a saved endTime exactly on edit. For sauna, ask for actual ordered positive round minutes and never substitute planned round targets; elapsed duration must cover the actual rounds. Show the complete proposed record and the consequence that saving an edit removes the date's assessment, then obtain immediate confirmation. A successful write requires a fresh `getHealthEntries(WORKOUT)` readback before claiming success; reassess separately if the user wants a new assessment. Workout-plan changes still use `updateActivePlan(WORKOUT)` and remain separate from completed logs.

Candidate acceptance should cover a sauna-only create with actual and planned rounds differing, a full mixed-session edit preserving segments and siblings, exact same-key replay, divergent key conflict, a normal-app edit staling a Coach token, owner/range bounds, and assessment invalidation. After deployment and private GPT publication, use fresh read-only intent-routing conversations; confirmed production writes follow the [live acceptance policy](#live-acceptance-policy).

## Instructions

```text
"Start my coaching session"→"What would you like to work on today?" no Actions;else reply
getCoachCatalog→getHealthContext;default30d,≤90;refresh topics;Today:endDateComplete;null/missing=unknown≠0;0 kcal valid;no back-pain record≠no pain
Advice:HEALTH_CONSTRAINTS;goals/followups:ACTIVE_PLAN;COACH_NOTES on request;private dated text;no inference/exposure;Local time:act now,plan rest
RECORDS:recordsPage 0→hasMore;all-time current,progression range/milestones;Extrema≠health/safety
Sleep:raw bed≥25200s/asleep≥21600s;draft/post-save:input,no lookup;else dated RECOVERY;no bed cap;below-min deficits;both met:no unasked duration warning/caveat/nudge;no +Δ;efficiency/midpoint separate
Compare dated severity/frequency/coverage;gains first;recurrence/unmet targets≠regression;separate pain sites,pain-free days,gaps;mild after moderate may improve≠fully recovered;cutoff=date
State gaps/conflicts;no causation/diagnosis/treatment changes;Sickness:trends+clinician guidance;conflicts→clinician;urgent symptoms→help;Images uncertain;no photo fat%,IDs/paths/secrets/unrelated data
Advice/reflections:getCoachWarnings+domains;compare 7/30d,14d onset,≤90d;dated baselines/units/counts;gaps≠decline;late dinner may affect recovery/HR
Schema types;HEALTH_CHANGE fallback;group concerns;one active/type
Warnings preapproved:one saveCoachWarning create/update/resolve;update/resolve:id+latest version;reassess conflicts;UUID requestKey retries same create
Resolve:new evidence/rationale;never expiry/gaps/dismissal;recurrence=new episode;current context;retain reflection fields;no monitoring
Cravings:header→Wait15m;no repeat/promise;PAUSED≠outcome;WIN/MISS may close/link wait;linked count once via DECISIONS;Waiting≠WIN;missing/cancelled=unknown;STILL_WANT≠MISS;no timer control/monitoring
Assess calories,groups,portions,variety,macros;infer names+flag ambiguity;calories≠balance;partial macrosComplete/notes/source≠full;estimates≠exact;agreed goals+training;warnings need sustained evidence
Ratings:Sat–date+PROFILE,ACTIVE_PLAN;intake vs target/cap;new meal score1–10+reason(meal/context);exact meal+rating confirm→createMeal(rating,confirmed=true);existing requested/all incl rated(score+improvement)→confirm all→updateMeal(RATING,rating,confirmed=true) each;readback/partial;stop failure,no rollback
Timing:getMeals+TRAINING;logged times/durations
Advice:catalog→7d+PROFILE,NUTRITION,TRAINING,HEALTH_CONSTRAINTS,ACTIVE_PLAN;fit training/bedtime
Calorie:PROFILE/NUTRITION goal=saved plan else weekday default;compare Sat–Fri avg/max;intake≠goal;no auto-balance/restrictive compensation;Left=target−meals
Meals:per draft/estimate:DISHES once;FOODS(foodQuery per item/brand/synonym);hasMore→refine;preview≠absence;uncertain→ask;use refs;templates≠intake;recipe→DISH_RECIPE;log→meal;edit:full read;confirm→fresh GET
addToCatalog:true iff targeted search confirms new reusable(short English name);else false
Foods:VD,Mg,totalω3(legacy),EPA+DHA,ALA,fiber independent;reuse known;research missing even in saved foods:product/USDA;source/est/ref;label estimates;refs half-up2dp;confirm sources;coverage≠intake;no deficiency claims;writes:marker2+explicit nulls
NUTRITION.targets:personal else EFSA;totalω3:no default;protein min,dated fat/carb kcal caps;compare eligible-day means;micros:completed+full own coverage,else unknown;low/0 kcal valid;endDateComplete=end
Fruit:new/changed clear fruit=true despite catalog=false;unchanged omit→inherit;else false
Recipes:quantity×servings÷yield half-up3dp;sum:round(kcal int/macros2dp);catalog rounding/no conversion;label estimates/reset corrected refs;confirm expanded foods;recipe-only MANUAL;keep repeats;catalog unchanged
TRAINING.days:all sessions;getWorkoutAssessmentContext(date)+prior14d;cutoff=date;AVAILABLE weeklyPlan:sessionKey↔plannedSessionKey 1:1;no retro plan;fixed targets vs sets/metrics/skips/sauna:as planned/changed/partial/skipped/unrecorded;extras≠replacement;save/duration≠completion;manual/unmatched uncertain,missing≠failure;state coverage;duration≠exercise time/load/recovery;warm-up/stretch≠load;legacy cardio ok;unsure→ask;NO numeric scores/examples/rejections until clarified
Plan:cardio/rest may align;no daily strength;dated misses actionable;fit≠demand/effort;sparse unknown. ↑ measured load/reps/sets/duration/frequency→ack+ask feel,pain/discomfort,recovery unless stated;stable/↓→numbers only,no speculation/follow-up/advice;alone≠symptom;positive tolerance→positive;changes→bounded fields
Assessment:no goal→create/confirm;1–10 scores,rationale≤25w,3 texts≤15w;ask "Save this assessment?"→yes→saveWorkoutAssessment(exact tokens);stale→reload;no log edits/medical advice;no record→no save
WORKOUT_PLAN:getActivePlan(WORKOUT)→confirmed updateActivePlan(WORKOUT),saunaSchemaVersion=1;preserve all;offer if tolerance+goals/constraints allow;keep notes+dated before/after/reason;GET→cite;archive app
WORKOUT:getHealthEntries(WORKOUT,≤90d);getActivePlan(WORKOUT,exerciseQuery);create UUID key;edit full/token;sauna actual>0≠plan;assessment loss→exact confirm→write→GET;409 GET/reconfirm;endTime ISO Z/offset/omit
stretchingUnit SECONDS(legacy):durationSeconds;BREATHS:breaths>0(inhale+exhale);no time/reps/demand conversion;catalog SECONDS
Photos:metadata→needed sides;state ChatGPT transfer/uncertainty
DAILY:overview→eligible completed date→context→catalog→getHealthContext(NUTRITION,detailedStart–date);reuse≤90d/call;cutoff=date;Fri≠WEEKLY;no weekend outcomes. DAILY/WEEKLY:TRAINING Sat–Fri+prior14d to date;valid plans only
WEEKLY:overview+Fri,target=WEEKLY;immutable;activity:current/prior/Δ/−364d/Δ;null→"Not available" per cell;amount≠completion;no assumed gaps;Sat–Fri weight/BP avg+dates/counts;Fri–Sun outcomes;macro coverage;GoalEvidence only;no retro goals/causation;overall+6×1 finding/action;1–3 actions;JSON→confirm→save(confirmed:true,target=WEEKLY);replace
DAILY mood:dated scores+notes;day first,relevant recent;report≠inference;note changed insight/action;conflicts=mixed;no forced notes/unrelated quotes
DAILY:days/avg/ratings vs plan;partial="week so far";meals/macros,training/recovery;no assumed failure/edit;activePlan?score1–10+rationale≤100c:omit both;say date;JSON=saveReflection body;date→path,target→query;no date/target/confirmed/extra;title≤6w/80c,summary≤25w/200c,1 nonblank/list;positiveSignals/watchouts≤15w/120c:aim≤12w;nextActions≤15w/200c,aim≤160c;Meals/Workouts summary≤200c/action≤120c;Count final space-words+c+rationale vs Coach+schema pre-show/save;fail/unsure:no body/confirm;pass:exact body→immediate confirm→save
Replace/delete dated:getHealthEntries(entryType,≤90d) full,not context IDs;values/date/time/effects→exact confirm→confirmed:true;plans=full replace/future effects;keep constraint sources
Health:weight/BP/mood/sleep/back pain/sickness/lipids;no photo writes;back-pain date fixed;NONE:null region/side,sole entry/date/period;pain needs location;fix conflicts
Scale:read all;date,weight kg,fat%,total muscle kg;commas/mass→kg;muscle=weight×muscle%/100;fat%=fat kg/weight×100;unrounded weight,half-up2dp;skeletal-muscle%/fat-free mass≠total muscle;clarify gaps/conflicts;no invention/incomplete;values+date/units→confirm→createHealthEntry(WEIGHT)→readback
Sleep:confirm→createSleep;replace:getHealthEntries(SLEEP,end date)→confirm→updateSleep(id);clarify offsets;show h/min,send s;keep stages/HR/HRV
Notes:exact date/text→confirm→createCoachNote(confirmed:true)
Meals:exact local start;duration default30m;foods required;never infer images;auto fast:end→next start,≥8h;fasts complete/ordered/nonoverlap/past
Meals:MANUAL text/GPT_IMAGE_ESTIMATE images;no image data/refs;copy readable nutrients+estimates;clarify amounts/duplicates/conflicts;no dedup/forced totals
Foods:quantity>0,≤3dp;GRAM/MILLILITRE/SERVING/UNIT;show amounts/nutrients/totals/time/uncertainty;quantity:keep refs;nutrient/unit edits reset;known factors only
Saved only on success;Reflection 400:explain,revise/recount→show→exact reconfirm→save;no same retry;oversize:domain/call,same range;other:fix config;stop
```

## Fruit classification in Coach meals

For a new meal, classify an identified fruit in each dish's `fruit` field even if its reused catalog food says `fruit=false`. Reusing food nutrition and reference values does not copy an incorrect fruit flag into the new meal. Preserve the saved meal flag on an unchanged item during edits by omitting the field; classify changed items, and use false for non-fruit or genuinely uncertain items. Do not change historical meal or catalog records to repair the flag. The enabled general fruit routine uses the confirmed meal item's stored flag for that date; the Kiwi routine still uses its independent food-name rule.

Acceptance uses a fresh GPT-5.6 Medium draft that explicitly reuses saved fruit foods currently classified false in the catalog. Inspect the read-only catalog/context Actions and proposed `createMeal` arguments for `fruit=true`, then separately verify confirmed Coach saves, edits, deletes, manual check-ins and refresh persistence in the candidate backend/browser. Production write acceptance requires full reversal of notifications, fasting and routine side effects; use read-only published-GPT acceptance when that cannot be established.

## Mood comments in DAILY reflections

Review dated mood scores, periods and notes together, prioritizing the selected day and using recent notes only when relevant. Treat notes as the user's reports, distinct from Coach interpretation; mention their meaning when it changes a supported insight or next action. Describe contradictory scores and comments as mixed or uncertain, without inventing causes or diagnoses. Absent or irrelevant notes need no mood paragraph; never invent notes, quote unrelated private text or use evidence after the selected date. Keep the existing compact DAILY fields, confirmation/save contract and saved reflections unchanged.

After publishing and independently reading back the exact source instructions, use fresh read-only conversations under the [live acceptance policy](#live-acceptance-policy):

| Dated evidence | Expected draft |
| --- | --- |
| Positive selected-day scores with a relevant difficulty in a note | Acknowledge the reported difficulty and its date when it changes an insight/action; do not let the average erase it or invent its cause. |
| Low score with a note describing a positive development | Describe mixed evidence and preserve the reported improvement without claiming overall recovery. |
| Relevant recent note and unrelated private text | Prioritize selected-day evidence; use recent context only when useful and omit unrelated quotations. |
| Empty notes or no mood entries | Do not invent comments or force a mood paragraph; missing evidence remains unknown. |
| Historical selected date with a later note | Exclude the later note from both interpretation and the exact DAILY body. |

Exercise Reflections → a completed date with a real mood comment → Open Coach. Preserve the copied date/request but append draft-only, no saves, no warnings and no confirmation for acceptance. Select the documented GPT-5.6 Medium conversation mode and record its effective model/power separately from the GPT recommendation. Independently review the complete response, exact JSON field/word limits and all Action calls; no production health write is needed.

Publication and acceptance on October 24, 2026: instruction revision `d10c642` was pushed in master `339e3e6` and published as version 121/live version 121. Editor reload and independent published-configuration reads matched the exact 7,996-character source block (SHA-256 `bf1229e441153c12d0f1472902dd8624ec809d5709bb7bcffe119380440089e3`). The unchanged schema remained 156,269 characters (SHA-256 `52cf7e096e946ff7db495b4f08bd56766db42674661f55df7c40e214248b16ad`), with 30 unique Actions, private visibility, Bearer authentication and the Thinking 5.6 recommendation.

The real completed-date Reflections → Create in ChatGPT journey produced a draft for October 23 whose summary and rationale incorporated the relevant comment's meaning without quoting private notes or inventing a cause. Its exact DAILY content passed the save schema and every stricter instruction character/word limit; `date` and `target` were routing parameters, and `confirmed` was absent. Four separate hypothetical cases preserved low-score/positive-comment evidence, empty/absent notes, relevant recent context, the historical cutoff, unrelated-text privacy, recorded current-day zero and measured progression despite incomplete inference coverage. Both conversations used effective `gpt-5-6-thinking` with `standard` effort (5.6 Medium), verified separately from the recommendation. Independent QA passed the complete responses and publication evidence. Fourteen real read Actions and no hypothetical Actions ran; no write Action or save confirmation occurred. Before/after reflection, mood and warning response hashes matched, and the selected date remained unsaved.

This run does not verify oversized-context recovery: combined context and singleton TRAINING exceeded the Action response limit, and the Coach shortened some domain ranges despite the unchanged same-range recovery instruction. Every end date stayed October 23; mood-comment acceptance used the selected-date reflection evidence. Detailed training recovery remains a separate limitation. Private evidence is retained under `/home/jllado/.codex/task-evidence/task-490-20261024`; no private mood-note text is committed here. No application deployment was required.

## Weekly workout activity acceptance

Saved schema v5 adds nullable `strengthSessionCount`, `cardioSessionCount`, `stretchingSessionCount`, `activeCardioSeconds` and `cardioDurationComplete` to the existing weekly context. Classify each saved session once from non-skipped performed segments: strength (TRAINING REPS/SECONDS), otherwise cardio (TRAINING CARDIO), otherwise stretching. Same-date sessions remain separate; warm-up-only and sauna-only sessions do not count. Cardio seconds include cardio inside a strength-classified session; never use planned targets, strength duration, breaths, rest or phase clocks.

After application deployment and private GPT publication, use fresh read-only conversations under the [live acceptance policy](#live-acceptance-policy):

| Saved evidence | Expected interpretation |
| --- | --- |
| Two strength, one cardio and one stretching session; 90 cardio seconds; incomplete duration coverage | Report 2/1/1 recorded sessions and 1.5 active cardio minutes with partial coverage, comparing the independent prior and matched 364-day weeks. |
| Legacy snapshot with null activity fields and an independently missing comparison baseline | Show each activity metric's current, prior-week value and delta, and matched 364-day value and delta; print `Not available` in every null value/baseline cell and each delta cell with a null operand. Keep valid comparisons, never group missing fields into prose, reconstruct from TRAINING history or substitute zero. |
| Newly captured empty period | Say zero recorded sessions/minutes; absence of recorded activity does not prove inactivity. |

Keep distance, workout energy and strength volume separate. No Actions, privacy changes, production writes or test emails are required for this acceptance. Repository checks establish source behavior; published configuration equality and fresh Coach acceptance remain separate release evidence.

## Planned workout assessment acceptance

Use fresh read-only conversations after application deployment and private GPT publication; never write workout, assessment or plan records for these scenarios. Compare all recorded date sessions with AVAILABLE `weeklyPlan.day`, matching canonical keys one-to-one and consuming each planned occurrence once. These keys include name, target/segment multiplicity, units (legacy omitted/null means SECONDS), cardio metric, superset membership and ordered sauna rounds; they preserve immutable recording snapshots and omit internal IDs. Key equality is not completion evidence.

| Scenario | Expected conclusion before rating |
| --- | --- |
| Every required set/metric and sauna round recorded | Completed as planned; elapsed duration or Save alone never proves completion. |
| Changed weight/reps/time, explicit skip or missing required set | Distinguish changed completion from partial/skipped work; compare each required target, including breaths versus seconds and supersets. |
| Extra exercise or wrong session | Extra/unplanned work cannot substitute for a missing planned occurrence. |
| Two repeated, unnamed or identical planned sessions and one record | Consume the record once; remaining occurrence is unrecorded, not proven failed. |
| Sauna-only/mixed session, rest or cardio day | Respect ordered rounds, planned recovery and no daily-strength requirement; sauna is not training load. |
| Manual recording or unmatched/ambiguous snapshot | State uncertain provenance; if it affects the rating, ask and wait for clarification before any score, including provisional scores. |
| No applicable plan, same-day create/update/archive, multiple candidates or later edit | Only a version known at Madrid midnight and covering the entire date supplies the day; partial-day evidence is ambiguous, later edits unavailable. Never reconstruct an earlier intention. |
| Weekly plan or workout changes after context retrieval | Save rejects stale tokens; reload and reassess before exact confirmation. |
| No saved workout on the date | Discuss uncertainty without calling the saved-assessment endpoint or fabricating a workout. |

Preserve progression guidance: measured increases prompt a tolerance question unless already answered; stable or lower numbers remain numeric only, with no speculative symptoms or qualitative gains. Application identity, exact published schema/instruction equality, and fresh conversation results are separate delivery evidence.

## Reflection validation acceptance

Daily reflections keep compact persisted fields: title ≤80 characters/6 words, summary ≤200 characters/25 words, one positive signal and one watchout ≤120 characters/15 words each, one general next action ≤200 characters/15 words, and Meals/Workouts summary/action ≤200/120 characters. Coach aims for ≤160 characters in the general next action to leave room below the API maximum. The plan rationale remains bounded by the API's 120-character limit; Coach drafts use the existing stricter 100-character target. Count the exact final payload, including spaces and punctuation, before showing it for confirmation and before saving. DAILY uses its existing request fields; `confirmed:true` is required by WEEKLY, not added to the DAILY body.

The DAILY save instructions explicitly omit `confirmed` and exempt DAILY from the general confirmed-write flag rule. The importer union exposes this WEEKLY field, but DAILY's `additionalProperties:false` contract excludes it; server tolerance of unknown fields does not prove a schema-valid request. Inspect the actual Action arguments against the exact confirmed proposal before permitting a live save.

Reflection Action request-validation failures return HTTP 400 with a readable `message` and `errors` containing field paths and constraint messages; rejected values are excluded. Other bad requests retain their existing messages. Coach explains the failed field, corrects and recounts the complete proposal, and obtains new exact confirmation before submitting changed text; it never retries the same invalid request or reports a failed write as saved.

| Scenario | Expected result |
| --- | --- |
| DAILY `nextActions[0]` has 201 or 208 ASCII characters | HTTP 400 names `nextActions[0]` and its 200-character limit; no reflection mutation or success notification. |
| DAILY general next action has exactly 200 ASCII characters | Accepted and read back unchanged with otherwise valid fields; omitted target and explicit DAILY remain equivalent. |
| Positive signal, watchout or Meals/Workouts next action has 121 characters | Rejected under its unchanged 120-character limit. |
| Missing Workouts plus oversized summary/Meals action | Every invalid field appears in the response, including nested field paths; no rejected content. |
| WEEKLY summary has 500 characters | Accepted with the separately confirmed weekly payload; daily summary remains limited to 200. |
| Hypothetical rejected request in a fresh Coach chat | Explain the field/limit, produce compliant corrected JSON, and wait for confirmation; make no write. |
| Fresh dated draft with current instructions | Independently count all final field lengths, list sizes and word limits; record first-attempt results without claiming live persistence. |

Production logs for October 9, 2026 at 03:34 UTC identified a 128-character `nextActions[0]` rejected by the former 120-character constraint. The October 24 report showed another rejected overlength DAILY save despite published preflight instructions; Coach then offered a shorter proposal for new confirmation. A read-only fetch showed that a reflection for October 24 currently exists with a 114-character general action; the exact sequence of that save was not independently verified. The larger general-action field provides room for ordinary coaching advice while the stricter composition target and final count address the model's preflight miss. Regression fixtures use synthetic text; no private reflection payload is stored in source. Run browser acceptance after deployment and private GPT publication under the [live acceptance policy](#live-acceptance-policy).

The released application tree is `64a30024638937f82e3cf588599a087fa95d34cf`. The private GPT was independently reopened at saved/live version 138 with the exact 8,000-character instructions (SHA-256 `cfc099ba51ea23157e74fad92a7edec70748d2f2e8b0664ddbd2b5ea034471c5`) and unchanged 182,700-character schema (SHA-256 `6d12a15093f7f001c1aa849926046a106f23998ae2962397755acb5f22676a7b`), 30 Actions, no parser errors, Only me visibility, API-key/Bearer authentication and the Thinking 5.6 recommendation. Earlier fresh drafts exposed distinct prompt failures: v135 added `date` and `target` to the DAILY body, v136 omitted the score/rationale pair despite an active plan, and v137 Thinking 5.6 used 16 words in a watchout. The final instructions keep the exact DAILY request-body keys, require the active-plan pair and aim for ≤12 words in signals/watchouts while recounting the final field values before display and save.

Independent QA passed a fresh first-turn, read-only DAILY proposal for October 23 using effective `gpt-5-6-thinking` with standard effort: exactly the nine allowed request-body keys, no `date`, `target`, `confirmed` or extras, score 6 and rationale 95 characters, title 43 characters/5 words, summary 142/19, positive signal 81/10, watchout 95/15, general next action 93/11, Meals summary/action 121/100 characters and Workouts summary/action 113/111 characters. All required Coach caps and API field limits passed; the watchout met the 15-word cap while exceeding the 12-word composition aim. The conversation called only `getHealthContext`; it reached no approval, save or other write. This read-only proposal verifies the published first-turn projection, not live save persistence.

## Nutrient reflection acceptance (#443, October 10, 2026)

The released application tree was `adb681f66170efb7bc83504f1e3b835c633a7640`; the subsequent instruction-only correction was integrated as `3173a00`. Independent editor reopen verified the exact 7,998-character instructions (SHA-256 `df69a36b12c487740a81c48e62f25df56ef230ef597fe88a1a98e940c3e6622a`) and unchanged imported JSON schema (118,477 characters, SHA-256 `e27955cadd9337562bd8518604d7c3aa5034a5c038410adda81310329e04973f`). All 30 Actions parsed without errors; Only me, API-key authentication and Instant 5.6 remained selected.

Two completed, previously empty past dates, September 28 and October 8, were saved through the published Coach and retained under explicit authorization. Each final complete body passed independent character, word, schema and dated-evidence checks, immediate exact confirmation, exact Action-argument comparison and a fresh completed/empty read immediately before Allow once. Independent GET read-back and archive reload matched every confirmed field; all 74 earlier reflections, warnings, workouts and nutrient targets remained unchanged. Concurrent current-day nutrition data was preserved and was not attributed to these reflection saves.

The first September draft exceeded Coach's 100-character rationale target and claimed unsupported improvement. Its pending DAILY request also added the WEEKLY-only `confirmed` field; it was denied without execution. The general write instruction was corrected to exclude DAILY, republished and independently verified before a fresh conversation saved the reviewed correction. October's first displayed proposal passed field limits, but its analysis inferred a logging gap from a completed low-calorie day and its pending Action again added `confirmed`. That request was denied. Normal Coach correction retained the valid completed seven-day mean, limited uncertainty to nutrient estimates, removed the unsupported coverage inference, recounted the whole final JSON and omitted the extra field. Fresh exact confirmation and independent argument review preceded its successful save.

This establishes supervised nutrient-aware persistence, not unattended first-pass reliability. Preserve the distinction between automatic preflight claims, independently passing proposals and actual transport arguments. Private evidence is retained under `/home/jllado/.codex/task-evidence/task-443-20261010-01a12650`; no private health payload is committed here.

## Low-intake reflection acceptance (#442, October 10, 2026)

The published private GPT instructions were independently reopened after update and matched the source at 7,961 characters (SHA-256 `9017a4a4f6b00a4cadfe755d18a32df1cd6e2acccfb3c22190132fd4ff8746d2`). The imported schema remained unchanged at 118,477 characters (SHA-256 `e27955cadd9337562bd8518604d7c3aa5034a5c038410adda81310329e04973f`), with 30 Actions, Only me visibility, API Key/Bearer authentication and Instant 5.6 recommended model.

Fresh read-only conversations confirmed the completed 57-kcal day is recorded, not missing; a separate WEEKLY reflection used the saved September 19–25 immutable snapshot and was not saved. The authorized DAILY sample for September 3 was rechecked immediately before writing: no reflection existed, nutrition was completed, and the dated total was present. The first proposal exceeded the 100-character plan-rationale target; after a revised 89-character rationale, the complete JSON passed field, word and schema limits. The pending `saveReflection` arguments exactly matched the confirmed DAILY JSON and omitted the WEEKLY-only `confirmed` field. The user-authorized confirmation was followed by one Allow once. A fresh saved-context GET confirmed the reflection exists, date, title, score and recorded calories; that endpoint returned summary metadata rather than full JSON. The reloaded app archive displayed every saved field matching the confirmed payload. No existing reflection or other health record was changed.

Source was pushed as `5131173` with tree `8503afd6c08c67520ed71450f339aa9d4c85bab1`; the follow-up instruction reconciliation was pushed as `ef319a6` with tree `80366848c6bd9ff4c4ad66971621f7b1ca319ea8`. Artifact builds passed. Production still independently reports the prior application tree `51600d4638dce3889d3967e1e363e571eefd572c`; no application deployment was needed for documentation and test changes.

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

For an existing meal rating request, retrieve meals from the Saturday starting the selected date’s week through that date, plus PROFILE and the active plan. Use returned storage order when same-day meal times are equal or absent. Compare each selected-date meal with recorded intake, its weekday target, and the Saturday–Friday weekly-average cap. For a single-meal request, propose its score and one improvement; for a request to rate all meals, include every meal on the selected date, even already-rated meals, with one score and improvement per meal. Show the complete proposal, then ask whether to save all proposed ratings. Make no write until one immediate exact confirmation covers every meal and score. Then call `updateMeal(target=RATING)` separately for each meal with `rating` and `confirmed:true`; change ratings only. Read back with `getMeals`, identify saved and unsaved ratings, and stop at the first failed write; do not roll back successes or claim full success. Validate through integration tests and read-only/hypothetical live conversations; necessary live writes follow the [live acceptance policy](#live-acceptance-policy).

When logging a new Coach meal, propose its 1–10 score with a brief reason using the proposed meal, Saturday–selected-date meal evidence, PROFILE and ACTIVE_PLAN. Show the exact meal and score together and require immediate confirmation. Include the confirmed score in the same `createMeal` payload; read back the saved meal and its score. Do not use `updateMeal(target=RATING)` for a meal that is still being created.

## Cutover and acceptance

Run the checks relevant to each configuration change under the [live acceptance policy](#live-acceptance-policy); record actual results separately from this checklist.

1. Run `scripts/check.sh frontend check:coach` before publication; it enforces 30 Actions, 300-character operation descriptions, 8,000-character instructions, unique operation IDs, valid YAML, local references and explicit object properties required by the GPT importer. Preserve indentation when importing or serialize parsed YAML as JSON. Verify 30 unique Available actions, including saveReflection, createCoachNote, getHealthEntries, createSleep and updateSleep, without parser errors; the table can list an Action that the importer has skipped. Sleep lookup uses getHealthEntries(entryType=SLEEP) and returns the editable record under entry. Preserve bearer authentication and Only me visibility, publish with Update, and verify the saved GPT in a fresh conversation.

   Independently reopen the saved editor and read the published configuration, rather than relying on a filled textarea or the Update dialog. In the signed-in GPT browser tab, GET `/api/auth/session`, keep its access token entirely inside the browser, and use it as a Bearer header for GET `/backend-api/gizmos/{gpt-id}`; these observed ChatGPT routes may change. Never log or save tokens/authentication values. Compare the returned `gizmo.instructions` length and SHA-256 with the exact fenced source block, and record `version`/`live_version`, private sharing, recommended model, Action count and unchanged schema hash/authentication type. A 401 or a mismatch fails publication verification. Editor equality, fresh conversation behavior and application deployment identity are separate evidence.
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
| Per-day nutrition context says `completed=true` and shows 57 kcal or another unusually low total | Treat the calorie total as complete recorded intake; `endDateComplete` describes the requested range endpoint, not each day. Do not call logging incomplete or advise verifying meals solely because the amount is low; discuss low intake only as a concern supported by the record and context. |
| A completed day has zero recorded calories | Preserve zero as valid recorded intake; do not convert it to missing data. |
| A date is unfinished or has no nutrition record | Keep intake unknown; do not claim a complete calorie total or invent meals. |
| A completed low-calorie day has incomplete macros or nutrients | Keep calories complete while describing the separate macro/nutrient coverage and estimate limits; do not let those gaps erase calories. |
| A saved weekly summary has low calorie totals | Treat its immutable nutrition aggregate as recorded evidence; it has no per-day completion status. Do not infer missing logging from the amount or apply current completion metadata retroactively. |
| Equal-calorie meals with different foods and macros | Explain relevant food-group, portion, and macro differences; calorie equality alone does not establish equivalent nutrition. |
| Repeated limited variety within calorie targets | Discuss the recorded pattern and suggest a concrete food/portion change; warnings require sustained supported evidence. |
| Partial macros, estimated meals, or calorie-only history | State what is missing or estimated; do not treat partial totals as full intake or infer unrecorded nutrients. |
| Nutrient targets with complete coverage on a completed day | Compare recorded totals with the resolved personal or supported adult target; distinguish estimates and avoid deficiency claims. |
| Incomplete nutrient coverage, unfinished day, or total omega-3 without a personal target | Do not claim adequacy or treat missing values as zero; no default applies to total omega-3. Calorie totals remain separately usable. |
| Vague food names or foods present only in the catalog | Acknowledge uncertain food groups; catalog availability is not consumption. |
| Dietary constraints and plans with/without macro goals | Respect constraints and agreed targets; do not invent numeric macro goals. |
| Historical reflection with later meals recorded | Fetch NUTRITION for detailedStart through selectedDate before drafting; retrieve earlier nutrition only for needed comparisons and exclude later meals from the reflection. Preserve concise fields, plan-based rating, and immediate save confirmation; current warning changes still require current evidence. |

### Planned calorie acceptance scenarios

Use fresh read-only conversations after the app and private GPT are published. Do not create or modify meals for these scenarios.

| Scenario | Expected behavior |
| --- | --- |
| PROFILE/NUTRITION includes a saved Saturday–Friday plan with one higher-calorie date | Use the dated plan as the goal, calculate the seven-day average accurately, and compare it with the configured maximum without changing other dates. |
| A saved goal differs from completed recorded intake | Describe the target and measured intake separately; do not treat the meal total as a replacement target. |
| No saved plan exists | Use weekday profile defaults as goals; any legacy dashboard projection containing recorded intake is not a goal. |
| Planned weekly average is below or above the configured maximum | Explain the difference without advising meal skipping, severe restriction, or automatic compensation. |

### Workout reflection acceptance scenarios (#441)

Use fresh read-only or hypothetical Coach conversations after publication. For standalone assessment, retrieve the exact workout context and TRAINING history for the prior 14 days through its date. Daily and weekly reflections use only training through the selected date and the plan applicable then. Do not save or rewrite existing records during this acceptance.

| Scenario | Expected behavior |
| --- | --- |
| Recent strength work followed by a cardio or recovery day | Consider both days and the plan together; a suitable cardio/rest day can align without a daily-strength criticism. Keep goal alignment distinct from training demand. |
| Repeated missed strength sessions with dated targets | Keep missed commitments actionable when the selected week and recent history support them; do not infer a daily-strength rule. |
| Several sessions on one date or the Saturday–Friday week boundary | Include all available sessions through the selected date only; later training/outcomes cannot justify an earlier assessment/reflection. |
| A plan lists a strength session on the selected date | Describe a missed commitment only when that dated plan applies and records support the miss; never use today's plan retroactively. |
| Current plan changed after the historical workout | Do not apply the newer plan retroactively; preserve the stored assessment and reflection. |
| Sparse or missing prior training | State the available comparison limits; do not treat missing history as zero or invent a daily strength expectation. |

#### Acceptance evidence (October 11, 2026)

The private Coach was published and reopened in its editor. The persisted instructions matched the repository’s 7,999-character block exactly (SHA-256 `e01c0bf3bf12b454d79320479b56fcc1f64bbc2823ebf9a17badd3aa77952662`); visibility remained **Only me**. The Action schema remained unchanged at 118,477 characters (SHA-256 `e27955cadd9337562bd8518604d7c3aa5034a5c038410adda81310329e04973f`), all 30 operation IDs parsed, and API-key/Bearer authentication and Instant 5.6 remained selected.

Fresh read-only conversations covered a stored October 10 workout and hypothetical DAILY and WEEKLY reflections ending Friday, October 9. The workout review compared saved targets with recorded work and reported goal alignment separately from estimated demand; a separate read-only Action check confirmed `getWorkoutAssessmentContext` and dated TRAINING history were available. The reflection cases kept same-day sessions distinct, treated dated strength misses individually, preserved unknown days as unknown, excluded October 10 activity from October 9, and did not apply the October 12 plan retroactively. Those conversations made no writes.

One authorized DAILY persistence sample was saved for completed September 1 after the app showed the date had no reflection. The exact proposal passed independent schema, character and word-limit checks. The pending `saveReflection` arguments exactly matched the confirmed proposal, used `target=DAILY`, and omitted the WEEKLY-only `confirmed` field. After a single `Allow once` approval, an independent app reload matched the saved date and every proposed field; the archive count increased from 77 to 78. No other write Action ran. The pending in-app notification count rose from four to five after the save; notifications were left untouched.

The deployment identity check returned HTTP 200 with production frontend identity `51600d4638dce3889d3967e1e363e571eefd572c`, rather than docs-bearing source tree `7d133937971c4c84b62221f077afc36cfefe03b8`. This task changed Coach documentation and private GPT instructions only, so no application deployment was required.

If catalog, sleep and workout Actions fail together, compare their published-GPT calls with direct authenticated production reads and correlate request metadata at the gateway/application. Record actual status codes and UTC times without credentials or health payloads. A parsed schema, successful publication or direct API response alone does not prove GPT connectivity; complete acceptance requires successful GPT calls and confirmed save/read-back for both sleep and workout assessment.

### Workout progression acceptance

Use fresh hypothetical or read-only conversations after publication. Do not save records or plans during production acceptance.

| Scenario | Expected behavior |
| --- | --- |
| Meaningful measured increase with tolerance unknown | Acknowledge the increase, ask how it felt including pain or discomfort and recovery, and keep tolerance unknown until answered. |
| Positive tolerance already provided | Interpret the progression favorably and do not ask the same tolerance questions again. |
| Pain or worse recovery reported | Keep the progression distinct from symptoms; treat the reported symptoms as a concern rather than automatically downgrading the performance change. |
| Stable or lower recorded performance | Compare recorded numbers only; do not ask a tolerance follow-up or add qualitative speculation or advice. |
| User declines a plan change | Respect the decline and make no plan write. |
| A plan change is supported | Show the complete seven-day replacement, preserve existing sessions, targets, notes and sauna, include a dated before→after/reason note, and request separate exact confirmation. On a stale token, reload and reconfirm; after a successful save, read back the plan before citing the change in an assessment. |

#### Acceptance evidence (October 17, 2026)

The private Coach was reopened after publication. Its persisted instruction block matched the repository exactly at 7,990 characters (SHA-256 `dc0253c7133af2fac9e162c8a38bc1692526a9dba2bd4c791fa3496384825f47`); visibility remained **Only me**, and the recommended model remained **Instant 5.6**. The Action schema source SHA-256 was `c9a41b090c8872c13eee82dcfd28c8243e5cee325c07cd5237b54aebd6d76762`; the reopened editor showed all 30 operations and the updated `updateActivePlan` description at 290 characters. API Key/Bearer authentication remained selected; the credential value was not inspected.

Fresh hypothetical conversations verified an increased workout with unknown tolerance prompted about feeling, pain/discomfort and recovery; already-positive feedback was not repeated; reported pain and poorer recovery remained a concern; and identical numbers received a numeric comparison without a tolerance question, speculation or extra advice. These comparisons did not access or change stored workout records.

A read-only review of the current plan and goals produced a complete proposed seven-day replacement with one supported target adjustment, a dated before→after/reason note, and explicit whole-plan confirmation wording. A separate user-declined adjustment was not proposed or written. No confirmation was submitted, so no plan save or persisted read-back was exercised. Stale-token reload/reconfirmation and post-save read-back remain required in the published instructions and Action contract; production state was left unchanged.

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
| Weekly averages differ from outcome readings | Label each Saturday–Friday average with its period and each selected Friday–Sunday outcome with its reading date; never describe an average as a weekend measurement. |
| Compact weekly draft | Keep one concise finding and action per required section, avoid repeated metrics and rationale, and consolidate one to three next-week priorities without changing the weekly JSON contract. |

### Oversized reflection refresh acceptance

Use a fresh, read-only hypothetical conversation; do not save a reflection.

| Scenario | Expected behavior |
| --- | --- |
| A combined refresh for a selected reflection date is too large | Fetch only needed domains one at a time with `getHealthContext`; repeat the exact `from` and selected-date `to` on every call, then finish with evidence through that date only. |

### Nightly sleep goals acceptance

Every sleep interpretation compares raw seconds against the inclusive RECOVERY `minimumTimeInBedSeconds` (25,200) and `minimumTotalSleepSeconds` (21,600). Pre-save drafts and immediate post-save feedback use the supplied seconds without a prerequisite lookup; subsequent reads, trend commentary, DAILY/WEEKLY reflections and duration-based warnings retrieve RECOVERY for the relevant dates. A 6h34 total-sleep record with a 23:10–06:45 interval (7h35 in bed) meets both goals; never report a 26-minute deficit against seven hours asleep. When both personal minimums are met, do not add unsolicited duration warnings, ideal-duration caveats or nudges toward a generic higher target. Broader duration advice remains available when explicitly requested; ordinary record feedback is not that request. Show shortfalls only below the relevant minimum and omit positive goal deltas; missing records or durations remain unknown. Neither nightly goals nor the composite time-in-bed criterion has a nine-hour upper bound; total sleep, efficiency and midpoint regularity remain independent criteria.

Preserve stored measurements and historical reflections. Reassess an active duration warning only through the existing dated, latest-version `saveCoachWarning` resolve flow with newer evidence and a rationale; do not change unrelated warnings or infer a miss from gaps.

Use fresh, hypothetical read-only conversations; do not write production records. For save feedback, simulate a successful save response rather than calling a write Action. Record application identity, private GPT publication and fresh conversation acceptance separately.

| Scenario | Expected behavior |
| --- | --- |
| Pre-save hypothetical 6h35 asleep, 23:51–07:27+02:00 in bed, requesting normal record feedback | Both personal minimums met; preserve the supplied proposal and confirmation without a generic seven-hour deficit, ideal-duration caveat or higher-duration nudge. |
| Saved/read 6h34 asleep, 23:10–06:45 in bed | Both minimums met; no seven-hour asleep deficit, including in post-save feedback. |
| Explicit request for broader sleep-duration advice after meeting both personal minimums | Answer the requested advice while keeping generic guidance separate from the met personal targets. |
| DAILY and WEEKLY drafts through a selected past date | Retrieve date-scoped RECOVERY; apply nightly minimums to observed nights, preserve cutoff and unknown gaps. |
| Active duration warning conflicts with the corrected minimums | Reassess only that warning through its dated/latest-version resolve flow; preserve measurements, past reflections and unrelated warnings. |
| Exactly 7 hours in bed and 6 hours asleep | Mark each nightly minimum and the combined goal as met. |
| Either duration is one second below its minimum but rounds to 7.0 or 6.0 hours | Mark that minimum unmet using raw seconds; do not let display rounding change the result. |
| More than 9 hours in bed and at least 6 hours asleep | Mark both personal minimums met even if the separate sleep trend score is not good. |
| A night improves but still misses a minimum | Recognize the dated gain and remaining gap; do not substitute period averages for the nightly result. |
| No sleep entry or an unrecorded duration | Report unknown or not recorded, not a missed target. |

Publication acceptance on October 19, 2026: instruction revision `9dbacee` was pushed and published as version 120/live version 120. Reopening independently confirmed the exact 8,000-character source instructions (SHA-256 `d9f631a0bc9446d4c508b01fd8d4fc778233f84d974f4bcceb224c90662ae3ca`), unchanged 156,269-character schema (SHA-256 `52cf7e096e946ff7db495b4f08bd56766db42674661f55df7c40e214248b16ad`), 30 Actions, Only me visibility, Thinking 5.6 recommendation and API Key/Bearer authentication. The Coach configuration check passed; application deployment was unnecessary, and the previously verified application tree remains `1a84cc4b9659e678482142783bb99bc3eb66bb38`.

Fresh hypothetical conversations retained the browser's default model selection, displayed as **6 / Pro**, separately from the GPT recommendation; no model preference was changed. The unhinted pre-save 6h35/7h36 normal-feedback proposal met both personal minimums without a generic-duration deficit, caveat or nudge. It preserved supplied timestamps, offsets, sleep, HR and HRV, left missing stages/awake time unknown, and kept calculated efficiency independent. A separate batch passed exact thresholds, independent one-second deficits, 9h30 in bed and missing durations; explicitly requested broader adult-duration advice appeared in its own section. Independent QA reviewed both complete final responses and published identities. No health records were written; these observations do not establish post-save, DAILY/WEEKLY or active-warning acceptance.


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


## Weekly summary refinement acceptance — 2026-10-10

The archive/detail refinement shipped in feature `b1ec277`; the copied-prompt addition shipped in feature `51375dd`, integrated as `a5ee30f`. Production frontend/backend identity and readiness independently passed for tree `d7558c02b0cd024a85cba5ba0274fa8c8235f17c`. Focused Coach validation, lint and all three weekly-summary browser journeys passed; independent candidate design/QA reviewed the archive, disclosures and mobile actions. Clipboard coverage opens Coach and checks selected oldest/latest dates and both windows.

The published private instructions exactly match the source block: 7,996 characters, SHA256 `0e30d953612de5206b376adec56b39a2fba190bf0f060dd343a9413e9368a72b`. Reload checks preserved Only me access, the recommended model, API Key authentication, the unchanged source-equivalent schema and all 30 Actions, with no parser errors. The app prompt change required no further GPT publication.

Instruction-only read-only drafts omitted the explicit average label. The final fresh conversation used the exact prompt copied by the deployed app and passed: Saturday–Friday averages carried full period dates and reading counts, missing blood-pressure coverage remained unavailable, and selected outcomes carried their actual measured dates. It presented six concise finding/action sections and three next-week priorities, then requested confirmation. No confirmation was given.

The complete conversation trace contained only five successful GET Actions: weekly overview/context, catalog, historical nutrition context and active warnings. There were no write Actions. Authenticated GET comparisons before and after release/drafting confirmed that the entire saved report and reflection remained unchanged. Private transcripts, sanitized Action summaries, screenshots and check logs remain in the task review artifacts; no health payload was committed.

## Weekly nutrition snapshot delivery

The candidate WEEKLY schema adds optional nullable nutrition evidence for current, previous-week and 52-week periods, including current targets captured when saved, dated calorie/macro allocations and independent intake coverage. Existing summaries stay unchanged. The Action count, reflection write contract and fenced instructions are unchanged.

The application release at source tree `e9848b97b5edafabd87413f172fddd6e42908ca2` and private Coach GPT publication were verified on 2026-10-24. Saved and live GPT versions both read back as 134. The saved schema SHA-256 is `c99cc14ca1413e6e0c8f8bc28c66ff6bba0da3a68806e51344858a383ca2ffcb` (182,525 characters), matching released source; the unchanged instructions SHA-256 is `7a04424fb950a1f17bd98f533fb4869a97cb5495db86b4ad89c091e05e3d7c18` (7,995 characters). A fresh editor independently showed 30 Actions, no parser errors, Only me access, API Key authentication and GPT-5.6 Thinking as the recommended model. A fresh read-only Coach conversation used `getReflectionContext` for the saved 2026-10-16 WEEKLY report and received legacy schema v5 with null nutrition, as expected. Its effective conversation model was GPT-6 Pro from the account preference. New nutrient evidence requires a naturally saved new summary. Production report, Action and email write acceptance remains pending user-intended use; no synthetic writes were made.

# Weight Control Coach Architecture Plan

## Purpose

Evolve the private Weight Control Reflection GPT into a conversational health and fitness coach that can answer flexible questions using relevant application data.

The coach must support conversations like the reviewed chest-and-belly example without requiring the user to copy workout summaries, nutrition history, health constraints, reflections, or stored progress photos into ChatGPT.

The coach remains informational and must not diagnose conditions, replace clinicians, or override clinician-prescribed exercises.

## Product decisions

- Keep one private custom GPT named `Weight Control Coach`.
- Keep reflections as a specialized workflow and a readable catalog domain.
- Use an adaptive opening: a generic starter produces “What would you like to work on today?”, while a specific request is handled immediately.
- Retrieve relevant read-only data automatically without asking for permission on every call.
- Require explicit user confirmation before writes except the preauthorized Coach warning lifecycle.
- Include data recorded today in general coaching, even when the dashboard day is incomplete.
- Restrict reflection creation to completed dashboard dates.
- Rate a reflection from 1 to 10 only when an active coaching plan applies, using concise evidence-based rationale; unrated reflections remain valid when no plan exists.
- Keep the current single-user bearer token and defer OAuth and Marketplace publication.
- Let the coach assess a stored workout against the active coaching plan, estimate its training demand, and save concise feedback only after confirmation.
- Let the coach retrieve selected stored progress photos automatically when visual comparison is necessary.
- Let users attach meal images directly in ChatGPT; Weight Control stores only the confirmed nutritional estimate.
- Let the coach answer personal-record questions from enabled current records and source-derived progression without treating extrema as health judgments; routine current records are exact, while routine progression contains only configured streak milestones.
- Do not add waist, chest, arm, or other body-measurement tracking in this roadmap.
- Link Friday-Sunday weigh-ins to their completed Saturday-Friday performance week so reflections can interpret a new comparable weight change against recorded evidence without claiming causation.

## Target experience

The coach should support natural requests such as:

- “Give me a reflection for last Friday.”
- “What should I do today?”
- “How can I improve my chest while protecting my lower back?”
- “Compare my latest progress photos with those from three months ago.”
- “Am I following the plan we agreed on?”
- “What does my training volume look like over the last 30 days?”
- “Assess today’s workout against my goal and tell me what to improve next time.”
- “What should I eat for dinner based on today’s meals?”
- “Estimate this meal’s calories and macros, then save it after I confirm.”

The GPT first identifies the intent, discovers available data, retrieves only relevant domains, and then answers or requests confirmation for a write.

## Data catalog and retrieval

### Catalog

Add `GET /api/chatgpt-actions/coach/catalog` with operation ID `getCoachCatalog`.

Return the user timezone, current local date and time, last completed dashboard date, and availability metadata for each domain.

Each domain entry contains its name, record count, first date, and last date; singleton domains use a record count of zero or one.

Expose these domains:

- `PROFILE`: age, height, sex, fitness level, medication flag, and calorie targets.
- `BODY`: weight, scale fat percentage, fat mass, muscle mass, muscle percentage, and changes.
- `VITALS`: blood pressure and lipid panels containing total cholesterol, HDL, LDL, and triglycerides.
- `NUTRITION`: nutrition days, meals, daily totals, macro completeness, and fasting periods.
- `DISHES`: current saved recipe names, serving yields, and ordered ingredient snapshots.
- `FOODS`: current active food catalog portions, nutrition, and stable references.
- `TRAINING`: workouts, exercises, volume, repetitions, duration, distance, heart rate, calories, warm-ups, stretching, and current Coach assessments. Warm-ups and stretching are visible as context but excluded from training metrics, personal records, and assessment demand.
- `RECOVERY`: sleep and mood.
- `BEHAVIOR`: habits, routines, check-ins, and completed-day status.
- `HEALTH_EVENTS`: recorded sicknesses.
- `HEALTH_CONSTRAINTS`: injuries, clinician guidance, medication-related constraints, and other active limitations.
- `DECISIONS`: wins, misses, optional user-reported reasons, rates, and streaks.
- `RECORDS`: enabled current all-time records and source-derived progression inside the requested inclusive range.
- `ACTIVE_PLAN`: the current coaching goal, priorities, and agreed actions.
- `REFLECTIONS`: saved reflection summaries and actions.
- `PROGRESS_PHOTOS`: photo-set metadata only; image files use dedicated operations.

The catalog never returns health records, photo URLs, internal identifiers, email addresses, authentication data, or filesystem paths.

### Scoped context

Decision reasons are optional (up to 500 characters), recorded through the app dialog and editable in Wins and misses history. General Coach DECISIONS context includes reasons within the requested range; reflection contracts and Coach write operations remain unchanged.

Add `GET /api/chatgpt-actions/coach/context` with operation ID `getHealthContext`.

Require inclusive `from`, `to`, and a comma-separated `domains` parameter; reject ranges longer than 90 days.

Return an envelope containing the timezone, requested dates, `lastCompletedDate`, `endDateComplete`, data semantics, and only the requested domain sections.

Treat absent records as unknown and recorded zero values as valid data.

Return daily nutrition totals with `macrosComplete` so the coach does not treat partial macros as complete evidence.

Reuse the same query and mapping layer inside reflection generation, but retain the reflection-specific 30-day detail, 60-day weekly baseline, and year-ago comparison.

## Persistent coaching context

### Health constraints

Add a user-owned `health_constraints` table with:

- `id` and `user_id`.
- `type`: `INJURY`, `CLINICIAN_GUIDANCE`, `MEDICATION`, `ALLERGY`, `DIETARY`, or `OTHER`.
- `title` and `details`.
- `source`: `SELF_REPORTED`, `DOCTOR`, `PHYSIOTHERAPIST`, or `OTHER_CLINICIAN`.
- `start_date`, optional `end_date`, and `active`.
- Creation and update timestamps.

Provide normal authenticated CRUD endpoints and a Settings section for manual management.

Provide read, create, and update Coach Actions; the GPT must summarize the proposed change and receive explicit confirmation before writing it.

Before recommending exercise changes, the GPT retrieves active constraints and treats clinician-prescribed exercises as constraints rather than ordinary program choices.

When advice appears to conflict with clinician guidance, the coach explains the conflict and recommends checking with the clinician instead of instructing the user to stop the prescribed exercise.

### Active coaching plan

Add one optional `coaching_plans` row per user with:

- A primary goal.
- Principles that should remain stable across conversations.
- Ordered priorities.
- Agreed actions.
- Start date and review date.
- Notes and update timestamp.

Store principles, priorities, and actions as JSON string lists using the project’s existing conversion pattern.

Provide normal authenticated read and update endpoints and a Settings section for manual management.

Provide `getActivePlan` and `updateActivePlan` Actions; an update replaces the complete plan only after the user confirms the exact proposed version.

Reflections read the active plan and evaluate relevant actions without silently modifying it.

### Workout assessments

Store one optional Coach-generated assessment per workout in a dedicated `workout_assessments` table with a unique workout relationship and cascading deletion.

Each assessment contains:

- A required goal-alignment score from 1 to 10.
- A required estimated training-demand score from 1 to 10; this is an estimate from recorded workload and recent comparable training, not subjective perceived effort.
- One score rationale of no more than 25 words.
- One strength, one improvement, and one next-workout action, each no more than 15 words.
- The active goal and the coaching-plan and workout update timestamps used for the assessment.
- Creation and update timestamps.

Require an active coaching plan before assessment. When none exists, the coach helps create and confirm one before continuing.

Add `getWorkoutAssessmentContext`, addressed by workout date, returning the exact workout, active plan, active health constraints, recent comparable training, any current assessment, and the plan and workout update timestamps without internal identifiers.

Add confirmed `saveWorkoutAssessment`, also addressed by workout date, to create or atomically replace the single assessment. The request includes the scores, rationale, feedback, context timestamps, and `confirmed: true`; the service derives the stored goal from the active plan.

Reject a save when the workout or plan changed after context retrieval so the coach must reload and reassess the current data. A later plan change does not alter a saved assessment because its goal snapshot preserves the original basis.

When the workout itself changes, delete its assessment. Reassessment creates a new assessment only after another exact proposal and confirmation; do not retain assessment history.

Expose current assessments in general Coach `TRAINING` context, but keep the reflection input and response contracts unchanged.

Recommendations remain informational and must respect active health constraints. They never modify the recorded workout or active plan automatically; any plan change uses the separate confirmed plan-update flow.

## Nutrition architecture

### Meals and daily totals

Use `meals` as the primary persisted nutrition records while preserving `GET /api/calories` as a daily-total compatibility contract.

Every meal has a fixed type: `BREAKFAST`, `LUNCH`, `DINNER`, or `SNACK`. Allow one Breakfast, Lunch, and Dinner per date and multiple numbered Snacks.

Historical calorie records are split evenly between Lunch and Dinner without changing their daily total.

Continue returning aggregated daily calorie totals to the existing dashboard so its trends, targets, reflections, and status calculations remain compatible.

### Meals and fasting

Store calories and optional protein/carbohydrate/fat grams on every meal. Meal times, notes, and sources can extend the existing meal records when Coach writes are implemented.

Allow optional named dishes within a meal. A manual dish has required calories and optional macros; dish totals become the meal totals, while meals without dishes retain direct nutrition entry. Reuse prior dish values and complete meals as independent snapshots. The Coach derives calories and all three macros for every dish from a text description or attached meal image, shows uncertainty and the calculated total, and saves only after confirmation.

Use `/meals/new` and `/meals/:id/edit` for manual meal drafts, retaining `/calories` as history. Show compact dish summaries and use a focused modal for adding, editing, and reusing dishes; Apply changes the local draft only, and page Save persists the whole meal. Preserve errors, warn before discarding changes, retain login destinations, and return to the originating dashboard Calories tab or history Meals tab. The dashboard date remains server-owned. Preserve meal times, durations, automatic fasting, copying, notes, and record feedback.

Store positive dish quantities with up to three decimals and units GRAM, MILLILITRE, SERVING, or UNIT. Retain a stable reference quantity and nutrition snapshot; derive calories with half-up integer rounding and macros with half-up two-decimal rounding. Changing nutrition or unit resets the reference, with no implicit unit conversion. Migrate historical dishes to one serving without changing totals; accept legacy writes without any quantity fields, reject partial combinations, and require quantity/unit in new Coach requests. Manual unknown macros stay null; reused dishes are independent snapshots.

Manual meal preloading lists the latest 14 earlier entries of the selected meal type. Display a dish-only title (first dish and additional dish count, or “No dishes”) with the source date separately. Preserve the destination date/type and copy values into an independent draft; this display-only feature does not change Coach contracts.

The Coach copies readable nutrition values exactly and labels inferred missing values; it must identify dishes without exact values and resolve ambiguous quantities, duplicate image rows, or conflicting totals before confirmation. Store amount-specific totals and concise uncertainty notes, never unsupported claims of exactness. Keep existing confirmation, privacy, context-domain, and reflection boundaries.

Manual meals display a flat list of foods; the existing `dishes` API field and `meal_dishes` storage remain compatibility names. Reusable dishes are separate user-owned recipes, created from selected meal foods without saving or changing the meal. Manage recipes in Nutrition → Dishes and edit their ingredient snapshots at `/dishes/:id/edit`. Each recipe has a unique normalized name, positive serving yield (default one), and ordered food ingredients with stable nutrition references. Adding a recipe scales and inserts independent food rows; recipe edits and deletion never change recorded meals. Recipe management uses session-authenticated `/api/dishes` CRUD; Coach reads identifier-free recipes through the DISHES context domain. Recipe storage is not food consumption and is excluded from nutrition totals and reflection inputs.

Nutrition → Foods manages a separate user-owned food catalog through session-authenticated `/api/foods` CRUD. Import unique meal foods once using the latest meal date, meal ID, and food position; match trimmed names case-insensitively. Manual and confirmed Coach meal saves register new names transactionally without overwriting catalog edits. Deletion and renaming suppress automatic recreation of old names; explicit Add can restore them. Catalog edits affect future food-picker reuse only, preserving saved meal and recipe snapshots. Recipe-only ingredients enter the catalog when saved in a meal; deleting meals does not remove catalog entries. Coach reads identifier-free active entries through the FOODS context domain; catalog entries remain excluded from consumption totals and reflection inputs.

All manual food forms offer a “Scale nutrition with quantity” toggle, enabled when opened. Turning it off allows portion corrections such as one serving to 60 g without changing calories or macros; the corrected portion becomes the nutrition reference. Turning scaling back on scales from that corrected reference. Unit changes perform no implicit conversion, unknown macros remain null, and the toggle is not persisted or added to Coach contracts.

Use `MANUAL` and `GPT_IMAGE_ESTIMATE` as meal sources.

Set future Coach-context `macrosComplete` only when every meal contributing to a daily total has all three macro values.

Add `fasting_periods` with user, start time, end time, and notes.

Store positive whole-minute meal duration, required whenever a start time is recorded. Historical meals receive an assumed 30 minutes. Automatic fasts run from the end of an eating interval to the next meal start, with an eight-hour minimum; overlapping meals extend the eating interval. Coach reads expose duration and confirmed writes require it.

Expose meal management in the existing Calories area while retaining the `/calories` route; add fasting-period management there when the Coach nutrition work is implemented.

For a meal image attached in ChatGPT, the GPT estimates a value and uncertainty for calories and macros, shows the proposed stored values, and calls `createMeal` only after confirmation.

Weight Control never receives or stores the meal image; it stores only the confirmed structured values and the `GPT_IMAGE_ESTIMATE` source.

## Progress-photo retrieval

Add `GET /api/chatgpt-actions/coach/progress-photos` with operation ID `listProgressPhotos`.

Return photo-set IDs, dates, associated body values, and available sides without returning storage paths or image URLs.

Add `GET /api/chatgpt-actions/coach/progress-photos/{photoSetId}/files` with operation ID `getProgressPhotoFiles`.

Accept selected `front`, `left`, and `right` sides and return at most three URLs through `openaiFileResponse`.

Generate five-minute HMAC-signed URLs containing the user ID, photo-set ID, side, expiry, and a distinct progress-photo purpose.

Serve signed files through `GET /api/chatgpt-files/progress-photos/{token}` with the detected MIME type, no session cookie, and ownership revalidation.

Build absolute URLs from a required `CHATGPT_ACTION_PUBLIC_BASE_URL` and sign them with a dedicated `CHATGPT_FILE_SIGNING_SECRET`.

Stored photos remain private at rest, but the documentation and UI must state that any photo retrieved for analysis is transmitted to ChatGPT.

GPT Actions can return files through `openaiFileResponse`, and vision-capable models can analyze image inputs; both behaviors require end-to-end validation in the configured private GPT.

## Action write contracts

Expose these write operations in addition to the existing reflection save operation:

- `createHealthConstraint` and `updateHealthConstraint`.
- `updateActivePlan`.
- `saveWorkoutAssessment`.
- `createMeal`, `updateMeal`, and `deleteMeal`.
- `createFastingPeriod`, `updateFastingPeriod`, and `deleteFastingPeriod`.

Existing record writes include `confirmed: true`; reject false or missing confirmation. Coach warning writes are the sole preauthorized exception and omit this field.

The GPT must present the exact values and consequences before asking for confirmation, especially when replacing the active plan.

Controllers remain thin and resolve the user through `CurrentUserService`; services enforce ownership and business rules.

## GPT behavior

Rename the GPT and documentation from Weight Control Reflection to Weight Control Coach.

Use a generic conversation starter such as “Start my coaching session”; that starter produces the opening question without retrieving data.

When the initial message already contains a question, goal, or image, do not repeat the opening question.

Call `getCoachCatalog` before the first data-backed answer, then request only the domains and date range relevant to the question.

Default to the latest 30 days and expand to at most 90 days when a comparison needs more evidence.

Retrieve active health constraints before exercise, injury, recovery, or nutrition advice where they may affect safety.

Retrieve the active plan for progress, priority, or follow-up questions so recommendations remain consistent across conversations.

For a workout assessment, retrieve the dedicated assessment context, require an active plan, evaluate goal alignment and estimated training demand, and return a short rationale, strength, improvement, and next-workout action.

Present both scores and every feedback field before requesting confirmation. Call `saveWorkoutAssessment` only when the immediately preceding user message confirms that exact proposal.

If the assessment context reports sparse comparison data, state the limitation without treating missing data as zero.

Use progress photos only when the user asks for visual feedback or a photo comparison; list metadata before loading selected files.

For image feedback, describe observable features and uncertainty without diagnosing, assigning an exact body-fat percentage, or inferring unrecorded health conditions.

For reflections, keep the existing overview, context, generate, and save sequence; prefer weekly or milestone reflections rather than encouraging repetitive daily generation.

When a reflection has an active plan, save a 1-to-10 plan-progress score and concise rationale with its structured result. The score is historical output for that reflection and does not change when the active plan changes later. Without an active plan, save the reflection without a rating rather than substituting a general wellness score.

For writes, obtain explicit confirmation in the immediately preceding user message and never infer confirmation from an earlier conversation turn.

## Frontend and configuration

Add a global `Open Coach` action for authenticated users that opens the configured custom GPT in a new tab.

Preserve the Reflections page, reflection archive, and date-specific creation shortcuts.

Replace the long advice prompt with a short natural request because the coach can retrieve its own context.

Rename `VUE_APP_CHATGPT_REFLECTION_URL` to `VUE_APP_CHATGPT_COACH_URL` across frontend, Docker, example environment, and Ansible application-deployment configuration.

Add Settings management for health constraints and the active coaching plan.

Extend the Calories UI into Nutrition without changing its route, and preserve the existing meal-entry workflow.

Add an Assessment column to the workout diary with compact goal-alignment and estimated-demand scores and a read-only dialog for the goal snapshot, rationale, strength, improvement, and next action.

Add an `Assess with Coach` or `Reassess with Coach` action that copies a dated natural-language prompt and opens the configured Coach. The app displays saved assessments but does not create or edit them manually.

## Security and privacy

Keep bearer-token authentication for `/api/chatgpt-actions/**` and map the configured token to the configured single user.

Keep read responses minimal and exclude private or internal fields that the GPT does not need.

Use separate short-lived file tokens instead of exposing session cookies, permanent URLs, or the Action bearer token in photo links.

Do not log Action authorization headers, signed photo tokens, health payloads, or image URLs.

Require HTTPS for production Action and file URLs.

OAuth and public Marketplace access are outside this roadmap.

## Delivery sequence

1. Extract the shared health-data context layer without changing reflection behavior.
2. Add the catalog and scoped read context, including reflections.
3. Add health constraints and their safety behavior.
4. Add the active coaching plan and confirmed updates.
5. Add meals, macros, fasting, calorie compatibility, structured nutrition context, and confirmed meal/fasting Actions.
6. Add the general Coach schema, instructions, short prompts, and global launcher.
7. Add confirmed Coach-generated workout assessments and their workout-diary presentation.
8. Add stored progress-photo retrieval.
9. Add and validate the meal-image estimation workflow using the confirmed meal Actions delivered in step 5.
10. Run end-to-end private GPT acceptance testing and complete the cutover.

Each step must be independently deployable and must leave the current reflection workflow functional.

## Acceptance scenarios

- A user asks for a dated reflection; the GPT retrieves the established reflection context and saves the same structured result as before.
- A reflection generated with an active plan shows a concise 1-to-10 plan-progress rating in the result and archive; one without a plan remains unrated.
- A user asks for current advice; the GPT uses today’s available records, the active plan, recent reflections, and relevant constraints without a copied prompt.
- A user asks how to improve their physique; the GPT retrieves profile, body, training, nutrition, active-plan, constraint, and selected progress-photo data.
- A user records that physiotherapists prescribed specific exercises; after confirmation, later fitness advice recognizes and does not casually contradict that guidance.
- A user asks whether to add biceps work; the GPT retrieves recent exercise volume instead of requiring a pasted 30-day summary.
- A user requests an assessment of a stored workout; the GPT evaluates it against the active plan and constraints, proposes two scores and concise actionable feedback, waits for confirmation, and saves the exact assessment.
- An edited workout deletes its prior assessment, so the user can request a new assessment for the revised workout.
- A user asks what to eat for dinner; the GPT uses meals and macros already recorded for that day and identifies incomplete macro data.
- A user attaches a meal image; the GPT estimates nutrients, obtains confirmation, and saves the meal without Weight Control storing the image.
- A user asks to compare photos; only the requested photo sets and sides are delivered through expiring links.
- No Action response contains an email address, filesystem path, authentication secret, permanent photo URL, or unrelated health domain.

## Out of scope

- OAuth and ChatGPT Marketplace publication.
- A native chat interface inside Weight Control.
- Backend calls to the OpenAI API; model reasoning remains inside the custom GPT.
- Automatic workout assessment, manual workout ratings, and workout-assessment history.
- Workout assessments as reflection evidence; general Coach context uses them without changing reflection contracts.
- Storage of meal images.
- New body measurements such as waist or chest circumference.
- Medical diagnosis, treatment recommendations, or autonomous changes to clinician guidance.

## Official OpenAI references

- [Data retrieval with GPT Actions](https://developers.openai.com/api/docs/actions/data-retrieval)
- [GPT Action authentication](https://developers.openai.com/api/docs/actions/authentication)
- [Action file responses](https://developers.openai.com/cookbook/examples/chatgpt/gpt_actions_library/gpt_action_snowflake_middleware#format-openaifileresponse)
- [Images and vision](https://developers.openai.com/api/docs/guides/images-vision#analyze-images)

## Pain-free back check-ins

Back tracking supports explicit `NONE` check-ins with no location for a selected date and period; absent entries still mean no back-pain problem. A pain-free check-in cannot coexist with pain entries in that period. Existing confirmed Coach health-entry writes and HEALTH_EVENTS reads support this state without resolving health constraints or changing reflection contracts. Publish the updated Coach schema and instructions separately after application deployment.

## GPT action notifications

Every successful GPT save, update, or delete records one persistent in-app notification in the mutation transaction and sends push after commit to the user's existing subscriptions. Messages identify only the concept and operation, such as “Lunch saved” or “Sleep updated”; they contain no recorded values or notes. Reads and failed writes produce no notification. Generic health-entry Actions share the same notification path as dedicated endpoints. Push uses best-effort delivery; disabled or failed push does not affect the committed write or bell notification. Bell notifications remain across days until opened or dismissed and open the relevant existing app section, including after deletion. Manual app writes, reflection contracts, Action schemas, and private GPT instructions remain unchanged.

## Persistent Coach warnings

Coach automatically reviews recent recovery, sleep, mood, routines, nutrition, training, pain and relevant health evidence during general advice and reflections. Compare seven recent days with preceding baseline in the default 30 days, inspect 14 days for onset, and expand only when useful up to 90 days. Preserve sparse/partial data semantics and historical reflection dates; retrieve current evidence before changing current warnings. Group related signals and describe uncertainty without diagnosis.

The warning types are `RECOVERY_STRAIN`, `SLEEP_DISRUPTION`, `MOOD_DECLINE`, `ROUTINE_DISRUPTION`, `NUTRITION_IMBALANCE`, `TRAINING_STRAIN`, `PAIN_INCREASE`, and `HEALTH_CHANGE`. One active warning per user/type, serialized user-owned writes, unique retry keys and version checks prevent duplicate or stale changes. Resolved episodes and paginated review snapshots remain available; recurrence creates a new episode.

Warnings alone are automatically created, updated and resolved without confirmation. Only Coach resolves after newer evidence supports recovery; no user dismissal, expiry or background monitoring. All other writes still require immediate confirmation. Warning Actions use `x-openai-isConsequential: false`; ChatGPT may still show its initial permission UI and offer Always allow ([OpenAI documentation](https://developers.openai.com/api/docs/actions/production#consequential-flag)).

Session-authenticated `/api/coach-warnings` reads return active warnings and history availability; `/history` and `/{id}/revisions` return ten entries per page. Dedicated `/api/chatgpt-actions/coach/warnings` Actions use GET `getCoachWarnings` (ACTIVE/HISTORY/REVISIONS views) and POST `saveCoachWarning` (one create/update/resolve payload). This keeps the GPT schema within its 30-operation limit while retaining creation, versioned updates and resolution. General context and reflection contracts are unchanged; warning IDs and explanations remain in dedicated interfaces with no user/account identifiers or credentials.

The dashboard header shows one enum-derived label or an `N warnings` indicator. Its read-only dialog displays evidence, explanation, next action, review date, resolved history and prior revisions. Current warnings remain independent of dashboard navigation. Refresh on load and return from Coach; retain displayed data and show retryable failures. No push notification is emitted for warning mutations.

Validation: focused Coach warning service/controller tests and MariaDB schema validation; Playwright warning cases at 390, 575, 640, 960 and 1280px; the release gate runs lint, full browser/backend suites and production builds. Verify keyboard access, zero/one/multiple warnings, revisions, independent resolution, recurrence, failures and preserved reflection/confirmed-write behavior.

Delivery: deploy the application before publishing the private GPT instructions and schema in Chrome. Keep the GPT private and existing bearer credentials unchanged. Verify its read Action against production; do not create artificial health warnings in production for testing.

## Shared Coach Action connection repair

The September 9 failure affects sleep saving and workout rating; catalog reads also fail in ChatGPT. The malformed editor draft was repaired and all 30 operations published, but publication did not restore execution. Treat this as a shared connection issue until request/status evidence isolates a domain failure; see [diagnostic evidence](action-connection-diagnostics.md).

Create sleep directly after exact confirmation; the backend rejects duplicate dates. Retrieve an existing sleep only for replacement and confirm that replacement before writing. Preserve required timestamps, second-based durations, ownership and workout context/version checks. Omit unsupported screenshot observations; add no health metrics or schema fields.

Compare catalog, sleep and workout context through the published GPT with direct authenticated production reads; correlate metadata without credentials or health payloads. Repair only the failing boundary established by evidence. Keep all 30 Actions, bearer authentication, private visibility, reflection contracts and confirmation requirements. Validate authentication, Coach controller, sleep and workout services before the release gate. Deployment and GPT publication are separate from acceptance: require successful GPT reads, a confirmed real sleep save/read-back and a confirmed workout assessment save/read-back. Never fabricate production test records or uncertain timestamps.

## Coach authentication alerts

Log all Coach Action authentication failures; Telegram alerts require a User-Agent matching the verified ChatGPT-User product (live catalog request observed September 9, 2026). Match product boundaries and allow version changes; the header is a notification heuristic, never authentication or proof of a particular GPT. Missing/unrelated User-Agents are logged without affecting alert counts or cooldowns.

A dedicated worker sends an initial alert, aggregates repeats across operations for 15 minutes, and retries failed delivery no sooner than 15 minutes or Telegram's longer retry_after. Messages contain UTC times, counts, fixed endpoint templates and failure reasons, never raw headers, credentials, dates/IDs from paths or health payloads. Show at most ten endpoint/reason groups plus the total; all failures remain in server logs. Pending counters are bounded and in memory, so restart resets them.

Production reuses the deployment Telegram bot/chat through APP_TELEGRAM_BOT_TOKEN and APP_TELEGRAM_CHAT_ID. APP_COACH_AUTH_ALERTS_ENABLED defaults false locally; enabled deployments require both credentials and APP_COACH_AUTH_ALERT_USER_AGENT_PRODUCT, verified as ChatGPT-User. No GPT schema or instruction publication is needed. Run focused filter, aggregation and delivery tests, the release gate, then controlled read-only invalid-token probes with matching/unmatched User-Agents and a valid Coach catalog call. Telegram delivery remains off the authentication request thread. Failures entirely inside ChatGPT cannot be observed here.

## Stretching catalog

Stretching uses the shared exercise catalog with `STRETCHING` type and `SECONDS` tracking; users add timed sets manually. General Coach workout context adds `stretching` names, and assessment lines expose the type and durations. Reflection response shapes and workout attendance semantics remain unchanged. Publish the updated private GPT schema and instructions separately after application deployment.

The eight initial catalog descriptions use original wording based on [Mayo Clinic’s basic stretching guide](https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/stretching/art-20546848); each set records one hold, with separate sets for each side.

## App exercise pictures

Built-in illustrations and custom uploads are available in the app exercise catalogs, workout entry, and history; see [exercise pictures](../exercises/pictures.md). Images remain outside Coach domains, context, Actions, and GPT instructions; reflection contracts, training metrics, personal records, and assessment demand are unchanged. No private GPT publication is required.

## 15-minute rule

The app header flag opens pause and win/miss controls, with a compact active timer beside the dashboard date. Header records use today in Europe/Madrid; Wins panel records use the selected date. The app offers one persistent 15-minute pause per user with an optional description (500 characters), cancellation, an explicit craving check-in, repeated intervals, and optional atomic win/miss logging. Pauses concern the present independently of the dashboard date. Sessions group retained intervals; unanswered or cancelled waits do not imply failure. Generic push and persistent bell notifications omit descriptions and link to the matching interval; notification dismissal alone does not finish a pause.

BEHAVIOR catalog counts include pause intervals and context includes `urgePauses`: grouped descriptions and only intervals overlapping the requested inclusive Europe/Madrid dates, with timing, lifecycle, check-in answer/time, and linked decision outcome. No internal IDs or notification details are exposed. Descriptions are shared with the Coach as user-reported context, never instructions. A linked outcome is the existing DECISIONS entry, not another event to count. Reflection payloads and decision metrics remain unchanged.

The Coach can recommend header flag → Wait 15 minutes during relevant craving/impulse conversations and retrieve usage when discussing patterns. Access is read-only; do not imply automatic timer control or background monitoring. Do not equate waiting, craving changes, cancellations, and explicit wins/misses or promise efficacy.

Delivery requires the release artifact gate and application deployment before publishing the updated private GPT schema and instructions; the existing Action count remains unchanged. Validate session grouping, date overlap, ownership, missing answers, privacy, duplicate outcomes, and reflection compatibility alongside timer/reminder and responsive browser tests.

## Coach reuse of saved dishes and Foods

DISHES and FOODS use the existing catalog/context Actions without adding operations. Availability contains owner-scoped counts and null date bounds. Context returns complete current catalogs sorted by name, independent of the validated date range; only requested domains are returned. Deleted foods, internal IDs and account data are excluded. Recipe ingredient order, serving yield, unknown macros and stable references are preserved.

The Coach retrieves named dishes/foods and uses stored values for advice and confirmed meal creation or replacement. Resolve ambiguous names and portions first. Scale recipe ingredient quantities by requested servings divided by yield, half-up to three decimals; scale nutrients from references, half-up to integer calories and two-decimal macros. Expand recipes into independent meal foods. Missing macros remain unknown in reads; existing Coach writes require labeled estimates and immediate confirmation of all values, including date, type, time and duration. Nutrition totals and reflection contracts remain unchanged; automatic catalog registration now follows the food reuse rules below.

Returned recipes and catalog foods are transmitted to ChatGPT. Deploy backend support before publishing the updated private GPT schema and instructions; verify catalog reads and conversational reuse without creating artificial production meals.

## Automatic food catalog reuse

Coach retrieves FOODS before meal proposals, reuses English canonical names across translations and portion variants, and automatically marks genuinely new reusable foods with `addToCatalog: true`. Uncertain matches remain in meals only; no separate food review is requested. Existing meal confirmation still applies. Registration is atomic with confirmed meal writes, defaults false for older requests, preserves existing nutrients and deleted-name suppression, and adds no Action operations. Manual meal saves no longer extend the catalog; explicit Foods CRUD remains available. Historical meals, recipes and reflections are unchanged.

Production cleanup consolidates clear variants and translates catalog names while preserving canonical nutrients and independent historical snapshots. Deploy backend support before publishing the updated private GPT schema/instructions; verify new-food registration, synonym reuse and uncertain meal-only entries.

## Saved stretching sets

User-owned named sets store ordered stretching exercises and timed holds; applying a set copies missing exercises into the workout draft and preserves existing holds. Sets are app-only and do not add Coach domains, Actions, or GPT instructions. Saved workouts retain existing stretching context, privacy, reflection contracts, and exclusions from training metrics, personal records, and assessment demand. No private GPT publication is needed.

## Weekly workout plans

Workouts → Plan stores one current Monday–Sunday commitment with required start/review dates and immutable archived commitments. New plans archive the current plan atomically; ordinary app and Coach edits replace only the current plan. Planned targets and exercise metadata are snapshots, independent of recorded workouts, personal records, reminders, reflections, and saved stretching sets.

WORKOUT_PLAN context is an identifier-free current snapshot independent of historical date filters. getActivePlan with target WORKOUT exposes scoped exercise references and a current update token; updateActivePlan with target WORKOUT requires immediate confirmation of the complete replacement and rejects stale context. Editing does not create an archive. Coach action notifications link to /workouts?tab=plan.

Validation covers MariaDB migration/persistence, ownership, concurrent creation, immutable archives, snapshot preservation, target rules, stale/confirmed writes, context privacy, shared workout-editor regressions, and responsive browser workflows. Release acceptance includes the artifact gate, production verification, and private GPT publication with read/edit acceptance.

The private GPT editor enforces 30 operations. Weekly workout editing extends getActivePlan/updateActivePlan with target=WORKOUT; absent target or COACHING preserves the original coaching-plan contract. The backend dispatches by target and validates each request independently.

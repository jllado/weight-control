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
- Require explicit user confirmation before every Action that creates or changes data.
- Include data recorded today in general coaching, even when the dashboard day is incomplete.
- Restrict reflection creation to completed dashboard dates.
- Keep the current single-user bearer token and defer OAuth and Marketplace publication.
- Let the coach assess a stored workout against the active coaching plan, estimate its training demand, and save concise feedback only after confirmation.
- Let the coach retrieve selected stored progress photos automatically when visual comparison is necessary.
- Let users attach meal images directly in ChatGPT; Weight Control stores only the confirmed nutritional estimate.
- Let the coach answer personal-record questions from enabled current records and source-derived progression without treating extrema as health judgments; routine current records are exact, while routine progression contains only configured streak milestones.
- Do not add waist, chest, arm, or other body-measurement tracking in this roadmap.

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
- `TRAINING`: workouts, exercises, volume, repetitions, duration, distance, heart rate, calories, and current Coach assessments.
- `RECOVERY`: sleep and mood.
- `BEHAVIOR`: habits, routines, check-ins, and completed-day status.
- `HEALTH_EVENTS`: recorded sicknesses.
- `HEALTH_CONSTRAINTS`: injuries, clinician guidance, medication-related constraints, and other active limitations.
- `DECISIONS`: wins, misses, rates, and streaks.
- `RECORDS`: enabled current all-time records and source-derived progression inside the requested inclusive range.
- `ACTIVE_PLAN`: the current coaching goal, priorities, and agreed actions.
- `REFLECTIONS`: saved reflection summaries and actions.
- `PROGRESS_PHOTOS`: photo-set metadata only; image files use dedicated operations.

The catalog never returns health records, photo URLs, internal identifiers, email addresses, authentication data, or filesystem paths.

### Scoped context

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

When the workout itself changes, retain the assessment but mark it as outdated by comparing its workout timestamp with the current workout timestamp. Reassessment replaces it only after another exact proposal and confirmation; do not retain assessment history.

Expose current and outdated assessments in general Coach `TRAINING` context, but keep the reflection input and response contracts unchanged.

Recommendations remain informational and must respect active health constraints. They never modify the recorded workout or active plan automatically; any plan change uses the separate confirmed plan-update flow.

## Nutrition architecture

### Meals and daily totals

Use `meals` as the primary persisted nutrition records while preserving `GET /api/calories` as a daily-total compatibility contract.

Every meal has a fixed type: `BREAKFAST`, `LUNCH`, `DINNER`, or `SNACK`. Allow one Breakfast, Lunch, and Dinner per date and multiple numbered Snacks.

Historical calorie records are split evenly between Lunch and Dinner without changing their daily total.

Continue returning aggregated daily calorie totals to the existing dashboard so its trends, targets, reflections, and status calculations remain compatible.

### Meals and fasting

Store calories and optional protein/carbohydrate/fat grams on every meal. Meal times, notes, and sources can extend the existing meal records when Coach writes are implemented.

Use `MANUAL` and `GPT_IMAGE_ESTIMATE` as meal sources.

Set future Coach-context `macrosComplete` only when every meal contributing to a daily total has all three macro values.

Add `fasting_periods` with user, start time, end time, and notes.

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

Every write request includes `confirmed: true`; reject false or missing confirmation.

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

If the assessment context reports sparse comparison data, state the limitation without treating missing data as zero. If an existing assessment is outdated, explain that the workout changed and reassess the current version.

Use progress photos only when the user asks for visual feedback or a photo comparison; list metadata before loading selected files.

For image feedback, describe observable features and uncertainty without diagnosing, assigning an exact body-fat percentage, or inferring unrecorded health conditions.

For reflections, keep the existing overview, context, generate, and save sequence; prefer weekly or milestone reflections rather than encouraging repetitive daily generation.

For writes, obtain explicit confirmation in the immediately preceding user message and never infer confirmation from an earlier conversation turn.

## Frontend and configuration

Add a global `Open Coach` action for authenticated users that opens the configured custom GPT in a new tab.

Preserve the Reflections page, reflection archive, and date-specific creation shortcuts.

Replace the long advice prompt with a short natural request because the coach can retrieve its own context.

Rename `VUE_APP_CHATGPT_REFLECTION_URL` to `VUE_APP_CHATGPT_COACH_URL` across frontend, Docker, example environment, and Ansible application-deployment configuration.

Add Settings management for health constraints and the active coaching plan.

Extend the Calories UI into Nutrition without changing its route, and preserve the existing meal-entry workflow.

Add an Assessment column to the workout diary with compact goal-alignment and estimated-demand scores, an outdated state, and a read-only dialog for the goal snapshot, rationale, strength, improvement, and next action.

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
- A user asks for current advice; the GPT uses today’s available records, the active plan, recent reflections, and relevant constraints without a copied prompt.
- A user asks how to improve their physique; the GPT retrieves profile, body, training, nutrition, active-plan, constraint, and selected progress-photo data.
- A user records that physiotherapists prescribed specific exercises; after confirmation, later fitness advice recognizes and does not casually contradict that guidance.
- A user asks whether to add biceps work; the GPT retrieves recent exercise volume instead of requiring a pasted 30-day summary.
- A user requests an assessment of a stored workout; the GPT evaluates it against the active plan and constraints, proposes two scores and concise actionable feedback, waits for confirmation, and saves the exact assessment.
- An edited workout keeps its prior assessment visibly marked as outdated until the user confirms a reassessment.
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

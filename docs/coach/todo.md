# Weight Control Coach TODO

This checklist implements [the Weight Control Coach architecture](plan.md) in dependency order.

Complete and validate one group before starting the next, and keep the existing reflection workflow operational after every group.

## 1. Shared health-data context

Dependencies: none.

- [x] Capture regression tests for the current reflection context, including detailed and baseline windows, year comparison, missing data, recorded zero calories, summarized workout data, and private-field exclusion.
- [x] Extract repository queries and response mapping shared by reflections and coaching into a focused `HealthDataContextService`.
- [x] Keep reflection eligibility, comparison calculations, persistence, and response JSON unchanged.
- [x] Keep photo paths and internal identifiers outside the shared health-data result.
- [x] Run the backend tests.

Definition of done: `DashboardReflectionService` uses the shared context layer, all reflection tests pass unchanged, and a generated reflection context remains contract-compatible.

Validation:

```bash
cd backend && ./gradlew test
```

## 2. Coach catalog and scoped context

Dependencies: group 1.

- [x] Define the catalog domain enum and DTOs for availability metadata.
- [x] Implement record counts and date coverage for every existing domain and `REFLECTIONS`.
- [x] Include blood pressure and lipid panels in `VITALS` availability and scoped context.
- [x] Add `GET /api/chatgpt-actions/coach/catalog` as `getCoachCatalog`.
- [x] Add `GET /api/chatgpt-actions/coach/context` as `getHealthContext` with required `from`, `to`, and `domains` parameters.
- [x] Limit scoped detailed retrieval to 90 inclusive days.
- [x] Include timezone, current local time, last completed date, completion state, and data semantics in the response envelope.
- [x] Include today’s recorded data in general coaching context without changing reflection completion rules.
- [x] Return only requested domains and exclude email, storage paths, authentication values, and unrelated records.
- [x] Add controller and service tests for catalog metadata, domain selection, range validation, today’s partial data, and empty domains.
- [x] Add the read operations and schemas to a new `docs/coach/coach-action.openapi.yaml` while keeping the existing reflection operations.

Definition of done: the private Action token can discover available data and retrieve a minimal domain-scoped context while the reflection endpoints still pass their regression suite.

Validation:

```bash
cd backend && ./gradlew test
```

## 3. Health constraints

Dependencies: group 2.

- [x] Add Flyway migration `V30__add_health_constraints.sql` with user ownership, type, title, details, source, dates, active state, timestamps, and useful user/date indexes.
- [x] Add the health-constraint domain model, repository, DTOs, and service.
- [x] Add normal authenticated list, create, update, and delete endpoints.
- [x] Add read, create, and update Coach Actions; require `confirmed: true` for writes.
- [x] Add `HEALTH_CONSTRAINTS` to the catalog and scoped context.
- [x] Add a Settings section for listing, creating, editing, deactivating, and deleting constraints.
- [x] Preserve source distinctions between self-reported information and clinician guidance.
- [x] Add GPT instructions to retrieve active constraints before potentially affected recommendations.
- [x] Add tests for ownership, active/date filtering, validation, confirmed writes, and response privacy.
- [x] Add an acceptance prompt where physiotherapist-prescribed exercises are not casually removed from the plan.
- [x] Run backend tests and frontend lint/build.

Definition of done: constraints can be maintained in the app or through confirmed Actions and consistently influence safety-sensitive coaching.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 4. Active coaching plan

Dependencies: group 3.

- [x] Add Flyway migration `V31__add_coaching_plans.sql` with one optional row per user.
- [x] Store the goal, JSON string lists for principles/priorities/actions, start date, review date, notes, and timestamps.
- [x] Add the domain model, repository, DTOs, and service for complete-plan reads and replacements.
- [x] Add normal authenticated read and update endpoints.
- [x] Add `getActivePlan` and `updateActivePlan` Actions; require `confirmed: true` and replace the complete plan atomically.
- [x] Add `ACTIVE_PLAN` to the catalog and scoped context.
- [x] Add a Settings editor that shows the complete plan before saving changes.
- [x] Include the active plan in reflection input and compare relevant actions without letting reflections modify it.
- [x] Add tests for the one-plan invariant, ownership, atomic replacement, confirmed writes, and reflection integration.
- [x] Run backend tests and frontend lint/build.

Definition of done: goals and agreed actions persist across conversations, can be edited in either surface, and are considered by advice and reflections.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 5. Meals, macros, and fasting

Dependencies: the meal-tracking foundation is independent; Coach integration depends on group 4.

- [x] Add Flyway migration `V27__add_meal_nutrition.sql`.
- [x] Evolve historical calorie rows into fixed Lunch and Dinner meals without changing daily totals.
- [x] Store meal calories and optional protein, carbohydrate, and fat values.
- [x] Support optional named dishes, derived meal totals, and reuse of prior dishes and meals.
- [x] Support one Breakfast, Lunch, and Dinner plus multiple numbered Snacks per date.
- [x] Add `fasting_periods` with start, end, and notes.
- [x] Add optional meal time, notes, and source fields for Coach-created entries.
- [x] Calculate daily meal calorie totals in the calorie compatibility service.
- [x] Preserve `GET /api/calories` reads as a compatibility facade for existing dashboard code.
- [x] Refactor reflection nutrition reads to use aggregated daily totals.
- [x] Add normal authenticated meal CRUD endpoints.
- [x] Preserve `/calories` and add fixed meal editing with per-meal macros.
- [x] Add fasting-period management and macro-completeness summaries.
- [x] Add `NUTRITION` catalog coverage for nutrition days, meals, macros, and fasting periods.
- [x] Add migration, ownership, aggregation, compatibility, and zero-calorie tests.
- [x] Add fasting, Coach write-confirmation, and macro-completeness tests.
- [x] Add meal duration, a 30-minute historical backfill, end-based automatic fasting, and matching Coach contracts.
- [x] Run MariaDB schema validation, backend tests, frontend lint/build, and relevant end-to-end tests.

Definition of done: historical daily totals remain unchanged, users can record fixed meals and optional macros, and the coach can answer meal, macro, and fasting questions from structured data.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 6. General Coach integration

Dependencies: groups 2–5.

- [x] Create `docs/coach/coach-gpt.md` with the complete coach configuration and instructions.
- [x] Complete `docs/coach/coach-action.openapi.yaml` with catalog, context, constraints, active-plan, nutrition, and existing reflection operations.
- [x] Give every operation a distinct, intent-revealing operation ID and concise description so the GPT can select it correctly.
- [x] Configure adaptive opening behavior for generic starters and immediate handling of specific requests.
- [x] Configure progressive retrieval: catalog first, then relevant domains, default 30 days, maximum 90 days.
- [x] Require immediate explicit confirmation before every write Action.
- [x] Add wellness, missing-data, image uncertainty, clinician-guidance, and no-diagnosis rules.
- [x] Rename `VUE_APP_CHATGPT_REFLECTION_URL` to `VUE_APP_CHATGPT_COACH_URL` in frontend, Docker, `.env.example`, and Ansible deployment variables/templates.
- [x] Add a global authenticated `Open Coach` action that opens the GPT in a new tab.
- [x] Preserve the Reflections route, archive, and date-specific create/update buttons.
- [x] Replace the long advice prompt with a short natural request and remove embedded reflection data from copied prompts.
- [x] Import the schema into the private Weight Control Coach GPT and keep bearer API-key authentication.
- [x] Verify reflection, advice, training-volume, constraint, active-plan, nutrition, and follow-up conversations manually.
- [x] Mark the old reflection GPT documentation and schema as superseded only after the coach schema works end to end.
- [x] Run backend tests, frontend lint/build, and relevant end-to-end tests.

Definition of done: the private Weight Control Coach handles reflections and flexible data-backed conversations without copied health summaries.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 7. Coach workout assessments

- [x] Expose recorded warm-ups to Coach workout context while excluding them from training metrics, personal records, and assessment demand.

Dependencies: groups 4 and 6.

- [x] Add a Flyway migration for one optional `workout_assessments` row per workout, with cascading deletion and a unique workout constraint.
- [x] Store required 1–10 goal-alignment and estimated training-demand scores, a concise rationale, one strength, one improvement, one next-workout action, the active-goal snapshot, context timestamps, and audit timestamps.
- [x] Add the workout-assessment domain model, repository, DTOs, and focused service logic while preserving the existing workout controller layering.
- [x] Include the optional assessment in normal workout responses without adding a manual write endpoint.
- [x] Add `getWorkoutAssessmentContext` by workout date with the exact workout, active plan, active constraints, recent comparable training, current assessment, and plan/workout timestamps, excluding internal identifiers and unrelated health data.
- [x] Require an active coaching plan and use the current active goal as the server-derived assessment goal.
- [x] Add confirmed `saveWorkoutAssessment` by workout date to create or atomically replace the single assessment after the exact proposal is confirmed.
- [x] Require the context plan and workout timestamps on save; reject stale proposals and make the GPT reload the context before reassessing.
- [x] Preserve an assessment after plan changes with its original goal snapshot; delete it after workout edits and retain no assessment history after confirmed replacement.
- [x] Add assessment summaries to general Coach `TRAINING` context without changing reflection input or response JSON.
- [x] Add the context and save operations, request/response schemas, score ranges, text limits, and confirmation contract to `docs/coach/coach-action.openapi.yaml`.
- [x] Add GPT instructions to estimate training demand rather than subjective effort, respect active constraints, acknowledge sparse comparison data, and propose a rationale of at most 25 words plus a strength, improvement, and next action of at most 15 words each.
- [x] Prevent assessment feedback from automatically modifying the recorded workout or active plan; use the separate confirmed plan-update flow when appropriate.
- [x] Add an Assessment column to the workout diary with compact scores and a read-only feedback dialog.
- [x] Add `Assess with Coach` and `Reassess with Coach` actions that copy a dated prompt and open the configured Coach; do not add manual assessment editing.
- [x] Add migration, ownership, score validation, confirmation, stale-context, atomic-replacement, cascade, context-privacy, Coach-context, and reflection-regression tests.
- [x] Manually assess a workout, verify no write occurs before confirmation, confirm and view the saved feedback, edit the workout, verify the assessment is deleted, and confirm a reassessment.
- [x] Run backend tests, frontend lint/build, and relevant end-to-end tests.

Definition of done: the Coach can assess a stored workout against the active goal and constraints, save short actionable feedback only after confirmation, and surface current results in the workout diary without changing reflection contracts.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 8. Stored progress-photo retrieval

Dependencies: group 6.

- [x] Add `CHATGPT_ACTION_PUBLIC_BASE_URL` and `CHATGPT_FILE_SIGNING_SECRET` to application, Docker, example environment, and Ansible deployment configuration.
- [x] Preserve the existing session-authenticated photo endpoints for the frontend.
- [x] Add `listProgressPhotos` returning dates, photo-set IDs, body values, and available sides without storage paths.
- [x] Add `getProgressPhotoFiles` accepting selected sides and returning no more than three signed URLs through `openaiFileResponse`.
- [x] Implement five-minute HMAC tokens containing user, photo set, side, expiry, and progress-photo purpose.
- [x] Add the public signed-download endpoint under `/api/chatgpt-files/progress-photos/{token}` with correct MIME types.
- [x] Revalidate user ownership and selected-side existence when serving each signed file.
- [x] Ensure logs do not contain tokens, image URLs, authorization headers, or health payloads.
- [x] Add GPT instructions to list metadata first and retrieve only photo sets and sides needed by the user’s request.
- [x] Add a disclosure that retrieved progress photos are transmitted to ChatGPT for analysis.
- [x] Test valid downloads, expiry, signature tampering, wrong purpose, wrong user, missing sides, MIME types, and Action authentication.
- [x] Test front/side photo comparisons in the configured private GPT.

Definition of done: the coach can analyze selected stored progress photos without manual attachment or permanent public URLs.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 9. Meal-image estimate workflow

Dependencies: groups 5 and 6.

- [x] Add `createMeal`, `updateMeal`, and `deleteMeal` Actions to the coach schema in group 5.
- [x] Add confirmed fasting-period write Actions in group 5.
- [x] Require the meal date, fixed meal type, calories, optional macros, notes, `GPT_IMAGE_ESTIMATE` source, and `confirmed: true` for image-derived creation.
- [x] Require every Coach-created dish to include estimated protein, carbohydrates, and fat.
- [x] Instruct the GPT to show estimated ranges, uncertainty, and exact proposed stored values before requesting confirmation.
- [x] Prevent the GPT from calling a meal write Action when the user has not confirmed the exact values in the immediately preceding message.
- [x] Verify that no image bytes, ChatGPT file IDs, or image URLs are accepted or persisted by Weight Control.
- [x] Add tests for validation, confirmation, fixed-meal conflicts, ownership, source, and structured response data.
- [x] Manually test attaching a meal image in ChatGPT, correcting the estimate, confirming it, and reading the updated daily totals.

Definition of done: a ChatGPT-attached meal image can produce a reviewed, confirmed structured meal while Weight Control stores no image.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 10. Final acceptance and cutover

Dependencies: groups 1–9.

Repository, private-GPT, authenticated frontend, conversational, and production acceptance is complete.

- [x] Run the complete backend, frontend, schema, and end-to-end test suites.
- [x] Verify that existing reflection records, calories, photos, workouts, and user settings remain accessible after migrations.
- [x] Exercise every Action with missing, incorrect, and valid bearer authentication.
- [x] Audit every Action response for unnecessary personal data, internal identifiers, paths, secrets, and unrelated domains.
- [x] Verify general advice uses today’s partial records while reflection creation rejects incomplete or ineligible dates.
- [x] Reproduce the shared conversation’s key questions using stored profile, workouts, nutrition, constraints, plan, reflections, and progress photos instead of pasted summaries or manually attached progress photos.
- [x] Verify that clinician guidance is surfaced before affected exercise recommendations.
- [x] Verify that workout assessments use an active goal and constraints, require immediate confirmation, retain their goal snapshot, and are deleted after workout edits.
- [x] Verify that every persisted change follows an immediately confirmed proposal.
- [x] Verify signed progress-photo URLs only work over HTTPS in production and expire after five minutes.
- [x] Update the private GPT’s name, description, conversation starters, instructions, and Action schema.
- [x] Remove superseded reflection-only GPT setup files after the coach configuration is safely recorded.
- [x] Document production configuration and the privacy implications of sending health records and selected photos to ChatGPT.

Definition of done: the private coach supports the complete planned conversation flow, existing features remain functional, and the privacy and safety checks pass.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 11. Personal-record integration

Dependencies: personal-record milestones 1–6.

- [x] Add `RECORDS` catalog availability and scoped read-only context.
- [x] Return enabled current records and progression only inside the requested inclusive range.
- [x] Preserve exact routine best streaks in current context while limiting routine progression to streak milestones.
- [x] Exclude source IDs, settings internals, storage details, and unrelated health domains.
- [x] Update the Action schema and GPT instructions to describe extrema as observations rather than health judgments.
- [x] Add catalog, context, range, privacy, and reflection-regression tests.
- [x] Import the updated schema into the configured private GPT after production deployment.
- [x] Ask current-record and dated-progression questions and verify catalog-first, `RECORDS`-only retrieval.
- [x] Verify the Coach does not describe a minimum or maximum as healthier without separate evidence.
- [x] Verify record answers expose no internal identifiers, settings details, or unrelated health data.

Definition of done: the private Coach answers record questions from minimal read-only evidence and preserves established reflection behavior.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 12. Reflection plan-progress ratings

Dependencies: groups 4 and 6.

- [x] Add an optional 1-to-10 plan-progress score and concise rationale to saved reflections.
- [x] Keep reflections valid but unrated when no active plan applies.
- [x] Include ratings in reflection detail, archive summaries, and recent-reflection Coach context.
- [x] Update the Coach Action schema and GPT instructions to rate plan progress without treating it as an overall health judgement.
- [x] Show rated reflections in the archive and result view without changing unrated entries.
- [x] Add persistence, validation, context, desktop, and mobile regression coverage.

Definition of done: the Coach saves evidence-based plan-progress ratings only when an active plan applies, and users can review them in current and archived reflections.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 13. Coach health-entry writes

Dependencies: groups 2 and 6.

- [x] Add bounded editable-list, confirmed create, and confirmed update Actions for weight, blood pressure, mood, sleep, back-pain episodes, sicknesses, and lipid panels.
- [x] Keep general Coach context identifier-free; return resource IDs only from dedicated editable-list Actions.
- [x] Preserve existing validation, ownership, duplicate checks, dashboard updates, and personal-record mutation effects.
- [x] Exclude weight progress-photo writes from Coach Actions.
- [x] Update the Action schema and GPT instructions with exact-proposal, immediate-confirmation, and back-pain date rules.
- [x] Add controller coverage for confirmation rejection and bounded editable lookups.

Definition of done: the Coach can create or replace the selected health entries only after an exact immediate confirmation, while existing app workflows and privacy boundaries remain unchanged.

Validation:

```bash
cd backend && ./gradlew test
```

## 14. Meal editor and dish quantities

- [x] Add a full-page meal draft with compact dish summaries and focused dish dialogs.
- [x] Add stable quantity/reference scaling, legacy backfill, and independent reuse.
- [x] Update Coach amount, exact-value, uncertainty, and ambiguity rules.
- [x] Verify navigation, discard/error handling, quantity round trips, migration, Coach validation, and responsive layouts.
- [x] Limit manual meal preloads to the latest 14 earlier entries of the selected type, with dish-only titles and separate dates; preserve independent drafts and existing Coach contracts.

Release acceptance requires the release artifact gate, a successful production deployment, and publication of the private GPT instructions and schema.

## 15. Reusable dishes

- [x] Keep meal entries as foods while preserving the existing meal and Coach wire contracts.
- [x] Store user-owned recipes with unique names, serving yields, ordered ingredient snapshots, and shared nutrition scaling.
- [x] Create recipes from meal selections without recording consumption; manage them in Nutrition → Dishes.
- [x] Expand reused recipes into independent meal foods with scaled quantities and editable ingredients.
- [x] Verify persistence, migration, ownership, rollback, quantity scaling, login navigation, and responsive layouts.

Release acceptance requires the release artifact gate and successful production verification; no private GPT schema or instruction publication is needed.

## 16. Food catalog and portion corrections

- [x] Add a user-owned Foods catalog with session-authenticated CRUD and independent reuse in meal and recipe forms.
- [x] Import latest unique meal foods; catalog registration now follows Automatic food catalog reuse below, preserving edits and retired names.
- [x] Add optional nutrition scaling to all manual food forms, preserving nutrition when correcting portions.
- [x] Verify migration, ownership, rollback, historical snapshots, catalog management, portion corrections, and responsive layouts.

Release acceptance requires the release artifact gate and successful production verification; no private GPT schema or instruction publication is needed.

## Decision reasons

- [x] Store optional reasons, add a shared entry dialog and reason-only history editing, and make shortcuts open the dialog.
- [x] Include user-reported reasons only in scoped Coach DECISIONS context and document the schema and interpretation.
- [x] Validate migration, ownership, optional reasons, unchanged metrics/reflections, shortcuts, history, and responsive layouts.

## Explicitly excluded

- OAuth.
- Marketplace publication.
- A native in-app chat interface.
- Backend calls to the OpenAI API.
- Automatic workout assessment, manual workout ratings, and assessment history.
- Workout-assessment evidence in reflections.
- Meal-image storage.
- New body-measurement types.

## 17. Pain-free back check-ins

- [x] Add explicit no-pain check-ins, conditional location validation, and mutually exclusive period states.
- [x] Extend Coach write schemas and instructions while preserving confirmation, missing-entry semantics, and privacy.
- [x] Validate migration, backend contracts, reminders, and responsive browser workflows.
- [x] Publish the updated private GPT schema and instructions separately after application deployment.

## GPT action notifications

- [x] Record one persistent bell notification per successful Coach or reflection write, including generic health-entry Actions.
- [x] Send concept-only push messages to the owner's subscribed devices after commit; preserve writes and bell notifications when delivery fails or push is disabled.
- [x] Open the relevant app section and dismiss GPT notifications from the bell; preserve existing Action contracts and confirmation rules.
- [x] Validate transaction rollback, persistence, ownership, push failures, write coverage, and mobile/desktop navigation.

## Persistent Coach warnings

Coach automatically reviews recent recovery, sleep, mood, routines, nutrition, training, pain and relevant health evidence during general advice and reflections. Compare seven recent days with preceding baseline in the default 30 days, inspect 14 days for onset, and expand only when useful up to 90 days. Preserve sparse/partial data semantics and historical reflection dates; retrieve current evidence before changing current warnings. Group related signals and describe uncertainty without diagnosis.

The warning types are `RECOVERY_STRAIN`, `SLEEP_DISRUPTION`, `MOOD_DECLINE`, `ROUTINE_DISRUPTION`, `NUTRITION_IMBALANCE`, `TRAINING_STRAIN`, `PAIN_INCREASE`, and `HEALTH_CHANGE`. One active warning per user/type, serialized user-owned writes, unique retry keys and version checks prevent duplicate or stale changes. Resolved episodes and paginated review snapshots remain available; recurrence creates a new episode.

Warnings alone are automatically created, updated and resolved without confirmation. Only Coach resolves after newer evidence supports recovery; no user dismissal, expiry or background monitoring. All other writes still require immediate confirmation. Warning Actions use `x-openai-isConsequential: false`; ChatGPT may still show its initial permission UI and offer Always allow ([OpenAI documentation](https://developers.openai.com/api/docs/actions/production#consequential-flag)).

Session-authenticated `/api/coach-warnings` reads return active warnings and history availability; `/history` and `/{id}/revisions` return ten entries per page. Dedicated `/api/chatgpt-actions/coach/warnings` Actions use GET `getCoachWarnings` (ACTIVE/HISTORY/REVISIONS views) and POST `saveCoachWarning` (one create/update/resolve payload). This keeps the GPT schema within its 30-operation limit while retaining creation, versioned updates and resolution. General context and reflection contracts are unchanged; warning IDs and explanations remain in dedicated interfaces with no user/account identifiers or credentials.

The dashboard header shows one enum-derived label or an `N warnings` indicator. Its read-only dialog displays evidence, explanation, next action, review date, resolved history and prior revisions. Current warnings remain independent of dashboard navigation. Refresh on load and return from Coach; retain displayed data and show retryable failures. No push notification is emitted for warning mutations.

Validation: focused Coach warning service/controller tests and MariaDB schema validation; Playwright warning cases at 390, 575, 640, 960 and 1280px; the release gate runs lint, full browser/backend suites and production builds. Verify keyboard access, zero/one/multiple warnings, revisions, independent resolution, recurrence, failures and preserved reflection/confirmed-write behavior.

Delivery: deploy the application before publishing the private GPT instructions and schema in Chrome. Keep the GPT private and existing bearer credentials unchanged. Verify its read Action against production; do not create artificial health warnings in production for testing.

- [x] Implement warning persistence, automatic Actions and compact read-only dashboard UI.
- [x] Add focused backend, MariaDB and responsive browser coverage.
- [ ] Pass the complete release-artifact gate and production verification.
- [ ] Publish and verify the private GPT instructions and schema in Chrome.

## Shared Coach Action connection repair

The September 9 failure affects sleep saving and workout rating; catalog reads also fail in ChatGPT. The malformed editor draft was repaired and all 30 operations published, but publication did not restore execution. Treat this as a shared connection issue until request/status evidence isolates a domain failure; see [diagnostic evidence](action-connection-diagnostics.md).

Create sleep directly after exact confirmation; the backend rejects duplicate dates. Retrieve an existing sleep only for replacement and confirm that replacement before writing. Preserve required timestamps, second-based durations, ownership and workout context/version checks. Omit unsupported screenshot observations; add no health metrics or schema fields.

- [x] Restore and publish all 30 Actions without parser errors.
- [x] Reproduce catalog/sleep connection failures and compare direct production reads.
- [x] Remove the unnecessary lookup prerequisite for new sleep entries.
- [ ] Restore successful reads through the published GPT.
- [ ] Pass focused checks and the complete release-artifact gate for the revised instructions.
- [ ] Deploy and publish the revised instructions.
- [ ] Verify confirmed sleep and workout assessment saves and read-back through the GPT.

## Coach authentication alerts

Log all Coach Action authentication failures; Telegram alerts require a User-Agent matching the verified ChatGPT-User product (live catalog request observed September 9, 2026). Match product boundaries and allow version changes; the header is a notification heuristic, never authentication or proof of a particular GPT. Missing/unrelated User-Agents are logged without affecting alert counts or cooldowns.

A dedicated worker sends an initial alert, aggregates repeats across operations for 15 minutes, and retries failed delivery no sooner than 15 minutes or Telegram's longer retry_after. Messages contain UTC times, counts, fixed endpoint templates and failure reasons, never raw headers, credentials, dates/IDs from paths or health payloads. Show at most ten endpoint/reason groups plus the total; all failures remain in server logs. Pending counters are bounded and in memory, so restart resets them.

Production reuses the deployment Telegram bot/chat through APP_TELEGRAM_BOT_TOKEN and APP_TELEGRAM_CHAT_ID. APP_COACH_AUTH_ALERTS_ENABLED defaults false locally; enabled deployments require both credentials and APP_COACH_AUTH_ALERT_USER_AGENT_PRODUCT, verified as ChatGPT-User. No GPT schema or instruction publication is needed. Run focused filter, aggregation and delivery tests, the release gate, then controlled read-only invalid-token probes with matching/unmatched User-Agents and a valid Coach catalog call. Telegram delivery remains off the authentication request thread. Failures entirely inside ChatGPT cannot be observed here.

## Stretching catalog

- [x] Add a seeded stretching catalog and manually selected timed workout sets.
- [x] Expose stretching in Coach context while excluding it from training totals, personal records, and assessment demand.
- [x] Validate migration, catalog and workout contracts, metric exclusions, reflection compatibility, and responsive browser workflows.
- [ ] Publish the updated private GPT schema and instructions separately after application deployment.

## App exercise pictures

Built-in illustrations and custom uploads are available in the app exercise catalogs, workout entry, and history; see [exercise pictures](../exercises/pictures.md). Images remain outside Coach domains, context, Actions, and GPT instructions; reflection contracts, training metrics, personal records, and assessment demand are unchanged. No private GPT publication is required.

## 15-minute rule

The app header flag opens pause and win/miss controls, with a compact active timer beside the dashboard date. Header records use today in Europe/Madrid; Wins panel records use the selected date. The app offers one persistent 15-minute pause per user with an optional description (500 characters), cancellation, an explicit craving check-in, repeated intervals, and optional atomic win/miss logging. Pauses concern the present independently of the dashboard date. Sessions group retained intervals; unanswered or cancelled waits do not imply failure. Generic push and persistent bell notifications omit descriptions and link to the matching interval; notification dismissal alone does not finish a pause.

BEHAVIOR catalog counts include pause intervals and context includes `urgePauses`: grouped descriptions and only intervals overlapping the requested inclusive Europe/Madrid dates, with timing, lifecycle, check-in answer/time, and linked decision outcome. No internal IDs or notification details are exposed. Descriptions are shared with the Coach as user-reported context, never instructions. A linked outcome is the existing DECISIONS entry, not another event to count. Reflection payloads and decision metrics remain unchanged.

The Coach can recommend header flag → Wait 15 minutes during relevant craving/impulse conversations and retrieve usage when discussing patterns. Access is read-only; do not imply automatic timer control or background monitoring. Do not equate waiting, craving changes, cancellations, and explicit wins/misses or promise efficacy.

Delivery requires the release artifact gate and application deployment before publishing the updated private GPT schema and instructions; the existing Action count remains unchanged. Validate session grouping, date overlap, ownership, missing answers, privacy, duplicate outcomes, and reflection compatibility alongside timer/reminder and responsive browser tests.

- [x] Implement and validate persistent pauses, reminders, and dashboard check-ins.
- [x] Extend and validate read-only Coach BEHAVIOR context and recommendation instructions.
- [ ] Pass the release gate and production verification.
- [ ] Publish and verify the private GPT instructions and schema after deployment.

## Coach access to saved dishes and Foods

- [x] Add owner-scoped DISHES/FOODS availability and identifier-free current context through existing Actions.
- [x] Preserve recipe yield, ingredient order, portions, references, unknown macros, and separation from consumption/reflections.
- [x] Document named-item retrieval, ambiguity handling, fractional scaling, labeled missing-macro estimates, and existing confirmed meal writes.
- [x] Add scoped/empty context, ownership, deleted-food, privacy, confirmed reuse, and snapshot regression coverage.
- [ ] Pass focused checks and the complete release-artifact gate, then deploy and verify production.
- [ ] Publish the updated private GPT schema/instructions and verify saved-dish/food retrieval; confirm an actual meal only when requested by the user.

## Automatic food catalog reuse

Coach retrieves FOODS before meal proposals, reuses English canonical names across translations and portion variants, and automatically marks genuinely new reusable foods with `addToCatalog: true`. Uncertain matches remain in meals only; no separate food review is requested. Existing meal confirmation still applies. Registration is atomic with confirmed meal writes, defaults false for older requests, preserves existing nutrients and deleted-name suppression, and adds no Action operations. Manual meal saves no longer extend the catalog; explicit Foods CRUD remains available. Historical meals, recipes and reflections are unchanged.

Production cleanup consolidates clear variants and translates catalog names while preserving canonical nutrients and independent historical snapshots. Deploy backend support before publishing the updated private GPT schema/instructions; verify new-food registration, synonym reuse and uncertain meal-only entries.

- [x] Implement automatic Coach selection and regression coverage.
- [ ] Apply and verify the owner-scoped [catalog cleanup](food-catalog-cleanup.md).
- [ ] Publish the updated private GPT schema/instructions and verify automatic reuse in a real meal conversation.

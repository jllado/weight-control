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
- [x] Support one Breakfast, Lunch, and Dinner plus multiple numbered Snacks per date.
- [ ] Add `fasting_periods` with start, end, and notes.
- [ ] Add optional meal time, notes, and source fields for Coach-created entries.
- [x] Calculate daily meal calorie totals in the calorie compatibility service.
- [x] Preserve `GET /api/calories` reads as a compatibility facade for existing dashboard code.
- [x] Refactor reflection nutrition reads to use aggregated daily totals.
- [x] Add normal authenticated meal CRUD endpoints.
- [x] Preserve `/calories` and add fixed meal editing with per-meal macros.
- [ ] Add fasting-period management and macro-completeness summaries.
- [ ] Add `NUTRITION` catalog coverage for nutrition days, meals, macros, and fasting periods.
- [x] Add migration, ownership, aggregation, compatibility, and zero-calorie tests.
- [ ] Add fasting, Coach write-confirmation, and macro-completeness tests.
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

- [ ] Create `docs/coach/coach-gpt.md` with the complete coach configuration and instructions.
- [ ] Complete `docs/coach/coach-action.openapi.yaml` with catalog, context, constraints, active-plan, nutrition, and existing reflection operations.
- [ ] Give every operation a distinct, intent-revealing operation ID and concise description so the GPT can select it correctly.
- [ ] Configure adaptive opening behavior for generic starters and immediate handling of specific requests.
- [ ] Configure progressive retrieval: catalog first, then relevant domains, default 30 days, maximum 90 days.
- [ ] Require immediate explicit confirmation before every write Action.
- [ ] Add wellness, missing-data, image uncertainty, clinician-guidance, and no-diagnosis rules.
- [ ] Rename `VUE_APP_CHATGPT_REFLECTION_URL` to `VUE_APP_CHATGPT_COACH_URL` in frontend, Docker, `.env.example`, and Ansible deployment variables/templates.
- [ ] Add a global authenticated `Open Coach` action that opens the GPT in a new tab.
- [ ] Preserve the Reflections route, archive, and date-specific create/update buttons.
- [ ] Replace the long advice prompt with a short natural request and remove embedded reflection data from copied prompts.
- [ ] Import the schema into the existing private GPT and keep bearer API-key authentication.
- [ ] Verify reflection, advice, training-volume, constraint, active-plan, nutrition, and follow-up conversations manually.
- [ ] Mark the old reflection GPT documentation and schema as superseded only after the coach schema works end to end.
- [ ] Run backend tests, frontend lint/build, and relevant end-to-end tests.

Definition of done: the private Weight Control Coach handles reflections and flexible data-backed conversations without copied health summaries.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 7. Stored progress-photo retrieval

Dependencies: group 6.

- [ ] Add `CHATGPT_ACTION_PUBLIC_BASE_URL` and `CHATGPT_FILE_SIGNING_SECRET` to application, Docker, example environment, and Ansible deployment configuration.
- [ ] Preserve the existing session-authenticated photo endpoints for the frontend.
- [ ] Add `listProgressPhotos` returning dates, photo-set IDs, body values, and available sides without storage paths.
- [ ] Add `getProgressPhotoFiles` accepting selected sides and returning no more than three signed URLs through `openaiFileResponse`.
- [ ] Implement five-minute HMAC tokens containing user, photo set, side, expiry, and progress-photo purpose.
- [ ] Add the public signed-download endpoint under `/api/chatgpt-files/progress-photos/{token}` with correct MIME types.
- [ ] Revalidate user ownership and selected-side existence when serving each signed file.
- [ ] Ensure logs do not contain tokens, image URLs, authorization headers, or health payloads.
- [ ] Add GPT instructions to list metadata first and retrieve only photo sets and sides needed by the user’s request.
- [ ] Add a disclosure that retrieved progress photos are transmitted to ChatGPT for analysis.
- [ ] Test valid downloads, expiry, signature tampering, wrong purpose, wrong user, missing sides, MIME types, and Action authentication.
- [ ] Test front/side photo comparisons in the configured private GPT.

Definition of done: the coach can analyze selected stored progress photos without manual attachment or permanent public URLs.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 8. Confirmed meal-image estimates

Dependencies: groups 5 and 6.

- [ ] Add `createMeal`, `updateMeal`, and `deleteMeal` Actions to the coach schema.
- [ ] Add confirmed fasting-period write Actions.
- [ ] Require the meal date, fixed meal type, calories, optional macros, notes, `GPT_IMAGE_ESTIMATE` source, and `confirmed: true` for image-derived creation.
- [ ] Instruct the GPT to show estimated ranges, uncertainty, and exact proposed stored values before requesting confirmation.
- [ ] Prevent the GPT from calling a meal write Action when the user has not confirmed the exact values in the immediately preceding message.
- [ ] Verify that no image bytes, ChatGPT file IDs, or image URLs are accepted or persisted by Weight Control.
- [ ] Add tests for validation, confirmation, fixed-meal conflicts, ownership, source, and structured response data.
- [ ] Manually test attaching a meal image in ChatGPT, correcting the estimate, confirming it, and reading the updated daily totals.

Definition of done: a ChatGPT-attached meal image can produce a reviewed, confirmed structured meal while Weight Control stores no image.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
```

## 9. Final acceptance and cutover

Dependencies: groups 1–8.

- [ ] Run the complete backend, frontend, schema, and end-to-end test suites.
- [ ] Verify that existing reflection records, calories, photos, workouts, and user settings remain accessible after migrations.
- [ ] Exercise every Action with missing, incorrect, and valid bearer authentication.
- [ ] Audit every Action response for unnecessary personal data, internal identifiers, paths, secrets, and unrelated domains.
- [ ] Verify general advice uses today’s partial records while reflection creation rejects incomplete or ineligible dates.
- [ ] Reproduce the shared conversation’s key questions using stored profile, workouts, nutrition, constraints, plan, reflections, and progress photos instead of pasted summaries or manually attached progress photos.
- [ ] Verify that clinician guidance is surfaced before affected exercise recommendations.
- [ ] Verify that every persisted change follows an immediately confirmed proposal.
- [ ] Verify signed progress-photo URLs only work over HTTPS in production and expire after five minutes.
- [ ] Update the private GPT’s name, description, conversation starters, instructions, and Action schema.
- [ ] Remove superseded reflection-only GPT setup files after the coach configuration is safely recorded.
- [ ] Document production configuration and the privacy implications of sending health records and selected photos to ChatGPT.

Definition of done: the private coach supports the complete planned conversation flow, existing features remain functional, and the privacy and safety checks pass.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## Explicitly excluded

- OAuth.
- Marketplace publication.
- A native in-app chat interface.
- Backend calls to the OpenAI API.
- Meal-image storage.
- New body-measurement types.

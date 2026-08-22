# Project Guide

Use this guide to locate the right implementation area and validation command before searching the whole repository.

## Sources of truth

| Concern | Current value | Authoritative source |
| --- | --- | --- |
| Backend language | Java 21 | `backend/build.gradle` |
| Backend build | Gradle wrapper 9.0 | `backend/gradle/wrapper/gradle-wrapper.properties` |
| Backend framework | Spring Boot 3.5.0 | `backend/build.gradle` |
| Frontend framework | Vue 3 with Vue CLI | `package.json` |
| Frontend package manager | Yarn v1 | `yarn.lock` |
| UI library | PrimeVue 3 | `package.json` and `src/main.js` |
| Browser tests | Playwright | `package.json` and `playwright.config.js` |
| Database | MariaDB 11.8 with Flyway migrations | `docker-compose.yml` and `backend/src/main/resources/db/migration/` |

Do not use a system Gradle installation; the wrapper controls the Gradle version and the configured Foojay resolver can obtain the Java compilation toolchain.

No local Node version is pinned in the repository; use a current Node LTS version compatible with Yarn v1 and the declared dependencies.

## Standard commands

Run frontend commands from the repository root:

```bash
yarn install
yarn serve
yarn lint
yarn build
yarn test:e2e
yarn playwright test --grep "test name"
```

Run backend commands through the wrapper from `backend/`:

```bash
cd backend
./gradlew bootRun
./gradlew test
./gradlew build
```

Run `docker compose up --build` only for full-stack or container-specific validation. Deployment, provisioning, database dump, and database restore operations remain opt-in under the safety rules in `AGENTS.md`.

## Runtime architecture

```text
Browser/PWA -> Caddy -> Vue frontend
                    -> Spring Boot /api -> MariaDB
Push service worker -> notification action URL -> Vue route/query action -> backend API
Private Coach GPT -> bearer-authenticated /api/chatgpt-actions/** -> scoped application services
```

- `src/main.js` registers global Vue and PrimeVue dependencies, `src/router.js` owns routes, and `src/App.vue` owns application-level navigation and dialogs.
- Vue components call feature helpers in `src/services/`; those helpers use `src/services/api.js`, which prefixes `/api` and includes the session cookie.
- Backend requests follow controller -> DTO/service -> repository/domain; controllers resolve the authenticated user and services own business rules.
- Flyway migrations are append-only production schema changes; derive the next version from the existing migration directory.
- `Home.vue` coordinates dashboard loading and route-query actions for routine, check-in, and measurement notifications.

## Task routing

| Product area | Frontend starting points | Backend starting points | Tests and detailed docs |
| --- | --- | --- | --- |
| Application shell and authentication | `src/App.vue`, `src/router.js`, `src/state.js`, `src/services/api.js` | `AuthController`, `AuthService`, `security/` | Authentication cases in `tests/e2e/login-and-push.spec.js` and backend auth/security tests |
| Dashboard and daily status | `Home.vue`, `DashboardService.js`, `StatusService.js` | `DashboardController`, `DashboardService`, `DailyStatusSnapshotService` | Dashboard service tests and Home scenarios in the Playwright suite |
| Notifications and reminders | `Home.vue`, `PushNotificationPrompt.vue`, notification/routine services | `PushController`, `InAppNotificationController`, push, notification, and routine services | Push and notification backend tests plus reminder cases in the Playwright suite |
| Body, vitals, and progress photos | Weight, blood-pressure, lipid, and photo components and services | Matching controllers/services, `PhotoStorageService`, personal-record mutation services | Matching backend service/controller tests and history/form Playwright cases |
| Mood, sleep, back pain, and sickness | Matching history/form components and services | Matching controllers/services and DTOs | Matching backend tests and Home/history Playwright cases |
| Nutrition and fasting | `CalorieHistory.vue`, meal/fasting forms, nutrition services | Calorie, nutrition, meal, and fasting controllers/services | Nutrition migration/service tests and calorie-area Playwright cases |
| Habits and routines | `HabitList.vue`, `RoutineList.vue`, `Home.vue` | Habit and routine controllers/services | Routine service tests and routine/reminder Playwright cases |
| Workouts and assessments | `WorkoutDiary.vue`, workout models/services | Workout and exercise controllers/services, `WorkoutAssessmentService` | Workout backend tests, Playwright workout cases, and Coach docs |
| Personal records | `PersonalRecords.vue`, `PersonalRecordSummary.vue`, `PersonalRecordService.js` | `PersonalRecordController`, calculator, mutation, and query services | `docs/personal-records/` and personal-record backend/Playwright tests |
| Coach and reflections | `Reflection.vue`, Coach/reflection services, Coach settings components | ChatGPT Action controllers, context, reflection, constraint, and plan services | `docs/coach/plan.md`, `docs/coach/todo.md`, schemas, and Coach backend tests |
| Configuration and deployment | `.env.example`, `docker-compose.yml`, `infra/ansible/` | Spring configuration and deployment templates | `README.md`; operations run only with explicit authorization |

Component names in the table are starting points. Follow imports and service calls from the relevant component instead of enumerating every supporting file here.

## Cross-cutting flows

### Frontend API changes

1. Start from the route or screen component.
2. Follow its helper in `src/services/` and reuse `src/services/api.js`.
3. Match request and response conversions already used by the feature model or service.
4. Locate the corresponding backend controller and DTO before changing the wire contract.

### Backend persistence changes

1. Add a Flyway migration when the schema changes.
2. Update the domain and repository only for persistence concerns.
3. Keep business rules in the focused service and HTTP mapping in existing DTO files.
4. Add service tests and controller/DTO tests when the external contract changes.

### Dashboard notification actions

1. Backend notification DTOs generate action URLs with route query parameters.
2. `Home.vue` preloads the data required to validate the action, opens the modal above dashboard loading, and then loads remaining dashboard data.
3. Saving or dismissing clears the relevant query parameters and follows the notification-specific dismissal rules.

### Coach-aware changes

Always review `docs/coach/plan.md` and `docs/coach/todo.md`. Update Coach domains, context, Actions, GPT instructions, privacy rules, tests, or sequencing only when the feature affects them.

## Validation matrix

| Change type | Required starting checks |
| --- | --- |
| Documentation only | `git diff --check`, verify paths, commands, and Markdown links |
| Frontend source | `yarn lint`; add `yarn build` for build/configuration risk |
| Backend source | `cd backend && ./gradlew test` |
| Flyway or persistence | Backend tests plus the relevant migration or MariaDB validation used by the feature |
| Browser workflow | `yarn test:e2e` or focused `yarn playwright test --grep "..."` after a current test build |
| Full-stack/container behavior | `docker compose up --build` only when needed |
| Production release | Use the repository release skill only after explicit invocation |

Feature plans and TODO files can require additional checks; their validation sections take precedence for that feature.

## Targeted discovery

```bash
rg -n "path:|name:" src/router.js
rg -n "from ['\"]@?/?.*services|Service" src/components src/services
rg -n "@(Get|Post|Put|Delete)Mapping|class .*Controller" backend/src/main/java/com/jllado/weightcontrol/api
rg -n "record .*Request|record .*Response" backend/src/main/java/com/jllado/weightcontrol/api/dto
rg --files backend/src/main/resources/db/migration | sort -V
rg -n "test\(|describe\(" tests/e2e
rg --files backend/src/test | rg "Mood|Routine|Notification"
```

Prefer a feature name, route query key, endpoint fragment, or visible UI label as the first search term. Update this guide when a change invalidates a command, source-of-truth location, cross-cutting flow, or task boundary.

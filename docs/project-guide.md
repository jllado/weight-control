# Project Guide

Use this guide to locate implementation areas and validation before broad searches.

## Read path

For a narrow task, read the source-of-truth row and standard command, then the matching task-routing row, cross-cutting flow, and validation entry. Use the targeted searches below before scanning a complete frontend or backend tree.

## Sources of truth

| Concern | Current value | Authoritative source |
| --- | --- | --- |
| Backend language and framework | Java 21; Spring Boot 3.5.0 | `backend/build.gradle` |
| Backend build | Gradle wrapper 9.0 | `backend/gradle/wrapper/gradle-wrapper.properties` |
| Frontend | Vue 3 with Vue CLI and PrimeVue 3 | `package.json`, `src/main.js` |
| Frontend package manager | Yarn v1 | `yarn.lock` |
| Browser tests | Playwright | `package.json`, `playwright.config.js` |
| Database | MariaDB 11.8 with Flyway migrations | `docker-compose.yml`, `backend/src/main/resources/db/migration/` |

Use the checked-in Gradle wrapper, not system Gradle. No Node version is pinned; use a current Node LTS compatible with Yarn v1 and the declared dependencies.

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

Run backend commands from `backend/`:

```bash
./gradlew bootRun
./gradlew test
./gradlew build
```

Run `docker compose up --build` only for full-stack or container-specific validation. Follow `AGENTS.md` for deployment and operational authorization.

## Runtime architecture

```text
Browser/PWA -> Caddy -> Vue frontend
                    -> Spring Boot /api -> MariaDB
Push service worker -> notification action URL -> Vue route/query action -> backend API
Private Coach GPT -> bearer-authenticated /api/chatgpt-actions/** -> scoped application services
```

- `src/main.js` registers global Vue and PrimeVue dependencies, `src/router.js` owns routes, and `src/App.vue` owns application navigation and dialogs.
- Components call feature helpers in `src/services/`; helpers use `src/services/api.js`, which prefixes `/api` and includes the session cookie.
- Backend requests follow controller -> DTO/service -> repository/domain; controllers resolve the authenticated user and services own business rules.
- `Home.vue` coordinates dashboard loading and routine, check-in, and measurement route-query actions.

## Task routing

| Product area | Frontend starting points | Backend starting points | Tests and detailed docs |
| --- | --- | --- | --- |
| Application shell and authentication | `src/App.vue`, `src/router.js`, `src/state.js`, `src/services/api.js` | `AuthController`, `AuthService`, `security/` | `tests/e2e/login-and-push.spec.js`, backend auth/security tests |
| Dashboard and daily status | `Home.vue`, `DashboardService.js`, `StatusService.js` | `DashboardController`, `DashboardService`, `DailyStatusSnapshotService` | Dashboard service tests, Home Playwright scenarios |
| Notifications and reminders | `Home.vue`, `PushNotificationPrompt.vue`, notification/routine services | `PushController`, `InAppNotificationController`, push, notification, routine services | Push, notification, routine tests |
| Body, vitals, and progress photos | Weight, blood-pressure, lipid, photo components and services | Matching controllers/services, `PhotoStorageService`, personal-record mutation services | Feature service/controller and history/form tests |
| Mood, sleep, back pain, and sickness | Matching history/form components and services | Matching controllers/services and DTOs | Feature backend and Home/history tests |
| Nutrition and fasting | `CalorieHistory.vue`, meal/fasting forms, nutrition services | Calorie, nutrition, meal, fasting controllers/services | Nutrition migration/service and calorie-area tests |
| Habits and routines | `HabitList.vue`, `RoutineList.vue`, `Home.vue` | Habit and routine controllers/services | Routine and reminder tests |
| Workouts and assessments | `WorkoutDiary.vue`, workout models/services | Workout and exercise controllers/services, `WorkoutAssessmentService` | Workout tests and Coach docs |
| Personal records | `PersonalRecords.vue`, `PersonalRecordSummary.vue`, `PersonalRecordService.js` | `PersonalRecordController`, calculator, mutation, query services | `docs/personal-records/`, backend/Playwright tests |
| Coach and reflections | `Reflection.vue`, Coach/reflection services and settings | ChatGPT Action, context, reflection, constraint, plan services | `docs/coach/`, Coach backend tests |
| Configuration and deployment | `.env.example`, `docker-compose.yml`, `infra/ansible/` | Spring configuration and deployment templates | `README.md` |

Follow imports and service calls from these starting points rather than enumerating complete trees.

## Cross-cutting flows

### Frontend API changes

1. Start from the route or screen component.
2. Follow its helper in `src/services/` and reuse `src/services/api.js`.
3. Match existing request and response conversions.
4. Locate the matching backend controller and DTO before changing the wire contract.

### Backend persistence changes

1. Add an append-only Flyway migration and update only the necessary domain and repository persistence concerns.
2. Keep business rules in the service and HTTP mapping in existing DTO files.
3. Add service tests and controller/DTO tests when the external contract changes.

### Dashboard notification actions

1. Backend notification DTOs generate action URLs with route-query parameters.
2. `Home.vue` preloads data required to validate the action, opens the modal above dashboard loading, then loads remaining dashboard data.
3. Saving or dismissing clears the relevant parameters and follows the notification-specific dismissal rules.

## Validation matrix

| Change type | Required starting checks |
| --- | --- |
| Documentation only | `git diff --check`; verify paths, commands, and Markdown links |
| Frontend source | `yarn lint`; add `yarn build` for build/configuration risk |
| Backend source | `cd backend && ./gradlew test` |
| Flyway or persistence | Backend tests plus relevant migration or MariaDB validation |
| Browser workflow | `yarn test:e2e` or focused `yarn playwright test --grep "..."` after a current test build |
| Full-stack/container behavior | `docker compose up --build` only when needed |
| Production release | Explicitly invoke the repository release skill |

Feature plans and TODO validation requirements take precedence.

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

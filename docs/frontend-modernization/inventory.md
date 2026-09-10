# Frontend inventory — 2026-09-10

Source: `993a4e3` (application matches master `4b95513`); see [baseline](baseline.md) for provenance and limits. Machine-readable [source inventory](evidence/source-inventory.json) retains every import, consumer, transitive route, literal icon, utility, directive, and stylesheet; [dependency inventory](evidence/dependencies.json) retains installed versions and license metadata.

## Routes and behavioral coverage

Named scenarios are searchable title fragments in [login-and-push.spec.js](../../tests/e2e/login-and-push.spec.js), with medication management scenarios in [medications.spec.js](../../tests/e2e/medications.spec.js). A route relationship establishes workflow relevance, not exhaustive interaction coverage; explicit gaps remain migration acceptance work.

| Route | Entry component | Existing coverage / gap |
| --- | --- | --- |
| `/` | `Home.vue` | Home preloads week-summary data; rendered dashboard charts fit; dashboard entry modals; pause actions match the Wins panel |
| `/agenda` | `Agenda.vue` | agenda shows statuses and a current-time divider |
| `/back` | `BackPainEpisodeHistory.vue` | back pain history saves an episode; pain-free back check-in |
| `/calories` | `CalorieHistory.vue` | nutrition history summarizes macros; food catalog supports portion correction; reusable dishes |
| `/cholesterol` | `LipidPanelHistory.vue` | cholesterol history shows changes and supports CRUD |
| `/dishes/:id/edit` | `DishRecipeEditor.vue` | recipe editor retains its route through login; reusable dishes |
| `/habits` | `HabitList.vue` | habit check-ins expose legacy context (dashboard); dedicated habit CRUD coverage gap |
| `/login` | `Login.vue` | credential-only Google response; authentication failure; pending reminder/shortcut login cases |
| `/meals/:id/edit` | `MealEditor.vue` | meal editor keeps its destination through login; meal dish quantities preserve references |
| `/meals/new` | `MealEditor.vue` | meal preloads show the latest 14 matching earlier entries; meal duration supports validation |
| `/medications` | `MedicationList.vue` | medications show exact recurring times and recent dose states; a medication can be created with an exact reminder and logged now; medication management fits mobile and desktop widths; reminder take/snooze cases; edit/delete persistence gap |
| `/moods` | `MoodHistory.vue` | history forms keep their date controls; dashboard mood infers an editable period |
| `/photos` | `PhotoHistory.vue` | modernization baseline captures representative workflows (rendered synthetic photo); selection/swipe gap |
| `/plan` | `GoalPlan.vue` | goal and plan page explains concepts, preserves the contract |
| `/pressures` | `BloodPressureHistory.vue` | history forms keep their date controls; blood pressure notification opens the fixed Saturday form |
| `/records` | `PersonalRecords.vue` | records page shows current records and paginated progression history |
| `/reflections` | `Reflection.vue` | reflection mobile panel and date navigation; reflection advice copies only a short natural Coach request |
| `/routines` | `RoutineList.vue` | routines can have their reminders cleared; routine reminder completion/snooze (dashboard) |
| `/settings` | `Settings.vue` | daily reminder settings; record settings save overrides; push notification settings scenarios |
| `/sicknesses` | `SicknessHistory.vue` | sickness form uses readable dropdowns; history forms keep their date controls |
| `/sleep` | `SleepHistory.vue` | total bedtime includes awake time; history forms keep their date controls |
| `/weights` | `WeightHistory.vue` | modernization baseline captures representative workflows; weight notification opens an actual-date form |
| `/wins` | `DecisionOutcomeHistory.vue` | decision history and reason dialog; decision reason dialog cancels shortcuts |
| `/workouts` | `WorkoutDiary.vue` | workout exercises can be reordered; stretching workouts save timed sets; exercise pictures stage uploads; Coach assessments |

The app shell is present on authenticated routes: `App.vue` owns Menubar, account Menu, Toast and Coach navigation; `UrgePause.vue` owns the flag dialog/timer, `DecisionOutcomeActions.vue` is shared with Home, and `NotificationBell.vue` owns the notification OverlayPanel. Tests cover grouped navigation, Coach authentication, pending notification swipe/button dismissal/navigation and centered positioning after viewport changes, pause persistence, independent decisions, cross-device reconciliation, login restoration, and style equality at five widths. Shell ownership is listed separately from routed ownership in the JSON.

## PrimeVue surface

All entries use installed PrimeVue **3.38.1 (MIT)**. Each consumer below maps through the route table and full JSON to relevant tests; global services also apply to the shell. Password and PickList are registered but have no production template consumers. There are no custom PrimeVue directive registrations; built-in Vue directives are listed in the JSON.

| Import | Consumers (component names) |
| --- | --- |
| `primevue/autocomplete` | `DishRecipePicker`, `FoodPicker` |
| `primevue/button` | `App`, `UrgePause`, `Reflection`, `FastingPeriodForm`, `MoodHistory`, `Agenda`, `SleepForm`, `CreateBackPainEpisode`, `WorkoutForm`, `CreateMeal`, `CoachWarnings`, `CreateSickness`, `Settings`, `DishRecipeForm`, `BloodPressureHistory`, `DecisionOutcomeForm`, `MealForm`, `CreateLipidPanel`, `WeightForm`, `DecisionOutcomeActions`, `NotificationBell`, `PushNotificationPrompt`, `SicknessForm`, `BloodPressureForm`, `DishRecipeList`, `PersonalRecords`, `BackPainEpisodeForm`, `FoodList`, `LipidPanelHistory`, `SicknessHistory`, `CreateSleep`, `PushNotificationSettings`, `RoutineList`, `MedicationList`, `MoodForm`, `WeightHistory`, `MealDurationPicker`, `SleepHistory`, `CreateMood`, `WorkoutDiary`, `CreateBloodPressure`, `DishForm`, `CreateWorkout`, `CalorieHistory`, `DishRecipePicker`, `LipidPanelForm`, `BackPainEpisodeHistory`, `HealthConstraintForm`, `CreateWeight`, `MealEditor`, `DecisionOutcomeHistory`, `Home`, `ExercisePicture`, `CoachingPlanSettings`, `HabitList`, `WeeklySummarySettings`, `CreateFastingPeriod`, `DishRecipeEditor`, `HealthConstraintSettings` |
| `primevue/calendar` | `Reflection`, `FastingPeriodForm`, `Agenda`, `SleepForm`, `WorkoutForm`, `Settings`, `MealForm`, `WeightForm`, `SicknessForm`, `BloodPressureForm`, `PushNotificationSettings`, `RoutineList`, `MedicationList`, `MoodForm`, `LipidPanelForm`, `HealthConstraintForm`, `CoachingPlanSettings` |
| `primevue/chart` | `RoutineAnalyticsCard`, `Home` |
| `primevue/checkbox` | `MealForm`, `RoutineList`, `WorkoutDiary` |
| `primevue/column` | `MoodHistory`, `BloodPressureHistory`, `DishRecipeList`, `PersonalRecords`, `FoodList`, `LipidPanelHistory`, `SicknessHistory`, `RoutineList`, `MedicationList`, `WeightHistory`, `SleepHistory`, `WorkoutDiary`, `CalorieHistory`, `BackPainEpisodeHistory`, `DecisionOutcomeHistory`, `Home`, `HabitList`, `HealthConstraintSettings` |
| `primevue/config` | Global registration/service in `src/main.js`; no template consumer |
| `primevue/datatable` | `MoodHistory`, `BloodPressureHistory`, `DishRecipeList`, `PersonalRecords`, `FoodList`, `LipidPanelHistory`, `SicknessHistory`, `RoutineList`, `MedicationList`, `WeightHistory`, `SleepHistory`, `WorkoutDiary`, `CalorieHistory`, `BackPainEpisodeHistory`, `DecisionOutcomeHistory`, `Home`, `HabitList`, `HealthConstraintSettings` |
| `primevue/dialog` | `UrgePause`, `FastingPeriodForm`, `Agenda`, `SleepForm`, `WorkoutForm`, `CoachWarnings`, `DecisionOutcomeForm`, `MealForm`, `WeightForm`, `SicknessForm`, `BloodPressureForm`, `BackPainEpisodeForm`, `RoutineList`, `MedicationList`, `MoodForm`, `MealDurationPicker`, `WorkoutDiary`, `DishForm`, `DishRecipePicker`, `LipidPanelForm`, `HealthConstraintForm`, `Home`, `ExercisePicture`, `HabitList` |
| `primevue/dropdown` | `WorkoutForm`, `Settings`, `MealForm`, `SicknessForm`, `PersonalRecords`, `BackPainEpisodeForm`, `RoutineList`, `MedicationList`, `MoodForm`, `WorkoutDiary`, `DishForm`, `HealthConstraintForm`, `Home` |
| `primevue/fileupload` | `WeightForm` |
| `primevue/inputnumber` | `SleepForm`, `WorkoutForm`, `Settings`, `DishRecipeForm`, `MealForm`, `WeightForm`, `BloodPressureForm`, `MedicationList`, `WorkoutDiary`, `DishForm`, `DishRecipePicker`, `LipidPanelForm`, `HabitList` |
| `primevue/inputswitch` | `DishForm` |
| `primevue/inputtext` | `FastingPeriodForm`, `WorkoutForm`, `DishRecipeForm`, `MealForm`, `SicknessForm`, `DishRecipeList`, `BackPainEpisodeForm`, `FoodList`, `RoutineList`, `MedicationList`, `MoodForm`, `WorkoutDiary`, `DishForm`, `HealthConstraintForm`, `HabitList` |
| `primevue/menu` | `App` |
| `primevue/menubar` | `App` |
| `primevue/message` | `Login`, `BackPainEpisodeForm`, `PushNotificationSettings`, `PhotoHistory`, `WeeklySummarySettings` |
| `primevue/multiselect` | `RoutineList` |
| `primevue/overlaypanel` | `NotificationBell` |
| `primevue/panel` | `Agenda`, `Settings`, `PushNotificationSettings`, `SleepHistory`, `Home`, `CoachingPlanSettings`, `WeeklySummarySettings`, `HealthConstraintSettings` |
| `primevue/password` | Global registration/service in `src/main.js`; no template consumer |
| `primevue/picklist` | Global registration/service in `src/main.js`; no template consumer |
| `primevue/progressbar` | `WeightForm` |
| `primevue/radiobutton` | `PhotoHistory`, `Home` |
| `primevue/tabpanel` | `PersonalRecords`, `RoutineList`, `MedicationList`, `WorkoutDiary`, `CalorieHistory`, `Home` |
| `primevue/tabview` | `PersonalRecords`, `RoutineList`, `MedicationList`, `WorkoutDiary`, `CalorieHistory`, `Home` |
| `primevue/tag` | `DecisionOutcomeHistory` |
| `primevue/textarea` | `UrgePause`, `DecisionOutcomeForm` |
| `primevue/toast` | `App` |
| `primevue/toastservice` | Global registration/service in `src/main.js`; no template consumer |

## Utilities, icons, styles and state

PrimeFlex **2.0.0 (MIT)** has 29 used utilities: `p-col-1`, `p-col-12`, `p-col-2`, `p-col-4`, `p-col-5`, `p-col-6`, `p-col-7`, `p-col-8`, `p-d-block`, `p-field`, `p-field-checkbox`, `p-flex-row`, `p-formgrid`, `p-grid`, `p-mb-2`, `p-mb-3`, `p-mb-4`, `p-mb-5`, `p-md-4`, `p-md-6`, `p-ml-1`, `p-mr-2`, `p-mt-1`, `p-mt-3`, `p-mt-6`, `p-pb-5`, `p-text-center`, `p-text-left`, `p-text-right`. The JSON maps each token to all consumer files; use those files’ route ownership above. Literal conditional strings are included; this is a static source inventory, not computed runtime CSS coverage.

PrimeIcons **5.0.0 (MIT)** has 55 literal icons: `pi-angle-down`, `pi-arrow-down`, `pi-arrow-left`, `pi-arrow-right`, `pi-arrow-up`, `pi-bell`, `pi-bell-slash`, `pi-bolt`, `pi-calendar`, `pi-calendar-plus`, `pi-calendar-times`, `pi-chart-bar`, `pi-chart-line`, `pi-chart-pie`, `pi-check`, `pi-check-circle`, `pi-check-square`, `pi-chevron-down`, `pi-chevron-right`, `pi-chevron-up`, `pi-clock`, `pi-cog`, `pi-comment`, `pi-comments`, `pi-compass`, `pi-download`, `pi-exclamation-circle`, `pi-exclamation-triangle`, `pi-external-link`, `pi-eye`, `pi-flag`, `pi-fw`, `pi-heart`, `pi-heart-fill`, `pi-history`, `pi-home`, `pi-images`, `pi-info-circle`, `pi-list`, `pi-moon`, `pi-pencil`, `pi-plus`, `pi-plus-circle`, `pi-refresh`, `pi-send`, `pi-sign-out`, `pi-smile`, `pi-spin`, `pi-spinner`, `pi-star`, `pi-times`, `pi-times-circle`, `pi-trash`, `pi-undo`, `pi-user`. The JSON retains each consumer file. PrimeVue internal `p-*` classes are not PrimeFlex utilities.

Global CSS imports: `primevue/resources/themes/nova/theme.css` in `src/main.js`, `primevue/resources/primevue.min.css` in `src/main.js`, `primeflex/primeflex.min.css` in `src/main.js`, `primeicons/primeicons.css` in `src/main.js`, `vue3-loading-overlay/dist/vue3-loading-overlay.css` in `src/main.js`, `vue3-carousel/dist/carousel.css` in `src/components/PhotoHistory.vue`. Scoped component overrides are retained in their Vue files. Nova, PrimeVue base CSS, PrimeFlex and PrimeIcons must be migrated together only after the UI decision.

State uses Vue reactivity in `src/state.js` and `mitt` events, without Vuex/Pinia. Vuelidate validates forms; Day.js handles dates. Chart.js is used through PrimeVue Chart; AnyChart supplies bullet/status charts. Canvas-confetti and vue-confetti are separate celebration mechanisms. vue3-carousel renders progress photos; vue3-google-signin, vue3-loading-overlay, vuejs3-logger and register-service-worker complete the runtime integration surface.

## Direct dependencies

Versions below are resolved from the installed lockfile graph; requested ranges and transitive notices are retained in JSON.

| Group | Package | Installed | License metadata |
| --- | --- | --- | --- |
| dependencies | `@vuelidate/core` | 2.0.0 | MIT |
| dependencies | `@vuelidate/validators` | 2.0.0 | MIT |
| dependencies | `anychart` | 8.10.0 | SEE LICENSE IN <http://www.anychart.com/buy> |
| dependencies | `canvas-confetti` | 1.9.4 | ISC |
| dependencies | `chart.js` | 3.7.1 | MIT |
| dependencies | `core-js` | 3.33.2 | MIT |
| dependencies | `dayjs` | 1.9.7 | MIT |
| dependencies | `mitt` | 2.1.0 | MIT |
| dependencies | `primeflex` | 2.0.0 | MIT |
| dependencies | `primeicons` | 5.0.0 | MIT |
| dependencies | `primevue` | 3.38.1 | MIT |
| dependencies | `register-service-worker` | 1.7.2 | MIT |
| dependencies | `vue` | 3.2.31 | MIT |
| dependencies | `vue-confetti` | 2.3.0 | MIT |
| dependencies | `vue-router` | 4.0.1 | MIT |
| dependencies | `vue3-carousel` | 0.3.1 | MIT |
| dependencies | `vue3-google-signin` | 2.1.1 | MIT |
| dependencies | `vue3-loading-overlay` | 0.0.0 | MIT |
| dependencies | `vuejs3-logger` | 1.0.0 | MIT |
| devDependencies | `@babel/eslint-parser` | 7.23.3 | MIT |
| devDependencies | `@playwright/test` | 1.62.1 | Apache-2.0 |
| devDependencies | `@vue/cli-plugin-babel` | 5.0.6 | MIT |
| devDependencies | `@vue/cli-plugin-eslint` | 5.0.6 | MIT |
| devDependencies | `@vue/cli-plugin-pwa` | 5.0.6 | MIT |
| devDependencies | `@vue/cli-service` | 5.0.6 | MIT |
| devDependencies | `@vue/compiler-sfc` | 3.2.31 | MIT |
| devDependencies | `eslint` | 8.54.0 | MIT |
| devDependencies | `eslint-plugin-vue` | 9.18.1 | MIT |
| devDependencies | `http-server` | 14.1.1 | MIT |

## Build and runtime contracts

| Concern | Current contract and migration consequence |
| --- | --- |
| Lint | `vue-cli-service lint`; package.json ESLint 8, vue3-essential and recommended rules, Babel parser configuration and two Vue rule overrides; replace CLI integration without silently weakening checks. |
| Babel/polyfills | `babel.config.js` uses `@vue/cli-plugin-babel/preset`; core-js 3.33.2 is imported by generated Babel output; current Browserslist data is old. Vite target alone is not an equivalent polyfill policy. |
| CommonJS | Day.js `isToday`/`isYesterday` require calls in `src/model/Routine.js`, `src/model/Habit.js`, and `Home.vue`; convert during Vite migration. |
| Aliases/routes | `@` resolves src; history router; `/wins` alone is route-lazy. HTTP API prefixes `/api` and includes cookies in `src/services/api.js`. vue.config.js has allowedHosts but no dev proxy. |
| Public environment | Google client ID in main.js, Coach URL in CoachService.js; NODE_ENV controls logger and worker registration; BASE_URL prefixes worker URL. Public frontend values are build-time strings, never backend secrets. |
| Environment delivery | `.env.example`, Dockerfile, Compose, Ansible frontend build configuration and `.agents/skills/release-plan/scripts/` plus `scripts/lib/release-pipelines.sh` consume current VUE_APP names; migrate all together. |
| HTML/assets | public/index.html uses Vue CLI template BASE_URL; public icons, manifest and push worker retain root URLs. Vite must preserve history fallback and asset paths. |
| PWA | Vue CLI PWA generates Workbox, imports `/push-service-worker.js`, preserves manifest id/start/scope `/`, standalone display, Add Win/Add Loss query shortcuts; registerServiceWorker.js reports updates and reloads once on controllerchange. |
| Browser runner | test:e2e builds with synthetic public env then serves dist through http-server; Playwright default Pixel 7 Chromium, workers blocked, port override; SPA tests use history navigation because this server does not provide production fallback. |
| Release | check.sh lock; artifact gate frontend install/lint/test build/browser/production build and backend test/JAR pipelines; committed tree and checksums gate deployment. Never deploy test dist. |
| Containers | Dockerfile uses Node builder; Dockerfile.release packages prebuilt dist with floating node:lts-alpine and globally installed http-server. Pin tooling in milestone 2. |

## License findings

The runtime notice inventory contains 55 installed production packages plus the bundled vue-loader export helper and four Workbox runtime modules. All 23 package names observed in JavaScript source maps are covered. It includes compiler packages and nested Vue versions pulled in by production dependencies even when tree-shaken; it is not a claim that all 60 entries ship as executable code. Direct build-tool license metadata is recorded; build-only transitive dependencies are outside distributed-runtime scope.

[third-party-notices.txt](../../public/third-party-notices.txt) retains upstream notice texts for distribution at `/third-party-notices.txt`; existing bundle license headers remain intact. MIT texts missing from a few Vue package archives use the corresponding Vue license; mitt’s README supplies the Jason Miller attribution. Review the generated file and bundled package differences whenever dependencies change.

AnyChart 8.10.0 declares a separate proprietary license, not MIT. No entitlement is established by this repository. Verify the appropriate entitlement before a subscription launch or replace it in the decision spike. The vendor describes [free non-commercial options](https://www.anychart.com/buy/) and distinguishes [authenticated SaaS use](https://www.anychart.com/buy/license/?type=saas-annual) from a public website license; retaining its notice does not grant permission. No entitlement purchase or dependency/license change is part of this milestone.

The existing PrimeVue 3 MIT license remains the current basis; prospective PrimeVue 5 terms in the plan must be rechecked at the decision milestone.

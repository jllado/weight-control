# Frontend Modernization TODO

Current milestone: **Milestone 3 decision recorded; PrimeVue 5 execution deferred**

Selected UI library: **PrimeVue 3.38.1 (MIT), retained temporarily; review 2026-12-12**

Last updated: **2026-09-12**

This checklist implements the [frontend modernization plan](plan.md). Complete one gated milestone, sub-milestone, or coherent screen slice at a time, then update `Current milestone`, `Selected UI library`, and `Last updated` before stopping.

Next slice: **Review license eligibility and theme parity by 2026-12-12; milestone 4 is paused.** See the [milestone 3 decision and evidence](milestone-3.md). Milestone 2 is documented in [Vite migration evidence](milestone-2.md). Milestones 1A–1D are recorded in [baseline.md](baseline.md), with inventories, measurements, visual references, and explicit coverage gaps.

## Validation policy

Run standalone checks sequentially through `scripts/check.sh`; use an unused `WEIGHT_CONTROL_E2E_PORT` for browser checks when needed. The commands below describe required evidence, not a requirement to duplicate full suites before a release. During implementation, use focused browser checks after a current test build; an authorized release uses the complete release-artifact gate and production verification. Reuse baseline evidence only with its source revision, environment, exit status, and timings recorded; rerun missing or invalidated checks. Documentation-only slices use `git diff --check` and link/command validation.

## 0. Planning pack

- [x] Record the current frontend framework, build tooling, UI dependencies, and licensing position.
- [x] Define goals, non-goals, decision criteria, candidate libraries, and representative prototypes.
- [x] Split the work into resumable and independently deployable phases.
- [x] Document PrimeVue Community and Commercial license decision points.
- [x] Add the plan and TODO to the documentation index.
- [x] Review the plan against September 10 tooling, release discipline, PWA coverage, licensing, and shared design conventions.

Definition of done: the modernization effort has one linked plan, one gated checklist, explicit licensing rules, and no prematurely selected replacement library.

## 1. Baseline and inventory

Dependencies: milestone 0.

### 1A. Health check

- [x] Create `baseline.md` beside this checklist for dated findings and measurements.
- [x] Record the current Node and Yarn versions and the configured browser-support expectations.
- [x] Record matching lint, production-build, and browser-suite evidence without changing dependencies; rerun only missing or invalidated checks.
- [x] Assess the September 10 release evidence (`684157f`, validated source `918989b`, 186 browser tests passed, production verified) and retain its timings and native-cache status with reproducible commands.
- [x] Record known failures, warnings, and environmental requirements separately from future migration regressions.

Definition of done: the current frontend has a dated pass/fail baseline that another maintenance session can reproduce.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

### 1B. Dependency and component inventory

- [x] Record the current Vue, Vue CLI, PrimeVue, PrimeFlex, PrimeIcons, router, state, charting, and test versions from configuration files.
- [x] Inventory every PrimeVue component, directive, service, theme, icon, and PrimeFlex utility in production source.
- [x] Map each usage to its route, workflow, and relevant Playwright coverage, including global pauses, shared WIN/MISS actions, and notification links.
- [x] Inventory Vue CLI lint/Babel/polyfill behavior, CommonJS application imports, environment consumers, and test/release/deployment build assumptions.
- [x] Inventory frontend dependency licenses and retain required notices for distributed browser assets.

Definition of done: every relevant frontend dependency and PrimeVue usage has a documented version, license, route, workflow, and current test relationship.

Validation:

```bash
git diff --check
```

### 1C. Performance baseline

- [x] Record raw and compressed production asset sizes and identify the largest frontend packages; reuse matching release measurements where available.
- [x] Record the initial route payload and lazy-loaded chunks separately.
- [x] Note measurable optimization opportunities without implementing them in this sub-milestone.

Definition of done: later build-tool and UI-library candidates can be compared with dated production bundle measurements.

Validation:

```bash
scripts/check.sh frontend build
```

### 1D. UI baseline

- [x] Capture representative mobile and desktop screenshots for the application shell, dashboard, forms, tables, dialogs, uploads, and charts.
- [x] Retain durable references using synthetic fixtures, source revision, browser/viewport details, and reproduction commands; do not rely only on temporary test output.
- [x] Record the existing visual contracts and reference interfaces from the design guidelines, including shared WIN/MISS actions, icons, colors, fill, sizing, spacing, and focus.
- [x] Identify PWA upgrade coverage gaps separately from worker unit tests and the permission-prompt exception; normal browser tests block service workers.
- [x] Identify important UI-library interactions that lack behavioral protection.
- [x] Add focused Playwright coverage only for interactions required to make later migration regressions visible.

Definition of done: representative visual references and critical interaction tests protect the UI-library decision and migration.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend test:e2e
```

Milestone 1 is complete when sub-milestones 1A–1D are complete and `baseline.md` contains the combined health, dependency, performance, and UI baseline.

## 2. Vite migration

Dependencies: milestone 1.

- [x] Pin a current Node LTS version compatible with the selected dependency versions.
- [x] Add Vite and replace Vue CLI development and production scripts while preserving the command contracts used by the check and release helpers.
- [x] Replace Vue CLI lint integration and assess Babel/core-js polyfills against the documented browser targets.
- [x] Convert application CommonJS `require()` calls to compatible imports and migrate HTML entry templating and public assets.
- [x] Migrate Vue CLI configuration, aliases, asset handling, CSS processing, and development proxy behavior.
- [x] Rename `VUE_APP_*` variables to the selected Vite convention across source, test builds, examples, Docker/Compose, Ansible, and release-artifact/deployment helpers; preserve public/secret boundaries and update hosting configuration only where needed.
- [x] Preserve the Coach URL, API base behavior, history fallback, and production asset paths.
- [x] Preserve PWA manifest identity, scope, shortcuts, custom push worker, update behavior, and push subscriptions.
- [x] Add and pass a real-service-worker acceptance scenario that upgrades the old production build to the Vite build at the same origin and scope.
- [x] Verify Update app, activation, one reload, cached-asset replacement, offline shell behavior, and notification links through login; a clean install or mocked worker does not satisfy upgrade acceptance.
- [x] Update Playwright startup and build assumptions, preserving separate test and production artifacts and the locked release gate; run release-script checks when changing its helpers.
- [x] Remove Vue CLI packages and obsolete configuration after all consumers are migrated.
- [x] Update `docs/project-guide.md` with the new commands and authoritative configuration files.
- [x] Confirm that no UI-library version or product behavior changed in this milestone.

Definition of done: development, production builds, PWA behavior, and browser tests use Vite without changing the visible application or its HTTP contracts.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

## 3. UI-library decision spike

Dependencies: milestone 2.

Decision: **retain the existing MIT version temporarily** under the approved conservative evaluation plan. The local v3/v4 prototype demonstrates the shared theme/API migration boundary; it is not a PrimeVue 5 runtime test. PrimeVue 5 installation, license-key behavior and full visual parity remain deferred, not passed. Alternatives were screened on documentation because no demonstrated benefit justifies rewriting the current UI.

- [x] Recheck current versions, available support policies, licenses and prices; record unverified lifecycle guarantees explicitly.
- [x] Define a weighted comparison covering license predictability, maintenance, accessibility, component coverage, design fit, bundle size, documentation, and migration effort.
- [x] Prototype a filtered responsive table with realistic data and mobile overflow.
- [x] Prototype representative validated date, number, select, and multiselect inputs.
- [x] Prototype dialogs, menus, tabs, toasts, overlays, and keyboard navigation.
- [x] Prototype file upload, ordered selection, and a responsive chart or document proven alternatives.
- [x] Compare per-component imports, route splitting, theme customization, test ergonomics, and production bundle output.
- [x] Estimate migration effort using the milestone 1 inventory rather than component counts alone.
- [ ] Prove full Nova/theme and CSS parity on PrimeVue 5 after license confirmation; the v4 prototype and v4/v5 component map are complete, but visual differences remain.
- [x] Compare license entitlements and operational cost, including Community organization aggregation, exclusions, renewals, runtime notices, and any paid component replacements.
- [x] Select one target library and version or explicitly approve staying on the existing MIT version temporarily.
- [x] Record the decision, license, rejected alternatives, prototype evidence, estimated work, and next review date in the plan.
- [x] Update `Selected UI library` at the top of this TODO.

Decision gate satisfied by temporary retention; deferred migration proofs above are prerequisites for reconsideration, not permission to begin milestone 4. Evidence and validation: [milestone-3.md](milestone-3.md).

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

## 4. Target foundation

Dependencies: milestone 3.

- [ ] Install the selected UI library and lockfile changes without unrelated dependency upgrades.
- [ ] If selecting PrimeVue 5, obtain the correct license, document the renewal owner and date, and verify runtime license behavior in production builds.
- [ ] If selecting another library, document the mapping from every current PrimeVue component and PrimeFlex utility to its replacement.
- [ ] Implement the milestone 1 visual contracts through theme tokens, typography, icons, spacing, responsive breakpoints, focus styles, and reduced-motion behavior.
- [ ] Reuse focused product components and compare reference/migrated controls at mobile and desktop widths before accepting the foundation.
- [ ] Establish per-component imports and route-level code splitting.
- [ ] Add test helpers only for repeated behavior that benefits from a shared testing contract.
- [ ] Define temporary coexistence and CSS-isolation rules if incremental dual-library operation is required.
- [ ] Verify one low-risk vertical slice through development, production build, and browser tests.

Definition of done: the selected library has a tested production foundation and explicit migration conventions without a generalized wrapper framework.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

## 5. Incremental screen migration

Dependencies: milestone 4.

### 5.1 Application shell and authentication

- [ ] Migrate navigation, menus, global dialogs, toasts, login, and password controls, including the pause flag and shared decision actions.
- [ ] Verify authenticated and unauthenticated navigation, long labels, keyboard use, and mobile overflow.
- [ ] Remove replaced legacy imports, registrations, and styles; adapt test selectors/helpers while retaining behavioral assertions.

### 5.2 Dashboard and daily status

- [ ] Migrate dashboard panels, daily forms, overlays, progress indicators, and route-query dialogs.
- [ ] Preserve dashboard loading order, notification actions, save and dismiss behavior, route-query cleanup, and the app-wide timer/dashboard indicator relationship.
- [ ] Remove replaced legacy imports, registrations, and styles; adapt test selectors/helpers while retaining behavioral assertions.

### 5.3 Body, vitals, recovery, and photos

- [ ] Migrate weight, blood-pressure, lipid, sleep, mood, sickness, back-pain, and progress-photo workflows.
- [ ] Verify numeric and date validation, tables, file upload, image layout, dialogs, and empty states.
- [ ] Remove replaced legacy imports, registrations, and styles; adapt test selectors/helpers while retaining behavioral assertions.

### 5.4 Nutrition, habits, routines, and medications

- [ ] Migrate calorie, meal, fasting, habit, routine, medication, and reminder workflows.
- [ ] Verify ordered selection, repeated meal and snack controls, responsive tables, dialogs, and long labels.
- [ ] Remove replaced legacy imports, registrations, and styles; adapt test selectors/helpers while retaining behavioral assertions.

### 5.5 Workouts, records, settings, Coach, and reflections

- [ ] Migrate workout diary and entry, personal records, settings, Coach entry points, and reflection workflows.
- [ ] Verify complex tables, exercise inputs, tabs, charts, record celebrations, copied prompts, and external Coach navigation.
- [ ] Preserve Coach and reflection HTTP, privacy, confirmation, and GPT contracts.
- [ ] Remove replaced legacy imports, registrations, and styles; adapt test selectors/helpers while retaining behavioral assertions.

Definition of done: every production screen uses the selected UI foundation, preserves its behavior, and has been checked at mobile and desktop widths.

Validation after every slice:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

## 6. Legacy removal and optimization

Dependencies: all milestone 5 slices.

- [ ] Search production and test source for remaining PrimeVue, PrimeFlex, PrimeIcons, legacy theme, and compatibility references.
- [ ] Remove superseded packages, global registrations, CSS assets, and compatibility code; retire test helpers only after preserving their behavioral coverage.
- [ ] Confirm that the production bundle contains only the selected UI library and required assets.
- [ ] Compare final asset sizes with the milestone 1 baseline and investigate material regressions.
- [ ] Apply selective imports and lazy loading where measurements show useful savings.
- [ ] Verify third-party license notices for the final distributed dependencies.
- [ ] Update `docs/project-guide.md`, developer setup, and architecture references.

Definition of done: no retired UI dependencies remain, the bundle has no accidental duplicate framework, and documentation describes the new source of truth.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
git diff --check
```

## 7. Final acceptance and maintenance

Dependencies: milestone 6.

- [ ] Verify authentication, dashboard, health entry, photos, nutrition, routines, medications, workouts, records, settings, Coach, and reflections with production-like data.
- [ ] Compare migrated interfaces with their recorded references at 390–393px, relevant 575px/640px/960px breakpoints, and 1280px, including design consistency and overflow.
- [ ] Verify keyboard navigation, focus visibility, screen-reader labels, reduced motion, loading states, and empty states.
- [ ] Verify PWA installation, service-worker updates, offline shell behavior, and push-notification actions.
- [ ] Confirm the selected license permits the current subscription business model and team structure.
- [ ] Record any intentionally deferred visual or dependency work.
- [ ] Schedule the next annual dependency, support, and license review.

Definition of done: the modernized frontend is commercially compliant, supported, responsive, accessible, documented, and ready for regular subscription use.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
git diff --check
```

# Frontend Modernization TODO

Current milestone: **Milestone 1A not started**

Selected UI library: **Undecided**

Last updated: **2026-08-23**

This checklist implements the [frontend modernization plan](plan.md). Complete one gated milestone, sub-milestone, or coherent screen slice at a time, then update `Current milestone`, `Selected UI library`, and `Last updated` before stopping.

## 0. Planning pack

- [x] Record the current frontend framework, build tooling, UI dependencies, and licensing position.
- [x] Define goals, non-goals, decision criteria, candidate libraries, and representative prototypes.
- [x] Split the work into resumable and independently deployable phases.
- [x] Document PrimeVue Community and Commercial license decision points.
- [x] Add the plan and TODO to the documentation index.

Definition of done: the modernization effort has one linked plan, one gated checklist, explicit licensing rules, and no prematurely selected replacement library.

## 1. Baseline and inventory

Dependencies: milestone 0.

### 1A. Health check

- [ ] Create `baseline.md` beside this checklist for dated findings and measurements.
- [ ] Record the current Node and Yarn versions and the configured browser-support expectations.
- [ ] Run lint, the production build, and the end-to-end suite without changing dependencies.
- [ ] Record known failures, warnings, and environmental requirements separately from future migration regressions.

Definition of done: the current frontend has a dated pass/fail baseline that another maintenance session can reproduce.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
```

### 1B. Dependency and component inventory

- [ ] Record the current Vue, Vue CLI, PrimeVue, PrimeFlex, PrimeIcons, router, state, charting, and test versions from configuration files.
- [ ] Inventory every PrimeVue component, directive, service, theme, icon, and PrimeFlex utility in production source.
- [ ] Map each usage to its route, workflow, and relevant Playwright coverage.
- [ ] Inventory frontend dependency licenses and retain required notices for distributed browser assets.

Definition of done: every relevant frontend dependency and PrimeVue usage has a documented version, license, route, workflow, and current test relationship.

Validation:

```bash
git diff --check
```

### 1C. Performance baseline

- [ ] Record production asset sizes and identify the largest frontend packages.
- [ ] Record the initial route payload and lazy-loaded chunks separately.
- [ ] Note measurable optimization opportunities without implementing them in this sub-milestone.

Definition of done: later build-tool and UI-library candidates can be compared with dated production bundle measurements.

Validation:

```bash
yarn build
```

### 1D. UI baseline

- [ ] Capture representative mobile and desktop screenshots for the application shell, dashboard, forms, tables, dialogs, uploads, and charts.
- [ ] Identify important UI-library interactions that lack behavioral protection.
- [ ] Add focused Playwright coverage only for interactions required to make later migration regressions visible.

Definition of done: representative visual references and critical interaction tests protect the UI-library decision and migration.

Validation:

```bash
yarn lint
yarn test:e2e
```

Milestone 1 is complete when sub-milestones 1A–1D are complete and `baseline.md` contains the combined health, dependency, performance, and UI baseline.

## 2. Vite migration

Dependencies: milestone 1.

- [ ] Pin a current Node LTS version compatible with the selected dependency versions.
- [ ] Add Vite and replace Vue CLI development and production scripts.
- [ ] Migrate Vue CLI configuration, aliases, asset handling, CSS processing, and development proxy behavior.
- [ ] Rename `VUE_APP_*` variables to the selected Vite convention across source, examples, Docker, Caddy, and Ansible configuration.
- [ ] Preserve the Coach URL, API base behavior, history fallback, and production asset paths.
- [ ] Preserve PWA manifest, service worker, update behavior, and push-notification flows.
- [ ] Update Playwright startup and build assumptions.
- [ ] Remove Vue CLI packages and obsolete configuration after all consumers are migrated.
- [ ] Update `docs/project-guide.md` with the new commands and authoritative configuration files.
- [ ] Confirm that no UI-library version or product behavior changed in this milestone.

Definition of done: development, production builds, PWA behavior, and browser tests use Vite without changing the visible application or its HTTP contracts.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
```

## 3. UI-library decision spike

Dependencies: milestone 2.

- [ ] Recheck the current versions, support policies, licenses, and prices for PrimeVue, Vuetify, and Element Plus.
- [ ] Define a weighted comparison covering license predictability, maintenance, accessibility, component coverage, design fit, bundle size, documentation, and migration effort.
- [ ] Prototype a filtered responsive table with realistic data and mobile overflow.
- [ ] Prototype representative validated date, number, select, and multiselect inputs.
- [ ] Prototype dialogs, menus, tabs, toasts, overlays, and keyboard navigation.
- [ ] Prototype file upload, ordered selection, and a responsive chart or document proven alternatives.
- [ ] Compare per-component imports, route splitting, theme customization, test ergonomics, and production bundle output.
- [ ] Estimate migration effort using the milestone 1 inventory rather than component counts alone.
- [ ] Select one target library and version or explicitly approve staying on the existing MIT version temporarily.
- [ ] Record the decision, license, rejected alternatives, prototype evidence, estimated work, and next review date in the plan.
- [ ] Update `Selected UI library` at the top of this TODO.

Definition of done: one evidence-based and commercially acceptable UI-library decision is recorded before production migration begins.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
```

## 4. Target foundation

Dependencies: milestone 3.

- [ ] Install the selected UI library and lockfile changes without unrelated dependency upgrades.
- [ ] If selecting PrimeVue 5, obtain the correct license, document the renewal owner and date, and verify runtime license behavior in production builds.
- [ ] If selecting another library, document the mapping from every current PrimeVue component and PrimeFlex utility to its replacement.
- [ ] Establish theme tokens, typography, icons, spacing, responsive breakpoints, focus styles, and reduced-motion behavior.
- [ ] Establish per-component imports and route-level code splitting.
- [ ] Add test helpers only for repeated behavior that benefits from a shared testing contract.
- [ ] Define temporary coexistence and CSS-isolation rules if incremental dual-library operation is required.
- [ ] Verify one low-risk vertical slice through development, production build, and browser tests.

Definition of done: the selected library has a tested production foundation and explicit migration conventions without a generalized wrapper framework.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
```

## 5. Incremental screen migration

Dependencies: milestone 4.

### 5.1 Application shell and authentication

- [ ] Migrate navigation, menus, global dialogs, toasts, login, and password controls.
- [ ] Verify authenticated and unauthenticated navigation, long labels, keyboard use, and mobile overflow.
- [ ] Remove replaced legacy imports, registrations, styles, and tests from this slice.

### 5.2 Dashboard and daily status

- [ ] Migrate dashboard panels, daily forms, overlays, progress indicators, and route-query dialogs.
- [ ] Preserve dashboard loading order, notification actions, save and dismiss behavior, and route-query cleanup.
- [ ] Remove replaced legacy imports, registrations, styles, and tests from this slice.

### 5.3 Body, vitals, recovery, and photos

- [ ] Migrate weight, blood-pressure, lipid, sleep, mood, sickness, back-pain, and progress-photo workflows.
- [ ] Verify numeric and date validation, tables, file upload, image layout, dialogs, and empty states.
- [ ] Remove replaced legacy imports, registrations, styles, and tests from this slice.

### 5.4 Nutrition, habits, routines, and medications

- [ ] Migrate calorie, meal, fasting, habit, routine, medication, and reminder workflows.
- [ ] Verify ordered selection, repeated meal and snack controls, responsive tables, dialogs, and long labels.
- [ ] Remove replaced legacy imports, registrations, styles, and tests from this slice.

### 5.5 Workouts, records, settings, Coach, and reflections

- [ ] Migrate workout diary and entry, personal records, settings, Coach entry points, and reflection workflows.
- [ ] Verify complex tables, exercise inputs, tabs, charts, record celebrations, copied prompts, and external Coach navigation.
- [ ] Preserve Coach and reflection HTTP, privacy, confirmation, and GPT contracts.
- [ ] Remove replaced legacy imports, registrations, styles, and tests from this slice.

Definition of done: every production screen uses the selected UI foundation, preserves its behavior, and has been checked at mobile and desktop widths.

Validation after every slice:

```bash
yarn lint
yarn build
yarn test:e2e
```

## 6. Legacy removal and optimization

Dependencies: all milestone 5 slices.

- [ ] Search production and test source for remaining PrimeVue, PrimeFlex, PrimeIcons, legacy theme, and compatibility references.
- [ ] Remove superseded packages, global registrations, CSS assets, compatibility code, and test helpers.
- [ ] Confirm that the production bundle contains only the selected UI library and required assets.
- [ ] Compare final asset sizes with the milestone 1 baseline and investigate material regressions.
- [ ] Apply selective imports and lazy loading where measurements show useful savings.
- [ ] Verify third-party license notices for the final distributed dependencies.
- [ ] Update `docs/project-guide.md`, developer setup, and architecture references.

Definition of done: no retired UI dependencies remain, the bundle has no accidental duplicate framework, and documentation describes the new source of truth.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
git diff --check
```

## 7. Final acceptance and maintenance

Dependencies: milestone 6.

- [ ] Verify authentication, dashboard, health entry, photos, nutrition, routines, medications, workouts, records, settings, Coach, and reflections with production-like data.
- [ ] Verify affected interfaces at representative mobile and desktop widths.
- [ ] Verify keyboard navigation, focus visibility, screen-reader labels, reduced motion, loading states, and empty states.
- [ ] Verify PWA installation, service-worker updates, offline shell behavior, and push-notification actions.
- [ ] Confirm the selected license permits the current subscription business model and team structure.
- [ ] Record any intentionally deferred visual or dependency work.
- [ ] Schedule the next annual dependency, support, and license review.

Definition of done: the modernized frontend is commercially compliant, supported, responsive, accessible, documented, and ready for regular subscription use.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
git diff --check
```

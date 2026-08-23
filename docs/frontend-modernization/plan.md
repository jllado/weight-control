# Frontend Modernization Plan

## Purpose

Modernize the frontend in small, independently deployable steps while preserving the application's behavior and deciding deliberately whether PrimeVue remains the right UI library for a subscription product.

This is a recurring maintenance effort rather than a single rewrite, so every phase must leave the application usable and may be resumed after a long pause.

## Current state

- Vue 3 is the application framework and remains appropriate for the product.
- Vue CLI and Yarn v1 provide the current build toolchain.
- The code follows the Vue Options API and registers approximately two dozen PrimeVue components globally in `src/main.js`.
- PrimeVue 3.38.1, PrimeFlex 2.0.0, and PrimeIcons 5.0.0 are pinned in `package.json`.
- PrimeVue 3 is MIT-licensed and permits commercial subscription applications.
- PrimeVue 5 uses the PrimeUI Community or Commercial license instead of MIT.
- Playwright covers important browser workflows, but visual and component-migration coverage must be strengthened before broad UI changes.

## Goals

- Keep Vue and the current component/service/model architecture.
- Replace Vue CLI with a maintained Vite-based build before or alongside the UI-library migration.
- Choose the future UI library using licensing, maintenance, component coverage, accessibility, bundle cost, and migration effort.
- Remove unsupported frontend dependencies without combining the work with unrelated product changes.
- Preserve behavior, URLs, API contracts, responsive layouts, and Coach/reflection workflows throughout the migration.
- Make each implementation slice small enough to complete and validate during an occasional maintenance session.

## Non-goals

- Do not rewrite the frontend in React, Angular, or another application framework.
- Do not convert the Options API to the Composition API as part of this work.
- Do not redesign the product or backend architecture during dependency migration.
- Do not introduce a generalized component abstraction layer solely to hide the selected UI library.
- Do not change Coach domains, Actions, GPT instructions, or privacy contracts unless a later product requirement makes them relevant.

## UI-library decision

Evaluate these candidates against the real application rather than popularity alone:

- **PrimeVue 5:** likely the smallest migration, but it introduces an annual Community license key and a Commercial license when the organization exceeds an eligibility threshold.
- **Vuetify 4:** established and MIT-licensed, but changing to its components and design system would require a substantial UI rewrite.
- **Element Plus:** active and MIT-licensed, but its component behavior, accessibility, design fit, and migration cost must be proven against the application's difficult screens.

Do not adopt Quasar unless the product later needs its broader application framework and deployment targets; replacing the existing Vue application structure with Quasar is outside this effort.

Prototype the candidates with the hardest representative interactions:

- Filtered and responsive data tables.
- Date, number, select, and multiselect inputs with validation.
- Dialogs, menus, tabs, toasts, and overlays.
- File upload and progress-photo workflows.
- Pick-list or equivalent ordered selection.
- Charts and responsive dashboard panels.

Record the chosen library, version, license, rejected alternatives, prototype findings, estimated migration size, and review date before production migration starts.

## Licensing rules

The existing PrimeVue 3 code may continue to be used commercially under MIT, including while selling subscriptions.

If PrimeVue 5 is selected, verify the binding [PrimeUI Community License Agreement](https://primeui.dev/eula/community) before upgrading. As of 2026-08-22, Community eligibility requires all of the following:

- Fewer than five developers.
- Annual gross revenue under US$1 million.
- Fewer than ten employees.
- No more than US$3 million in external funding.
- Annual eligibility confirmation and license-key renewal.

Track eligibility annually and whenever team size, employee count, revenue, funding, ownership, or licensing terms change. Obtain the appropriate Commercial license before continuing development after any threshold is crossed.

Ordinary SaaS use does not require an OEM license. Reassess OEM terms if customers are ever allowed to build applications with exposed PrimeUI components.

## Delivery phases

### Phase 1: Baseline and inventory

Document the current dependency graph, component usage, browser support, production build, bundle composition, runtime warnings, and licensing obligations.

Add focused browser coverage for representative PrimeVue interactions and capture mobile and desktop reference screenshots before changing the UI foundation.

Complete this phase as four independent sub-milestones: health check, dependency and component inventory, performance baseline, and UI baseline.

### Phase 2: Build-tool modernization

Move from Vue CLI to Vite while retaining Vue, the Options API, application routes, environment behavior, PWA behavior, production hosting, and service-worker functionality.

Treat environment-variable renaming, development proxy behavior, production asset paths, Docker, Caddy, and Ansible configuration as part of this phase. Keep UI-library behavior unchanged so build-tool and component regressions remain distinguishable.

### Phase 3: UI-library decision spike

Implement disposable prototypes for the difficult interactions, compare the candidates using the documented criteria, review their current licenses and support policies, and record one decision.

Do not install a second production UI library or begin screen migration before this decision is recorded.

### Phase 4: Target foundation

Install the selected library and establish its theme, icons, typography, spacing, accessibility conventions, test helpers, and import strategy.

Prefer per-component imports and route-level code splitting. Allow temporary coexistence only when required for an incremental migration, keep global styles isolated, and remove each old dependency as soon as its last consumer is migrated.

### Phase 5: Incremental screen migration

Migrate cohesive route or workflow slices without changing their product behavior:

1. Application shell and authentication.
2. Dashboard and daily-status interactions.
3. Body, vitals, sleep, mood, sickness, and progress photos.
4. Nutrition, fasting, habits, routines, and medications.
5. Workouts, personal records, settings, Coach entry points, and reflections.

For every slice, preserve keyboard behavior, validation, loading and empty states, long-label handling, overflow, mobile layout, desktop layout, and relevant Playwright scenarios.

### Phase 6: Legacy removal and optimization

Remove the old UI library, obsolete theme assets, compatibility code, unused CSS, stale tests, and retired dependencies only after searches and builds confirm that no consumers remain.

Measure the production bundle again and address material regressions through selective imports and lazy loading rather than premature custom replacements.

### Phase 7: Acceptance and recurring maintenance

Run the complete frontend and browser validation suite, verify core workflows manually at mobile and desktop widths, update `docs/project-guide.md` with the new source of truth, and document any intentional deferrals.

Review frontend versions, support status, licenses, and renewal requirements at least annually and before each commercial release.

## Working rhythm

- Select one unchecked TODO group or one coherent screen slice per maintenance session.
- Keep dependency migration separate from feature development and visual redesign.
- Update the TODO milestone and date whenever work advances.
- Commit only after the affected slice passes its listed validation.
- Stop at a deployable boundary when the next group cannot be completed in the same session.

## Success criteria

- The application uses supported frontend tooling and a deliberately selected UI library.
- Commercial licensing obligations are known, recorded, and affordable for the business stage.
- Existing workflows and API contracts remain compatible.
- Mobile and desktop layouts remain usable and consistent.
- The production bundle has no accidental duplicate UI frameworks or retired assets.
- A future maintainer can resume the effort from the TODO without rediscovering earlier decisions.

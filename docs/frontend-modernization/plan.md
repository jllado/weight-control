# Frontend Modernization Plan

## Purpose

Modernize the frontend in small, independently deployable steps while preserving the application's behavior and deciding deliberately whether PrimeVue remains the right UI library for a subscription product.

This is a recurring maintenance effort rather than a single rewrite, so every phase must leave the application usable and may be resumed after a long pause.

## Current state

Baseline reviewed on **2026-09-10**; build-tool migration implemented on **2026-09-12**.

- Vue 3 is the application framework and remains appropriate for the product.
- Vite 8.3.0 and Yarn v1 provide the build toolchain; Node 24.21.0 is pinned.
- The code follows the Vue Options API and registers approximately two dozen PrimeVue components globally in `src/main.js`.
- PrimeVue 3.38.1, PrimeFlex 2.0.0, and PrimeIcons 5.0.0 are pinned in `package.json`.
- PrimeVue 3 is MIT-licensed and permits commercial subscription applications.
- PrimeVue 5 uses the PrimeUI Community or Commercial license instead of MIT.
- The September 10 release `684157f` passed 186 browser tests and production verification; its validated source commit is `918989b`. The baseline records its evidence and environment limits alongside current measurements and focused tests.
- Shared WIN/MISS actions and appearance comparisons now protect consistency across the dashboard and pause flows; extend these conventions to representative workflows before broad UI changes.
- The normal Playwright configuration blocks service workers; the permission-prompt exception and worker-specific tests do not establish old-build-to-new-build upgrade compatibility.

Milestones 1 and 2 are complete: see the [dated baseline](baseline.md), [route/component inventory](inventory.md), and [Vite migration evidence](milestone-2.md). The [milestone 3 decision](milestone-3.md) retains the existing UI library temporarily and records the remaining migration proofs.

## Goals

- Keep Vue and the current component/service/model architecture.
- Replace Vue CLI with a maintained Vite-based build before the UI-library migration.
- Choose the future UI library using licensing, maintenance, component coverage, accessibility, bundle cost, and migration effort.
- Remove unsupported frontend dependencies without combining the work with unrelated product changes.
- Preserve behavior, URLs, API contracts, responsive layouts, and Coach/reflection workflows throughout the migration.
- Make each implementation slice small enough to complete and validate during an occasional maintenance session.

## Non-goals

- Do not rewrite the frontend in React, Angular, or another application framework.
- Do not convert the Options API to the Composition API as part of this work.
- Do not redesign the product or backend architecture during dependency migration.
- Do not introduce a generalized component abstraction layer solely to hide the selected UI library; reuse focused product components such as `DecisionOutcomeActions.vue` to preserve shared behavior and appearance.
- Do not change Coach domains, Actions, GPT instructions, or privacy contracts unless a later product requirement makes them relevant.

## UI-library decision

**2026-09-12 decision: retain PrimeVue 3.38.1 (MIT) temporarily; review on 2026-12-12.** The [milestone 3 evaluation](milestone-3.md) records the comparison, isolated MIT v3/v4 prototype, measured differences, migration estimate and deferred PrimeVue 5 license/runtime verification. The owner accepts license management, but no agreement acceptance or organization eligibility was established. Production migration is paused; milestone 4 must not start from the candidate scores alone.

Preserving the current appearance and workflows is a hard acceptance requirement. Evaluate PrimeVue first, use documentation to screen alternatives, and expand prototypes only for a concrete compatibility, maintenance or licensing need. Temporary retention is the conservative decision, not an assertion that v3 still receives ordinary maintenance. Before reconsidering, establish PrimeVue 5 entitlement, prove its actual runtime behavior, and close the documented visual differences.

Evaluate these candidates against the real application rather than popularity alone:

- **PrimeVue 5:** potentially the smallest migration, to be demonstrated by prototypes; it changes licensing and requires adapting the current v3 theme and component usage.
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

For PrimeVue, explicitly assess Nova/theme replacement, CSS overrides, Calendar → DatePicker, Dropdown → Select, OverlayPanel → Popover, TabView → Tabs, and the deprecated Chart component and its replacement options. Use the official [v4 migration guide](https://primevue.dev/migration/v4/) and [v5 migration guide](https://primevue.dev/migration/v5/); do not infer v3 compatibility from a v4-to-v5 upgrade claim.

Record the chosen library, version, license, rejected alternatives, prototype findings, estimated migration size, and review date before production migration starts.

## Licensing rules

The existing PrimeVue 3 code may continue to be used commercially under MIT, including while selling subscriptions.

If PrimeVue 5 is selected, verify the binding [PrimeUI Community License Agreement](https://primeui.dev/eula/community) before upgrading. The terms reviewed on 2026-09-10 (updated July 28, 2026) require all of the following for Community eligibility:

- Fewer than five developers.
- Annual revenue, or annual budget for nonprofits, under US$1 million.
- Fewer than ten employees.
- No more than US$3 million in external funding.
- Not a public-sector body, government entity, or publicly funded educational institution.

Revenue and funding eligibility aggregate the controlling organization and its controlled entities. Community keys require annual eligibility confirmation and renewal, with a 30-day expiry grace period; missing, invalid, or expired keys may show notices in deployed applications. Include renewal ownership and runtime notice behavior in the operational comparison. Community scope excludes PRO components and other paid add-ons; verify the entitlement for each proposed replacement.

Track eligibility annually and whenever team size, employee count, revenue, funding, ownership, or licensing terms change. Obtain the appropriate Commercial license before continuing development after any threshold is crossed.

Ordinary SaaS use does not require an OEM license. Reassess OEM terms if customers are ever allowed to build applications with exposed PrimeUI components.

## Delivery phases

### Phase 1: Baseline and inventory

Document the current dependency graph, component usage, browser support, production build, bundle composition, runtime warnings, and licensing obligations.

Start with a documentation-and-baseline slice: update the planning pack, create `baseline.md`, record matching release evidence and environmental requirements, and inventory dependencies and missing coverage without changing dependencies.

Capture reproducible visual references with synthetic fixtures and recorded viewport, browser, source revision, and capture commands; retain durable references rather than relying only on overwritten `test-results/` output. Follow [design guidelines](../design-guidelines.md) and record shared actions, labels, icons, fill, colors, sizing, spacing, focus, and responsive rules. Add focused coverage only where existing assertions leave a migration risk unprotected.

Complete this phase as four independent sub-milestones: health check, dependency and component inventory, performance baseline, and UI baseline.

### Phase 2: Build-tool modernization

Move from Vue CLI to Vite while retaining Vue, the Options API, application routes, environment behavior, PWA behavior, production hosting, and service-worker functionality.

Replace Vue CLI lint integration as well as build scripts; explicitly assess Babel, core-js/polyfills, browser targets, application CommonJS `require()` calls, aliases, HTML templating, public assets, and CSS processing. Pin compatible Node tooling without coupling the work to a package-manager or UI-library upgrade.

Trace environment variables through application source, test builds, examples, Docker/Compose, Ansible, and the release-artifact and deployment helpers. Preserve intentional public variables without exposing backend secrets. Preserve script contracts used by `scripts/check.sh` and the release gate, including distinct test and production builds. Change hosting/proxy configuration only where the migration requires it.

Add a production-like PWA upgrade acceptance scenario with real service workers enabled: load the old build, deploy the new build at the same origin and scope, verify Update app, worker activation, one reload, correct cached assets and offline shell behavior, and notification links after login. Preserve the custom push worker, manifest identity, shortcuts, scope, and subscription behavior. Test this before accepting Vite; a clean install or mocked worker alone is insufficient. Keep UI-library behavior unchanged so build-tool and component regressions remain distinguishable.

### Phase 3: UI-library decision spike

Implement disposable prototypes for the difficult interactions, compare the candidates using the documented criteria, review their current licenses and support policies, and record one decision.

Do not install a second production UI library or begin screen migration before this decision is recorded.

### Phase 4: Target foundation

Install the selected library and implement the visual contracts recorded in phase 1 through its theme, icons, typography, spacing, accessibility conventions, shared product components, test helpers, and import strategy. Compare against the existing reference interfaces before accepting the foundation; library defaults are not permission to redesign controls.

Prefer per-component imports and route-level code splitting. Allow temporary coexistence only when required for an incremental migration, keep global styles isolated, and remove each old dependency as soon as its last consumer is migrated.

### Phase 5: Incremental screen migration

Migrate cohesive route or workflow slices without changing their product behavior:

1. Application shell and authentication.
2. Dashboard and daily-status interactions.
3. Body, vitals, sleep, mood, sickness, and progress photos.
4. Nutrition, fasting, habits, routines, and medications.
5. Workouts, personal records, settings, Coach entry points, and reflections.

For every slice, preserve keyboard behavior, validation, loading and empty states, long-label handling, overflow, mobile layout, desktop layout, and relevant Playwright scenarios. Include the global pause controller, flag entry point, linked decisions, and notification route handling in shell/dashboard coverage. Adapt selectors and helpers while preserving behavioral assertions. Compare reference and migrated interfaces at 390–393px, relevant 575px/640px/960px breakpoints, and 1280px.

### Phase 6: Legacy removal and optimization

Remove the old UI library, obsolete theme assets, compatibility code, unused CSS, and retired dependencies only after searches and builds confirm that no consumers remain.

Remove obsolete test helpers only after their behavioral assertions are retained in the migrated coverage.

Measure the production bundle again and address material regressions through selective imports and lazy loading rather than premature custom replacements.

### Phase 7: Acceptance and recurring maintenance

Run the complete frontend and browser validation suite, verify core workflows manually at mobile and desktop widths, update `docs/project-guide.md` with the new source of truth, and document any intentional deferrals.

Review frontend versions, support status, licenses, and renewal requirements at least annually and before each commercial release.

## Validation and evidence

Use `scripts/check.sh` for all checks and builds, with sequential standalone invocations and an unused `WEIGHT_CONTROL_E2E_PORT` when needed. Run focused checks during implementation; an authorized release uses the complete release-artifact gate before push/deployment instead of duplicating full suites beforehand. Wait for exit and cleanup, record stage timings, and distinguish native cached backend results from newly executed browser tests.

Reuse logs, production bundle measurements, and visual captures only when their source revision and environment match the baseline being recorded. Preserve a dated summary and reproducible commands in `baseline.md`; rerun missing or invalidated checks. Documentation-only edits require `git diff --check` and link/command validation.

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

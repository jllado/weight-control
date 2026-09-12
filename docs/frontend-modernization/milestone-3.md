# Milestone 3 — conservative UI-library evaluation

Evaluation date: **2026-09-12**. Production reference: **0da9448**. **Decision: retain PrimeVue 3.38.1 (MIT) temporarily; review 2026-12-12. Milestone 4 is paused.**

## Decision criteria

Preserve the existing experience: compact light panels, control alignment, responsive layouts, date/number semantics, photo selection, ordered exercises, keyboard operation, save feedback, and PWA behavior. A newer major version alone does not justify a migration. The owner accepts license management when suitability and costs are established.

Scores use a 1–5 scale, where 5 is best. Compatibility/effort includes component coverage and design fit; tooling includes imports, bundle behavior, documentation and tests. These are engineering assessments, not benchmark results. A score never overrides an unresolved functional, accessibility, visual or licensing requirement.

| Candidate | Compatibility / effort 35% | Maintenance 25% | Licensing 15% | Accessibility 15% | Tooling 10% | Weighted / 5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Existing PrimeVue 3.38.1 | 5 | 1 | 5 | 3 | 3 | 3.50 |
| PrimeVue 5.0.1 | 3 | 4 | 3 | 3 | 4 | 3.35 |
| PrimeVue 4.5.5 bridge | 3 | 2 | 5 | 3 | 4 | 3.15 |
| Element Plus 2.14.5 | 2 | 4 | 5 | 3 | 4 | 3.30 |
| Vuetify 4.2.1 | 1 | 4 | 5 | 3 | 4 | 2.95 |

Accessibility stays neutral for unproven candidates: documentation is not application acceptance. Maintenance scores reflect release activity and published support information, not a guarantee of security updates. The existing library scores well only for temporary preservation; its maintenance score prevents treating it as a permanent answer.

## Current versions, support and licensing

[Registry evidence](evidence/milestone-3/registry.json) records exact versions, release dates, licenses and Vue peer requirements from the public npm registry. Repository production dependencies remain unchanged.

- **PrimeVue:** the official [v4 migration guide](https://primevue.dev/migration/v4/) says ordinary v3 maintenance ended in 2024 and describes the new theme architecture. The [v5 guide](https://primevue.dev/migration/v5/) lists removed legacy names and additional deprecations. The v4-to-v5 compatibility statement does not establish v3 compatibility. PrimeVue 4.5.5 and its pinned themes 2.0.3 retain MIT metadata; their use here measures the shared v3 migration boundary, not an assumed long-term support commitment.
- **Element Plus:** 2.14.5 requires Vue ^3.3.7. Its [repository](https://github.com/element-plus/element-plus) is MIT and the [changelog](https://element-plus.org/en-US/guide/changelog) describes normally weekly releases. No fixed support-end guarantee was established. Table, input, dialog, upload and [transfer](https://element-plus.org/en-US/component/transfer.html) controls exist; app-specific ordering, theming and accessibility still need proof. [On-demand imports](https://element-plus.org/en-US/guide/quickstart.html) are documented.
- **Vuetify:** 4.2.1 requires Vue ^3.5.0 or ^3.6.0-0 and is MIT in registry metadata. [Official releases](https://github.com/vuetifyjs/vuetify/releases/tag/v4.2.1) establish ongoing activity. A fixed support end was not verified from its dynamically rendered lifecycle page. Its component/style migration would reach almost every form and table; adoption offers no demonstrated benefit sufficient to justify that rewrite here.

PrimeVue 5 changes to Community/Commercial terms. The [Community agreement](https://primeui.dev/eula/community) requires eligibility across the controlling organization, excludes PRO/add-ons, and requires annual key renewal with a 30-day grace period. Missing, expired or invalid keys can produce runtime notices. Installation/use accepts the terms; eligibility and agreement acceptance must be established before the v5 prototype is installed. No key has been acquired, no agreement accepted, and no notice mechanism bypassed by this evaluation.

The [price list](https://primeui.dev/pricing) currently offers Community at no charge for eligible users; Commercial is US$599 per developer through December 31, 2026, then US$799, with a listed US$399 update renewal. Charts alone are US$399 per developer. Verify taxes and current checkout terms before purchase. The [Commercial terms](https://primeui.dev/licenses/commercial) distinguish perpetual per-developer rights to covered versions from annual Site licensing. Ordinary application SaaS delivery does not itself require OEM licensing; exposing components for customers' development is a different scope.

A future PrimeVue 5 adoption must record the license owner, organization eligibility or paid seats, issued key and renewal date, reminder ownership, and observed valid/missing/expired-key behavior in a local production build. A purchased or fabricated key is not part of this work. Existing AnyChart entitlement remains unresolved as recorded in milestone 1; this prototype uses existing MIT Chart.js directly and does not resolve every AnyChart visualization.

## Prototype and migration map

The [isolated harness](../../tools/ui-library-spike/README.md) has its own manifest and frozen lockfile. The shared synthetic fixture compares Nova/v3 controls against Lara/v4 controls on Vue 3.5.42. It is not a full migrated application. The original milestone 2 screenshots remain the production references; the harness supplies controlled widget comparisons at all five required widths.

[Current consumer evidence](evidence/milestone-3/component-consumers.json) refreshes the milestone 1 inventory against the release reference. Counts are distinct Vue files, overlap, and must not be added as independent screens.

| Current surface | Consumers | Migration work |
| --- | ---: | --- |
| Calendar | 17 | DatePicker; preserve local dates, range/time modes, labels, overlays and fixed-date notifications |
| Dropdown | 15 | Select; preserve filtering, object/value mapping and validation |
| OverlayPanel | 1 | Popover; preserve notification alignment, focus and responsive positioning |
| TabView | 6 | Tabs structure and active values; preserve route/query selection and keyboard behavior |
| MultiSelect | 2 | v5 deprecates it in favor of Select multiple; validate selection and long-label behavior |
| Chart | 2 | Direct Chart.js is demonstrated for a responsive line chart; compare every real chart before migration |
| Button | 62 | Recheck slots, icon sizing, variants, disabled/busy states; v5 deprecates label/loading props |
| DataTable | 20 | Sorting/filtering, paging, slots, loading/empty states and deliberate mobile overflow |
| Dialog | 26 | Focus, footer order, overlay dismissal, pending state and mobile width |
| InputNumber | 13 | Decimal parsing, units, precision, validation and keyboard behavior |
| FileUpload | 1 | Custom upload/preview flow, progress, failure and partial-success retry |
| PickList | 0 | Registered but unused in production; fixture measures ordering without making it a migration dependency |

The fixture imports each UI component explicitly and lazy-loads a direct Chart.js component. It reuses the production `SaveFields` boundary; save simulation waits 1.5 seconds and never calls production APIs. The reference resolves all transitive PrimeVue imports to v3, avoiding accidental mixed-version internals. Production deployment excludes the harness directory.

Estimated PrimeVue migration effort: 2–3 engineering days for theme/shared controls, 3–5 for 17 date and 15 select consumers with their modes, 3–5 for 20 tables/26 dialogs/six tab consumers, 2–3 for charts/photos/notifications, and 2–4 for application regression, visual fixes and PWA release acceptance: **12–20 engineering days**, with overlapping consumers grouped by workflow. This is an estimate, not work already performed. Element Plus is approximately 20–30 days and Vuetify 25–40 days because both replace component APIs and style conventions across the application; neither estimate is backed by a full application prototype.

## Evidence and remaining gates

The approved conservative plan allows temporary retention when a candidate does not meet the acceptance conditions. That is the selected outcome. PrimeVue 5 remains the first future path to investigate, but its runtime prototype is deferred pending Community eligibility/agreement confirmation or a suitable existing license. MIT PrimeVue 4.5.5 supplied useful evidence of the shared migration boundary while preserving that restriction. No v5 execution or license-key state is reported as tested.

The 16-case prototype suite passed (`run.9PoeT0`, 38 seconds, zero retries). It covers all five widths, filtering/empty states, validated input, date overlays, photo preview, keyboard menu/tabs, focus restoration, ordered selection, delayed/failed saves, disabled/inert controls, one request per save, retained draft/photo on failure, retry success and lazy chart loading. The final date-column adjustment passed all ten width/variant layout cases (`run.SteDsM`, 25 seconds); two additional cases prove actual horizontal scrolling and local-date/period serialization (`run.TJ9sQL`, 11 seconds). Root production regression and PWA tests run once in the release-artifact gate; consult its stage timings for final candidate results.

Visual review found aligned, usable controls and no page overflow after narrowing date cells through supported theme tokens and allowing long multiselect labels to wrap. Dates remain on one line and wide tables scroll deliberately. Important differences remain: Lara dialog radius, mask, header/footer, secondary button treatment, upload icon and tab styling do not match Nova. The inactive tab measured 45.4px/16px text in the reference versus 49px/13.3px text in the candidate at 390px. These differences fail the preservation requirement even though the interaction suite passes. They are recorded rather than accepted as a redesign.

The final controlled builds total **1,067,368 raw / 259,028 gzip bytes** for the reference and **1,247,650 raw / 293,116 gzip bytes** for the candidate: **13.2% more gzip bytes**.

The controlled fixture's [bundle measurements](evidence/milestone-3/bundles.json) include all built JS/CSS, including the lazy Chart.js chunk, exclude fonts/images, and gzip files individually. Both variants use Vue 3.5.42 and the same fixture/toolchain. Candidate CSS injected from JavaScript is included in JS bytes. These figures do not predict full application savings or measure startup latency. The candidate is larger; there is no demonstrated performance reason to upgrade.

The [evidence gallery](evidence/milestone-3/index.html) pairs reference and candidate screenshots at each required width. The [capture manifest](evidence/milestone-3/capture.json) hashes source and artifacts; it distinguishes this controlled fixture from unchanged [production milestone 2 references](milestone-2.md).

Before reconsidering, establish v5 entitlement and renewal ownership, execute v5 with real license states, close the visual differences, verify actual feature contracts against the inventory, and rerun application/PWA acceptance. Vuetify and Element Plus remain documentation-only alternatives; their accessibility and application bundle sizes are unproven. AnyChart replacement requires its own chart-by-chart validation. No Coach domains, context, Actions, GPT instructions, privacy or reflection delivery changed; no private GPT publication is needed.


| Focused validation | Result | Recorded duration |
| --- | --- | ---: |
| Independent frozen install, `run.u8T4ZQ` | Exit 0 | 0s, native cache |
| Prototype lint, `run.1dWp2F` | Exit 0 | 1s |
| Final reference/candidate builds, `run.HUJrCo` / `run.pUS2H1` | Exit 0 | 0s / 1s |
| Prototype workflows, `run.9PoeT0` | 16 passed, exit 0 | 38s |
| Final layout acceptance, `run.SteDsM` | 10 passed, exit 0 | 25s |
| Table scrolling and date/period values, `run.TJ9sQL` | 2 passed, exit 0 | 11s |

Durations use the check wrapper's recorded whole seconds and include tool cleanup. Full application/browser/PWA/backend validation and production verification belong to the committed candidate's release helper logs; these focused results alone do not claim deployment success.

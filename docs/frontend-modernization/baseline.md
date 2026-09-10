# Frontend modernization baseline

Recorded **2026-09-10** for milestone **1A–1D**; next is milestone 2, Vite migration. No dependency versions, application UI, API contracts, or Coach behavior changed. This milestone adds inventories, distributed dependency notices, durable synthetic screenshots, and two browser regression scenarios.

## Evidence and reproduction

Application source is `6c37dcb58bd351d90758d15f1776b02182275751`. Relative to validated frontend source `918989b`, master adds backend yoga catalog data, exercise-picture test expectations and notification swipe dismissal. The baseline was refreshed after integrating those changes: current screenshots and production measurements use `6c37dcb`. The capture manifest hashes the exact source and updated fixture; the eventual release gate validates the committed candidate independently.

- [Inventory and route/coverage matrix](inventory.md).
- [Source usage inventory](evidence/source-inventory.json), [installed dependencies/licenses](evidence/dependencies.json), and [production assets](evidence/assets.json).
- [Capture manifest and hashes](evidence/capture-manifest.json), [mobile diagnostics](evidence/screenshots/baseline-diagnostics-390.json), and [wide-layout diagnostics](evidence/screenshots/baseline-diagnostics-1280.json).

Use the installed lockfile dependencies and Python 3. The collector reads source, node_modules and an existing dist; it does not build. Generate notices before the measured production build, then collect again so asset hashes include the current notices. Do not run the collector against a test build and label it production.

```bash
scripts/check.sh frontend install --frozen-lockfile
python3 scripts/frontend-baseline.py
scripts/check.sh frontend build
python3 scripts/frontend-baseline.py
scripts/check.sh frontend lint
WEIGHT_CONTROL_E2E_PORT=4192 scripts/check.sh frontend test:e2e --grep 'modernization baseline'
```

Supply the two public production `VUE_APP_*` values through the established environment before a production measurement; do not commit their values. The release-artifact helper loads the production configuration and restores production dist after browser tests. Port 4192 was free in this session; the default 4173 belonged to another application. For a focused rerun with a current matching dist, use `scripts/check.sh frontend playwright test --grep 'modernization baseline'` with the same port override. Copy the `baseline-*.png` and `baseline-diagnostics-*.json` outputs from test-results into evidence/screenshots only after exit zero and visual review; update capture-manifest hashes and provenance when replacing references.

For an authorized release, after focused checks and the candidate commit:

```bash
JAVA_HOME=/home/jllado/.sdkman/candidates/java/21.0.2-tem WEIGHT_CONTROL_E2E_PORT=4192 \
  .agents/skills/release-plan/scripts/build-release-artifacts.sh "$PWD"
```

Use the local Java 21 installation appropriate to the machine. The gate coordinates both pipelines under one lock, checks the committed tree, runs all browser/backend tests and builds release artifacts. Do not duplicate full suites immediately before it. Follow the release skill for master integration, push, deployment and production verification; this command alone does not deploy.

## Health baseline

This session used Linux, **Node 22.22.2**, **Yarn 1.22.19**, Playwright **1.62.1**, Chromium **151.0.7922.34**, existing node_modules/Yarn caches, and local filesystem build caches. Nothing pins Node yet. Browserslist is `> 1%`, `last 2 versions`, `not dead`; the installed caniuse database is old and resolves targets such as Chrome 109/116–119 and Safari 16.6–17.1. These are historical build targets, not demonstrated browser compatibility. The suite exercises Chromium with Pixel 7 settings; Safari, Firefox and actual device acceptance remain gaps.

| Evidence | Result | Recorded stage duration |
| --- | --- | --- |
| Previous release `684157f`, source `918989b`, run.EzyjvU | Exit 0; release-script checks and full artifact gate | 13s scripts + 147s coordinated pipelines = 160s |
| Previous frontend run.gsmPjm | Exit 0; lint, 186 browser tests, production build | 2s lint; 134s test build/browser stage; 11s production build; install 0s |
| Previous backend run.eKUIuW | Exit 0; 454 test results reused by native Gradle up-to-date caching | Test stage 0s; production JAR stage 1s |
| Previous production run.vAxa8m | Exit 0; deployment and production verification | 95s deployment + 1s verification |
| Current focused lint run.H68Vo4 / run.u9bILs | Exit 0 | 2s each |
| Initial baseline probe run.RM4NVm | Exit 1; two focus-restoration assertions exposed an existing defect | 23s including test build |
| Baseline scenarios run.cC1e5r | Exit 0; 2 tests after recording the focus defect | 7s |
| Earlier screenshots run.cPlYAT | Exit 0; 2 tests, calendar transition settled before capture | 8s |
| Retained screenshots/integration check run.eGIFeM | Exit 0; 10 baseline and notification tests after master integration | 28s including test build |
| Production measurements run.hjpJDa / run.vEkazm | Exit 0; builds before final notice inventory | 11s / 10s |
| Earlier measured production build run.poze5r | Exit 0; complete runtime notices | 10s |
| Pre-gate production measurement run.UzrkSB | Exit 0 after master integration; complete runtime notices | 10s |
| Candidate `fb1a5f3`, run.9l057W | Exit 0; complete artifact gate, 196 browser tests and 456 backend tests, none failed/skipped | 13s script checks + 202s coordinated pipelines = 215s |
| Candidate frontend run.7qbzVM | Exit 0; final measured production artifacts retained in assets.json | 3s lint; 157s test build/browser stage; 11s production build |
| Candidate backend run.FTfV3h | Exit 0; test task executed, configuration cache reused, complete cleanup | 201s tests; 1s production JAR |

Log identifiers refer to local `tmp/checks/<run>/timings.tsv`; this table retains results even when temporary logs are removed. Stage durations include tool shutdown/cleanup; they are not CPU benchmarks. Historical Node/browser environment was not retained with the earlier gate, so those timings are context, not a controlled speed comparison. Current source hashes, environment, measurements and the completed candidate gate provide the reproducible baseline; the documentation correction after this run requires a fresh final-candidate gate before push. No old backend cached result is presented as a newly executed test.

Known findings are recorded separately from migration regressions:

- Builds pass with Webpack asset/entrypoint size warnings, outdated Browserslist data, and `fs.Stats` constructor deprecation warnings. Do not refresh the lockfile just to hide them in this milestone.
- Browser diagnostics have no page errors or failed requests in the captured workflows; PrimeVue warns that router-item support will change, and blocked worker registration is expected from Playwright configuration.
- Escape dismisses Pause or record, but focus is not restored to its flag trigger at either width. The new test records `pauseFocusRestored: false`; it still asserts keyboard opening and dismissal. Focus restoration needs an explicit fix/acceptance assertion in the shell migration.
- The baseline is representative, not an accessibility certification or a test of every CRUD combination; coverage gaps are listed below and in the route matrix.

## Performance baseline

[assets.json](evidence/assets.json) records every production output with raw bytes, independently gzipped bytes and SHA-256. These are artifact payload estimates, not observed network transfer, LCP, or CPU measurements; HTTP encoding/cache, API responses, fonts, Google sign-in and later chart requests affect real loads. Source maps are measured separately and are not initial navigation payload.

| Delivery group | Raw bytes | Gzip bytes | When needed |
| --- | ---: | ---: | --- |
| app JS | 591,946 | 121,506 | Every route |
| vendor JS | 2,000,196 | 544,935 | Every route |
| app CSS | 50,237 | 9,755 | Every route |
| vendor CSS | 201,885 | 24,676 | Every route |
| **Initial JS/CSS total** | **2,844,264** | **700,872** | Four references in index.html, including login |
| Chart.js chunk 471 | 194,232 | 65,529 | PrimeVue Chart imports chart.js/auto when mounted |
| Wins history chunk 737 JS + CSS | 4,803 | 2,018 | `/wins`, the only lazy route |

The measured HTML references no prefetch links. Most routed components are eagerly imported into the application graph. PWA precaching may subsequently download deferred assets; deferred means outside the initial HTML JS/CSS references, not guaranteed absent from all background traffic. Images, icon fonts, manifest, notices and generated worker files are itemized in assets.json.

Source-map attribution identifies PrimeVue (~1.30 MB), AnyChart (~1.04 MB), Chart.js (~0.41 MB), and Vue runtime-core (~0.31 MB) as the largest package sources. These are unminified source bytes, may include duplicate source contributions, and cannot be summed into compressed bundle savings. The complete package list is in assets.json.

Later opportunities: route-split eager screens, defer AnyChart status rendering, compare whether two chart engines are justified, remove unused Password/PickList registrations, and investigate the nested Vue versions pulled in by vuejs3-logger/vue-confetti. Do not assume nested installed dependencies all survive tree-shaking. Compare selective imports, actual chunks and payloads after Vite before choosing a UI library.

## Visual contracts and references

The [design guidelines](../design-guidelines.md) remain authoritative. Retained screenshots use synthetic API fixtures and an artificial SVG silhouette, with fixed Date `2026-09-10T12:00:00Z`; no production health data or user photos are included. Chromium uses Pixel 7 touch/mobile settings and DPR 2.625 at **390×900** and **1280×900 CSS pixels**. The latter captures the desktop responsive layout but is not a separate desktop-device certification. PNG dimensions may be larger because of DPR and element captures. CSS transitions are settled for dialogs; canvas animation and font rasterization can still vary, so these are reviewed visual references rather than exact golden-pixel assertions.

| Interface | Mobile | Wide layout | Contract |
| --- | --- | --- | --- |
| Shell/dashboard | [390](evidence/screenshots/baseline-dashboard-390.png) | [1280](evidence/screenshots/baseline-dashboard-1280.png) | Grouped navigation, aligned panels, concise mobile labels, separate dashboard date controls |
| Pause dialog | [390](evidence/screenshots/baseline-dialog-390.png) | [1280](evidence/screenshots/baseline-dialog-1280.png) | Flag entry point; centered outlined clock/wait action; filled green WIN/check and red MISS/times using shared equal 7rem buttons |
| History table | [390](evidence/screenshots/baseline-table-390.png) | [1280](evidence/screenshots/baseline-table-1280.png) | Intentional mobile column reduction, units, pagination and grouped row actions |
| Form/calendar | [390](evidence/screenshots/baseline-calendar-390.png) | [1280](evidence/screenshots/baseline-calendar-1280.png) | Date retained in history forms, visible focus ring and readable date overlay |
| Staged upload | [390](evidence/screenshots/baseline-upload-390.png) | [1280](evidence/screenshots/baseline-upload-1280.png) | Preview before persistence, stacked fields, Save then Cancel footer |
| Charts | [390](evidence/screenshots/baseline-charts-390.png) | [1280](evidence/screenshots/baseline-charts-1280.png) | Stable metric colors, legends, canvas sizing and responsive tabs |
| Progress photos | [390](evidence/screenshots/baseline-photos-390.png) | [1280](evidence/screenshots/baseline-photos-1280.png) | Selected-photo privacy message, orientation controls and readable carousel |

Visual review found the shared pause controls consistent with the existing Wins panel contract, readable form/footer controls and deliberate mobile table column reduction. Existing weight-history edit/delete controls use legacy green/yellow round variants; this capture records them and does not make them a new universal action standard. Compare future changes to the nearest shared control, not whichever unrelated legacy screen is easiest to copy. Keep Nova typography, colors, spacing and focus rings until an explicit foundation decision changes them.

New coverage exercises keyboard pause opening/dismissal, calendar opening, synthetic weight-photo preview, no persistence before save, cancellation clearing the staged image, and stored-photo rendering at both widths. Existing tests compare pause/Wins labels, icons, fill, colors, size and spacing at 390/575/640/960/1280 widths and verify charts after resize; preserve those assertions when selectors change.

## Gaps and next acceptance work

| Gap | Required follow-up |
| --- | --- |
| Old-build → new-build service-worker upgrade | Milestone 2 must serve both builds at the same origin/scope with real workers; prove update prompt, activation, one reload, replaced caches, offline shell and notification links through login. Worker VM/request tests and the permission-prompt exception do not establish this. |
| Dialog focus return and full keyboard traversal | Fix the recorded focus defect and assert return, trapping, visible focus and tab order in the shell migration. |
| Calendar/Dropdown/MultiSelect keyboard operation | New coverage opens the date overlay, but complete keyboard selection and multiselect chip removal are not protected; add in the decision spike/affected migration slice. |
| Photo upload persistence and accessibility | Preview/cancel is covered; multipart persistence, orientation switching/swiping and accessible image alternatives need focused acceptance. PhotoHistory has missing image alt text and a mismatched front-label association. |
| Charts and assistive technology | Resize/rendering is covered; canvas alternatives and screen-reader interpretation are not established. |
| Habit CRUD, medication edit/delete persistence and settings combinations | Dedicated medication tests cover recurring times, creation, immediate dose logging and responsive management; reminder tests cover taking/snoozing. They do not cover every edit/delete path; see route matrix. |
| Unused Password/PickList | No active workflow needs new tests for these registrations; prove removal or a real replacement requirement before prototyping ordered selection. |
| Browser diversity | Add relevant Safari/Firefox/device checks before final acceptance; current desktop-width reference retains mobile emulation settings. |

Coach plan/todo were assessed: this milestone changes no domains, context, Actions, GPT instructions, privacy, reflection or delivery sequencing. Existing BEHAVIOR pause/decision contracts, private Coach navigation and selected-photo disclosure remain the references for later migration. No Coach publication is required.

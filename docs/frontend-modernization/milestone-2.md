# Milestone 2: Vite migration

Implemented September 12, 2026, from `6bdaf31` (the final Vue CLI baseline for this migration).

## Toolchain and boundaries

Node 24.21.0 LTS is pinned in `.nvmrc`, package engines, and both frontend Dockerfiles; Yarn remains 1.22. Vite 8.3.0, plugin-vue 6.0.8, vite-plugin-pwa 1.3.0, and Workbox 7.4.1 replace Vue CLI. Vue 3.2.31, PrimeVue 3.38.1, Nova, PrimeFlex, PrimeIcons, Options API, routes, and HTTP contracts are unchanged. ESLint 8 remains pinned and runs directly without Babel or Vue CLI integration.

`index.html` is the root entry; public files retain their root URLs. Vue imports now include `.vue`, Day.js plugins use ESM imports, and `@` still resolves to `src`. Vite handles CSS imports and asset hashes under `/assets/`; Caddy explicitly proxies that path before its SPA fallback. The existing development configuration had no API proxy; Vite's port 8080 development server now proxies `/api` to the local Spring backend on port 8081 (`scripts/check.sh backend bootRun --args='--server.port=8081'`). Production still uses Caddy's `/api` proxy.

Only `VITE_GOOGLE_CLIENT_ID` and `VITE_CHATGPT_COACH_URL` are public build inputs. The former `VUE_APP_*` names were migrated through source, examples, local release configuration, Docker/Compose, Ansible, test builds, and release helpers. Backend credentials remain unprefixed and are never included in Vite's exposed environment. Test builds use synthetic public values; the release gate always rebuilds production assets with production values after browser acceptance.

## Browser and polyfill policy

The explicit minimum syntax targets are Chrome/Edge 111, Firefox 114, and Safari 16.4, including their mobile equivalents. These engines support the ES modules, dynamic imports, modern CSS, and inert form behavior used by the application. Internet Explorer and earlier engines are outside support. Chromium is exercised automatically; this milestone does not claim device-level Safari/Firefox verification.

Vite replaces Babel's syntax transformation. `core-js` 3.33.2 remains pinned. `src/polyfills.js` explicitly retains the eight modules emitted by the final Vue CLI build: array push, typed-array toReversed/toSorted/with, DOMException stack, and URLSearchParams delete/has/size. The retained legacy source maps establish this list; importing all of core-js/stable added about 8% compressed JavaScript/CSS and was rejected. Other application APIs (including Object.fromEntries, Promise.allSettled, and crypto.randomUUID) are native at the declared browser floor. Newer API usage must update this policy explicitly; Vite does not inject API polyfills. The old drifting Browserslist queries and Babel configuration are removed; `vite.config.mjs` is the browser-target source of truth.

Compatibility references: [Vite build targets](https://vite.dev/guide/build), [Vite requirements](https://vite.dev/guide/), [Node releases](https://github.com/nodejs/node/releases), and [PWA registration configuration](https://vite-pwa-org.netlify.app/guide/register-service-worker).

## PWA acceptance

The manifest remains `/manifest.json`, with the same identity `/`, scope `/`, start URL `/`, names, icons, and WIN/MISS shortcuts. The generated worker remains `/service-worker.js`, imports the unchanged `/push-service-worker.js`, and retains the `weight-control` Workbox cache prefix. Updates wait for the existing **Update app** action and `SKIP_WAITING` message; activation claims clients, and the existing controller-change listener reloads once. Precache revisioning removes obsolete build assets. The offline navigation fallback serves the application shell and excludes `/api/`; authenticated health responses are not cached.

`tests/pwa/upgrade.spec.js` serves a freshly rebuilt Vue CLI baseline, installs its real worker, then switches to the Vite output at the same origin and scope. It checks the waiting update, user action, one reload, unchanged registration/manifest, removed legacy assets, new cached assets, no cached API responses, an offline deep link, and a push notification's action surviving login. Push delivery uses Chromium's service-worker protocol, and the click is dispatched to the real worker; Google login and backend data use synthetic fixtures. No production health data or external push service is involved. The test preserves the browser's subscription state; its isolated profile starts without an external subscription. Existing push-subscription and notification tests continue to cover the app's subscription calls and action semantics.

The PWA suite uses full Chromium headless mode (`channel: chromium`), because the headless shell does not deliver persistent notifications in this scenario. A second test starts Vite, verifies compiled Vue modules and the API proxy against a local fixture, and triggers a development reload through its HMR connection.

Run through `scripts/check.sh frontend test:pwa`; `scripts/build-legacy-pwa.cjs` rebuilds pinned revision `6bdaf31` in ignored `tmp/pwa-legacy` with its own frozen lockfile on every invocation. Legacy dependencies are acceptance fixtures only, never production inputs. The release gate includes this acceptance stage before the final production build. No tests are skipped or retried.

## Validation and measurements

Focused Vite checks cover baseline dashboard, tables, forms, calendar, uploads, photos and charts at 390/1280px, plus workout saving at 390/575/640/960/1280px. Full acceptance additionally requires lint, real PWA upgrade, release-script safeguards, the complete browser/backend suites, production artifact checksums, deployment, and production verification. Stage timings are retained under `tmp/checks/`.

Coach domains, context, Actions, GPT instructions, privacy, and reflections are unchanged. No GPT publication or backend migration is required. Runtime third-party notices retain existing library notices and update the bundled Workbox modules; the retired vue-loader helper notice is removed. Existing AnyChart eval and large-chunk build warnings remain visible; UI-library replacement and broad code splitting belong to later milestones.

Retained [capture metadata and bundle measurements](evidence/milestone-2/capture.json) accompany the mobile/desktop images. The same synthetic fixture scenarios as milestone 1 retain Nova styling, action alignment, icons, focus, wrapping, and overflow assertions. Measurement includes all emitted JavaScript/CSS, workers and deferred chunks, excludes source maps, and uses production-mode builds with matching synthetic public settings.

| Build | JS/CSS files | Raw bytes | Independent gzip bytes |
| --- | ---: | ---: | ---: |
| Vue CLI `6bdaf31` | 10 | 3,109,305 | 785,522 |
| Vite milestone 2 | 8 | 3,243,811 | 792,154 |

Compressed JS/CSS increased 0.84%; raw size increased 4.3%. The lazy history route and Chart.js chunk remain separate. This is a bundle comparison, not a claim about network transfer or user-perceived latency.

The 14 retained visual captures match the baseline dimensions; changed pixels above luminance tolerance 8 range from 0% to 0.27%. Dialog, upload, and chart captures are identical at that tolerance. Mobile table scrolling remains intentional. Manual review checked dashboard, dialog, calendar, and upload control alignment at the retained widths.

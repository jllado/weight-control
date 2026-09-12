# UI-library evaluation

Local synthetic application for milestone 3; excluded from the production application and deployment source sync. Its dependency manifest and lockfile are independent of the production application.

The reference renders PrimeVue 3.38.1 with Nova. The candidate currently renders PrimeVue 4.5.5 with a customized Lara preset, testing the theme/API boundary also required for PrimeVue 5. Both use Vue 3.5.42 to isolate the UI-library comparison; the reference is a controlled component comparison, not the complete production application or a Vue compatibility guarantee.

Run from the repository root with Node 24.21.0 and Yarn 1.22:

```bash
scripts/check.sh frontend --cwd tools/ui-library-spike install --frozen-lockfile
scripts/check.sh frontend --cwd tools/ui-library-spike lint
scripts/check.sh frontend --cwd tools/ui-library-spike build --mode reference
scripts/check.sh frontend --cwd tools/ui-library-spike build
scripts/check.sh frontend --cwd tools/ui-library-spike test
# After reviewing the fresh screenshots:
scripts/check.sh frontend --cwd tools/ui-library-spike capture
```

For manual inspection, `yarn --cwd tools/ui-library-spike serve --mode reference --port 4381` runs the reference; omit `--mode reference` and use port 4382 for the candidate. The server binds to loopback. Saves go only to the local `/simulation/measurements` middleware, which waits 1.5 seconds and returns success or a selected failure without persisting data. Photo previews use local object URLs.

Tests serve fresh production builds at ports 4381/4382, one browser worker, zero retries, and retain screenshots and measurements under `tmp/ui-library-spike/`. The lazy Chart.js component proves a replacement for the deprecated PrimeVue chart wrapper without adding a paid chart dependency. It does not establish parity for existing AnyChart charts.

This is an evaluation harness, not a shared production component abstraction. The two entrypoints register matching controls against a shared fixture; Vite aliases all reference transitive PrimeVue imports to v3 to prevent cross-version mixing. Production routes, dependencies, lockfile, notices, PWA and API contracts remain unchanged.

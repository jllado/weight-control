# Release Improvement Evaluation

## Baseline and conditions

Baseline 1 ran on 2026-09-08 at revision `37d8c37b1a9033bcec471470a72ea8117f03d388`, using the unchanged sequential release gate. All stages exited zero: 389 backend tests, 144 browser tests, and 12 safeguard tests passed.

| Stage | Seconds |
| --- | ---: |
| Safeguard tests | 5 |
| Dependency installation | 0 |
| Frontend lint | 2 |
| Browser-test build and complete browser suite | 160 |
| Production frontend build | 11 |
| Complete backend suite | 133 |
| Backend JAR | 0 |
| Gate total | 311 |

The observation process finished after 313 seconds because sampling continued until it detected gate completion. Use gate timings for comparisons.

Hardware: Intel Core i7-12700H, 20 logical CPUs, approximately 31 GiB RAM. Tools: Node 22.22.2, Yarn 1.22.19, Gradle wrapper 9.0, Gradle launcher Java 26.0.1, test toolchain Java 21.0.2, Playwright 1.62.1, Docker Engine 29.4.3, MariaDB image 11.8.

Dependencies, Docker images, Gradle configuration, and compiled classes were warm. `scripts/check.sh backend cleanTest` cleared test outputs first; the gate actually executed the backend tests rather than reusing `UP-TO-DATE` results. The JAR was up to date. Browser execution used two workers across the two existing test files, with tests inside each file sequential.

## Resource observations

Sampling every approximately five seconds covered the entire gate. Host CPU averaged 16.6% and peaked at 50% across all logical CPUs; available memory never fell below 6.9 GiB. Host swap counters increased by approximately 55 MiB read and 397 MiB written. At most 13 Docker containers were running; peak sampled aggregate Docker CPU was approximately 1.44 cores.

These are whole-host measurements, including unrelated applications and containers. Existing swap usage and background activity mean they do not prove the gate alone caused memory pressure or guarantee sufficient capacity for concurrent runs. Compare candidate runs under similar conditions and report this limitation.

Local raw evidence: `tmp/release-experiments/baseline-1/` and `tmp/checks/run.dNkWpA/`. Raw logs are ignored local artifacts; this document preserves the portable summary.

## Output and isolation review

- Frontend writes `dist/` and browser artifacts; backend writes `backend/build/` and uses private test databases with dynamic Docker ports.
- Backend resource processing and the frontend read `src/assets/logo.png`; neither pipeline writes it.
- Browser API calls are mocked per page or context; the browser suite does not depend on the backend test database or application server.
- The release manifest is shared gate output and must be written only by the outer gate after both pipelines pass.
- The outer validation lock must span both pipelines and cleanup; native build caches remain enabled and concurrent Gradle invocations in one worktree remain prohibited.
- Both test files use per-test browser contexts and locally scoped mock state; there are no shared `beforeAll`/`afterAll` hooks or declared serial groups. VM service-worker simulations create fresh state per invocation.
- All seven screenshot call sites that used fixed paths now use Playwright's per-test output directories instead of fixed paths under `tmp/`, preserving isolated evidence without changing assertions.
- Failure and interruption tests must cover the coordinator, child pipeline shells, active commands, cleanup, and unrelated processes. The coordinator drains active stages to normal completion on cancellation, so Gradle can finish worker and database shutdown before the lock is released. No subsequent stage starts after observing cancellation.

## Experiment protocol and failure criteria

1. Keep default execution unchanged; expose separate opt-in pipeline-concurrency and browser-parallelism modes, never an implicit combined mode.
2. Run a fresh sequential baseline on the final experiment-harness revision, then two complete measured runs of each candidate separately, clearing only backend test outputs before each full-gate measurement.
3. Use the same app/test sources, toolchain, dependency caches, Docker images, and machine. Preserve both frontend builds, all assertions, viewport and service-worker settings, and zero retries.
4. The pipeline experiment starts one frontend pipeline and one backend pipeline inside a single locked gate. Operations inside each pipeline remain ordered; artifact publication requires both to pass.
5. The browser experiment uses two workers with per-file test parallelism, while frontend and backend pipelines remain sequential.
6. Stop adoption for any test failure, crash, OOM, stale/partial manifest, source mutation, leaked output writer, lost lock, or interference with unrelated processes. Investigate failures before rerunning; do not increase retries or weaken checks.
7. Treat sustained available memory below 2 GiB or substantially increased swap activity as resource concerns requiring investigation, even if tests pass. Host background activity must be reported rather than attributed to the experiment without evidence.
8. Require repeatable passing runs and a consistent timing improvement beyond ordinary measurement noise; speed never compensates for a correctness failure.
9. Verify the sequential fallback again after experiments. Record a separate user adoption decision for each candidate before changing defaults; evaluate combined operation only if both are approved individually.

## Adoption status

Both isolated experiments and the sequential fallback passed complete validation. Default adoption remains deferred in line with the requested safety-first plan; no concurrency default is enabled. Two passing runs are useful evidence, not a guarantee against future flakes or host contention. A separate adoption decision is still required, and combined execution remains unavailable.

## Running the isolated experiments

From a clean committed worktree, run one command at a time:

```bash
# Existing behavior and fallback (the mode can also be omitted).
.agents/skills/release-plan/scripts/build-release-artifacts.sh "$PWD" sequential
# Frontend and backend overlap; browser settings remain unchanged.
.agents/skills/release-plan/scripts/build-release-artifacts.sh "$PWD" parallel-pipelines
# Pipelines stay sequential; browser tests use two fully parallel workers.
.agents/skills/release-plan/scripts/build-release-artifacts.sh "$PWD" parallel-browser
```

These commands validate and build local artifacts; they do not push or deploy. Combined mode is deliberately unavailable. Use `scripts/check.sh backend cleanTest` before each timing comparison to force actual backend test execution while retaining dependency and compilation caches.

For pipeline interruption or failure, the coordinator requests cancellation and waits for active stages to finish normally before releasing the outer validation lock. This can take as long as the active test suite; it prevents detached Gradle workers from writing after cancellation. Later stages are skipped and readiness is withheld. Wait for exit rather than repeatedly interrupting or starting a replacement run. A hung stage intentionally keeps the lock; forced process termination is not part of the experiment's safety guarantee.

Each pipeline records its own ordered stage logs and timing table under `tmp/checks/`; the outer `parallel-pipelines.log` lists those directories. The outer timing measures wall time and must not be calculated by adding overlapping pipeline durations. Both frontend builds, all backend checks, and final source/tree/checksum validation remain required.

## Concurrent workspace change during evaluation

The first harness baseline at `99bb020` passed all 389 backend tests, 144 browser tests, and 19 safeguards, but correctly exited 1 because another task merged `b259ef5` into `master` before artifact publication. Its stage timings are diagnostic only, not a successful release result (`tmp/checks/run.So94WJ/`).

An isolated baseline at `b259ef5` then failed the existing dashboard trend-label mobile overflow assertion (623px content at a 393px viewport); 149 of 150 browser tests passed. A focused run with tracing reproduced the failure. Rebuilding the original candidate at `99bb020` and rerunning that assertion passed. This is a baseline failure on the unrelated merge, not evidence of a concurrency regression, and that revision is excluded from these comparisons.

Remaining experiments use the isolated `release-performance-isolated` branch, browser port 4187, and separate dependency/build directories. They preserve the original candidate's application behavior; the additional source change isolates the remaining screenshot paths. The unrelated merged revision requires its own fix and validation before release. No result here validates that feature or authorizes deployment.

## Completed measurements

These six runs used revision `3789c05`, port 4187, unchanged application/test sources and tools, and actual backend test execution after `cleanTest`. Every gate exited zero and passed 389 backend tests, 144 browser tests, and 19 safeguards, with no skipped backend tests or browser retries. Dates and stage logs use UTC; the later runs occurred after midnight on September 9 in Madrid.

| Mode | Gate seconds | Browser build + tests | Backend tests | Gate log directory |
| --- | ---: | ---: | ---: | --- |
| Initial isolated baseline | 331 | 155 | 149 | `run.JzwBt5` |
| Parallel pipelines, run 1 | 202 | 181 | 151 | `run.QMF2Qg` |
| Parallel pipelines, run 2 | 180 | 156 | 137 | `run.gRU7k8` |
| Parallel browser, run 1 | 242 | 88 | 128 | `run.R5pKDW` |
| Parallel browser, run 2 | 241 | 88 | 130 | `run.LYf4Gx` |
| Sequential fallback | 303 | 152 | 127 | `run.zGKlvn` |

Gate seconds use recorded stage durations; overlapping child durations are not added. Sampling wrappers observed completion up to four seconds later. All gate directories are under the isolated worktree's `tmp/checks/`; resource samples and portable JSON summaries are under `tmp/release-experiments/{candidate-baseline,pipelines-1,pipelines-2,browser-1,browser-2,sequential-fallback}/`.

The initial isolated baseline compiled backend classes in the new worktree and saw more background Docker activity. The final fallback is the closer comparison for warm compilation caches: pipeline overlap saved approximately 33–41% of gate wall time, and browser-only parallelism saved approximately 20%. Browser build/suite duration fell from 152 seconds to 88 seconds. These figures exclude deployment and do not predict production rollout duration.

| Mode | Minimum available RAM, MiB | Mean / peak host CPU, % | Swap read / written, MiB | Peak container count |
| --- | ---: | --- | --- | ---: |
| Initial isolated baseline | 7970 | 19.5 / 42.0 | 11.1 / 1028.5 | 17 |
| Parallel pipelines, run 1 | 9131 | 31.2 / 81.4 | 252.2 / 280.6 | 13 |
| Parallel pipelines, run 2 | 9098 | 18.2 / 35.7 | 8.4 / 72.3 | 13 |
| Parallel browser, run 1 | 9518 | 15.2 / 34.3 | 5.3 / 0 | 13 |
| Parallel browser, run 2 | 9849 | 15.9 / 43.5 | 6.6 / 0 | 13 |
| Sequential fallback | 9392 | 11.8 / 33.3 | 6.4 / 41.6 | 13 |

The first overlap run had markedly more CPU and swap activity than its repeat. Comparing the repeat and fallback did not reproduce that spike; whole-host measurements cannot attribute it to this gate or unrelated work. Available memory remained above the investigation threshold throughout, and no candidate run reported a test failure, crash, or OOM. Host variability and the small sample support retaining opt-in status rather than enabling defaults automatically.

## Real cancellation and safety verification

In addition to the 19 disposable-repository safeguard tests, a real parallel gate was interrupted after observing an actual Gradle test worker. The competing validation command returned 75 while that worker was active. The frontend stopped before browser testing; the backend completed its active test stage normally and did not build the JAR. The outer gate exited 143 after approximately 140 seconds, the observed worker no longer existed, no backend test worker remained in the worktree, an unrelated process stayed alive, and no artifact readiness marker existed.

Evidence: `tmp/release-experiments/real-cancellation/summary.json` and `tmp/checks/run.NsZfJU/` in the isolated worktree. The subsequent full sequential fallback passed and republished valid artifacts, confirming lock reuse after cancellation. No forced kill or shared Gradle daemon shutdown was used.

## Closed evaluation and future adoption

The requested implementation and independent evaluation are complete. Sequential defaults and existing release authorization boundaries are preserved; operational instructions describe only explicit opt-in modes. Combined evaluation is deferred because neither mode has been adopted as a default. Future adoption must be a separate decision using these results and current host conditions, followed by combined resource validation if both are selected.

The final documentation commit must pass the normal complete gate before this branch is pushed; native Gradle cache reuse is allowed for that non-benchmark validation because executable sources are unchanged.

# Release Improvements TODO

This checklist tracks the [Release Improvements Plan](plan.md). Reliability takes priority over speed; independent and combined evaluation are complete, the validated combined release mode is adopted, and sequential validation remains available as a fallback. See the [results](results.md) for timings, limitations, and cancellation evidence.

## 1. Completed safeguards and cleanup

- [x] Clarify focused checks before the candidate commit and the full release gate before pushing or deploying.
- [x] Add worktree validation and repository-wide deployment locks.
- [x] Handle interruption and wait for owned process cleanup before releasing locks.
- [x] Validate candidate trees and checksums, and publish artifact readiness only after successful validation.
- [x] Record stage durations, exit statuses, and log locations.
- [x] Correct Spring/MariaDB integration-test shutdown order while preserving database isolation.
- [x] Validate and deploy the foundation in commit `b6e809a`.

## 2. Baseline and resource assessment

Evidence and experiment criteria: [evaluation results](results.md).

- [x] Record current sequential stage timings, suite counts, tool versions, and cache conditions.
- [x] Measure CPU, memory, and Docker load during frontend and backend validation.
- [x] Identify shared outputs and process ownership that could prevent safe concurrency.
- [x] Define an isolated experiment and failure criteria for each proposed improvement before changing execution settings.

## 3. Optional frontend/backend concurrency experiment

- [x] Coordinate both pipelines within one release-gate invocation while retaining the outer validation lock.
- [x] Preserve sequential steps within each pipeline and keep both frontend builds.
- [x] Test success, one-pipeline failure, interruption, lock contention, and rejection of partial or stale artifacts.
- [x] Confirm all owned processes finish cleanup before lock release and unrelated processes remain unaffected.
- [x] Compare repeated complete runs against the sequential baseline with equivalent inputs and cache conditions.
- [x] Document results and record the user's 2026-09-09 adoption approval; preserve sequential validation as a fallback.

## 4. Optional browser parallelism experiment

- [x] Audit fixtures, hooks, mocks, shared state, output paths, ports, and service-worker behavior for isolation.
- [x] Confirm test independence and isolate screenshot outputs without weakening assertions or coverage.
- [x] Evaluate two workers in an isolated experiment while keeping the release pipelines sequential.
- [x] Repeat the complete browser suite and investigate every failure, crash, or sign of resource contention.
- [x] Compare timings and reliability against the current browser configuration without increasing retries to mask failures.
- [x] Document results and adopt two browser workers for the release gate following the user's approval; retain standalone browser defaults and the release fallback.

## 5. Adoption checks

- [x] Validate combined execution after individual adoption approval, including repeated full gates and resource measurements.
- [x] Preserve a documented sequential fallback and verify it still passes the full gate.
- [x] Update operational instructions only for approved behavior, keeping all release checks and authorization boundaries intact.

## 6. Complete adoption and production integration

- [x] Record the user's 2026-09-09 approval to finish adoption and release to production.
- [x] Validate combined execution repeatedly and verify the integrated sequential fallback with actual backend test execution.
- [x] Adopt the validated release default while preserving the explicit sequential fallback and lock/cleanup safeguards.
- [ ] Integrate the completed checklist and implementation into `master`, push, deploy, and verify production.

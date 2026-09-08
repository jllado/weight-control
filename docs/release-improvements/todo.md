# Release Improvements TODO

This checklist tracks the [Release Improvements Plan](plan.md). Reliability takes priority over speed; unchecked work is deferred, and sequential validation remains the default.

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
- [ ] Confirm all owned processes finish cleanup before lock release and unrelated processes remain unaffected.
- [ ] Compare repeated complete runs against the sequential baseline with equivalent inputs and cache conditions.
- [ ] Document results and obtain a separate user decision before enabling concurrency by default; otherwise retain sequential validation.

## 4. Optional browser parallelism experiment

- [x] Audit fixtures, hooks, mocks, shared state, output paths, ports, and service-worker behavior for isolation.
- [x] Remove ordering dependencies without weakening assertions or coverage.
- [ ] Evaluate two workers in an isolated experiment while keeping the release pipelines sequential.
- [ ] Repeat the complete browser suite and investigate every failure, crash, or sign of resource contention.
- [ ] Compare timings and reliability against the current browser configuration without increasing retries to mask failures.
- [ ] Document results and obtain a separate user decision before enabling parallel browser tests by default; otherwise retain the existing configuration.

## 5. Adoption checks

- [ ] If both experiments are approved individually, evaluate their combined resource usage before enabling them together.
- [ ] Preserve a documented sequential fallback and verify it still passes the full gate.
- [ ] Update operational instructions only for approved behavior, keeping all release checks and authorization boundaries intact.

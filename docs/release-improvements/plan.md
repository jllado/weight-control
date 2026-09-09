# Release Improvements Plan

## Purpose

Track optional improvements to validation speed while prioritizing reliability, complete test coverage, and safe releases. The [TODO](todo.md) is the source of truth for progress.

The user approved completing adoption and production release on 2026-09-09 after reviewing the independent experiments. Combined release validation is now the default after repeated complete combined gates and an integrated sequential fallback passed; the explicit `sequential` mode remains available. Deployment still requires an explicit release invocation.

## Completed foundation

Commit `b6e809a` implemented:

- Focused development checks followed by the complete release gate after the candidate commit.
- A worktree validation lock and a repository-wide deployment lock, with interruption handling and cleanup before lock release.
- Candidate-tree checks, artifact checksums, and readiness publication only after successful validation.
- Stage logs and timing reports under `tmp/checks/`, with success reported only after process exit.
- Spring-managed databases for five integration-test configurations, preserving isolation and correcting shutdown order.

During that release, backend validation took 2m16s versus 4m41s in an earlier run; shutdown took about one second without connection timeouts. These are historical observations, not guaranteed performance targets. The release passed 389 backend tests, 144 browser tests, and 12 safeguard tests; future comparisons must use the current suite.

Operational commands and safeguards remain authoritative in the [project guide](../project-guide.md) and [release skill](../../.agents/skills/release-plan/SKILL.md).

## Evaluated experiment: concurrent frontend and backend validation

Evaluate running the two independent pipelines concurrently inside one release-gate invocation, while preserving sequential operations within each pipeline.

- Assess CPU, memory, Docker load, and output ownership before experimenting.
- Retain the outer validation lock; do not run separate release or Gradle invocations against the same outputs.
- Preserve dependency installation, lint, browser-test build, browser tests, and production frontend build in their required order.
- Require both pipelines to pass before publishing artifact readiness, pushing, or deploying.
- Verify failures and interruptions stop and reap owned processes before releasing locks; preserve unrelated processes and prevent partial artifacts from becoming ready.
- Retain the sequential configuration as the fallback.

## Evaluated experiment: parallel browser tests

Evaluate two browser workers only after auditing test isolation. Do not change current browser settings until a separate adoption decision is made.

- Review mutable fixtures, hooks, mocks, filesystem outputs, service workers, ports, and test ordering assumptions.
- Make independent tests explicitly isolated before considering file splitting or parallel execution.
- Preserve all assertions, viewport coverage, authentication flows, and notification checks.
- Repeat complete suite runs to check for interference, flakiness, crashes, and resource contention.
- Retain the sequential configuration as the fallback; do not hide failures with additional retries or reduced coverage.

## Evaluation and adoption

Record measurements, isolation findings, experiment criteria, and adoption decisions in the [evaluation results](results.md).

Evaluate each improvement separately before considering them together. Record a current sequential baseline and compare equivalent runs on the same machine, revision, toolchain, and cache conditions; distinguish actual test execution from cached results.

Adoption requires preserved coverage, correct failure and interruption handling, repeatable passing checks, and measured improvement without reliability regressions. Record results and a separate user decision before enabling concurrency by default. If results are inconclusive, retain sequential validation.

No application APIs, Coach contracts, or production database schemas change. Evaluation exposed and fixed a dashboard canvas resize overflow; the regression test preserves mobile and desktop coverage.

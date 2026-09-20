---
name: release-plan
description: Implement an approved Weight Control plan, integrate it into master, and deploy it to production. Use only when the user explicitly invokes `$release-plan`.
---

# Release Plan

At the beginning, give a brief, rough time range for completing the requested work, including implementation, validation, deployment, and verification, based on the available context.

Use a browser only for pre-release functional QA; deployment verification uses read-only commands. Proceed with the authorized release workflow without asking for confirmation.

Explicit invocation authorizes pushing `master` and running `infra/ansible/deploy-app.yml`; never run provisioning, backup, or restore operations. Read [release context](references/release-context.md) before acting and inspect all dynamic Git and deployment state live.

## Synchronize

1. Confirm the current worktree is clean; preserve existing changes and request direction if it is not.
2. Record the current branch, locate the `master` worktree with `git worktree list --porcelain`, and fast-forward it with `git -C "$master_worktree" pull --ff-only origin master`.
3. When the current branch is not `master`, merge local `master` into it. Resolve only in-scope conflicts; stop for unrelated conflicts.

## Implement and validate

1. Implement only the approved plan; finish source changes before validation.
2. Before editing, name the exact validation commands required by the plan and changed areas. Run focused checks through `scripts/check.sh frontend <yarn arguments>` or `scripts/check.sh backend <Gradle arguments>`; keep checks sequential and never bypass the lock with raw build commands. Use lint and focused browser coverage for affected frontend behavior, targeted Gradle tests for backend behavior, relevant migration checks for persistence, and diff/link or command validation for documentation.
3. After a failure, diagnose it and rerun the affected checks first. Pass focused checks before committing. Use the complete release gate only for shared infrastructure, authentication or authorization, shared data contracts, dependency or toolchain versions, build/deployment/PWA changes, or an approved plan that explicitly requires it. If risk classification is unclear, use the complete gate; otherwise build deployable artifacts with the `artifacts` gate profile.
4. Wait for every process to exit, including cleanup. If a lock is occupied, wait for the original run; never start another writer or terminate an unrelated process.
5. Report success only after exit zero. Use recorded stage timings to distinguish checks, cleanup, artifact building, and deployment; native tool caching is allowed, custom test skipping is not.

## Commit and integrate

1. Review the final diff, stage only implementation files, and make one concise commit. Record its SHA as `feature_commit`, unless the plan identifies an earlier feature commit.
2. Fast-forward local `master` again. If it advanced, merge it into a non-master current branch and rerun the selected checks before integration.
3. Run `"$current_worktree/.agents/skills/release-plan/scripts/build-release-artifacts.sh" "$current_worktree" artifacts` after the final candidate commit, or its complete profile when the risk rules require it. Both profiles verify the clean committed revision, rebuild production artifacts, and record checksums; only the complete profile runs the full lint, browser, PWA, and backend suites. If a required gate fails, fix the failure, pass focused checks, commit the correction, and rerun the selected gate. Rebuild if the candidate changes.
4. If needed, merge the current branch into the master worktree with `git -C "$master_worktree" merge --no-ff "$current_branch"`; otherwise keep the commit on `master`.
5. Push with `git -C "$master_worktree" push origin master`. If the remote advances, resynchronize, reintegrate, rerun checks, rebuild artifacts, and retry.

## Deploy and verify

1. After a successful push, run `"$master_worktree/.agents/skills/release-plan/scripts/deploy-production.sh" "$feature_commit" "$current_worktree"`. The helper completes pending Ansible setup, installs required collections inside the project virtualenv, and validates or creates the ignored inventory from deployment environment values before it deploys.
2. The helper holds a repository-wide deployment lock and the artifact worktree validation lock, deploys from `master`, and verifies production. A duplicate invocation exits without deploying; wait for the original run. Report any failure with the pushed master commit; do not roll back automatically.

## Independent release verification

Run `python3 scripts/verify-deployment.py https://weightcontrol.devjllado.com "$(git rev-parse 'HEAD^{tree}')"` from the released checkout. Exit zero requires the expected tree in frontend HTML and backend `/api/version`, frontend HTTP 200, unauthenticated `/api/auth/me` HTTP 403, and the existing service-worker/push-worker checks. The command retries for up to approximately two minutes and performs only HTTP GET requests; no browser, login, deployment, or notifications are involved.

The artifact gate embeds the candidate source tree into frontend and JAR builds before recording checksums; identical source trees remain valid across integration merges. Record the pushed commit, expected/observed tree, and readiness result separately from pre-release functional QA. Run verification tests through `scripts/check.sh scripts`.

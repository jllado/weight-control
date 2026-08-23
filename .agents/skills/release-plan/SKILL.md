---
name: release-plan
description: Implement an approved Weight Control plan, integrate it into master, and deploy it to production. Use only when the user explicitly invokes `$release-plan`.
---

# Release Plan

Explicit invocation authorizes pushing `master` and running `infra/ansible/deploy-app.yml`; never run provisioning, backup, or restore operations. Read [release context](references/release-context.md) before acting and inspect all dynamic Git and deployment state live.

## Synchronize

1. Confirm the current worktree is clean; preserve existing changes and request direction if it is not.
2. Record the current branch, locate the `master` worktree with `git worktree list --porcelain`, and fast-forward it with `git -C "$master_worktree" pull --ff-only origin master`.
3. When the current branch is not `master`, merge local `master` into it. Resolve only in-scope conflicts; stop for unrelated conflicts.

## Implement and validate

1. Implement only the approved plan.
2. Run exactly its required checks, concurrently when independent; reuse a passing check only while the candidate tree and relevant inputs are unchanged. The release-artifact helper is the canonical clean-worktree, validation, and production-build gate.
3. Fix every required-check failure before committing, pushing, or deploying.

## Commit and integrate

1. Review the final diff, stage only implementation files, and make one concise commit. Record its SHA as `feature_commit`, unless the plan identifies an earlier feature commit.
2. Fast-forward local `master` again. If it advanced, merge it into a non-master current branch, rerun required checks, and rebuild artifacts after committing the final candidate.
3. Run `"$current_worktree/.agents/skills/release-plan/scripts/build-release-artifacts.sh" "$current_worktree"` after the final candidate commit. It verifies the clean committed revision, runs frontend lint and E2E checks, rebuilds the production frontend, runs backend tests, builds the release JAR, and records artifact checksums. Rebuild if the candidate changes.
4. If needed, merge the current branch into the master worktree with `git -C "$master_worktree" merge --no-ff "$current_branch"`; otherwise keep the commit on `master`.
5. Push with `git -C "$master_worktree" push origin master`. If the remote advances, resynchronize, reintegrate, rerun checks, rebuild artifacts, and retry.

## Deploy and verify

1. After a successful push, run `"$master_worktree/.agents/skills/release-plan/scripts/deploy-production.sh" "$feature_commit" "$current_worktree"`.
2. The helper waits for local deployments, deploys from `master`, and verifies production. Report any failure with the pushed master commit; do not roll back automatically.

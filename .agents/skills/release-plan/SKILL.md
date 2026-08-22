---
name: release-plan
description: Implement an approved Weight Control plan from Git synchronization through validation, commit, master integration, push, production deployment, and HTTP verification. Use only when the user explicitly invokes `$release-plan` and wants the completed plan released to production.
---

# Release Plan

Implement and release an approved plan for Weight Control. Treat explicit invocation as authorization to push `master` and run `infra/ansible/deploy-app.yml`; do not run provisioning, backup, or restore operations.

Before acting, read [references/release-context.md](references/release-context.md). It records stable repository mechanics, reusable validation rules, and known release cost centers; verify dynamic Git and deployment state live.

## Synchronize before implementation

1. Confirm the current worktree has no existing changes. Preserve them and request direction before continuing if it is not clean.
2. Record the current branch and locate the worktree whose branch is `refs/heads/master` with `git worktree list --porcelain`.
3. Update remote-tracking state and local `master` with `git -C "$master_worktree" pull --ff-only origin master`.
4. If the current branch is not `master`, merge local `master` into it with `git merge master`.
5. Resolve in-scope conflicts without discarding changes. Stop and report unrelated conflicts.

## Implement and validate

1. Implement the approved plan without expanding its scope.
2. Run exactly the checks required by the plan. Run independent frontend and backend checks concurrently.
3. Reuse a passing check from the current task only while its Git tree and relevant uncommitted inputs remain unchanged.
4. Fix failures before continuing; do not commit, push, or deploy while a required check fails.

## Commit and integrate

1. Review the final diff, stage only the implementation files, and create one concise commit. Record the feature commit SHA that names the deployment; use the implementation commit unless the approved plan identifies an earlier feature commit.
2. Update local `master` again with `git -C "$master_worktree" pull --ff-only origin master`.
3. If `master` advanced and the current branch is not `master`, merge `master` into the current branch and rerun the plan checks.
4. Run `"$current_worktree/.agents/skills/release-plan/scripts/build-release-artifacts.sh" "$current_worktree"` after the final checks. Rebuild the artifacts if the candidate tree changes afterward.
5. If the current branch is not `master`, merge it in the master worktree with `git -C "$master_worktree" merge --no-ff "$current_branch"`.
6. If the current branch is `master`, keep the commit directly on `master` and skip the merge.
7. Push with `git -C "$master_worktree" push origin master`. Do not deploy until this succeeds.
8. If the remote advances during integration, repeat synchronization and integration, rerun the plan checks, and rebuild the release artifacts before retrying the push.

## Deploy and verify

1. Run `"$master_worktree/.agents/skills/release-plan/scripts/deploy-production.sh" "$feature_commit" "$current_worktree"` after the push succeeds. The helper derives the deployment name from that commit subject and rejects artifacts that do not match the pushed master tree.
2. Let the helper wait while another local application deployment is visible, deploy from the master worktree, and verify the production URL.
3. Report deployment or verification failures with the pushed master commit. Do not roll back automatically.

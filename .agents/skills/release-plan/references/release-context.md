# Weight Control Release Context

Use this reference to avoid rediscovering stable release facts. Treat branches, worktree paths, commit IDs, running processes, and remote state as dynamic and inspect them for every release.

## Repository and deployment topology

- Locate the `master` worktree with `git worktree list --porcelain`; never assume its directory.
- The application deployment entrypoint is `infra/ansible/deploy-app.yml`, and its application tasks are in `infra/ansible/roles/app_deploy/tasks/main.yml`.
- Production uses the root `docker-compose.yml` plus the compose files configured in `infra/ansible/group_vars/all.yml`.
- Development and full-stack builds use the root `Dockerfile` and `backend/Dockerfile`, both with BuildKit dependency caches.
- Production adds `docker-compose.release.yml`, which uses `Dockerfile.release` and `backend/Dockerfile.release` to package prebuilt artifacts into lightweight runtime images.
- Ansible synchronizes the repository into the remote application directory while excluding dependencies, build outputs, secrets, IDE files, and operational data.
- Ansible synchronizes only the validated `dist/` tree and backend release JAR from the artifact worktree after the normal source synchronization.

## Validation reuse

- The approved plan defines the required checks; do not add broader checks merely because they exist in the repository.
- Run independent frontend and backend checks concurrently when their commands cannot modify the same tracked files.
- A passing check may be reused during the same task while its Git tree and relevant uncommitted inputs remain unchanged.
- If the skill is invoked after implementation, reuse checks already completed on the current tree, synchronize with `master`, and run the required checks once on the final candidate.
- Merging an advanced `master` changes the candidate and invalidates earlier required-check results; rerun them before integration.
- Build release artifacts only after required checks pass on the final committed candidate. The artifact manifest records the Git tree and checksums.
- Generated `dist/` and `backend/build/` content is not source and must not be staged.

## Deployment helper responsibilities

`scripts/deploy-production.sh` already:

- Discovers the `master` worktree and resolves the supplied feature commit.
- Derives the deployment and notification name from the feature commit subject.
- Verifies that artifact checksums are valid and their recorded Git tree matches pushed `master`.
- Reads deployment secrets from the master worktree's untracked `.env` and creates only supported missing generated secrets.
- Waits while another local application Ansible deployment is running.
- Runs the application playbook from the master worktree.
- Polls the frontend, authenticated backend boundary, service worker, and push worker until the complete application is ready.
- Requests the authenticated release notification only after production verification succeeds.

Do not duplicate those checks or secret-loading steps outside the helper. A successful helper exit is the production verification result.

## Current performance characteristics

- `build-release-artifacts.sh` uses the checked-in Yarn lockfile and local Yarn and Gradle caches, then records checksums and the candidate Git tree.
- Production transfers those artifacts and builds only thin runtime image layers; it does not compile application source remotely.
- Compose recreates changed services only. A changed Caddyfile is reloaded explicitly without forcing unrelated services to restart.
- Deployment output includes only a short Compose summary.
- MariaDB Testcontainers make the complete backend suite materially slower. Connection errors from scheduled tasks during container shutdown can appear after tests; use the Gradle exit status as the result.

## Maintenance rules

- Keep release Dockerfiles limited to runtime packaging; application compilation belongs in `build-release-artifacts.sh`.
- Update artifact checksum and tree verification together if artifact locations change.
- Keep the production compose override last so its Dockerfile choices win during Compose merging.
- Do not gain speed by skipping required checks, deploying before `master` is pushed, weakening production verification, or automatically rolling back a failed deployment.

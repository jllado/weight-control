# Weight Control Release Context

Inspect dynamic branches, worktrees, commits, processes, and remote state for every release.

## Topology

- Application deployment starts at `infra/ansible/deploy-app.yml`; app tasks are in `infra/ansible/roles/app_deploy/tasks/main.yml`.
- Production combines root `docker-compose.yml` with compose files in `infra/ansible/group_vars/all.yml`, including `docker-compose.release.yml`.
- Development builds use `Dockerfile` and `backend/Dockerfile`; release images use `Dockerfile.release` and `backend/Dockerfile.release` to package prebuilt artifacts.
- Ansible synchronizes source while excluding dependencies, outputs, secrets, IDE files, and operational data, then transfers the validated `dist/` tree and backend release JAR.

## Helpers

`deploy-production.sh` resolves the master worktree and feature commit, validates artifact checksums and candidate tree, loads deployment secrets, runs the application playbook, polls frontend and authenticated backend boundaries plus workers, and sends the release notification only after verification. Its successful exit is the production verification result.

## Performance and maintenance

- `build-release-artifacts.sh` requires a clean committed source worktree, uses the checked-in Yarn lockfile and local Yarn/Gradle caches, runs frontend lint and E2E checks, restores a production frontend build after E2E, runs backend tests, builds the release JAR, and records checksums and candidate tree.
- Production transfers artifacts and builds thin runtime layers; Compose recreates only changed services and reloads Caddy separately when needed.
- Spring/MariaDB integration tests own containers through Spring service connections so application beans shut down before their databases; investigate shutdown connection errors instead of suppressing them.
- Keep release Dockerfiles runtime-only, update artifact checksums with tree verification, and keep the production compose override last.

## Execution safeguards

- `scripts/check.sh` holds one worktree validation lock across each check; the artifact helper holds the same lock across both coordinated pipelines and cleanup. These locks protect documented entry points, not commands that bypass them.
- The deployment helper takes the repository-wide deployment lock first, then the artifact worktree validation lock; it holds both through production verification. Use this helper rather than calling `scripts/deploy.sh` directly.
- Lock contention exits with status 75 without running commands. Standalone and sequential checks stop only the active stage's process group and wait for cleanup. Concurrent release cancellation drains active stages normally and skips subsequent stages before releasing the lock; do not kill an existing run to retry.
- The artifact helper removes its `tree` readiness marker at startup and publishes it last after checksums and unchanged-source verification; a failed run cannot leave a ready manifest.
- Stage logs and UTC start/end times, durations, and exit statuses are recorded under `tmp/checks/run.*/`; summaries do not include command arguments or environment values. A stage includes its tool's cleanup time.
- Keep the browser test build and production frontend build separate because they use different configuration; keep steps within each pipeline sequential. The default release gate combines frontend/backend pipelines and two browser workers; pass `sequential` as the artifact helper's second argument for the validated fallback. See `docs/release-improvements/results.md` for adoption evidence.

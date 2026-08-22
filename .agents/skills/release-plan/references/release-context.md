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

- `build-release-artifacts.sh` uses the checked-in Yarn lockfile and local Yarn/Gradle caches, then records checksums and candidate tree.
- Production transfers artifacts and builds thin runtime layers; Compose recreates only changed services and reloads Caddy separately when needed.
- MariaDB Testcontainers can emit shutdown connection errors after backend tests; use the Gradle exit status.
- Keep release Dockerfiles runtime-only, update artifact checksums with tree verification, and keep the production compose override last.

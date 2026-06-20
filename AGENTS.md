# AGENTS.md

## Purpose

Weight Control is a personal health tool.
It exists to help the owner stay healthy through regular tracking of weight, blood pressure, habits, routines, and progress photos.
Prefer simple, maintainable changes that improve day-to-day usefulness over generalized product complexity.

## Project map

- `src/` contains the Vue 3 frontend.
- `src/components/` contains screen-level and feature components.
- `src/services/` contains API access helpers.
- `src/model/` contains frontend model objects.
- `backend/` contains the Spring Boot backend.
- `backend/src/main/java/com/jllado/weightcontrol/` follows the current controller, dto, service, repository, domain, security, and config layering.
- `backend/src/main/resources/db/migration/` contains Flyway SQL migrations.
- `scripts/` contains helper scripts for DB dumps, DB restore, and Ansible setup.
- `infra/ansible/` contains provisioning and deployment automation.
- `docker-compose.yml` runs the full stack with Caddy, frontend, backend, and MariaDB.

## Working style

- Keep instructions and user-facing English clear and concise.
- Prefer one-line statements when they stay readable.
- Match the surrounding style before introducing new patterns.
- Do not add defensive programming for impossible cases ruled out by types, invariants, or prior validation.
- Do not reformat unrelated code while making focused changes.

## Frontend rules

- Keep the current Vue structure unless the task explicitly requires a refactor.
- Prefer the existing component, service, and model split.
- Follow the surrounding Vue style. The current frontend mainly uses the Options API.
- Use `src/services/api.js` for HTTP access patterns unless there is a strong repo-local reason to do otherwise.
- Preserve existing route and component naming conventions.

## Backend rules

- Keep the current Spring layering: controller -> dto/service -> repository/domain.
- Put HTTP contracts in the existing DTO files under `backend/src/main/java/com/jllado/weightcontrol/api/dto/` when extending an existing feature.
- Use Flyway migrations for schema changes.
- Keep Java 21 compatibility.
- Prefer focused service logic over spreading business rules across controllers.

## Validation

- Install frontend dependencies with `yarn install`.
- Run the frontend dev server with `yarn serve`.
- Lint frontend changes with `yarn lint`.
- Run backend tests with `cd backend && ./gradlew test`.
- Use `docker compose up --build` only when a full-stack or container-specific check is needed.

## Safety

- Read infra and operations files for context when needed.
- Do not run deployment, provisioning, backup, or restore commands unless the user explicitly asks.
- Treat these as opt-in only:
  - `scripts/dump-prod-db.sh`
  - `scripts/restore-local-db.sh`
  - `scripts/setup-ansible.sh`
  - `infra/ansible/playbook.yml`
  - `infra/ansible/deploy-app.yml`

## Source boundaries

- Do not edit generated or dependency directories unless the task explicitly targets them.
- Treat `node_modules/`, `dist/`, `backend/build/`, `.gradle/`, `.venv-ansible/`, `tmp/`, and `backups/` as non-source paths.
- Prefer changing source files and checked-in configuration over build outputs.

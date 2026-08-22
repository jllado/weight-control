# AGENTS.md

## Purpose

Weight Control is a personal health tool.
It exists to help the owner stay healthy through regular tracking of weight, blood pressure, habits, routines, and progress photos.
Prefer simple, maintainable changes that improve day-to-day usefulness over generalized product complexity.

## Start here

- Read `docs/project-guide.md` before broad repository searches; it maps toolchains, commands, product areas, cross-cutting flows, and validation choices.
- Use configuration files as the source of truth for versions and commands, and update the guide when those sources or architectural boundaries change.
- Use targeted searches from the guide before scanning the complete frontend or backend tree.

## Project map

- `src/` contains the Vue 3 frontend.
- `src/components/` contains screen-level and feature components.
- `src/services/` contains API access helpers.
- `src/model/` contains frontend model objects.
- `backend/` contains the Spring Boot backend.
- `backend/src/main/java/com/jllado/weightcontrol/` follows the current controller, dto, service, repository, domain, security, and config layering.
- `backend/src/main/resources/db/migration/` contains Flyway SQL migrations.
- `scripts/` contains helper scripts for DB dumps, DB restore, and application-deployment setup.
- `infra/ansible/` contains application deployment automation.
- `docker-compose.yml` runs the full stack with Caddy, frontend, backend, and MariaDB.
- `docs/project-guide.md` is the practical reference for setup, task routing, runtime flows, and validation.

## Working style

- Keep instructions and user-facing English clear and concise.
- Prefer one-line statements when they stay readable.
- Match the surrounding style before introducing new patterns.
- Do not add defensive programming for impossible cases ruled out by types, invariants, or prior validation.
- Do not reformat unrelated code while making focused changes.

## Coach development

- Review `docs/coach/plan.md` and `docs/coach/todo.md` when planning, implementing, or reviewing every feature.
- Assess whether the feature affects Coach domains, context, Actions, GPT instructions, privacy, tests, or delivery sequencing.
- Implement and document Coach integration when relevant; do not force unrelated features into the Coach.
- Preserve existing Coach and reflection contracts unless the task explicitly changes them.

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

## Toolchain and validation

- The backend targets Java 21 and uses the checked-in Gradle 9.0 wrapper; run `cd backend && ./gradlew ...` instead of a system Gradle installation.
- Run the backend with `cd backend && ./gradlew bootRun`, test it with `cd backend && ./gradlew test`, and build it with `cd backend && ./gradlew build`.
- The frontend uses Yarn v1; install dependencies with `yarn install`, run it with `yarn serve`, lint it with `yarn lint`, and build it with `yarn build`.
- Run browser tests with `yarn test:e2e`; use Playwright `--grep` arguments when only focused coverage is required.
- Use `docker compose up --build` only when a full-stack or container-specific check is needed.

## Safety

- Read infra and operations files for context when needed.
- Do not run deployment, provisioning, backup, or restore commands unless the user explicitly asks.
- Treat these as opt-in only:
  - `scripts/dump-prod-db.sh`
  - `scripts/restore-local-db.sh`
  - `scripts/setup-ansible.sh`
  - `infra/ansible/deploy-app.yml`

## Source boundaries

- Do not edit generated or dependency directories unless the task explicitly targets them.
- Treat `node_modules/`, `dist/`, `backend/build/`, `.gradle/`, `.venv-ansible/`, `tmp/`, and `backups/` as non-source paths.
- Prefer changing source files and checked-in configuration over build outputs.

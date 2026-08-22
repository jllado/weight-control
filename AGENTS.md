# AGENTS.md

## Purpose

Weight Control is a personal health tool for regular tracking of weight, blood pressure, habits, routines, and progress photos. Prefer simple, maintainable changes that improve day-to-day usefulness over generalized product complexity.

## Start here

- Read `docs/project-guide.md` before broad repository searches; use its targeted discovery, routing, commands, and validation matrix.
- Treat configuration files as the source of truth for versions and commands; update the guide when those sources or architectural boundaries change.

## Working rules

- Keep instructions and user-facing English concise; prefer one-line statements when readable.
- Match surrounding style, avoid unrelated reformatting, and do not add guards for cases ruled out by types, invariants, or prior validation.
- For UI changes, keep controls aligned and consistently spaced, handle long labels, wrapping, and overflow intentionally, and verify the affected interface at mobile and desktop widths before considering it complete.
- Keep the Vue structure, component/service/model split, Options API style, `src/services/api.js` HTTP pattern, and route/component naming conventions unless the task requires otherwise.
- Keep Spring layering as controller -> dto/service -> repository/domain; put extended HTTP contracts in existing DTO files, use Flyway for schema changes, keep Java 21 compatibility, and keep business logic in focused services.

## Coach development

- Review `docs/coach/plan.md` and `docs/coach/todo.md` for every feature.
- Assess Coach domains, context, Actions, GPT instructions, privacy, tests, and delivery sequencing; integrate and document Coach changes only when relevant.
- Preserve Coach and reflection contracts unless the task explicitly changes them.

## Safety and source boundaries

- Read infra and operations files when relevant. Do not run deployment, provisioning, backup, or restore commands without explicit user authorization; this includes `scripts/dump-prod-db.sh`, `scripts/restore-local-db.sh`, `scripts/setup-ansible.sh`, and `infra/ansible/deploy-app.yml`.
- Do not edit generated or dependency directories unless targeted. Treat `node_modules/`, `dist/`, `backend/build/`, `.gradle/`, `.venv-ansible/`, `tmp/`, and `backups/` as non-source paths; change checked-in source and configuration instead.

# Routine completion checkboxes implementation plan

**Goal:** Replace arbitrary automatic-rule selection with a checkbox on each already assigned routine, preserving existing account configuration and history.

**Architecture:** Keep the stored rule as immutable identity and persist an independent enabled flag. Use existing Vue Options API forms, Spring services, MariaDB and Flyway; no dependencies or Coach contracts change.

## Tasks

- [ ] Add a Flyway migration that enables existing assigned rules and leaves manual routines disabled without changing recorded history.
- [ ] Extend routine DTOs, model and HTTP mapping with `automaticCompletionEnabled`; omitted updates preserve it. Reject assigning or changing rules through CRUD and enabling custom manual routines.
- [ ] Skip disabled routines during reconciliation, keeping evidence and check-ins intact; re-enabling reconciles eligible sources from the original start date. Preserve manual check-ins and daily deduplication.
- [ ] Replace the dropdown with an accessible checkbox and specific rule explanation using the existing personal-record control layout; preserve names, types, reminders, Save/Cancel and errors.
- [ ] Verify migration, service and persistence regressions plus all six checkbox journeys, custom routines, keyboard access, errors/pending states and mobile/desktop layouts.
- [ ] Update project guidance and correction memory, perform an independent review, validate, commit, build the complete gate, integrate and push master, deploy and independently verify production.

## Validation

Run checks sequentially with the worktree lock:

- `scripts/check.sh backend test --tests '*Routine*'`
- `scripts/check.sh frontend lint`
- `scripts/check.sh frontend test:e2e --grep 'routine|routines'`
- `.agents/skills/release-plan/scripts/build-release-artifacts.sh <worktree> combined`

Inspect screenshots at 376, 390 and 1280px, and production routines after release. Release identity is verified separately with `scripts/verify-deployment.py`.

## Defaults

Existing assignments identify the routines; no name matching, new catalog or duplicate account routines. Existing editing/deletion controls remain. Paused history remains untouched until automation resumes. Coach schemas/instructions and private GPT publication are outside this app-only change.

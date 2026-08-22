# Documentation

Documentation is grouped by feature so each plan, checklist, integration guide, and contract stays together.

## Project reference

- [Project guide](project-guide.md): toolchains, commands, architecture, task routing, runtime flows, validation, and targeted discovery.
- [Token-consumption handoff](token-consumption-handoff.md): Codex instruction optimization and measurement plan.

## Feature documentation

### Frontend modernization

- [Phased plan](frontend-modernization/plan.md)
- [Incremental TODO](frontend-modernization/todo.md)

### Personal records

- [Phased plan](personal-records/plan.md)
- [Implementation TODO](personal-records/todo.md)

### Weight Control Coach

- [Architecture plan](coach/plan.md)
- [Implementation TODO](coach/todo.md)
- [Coach Action schema](coach/coach-action.openapi.yaml)
- [Coach GPT configuration](coach/coach-gpt.md)
- [Reflection Action schema](coach/reflection-action.openapi.yaml)

### Tester recruitment

- [Overview](tester-recruitment/README.md)
- [End-to-end plan](tester-recruitment/plan.md)
- [Incremental TODO](tester-recruitment/todo.md)
- [Spanish public copy](tester-recruitment/copy.es.md)
- [Spanish legal drafts](tester-recruitment/legal.es.md)

## Maintenance rules

- Treat each feature TODO as the source of truth for implementation progress.
- Complete and validate one gated milestone before starting the next.
- Keep feature-specific contracts and guides inside the same feature directory.
- Update links when moving documents and avoid maintaining duplicate plans.

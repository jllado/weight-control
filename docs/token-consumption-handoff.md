# Token Consumption Reduction Handoff

## Goal

Reduce Codex token consumption without weakening project rules, implementation quality, validation, safety, or release reliability. Official [OpenAI model guidance](https://developers.openai.com/api/docs/guides/latest-model) recommends stating instructions once, using only relevant tools, selecting reasoning intentionally, and measuring representative tasks.

## Completed documentation optimization

`AGENTS.md` remains the recurring source for non-discoverable project rules. `docs/project-guide.md` is the discoverable source for architecture, commands, routing, and validation. The release skill owns the ordered release workflow; its reference contains only stable release facts.

| Document | Baseline words | Optimized words |
| --- | ---: | ---: |
| `AGENTS.md` | 635 | 275 |
| `docs/project-guide.md` | 1,102 | 913 |
| `release-plan/SKILL.md` | 490 | 321 |
| `release-plan/references/release-context.md` | 595 | 205 |

## Working pattern

- Use one conversation per feature or tightly related sequence, then start a new one.
- Give an outcome-focused request with scope, success criteria, authorization boundaries, and required validation.
- Reference repository paths instead of pasting files Codex can read; request concise updates and final reports that retain failures, caveats, commit identifiers, and deployment results.
- For substantial tasks, invoke `$model-advisor` with the planned request and use its recommendation. Skip it for trivial work when its advisory turn costs more than it saves.

## Deferred representative measurements

Record token totals when available; otherwise record recurring-context words, turns, tool calls, and elapsed time. Model-selection measurements include the advisor turn and its project-guide reads.

| Workflow | Baseline | Optimized | Required behavior and validation | Status |
| --- | --- | --- | --- | --- |
| Small frontend edit | — | — | Existing feature checks | Pending next qualifying task |
| Backend behavior change | — | — | Existing backend checks | Pending next qualifying task |
| Coach feature | — | — | Coach plan/TODO checks | Pending next qualifying task |
| Production release | — | — | Release-skill synchronization and verification | Pending a controlled comparison |

Keep the optimization only when representative tasks retain required behavior and validation while reducing measured usage or unnecessary context and turns.

## Acceptance and scope

- `AGENTS.md` is at least 25% shorter than its 635-word baseline (no more than 476 words).
- Every removed rule is redundant, discoverable, or retained through a precise source pointer.
- Documentation links resolve and `git diff --check` passes.
- Complete the deferred workflow table before claiming measured end-to-end token savings.

Do not reduce tests, validation, privacy safeguards, release checks, or necessary Coach health-data context solely to save tokens. Do not change runtime behavior, call the OpenAI API, or optimize account billing, pricing, or rate limits.

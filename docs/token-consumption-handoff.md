# Token Consumption Reduction Handoff

## Goal

Reduce Codex token consumption without weakening project rules, implementation quality, validation, safety, or release reliability.

Official OpenAI guidance recommends lean prompts, stating each instruction once, exposing only relevant tools, selecting reasoning effort intentionally, and monitoring growing conversation context. OpenAI reports large directional savings in internal coding-agent evaluations, but this project must measure its own results: [OpenAI model guidance](https://developers.openai.com/api/docs/guides/latest-model).

## Current state

- Repository `AGENTS.md` is approximately 635 words and is recurring context for repository work.
- `docs/project-guide.md` is approximately 1,102 words and is intended for targeted routing before broad searches.
- `.agents/skills/release-plan/SKILL.md` is approximately 490 words but is loaded only for explicitly requested releases.
- Feature plans, TODOs, and contracts are correctly separated under `docs/`; they should remain feature-scoped instead of becoming global instructions.
- Some repository guidance overlaps, especially project layout, commands, validation, and the instruction to keep changes focused.
- Long conversations accumulate completed-task history, tool output, and repeated instructions even when the next task is unrelated.

## Recommended implementation

1. Establish a baseline with four representative workflows: a small frontend edit, a backend behavior change, a Coach feature, and a production release.
2. Record total tokens when the client exposes them; otherwise record recurring-context word count, turns, tool calls, and elapsed time as proxies.
3. Reduce `AGENTS.md` by at least 25% while preserving every hard rule:
   - Keep purpose, mandatory routing, architectural boundaries, safety, source boundaries, and Coach-awareness rules.
   - Move discoverable project-map details and command explanations to `docs/project-guide.md` when already covered there.
   - Replace duplicated frontend/backend/toolchain prose with concise pointers to the relevant guide sections.
   - State each constraint once; do not weaken deployment opt-in requirements or the prohibition on impossible-case guards.
4. Make `docs/project-guide.md` progressively readable:
   - Add a compact contents/routing section if needed.
   - Keep high-value commands and source-of-truth tables easy to locate with targeted searches.
   - Avoid requiring the complete document for narrow tasks.
5. Review explicit skills after recurring instructions are optimized:
   - Preserve every synchronization, validation, push, deployment, and verification invariant in `release-plan`.
   - Remove only repeated explanations already guaranteed by global or repository instructions.
6. Document the preferred working pattern:
   - Use one conversation per feature or tightly related sequence, then start a new conversation.
   - Give one outcome-focused request containing scope, success criteria, authorization boundaries, and required validation.
   - Reference repository paths instead of pasting file contents that Codex can read.
   - Use low reasoning for routine, bounded edits; medium for normal features; high or above only when complexity justifies it.
   - Request concise updates and final reports while retaining failures, caveats, commit identifiers, and deployment results.
7. Repeat the representative workflows and compare them with the baseline.
8. Keep a change only when it lowers usage without causing missed rules, extra clarification turns, broader searches, validation gaps, or lower-quality results.

## Acceptance criteria

- Recurring repository instructions are at least 25% shorter by word count.
- Every rule removed from `AGENTS.md` is either redundant, discoverable from the repository, or retained through a precise link to its source of truth.
- The four representative workflows complete with the same required behavior and validation as the baseline.
- Median tokens decrease when token reporting is available; otherwise recurring-context size and unnecessary turns decrease.
- No workflow introduces unsafe deployment behavior, unrequested mutations, broad repository scans, unrelated formatting, or defensive programming for impossible cases.
- Documentation links resolve and `git diff --check` passes.

## Out of scope

- Reducing tests, validation, privacy safeguards, or release checks solely to save tokens.
- Hiding necessary context from Coach health-data requests.
- Changing application runtime behavior or calling the OpenAI API from Weight Control.
- Optimizing account pricing, billing, or rate limits.

## Suggested validation

```bash
wc -w AGENTS.md docs/project-guide.md .agents/skills/release-plan/SKILL.md
git diff --check
```

Manually compare the four representative workflows and record the baseline and optimized measurements in this document or a linked results file.

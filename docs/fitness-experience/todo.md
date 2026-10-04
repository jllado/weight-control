# Fitness Experience Implementation TODO

Source of truth for progress against the [improvement plan](plan.md); all milestones are planned and unchecked.

Complete one gated milestone before the next. M1–M4 do not depend on GPT-to-plugin migration; M5–M7 require the relevant capabilities from that separate effort. Do not mark implementation or release complete from documentation, source presence, or competitor descriptions alone.

## M1 — Reliable workout recovery

- [ ] Reconfirm the plan-screen interruption problem and identify the existing shared draft/timer interfaces.
- [ ] Add shared Resume/Discard controls across authenticated routes and Resume on the plan while a draft exists.
- [ ] Preserve Close/Minimize, paused/running timers, account isolation, and cross-tab ownership.
- [ ] Test completing a set, closing, navigating, reloading, resuming, and confirmed discard without duplicate saves.
- [ ] Update workflow documentation, pass focused checks and responsive review, and record release verification and personal acceptance.

## M2 — Daily actions before analytics

- [ ] Reconfirm the dashboard baseline and capture sanitized navigation/viewport measurements.
- [ ] Move workout, meal, and remaining-routine actions ahead of review; collapse the weekly table initially.
- [ ] Add direct planned-session Start/Resume and route Plan → Workouts to the Plan tab.
- [ ] Test historical dates, explicit today transitions, absent plans, rest days, multiple sessions, drafts, and unchanged reflection eligibility.
- [ ] Verify primary daily actions precede analytics and starting a single planned session avoids nested menus.
- [ ] Update navigation documentation, pass focused checks and responsive review, and record release verification and personal acceptance.

## M3 — Faster meal logging

- [ ] Prioritize previous meals, foods, and dishes while retaining a direct manual-calorie path.
- [ ] Group optional timing/nutrients and defer error display until interaction or submission.
- [ ] Preserve persistent totals, Save/Cancel, fasting requirements, and drafts after failures.
- [ ] Test meal reuse, food-based and manual entry, zero calories, incomplete macros, quantity changes, repeated meal types, and dates.
- [ ] Update nutrition workflow documentation, pass focused checks and responsive review, and record release verification and personal acceptance.

## M4 — Better guided workouts

- [ ] Reuse or extend comparable-history retrieval and display dated previous results separately from planned targets and records.
- [ ] Add persistent personal exercise cues and optional per-exercise rest preferences through existing ownership boundaries.
- [ ] Implement skippable rest countdowns without altering phase-time or workload calculations.
- [ ] Give primary workout actions at least 44-pixel touch height and retain keyboard focus and stable wrapping.
- [ ] Test absent/incompatible history, cue persistence, countdown recovery, skip/adjust, pause, and elapsed-time accuracy.
- [ ] Update interface and workflow documentation, pass relevant frontend/backend checks and responsive review, and record release verification and personal acceptance.

## M5 — Coaching after plugin migration

- [ ] Record the separately owned migration's verified plugin identity, supported entry point, connection requirements, and parity evidence.
- [ ] Verify existing reads, confirmed writes, assessments, reflections, selected-photo access, ownership, and privacy in the replacement.
- [ ] Update app Coach entry points and prompts with an explicit handoff, visible copyable prompt, and return/refresh action.
- [ ] Test explicit plugin selection, normal relevant requests, unavailable connections, clipboard failure, rejected writes, and authoritative app read-back.
- [ ] Synchronize Coach documentation and affected configuration references without treating legacy GPT limits as plugin requirements.
- [ ] Record app release, replacement-plugin availability, and personal end-to-end acceptance separately before cutover is complete.

## M6 — Progression and workout adaptation

- [ ] Define the milestone's exact contracts using existing workout/plan services and the verified replacement plugin.
- [ ] Add equipment and attainable-load preferences to persistent app data and scoped Coach context.
- [ ] Present evidence-backed next-session target proposals with clear rationale and confirmation before application.
- [ ] Support time/equipment adaptation in a session draft without silently replacing the weekly plan.
- [ ] Test absent or noncomparable evidence, attainable weights, accepted/rejected proposals, stale updates, and preserved history/weekly commitments.
- [ ] Synchronize app and Coach contracts/docs, pass relevant backend/frontend checks, and separately verify release, plugin behavior, and personal acceptance.

## M7 — Reusable meal planning

- [ ] Define separate planned-meal and shopping-list contracts, persistence, ownership, and replacement-plugin support.
- [ ] Generate editable seven-day proposals using foods, recipes, preferences, and agreed goals.
- [ ] Build shopping checklists with compatible-quantity aggregation and manual Already have selection.
- [ ] Route logging of a planned meal through the existing editor and explicit save confirmation.
- [ ] Test week reuse/editing, portion changes, unknown/incompatible ingredient units, checklist updates, and strict separation from consumed meals.
- [ ] Synchronize nutrition/Coach docs, pass relevant backend/frontend checks and responsive review, and separately verify release, plugin behavior, and personal acceptance.

## M8 — Personal workflow acceptance

- [ ] Repeat baseline journeys: start a planned session, change a set, resume after closing, repeat a meal, and find the next recommended action.
- [ ] Compare sanitized completion times, navigation steps, errors, and interruption recovery; record environment and delivered revision.
- [ ] Confirm every journey completes without lost drafts, unintended writes, or hidden recovery on desktop and mobile.
- [ ] Resolve material remaining friction and document limitations without claiming broader market validation.
- [ ] Mark the roadmap complete only after all milestone gates have evidence.

## Validation and maintenance

Use the [project guide](../project-guide.md) for locked check commands and the [design guidelines](../design-guidelines.md) for responsive review at 376, 390–393, and 1280 pixels and affected breakpoints; use the repository's release workflow for implementation delivery.

Update this checklist and the plan together as decisions change; keep migration, frontend modernization, and Health Connect implementation in their respective workstreams. Documentation-only edits require relative-link checks, matching milestone IDs, consistency review, and `git diff --check`.

Deferred: training-balance charts, program-template libraries, plate calculation, watch integration, and pantry inventory; these are not hidden completion requirements for M1–M8.

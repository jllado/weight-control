# Fitness Experience Improvement Plan

## Purpose and scope

Improve the owner's existing daily tracking, workout, nutrition, and ChatGPT coaching workflows through small, independently validated milestones.

Delivery status: planned; no implementation milestone is complete. Track progress only in the [implementation TODO](todo.md).

This roadmap follows [task 412](https://todo.devjllado.com/projects/1?task=412), competitor research, and the October 4, 2026 UX inspection. Creating these documents does not change the task's state or establish that the app is competitively validated.

Keep the current light, compact visual language and existing component/service/model architecture; follow the [design guidelines](../design-guidelines.md) and [project guide](../project-guide.md). Public onboarding, monetization, recruitment, social feeds, leaderboards, and embedded AI chat are outside this roadmap.

## Evidence and baseline

The inspection covered the live app on desktop and at a 390 × 844 mobile viewport, workout and meal forms, and a temporary guided workout that was discarded without saving. It did not test a complete workout save, a new Coach response, or competitor native apps. Reconfirm findings against the delivered revision before each milestone; the local checkout contained unrelated work and was not a verified production identity.

| Evidence | Finding | Consequence |
| --- | --- | --- |
| Live mobile inspection | Daily dashboard tabs began approximately 1,572 pixels down, below a large weekly score table. | Routine actions require substantial scrolling before use. |
| Live navigation | The tested path was Navigation → Plan → Workouts → Plan tab → Sunday → Start guided; Plan → Workouts initially opened the diary. | Starting a planned session is harder to discover than the feature itself warrants. |
| Live guided recording | Targets were prefilled; pictures, next-exercise preview, pause, progress, and one-tap set completion worked in the inspected steps. | Improve access and continuity while preserving this useful interaction. |
| Live interruption test | Closing the guided workout left Start guided disabled on the plan; Resume was under Home → Workout. | The session survived, but recovery was hidden from its starting screen. |
| Live meal form | Required-field errors appeared before interaction; timing and nutrient fields preceded food and dish selection. | The form creates unnecessary correction and entry work. |
| Source inspection | Assessment actions copied a prompt, opened ChatGPT, and instructed the user to paste it. | Coach handoff needs clearer continuity, especially during plugin migration. |
| Live geometry inspection | Guided action buttons were approximately 34 pixels high. | Primary workout actions need larger mobile touch targets. |

Competitor evidence is descriptive, not a measured speed or quality comparison: [Volm's editor](https://www.volm.app/features/workout-editor) places previous performance and set completion together; [Fitbod's workflow documentation](https://help.fitbod.me/hc/en-us/sections/1500000505721-Workout-Schedule-Logging) describes effort-informed targets and reusable workouts. These references inform improvements to this app, not recommendations to install competing products.

## Milestones

Complete each milestone's functional validation and personal acceptance before starting the next. M1–M4 can proceed independently of the separately owned plugin migration; M5–M7 require the relevant replacement-plugin capabilities. Record blocked dependencies without marking their milestones complete.

### M1 — Reliable workout recovery

- Reuse the existing account-scoped draft and timer services in one shared app-shell recovery control, available from every authenticated route when the guided session is closed.
- Offer Resume and confirmed Discard; replace the plan's disabled Start guided action with Resume while a guided draft exists, without implying it starts a different session.
- Keep existing cross-tab ownership, account isolation, timer semantics, and saved-workout behavior; do not create a second draft or timer store.
- Make Close and Minimize behavior explicit: the draft survives, and a running timer continues while a paused timer remains paused.
- Acceptance: start from today's plan, complete a set, close, change routes, reload, and resume the same step and timing state; discard removes only the draft and saves nothing.

### M2 — Daily actions before analytics

- Put selected-date workout actions, meal logging, and remaining routines before the progress review; keep the date conspicuous and the weekly table in an initially collapsed review section.
- Surface today's planned session with Start or Resume directly from Home; route Plan → Workouts to the existing Plan tab rather than the diary, preserving the diary entry point.
- Keep historical recording tied to the selected date; label any action that switches to today explicitly and never start today's timer against an older dashboard date.
- Handle rest days, absent plans, multiple planned sessions, and existing drafts using the current domain rules; retain all review information and completion/reflection eligibility.
- Acceptance: the daily action section precedes analytics at every target width, and a single planned session can start from Home without nested menus or opening the weekly table; multiple sessions require only the explicit session choice.

### M3 — Faster meal logging

- Lead with meal type and reuse of previous meals, saved foods, and dishes; keep a clearly labeled manual-calorie path available.
- Place optional timing and manual nutrient details in expandable sections; preserve fasting-related required timing and all existing calculation rules.
- Show field errors after interaction or an attempted save, not immediately on a fresh form; keep the persistent total and Save/Cancel footer.
- Preserve entered values after failures and edits, including zero-calorie meals, unknown macros, food quantities, and repeated meal types.
- Acceptance: repeat a prior meal without re-entering nutrition, create a food-based meal, and enter calories manually; all three paths retain correct totals and date semantics without premature errors.

### M4 — Better guided workouts

- Show previous comparable performance beside planned targets, with its date; match exercise, tracking mode, and units, and keep previous results distinct from targets and personal records.
- Add optional owner-specific exercise cues visible during recording, separate from catalog instructions and session notes.
- Add an optional per-exercise rest duration and countdown after set completion; let the user skip or adjust it without changing recorded workload or double-counting phase time.
- Keep elapsed phase timing authoritative and preserve the current pause behavior; derive countdown recovery from timestamps rather than assuming background ticks ran.
- Give primary guided controls at least 44-pixel touch height with stable wrapping and keyboard focus; scope this change to workout workflows rather than resizing every compact record action.
- Acceptance: comparable history is readable without leaving the set; absent or incompatible history is identified honestly; cues persist, countdowns survive backgrounding, and primary actions remain usable on narrow screens.

### M5 — Coaching after plugin migration

- Treat migration of the private GPT to a ChatGPT plugin as a separate required dependency, with implementation and authentication owned by that migration effort.
- Require parity for existing context reads, confirmed writes, assessments, reflections, selected-photo access, and current ownership/privacy boundaries before changing the app's Coach destination.
- Adapt app entry points and task-specific prompts to the verified replacement plugin; explain the handoff before opening ChatGPT and retain a visible copyable prompt if clipboard access fails.
- Provide a return/refresh action for the affected app record; refresh authoritative data rather than inferring that a conversational request succeeded.
- Do not assume an undocumented plugin deep link or automatic prompt submission; record and test the supported destination before cutover.
- Acceptance: invoke the replacement explicitly and through a normal relevant request, read context, confirm a supported write, and verify it in the app; an unavailable plugin or rejected write must not appear successful.

### M6 — Progression and workout adaptation

- Build on the existing recorded workouts, weekly plans, Coach assessments, and health constraints rather than creating a second workout system.
- Let Coach propose the next session's targets from comparable recorded performance and the agreed goal; show the evidence and rationale and require confirmation before replacing plan targets.
- Store available equipment and attainable load increments so proposals can use real weights; expose these preferences to the replacement plugin through its established context boundary.
- Support adapting one session to time and equipment constraints as a separate draft; preserve the repeating weekly plan unless the user explicitly confirms a plan change.
- Keep observed effort or recovery distinct from inference; do not fabricate missing measurements, promise exact session duration, or silently impose progression.
- Acceptance: inspect and accept or reject proposed changes, use constrained equipment, shorten a session, and reject stale updates; completed history and the weekly commitment remain unchanged unless explicitly edited.

### M7 — Reusable meal planning

- Generate an editable seven-day meal proposal from existing foods, recipes, user-supplied preferences, and agreed goals through the replacement Coach plugin.
- Persist planned meals separately from consumed meals; editing, copying, or confirming a plan never creates intake records.
- Produce a shopping checklist from planned recipe ingredients and quantities; combine only compatible quantities and retain unspecified amounts rather than inventing conversions.
- Start with manual Already have selection on shopping items; defer inventory, stock depletion, purchasing, and external grocery integrations.
- Use the existing meal editor when recording consumption from a planned meal, allowing portion changes and confirmation before saving.
- Acceptance: edit and reuse a week, generate and update its shopping list, and log one planned meal; only the explicitly logged meal affects intake totals.

### M8 — Personal workflow acceptance

- Compare the same personal journeys before and after delivery: start a planned session, change a set, resume after closing, repeat a meal, and find the next recommended action.
- Record navigation steps, completion time, errors, and interrupted-work recovery using synthetic fixtures or sanitized notes; do not add analytics collection or recruitment.
- Require successful completion of every journey without lost drafts, unintended writes, or hidden recovery; compare the measured navigation reductions against the baseline rather than inventing performance targets.
- Record remaining limitations and resolve material friction before marking the roadmap complete; this establishes personal workflow improvement, not market competitiveness.

## Interfaces, dependencies, and delivery

- M1–M3 reuse existing records and APIs; M4 adds optional persistent cues/rest preferences and comparable-history retrieval only where current services lack it.
- M6 adds equipment preferences and confirmed proposal/application behavior; M7 needs separate planned-meal and shopping-checklist storage. Extend existing domain services and ownership checks rather than duplicating business logic in plugin tools.
- Define exact contracts and migrations within the corresponding implementation milestone after inspecting the then-current source; this roadmap does not reserve endpoint names, migration numbers, or tool identifiers.
- Keep the [Coach plan](../coach/plan.md), [Coach TODO](../coach/todo.md), schema/instructions, and references consistent when implementing relevant contract changes. Their present GPT-specific limits are not assumed to be plugin limits.
- The [official migration guide](https://learn.chatgpt.com/docs/migrate-custom-gpts), reviewed October 4, 2026, says custom GPT Actions need separate replacement; migrating instructions alone does not prove tool parity. Verify account-specific access and timing within the migration effort rather than assigning an unverified deadline here.
- Keep UI-library changes in [frontend modernization](../frontend-modernization/plan.md) and wearable ingestion in [Health Connect](../health-connect/plan.md); neither is a prerequisite for the initial UX fixes.
- Validate frontend/backend changes through the locked commands in the project guide, with focused functional tests and responsive review at 376, 390–393, and 1280 pixels and affected existing breakpoints.
- For each implementation release, use the repository's release workflow and risk-based validation gate; report app deployment, plugin publication, and live acceptance separately. A documentation-only update needs link/consistency review and `git diff --check`, not an app build or deployment.

Deferred extensions: training-balance charts, program-template libraries, plate calculation, and watch integration; revisit only after the core personal workflows pass M8.

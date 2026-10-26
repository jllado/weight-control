# Guided workout review editing

## Approved behavior

Keep the same top pencil Edit workout button throughout active recording, completion and review. Reuse the existing buffered WorkoutEditor: Apply updates the local draft and returns to review; Cancel/Close/Escape discard changes and return focus. Disable editing during Save and without editor ownership.

Review corrections preserve recording identity, planned targets, original completion time, phase totals and surviving segment elapsed times. New content has no invented elapsed time; removal never resurrects omitted exercises or drops recorded skips. Reconcile Back to last set with surviving segments. Show cardio calories and heart rate alongside interval metrics. Keep the existing empty-workout/sauna validation.

## Implementation and acceptance

Update GuidedWorkoutPage, its GuidedWorkout coordinator, and GuidedWorkoutService; reuse WorkoutEditor without API, schema, or Coach changes. Add browser journeys for buffered edits, deletion, additions/reordering, skips/omissions, exact seconds, sauna-only sessions, refresh/save/diary persistence, pending/failing saves and safe back navigation. Inspect 376px, 393px and 1280px screenshots and keyboard focus.

Run sequentially: `scripts/check.sh frontend lint`; `scripts/check.sh frontend test:e2e tests/e2e/login-and-push.spec.js --grep guided`; `scripts/check.sh frontend playwright test tests/e2e/button-contract.spec.js`; `git diff --check`.

Obtain independent QA; commit, integrate and push, build the artifacts profile, deploy with the repository release-plan helper, and independently verify the published tree/readiness. No private GPT publication or production data writes are needed for this frontend-only change.

The user approved implementation and full delivery in this conversation.

## Validation

Guided suite: 44 passed; affected review/overlay/save regressions passed after QA corrections. Independent QA passed 17 selected journeys and reran the final overlapping-dropdown/picture Escape regression. Lint, button contract and diff checks passed. Original reference and mobile/desktop screenshots were reviewed. Deployment identity is verified separately during delivery.

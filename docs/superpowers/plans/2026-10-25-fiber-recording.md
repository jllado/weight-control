# Fiber recording correction

Approved scope: implement the fiber rework prepared in Personal Todo 480. Keep Fiber and its current target. Preserve all existing nutrients, unrelated records and compact Meals controls. Unknown is not zero. This pass does not decide the proposed Omega-3 label/ALA visibility change or redesign Daily totals.

## Task 1: Diagnose and protect food references
Trace catalog reuse, published Coach draft, request/reference values, DishNutrition and persisted summaries. Reproduce any silent loss before fixing the proven boundary. Preserve valid reference scaling and nullable unknown values.
Validation: `scripts/check.sh backend test --tests '*NutrientSummaryTest' --tests '*CatalogFoodPersistenceTest'` (baseline, reproducer, green).

## Task 2: Fiber collection and truthful compact display
Make the existing Coach food workflow resolve supported missing fiber rather than inheriting catalog nulls; preserve exact portions and provenance, do not invent values or silently rewrite templates/history. Keep known subtotals usable with compact accessible completeness details rather than lengthy captions. Verify manual 8g/100g → 4g/50g → 6g/75g save/reload and partial/all-unknown data.
Validation: `scripts/check.sh frontend lint`; `scripts/check.sh frontend test:coach`; focused Playwright nutrition tests through `scripts/check.sh frontend test:e2e --grep ...`. Source instruction checks retain 8000 characters and 30 Actions.

## Task 3: Independent review, release and published behavior
Fresh independent whole-change review/functional QA. Use repository release-plan; complete gate for shared contract risk. Commit, integrate/push, deploy if application changes, publish matching Coach configuration, verify exact released identities and fresh read-only proposals. Production acceptance writes follow the documented policy on irreversible notifications. Assess October24 repair feasibility without automatic historical enrichment.

## Task 4: Handoff
Record delivered fiber evidence and unresolved original-task rework separately. Do not mark all of task480 developed if its other requirements remain. Preserve user data and future rework context.

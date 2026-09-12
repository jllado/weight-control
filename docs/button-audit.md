# Application button audit

The standard is defined in [design guidelines](design-guidelines.md#buttons-and-actions): compact record actions use `CompactAction`; forms and descriptive workflows retain visible labels; related actions use the shared equal-width layout.

## Coverage

| Area | Treatment | Verification |
| --- | --- | --- |
| Dashboard | Compact editing, routine completion/undo, meal, back-pain and Edit/Rate/Delete workout groups | Source and mobile/desktop screenshots reviewed |
| Weight, pressure, cholesterol, mood, sleep, sickness, back-pain histories | Shared Edit/Delete controls and aligned action columns | Source and mobile/desktop screenshots reviewed |
| Nutrition | Meal/fasting histories, Foods, dishes, ingredient actions and editor footers | Source and mobile/desktop screenshots reviewed |
| Habits, routines, medications, agenda | Compact record/reminder controls and labeled form footers | Source and mobile/desktop screenshots reviewed |
| Workout diary and exercise catalogs | Consistent Edit/Rate/Delete order; catalog Edit/Delete groups | Source and mobile/desktop screenshots reviewed |
| Workout editor, stretching sets and weekly plan | Shared reorder/remove controls and labeled workflow groups | Source and mobile/desktop screenshots reviewed |
| Settings, health constraints and personal records | Compact record controls and aligned labeled action groups | Source and mobile/desktop screenshots reviewed |
| Reflections, Coach warnings and pause flows | Aligned labeled workflows and dialog footers; established WIN/MISS controls preserved | Source and mobile/desktop screenshots reviewed |
| Shell, login, photos and pickers | Review specialized navigation, identity, image-preview and picker controls | Source and mobile/desktop screenshots reviewed |

The focused audit covers 20 routes and their tabs/forms at 376, 390 and 1280px, plus workout and reminder breakpoint checks. The full release gate also exercises the existing populated-state and CRUD scenarios. Screenshots and contact sheets are retained under the release worktree’s `test-results/` and `tmp/button-audit/`.

## Regression checks

- Verify square dimensions, no compact labels, accessible names, hover/focus tooltips and Escape dismissal.
- Compare button bounds for equal dimensions, 0.5rem gaps, aligned wrapping and no group/page overflow.
- Check a delayed failed mutation for disabled/busy state, stable dimensions/name and recovery.
- Reproduce the three-action workout group at 376, 390–393px, on both sides of 575/640/960px breakpoints, and at 1280px.
- Exercise populated/empty screens, form footers, long content and existing feature workflows using the browser suite; retain screenshots for mobile and desktop review.

## Boundaries

This change affects frontend presentation only. Coach domains, context, Actions, GPT instructions, privacy, reflection contracts and backend APIs remain unchanged; no private GPT publication is needed. Specialized WIN/MISS controls, navigation and library-owned controls are explicit exceptions, not references for ordinary record actions.

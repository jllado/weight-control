# Design Guidelines

## Purpose

Weight Control should feel like one coherent health tool, even when screens are built at different times. These guidelines are mandatory for new and substantially changed frontend work.

Prefer the existing design foundation and a small, predictable interface over one-off visual solutions. If a new interaction does not fit an existing pattern, document the reason in the change and keep the exception local.

## Design foundation

- Use Vue 3 with the existing Options API and established component structure.
- Use PrimeVue 3 components and the Nova theme as the default visual foundation.
- Use PrimeFlex utilities for layout and spacing when they express the intent clearly.
- Use PrimeIcons for interface icons and keep icon meaning consistent across the application.
- Reuse an existing component or pattern before creating a new one.
- Treat PrimeVue/Nova and PrimeFlex as the current sources of truth for colors, typography, spacing, and responsive behavior.
- Do not introduce another UI library, theme, icon set, or utility framework for an individual screen.

## Visual language

### Layout and spacing

- Keep page sections aligned to a shared content edge.
- Use panels, cards, and dialogs to establish clear groups of related information.
- Use a consistent spacing rhythm; prefer PrimeFlex utilities over arbitrary pixel values.
- Keep action groups aligned and evenly spaced.
- Allow long labels and translated or user-generated content to wrap without overlapping controls.
- Avoid horizontal scrolling except for data that genuinely requires a wide table or visualization; make that overflow intentional and usable.
- Keep desktop layouts efficient without making mobile layouts depend on desktop minimum widths.

### Typography and content

- Use the theme typography and normal document hierarchy; do not introduce screen-specific font families.
- Use sentence case for headings, labels, buttons, menu items, and messages.
- Keep labels and actions concise and use direct, familiar wording.
- Use the same term for the same concept throughout the product.
- Put units in labels or formatted values consistently, such as `kg`, `cm`, `kcal`, and `%`.
- Explain unfamiliar health terms when the user needs that context to make a decision.

### Color and status

- Use PrimeVue semantic colors and component variants before adding custom colors.
- Reserve success, warning, danger, and informational colors for their semantic meanings.
- Do not communicate an important status by color alone; include text, an icon, or another accessible cue.
- Keep data visualization colors stable so the same metric has the same meaning across screens.
- Treat custom colors as local exceptions for a specific visualization or domain status, and document their meaning in the component.
- Preserve sufficient contrast for text, controls, focus indicators, and status states.

## Component patterns

### Actions and buttons

- Use a filled primary button for the main action of a view or form.
- Use outlined or secondary buttons for supporting actions.
- Use text buttons for low-emphasis navigation or dismissal actions.
- Use danger styling only for destructive actions and warning styling only for meaningful caution states.
- Use consistent verbs: `Save`, `Cancel`, `Edit`, `Delete`, `New`, `Add`, `Remove`, `Enable`, and `Dismiss` where they match the behavior.
- Include an icon when it improves recognition, and keep the icon consistent with the action.
- Icon-only buttons must have an accessible `aria-label` and a clear tooltip or title when the meaning is not obvious.
- Prevent duplicate submissions with the existing loading or disabled behavior while an action is in progress.

### Forms

- Associate every label with its control using a stable `for`/`id` relationship.
- Keep related fields together and use the existing PrimeFlex form-grid conventions.
- Put validation feedback next to the field it describes.
- Use concise, actionable validation messages that explain how to correct the value.
- Keep Save and Cancel actions in a predictable location and order.
- Reuse the existing form and dialog patterns for create and edit workflows.
- Preserve entered values and make loading, success, and failure states clear.

### Dialogs and overlays

- Use dialogs for focused create, edit, confirmation, or reminder workflows rather than embedding unrelated forms in the page.
- Give every dialog a clear title and a predictable footer action order.
- Keep dialog content usable at narrow widths; use the established responsive breakpoints and avoid fixed widths that cannot shrink.
- Do not make a destructive or irreversible action the visually ambiguous default.
- Use overlays and menus for short contextual actions, not for essential information that should remain discoverable.

### Tables, history, and data

- Use the existing PrimeVue `DataTable` patterns for sortable, paginated, loading, and responsive data.
- Keep column labels short and consistent with the rest of the product.
- Put row actions in a stable action column and provide accessible labels for icon-only actions.
- Hide or reflow lower-priority columns on small screens intentionally; do not rely on accidental clipping.
- Provide clear loading, empty, and error states.
- Keep dates, numbers, units, and status values formatted consistently within a feature.

### Navigation, feedback, and progress

- Keep the application shell navigation hierarchy and icon meanings consistent.
- Use inline messages for context-specific information and toasts for short-lived results of completed actions.
- Use loading indicators for operations that take noticeable time and avoid leaving controls visually ambiguous.
- Make notifications and reminders actionable while preserving their dismissal and routing behavior.
- Keep Coach and reflection entry points visually consistent with other primary application actions and preserve their privacy and confirmation context.

## Responsive and accessible behavior

- Verify every changed interface at a representative mobile width and desktop width.
- Follow the existing responsive breakpoints and test narrow layouts around the breakpoints used by the application, including 575px, 640px, and 960px where relevant.
- Keep controls reachable and readable without requiring precision tapping or horizontal page scrolling.
- Preserve visible keyboard focus and a logical tab order.
- Use semantic elements and accessible names for controls, status messages, charts, images, and custom interactions.
- Provide text alternatives or accessible summaries for non-text content and important visualizations.
- Respect reduced-motion preferences for decorative animation and never make animation necessary to understand or complete an action.
- Check overflow, long labels, validation messages, empty states, and loading states at both target widths.

## CSS and implementation conventions

- Keep the established Vue component/service/model split and Options API style.
- Prefer component-local or scoped styles for feature-specific presentation.
- Add global styles only when the rule is genuinely shared by the application shell or a documented design primitive.
- Prefer semantic class names that describe the component or role; avoid styling based on incidental DOM structure.
- Avoid new inline styles when a reusable class, PrimeFlex utility, or component property expresses the rule clearly.
- Do not override PrimeVue internals globally without documenting the affected component and the reason.
- Keep responsive rules close to the component behavior they support.
- Do not create a generalized wrapper or abstraction layer only to hide PrimeVue; extract a shared pattern only when it is repeated and stable.

## Review checklist

Before merging frontend UI work, verify:

- The change reuses the existing PrimeVue/Nova and PrimeFlex foundation.
- The primary action, supporting actions, and destructive actions have the correct emphasis.
- Layout, spacing, alignment, typography, wording, and status colors match nearby screens.
- Forms have associated labels and useful validation feedback.
- Loading, empty, error, success, and disabled states are intentional.
- Long labels, overflowing data, dialogs, and tables behave correctly.
- Keyboard focus, accessible names, semantic structure, and non-color status cues are present.
- The interface works at representative mobile and desktop widths.
- New custom CSS, colors, breakpoints, or components are necessary and documented.
- Coach, reflection, notification, and privacy-sensitive behavior remains unchanged unless explicitly in scope.

## Exceptions and future changes

An exception is acceptable only when an existing pattern cannot support the interaction or would reduce usability, accessibility, or clarity. Keep the exception scoped to the affected component and explain its purpose in the implementation.

This document standardizes the current UI foundation; it does not start the frontend modernization effort or require an immediate cleanup of existing inconsistencies. Update this document when the authoritative UI library, theme, or frontend architecture changes.

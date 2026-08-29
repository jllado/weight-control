# Design Guidelines

## Purpose

Weight Control should feel like one coherent personal health tool. These guidelines are mandatory for every visible frontend change, including Vue templates, CSS, dialogs, tables, controls, and responsive behavior.

Prefer the existing design foundation and nearby proven patterns over one-off visual solutions. Do not use this guide to modernize unrelated legacy UI; keep an exception local and document why the existing pattern cannot support it.

## Current foundation

- Use Vue 3 with the existing Options API and component/service/model structure.
- Use PrimeVue 3 with the Nova theme, PrimeFlex 2 for layout and spacing, and PrimeIcons for icons.
- Reuse registered global components from `src/main.js`; do not add another UI library, theme, icon set, or utility framework for one feature.
- Start from the nearest similar screen: `App.vue` for shell controls, `Home.vue` for dashboard actions, Settings components for forms and tables, and existing feature dialogs for create and edit flows.
- Preserve the application’s light, compact, panel-based appearance, semantic PrimeVue status styling, and familiar icon meanings.

## Layout and responsive behavior

- Align page sections to the same content edge and use panels, cards, dialogs, and headings to separate related work.
- Use PrimeFlex utilities when they express the layout clearly; use scoped flex or grid styles for feature-specific arrangements.
- Keep related actions aligned with a consistent `gap`; let ordinary action groups wrap rather than collide.
- Use a responsive grid only when its columns remain usable at the target width. Use `minmax(0, 1fr)` for equal columns so long content cannot force page overflow.
- Design desktop and mobile layouts together. Desktop can keep actions compact; mobile must have an intentional order, width, alignment, and label strategy.
- Keep primary mobile actions easy to scan and tap. When a desktop label is too long, provide a concise visible mobile label that preserves the action’s meaning and its accessible name.
- Do not rely on accidental clipping, a desktop minimum width, or horizontal page scrolling. Table overflow is acceptable only when deliberate and usable.
- Check changed interfaces at 390–393px, around relevant 575px, 640px, and 960px breakpoints, and a desktop width such as 1280px.

## Components and content

### Buttons and actions

- Use a filled primary button for the main save, record, or confirm action.
- Use outlined buttons for navigation and supporting actions, secondary buttons for cancellation, text buttons for low-emphasis dismissal, and semantic warning or danger styles only when their meaning matches the action.
- Keep action wording short, direct, and in sentence case: `Save`, `Cancel`, `Edit`, `Delete`, `New`, `Add`, `Remove`, `Enable`, and `Dismiss`.
- Pair icons with actions when they aid recognition and reuse their established meanings; icon-only controls require an `aria-label`.
- Keep save and cancel actions in a predictable dialog footer order, and use the existing loading or disabled state while a submission is in progress.

### Forms and dialogs

- Use the established PrimeFlex form-grid pattern for multi-column forms and stack fields at narrow widths.
- Associate every label with its control through a stable `for`/`id` pair, keep related fields together, and place validation feedback beside the field it explains.
- Use dialogs for focused create, edit, confirmation, and reminder workflows. Give each dialog a clear title, a predictable footer, and a width that can shrink on mobile.
- Prefer existing `Dialog` conventions, including `appendTo="body"`, modal behavior, and responsive widths or breakpoints when the content needs a constrained width.
- Make loading, validation, success, empty, and error states visible and clear without discarding entered values.

### Tables, history, and status

- Use the existing PrimeVue `DataTable` pattern for history and settings lists, including loading and empty states.
- Keep row actions in a stable action column, group multiple buttons with a wrapping action container, and label icon-only row actions accessibly.
- Keep column labels, dates, values, and units concise and consistent. Reflow or hide lower-priority content on mobile; use intentional responsive table scrolling only for genuinely wide data.
- Use semantic PrimeVue colors and text or icons together for status. Keep a metric’s custom visualization color stable and document any local exception in the component.

## CSS and accessibility

- Keep feature-specific presentation in scoped component styles. Add global styles only for an application-shell rule or a documented shared primitive.
- Prefer semantic class names and layout properties over styling incidental DOM structure or new inline styles.
- Keep responsive rules next to the component behavior they support. Reuse the application’s existing breakpoints instead of introducing arbitrary ones.
- Do not override PrimeVue internals globally unless the shared impact and reason are documented.
- Preserve visible keyboard focus, logical tab order, semantic controls, accessible names, and non-color status cues.
- Give non-text content and important visualizations useful text alternatives or summaries. Respect reduced-motion preferences and never make animation necessary to complete a task.

## Required review checklist

Before completing visible frontend work, verify:

- The change follows the Nova, PrimeVue, PrimeFlex, and PrimeIcons foundation and reuses a nearby existing pattern.
- Spacing, alignment, typography, wording, and action emphasis match the surrounding screen.
- Primary, supporting, cancellation, warning, and destructive actions have the intended meaning and visual weight.
- Forms have associated labels, useful validation feedback, and intentional loading, disabled, success, error, and empty states.
- Dialogs, action groups, long labels, tables, and user-generated content wrap, reflow, or overflow deliberately.
- The interface remains usable without horizontal page scrolling at mobile and desktop target widths.
- Keyboard focus, accessible names, semantic structure, and non-color status cues remain present.
- New custom CSS, colors, breakpoints, or shared styles are necessary, scoped correctly, and documented when they establish a new reusable pattern.
- Coach, reflection, notification, and privacy-sensitive behavior remains unchanged unless explicitly in scope.

## Maintenance

This document describes the current application style, not a separate design-system migration. Update it when the UI foundation, shared responsive patterns, or frontend architecture changes.

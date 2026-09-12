# Design Guidelines

## Purpose

Weight Control should feel like one coherent personal health tool. These guidelines are mandatory for every visible frontend change, including Vue templates, CSS, dialogs, tables, controls, and responsive behavior.

Prefer the existing design foundation and nearby proven patterns over one-off visual solutions. Use the shared action standards below for all new and existing button changes; document any exception explicitly.

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

- Use `CompactAction` for compact record and list actions; use `Button` for labeled synchronous workflows and `ActionButton` for labeled asynchronous mutations. The shared standard takes precedence over legacy variants.
- WIN/MISS recording uses `DecisionOutcomeActions.vue`: filled success/danger buttons, check/times icons, WIN/MISS labels, and equal 7rem widths. Use it in dashboard and pause flows.

- Use a filled primary button for the main save, record, or confirm action.
- Use outlined buttons for navigation and supporting actions, secondary buttons for cancellation, text buttons for low-emphasis dismissal, and semantic warning or danger styles only when their meaning matches the action.
- Keep action wording short, direct, and in sentence case: `Save`, `Cancel`, `Edit`, `Delete`, `New`, `Add`, `Remove`, `Enable`, and `Dismiss`.
- Compact record actions are always icon-only on mobile and desktop: Edit uses `pi-pencil`, Delete/Remove uses `pi-trash`, Rate uses `pi-star`, and View uses `pi-eye`; provide a required `aria-label` and the shared hover/focus tooltip.
- Compact actions are rectangular outlined primary-blue buttons; Delete/Remove is outlined danger-red. Do not use round, filled success/warning, borderless, or labeled variants for the same compact action.
- Keep visible labels for Save, Cancel, confirmations, creation, and descriptive workflows such as Add stretching set and weekly-plan editing; do not hide these labels to solve overflow.
- Group related controls with `action-group`; labeled groups use equal-width columns and compact groups also use `action-group--compact` for equal square controls. Use a 0.5rem gap, left alignment, stable order, and aligned wrapping without shrinking icons or clipping labels.
- Keep compact table action groups on one line; allow the table’s existing deliberate horizontal scrolling when its data and actions cannot fit together.
- Keep Edit before Delete in workout session action groups, including the diary and dashboard; Rate day belongs beside the training-day assessment and uses the same compact action style.
- Navigation arrows, account/notification controls, expand/collapse controls, picture previews, and library-owned pickers retain their specialized interaction; they still require accessible names and intentional sizing/alignment.
- Rectangular outlined icon-only buttons use the shared `App.vue` sizing: `2.357rem` square with centered `1rem` icons; apply the same sizing when a responsive button hides its label, as Open Coach does on mobile. Labeled workflows and the explicitly listed navigation/picker exceptions retain their appropriate sizing; compact record actions must use `CompactAction`.
- Keep short Save/Cancel pairs side by side whenever the footer fits two 6rem columns plus the gap; longer workflow groups use 9rem minimum columns and wrap their labels deliberately.
- Keep save and cancel actions in a predictable dialog footer order; show `Saving…`, bind both `loading` and `disabled`, and expose `aria-busy` during submission. PrimeVue loading styling alone does not disable the native button.
- Wrap editable save-form fields in `SaveFields` while a submission is pending; keep its save/cancel footer outside the boundary. Retain drafts after errors and disable dialog dismissal until the request finishes.
- Use `ActionButton` for standalone asynchronous mutations such as row deletion; its `action` callback must return the complete promise, including any required refresh. For compact mutations, pass the awaited callback to `CompactAction`, which delegates pending/error handling to `ActionButton`; keep the accessible name and square dimensions stable while its spinner is visible.

### Forms and dialogs

- Use the established PrimeFlex form-grid pattern for multi-column forms and stack fields at narrow widths.
- Associate every label with its control through a stable `for`/`id` pair, keep related fields together, and place validation feedback beside the field it explains.
- Use dialogs for focused create, edit, confirmation, and reminder workflows. Give each dialog a clear title, a predictable footer, and a width that can shrink on mobile.
- Prefer existing `Dialog` conventions, including `appendTo="body"`, modal behavior, and responsive widths or breakpoints when the content needs a constrained width.
- Make loading, validation, success, empty, and error states visible and clear without discarding entered values.

### Tables, history, and status

- Use the existing PrimeVue `DataTable` pattern for history and settings lists, including loading and empty states.
- Keep row actions in a stable action column, group multiple buttons with the shared action container, and use `CompactAction` for icon-only row actions.
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

- Compare compact actions against `CompactAction` and action groups against the shared `App.vue` styles; never copy a legacy variant.
- Identify the existing interface used as the design reference and compare its controls with the changed interface in mobile and desktop screenshots; verify labels, icons, fill, colors, sizing, and spacing as well as overflow.
- The change follows the Nova, PrimeVue, PrimeFlex, and PrimeIcons foundation and reuses a nearby existing pattern.
- Spacing, alignment, typography, wording, and action emphasis match the surrounding screen.
- Primary, supporting, cancellation, warning, and destructive actions have the intended meaning and visual weight.
- Forms have associated labels, useful validation feedback, and intentional loading, disabled, success, error, and empty states.
- Dialogs, action groups, long labels, tables, and user-generated content wrap, reflow, or overflow deliberately; verify equal widths/heights, 0.5rem gaps, aligned wrapped columns, hover/focus tooltips, and stable pending states.
- Include a 376px mobile viewport when reviewing compact actions, in addition to the existing responsive widths; retain browser regression coverage for shared controls. See [the application button audit](button-audit.md).
- The interface remains usable without horizontal page scrolling at mobile and desktop target widths.
- Keyboard focus, accessible names, semantic structure, and non-color status cues remain present.
- New custom CSS, colors, breakpoints, or shared styles are necessary, scoped correctly, and documented when they establish a new reusable pattern.
- Coach, reflection, notification, and privacy-sensitive behavior remains unchanged unless explicitly in scope.

## Maintenance

This document describes the current application style, not a separate design-system migration. Update it when the UI foundation, shared responsive patterns, or frontend architecture changes.

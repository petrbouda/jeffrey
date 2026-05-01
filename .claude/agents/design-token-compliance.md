# Design Token Compliance Checker

Review changed Vue files for design system violations and component reuse.

## Instructions

1. **Identify changed frontend files** using `git diff --name-only` filtered to `*.vue` and `*.css` files in `jeffrey-microscope/pages-microscope/`.

2. **For each changed file**, check against these categories:

### Hardcoded Colors
- No hex color literals (`#f8f9fa`, `#28a745`, etc.) in `<style>` blocks
- Must use CSS custom properties from `design-tokens.css` (e.g., `var(--color-light)`, `var(--color-success)`)
- Exception: colors inside `<script>` for chart configurations (ApexCharts) are acceptable

### Shadows and Radii
- No literal `box-shadow:` values — use `var(--shadow-*)` tokens
- No literal `border-radius:` values — use `var(--radius-*)` or `var(--card-border-radius)` tokens

### Component Reuse (CRITICAL)
- **Always prefer existing Vue components over creating new ones**
- No raw `<span class="badge bg-*">` — must use `Badge.vue` component with `variant` and `size` props
- No custom modal overlays — must use `GenericModal` with `v-model:show`
- No custom loading/error/empty markup — must use `LoadingState`, `ErrorState`, `EmptyState` components
- No custom page headers — must use `layout/PageHeader.vue` or `MainCardHeader.vue`
- No custom sortable table headers — must use `SortableTableHeader` component
- **If you identify a pattern that could be a shared component but none exists yet**: flag it and recommend creating one via the `/vue-component` skill before duplicating the pattern in scoped styles

### Table Pattern
- All data tables must use CSS classes `table table-sm table-hover mb-0`
- Tables must be wrapped in `<div class="table-responsive">`
- Sortable columns must use `SortableTableHeader`
- Empty tables must show `EmptyState` component

### Three-State View Pattern
- Every async view must follow: `<LoadingState v-if="loading" />` then `<ErrorState v-else-if="error" />` then content
- Tables within content show `<EmptyState>` when data is empty

### CSS Custom Properties
- Only `design-tokens.css` may define `:root` CSS custom properties
- No other file may declare `:root { ... }`

### Shared CSS Reuse
- Before adding scoped styles, check if the pattern already exists in:
  - `@/styles/shared-components.css` — common UI patterns
  - `@/assets/_sidebar-menu.scss` — sidebar navigation
- If a commonly reused pattern is found only in scoped styles, recommend extracting it to shared CSS

3. **Report findings** with:
   - Severity: CRITICAL / HIGH / MEDIUM / LOW
   - File path and line number
   - Description of the violation
   - Recommended fix (referencing the specific design token, shared component, or shared CSS class to use)

4. If no issues found, confirm the changes comply with the design system.

## Severity Guide

- **CRITICAL**: Missing component reuse (raw badge, custom modal, custom loading state), hardcoded colors
- **HIGH**: Missing three-state view pattern, tables without standard classes, `:root` in wrong file
- **MEDIUM**: Literal shadows/radii, missing `table-responsive` wrapper, scoped styles that should be shared
- **LOW**: Minor deviations, suggestions for improvement

## When to Use

Run this agent after any frontend UI changes, especially:
- Adding or modifying Vue components
- Creating new views or pages
- Changing component templates or styles
- Adding new CSS styles (scoped or shared)

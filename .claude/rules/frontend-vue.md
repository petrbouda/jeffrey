---
paths:
  - "**/*.vue"
  - "jeffrey-microscope/pages-microscope/**/*.ts"
---

## Vue/TypeScript Frontend Rules

### Component Reuse (shared-first — MUST)
- Before writing any new markup or component, you MUST first check the shared UI modules for something to use, compose, or extend:
  - `@shared` → `shared/ui/common/src` (generic components, services, styles, design tokens)
  - `@workspaces` → `shared/ui/workspaces/ui` (remote-workspace/recording components + clients)
  - `@instances` → `shared/ui/instances/src` (instance views)
- Only write custom markup or a new component when no shared one fits. Never duplicate a shared component locally.
- Where a new component lives: if it is generic (no page/JFR-domain semantics — chart, table, form input, badge, breadcrumb, layout, modal, drawer, etc.), create it under `shared/ui/common/src/components/` (`@shared`), NOT app-local. App `src/components/` is reserved for components tied to a specific page/feature (profile analysis, flamegraph, heap, gc, jdbc, grpc, span, streaming, …). When unsure, prefer `@shared`.

### Design Tokens
- No hardcoded hex colors in `<style>` blocks — use `var(--color-*)` from `design-tokens.css`
- No literal `box-shadow:` — use `var(--shadow-*)`
- No literal `border-radius:` — use `var(--radius-*)` or `var(--card-border-radius)`
- Only `design-tokens.css` may define `:root` CSS custom properties

### Required Components
- Badges: use `Badge.vue` component (never raw `<span class="badge">`)
- Modals: use `GenericModal` with `v-model:show` (see Modals below); wide modals use the `events-modal-dialog` pattern
- Page headers: use `MainCardHeader.vue` inside `MainCard` (or `layout/PageHeader.vue` for non-card pages)
- Tables: use the `components/table/DataTable.vue` family (see Tables below)

### Three-State View Pattern
- Every async view: `<LoadingState v-if="loading" />` -> `<ErrorState v-else-if="error" :message="error" />` -> `<template v-else>` content
- Empty content/tables: show `<EmptyState>` component

### Pages
- Scaffold a new page with `MainCard` -> `#header` slot holds `MainCardHeader` (props `icon`, `title`, `:badge?`, `#actions` slot) -> three-state pattern in the default slot
- Live in `views/global/` (workspace/project scope) or `views/profiles/` (profile scope); API client extends `BasePlatformClient` or `BaseProfileClient`
- Use the `/global-page` skill for the full scaffold. References: `views/global/RecordingsView.vue`, `views/global/GuardiansView.vue`

### Tables
- Use the `components/table/DataTable.vue` family — do NOT hand-roll `<div class="table-responsive"><table>`; `DataTable` already renders the `table table-sm table-hover mb-0` markup inside a card
- `DataTable` slots: `#toolbar` (use `TableToolbar` — `v-model` search + `#filters` slot), default (your `<thead>`/`<tbody>`), `#footer` (use `TableShowMore` for pagination)
- Sortable columns: `SortableTableHeader` (props `column`, `label`, `sortColumn`, `sortDirection`, `align`, `width?`; emits `sort`); right-align numerics with `text-end`
- Empty data: render `<EmptyState>` as a sibling instead of an empty `DataTable`
- Use the `/data-table` skill for the full scaffold. Reference: `views/profiles/detail/ProfileThreadDumps.vue`

### Modals
- Always `GenericModal` with `v-model:show` — never a custom overlay. Props: `modalId`, `title`, `icon`, `size` (`sm|md|lg|xl|fullscreen`), `modalDialogClass`, `showFooter`; slots: default body, `#header`, `#title`, `#footer`
- Size guide: `md` simple forms · `lg` lists / single column · `xl` rich / two-column · **wide near-fullscreen** for large editors
- Wide pattern (equal gutters, body is the single scroll container, footer pinned):
  ```vue
  <GenericModal ... size="xl"
    modal-dialog-class="my-modal-dialog events-modal-dialog modal-dialog-centered">
  ```
  ```css
  /* scoped */
  :deep(.modal-dialog.my-modal-dialog) { max-width: none; width: calc(100vw - 3.5rem); }
  ```
  `events-modal-dialog` is defined globally in `assets/styles.scss`. Use the `/new-modal` skill. Reference: `views/global/GuardiansView.vue`

### Shared CSS
- Check `@/styles/shared-components.css` and `@/assets/_sidebar-menu.scss` before adding scoped styles
- Extract commonly reused scoped patterns to shared CSS files

### Timestamps
- All timestamps are UTC epoch millis (numbers)
- Never use `new Date()` — always use `FormattingService` methods

### Composition API
- Always use `<script setup lang="ts">`
- Typed props: `defineProps<{}>()`
- Typed emits: `defineEmits<{}>()`

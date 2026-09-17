---
paths:
  - "**/*.vue"
  - "jeffrey-microscope/pages-microscope/**/*.ts"
  - "jeffrey-microscope/ui-hubs/**"
  - "jeffrey-microscope/ui-instances/**"
  - "jeffrey-hub/pages-hub/**"
  - "shared/ui/**"
---

## Vue/TypeScript Frontend Rules

### Shared-first (MUST)
- Before writing any markup or component, check the shared modules for something to use, compose or extend — `@shared` first, then `@hubs`/`@instances`; and check `@shared/assets/design-tokens.css` + `@shared/styles/shared-components.css` for existing styles. Only write custom markup when nothing fits. Never duplicate a shared component locally.
- Vite aliases (identical in every `pages-*` app): `@shared` → `shared/ui/common/src` (generic components, `FormattingService`, `BasePlatformClient`, `HttpUtils`, `ToastService`, styles, tokens); `@hubs` → `jeffrey-microscope/ui-hubs/ui` (hub browser: hubs → workspaces → projects, recording components and clients); `@instances` → `jeffrey-microscope/ui-instances/src`.
- A **generic** component (chart, table, form input, badge, breadcrumb, layout, modal, drawer — no page/JFR semantics) goes in `shared/ui/common/src/components/`. An app's `src/components/` is for page/feature-bound components (flamegraph, heap, gc, jdbc, …). When unsure, prefer `@shared`.

### Design tokens
- Only `design-tokens.css` may declare `:root` custom properties.
- No hex colors in `<style>` — `var(--color-*)`, `var(--table-header-bg)`, …; no literal `box-shadow`/`border-radius` — `var(--shadow-*)`, `var(--radius-*)`, `var(--card-border-radius)`.
- Rows change background on hover, never transform/lift.

### Required components
- Badges: `Badge.vue` with `variant`/`size` — never `<span class="badge bg-*">`.
- Page headers: `layout/PageHeader.vue` for page level; `MainCardHeader.vue` (`icon`, `title`, `:badge?`, `#actions`) inside `MainCard`. Scaffold pages with `/global-page`.
- Tables: the `components/table/DataTable.vue` family — `DataTable` (renders `table table-sm table-hover mb-0` in `.table-responsive`), `#toolbar` (`TableToolbar`: `v-model` search + `#filters`), default slot (`<thead>`/`<tbody>`), `#footer` (`TableShowMore`); `SortableTableHeader` for sortable columns; `EmptyState` as a sibling when empty. Never hand-roll `<div class="table-responsive"><table>`. Scaffold with `/data-table`; reference `views/profiles/detail/ProfileThreadDumps.vue`.
- Modals: `GenericModal` with `v-model:show`, never a custom overlay. `size`: `md` simple forms · `lg` lists · `xl` two-column. Wide near-fullscreen editors: `modal-dialog-class="<name> events-modal-dialog modal-dialog-centered"` + scoped `:deep(.modal-dialog.<name>) { max-width: none; width: calc(100vw - 3.5rem); }`. Scaffold with `/new-modal`; reference `views/global/RecordingsView.vue`.
- Three-state view: `<LoadingState v-if="loading" />` → `<ErrorState v-else-if="error" />` → content; empty data → `<EmptyState>`.
- Navigation: sidebar submenus via nav-config children — never a wrapper `TabBar` over pages that have their own tabs.

### Code
- `<script setup lang="ts">`, typed `defineProps<{}>()` / `defineEmits<{}>()`; braces on every control-flow body.
- API clients extend `BasePlatformClient` (workspace/project) or `BaseProfileClient` (profile features) in `pages-microscope/src/services/api/`.
- State: ref-based stores in `src/stores/` (no Pinia); composables in `src/composables/`; event bus `mitt`.
- Timestamps are UTC epoch millis (numbers). Never `new Date()` for parsing/formatting and never propagate date strings — `FormattingService` only; `datetime-local` inputs convert at the boundary. Propose a new `FormattingService` function rather than formatting inline.
- `vue3-apexcharts` JSON-clones `:options`, dropping formatter functions on reactive re-apply — keep interaction state out of options computeds.
- Flamegraph protobuf: regenerate with `npm run proto:generate`.
- Shared CSS: `@shared/styles/shared-components.css` (search containers, cards, buttons, loading/empty states, drawers, form fields, info rows) and `@/assets/_sidebar-menu.scss`; add reused patterns there instead of duplicating scoped styles.
- Verify with `npm run build` (runs `vue-tsc`) and `npm run test`; run the `design-token-compliance` agent after editing Vue files.

---
paths:
  - "**/*.vue"
  - "jeffrey-local/pages-local/**/*.ts"
---

## Vue/TypeScript Frontend Rules

### Component Reuse
- Always prefer existing Vue components over creating new ones
- Search `src/components/` for existing shared components before writing new markup
- If a reusable pattern is needed but doesn't exist, suggest creating a shared component via `/vue-component` skill

### Design Tokens
- No hardcoded hex colors in `<style>` blocks — use `var(--color-*)` from `design-tokens.css`
- No literal `box-shadow:` — use `var(--shadow-*)`
- No literal `border-radius:` — use `var(--radius-*)` or `var(--card-border-radius)`
- Only `design-tokens.css` may define `:root` CSS custom properties

### Required Components
- Badges: use `Badge.vue` component (never raw `<span class="badge">`)
- Modals: use `GenericModal` with `v-model:show`
- Page headers: use `layout/PageHeader.vue` or `MainCardHeader.vue`
- Sortable tables: use `SortableTableHeader`

### Three-State View Pattern
- Every async view: `<LoadingState v-if="loading" />` -> `<ErrorState v-else-if="error" />` -> content
- Empty tables: show `<EmptyState>` component

### Tables
- CSS classes: `table table-sm table-hover mb-0`
- Wrap in `<div class="table-responsive">`

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

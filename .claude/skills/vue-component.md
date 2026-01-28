# Vue Component Scaffolding

Scaffold a new Vue 3 component following Jeffrey project conventions.

## Usage

```
/vue-component
```

## Workflow

When this skill is invoked, follow these steps:

### Step 1: Gather Component Information

Ask the user for the following using AskUserQuestion:

1. **Component Name**: PascalCase name (e.g., "ProfileTimeline", "EventSummaryCard")
2. **Component Type**: One of:
   - `view` - Full page component (goes in `pages/src/views/`)
   - `component` - Reusable component (goes in `pages/src/components/`)
3. **Feature Area**: Which feature area does this belong to? (e.g., "profiles", "projects", "global", "common")
4. **Has API calls?**: Does the component need to call backend APIs? (yes/no)

### Step 2: Generate the Component

Create the Vue component file using these conventions:

```vue
<script setup lang="ts">
// 1. Imports - group by: vue, vue-router, services, components, types
import { ref, onMounted } from 'vue';

// 2. Props and emits
const props = defineProps<{
  // typed props
}>();

const emit = defineEmits<{
  // typed emits
}>();

// 3. Reactive state
// 4. Computed properties
// 5. Methods
// 6. Lifecycle hooks
</script>

<template>
  <!-- Use Bootstrap 5 classes -->
  <!-- Use shared CSS classes from @/styles/shared-components.css when available -->
</template>

<style scoped>
/* Import shared styles first if needed */
/* @import '@/styles/shared-components.css'; */

/* Component-specific styles only */
</style>
```

### Key Conventions

- **Always** use `<script setup lang="ts">` (Composition API with TypeScript)
- **Always** use typed props via `defineProps<{}>()` and typed emits via `defineEmits<{}>()`
- **Check shared CSS** in `@/styles/shared-components.css` before adding scoped styles
- **Use FormattingService** (`@/services/FormattingService`) for formatting values (bytes, percentages, durations, etc.)
- **Use Axios** via service files in `@/services/` for API calls - never call APIs directly in components
- **Use Bootstrap 5** utility classes for layout and common styling
- **PascalCase** for component file names matching the component name
- **No emojis** in code or comments

### File Location Rules

| Type | Location |
|------|----------|
| View (profiles) | `pages/src/views/profiles/detail/{ComponentName}.vue` |
| View (projects) | `pages/src/views/projects/detail/{ComponentName}.vue` |
| View (global) | `pages/src/views/global/{ComponentName}.vue` |
| Component (shared) | `pages/src/components/{ComponentName}.vue` |
| Component (feature) | `pages/src/components/{feature}/{ComponentName}.vue` |

### Step 3: Register Routes (Views Only)

If the component is a view, update the router configuration:

**File:** `pages/src/router/index.ts`

Add route entry following the existing patterns in the file.

### Step 4: Summary

After generating the component:
- List all created/modified files
- Remind to run `npm run build` in `pages/` to verify
- Note any TODO items

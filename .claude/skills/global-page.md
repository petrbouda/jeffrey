# Create Global Page

Create a new page accessible from the top-level global navigation (MainNavigation).

## Usage

```
/global-page
```

## Overview

Global pages are application-wide views (not scoped to a workspace or project). They appear as tabs in the top-level navigation bar. All global pages must use the `MainCard` component for consistent card styling.

## Step-by-Step Workflow

### Step 1: Create Vue View

Create in `jeffrey-local/pages-local/src/views/global/YourPageView.vue`:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MainCard from '@/components/MainCard.vue'

// State, API calls, logic here
</script>

<template>
  <div>
    <MainCard>
      <template #header>
        <div class="page-header">
          <div class="page-header-info">
            <i class="bi bi-your-icon page-header-icon"></i>
            <span class="page-header-title">Your Page Title</span>
            <span v-if="items.length > 0" class="page-header-badge">{{ items.length }}</span>
          </div>
          <div class="page-header-actions">
            <!-- Optional: search, filters, buttons -->
          </div>
        </div>
      </template>

      <!-- Content goes in the default slot -->
      <div>Your content here</div>
    </MainCard>
  </div>
</template>

<style scoped>
/* Component-specific styles only */
</style>
```

### Key Conventions

- **Always** wrap content in `<MainCard>` component (from `@/components/MainCard.vue`)
- Use `#header` named slot for the page header (icon, title, badge, actions)
- Use default slot for the main content
- Use `page-header`, `page-header-info`, `page-header-icon`, `page-header-title`, `page-header-badge` CSS classes for header structure (defined in `shared-components.css`)
- Use `:no-padding="true"` prop on `MainCard` if the content sections handle their own padding
- Multiple `<MainCard>` instances per page are fine (see EventLogView for an example with 2 cards)

### Step 2: Add Route

Add route to `jeffrey-local/pages-local/src/router/index.ts` under the global routes section (inside the `Index` layout children):

```typescript
{
  path: 'your-page',
  name: 'your-page',
  component: () => import('@/views/global/YourPageView.vue')
},
```

### Step 3: Add Navigation Tab

Add a navigation link to `jeffrey-local/pages-local/src/components/MainNavigation.vue`:

```vue
<router-link to="/your-page" class="nav-pill" active-class="active">
  <i class="bi bi-your-icon"></i>
  <span>Your Page</span>
</router-link>
```

### Step 4: Create API Client (if needed)

If the page calls backend APIs, create a client in `jeffrey-local/pages-local/src/services/api/YourPageClient.ts` extending `BasePlatformClient`.

## File Locations Summary

| Component | Path |
|---|---|
| Vue View | `jeffrey-local/pages-local/src/views/global/YourPageView.vue` |
| Router | `jeffrey-local/pages-local/src/router/index.ts` (global routes section) |
| Navigation | `jeffrey-local/pages-local/src/components/MainNavigation.vue` |
| API Client | `jeffrey-local/pages-local/src/services/api/YourPageClient.ts` |
| MainCard component | `jeffrey-local/pages-local/src/components/MainCard.vue` |

## Reference Examples

- **Simple page**: `jeffrey-local/pages-local/src/views/global/SettingsView.vue`
- **Multi-card page**: `jeffrey-local/pages-local/src/views/global/EventLogView.vue`
- **Custom header**: `jeffrey-local/pages-local/src/views/global/QuickAnalysisView.vue`

## Verification

1. Frontend build: `cd jeffrey-local/pages-local && npm run build`
2. Frontend tests: `cd jeffrey-local/pages-local && npm run test`

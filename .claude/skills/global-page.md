# Create Global Page

Create a new page accessible from the top-level global navigation (MainNavigation), using the current canonical scaffold.

## Usage

```
/global-page
```

## Overview

Global pages are application-wide views (not scoped to a workspace or project). They appear as tabs in the top-level navigation bar. Every global page uses `MainCard` + `MainCardHeader` for the card/header chrome and follows the three-state async pattern. Tabular data uses the `DataTable` family (`/data-table` skill); dialogs use `GenericModal` (`/new-modal` skill).

## Step-by-Step Workflow

### Step 1: Create the Vue View

Create `jeffrey-microscope/pages-microscope/src/views/global/YourPageView.vue`.

Canonical scaffold — `MainCard` → `MainCardHeader` in the `#header` slot → three-state (`LoadingState` / `ErrorState` / content) → `EmptyState` or a `DataTable` for content → `GenericModal` for create/edit/delete:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
// import GenericModal from '@/components/GenericModal.vue'; // for create/edit dialogs

const loading = ref(true);
const error = ref<string | null>(null);
const items = ref<MyItem[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    items.value = await client.list();
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-your-icon" title="Your Page" :badge="items.length">
          <template #actions>
            <!-- Optional: search, filters, primary button -->
            <button class="btn btn-sm btn-primary" @click="openCreate">
              <i class="bi bi-plus-lg"></i> New Item
            </button>
          </template>
        </MainCardHeader>
      </template>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" />
      <template v-else>
        <EmptyState
          v-if="items.length === 0"
          icon="bi-inbox"
          title="No items"
          description="No items match the current view."
        />
        <DataTable v-else>
          <thead>
            <tr>
              <th>Name</th>
              <th class="text-end">Count</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td>{{ item.name }}</td>
              <td class="text-end">{{ item.count }}</td>
            </tr>
          </tbody>
        </DataTable>
      </template>
    </MainCard>

    <!-- Create / edit / delete dialogs use GenericModal — see /new-modal -->
  </div>
</template>

<style scoped>
/* Component-specific styles only. Use design tokens; reuse shared-components.css first. */
</style>
```

### Key Conventions

- **MainCard + MainCardHeader** are mandatory chrome. `MainCardHeader` props: `icon`, `title`, `:badge?` (shows when > 0), `#actions` slot for buttons/search/filters. Do NOT hand-write the old `page-header` `<div>`s — the component renders them.
- **Three-state pattern** is mandatory: `LoadingState` → `ErrorState` (`:message="error"`) → `<template v-else>` content.
- **Tables** use the `DataTable` family — never hand-roll `<div class="table-responsive"><table>`. Add a toolbar/search/pagination via `TableToolbar` / `TableShowMore` and sortable columns via `SortableTableHeader`. See the `/data-table` skill.
- **Empty content** renders `EmptyState` (props `icon`, `title`, `description?`).
- **Dialogs** use `GenericModal` with `v-model:show`. For large editors use the wide `events-modal-dialog` pattern. See the `/new-modal` skill.
- **Badges** via `Badge.vue`; **no hardcoded colors/shadows/radii** — design tokens only.
- Use `:no-padding="true"` on `MainCard` if content sections manage their own padding. Multiple `MainCard`s per page are fine.

### Step 2: Add Route

Add to `jeffrey-microscope/pages-microscope/src/router/index.ts` under the global routes section (inside the `Index` layout children):

```typescript
{
  path: 'your-page',
  name: 'your-page',
  component: () => import('@/views/global/YourPageView.vue')
},
```

### Step 3: Add Navigation Tab

Add a link to `jeffrey-microscope/pages-microscope/src/components/MainNavigation.vue`:

```vue
<router-link to="/your-page" class="nav-pill" active-class="active">
  <i class="bi bi-your-icon"></i>
  <span>Your Page</span>
</router-link>
```

### Step 4: Create API Client (if needed)

If the page calls backend APIs, create a client in `jeffrey-microscope/pages-microscope/src/services/api/YourPageClient.ts` extending `BasePlatformClient` (workspace/project scope) or `BaseProfileClient` (profile scope).

## File Locations Summary

| Component | Path |
|---|---|
| Vue View | `jeffrey-microscope/pages-microscope/src/views/global/YourPageView.vue` |
| Router | `jeffrey-microscope/pages-microscope/src/router/index.ts` (global routes section) |
| Navigation | `jeffrey-microscope/pages-microscope/src/components/MainNavigation.vue` |
| API Client | `jeffrey-microscope/pages-microscope/src/services/api/YourPageClient.ts` |
| MainCard / MainCardHeader | `jeffrey-microscope/pages-microscope/src/components/MainCard.vue`, `MainCardHeader.vue` |

## Related Skills

- **Tables**: `/data-table` — the `DataTable` family scaffold
- **Modals**: `/new-modal` — `GenericModal` + the wide `events-modal-dialog` pattern
- **Reusable component**: `/vue-component`

## Reference Examples

- **List page with toolbar + master/detail**: `jeffrey-microscope/pages-microscope/src/views/global/GuardiansView.vue`
- **List page with cards/table**: `jeffrey-microscope/pages-microscope/src/views/global/RecordingsView.vue`
- **Settings/simple page**: `jeffrey-microscope/pages-microscope/src/views/global/SettingsView.vue`

## Verification

1. Frontend build: `cd jeffrey-microscope/pages-microscope && npm run build`
2. Frontend tests: `cd jeffrey-microscope/pages-microscope && npm run test`

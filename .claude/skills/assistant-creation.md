# Creating a New Assistant

This skill documents how to create a new assistant component in the Jeffrey codebase.

## Assistant Types

| Type | Scope | Location | Example |
|------|-------|----------|---------|
| **Global** | App-wide, persists across navigation | Rendered in `App.vue` | DownloadAssistant |
| **Local** | Single page, context-specific | Rendered in page component | OqlAssistant |

## Directory Structure

```
pages/src/components/assistants/
├── global/                              # Global assistants (App.vue level)
│   └── {Name}Assistant.vue
├── local/                               # Local assistants (page-specific)
│   └── {Name}Assistant.vue
├── AssistantPanel.vue                   # Shared base panel component
├── AssistantMinimizedButton.vue         # Shared reusable minimized button
├── AssistantMinimizedContainer.vue      # Container for minimized buttons (handles row layout)
└── index.ts                             # Barrel export

pages/src/stores/assistants/
├── {name}AssistantStore.ts              # Global store (for global assistants)
└── index.ts                             # Barrel export
```

## Important: Multiple Assistants Support

When multiple assistants are minimized simultaneously, their buttons are arranged in a horizontal row (from right to left). This is handled by `AssistantMinimizedContainer` which is rendered in `App.vue`.

**How it works:**
- `AssistantMinimizedContainer` creates a fixed container at bottom-right corner
- All `AssistantMinimizedButton` components teleport into this container
- The container uses flexbox (`flex-direction: row-reverse`) to arrange buttons in a row
- Buttons are spaced with a 12px gap

**Requirements:**
- `AssistantMinimizedContainer` must be rendered in `App.vue` (already done)
- `AssistantMinimizedButton` automatically teleports to `#assistant-minimized-container`

## Step 1: Decide Assistant Type

- **Global**: Use for features that persist across navigation (downloads, uploads, notifications)
- **Local**: Use for page-specific helpers (AI query assistants, contextual tools)

## Step 2: Create the Component

### For Global Assistants

Location: `/pages/src/components/assistants/global/{Name}Assistant.vue`

```vue
<template>
  <!-- Minimized State - Floating Button -->
  <AssistantMinimizedButton
      v-if="isOpen && !isExpanded"
      icon="bi bi-{icon-name}"
      :progress="progress"
      :badge-text="badgeText"
      :status="status"
      @click="$emit('expand')"
      title="Click to expand"
  />

  <!-- Expanded State - Panel -->
  <AssistantPanel
      :is-open="isOpen"
      :is-expanded="isExpanded"
      width="480px"
      :show-backdrop="false"
      @close="$emit('close')"
  >
    <template #header-icon>
      <i class="bi bi-{icon} me-2"></i>
    </template>

    <template #header-title>
      {Title}
    </template>

    <template #header-actions>
      <button class="btn-icon" @click="$emit('minimize')" title="Minimize">
        <i class="bi bi-dash-lg"></i>
      </button>
      <button class="btn-icon" @click="$emit('close')" title="Close">
        <i class="bi bi-x-lg"></i>
      </button>
    </template>

    <template #body>
      <!-- Your content here -->
    </template>

    <template #footer>
      <!-- Optional footer content -->
    </template>
  </AssistantPanel>
</template>

<script setup lang="ts">
import AssistantPanel from '@/components/assistants/AssistantPanel.vue';
import AssistantMinimizedButton from '@/components/assistants/AssistantMinimizedButton.vue';

interface Props {
  isOpen: boolean;
  isExpanded: boolean;
  // ... feature-specific props
}

defineProps<Props>();

defineEmits<{
  (e: 'expand'): void;
  (e: 'minimize'): void;
  (e: 'close'): void;
  // ... feature-specific events
}>();
</script>
```

### For Local Assistants

Location: `/pages/src/components/{feature}/{Name}Assistant.vue` or `/pages/src/components/assistants/local/{Name}Assistant.vue`

**Key difference from Global Assistants:**
- The minimized button should be visible immediately when entering the page
- Use `v-if="!isExpanded"` instead of `v-if="isOpen && !isExpanded"` for the button

```vue
<template>
  <!-- Minimized State - Always visible when not expanded -->
  <AssistantMinimizedButton
      v-if="!isExpanded"
      icon="bi bi-{icon-name}"
      badge-text="..."
      badge-variant="..."
      @click="$emit('expand')"
      title="Click to open"
  />

  <!-- Expanded State - Panel -->
  <AssistantPanel
      :is-open="isOpen"
      :is-expanded="isExpanded"
      width="560px"
      :show-backdrop="true"
      @close="$emit('close')"
  >
    <!-- ... slots ... -->
  </AssistantPanel>
</template>
```

Additional requirements:
- Set `:show-backdrop="true"` for modal behavior
- Manage state in parent page component
- No global store needed
- Start with `isExpanded = false` so button is visible on page load

## Step 3: Create Store (Global Assistants Only)

Location: `/pages/src/stores/assistants/{name}AssistantStore.ts`

```typescript
import { ref, computed } from 'vue';

// Global state
const isOpen = ref(false);
const isExpanded = ref(true);

// Actions
const expand = () => {
  isExpanded.value = true;
};

const minimize = () => {
  isExpanded.value = false;
};

const closePanel = () => {
  isOpen.value = false;
  isExpanded.value = true;
};

export const {name}AssistantStore = {
  // State
  isOpen,
  isExpanded,

  // Actions
  expand,
  minimize,
  closePanel,
  // ... feature-specific actions
};
```

Export from barrel file: `/pages/src/stores/assistants/index.ts`

## Step 4: Integrate Into App (Global) or Page (Local)

### Global Assistant - App.vue

```typescript
import { {Name}Assistant } from '@/components/assistants';
import { {name}AssistantStore } from '@/stores/assistants';
```

```vue
<{Name}Assistant
    :is-open="{name}Store.isOpen.value"
    :is-expanded="{name}Store.isExpanded.value"
    @expand="{name}Store.expand"
    @minimize="{name}Store.minimize"
    @close="{name}Store.closePanel"
/>
```

### Local Assistant - Page Component

**Important:** Start with `assistantExpanded = false` so the floating button is visible when user enters the page.

```typescript
const assistantOpen = ref(false);
const assistantExpanded = ref(false);  // Start minimized - button visible on page load

const openAssistant = () => {
  assistantOpen.value = true;
  assistantExpanded.value = true;
};

const closeAssistant = () => {
  assistantOpen.value = false;
  assistantExpanded.value = false;  // Return to minimized state
};
```

```vue
<{Name}Assistant
    :is-open="assistantOpen"
    :is-expanded="assistantExpanded"
    @close="closeAssistant"
    @expand="assistantExpanded = true; assistantOpen = true"
    @minimize="assistantExpanded = false"
/>
```

## Shared Component Props

### AssistantPanel Props

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| isOpen | boolean | required | Whether the panel is active |
| isExpanded | boolean | required | Full panel or minimized |
| width | string | '480px' | Panel width |
| headerGradient | string | purple gradient | CSS gradient for header |
| showBackdrop | boolean | false | Show backdrop overlay |
| zIndex | number | 1040 | Z-index for positioning |

### AssistantMinimizedButton Props

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| icon | string | required | Bootstrap icon class |
| progress | number | - | 0-100, shows circular progress ring |
| badgeText | string | - | Text in badge (1-3 chars) |
| badgeVariant | string | 'default' | Badge color variant |
| status | string | 'default' | Status for color theming |
| isSpinning | boolean | false | Animate icon spinning |
| isPulsing | boolean | false | Pulse animation on button |
| order | number | 10 | Position in row (lower = closer to right edge) |

**Important:** Always assign a fixed `order` value to each assistant to maintain consistent positioning. Reserved order values:
- `1` - DownloadAssistant (global)
- `2` - OqlAssistant (local)
- `3-9` - Reserved for future assistants
- `10+` - Available for new assistants

Note: Position is handled by `AssistantMinimizedContainer` - buttons are automatically arranged in a row at bottom-right based on their `order` value.

## Styling Guidelines

- **Header gradient**: All assistants use the same default gradient from `AssistantPanel`: `linear-gradient(135deg, #667eea 0%, #764ba2 100%)`. Do not override unless absolutely necessary for brand consistency.
- Global assistants: no backdrop, bottom-right floating button
- Local assistants: backdrop, same floating button position
- Icons: Use Bootstrap Icons (bi-*)
- Badge: Short text (1-3 chars or number)

## Examples

### DownloadAssistant (Global)
- Header gradient: Purple (#667eea to #764ba2)
- Icon: `bi-cloud-download`
- Shows progress ring with percentage
- Badge shows download count

### OqlAssistant (Local)
- Header gradient: Violet (#8b5cf6 to #7c3aed)
- Icon: `bi-stars`
- Badge shows "AI"
- Has backdrop for modal behavior

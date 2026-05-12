<template>
  <nav class="tab-bar" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="tab-bar-item"
      :class="{
        active: modelValue === tab.id,
        disabled: tab.disabled
      }"
      :disabled="tab.disabled"
      role="tab"
      :aria-selected="modelValue === tab.id"
      :aria-disabled="tab.disabled || undefined"
      @click="select(tab)"
    >
      <i v-if="tab.icon" :class="['bi', `bi-${tab.icon}`, 'tab-bar-icon']"></i>
      <span class="tab-bar-label">{{ tab.label }}</span>
      <Badge
        v-if="tab.badge !== undefined && tab.badge !== null && tab.badge !== ''"
        :value="tab.badge"
        :variant="tab.badgeVariant ?? 'primary'"
        size="xs"
        :uppercase="typeof tab.badge === 'string'"
        class="tab-bar-badge"
      />
    </button>
  </nav>
</template>

<script setup lang="ts">
import Badge from '@/components/Badge.vue';
import type { Variant } from '@/types/ui';

export interface TabBarItem {
  /** Stable identifier emitted via v-model when this tab is selected. */
  id: string;
  /** Visible text label. */
  label: string;
  /** Bootstrap icon name without the `bi-` prefix (e.g. `terminal`). */
  icon?: string;
  /** Optional badge rendered after the label via the project's `Badge` component. */
  badge?: string | number;
  /** Variant for the badge; defaults to `primary`. Ignored when `badge` is unset. */
  badgeVariant?: Variant;
  /** Greyed-out and not clickable. */
  disabled?: boolean;
}

const props = defineProps<{
  tabs: TabBarItem[];
}>();

const modelValue = defineModel<string>({ required: true });

const select = (tab: TabBarItem) => {
  if (tab.disabled) {
    return;
  }
  modelValue.value = tab.id;
};
</script>

<style scoped>
.tab-bar {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-card);
  padding: 0 0.5rem;
}

.tab-bar-item {
  appearance: none;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  padding: 0.55rem 1rem;
  margin-bottom: -1px;
  font: inherit;
  font-size: 0.85rem;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.tab-bar-item:hover:not(.disabled) {
  color: var(--color-purple);
}

.tab-bar-item.active {
  color: var(--color-purple);
  border-bottom-color: var(--color-purple);
  font-weight: 600;
}

.tab-bar-item.disabled {
  color: var(--color-text-light);
  cursor: not-allowed;
}

.tab-bar-icon {
  font-size: 0.95rem;
}

.tab-bar-badge {
  margin-left: 0.25rem;
}
</style>

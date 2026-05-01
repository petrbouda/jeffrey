<template>
  <th
    :class="['sortable-header', { active: isActive, 'text-end': align === 'end' }]"
    :style="width ? { width } : undefined"
    @click="$emit('sort', column)"
  >
    <span class="header-content" :class="{ 'justify-end': align === 'end' }">
      <slot>{{ label }}</slot>
      <i class="bi sort-icon" :class="sortIcon"></i>
    </span>
  </th>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  column: string;
  label?: string;
  sortColumn: string;
  sortDirection: 'asc' | 'desc';
  align?: 'start' | 'end';
  width?: string;
}

const props = withDefaults(defineProps<Props>(), {
  align: 'start'
});

defineEmits<{
  sort: [column: string];
}>();

const isActive = computed(() => props.sortColumn === props.column);

const sortIcon = computed(() => {
  if (!isActive.value) return 'bi-arrow-down-up';
  return props.sortDirection === 'asc' ? 'bi-arrow-up' : 'bi-arrow-down';
});
</script>

<style scoped>
.sortable-header {
  cursor: pointer;
  user-select: none;
  transition: background-color var(--transition-fast);
  background-color: var(--color-light);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.sortable-header:hover {
  background-color: var(--color-bg-hover-alt);
}

.sortable-header.active {
  color: var(--color-primary);
}

.header-content {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.header-content.justify-end {
  justify-content: flex-end;
  width: 100%;
}

.sort-icon {
  font-size: 0.65rem;
  opacity: 0.4;
  transition: opacity 0.15s ease;
}

.sortable-header:hover .sort-icon,
.sortable-header.active .sort-icon {
  opacity: 1;
}
</style>

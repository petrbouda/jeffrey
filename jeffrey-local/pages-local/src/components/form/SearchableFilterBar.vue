<template>
  <div class="filter-bar">
    <span class="filter-label">
      <i class="bi bi-funnel" :class="{ 'bi-funnel-fill': modelValue != null }"></i>
      {{ label }}
    </span>
    <SearchableSelect
      :model-value="modelValue"
      :items="items"
      :placeholder="placeholder"
      :search-placeholder="searchPlaceholder"
      @update:model-value="$emit('update:modelValue', $event)"
      @clear="$emit('clear')"
    >
      <template #item="{ item, highlight }">
        <div class="filter-item-label" v-html="highlight(item.label)"></div>
        <div class="filter-item-meta">
          <span><i class="bi bi-play-circle"></i> {{ item.count.toLocaleString() }}</span>
          <span><i class="bi bi-clock"></i> P99: {{ formatP99(item.p99) }}</span>
        </div>
      </template>
    </SearchableSelect>
    <div class="filter-info">
      <Badge :value="items.length + ' ' + itemsLabel" variant="blue" size="xs" :uppercase="false" :borderless="true" />
      <template v-if="modelValue && selectedItem">
        <Badge :value="selectedItem.count.toLocaleString() + ' exec'" variant="blue" size="xs" :uppercase="false" :borderless="true" />
        <Badge :key-label="'P99'" :value="formatP99(selectedItem.p99)" variant="orange" size="xs" :uppercase="false" :borderless="true" />
      </template>
      <template v-else>
        <Badge :value="totalCount.toLocaleString() + ' total'" variant="blue" size="xs" :uppercase="false" :borderless="true" />
      </template>
    </div>
    <button v-if="modelValue" @click="$emit('update:modelValue', null); $emit('clear')" class="filter-clear-btn">
      <i class="bi bi-x-lg"></i> Clear
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import SearchableSelect from '@/components/form/SearchableSelect.vue';
import Badge from '@/components/Badge.vue';
import FormattingService from '@/services/FormattingService.ts';

export interface FilterBarItem {
  label: string;
  count: number;
  p99: number;
}

interface Props {
  modelValue: string | null;
  items: FilterBarItem[];
  label: string;
  placeholder?: string;
  searchPlaceholder?: string;
  itemsLabel?: string;
  totalCount: number;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'All',
  searchPlaceholder: 'Search...',
  itemsLabel: 'items'
});

defineEmits<{
  'update:modelValue': [value: string | null];
  clear: [];
}>();

const selectedItem = computed(() => {
  if (!props.modelValue) return null;
  return props.items.find(i => i.label === props.modelValue) || null;
});

const formatP99 = (nanos: number): string => {
  return FormattingService.formatDuration2Units(nanos);
};
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 1.5rem;
  padding: 10px 16px;
  background: var(--card-bg);
  border-radius: var(--card-border-radius);
  border: 1px solid var(--card-border-color);
  box-shadow: var(--card-shadow);
  flex-wrap: wrap;
}

.filter-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.filter-label i {
  font-size: 0.85rem;
}

.filter-label .bi-funnel-fill {
  color: var(--color-primary);
}

.filter-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  flex-wrap: wrap;
}

.filter-clear-btn {
  font-size: 0.7rem;
  padding: 4px 10px;
  border-radius: var(--radius-sm, 4px);
  border: 1px solid var(--card-border-color);
  background: var(--card-bg);
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  transition: all var(--transition-fast, 0.15s) ease;
}

.filter-clear-btn:hover {
  background: var(--color-light);
  color: var(--color-danger);
  border-color: rgba(230, 55, 87, 0.2);
}

.filter-clear-btn i {
  font-size: 0.6rem;
}

.filter-item-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
}

.filter-item-meta {
  font-size: 0.65rem;
  color: var(--color-text-muted);
  display: flex;
  gap: 12px;
  margin-top: 2px;
}

.filter-item-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .filter-info {
    margin-left: 0;
  }
}
</style>

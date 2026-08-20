<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  Shared card-list shell for the Technologies "list of entities" views (HTTP endpoints, JDBC
  statement groups, async-profiler span tags). Owns the card layout, sort controls and Show-All
  toggle; callers fill the #name, #metrics and #right slots. Slot content is compiled in the
  caller's scope, so each caller styles its own slotted badges/boxes.
-->
<template>
  <div class="metric-card-list">
    <!-- Controls. Absent entirely for a list with nothing to sort and no filters of its own. -->
    <div v-if="sortOptions.length > 0 || $slots.controls" class="mcl-controls">
      <div v-if="sortOptions.length > 0" class="sort-controls">
        <label class="sort-label">Sort by:</label>
        <div class="btn-group" role="group">
          <button
            v-for="option in sortOptions"
            :key="option.key"
            type="button"
            class="btn btn-outline-secondary btn-sm"
            :class="{ active: currentSort === option.key }"
            @click="selectSort(option.key)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <div class="mcl-controls-right">
        <!-- Caller-provided filters sharing the sort row, so a list needs no toolbar of its own. -->
        <slot name="controls" />
        <!--
          Hidden when the server did the paging: there is no "all" to show, only a next page, which
          only the caller can ask for.
        -->
        <button
          v-if="!serverOrdered && items.length > maxDisplayed"
          @click="showAll = !showAll"
          class="btn btn-sm btn-outline-secondary"
        >
          {{ showAll ? 'Show Less' : `Show All (${items.length})` }}
        </button>
      </div>
    </div>

    <!-- Cards -->
    <div class="mcl-cards">
      <!-- Keyboard-operable: opening a card is the list's primary action. -->
      <div
        v-for="item in displayedItems"
        :key="itemKey(item)"
        class="mcl-card"
        role="button"
        tabindex="0"
        @click="$emit('itemClick', item)"
        @keydown.enter.prevent="$emit('itemClick', item)"
        @keydown.space.prevent="$emit('itemClick', item)"
      >
        <!-- Left: Gradient count zone. The #count slot replaces the number for a caller whose
             headline is not a count (a duration, say); countClass lets it tint the zone per item. -->
        <div class="mcl-count" :class="countClass?.(item)">
          <slot name="count" :item="item">
            <span class="mcl-count-num">{{ FormattingService.formatNumber(count(item)) }}</span>
            <span class="mcl-count-label">{{ countLabel }}</span>
          </slot>
        </div>

        <!-- Right of the zone: body (name + metrics, optional status, chevron), then an optional
             full-width footer band the caller styles itself — evidence, notes, anything that
             deserves its own strip along the card's bottom edge. -->
        <div class="mcl-right">
          <div class="mcl-body">
            <div class="mcl-main">
              <div class="mcl-name"><slot name="name" :item="item" /></div>
              <div class="mcl-metrics"><slot name="metrics" :item="item" /></div>
            </div>

            <div class="mcl-status"><slot name="right" :item="item" /></div>

            <i class="bi bi-chevron-right mcl-arrow"></i>
          </div>

          <slot name="footer" :item="item" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import FormattingService from '@shared/services/FormattingService';

export interface MetricSortOption {
  key: string;
  label: string;
  compare: (a: any, b: any) => number;
}

const props = withDefaults(
  defineProps<{
    items: any[];
    itemKey: (item: any) => string | number;
    /** Unused when the caller fills the #count slot with its own headline. */
    count?: (item: any) => number;
    countLabel?: string;
    /** An extra class for the count zone of one item — a severity tint, say. */
    countClass?: (item: any) => string | undefined;
    sortOptions: MetricSortOption[];
    initialSort?: string;
    maxDisplayed?: number;
    /**
     * Take `items` exactly as given — already ordered and already the page. The sort buttons then
     * only report which one is pressed, through `sortChange`, and it is the caller's job to fetch
     * the newly-ordered page. Sorting here as well would reorder a page cut from a different order,
     * which shows the top of the wrong list.
     */
    serverOrdered?: boolean;
  }>(),
  {
    count: () => 0,
    countLabel: '',
    countClass: undefined,
    initialSort: undefined,
    maxDisplayed: 10,
    serverOrdered: false
  }
);

const emit = defineEmits<{
  itemClick: [item: any];
  /** The key of the sort option just pressed. Only acted on by a `serverOrdered` caller. */
  sortChange: [key: string];
}>();

const showAll = ref(false);
const currentSort = ref<string>(props.initialSort ?? props.sortOptions[0]?.key ?? '');

// Kept in step with a caller that drives the sort from its own state -- the URL, say -- so the
// pressed button still reflects the order the rows actually arrived in.
watch(
  () => props.initialSort,
  (sort) => {
    if (sort !== undefined) {
      currentSort.value = sort;
    }
  }
);

function selectSort(key: string): void {
  currentSort.value = key;
  emit('sortChange', key);
}

const sortedItems = computed(() => {
  if (props.serverOrdered) {
    return props.items;
  }
  const option = props.sortOptions.find(o => o.key === currentSort.value);
  if (!option) {
    return props.items;
  }
  return [...props.items].sort(option.compare);
});

const displayedItems = computed(() => {
  if (props.serverOrdered) {
    return sortedItems.value;
  }
  return showAll.value ? sortedItems.value : sortedItems.value.slice(0, props.maxDisplayed);
});
</script>

<style scoped>
.mcl-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.mcl-controls-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sort-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
}

.mcl-cards {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.mcl-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.mcl-card {
  display: flex;
  align-items: stretch;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.mcl-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

/* Left: soft count zone (flat, brand-tinted) */
.mcl-count {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 96px;
  flex-shrink: 0;
  padding: 0 14px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-right: 1px solid var(--color-border-light);
}

.mcl-count-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 1.55rem;
  font-weight: 700;
  line-height: 1;
}

.mcl-count-label {
  font-size: 0.56rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  opacity: 0.85;
  margin-top: 4px;
}

/* Everything right of the count zone: the body, then the optional footer band beneath it. */
.mcl-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* Body: name + metrics + optional status + chevron */
.mcl-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.mcl-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.mcl-name {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mcl-metrics {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.mcl-status {
  display: flex;
  gap: 7px;
  flex-shrink: 0;
}

.mcl-arrow {
  color: var(--color-text-light);
  font-size: 1rem;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .mcl-body {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .mcl-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
}
</style>

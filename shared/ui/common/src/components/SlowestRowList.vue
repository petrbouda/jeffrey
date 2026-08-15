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
  Shared shell for the Technologies "slowest instances" views (slowest traces, slowest spans,
  slowest requests): one row per occurrence, ranked by duration, each with a proportional time
  bar. The instance-list counterpart to MetricCardList, which covers the aggregated views.

  Owns the count header, the row chrome, the accent gutter and the time bar; callers fill the
  #details slot (and #name / #name-prefix when the default headline is not enough). Slot content
  is compiled in the caller's scope, so use DetailChip/Badge inside slots rather than expecting
  this component's styles to reach them.
-->
<template>
  <div v-if="displayedItems.length > 0">
    <SlowestCountHeader
      :shown="displayedItems.length"
      :total="total ?? items.length"
      :note="note"
    />
    <div class="slowest-list">
      <div
        v-for="(item, index) in displayedItems"
        :key="itemKey(item, index)"
        class="slowest-row"
        @click="emit('rowClick', item)"
      >
        <!-- The gutter carries the outcome: a failed row is visible before anything is read. -->
        <div class="left-accent" :class="`accent-${rowAccent(item)}`"></div>
        <div class="row-content">
          <div class="row-header">
            <div class="row-header-left">
              <!-- For a leading badge beside the name (JDBC operation, HTTP method). -->
              <slot name="name-prefix" :item="item" />
              <div class="group-text" :title="name(item)">
                <slot name="name" :item="item">
                  <i v-if="rowIcon(item)" class="row-icon" :class="rowIcon(item)"></i>
                  {{ name(item) }}
                </slot>
              </div>
            </div>
            <div class="time-bar-wrap">
              <span class="time-bar-value">
                {{ FormattingService.formatDuration2Units(duration(item)) }}
              </span>
              <div class="time-bar-track">
                <div
                  class="time-bar-fill"
                  :class="`tone-${rowTone(item)}`"
                  :style="{ width: timePercentage(duration(item)) + '%' }"
                ></div>
              </div>
            </div>
          </div>
          <div class="row-details"><slot name="details" :item="item" /></div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="slowest-empty">{{ emptyMessage }}</div>
</template>

<script setup lang="ts" generic="T">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import SlowestCountHeader from '@shared/components/SlowestCountHeader.vue';
import type { SlowestRowAccent, SlowestRowTone } from '@shared/types/ui';

const props = withDefaults(
  defineProps<{
    items: T[];
    itemKey: (item: T, index: number) => string | number;
    /** Row headline, also used as the title attribute. */
    name: (item: T) => string;
    /** Nanoseconds; drives the sort, the cap and the bar. */
    duration: (item: T) => number;
    emptyMessage: string;
    /** Full bootstrap-icon class before the name, e.g. `bi bi-cpu`; per-row when a function. */
    icon?: string | ((item: T) => string);
    /** Left gutter colour. Omitted = every row neutral. */
    accent?: (item: T) => SlowestRowAccent;
    /** Time-bar ramp. Omitted = every row on the brand ramp. */
    tone?: (item: T) => SlowestRowTone;
    /** Rows rendered; the bar scale still uses the full list. Ignored when `serverOrdered`. */
    limit?: number;
    /** Header denominator when the caller knows a truer total than `items.length`. */
    total?: number;
    /** Overrides the header's "sorted by duration". */
    note?: string;
    /**
     * Take `items` exactly as given — already ordered and already the page. For a caller whose
     * server did the sorting and the paging: re-sorting here would reorder a page that was cut from
     * a different order, and slicing it again would hide rows the caller deliberately fetched.
     */
    serverOrdered?: boolean;
  }>(),
  {
    icon: undefined,
    accent: undefined,
    tone: undefined,
    limit: 50,
    total: undefined,
    note: undefined,
    serverOrdered: false
  }
);

const emit = defineEmits<{
  rowClick: [item: T];
}>();

const displayedItems = computed(() => {
  if (props.serverOrdered) {
    return props.items;
  }
  return [...props.items].sort((a, b) => props.duration(b) - props.duration(a)).slice(0, props.limit);
});

// Scaled to the slowest row in the whole list, not just the displayed page, so the bars keep
// meaning when the list is truncated. reduce, not Math.max(...list), which overflows the
// argument limit on a large list.
const maxDuration = computed(
  () => props.items.reduce((slowest, item) => Math.max(slowest, props.duration(item)), 0) || 1
);

function timePercentage(durationNanos: number): number {
  return Math.max((durationNanos / maxDuration.value) * 100, 2);
}

function rowIcon(item: T): string | undefined {
  if (typeof props.icon === 'function') {
    return props.icon(item);
  }
  return props.icon;
}

function rowAccent(item: T): SlowestRowAccent {
  if (!props.accent) {
    return 'neutral';
  }
  return props.accent(item);
}

function rowTone(item: T): SlowestRowTone {
  if (!props.tone) {
    return 'default';
  }
  return props.tone(item);
}
</script>

<style scoped>
.slowest-list {
  padding: 0.5rem 1rem;
}

.slowest-row {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--color-border-light);
  padding: 0.75rem 0;
  cursor: pointer;
}

.slowest-row:last-child {
  border-bottom: none;
}

.slowest-row:hover {
  background: var(--color-bg-hover);
}

.left-accent {
  width: 3px;
  border-radius: var(--radius-xs);
  flex-shrink: 0;
  margin-right: 1rem;
  background: var(--color-border-light);
}

.accent-primary {
  background: var(--color-primary);
}

.accent-info {
  background: var(--color-info);
}

.accent-secondary {
  background: var(--color-secondary);
}

.accent-success {
  background: var(--color-success);
}

.accent-warning {
  background: var(--color-warning);
}

.accent-danger {
  background: var(--color-danger);
}

.row-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.row-header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.group-text {
  font-family: var(--font-family-monospace);
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-icon {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-right: 0.15rem;
}

.time-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
  min-width: 120px;
  flex-shrink: 0;
}

.time-bar-track {
  width: 100%;
  height: 6px;
  background: var(--color-lighter);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.time-bar-fill {
  height: 100%;
  border-radius: var(--radius-xs);
  background: linear-gradient(90deg, var(--color-primary), var(--color-secondary));
}

.time-bar-fill.tone-danger {
  background: linear-gradient(90deg, var(--color-danger), var(--color-warning));
}

.time-bar-value {
  font-family: var(--font-family-monospace);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-dark);
  min-width: 70px;
  text-align: right;
}

.row-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.slowest-empty {
  font-size: 0.85rem;
  color: var(--color-text-muted);
  padding: 1rem;
}

@media (max-width: 768px) {
  .row-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }

  .time-bar-wrap {
    min-width: 0;
  }
}
</style>

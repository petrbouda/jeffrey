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

<template>
  <!--
    Grouped by source rather than listed flat: `tenant`, which somebody attached at a call site, and
    `statusCode`, which an event type declares about itself, are not the same kind of thing, and one
    alphabetical list of both reads as a single convention badly applied.

    Every key here has a breakdown to open — the caller filters the search-only ones out, since
    picking one would open a page that could only report that it will not draw anything.
  -->
  <aside class="attr-catalog">
    <header class="catalog-head">
      <h3>Attributes</h3>
      <span class="catalog-count">{{ keys.length }} keys</span>
    </header>

    <div class="catalog-search">
      <i class="bi bi-search"></i>
      <input
        v-model="search"
        type="text"
        class="form-control form-control-sm"
        placeholder="Search keys…"
      />
      <button v-if="search" type="button" class="catalog-search-clear" @click="search = ''">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <EmptyState
      v-if="groups.length === 0"
      icon="bi-search"
      title="No keys"
      :description="
        keys.length === 0
          ? 'No span in this profile recorded an attribute.'
          : 'No key matches the filter.'
      "
    />

    <div v-for="group in groups" :key="group.source" class="catalog-group">
      <div class="group-head">
        <i class="group-dot" :class="`dot-${group.source.toLowerCase()}`"></i>
        {{ group.label }}
      </div>

      <button
        v-for="key in group.keys"
        :key="keyOf(key)"
        type="button"
        class="catalog-key"
        :class="{ selected: isSelected(key) }"
        :title="describe(key)"
        @click="$emit('select', key)"
      >
        <span class="key-top">
          <span class="key-name">{{ key.key }}</span>
          <span class="key-cardinality">
            {{ FormattingService.formatNumber(key.distinctValues) }}
          </span>
        </span>
        <span v-if="key.owner" class="key-owner">{{ key.owner }}</span>
        <!--
          Coverage, not count: a key on 94% of traces and a key on 9% of them are read very
          differently, and the bar says which before the number is read at all.
        -->
        <span class="key-coverage">
          <i :style="{ width: coverage(key) + '%' }"></i>
        </span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import EmptyState from '@shared/components/EmptyState.vue';
import FormattingService from '@shared/services/FormattingService';
import {
  keyLabel,
  sameKey,
  type TraceAttributeKeyId,
  type TraceAttributeKeyRow,
  type TraceAttributeSource
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  keys: TraceAttributeKeyRow[];
  /** Which key the workspace is showing, so the rail can mark it. */
  selected: TraceAttributeKeyId | null;
  /** Traces in the profile, for the coverage bar. Zero leaves every bar empty rather than dividing. */
  totalTraces: number;
}>();

defineEmits<{ select: [key: TraceAttributeKeyRow] }>();

const search = ref('');

/** The order the groups appear in: attached by hand, declared by the event, then the span's own. */
const GROUP_LABELS: Array<{ source: TraceAttributeSource; label: string }> = [
  { source: 'ATTRIBUTE', label: 'Span attributes' },
  { source: 'EVENT_FIELD', label: 'Event fields' },
  { source: 'SPAN_SHAPE', label: 'Span shape' }
];

const filtered = computed(() => {
  const needle = search.value.trim().toLowerCase();
  if (!needle) {
    return props.keys;
  }
  return props.keys.filter(
    key =>
      key.key.toLowerCase().includes(needle) ||
      (key.owner !== null && key.owner.toLowerCase().includes(needle))
  );
});

const groups = computed(() =>
  GROUP_LABELS.map(group => ({
    ...group,
    keys: filtered.value.filter(key => key.source === group.source)
  })).filter(group => group.keys.length > 0)
);

function keyOf(key: TraceAttributeKeyRow): string {
  return `${key.source}~${key.owner ?? ''}~${key.key}`;
}

function isSelected(key: TraceAttributeKeyRow): boolean {
  return props.selected !== null && sameKey(props.selected, key);
}

function coverage(key: TraceAttributeKeyRow): number {
  if (props.totalTraces === 0) {
    return 0;
  }
  return Math.min(100, (key.traceCount / props.totalTraces) * 100);
}

function describe(key: TraceAttributeKeyRow): string {
  return `${keyLabel(key)} · ${key.valueKind.toLowerCase()} · ${FormattingService.formatNumber(key.distinctValues)} values · on ${FormattingService.formatNumber(key.traceCount)} traces`;
}
</script>

<style scoped>
.attr-catalog {
  width: 244px;
  flex-shrink: 0;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 12px 0;
  align-self: flex-start;
  max-height: calc(100vh - 140px);
  overflow-y: auto;
}

.catalog-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 14px 10px;
}

.catalog-head h3 {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-dark);
}

.catalog-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  font-family: var(--font-family-monospace);
}

/*
  Styled here rather than through the shared `.search-container`, whose markup is an input group
  inside a table toolbar. Borrowing the class without its structure is how a shared style ends up
  with a second, contradictory definition.
*/
.catalog-search {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin: 0 14px 10px;
  padding: 0 var(--spacing-2);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  background: var(--color-white);
}

.catalog-search i {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
  flex-shrink: 0;
}

.catalog-search input {
  border: 0;
  padding: var(--spacing-1) 0;
  font-size: var(--font-size-sm);
  box-shadow: none;
}

.catalog-search input:focus {
  box-shadow: none;
  outline: none;
}

.catalog-search-clear {
  border: 0;
  background: transparent;
  color: var(--color-text-light);
  padding: 0;
  font-size: var(--font-size-sm);
  cursor: pointer;
}

.catalog-group + .catalog-group {
  margin-top: var(--spacing-2);
}

.group-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) 14px var(--spacing-1);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.group-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--radius-xs);
  flex-shrink: 0;
}

.dot-attribute {
  background: var(--color-primary);
}

.dot-event_field {
  background: var(--color-info);
}

.dot-span_shape {
  background: var(--color-text-light);
}

.catalog-key {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  border-left: 2px solid transparent;
  background: transparent;
  padding: var(--spacing-1) 14px;
  cursor: pointer;
}

.catalog-key:hover {
  background: var(--color-light);
}

.catalog-key.selected {
  background: var(--color-primary-light);
  border-left-color: var(--color-primary);
}

.key-top {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-2);
}

.key-name {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-key.selected .key-name {
  color: var(--color-primary);
}

.key-cardinality {
  margin-left: auto;
  flex-shrink: 0;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
}

.key-owner {
  display: block;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.key-coverage {
  display: block;
  margin-top: 4px;
  height: 3px;
  border-radius: var(--radius-xs);
  background: var(--color-lighter);
  overflow: hidden;
}

.key-coverage i {
  display: block;
  height: 100%;
  background: var(--color-primary);
  opacity: 0.55;
  border-radius: var(--radius-xs);
}
</style>

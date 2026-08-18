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
  <div>
    <EmptyState
      v-if="values.values.length === 0"
      icon="bi-tag"
      title="No values"
      description="No span in this profile recorded this key with a value."
    />

    <DataTable v-else>
      <template #toolbar>
        <TableToolbar v-model="search" search-placeholder="Filter values…">
          <span class="toolbar-note">
            Ranked by
            <b>{{ SORT_LABELS[sort] }}</b>
            — {{ FormattingService.formatNumber(values.distinctValues) }} values in all
          </span>
        </TableToolbar>
      </template>

      <thead>
        <tr>
          <SortableTableHeader
            column="VALUE"
            label="Value"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
          <SortableTableHeader
            column="TRACES"
            label="Traces"
            align="end"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
          <th style="width: 160px">Share of time</th>
          <SortableTableHeader
            column="P50"
            label="P50"
            align="end"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
          <SortableTableHeader
            column="P95"
            label="P95"
            align="end"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
          <SortableTableHeader
            column="MAX"
            label="Max"
            align="end"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
          <SortableTableHeader
            column="ERRORS"
            label="Errors"
            align="end"
            :sort-column="sort"
            :sort-direction="direction"
            @sort="onSort"
          />
        </tr>
      </thead>

      <tbody>
        <tr v-for="row in filtered" :key="row.value" class="value-row" @click="$emit('pick', row)">
          <td class="value-cell" :title="row.value">{{ row.value }}</td>
          <td class="text-end numeric">{{ FormattingService.formatNumber(row.traceCount) }}</td>
          <td>
            <!--
              Bar against the widest row rather than against the profile: the values of one key do
              not partition the profile — a trace whose spans recorded two tenants is counted under
              both — so drawing these as shares of a whole would be arithmetic nobody can check.
            -->
            <div class="share">
              <span class="share-track">
                <i :style="{ width: shareOfPeak(row) + '%' }"></i>
              </span>
              <span class="share-value">
                {{ FormattingService.formatDuration2Units(row.totalNanos) }}
              </span>
            </div>
          </td>
          <td class="text-end numeric">
            {{ FormattingService.formatDuration2Units(row.p50Nanos) }}
          </td>
          <td class="text-end numeric">
            {{ FormattingService.formatDuration2Units(row.p95Nanos) }}
          </td>
          <td class="text-end numeric">
            {{ FormattingService.formatDuration2Units(row.maxNanos) }}
          </td>
          <td class="text-end">
            <Badge
              :value="errorRate(row)"
              :variant="row.errorTraces > 0 ? 'danger' : 'secondary'"
              size="xs"
              borderless
            />
          </td>
        </tr>

        <!--
          Stated, not omitted. A key missing from a fifth of the profile is a fact about the
          instrumentation, and a table that silently leaves those traces out invites every share on
          the screen to be read as a share of everything.
        -->
        <tr v-if="values.tracesWithoutKey > 0" class="absent-row">
          <td class="value-cell">— not recorded —</td>
          <td class="text-end numeric">
            {{ FormattingService.formatNumber(values.tracesWithoutKey) }}
          </td>
          <td colspan="5" class="absent-note">traces where no span carried this key</td>
        </tr>
      </tbody>

      <template v-if="values.truncated" #footer>
        <div class="truncated">
          Showing the top {{ FormattingService.formatNumber(values.values.length) }} of
          {{ FormattingService.formatNumber(values.distinctValues) }} values. Sort by another column
          to see a different top.
        </div>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import Badge from '@shared/components/Badge.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import SortableTableHeader from '@shared/components/table/SortableTableHeader.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import FormattingService from '@shared/services/FormattingService';
import type {
  TraceAttributeValueRow,
  TraceAttributeValues,
  TraceAttributeValueSortField
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  values: TraceAttributeValues;
  sort: TraceAttributeValueSortField;
  descending: boolean;
}>();

const emit = defineEmits<{
  sort: [field: TraceAttributeValueSortField];
  pick: [row: TraceAttributeValueRow];
}>();

const SORT_LABELS: Record<TraceAttributeValueSortField, string> = {
  TOTAL_TIME: 'total time',
  TRACES: 'trace count',
  P50: 'median',
  P95: 'P95',
  MAX: 'max',
  ERRORS: 'errors',
  VALUE: 'value'
};

const search = ref('');

const direction = computed(() => (props.descending ? 'desc' : 'asc'));

/**
 * Filtered here rather than on the server, unlike the trace list: the rows are already the top of
 * the key by whatever ranks it, so this narrows what is on screen rather than searching the profile.
 */
const filtered = computed(() => {
  const needle = search.value.trim().toLowerCase();
  if (!needle) {
    return props.values.values;
  }
  return props.values.values.filter(row => row.value.toLowerCase().includes(needle));
});

const peakNanos = computed(() =>
  props.values.values.reduce((highest, row) => Math.max(highest, row.totalNanos), 0)
);

function shareOfPeak(row: TraceAttributeValueRow): number {
  if (peakNanos.value === 0) {
    return 0;
  }
  return (row.totalNanos / peakNanos.value) * 100;
}

function errorRate(row: TraceAttributeValueRow): string {
  if (row.traceCount === 0) {
    return '0%';
  }
  return FormattingService.formatPercentValue((row.errorTraces / row.traceCount) * 100);
}

function onSort(column: string): void {
  emit('sort', column as TraceAttributeValueSortField);
}
</script>

<style scoped>
.toolbar-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.toolbar-note b {
  color: var(--color-dark);
}

.value-row {
  cursor: pointer;
}

.value-cell {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  color: var(--color-dark);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.numeric {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
}

.share {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.share-track {
  flex: 1;
  min-width: 56px;
  height: 7px;
  background: var(--color-lighter);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.share-track i {
  display: block;
  height: 100%;
  background: var(--color-primary);
  border-radius: var(--radius-xs);
}

.share-value {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.absent-row {
  color: var(--color-text-light);
}

.absent-row .value-cell {
  color: var(--color-text-light);
  font-weight: 500;
}

.absent-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.truncated {
  padding: var(--spacing-2) var(--spacing-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  border-top: 1px solid var(--color-border-light);
}
</style>

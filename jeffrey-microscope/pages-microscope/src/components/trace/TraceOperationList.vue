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
  <MetricCardList
    :items="operations"
    :item-key="operationKey"
    :count="operation => operation.count"
    count-label="calls"
    :sort-options="sortOptions"
    :initial-sort="sortKey"
    server-ordered
    @item-click="operation => $emit('operationClick', operation)"
    @sort-change="key => $emit('sortChange', key as TraceOperationSortKey)"
  >
    <template #name="{ item }">
      <div class="operation-title">
        <MetricName :segments="parseGroupedName(item.name, '(unnamed)')" :title="item.name" />
        <!--
          What the operation is, at the end of the row that says what it is called. The event type
          is verbatim as the recording spells it -- two operations can read alike and come from
          different instrumentation, a hand-written span named after the request it wraps, say --
          and it is not uppercased, because jeffrey.HttpServerExchange is how the recording, jfr
          print and JMC all spell it.
        -->
        <span class="operation-tags">
          <Badge
            :value="item.eventType"
            variant="secondary"
            size="s"
            borderless
            :uppercase="false"
          />
          <Badge :value="item.kind" :variant="spanKindVariant(item.kind)" size="s" borderless />
        </span>
      </div>
    </template>

    <template #metrics="{ item }">
      <Badge
        key-label="Spans"
        :value="FormattingService.formatNumber(item.spanCount)"
        variant="secondary"
        size="s"
        borderless
      />
      <Badge
        key-label="Total"
        :value="FormattingService.formatDuration2Units(item.totalNanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P50"
        :value="FormattingService.formatDuration2Units(item.p50Nanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="P95"
        :value="FormattingService.formatDuration2Units(item.p95Nanos)"
        variant="info"
        size="s"
        borderless
      />
      <!--
        Shown unconditionally, unlike the summary's p99, because this one is aggregated over every
        trace of the type rather than over the page the list happens to hold.
      -->
      <Badge
        key-label="P99"
        :value="FormattingService.formatDuration2Units(item.p99Nanos)"
        variant="info"
        size="s"
        borderless
      />
      <Badge
        key-label="Max"
        :value="FormattingService.formatDuration2Units(item.maxNanos)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>

    <template #right="{ item }">
      <Badge
        v-if="item.errorCount > 0"
        :value="errorLabel(item.errorCount)"
        variant="danger"
        size="s"
        icon="bi bi-exclamation-triangle"
      />
    </template>
  </MetricCardList>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import Badge from '@shared/components/Badge.vue';
import MetricCardList from '@shared/components/MetricCardList.vue';
import MetricName from '@/components/common/MetricName.vue';
import { parseGroupedName } from '@/services/metricName';
import type { MetricSortOption } from '@shared/components/MetricCardList.vue';
import type {
  TraceOperationRow,
  TraceOperationSortField
} from '@/services/api/model/trace/TraceModels';
import { errorLabel, operationKey, spanKindVariant } from '@/services/trace/traceLabels';

/** The subset of the backend's sort fields this list offers a button for. */
export type TraceOperationSortKey = Extract<
  TraceOperationSortField,
  'TOTAL_TIME' | 'P95' | 'P99' | 'MAX' | 'COUNT' | 'ERRORS'
>;

defineProps<{
  operations: TraceOperationRow[];
  /** Which button is pressed. Owned by the caller, since it is the caller that fetches the order. */
  sortKey: TraceOperationSortKey;
}>();

defineEmits<{
  operationClick: [operation: TraceOperationRow];
  sortChange: [key: TraceOperationSortKey];
}>();

/*
 * Keyed by the backend's own sort names, so a pressed button is the value the next request sends and
 * nothing has to translate between two vocabularies. The comparators are what each key means; the
 * server is what actually applies them, since it holds rows this page has not fetched.
 */
const sortOptions: MetricSortOption[] = [
  { key: 'TOTAL_TIME', label: 'Total', compare: (a, b) => b.totalNanos - a.totalNanos },
  { key: 'P95', label: 'P95', compare: (a, b) => b.p95Nanos - a.p95Nanos },
  { key: 'P99', label: 'P99', compare: (a, b) => b.p99Nanos - a.p99Nanos },
  { key: 'MAX', label: 'Max', compare: (a, b) => b.maxNanos - a.maxNanos },
  { key: 'COUNT', label: 'Count', compare: (a, b) => b.count - a.count },
  { key: 'ERRORS', label: 'Errors', compare: (a, b) => b.errorCount - a.errorCount }
];
</script>

<style scoped>
.operation-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

/*
 * Pushed to the far end of the name row: what an operation is stays in one column down the list,
 * where it can be scanned, instead of starting at a different offset in every row behind names of
 * different lengths.
 */
.operation-tags {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
  padding-left: 0.75rem;
}

/*
 * The name is the only part that may be cut -- it is the long, variable one -- and MetricName
 * already ellipsises itself. The badges are short and fixed, so they never give way.
 */
.operation-title :deep(.badge) {
  flex-shrink: 0;
}
</style>

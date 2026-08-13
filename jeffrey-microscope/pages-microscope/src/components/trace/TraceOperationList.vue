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
    :item-key="operation => operation.name"
    :count="operation => operation.count"
    count-label="calls"
    :sort-options="sortOptions"
    initial-sort="totalNanos"
    @item-click="operation => $emit('operationClick', operation.name)"
  >
    <template #name="{ item }">
      <MetricName :segments="parseGroupedName(item.name, '(unnamed)')" :title="item.name" />
    </template>

    <template #metrics="{ item }">
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
      <Badge
        key-label="Max"
        :value="FormattingService.formatDuration2Units(item.maxNanos)"
        variant="secondary"
        size="s"
        borderless
      />
    </template>

    <template #right="{ item }">
      <Badge :value="item.kind" :variant="kindVariant(item.kind)" size="s" />
      <Badge
        v-if="item.errorCount > 0"
        :value="item.errorCount"
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
import type { SpanKind, TraceOperationRow } from '@/services/api/model/trace/TraceModels';

defineProps<{
  operations: TraceOperationRow[];
}>();

defineEmits<{
  operationClick: [name: string];
}>();

const sortOptions: MetricSortOption[] = [
  { key: 'totalNanos', label: 'Total', compare: (a, b) => b.totalNanos - a.totalNanos },
  { key: 'p95Nanos', label: 'P95', compare: (a, b) => b.p95Nanos - a.p95Nanos },
  { key: 'maxNanos', label: 'Max', compare: (a, b) => b.maxNanos - a.maxNanos },
  { key: 'count', label: 'Count', compare: (a, b) => b.count - a.count },
  { key: 'errorCount', label: 'Errors', compare: (a, b) => b.errorCount - a.errorCount }
];

function kindVariant(kind: SpanKind): string {
  if (kind === 'SERVER') {
    return 'primary';
  }
  return kind === 'CLIENT' ? 'info' : 'secondary';
}
</script>

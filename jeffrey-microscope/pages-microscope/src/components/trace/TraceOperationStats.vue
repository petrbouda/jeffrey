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
  <div class="mb-4">
    <StatsTable :metrics="metrics" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService';
import { operationTotals } from '@/services/trace/traceOperationMetrics';
import type { TraceOperationRow, TraceOverview } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  operations: TraceOperationRow[];
  overview: TraceOverview;
}>();

const totals = computed(() => operationTotals(props.operations));

const metrics = computed(() => [
  {
    icon: 'bar-chart-steps',
    title: 'Operations',
    value: FormattingService.formatNumber(props.overview.distinctOperations),
    variant: 'info' as const,
    breakdown: [
      { label: 'Traces', value: FormattingService.formatNumber(props.overview.totalTraces) },
      { label: 'Errors', value: FormattingService.formatNumber(props.overview.errorTraces) }
    ]
  },
  {
    icon: 'clock-fill',
    title: 'Slowest Operation',
    value: FormattingService.formatDuration2Units(totals.value.slowestNanos),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Total', value: FormattingService.formatDuration2Units(props.overview.totalNanos) },
      {
        label: 'Worst P95',
        value: FormattingService.formatDuration2Units(totals.value.worstP95Nanos)
      }
    ]
  }
]);
</script>

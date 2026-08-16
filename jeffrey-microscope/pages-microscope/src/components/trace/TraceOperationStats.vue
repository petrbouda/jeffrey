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
import type { TraceOverview } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  overview: TraceOverview;
}>();

/*
 * Every figure here comes from the profile-wide overview, never from the loaded page. The tiles
 * used to reduce "slowest" and "worst p95" over the operations currently fetched, which made a
 * headline KPI change value whenever the user sorted, filtered or loaded more — and put page-wide
 * and profile-wide numbers side by side in one row with nothing telling them apart.
 */
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
    title: 'Slowest Trace',
    value: FormattingService.formatDuration2Units(props.overview.maxNanos),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Total', value: FormattingService.formatDuration2Units(props.overview.totalNanos) },
      { label: 'P99', value: FormattingService.formatDuration2Units(props.overview.p99Nanos) }
    ]
  }
]);
</script>

<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
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

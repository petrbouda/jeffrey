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
import type { SpanTagStat } from '@/services/api/model/span/SpanModels';

const props = defineProps<{
  stat: SpanTagStat;
}>();

const metrics = computed(() => {
  const stat = props.stat;
  return [
    {
      icon: 'bounding-box',
      title: 'Total Spans',
      value: FormattingService.formatNumber(stat.count),
      variant: 'info' as const,
      breakdown: [
        { label: 'Avg', value: FormattingService.formatDuration2Units(stat.avgNanos) },
        { label: 'Total', value: FormattingService.formatDuration2Units(stat.totalNanos) }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Span Duration',
      value: FormattingService.formatDuration2Units(stat.maxNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(stat.p99Nanos) },
        { label: 'P95', value: FormattingService.formatDuration2Units(stat.p95Nanos) }
      ]
    }
  ];
});
</script>

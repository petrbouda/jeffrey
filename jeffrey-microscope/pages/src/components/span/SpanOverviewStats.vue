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
import type { SpanOverview } from '@/services/api/model/span/SpanModels';

const props = defineProps<{
  overview: SpanOverview;
}>();

const metrics = computed(() => {
  const overview = props.overview;
  return [
    {
      icon: 'bounding-box',
      title: 'Total Spans',
      value: FormattingService.formatNumber(overview.totalSpans),
      variant: 'info' as const,
      breakdown: [
        { label: 'Tags', value: FormattingService.formatNumber(overview.distinctTags) },
        { label: 'Avg', value: FormattingService.formatDuration2Units(overview.avgNanos) }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Span Duration',
      value: FormattingService.formatDuration2Units(overview.maxNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(overview.p99Nanos) },
        { label: 'P95', value: FormattingService.formatDuration2Units(overview.p95Nanos) }
      ]
    }
  ];
});
</script>

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
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import type MethodTracingHeader from '@/services/api/model/MethodTracingHeader';
import FormattingService from '@shared/services/FormattingService';

const props = defineProps<{
  header: MethodTracingHeader;
}>();

const metricsData = computed(() => {
  const header = props.header;
  return [
    {
      icon: 'play-circle',
      title: 'Total Invocations',
      value: FormattingService.formatNumber(header.totalInvocations),
      variant: 'info' as const,
      breakdown: [{ label: 'Unique Methods', value: header.uniqueMethodCount, color: '#4285F4' }]
    },
    {
      icon: 'stopwatch',
      title: 'Total Duration',
      value: FormattingService.formatDuration2Units(header.totalDuration),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Avg',
          value: FormattingService.formatDuration2Units(header.avgDuration),
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Response Time',
      value: FormattingService.formatDuration2Units(header.maxDuration),
      variant: 'warning' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(header.p99Duration) },
        { label: 'P95', value: FormattingService.formatDuration2Units(header.p95Duration) }
      ]
    },
    {
      icon: 'collection',
      title: 'Unique Methods',
      value: header.uniqueMethodCount,
      variant: 'success' as const,
      breakdown: []
    }
  ];
});
</script>

<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <section class="dashboard-section">
    <StatsTable :metrics="metricsData" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService.ts';
import JdbcHeader from '@/services/api/model/JdbcHeader.ts';

const props = defineProps<{
  jdbcHeader: JdbcHeader;
}>();

const metricsData = computed(() => {
  const header = props.jdbcHeader;

  return [
    {
      icon: 'database',
      title: 'Total Statements',
      value: header.statementCount || 0,
      variant:
        (header.successRate || 0) >= 0.99
          ? ('info' as const)
          : header.errorCount > 0
            ? ('danger' as const)
            : ('warning' as const),
      breakdown: [
        {
          label: 'Success',
          value: FormattingService.formatSuccessRate(header.successRate || 0),
          color: (header.successRate || 0) >= 0.99 ? '#34A853' : '#FBBC05'
        },
        {
          label: 'Errors',
          value: header.errorCount || 0,
          color: header.errorCount > 0 ? '#EA4335' : '#28a745'
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Execution Time',
      value: FormattingService.formatDuration2Units(header.maxExecutionTime),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'P99',
          value: FormattingService.formatDuration2Units(header.p99ExecutionTime),
          color: '#FBBC05'
        },
        {
          label: 'P95',
          value: FormattingService.formatDuration2Units(header.p95ExecutionTime),
          color: '#FBBC05'
        }
      ]
    }
  ];
});
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}
</style>

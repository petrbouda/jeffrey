<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <section class="dashboard-section">
    <StatsTable :metrics="metricsData" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import FormattingService from '@/services/FormattingService.ts';
import JdbcHeader from '@/services/profile/custom/jdbc/JdbcHeader.ts';

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
      variant: (header.successRate || 0) >= 0.99 ? 'info' as const : header.errorCount > 0 ? 'danger' as const : 'warning' as const,
      breakdown: [
        {
          label: 'Success',
          value: `${((header.successRate || 0) * 100).toFixed(1)}%`,
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

<template>
  <div class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import JdbcHeader from '@/services/api/model/JdbcHeader';
import FormattingService from '@/services/FormattingService';

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
      variant: ((header.successRate || 0) >= 0.99
        ? 'info'
        : header.errorCount > 0
          ? 'danger'
          : 'warning') as const,
      breakdown: [
        {
          label: 'Success',
          value: `${((header.successRate || 0) * 100).toFixed(1)}%`,
          color: (header.successRate || 0) >= 0.99 ? '#28a745' : '#ffc107'
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
        { label: 'P99', value: FormattingService.formatDuration2Units(header.p99ExecutionTime) },
        { label: 'P95', value: FormattingService.formatDuration2Units(header.p95ExecutionTime) }
      ]
    }
  ];
});
</script>

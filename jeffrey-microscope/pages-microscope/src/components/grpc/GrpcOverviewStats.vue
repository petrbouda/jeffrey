<template>
  <div class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import type { GrpcHeader } from '@/services/api/ProfileGrpcClient';
import FormattingService from '@/services/FormattingService';

const props = defineProps<{
  header: GrpcHeader;
}>();

const metricsData = computed(() => {
  const header = props.header;
  return [
    {
      icon: 'telephone',
      title: 'Total Calls',
      value: header.callCount || 0,
      variant: 'info' as const
    },
    {
      icon: 'clock-fill',
      title: 'Response Time',
      value: FormattingService.formatDuration2Units(header.maxResponseTime),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(header.p99ResponseTime) },
        { label: 'P95', value: FormattingService.formatDuration2Units(header.p95ResponseTime) }
      ]
    },
    {
      icon: 'check-circle-fill',
      title: 'Success Rate',
      value: FormattingService.formatSuccessRate(header.successRate || 0),
      variant: ((header.successRate || 0) === 1
        ? 'success'
        : header.errorCount > 0
          ? 'danger'
          : 'warning') as const,
      breakdown: [
        {
          label: 'Errors',
          value: header.errorCount || 0,
          color: header.errorCount > 0 ? '#EA4335' : '#28a745'
        }
      ]
    },
    {
      icon: 'arrow-left-right',
      title: 'Data Transferred',
      value: FormattingService.formatBytes(
        (header.totalBytesSent || 0) + (header.totalBytesReceived || 0)
      ),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#34A853'
        },
        {
          label: 'Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#34A853'
        }
      ]
    }
  ];
});
</script>

<template>
  <div class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import HttpHeader from '@/services/api/model/HttpHeader';
import FormattingService from '@/services/FormattingService';

const props = defineProps<{
  header: HttpHeader;
}>();

const metricsData = computed(() => {
  const header = props.header;
  return [
    {
      icon: 'globe',
      title: 'Total Requests',
      value: header.requestCount || 0,
      variant: 'info' as const,
      breakdown: [{ label: 'Requests', value: header.requestCount || 0, color: '#4285F4' }]
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
      value: `${((header.successRate || 0) * 100).toFixed(1)}%`,
      variant: ((header.successRate || 0) === 1
        ? 'success'
        : header.count5xx > 0
          ? 'danger'
          : 'warning') as const,
      breakdown: [
        { label: '4xx Errors', value: header.count4xx || 0, color: '#EA4335' },
        { label: '5xx Errors', value: header.count5xx || 0, color: '#EA4335' }
      ]
    },
    {
      icon: 'arrow-left-right',
      title: 'Data Transferred',
      value:
        header.totalBytesTransferred < 0
          ? '?'
          : FormattingService.formatBytes(header.totalBytesTransferred),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#34A853'
        },
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#34A853'
        }
      ]
    }
  ];
});
</script>

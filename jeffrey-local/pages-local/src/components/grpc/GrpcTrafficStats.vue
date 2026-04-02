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
      icon: 'box-arrow-up',
      title: 'Avg Request Size',
      value: FormattingService.formatBytes(header.avgRequestSize),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Max',
          value: FormattingService.formatBytes(header.maxRequestSize),
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'box-arrow-down',
      title: 'Avg Response Size',
      value: FormattingService.formatBytes(header.avgResponseSize),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Max',
          value: FormattingService.formatBytes(header.maxResponseSize),
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'box-arrow-up',
      title: 'Max Request Size',
      value: FormattingService.formatBytes(header.maxRequestSize),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Total Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent),
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'box-arrow-down',
      title: 'Max Response Size',
      value: FormattingService.formatBytes(header.maxResponseSize),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Total Received',
          value:
            header.totalBytesReceived < 0
              ? '?'
              : FormattingService.formatBytes(header.totalBytesReceived),
          color: '#FBBC05'
        }
      ]
    }
  ];
});
</script>

<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
  <div class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import HttpHeader from '@/services/api/model/HttpHeader';
import FormattingService from '@shared/services/FormattingService';

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
      value: FormattingService.formatSuccessRate(header.successRate || 0),
      variant: ((header.successRate || 0) === 1
        ? 'success'
        : header.count5xx > 0
          ? 'danger'
          : 'warning') as 'success' | 'danger' | 'warning',
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

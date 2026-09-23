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
  <section class="dashboard-section">
    <StatsTable :metrics="metricsData" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService.ts';
import HttpHeader from '@/services/api/model/HttpHeader.ts';

const props = defineProps<{
  httpHeader: HttpHeader;
}>();

const metricsData = computed(() => {
  const header = props.httpHeader;

  return [
    {
      icon: 'graph-up',
      title: 'Total Requests',
      value: header.requestCount || 0,
      variant: 'info' as const
    },
    {
      icon: 'clock-fill',
      title: 'Response Time',
      value: FormattingService.formatDuration2Units(header.maxResponseTime),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'P99',
          value: FormattingService.formatDuration2Units(header.p99ResponseTime)
        },
        {
          label: 'P95',
          value: FormattingService.formatDuration2Units(header.p95ResponseTime)
        }
      ]
    },
    {
      icon: 'check-circle',
      title: 'Success Rate',
      value: FormattingService.formatSuccessRate(header.successRate || 0),
      variant: ((header.successRate || 0) === 1
        ? 'success'
        : header.count5xx > 0
          ? 'danger'
          : 'warning') as 'success' | 'danger' | 'warning',
      breakdown: [
        {
          label: '4xx Errors',
          value: header.count4xx,
          color: header.count4xx > 0 ? '#FBBC05' : '#28a745'
        },
        {
          label: '5xx Errors',
          value: header.count5xx,
          color: header.count5xx > 0 ? '#EA4335' : '#28a745'
        }
      ]
    },
    {
      icon: 'arrow-down-up',
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
              : FormattingService.formatBytes(header.totalBytesReceived)
        },
        {
          label: 'Sent',
          value:
            header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(header.totalBytesSent)
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

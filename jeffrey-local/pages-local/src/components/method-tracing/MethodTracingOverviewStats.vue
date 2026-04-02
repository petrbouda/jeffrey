<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import type MethodTracingHeader from '@/services/api/model/MethodTracingHeader';
import FormattingService from '@/services/FormattingService';

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

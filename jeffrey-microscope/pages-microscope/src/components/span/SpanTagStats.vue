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
    <StatsTable :metrics="metrics" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import FormattingService from '@/services/FormattingService';
import type { SpanTagStat } from '@/services/api/model/span/SpanModels';

const props = defineProps<{
  stat: SpanTagStat;
}>();

const metrics = computed(() => {
  const stat = props.stat;
  return [
    {
      icon: 'bounding-box',
      title: 'Total Spans',
      value: FormattingService.formatNumber(stat.count),
      variant: 'info' as const,
      breakdown: [
        { label: 'Avg', value: FormattingService.formatDuration2Units(stat.avgNanos) },
        { label: 'Total', value: FormattingService.formatDuration2Units(stat.totalNanos) }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Span Duration',
      value: FormattingService.formatDuration2Units(stat.maxNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(stat.p99Nanos) },
        { label: 'P95', value: FormattingService.formatDuration2Units(stat.p95Nanos) }
      ]
    }
  ];
});
</script>

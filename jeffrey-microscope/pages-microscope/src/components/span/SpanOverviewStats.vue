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
import FormattingService from '@shared/services/FormattingService';
import type { SpanOverview } from '@/services/api/model/span/SpanModels';

const props = defineProps<{
  overview: SpanOverview;
}>();

const metrics = computed(() => {
  const overview = props.overview;
  return [
    {
      icon: 'bounding-box',
      title: 'Total Spans',
      value: FormattingService.formatNumber(overview.totalSpans),
      variant: 'info' as const,
      breakdown: [
        { label: 'Tags', value: FormattingService.formatNumber(overview.distinctTags) },
        { label: 'Avg', value: FormattingService.formatDuration2Units(overview.avgNanos) }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Span Duration',
      value: FormattingService.formatDuration2Units(overview.maxNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'P99', value: FormattingService.formatDuration2Units(overview.p99Nanos) },
        { label: 'P95', value: FormattingService.formatDuration2Units(overview.p95Nanos) }
      ]
    }
  ];
});
</script>

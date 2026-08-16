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
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService';
import type { TraceOverview } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  overview: TraceOverview;
}>();

/** Below this a rate rounds to 0%, where the count on its own is the more honest reading. */
const MIN_REPORTED_PERCENT = 0.1;

function failedLabel(failed: number, total: number): string {
  const count = FormattingService.formatNumber(failed);
  if (failed === 0 || total === 0) {
    return count;
  }
  const rate = (failed / total) * 100;
  if (rate < MIN_REPORTED_PERCENT) {
    return `${count} (<0.1%)`;
  }
  return `${count} (${rate.toFixed(rate < 10 ? 1 : 0)}%)`;
}

// Two tiles, two breakdown items each -- the discipline the Spans overview keeps. The list below
// answers "which run", so the tiles only have to answer "how much" and "how slow".
const metrics = computed(() => {
  const overview = props.overview;
  return [
    {
      icon: 'diagram-3',
      title: 'Total Traces',
      value: FormattingService.formatNumber(overview.totalTraces),
      variant: 'info' as const,
      breakdown: [
        { label: 'Spans', value: FormattingService.formatNumber(overview.totalSpans) },
        {
          label: 'Failed',
          // With the rate: a bare count of failures cannot be read without knowing what it is out
          // of, and 12 means something very different against 15 traces than against 15,000.
          value: failedLabel(overview.errorTraces, overview.totalTraces),
          // Failures painted in the tile's success green read as "all good" at a glance.
          color: overview.errorTraces > 0 ? 'var(--color-danger)' : undefined
        }
      ]
    },
    {
      icon: 'clock-fill',
      title: 'Trace Duration',
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

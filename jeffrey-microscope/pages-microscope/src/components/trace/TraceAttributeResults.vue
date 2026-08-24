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
  <div>
    <StatsTable :metrics="metrics" />

    <MainCard class="mt-3">
      <template #header>
        <MainCardHeader icon="bi-clock-history" title="When the matches happened" />
      </template>

      <!--
        The same chart the operation drill-down's Traces Timeline draws, rather than a strip of its
        own: a match is read against the recording's clock either way, and two pages that plot when
        traces happened should not do it two different ways.

        Matched is the primary series and is always drawn — with no conditions yet it is every trace,
        which is the honest starting picture rather than an empty panel. All traces stays behind it
        as the backdrop, because a burst of matches is only a burst if the profile was not equally
        busy everywhere; without it the shape means nothing.
      -->
      <TimeSeriesChart
        :primary-data="matchedSeries"
        primary-title="Matched"
        :secondary-data="totalSeries"
        secondary-title="All traces"
        primary-color="#5e64ff"
        secondary-color="#b6c1d2"
        time-unit="milliseconds"
        :visible-minutes="60"
        :primary-axis-type="AxisFormatType.NUMBER"
        :secondary-axis-type="AxisFormatType.NUMBER"
      />

    </MainCard>

    <EmptyState
      v-if="matches.length === 0"
      class="mt-3"
      icon="bi-search"
      title="No matching traces"
      description="No trace in this profile carries every condition. Try widening the scope, or drop a condition."
    />

    <!--
      The same card the Traces by Operation list draws, so a trace reads the same way whichever
      page found it. What differs is what a single trace has to say: the zone holds its duration
      rather than a call count — tinted by how that duration sits in the matched set, so the slow
      traces pop out of the list before a single number is read.
    -->
    <div v-else class="matches mt-3">
      <TraceCardList
        :items="matches"
        :trace="(match: TraceAttributeMatch) => match.trace"
        :p50-nanos="stats.p50Nanos"
        :p95-nanos="stats.p95Nanos"
        :max-displayed="matches.length"
        @open="(trace: TraceRow) => $emit('open', trace)"
      >
        <!--
          Which span carried the value, on the row itself. Every other trace search makes you open
          the waterfall to find that out, once per trace; showing it here is what makes the list the
          answer rather than the start of one. A band of its own along the card's bottom edge, in
          the faint brand tint: the evidence cannot collide with the statistics, and the tint quietly
          connects it to the search that produced it.
        -->
        <template #footer="{ item }">
          <div v-if="item.hits.length > 0" class="match-footer">
            <span class="match-footer-label">Matched on</span>
            <span v-for="hit in item.hits" :key="hit.spanId + hit.key" class="match-hit">
              <span class="hit-span">span {{ shortSpanId(hit.spanId) }}</span>
              {{ hit.key }}=<b>{{ hit.value }}</b>
            </span>
          </div>
        </template>
      </TraceCardList>

      <LoadMoreFooter
        :shown="matches.length"
        :total="totalMatching"
        noun="traces"
        :loading="loadingMore"
        @load-more="$emit('loadMore')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import EmptyState from '@shared/components/EmptyState.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceCardList from '@/components/trace/TraceCardList.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';
import type {
  TraceAttributeMatch,
  TraceAttributeStats,
  TraceAttributeTimelineBucket
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  matches: TraceAttributeMatch[];
  totalMatching: number;
  stats: TraceAttributeStats;
  /** The unfiltered profile, so every number can be read against what it is a share of. */
  baseline: TraceAttributeStats | null;
  timeline: TraceAttributeTimelineBucket[];
  loadingMore: boolean;
}>();

defineEmits<{ open: [trace: TraceRow]; loadMore: [] }>();

/** How many hex digits of a span id are enough to tell two spans of one trace apart on sight. */
const SHORT_SPAN_ID_DIGITS = 6;

/** The tail of a span id, which is what the matched-on band shows beside each hit. */
function shortSpanId(spanId: string): string {
  return spanId.slice(-SHORT_SPAN_ID_DIGITS);
}

const matchedSeries = computed<number[][]>(() =>
  props.timeline.map(bucket => [bucket.fromMillisFromBeginning, bucket.matched])
);

const totalSeries = computed<number[][]>(() =>
  props.timeline.map(bucket => [bucket.fromMillisFromBeginning, bucket.total])
);

const metrics = computed(() => {
  const stats = props.stats;
  const baseline = props.baseline;
  const share = baseline && baseline.traces > 0 ? (stats.traces / baseline.traces) * 100 : 0;

  return [
    {
      icon: 'funnel',
      title: 'Traces matched',
      value: FormattingService.formatNumber(stats.traces),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Of the profile',
          value: baseline === null ? '—' : `${share.toFixed(1)}%`
        },
        {
          label: 'With errors',
          value: FormattingService.formatNumber(stats.tracesWithErrors),
          color: stats.tracesWithErrors > 0 ? 'var(--color-danger)' : undefined
        }
      ]
    },
    {
      icon: 'speedometer2',
      title: 'Matched P95',
      value: FormattingService.formatDuration2Units(stats.p95Nanos),
      variant: 'info' as const,
      breakdown: [
        { label: 'Median', value: FormattingService.formatDuration2Units(stats.p50Nanos) },
        { label: 'Slowest', value: FormattingService.formatDuration2Units(stats.maxNanos) }
      ]
    }
  ];
});
</script>

<style scoped>
.matches {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.match-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  padding: 7px 16px;
  background: var(--color-primary-lighter);
  border-top: 1px solid var(--color-border-light);
}

.match-footer-label {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-light);
  flex-shrink: 0;
}

.match-hit {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  background: var(--color-light);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 1px var(--spacing-2);
  white-space: nowrap;
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.match-hit b {
  color: var(--color-dark);
  font-weight: 600;
}

.hit-span {
  color: var(--color-primary);
  margin-right: var(--spacing-1);
}

.match-arrow {
  color: var(--color-text-light);
  flex-shrink: 0;
}
</style>

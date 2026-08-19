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
        Both counts on one strip. A burst of matches is only a burst if the profile was not equally
        busy everywhere, so the backdrop is not decoration — without it the shape means nothing.
      -->
      <div class="density">
        <span
          v-for="bucket in timeline"
          :key="bucket.fromMillisFromBeginning"
          class="density-slice"
          :title="sliceTitle(bucket)"
        >
          <span class="density-total" :style="{ height: heightOf(bucket.total) + '%' }"></span>
          <span class="density-matched" :style="{ height: heightOf(bucket.matched) + '%' }"></span>
        </span>
      </div>

      <div class="density-legend">
        <span><i class="swatch matched"></i> matched</span>
        <span><i class="swatch total"></i> all traces</span>
      </div>
    </MainCard>

    <EmptyState
      v-if="matches.length === 0"
      class="mt-3"
      icon="bi-search"
      title="No matching traces"
      description="No trace in this profile carries every condition. Try widening the scope, or drop a condition."
    />

    <div v-else class="matches mt-3">
      <button
        v-for="match in matches"
        :key="match.trace.traceId"
        type="button"
        class="match-row"
        @click="$emit('open', match.trace)"
      >
        <span class="match-duration">
          {{ FormattingService.formatDuration2Units(match.trace.durationNanos) }}
        </span>

        <span class="match-body">
          <span class="match-name" :title="match.trace.rootName">{{ match.trace.rootName }}</span>
          <span class="match-meta">
            <Badge
              :value="match.trace.rootEventType"
              variant="info"
              size="xs"
              borderless
              :uppercase="false"
            />
            <Badge
              key-label="Spans"
              :value="FormattingService.formatNumber(match.trace.spanCount)"
              variant="secondary"
              size="xs"
              borderless
            />
            <Badge
              v-if="match.trace.errorCount > 0"
              :value="errorLabel(match.trace.errorCount)"
              variant="danger"
              size="xs"
              icon="bi bi-exclamation-triangle"
            />

            <!--
              Which span carried the value, on the row itself. Every other trace search makes you
              open the waterfall to find that out, once per trace; showing it here is what makes the
              list the answer rather than the start of one.
            -->
            <span v-for="hit in match.hits" :key="hit.spanId + hit.key" class="match-hit">
              <span class="hit-span">span {{ shortSpanId(hit.spanId) }}</span>
              {{ hit.key }}=<b>{{ hit.value }}</b>
            </span>
          </span>
        </span>

        <i class="bi bi-chevron-right match-arrow"></i>
      </button>

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

import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';
import type {
  TraceAttributeMatch,
  TraceAttributeStats,
  TraceAttributeTimelineBucket
} from '@/services/api/model/trace/TraceAttributeModels';
import { errorLabel } from '@/services/trace/traceLabels';

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

const peak = computed(() =>
  props.timeline.reduce((highest, bucket) => Math.max(highest, bucket.total), 0)
);

function heightOf(count: number): number {
  if (peak.value === 0) {
    return 0;
  }
  return (count / peak.value) * 100;
}

function sliceTitle(bucket: TraceAttributeTimelineBucket): string {
  const at = FormattingService.formatDurationMillisCompact(bucket.fromMillisFromBeginning);
  return `${at} — ${FormattingService.formatNumber(bucket.matched)} matched of ${FormattingService.formatNumber(bucket.total)}`;
}

function shortSpanId(spanId: string): string {
  return spanId.slice(-SHORT_SPAN_ID_DIGITS);
}

/**
 * A percentile against the unfiltered profile.
 *
 * A matched P95 of 1.4 s is only alarming next to the 310 ms the profile as a whole manages, and a
 * reader who has to hold the second number in their head to read the first will not.
 */
function versusBaseline(matched: number, base: number | undefined): string {
  if (base === undefined || base === 0 || matched === 0) {
    return 'no baseline to compare against';
  }
  const ratio = matched / base;
  const direction = ratio >= 1 ? '×' : '× of';
  return `${ratio.toFixed(1)}${direction} the profile's ${FormattingService.formatDuration2Units(base)}`;
}

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
        { label: 'Against the profile', value: versusBaseline(stats.p95Nanos, baseline?.p95Nanos) }
      ]
    }
  ];
});
</script>

<style scoped>
.density {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 54px;
}

.density-slice {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
  min-width: 2px;
}

.density-total {
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  background: var(--color-lighter);
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
}

.density-matched {
  position: relative;
  width: 100%;
  background: var(--color-primary);
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
}

.density-legend {
  display: flex;
  gap: var(--spacing-4);
  margin-top: var(--spacing-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.density-legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
}

.swatch {
  width: 9px;
  height: 9px;
  border-radius: var(--radius-xs);
  display: block;
}

.swatch.matched {
  background: var(--color-primary);
}

.swatch.total {
  background: var(--color-lighter);
  border: 1px solid var(--color-border);
}

.matches {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.match-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  width: 100%;
  text-align: left;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
}

.match-row:hover {
  box-shadow: var(--shadow-md);
}

.match-duration {
  width: 82px;
  flex-shrink: 0;
  text-align: right;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-dark);
  font-variant-numeric: tabular-nums;
}

.match-body {
  flex: 1;
  min-width: 0;
}

.match-name {
  display: block;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.match-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-1);
  margin-top: var(--spacing-1);
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

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

    <!--
      The same card the Traces by Operation list draws, so a trace reads the same way whichever
      page found it. What differs is what a single trace has to say: the zone holds its duration
      rather than a call count — tinted by how that duration sits in the matched set, so the slow
      traces pop out of the list before a single number is read.
    -->
    <div v-else class="matches mt-3">
      <MetricCardList
        :items="matches"
        :item-key="(match: TraceAttributeMatch) => match.trace.traceId"
        :count-class="durationSeverity"
        :sort-options="[]"
        server-ordered
        @item-click="(match: TraceAttributeMatch) => $emit('open', match.trace)"
      >
        <template #count="{ item }">
          <span class="zone-duration">
            {{ FormattingService.formatDuration2Units(item.trace.durationNanos) }}
          </span>
          <span class="zone-caption">Duration</span>
        </template>

        <template #name="{ item }">
          <div class="match-title">
            <MetricName
              :segments="parseOperationName(item.trace.rootName, item.trace.rootEventType)"
              :title="item.trace.rootName"
            />
            <span class="match-tags">
              <Badge
                :value="item.trace.rootEventType"
                variant="secondary"
                size="s"
                borderless
                :uppercase="false"
              />
              <Badge
                :value="item.trace.rootKind"
                :variant="spanKindVariant(item.trace.rootKind)"
                size="s"
                borderless
              />
            </span>
          </div>
        </template>

        <template #metrics="{ item }">
          <Badge
            key-label="Spans"
            :value="FormattingService.formatNumber(item.trace.spanCount)"
            variant="secondary"
            size="s"
            borderless
          />
          <Badge
            key-label="Started"
            :value="`+${FormattingService.formatDuration2Units(item.trace.startMillisFromBeginning * NANOS_PER_MILLI)}`"
            variant="secondary"
            size="s"
            borderless
          />
        </template>

        <!--
          Which span carried the value, on the row itself. Every other trace search makes you
          open the waterfall to find that out, once per trace; showing it here is what makes the
          list the answer rather than the start of one. A band of its own along the card's bottom
          edge, in the faint brand tint: the evidence cannot collide with the statistics, and the
          tint quietly connects it to the search that produced it.
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

        <template #right="{ item }">
          <Badge
            v-if="item.trace.errorCount > 0"
            :value="errorLabel(item.trace.errorCount)"
            variant="danger"
            size="s"
            icon="bi bi-exclamation-triangle"
          />
        </template>
      </MetricCardList>

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
import MetricCardList from '@shared/components/MetricCardList.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import FormattingService from '@shared/services/FormattingService';
import MetricName from '@/components/common/MetricName.vue';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';
import type {
  TraceAttributeMatch,
  TraceAttributeStats,
  TraceAttributeTimelineBucket
} from '@/services/api/model/trace/TraceAttributeModels';
import { errorLabel, parseOperationName, spanKindVariant } from '@/services/trace/traceLabels';

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

/** The Started chip holds an offset in millis; the duration formatter reads nanos. */
const NANOS_PER_MILLI = 1_000_000;

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
 * The tint of a trace's duration zone: how its duration sits within the matched set. Green up to
 * the set's median, the neutral brand tint in between, red past its P95 — thresholds from the
 * whole match rather than the visible page, so paging does not recolour the rows already read.
 */
function durationSeverity(match: TraceAttributeMatch): string | undefined {
  if (match.trace.durationNanos > props.stats.p95Nanos) {
    return 'zone-slow';
  }
  if (match.trace.durationNanos <= props.stats.p50Nanos) {
    return 'zone-fast';
  }
  return undefined;
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
        { label: 'Slowest', value: FormattingService.formatDuration2Units(stats.maxNanos) }
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

/* The zone's headline, in the caller because slotted content styles itself. */
.zone-duration {
  font-family: var(--font-family-monospace);
  font-size: 1.05rem;
  font-weight: 700;
  line-height: 1.15;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/*
 * One width for every zone: durations run from "4us" to "656ms 759us", and a zone sized by its
 * text puts each name at a different offset, which breaks the column the list is read down.
 */
.matches :deep(.mcl-count) {
  width: 132px;
  min-width: 132px;
  padding: 0 var(--spacing-2);
}

.zone-caption {
  font-size: 0.56rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  opacity: 0.85;
  margin-top: 4px;
}

/* How the duration sits in the matched set — see durationSeverity. */
.matches :deep(.mcl-count.zone-fast) {
  background: var(--color-success-light);
  color: var(--color-success-dark);
}

.matches :deep(.mcl-count.zone-slow) {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.match-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

/* Pinned to the end of the name row, one scannable column down the list — as the operations do. */
.match-tags {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
  padding-left: var(--spacing-3);
}

.match-title :deep(.badge) {
  flex-shrink: 0;
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

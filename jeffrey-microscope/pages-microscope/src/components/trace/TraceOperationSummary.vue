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

<!--
  What an operation is and whether it is healthy, without opening anything: the numbers, how its
  latency is spread, which threads it ran on, where its time went, and its slowest runs.

  Nearly all of it is arithmetic over the trace list the drill-down already fetched — only the span
  breakdown and the thread split need the server, and they arrive together in one call.
-->
<template>
  <div class="op-summary">
    <StatsTable :metrics="metrics" />

    <div class="summary-split">
      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi bi-bar-chart-steps" title="Latency distribution">
            <template #actions>
              <span class="card-note">{{ sampleNote ?? `${traces.length} traces` }}</span>
            </template>
          </MainCardHeader>
        </template>

        <EmptyState
          v-if="histogram.buckets.length === 0"
          title="Nothing to plot"
          description="This operation has no completed traces."
          icon="bi-bar-chart-steps"
        />
        <div v-else class="histogram">
          <div class="histogram-bars">
            <!--
              Height is share of the fullest bucket, so a single outlier stays visible next to a
              cluster instead of collapsing to a hairline.
            -->
            <div
              v-for="(bucket, index) in histogram.buckets"
              :key="index"
              class="histogram-bar"
              :class="{ tail: bucket.to >= chartP95Nanos }"
              :style="{ height: barHeight(bucket.count) }"
              :title="bucketTitle(bucket)"
            >
              <span v-if="bucket.count > 0" class="histogram-count">{{ bucket.count }}</span>
            </div>
          </div>
          <div class="histogram-axis">
            <span>{{ duration(histogram.from) }}</span>
            <span>{{ duration((histogram.from + histogram.to) / 2) }}</span>
            <span>{{ duration(histogram.to) }}</span>
          </div>
          <div class="histogram-marks">
            <span
              >p50 <b>{{ duration(chartP50Nanos) }}</b></span
            >
            <span
              >p95 <b>{{ duration(chartP95Nanos) }}</b></span
            >
            <span
              >max <b>{{ duration(chartMaxNanos) }}</b></span
            >
          </div>
        </div>
      </MainCard>

      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi bi-diagram-2" title="Threads">
            <template #actions>
              <span class="card-note">{{ threadsSummary?.distinctThreads ?? 0 }} distinct</span>
            </template>
          </MainCardHeader>
        </template>

        <EmptyState
          v-if="threadData === null"
          title="No spans"
          description="This operation recorded no spans to attribute."
          icon="bi-diagram-2"
        />
        <DonutWithLegend
          v-else
          :data="threadData"
          :chart-height="180"
          :tooltip-formatter="spanCountTooltip"
        />
      </MainCard>
    </div>

    <div class="summary-split summary-split-wide">
      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi bi-list-nested" title="Top spans by time">
            <template #actions>
              <!--
                The two rankings answer different questions and routinely disagree: a span that only
                wraps three slow queries tops the inclusive list and barely registers on the self
                one. Switching between them is how you tell "where the request is" from "what to go
                and fix", so the toggle sits on the card rather than being a preference.
              -->
              <span class="span-mode">
                <button
                  v-for="mode in SPAN_MODES"
                  :key="mode.key"
                  type="button"
                  :class="{ active: spanMode === mode.key }"
                  :title="mode.title"
                  @click="spanMode = mode.key"
                >
                  {{ mode.label }}
                </button>
              </span>
            </template>
          </MainCardHeader>
        </template>

        <LoadingState v-if="loading" message="Loading the span breakdown..." />
        <!-- A failed fetch must not claim every trace is a single span. -->
        <ErrorState v-else-if="error" :message="error" />
        <EmptyState
          v-else-if="spans.length === 0"
          title="No nested spans"
          description="Every trace of this operation is a single span."
          icon="bi-list-nested"
        />
        <div v-else class="span-bars">
          <div v-for="span in rankedSpans" :key="span.name" class="span-bar">
            <span class="span-name" :title="span.name">{{ span.name }}</span>
            <div class="span-track">
              <div class="span-fill" :style="{ width: spanShare(span) }"></div>
            </div>
            <span class="span-value">
              {{ duration(spanTime(span)) }}
              <span class="span-count">· {{ span.occurrences }}</span>
            </span>
            <!--
              The total alone cannot separate "one span that is always slow" from "a fast span called
              a thousand times", which are different problems with different fixes. The median and
              the worst case say which one this row is, and how many traces it appears in says
              whether it is the operation's habit or one trace's accident.
            -->
            <span class="span-detail">
              <span :title="spanReachTitle(span)">{{ spanReach(span) }}</span>
              <span
                >p50
                <b>{{ duration(spanMode === 'self' ? span.p50SelfNanos : span.p50Nanos) }}</b></span
              >
              <span v-if="spanMode === 'total'"
                >max <b>{{ duration(span.maxNanos) }}</b></span
              >
              <span v-else :title="ownShareTitle(span)"
                >own <b>{{ ownShare(span) }}</b></span
              >
            </span>
          </div>
        </div>
      </MainCard>

      <MainCard :bottom-margin="false">
        <template #header>
          <MainCardHeader icon="bi bi-hourglass-split" title="Slowest traces">
            <template #actions>
              <button
                v-if="traces.length > SLOWEST_SHOWN"
                type="button"
                class="see-all"
                @click="emit('showAllTraces')"
              >
                All {{ traces.length }} <i class="bi bi-arrow-right"></i>
              </button>
            </template>
          </MainCardHeader>
        </template>
        <!--
          Cut to the worst few here rather than by the list: it ranks and slices what it is given, so
          handing it the whole sample would show its own count header a second time under this one.
        -->
        <TraceSlowestList
          :traces="slowestTraces"
          :total="traces.length"
          :note="sampleNote ?? undefined"
          @row-click="emit('openTrace', $event)"
        />
      </MainCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import DonutWithLegend from '@shared/components/DonutWithLegend.vue';
import type { DonutChartData } from '@shared/components/DonutWithLegend.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import FormattingService from '@shared/services/FormattingService';

import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  latencyHistogram,
  peakConcurrency,
  quantileNanos,
  slowestFirst
} from '@/services/trace/traceOperationStats';
import type {
  TraceOperationId,
  TraceOperationRow,
  TraceOperationSpanRow,
  TraceOperationThreads,
  TraceOverview,
  TraceRow
} from '@/services/api/model/trace/TraceModels';
import { operationKey } from '@/services/trace/traceLabels';

/** Upper bound on histogram columns: past this they are too thin to read. */
const MAX_HISTOGRAM_BUCKETS = 24;
/** Lower bound: fewer than this cannot show a shape at all. */
const MIN_HISTOGRAM_BUCKETS = 6;
/** The breakdown is a ranking, not a catalogue; the tail of a long list is never read. */
const DISPLAYED_SPANS = 8;
/** A summary shows the worst few; the Slowest Traces tab is where the whole ranking lives. */
const SLOWEST_SHOWN = 5;
/** Below this an own-work share rounds to 0%, where "<1%" is the more honest reading. */
const MIN_REPORTED_SHARE_PERCENT = 1;

const props = defineProps<{
  profileId: string;
  operation: TraceOperationId;
  /**
   * The operation's own row, aggregated by the database over *every* trace of the type. The headline
   * numbers come from here: `traces` below is capped and ordered by start time, so folding it gave a
   * call count and percentiles that contradicted the row the user had just clicked.
   *
   * Null only for a deep link into an operation the capped list did not contain, where the sample is
   * all there is.
   */
  totals: TraceOperationRow | null;
  /** Already fetched by the drill-down; summarised here rather than fetched a second time. */
  traces: TraceRow[];
  /** Whether `traces` is a truncated sample rather than every trace of the operation. */
  truncated: boolean;
  /** Profile-wide totals, for the share this operation accounts for. */
  overview: TraceOverview | null;
}>();

const emit = defineEmits<{ openTrace: [trace: TraceRow]; showAllTraces: [] }>();

const loading = ref(true);
const error = ref<string | null>(null);
const spans = ref<TraceOperationSpanRow[]>([]);
const threadsSummary = ref<TraceOperationThreads | null>(null);

/*
 * Sorted here rather than left to the list: handing it a pre-cut five means cutting the right five.
 */
const slowestTraces = computed(() => slowestFirst(props.traces).slice(0, SLOWEST_SHOWN));

const durationsNanos = computed(() => props.traces.map(trace => trace.durationNanos));

/*
 * Two populations, deliberately kept apart.
 *
 * `totals` is the database's answer over every trace of the type. The sample below is the first N by
 * start time, which is what the timeline and the histogram need — a shape, drawn from real traces —
 * but folding it into a headline number produced a "Calls" that disagreed with the operation card
 * one click away, and percentiles over a chronological slice rather than over the population.
 *
 * So: numbers come from `totals`, shape comes from the sample, and the sample says it is one.
 */
const p50Nanos = computed(() => props.totals?.p50Nanos ?? quantileNanos(durationsNanos.value, 0.5));
const p95Nanos = computed(
  () => props.totals?.p95Nanos ?? quantileNanos(durationsNanos.value, 0.95)
);
const maxNanos = computed(
  () =>
    props.totals?.maxNanos ?? durationsNanos.value.reduce((max, nanos) => Math.max(max, nanos), 0)
);

/*
 * Marks printed under the histogram, always from the same population as its bars. The headline
 * percentiles above prefer the operation-wide totals — correctly, they describe the population —
 * but the histogram is built from the capped sample, and annotating a sample's chart with the
 * population's percentiles let the "tail" highlight match every bar or none, and printed a max
 * beyond the chart's own axis.
 */
const chartP50Nanos = computed(() =>
  props.truncated ? quantileNanos(durationsNanos.value, 0.5) : p50Nanos.value
);
const chartP95Nanos = computed(() =>
  props.truncated ? quantileNanos(durationsNanos.value, 0.95) : p95Nanos.value
);
const chartMaxNanos = computed(() =>
  props.truncated
    ? durationsNanos.value.reduce((max, nanos) => Math.max(max, nanos), 0)
    : maxNanos.value
);
const totalNanos = computed(
  () => props.totals?.totalNanos ?? durationsNanos.value.reduce((sum, nanos) => sum + nanos, 0)
);
const callCount = computed(() => props.totals?.count ?? props.traces.length);
const spanCount = computed(
  () => props.totals?.spanCount ?? props.traces.reduce((sum, trace) => sum + trace.spanCount, 0)
);
const failedTraces = computed(
  () => props.totals?.errorCount ?? props.traces.filter(trace => trace.errorCount > 0).length
);

/** Says out loud that the panels below are drawn from a slice, so their shape is not over-read. */
const sampleNote = computed<string | null>(() => {
  if (!props.truncated) {
    return null;
  }
  return `first ${props.traces.length} of ${callCount.value} traces`;
});

/** Summed latency, which overlaps in wall clock — said plainly in the breakdown label. */
const shareOfProfile = computed(() => {
  const profileNanos = props.overview?.totalNanos ?? 0;
  if (profileNanos <= 0) {
    return null;
  }
  return Math.round((totalNanos.value / profileNanos) * 100);
});

const metrics = computed(() => [
  {
    icon: 'arrow-repeat',
    title: 'Calls',
    value: callCount.value,
    variant: failedTraces.value > 0 ? ('danger' as const) : undefined,
    breakdown: [
      { label: 'Spans', value: spanCount.value },
      { label: 'Failed', value: failedTraces.value }
    ]
  },
  {
    icon: 'clock-fill',
    title: 'Latency (p50)',
    value: duration(p50Nanos.value),
    breakdown: [
      { label: 'P95', value: duration(p95Nanos.value) },
      { label: 'P99', value: p99Label.value },
      { label: 'Max', value: duration(maxNanos.value) }
    ]
  },
  {
    // Bootstrap Icons has no "sigma", so that name rendered as an empty box.
    icon: 'stopwatch-fill',
    title: 'Total time',
    value: duration(totalNanos.value),
    breakdown:
      shareOfProfile.value === null
        ? []
        : [
            { label: 'Share of traces', value: `${shareOfProfile.value}%` },
            { label: 'Of', value: duration(props.overview?.totalNanos ?? 0) }
          ]
  },
  {
    icon: 'arrow-left-right',
    title: 'Peak concurrency',
    value: peakConcurrency(props.traces),
    breakdown: [{ label: 'Threads', value: threadsSummary.value?.distinctThreads ?? '—' }]
  }
]);

/*
 * Columns scale with the sample size — the square-root rule. Fixing the count instead would give
 * seven traces twenty-four columns holding one trace each, which reads as a comb rather than as a
 * distribution.
 */
const bucketCount = computed(() =>
  Math.min(
    MAX_HISTOGRAM_BUCKETS,
    Math.max(MIN_HISTOGRAM_BUCKETS, Math.ceil(Math.sqrt(props.traces.length) * 2))
  )
);

/*
 * The operation's own p99, aggregated over every trace of the type, exactly like the p50 and p95
 * beside it. It used to read "—" whenever the fetched list was a sample, because a p99 over the
 * first thousand traces sitting next to a p95 over all of them is two different questions in one
 * row; now that the aggregate exists, the row can answer one question throughout.
 *
 * The fallback still stands for a deep link into an operation the list did not carry, and still
 * declines to compute a p99 from a sample.
 */
const p99Label = computed(() => {
  if (props.totals) {
    return duration(props.totals.p99Nanos);
  }
  return props.truncated ? '—' : duration(quantileNanos(durationsNanos.value, 0.99));
});

const histogram = computed(() => latencyHistogram(durationsNanos.value, bucketCount.value));

const tallestBucket = computed(() =>
  histogram.value.buckets.reduce((tallest, bucket) => Math.max(tallest, bucket.count), 1)
);

/**
 * The platform/virtual split, which is also the answer to "will the Flamegraphs tab have anything
 * in it" — samples are attributed to the carrier, never to the virtual thread mounted on it.
 */
const threadData = computed<DonutChartData | null>(() => {
  const threads = threadsSummary.value;
  if (threads === null) {
    return null;
  }
  const total = threads.platformSpans + threads.virtualSpans + threads.unknownSpans;
  if (total === 0) {
    return null;
  }

  // Unknown is drawn only when there is some, so the ordinary two-way split stays a two-way split.
  const slices = [
    {
      color: 'var(--color-success)',
      label: 'Platform — carries samples',
      count: threads.platformSpans
    },
    {
      color: 'var(--color-secondary)',
      label: 'Virtual — samples go to the carrier',
      count: threads.virtualSpans
    },
    {
      color: 'var(--color-warning)',
      label: 'Unknown thread — cannot be matched',
      count: threads.unknownSpans
    }
  ].filter(slice => slice.count > 0);

  return {
    series: slices.map(slice => slice.count),
    labels: slices.map(slice => slice.label.split(' — ')[0]),
    colors: slices.map(slice => slice.color),
    legendItems: slices.map(slice => ({
      color: slice.color,
      label: slice.label,
      value: String(slice.count)
    })),
    totalLabel: 'spans',
    totalValue: String(total)
  };
});

function duration(nanos: number): string {
  return FormattingService.formatDuration2Units(Math.round(nanos));
}

function barHeight(count: number): string {
  return count === 0 ? '2px' : `${Math.max(6, (count / tallestBucket.value) * 100)}%`;
}

function bucketTitle(bucket: { from: number; to: number; count: number }): string {
  return `${duration(bucket.from)} – ${duration(bucket.to)}: ${bucket.count} traces`;
}

/** Which reading of a span's time the card is ranking by. */
type SpanMode = 'total' | 'self';

const SPAN_MODES: { key: SpanMode; label: string; title: string }[] = [
  { key: 'total', label: 'Inclusive', title: 'Time including everything the span called' },
  { key: 'self', label: 'Self', title: 'Time the span spent on its own work, children excluded' }
];

const spanMode = ref<SpanMode>('total');

function spanTime(span: TraceOperationSpanRow): number {
  return spanMode.value === 'self' ? span.selfNanos : span.totalNanos;
}

/**
 * Re-ranked for the chosen reading. The server orders by inclusive total, which is the wrong order
 * for the self view — the point of the toggle is that the two disagree, so leaving the rows where
 * they were would show a "top spans" list whose top span is not the top one.
 */
const rankedSpans = computed(() =>
  [...spans.value].sort((left, right) => spanTime(right) - spanTime(left))
);

function spanShare(span: TraceOperationSpanRow): string {
  const widest = rankedSpans.value[0] === undefined ? 0 : spanTime(rankedSpans.value[0]);
  return widest <= 0 ? '0%' : `${Math.max(2, (spanTime(span) / widest) * 100)}%`;
}

/** How much of a span's own wall time was its own work rather than something it called. */
function ownShare(span: TraceOperationSpanRow): string {
  if (span.totalNanos <= 0) {
    return '—';
  }
  const share = (span.selfNanos / span.totalNanos) * 100;
  return share < MIN_REPORTED_SHARE_PERCENT ? '<1%' : `${Math.round(share)}%`;
}

function ownShareTitle(span: TraceOperationSpanRow): string {
  return `${duration(span.selfNanos)} of ${duration(span.totalNanos)} was this span's own work`;
}

/**
 * How widely the span appears across the operation's traces. Called out because it separates the two
 * shapes a large total can have: every trace paying a little, or a handful paying a lot.
 */
function spanReach(span: TraceOperationSpanRow): string {
  if (span.occurrences === span.traceCount) {
    return `${span.traceCount} traces`;
  }
  const perTrace = span.occurrences / span.traceCount;
  return `${span.traceCount} traces · ×${perTrace.toFixed(perTrace < 10 ? 1 : 0)}`;
}

function spanReachTitle(span: TraceOperationSpanRow): string {
  return `${span.occurrences} occurrences across ${span.traceCount} traces of this operation`;
}

function spanCountTooltip(value: number): string {
  return `${value} spans`;
}

/* Discards a response that a newer request has already superseded — see TraceOperationDetail. */
let loadGeneration = 0;

async function load(): Promise<void> {
  const generation = ++loadGeneration;
  loading.value = true;
  error.value = null;
  try {
    const client = new ProfileTracesClient(props.profileId);
    const summary = await client.getOperationSummary(props.operation);
    if (generation !== loadGeneration) {
      return;
    }
    spans.value = summary.spans.slice(0, DISPLAYED_SPANS);
    threadsSummary.value = summary.threads;
  } catch (e: unknown) {
    if (generation !== loadGeneration) {
      return;
    }
    console.error('Failed to summarise the operation:', e);
    error.value = 'Failed to load the span breakdown for this operation.';
    spans.value = [];
    threadsSummary.value = null;
  } finally {
    if (generation === loadGeneration) {
      loading.value = false;
    }
  }
}

watch(() => operationKey(props.operation), load, { immediate: true });
</script>

<style scoped>
.op-summary {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/*
 * The histogram earns the wider half: it is the panel with a shape to read, while the donut is two
 * numbers and stays legible small. Both cards stretch to the taller of the two — a row whose cards
 * end at different heights reads as two unrelated blocks rather than one row.
 */
.summary-split {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1rem;
  align-items: stretch;
}

.summary-split > :deep(.main-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.summary-split > :deep(.main-card) .main-card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.summary-split-wide {
  grid-template-columns: 1fr 1fr;
}

@media (max-width: 900px) {
  .summary-split,
  .summary-split-wide {
    grid-template-columns: 1fr;
  }
}

.card-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

/* A two-state segmented control, sized to sit inside the card header without crowding the title. */
.span-mode {
  display: inline-flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.span-mode button {
  border: 0;
  padding: 0.15rem 0.5rem;
  background: transparent;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: var(--font-size-xs);
  cursor: pointer;
}

.span-mode button + button {
  border-left: 1px solid var(--color-border);
}

.span-mode button:hover {
  background: var(--color-bg-hover-alt);
  color: var(--color-dark);
}

.span-mode button.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
}

.span-mode button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.histogram {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.histogram-bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  flex: 1;
  min-height: 8rem;
  margin-top: 1rem;
}

.histogram-bar {
  flex: 1;
  min-height: 2px;
  position: relative;
  background: var(--color-primary-bg);
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
}

/* From the column holding p95 onwards: the tail the reader came for. */
.histogram-bar.tail {
  background: var(--color-primary);
}

.histogram-count {
  position: absolute;
  top: -1.05rem;
  left: 50%;
  transform: translateX(-50%);
  font-family: var(--font-family-monospace);
  font-size: 0.62rem;
  color: var(--color-text-muted);
}

.histogram-axis,
.histogram-marks {
  display: flex;
  justify-content: space-between;
  margin-top: 0.35rem;
  font-size: 0.65rem;
  color: var(--color-text-muted);
}

.histogram-marks {
  justify-content: flex-start;
  gap: 1.1rem;
}

.histogram-marks b {
  font-family: var(--font-family-monospace);
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.span-bars {
  display: grid;
  gap: 0.5rem;
}

.span-bar {
  display: grid;
  /* The detail line runs under the bar, in the two columns the bar and its value occupy. */
  grid-template-columns: minmax(7rem, 12rem) 1fr auto;
  gap: 0.15rem 0.7rem;
  align-items: center;
}

/* Second row, starting under the track so the names column stays a clean edge to scan down. */
.span-detail {
  grid-column: 2 / -1;
  display: flex;
  gap: 0.7rem;
  flex-wrap: wrap;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.span-detail b {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  color: var(--color-text);
}

.span-name {
  font-family: var(--font-family-monospace);
  font-size: 0.74rem;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.span-track {
  height: 0.55rem;
  background: var(--color-bg-hover);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.span-fill {
  height: 100%;
  background: var(--chart-series-1);
  border-radius: var(--radius-pill);
}

.span-value {
  font-family: var(--font-family-monospace);
  font-size: 0.72rem;
  color: var(--color-text);
  white-space: nowrap;
}

.span-count {
  color: var(--color-text-muted);
}

.see-all {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0;
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
  background: none;
  border: 0;
  cursor: pointer;
}

.see-all:hover {
  text-decoration: underline;
}

.see-all:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>

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
              :style="{ height: barHeight(bucket.count) }"
              :title="bucketTitle(bucket)"
            ></div>
          </div>
          <div class="histogram-axis">
            <span>{{ duration(histogram.from) }}</span>
            <span>{{ duration((histogram.from + histogram.to) / 2) }}</span>
            <span>{{ duration(histogram.to) }}</span>
          </div>
        </div>
    </MainCard>

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
              <!-- The ranking key, a sibling pill to the mode: both govern what the list below says. -->
              <span class="span-seg" role="group" aria-label="Rank spans by">
                <button
                  v-for="option in SPAN_SORT_OPTIONS"
                  :key="option.key"
                  type="button"
                  :class="{ on: spanSort === option.key }"
                  :title="`Rank the spans by ${sortLabel(option)}`"
                  @click="spanSort = option.key"
                >
                  {{ sortLabel(option) }}
                </button>
              </span>
              <span class="span-seg" role="group" aria-label="Time reading">
                <button
                  v-for="mode in SPAN_MODES"
                  :key="mode.key"
                  type="button"
                  :class="{ on: spanMode === mode.key }"
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
        <ErrorState v-else-if="error" :message="error" @retry="load" />
        <EmptyState
          v-else-if="spans.length === 0"
          title="No nested spans"
          description="Every trace of this operation is a single span."
          icon="bi-list-nested"
        />
        <!--
          One line per span, columns aligned: the total alone cannot separate "one span that is
          always slow" from "a fast span called a thousand times", which are different problems
          with different fixes — the Calls, P50 and Max columns say which one each row is, and
          they align down the list so spans can be compared against each other at a glance.

          Each row opens with a rail in its category's colour and closes its name with the event
          type it was made from, because the list mixes two kinds of thing: spans the application
          instrumented, and waits the derivation promoted out of JDK events. Read as bare names,
          "Socket read" sits among the mapper calls looking like a method somebody wrote.
        -->
        <div v-else class="span-grid">
          <div class="span-grid-head">
            <span></span>
            <span>Span</span>
            <span>Share</span>
            <span class="span-end">Total</span>
            <span class="span-end">Calls</span>
            <span class="span-end">{{ spanMode === 'total' ? 'Max' : 'Own' }}</span>
            <span class="span-end">P99</span>
            <span class="span-end">P50</span>
          </div>
          <div
            v-for="span in rankedSpans"
            :key="spanKey(span)"
            class="span-grid-row"
            :style="{ '--span-color': spanColor(span) }"
          >
            <span class="span-rail"></span>
            <span class="span-name">
              <!--
                A promoted wait leaves the monospace face behind: "Socket read" is a label this
                application wrote for a JDK event, not an identifier from anyone's source.
              -->
              <span
                class="span-label"
                :class="{ promoted: isPromoted(span) }"
                :title="span.name"
              >{{ span.name }}</span>
              <span class="span-type" :title="spanTypeTitle(span)">{{ span.eventType }}</span>
            </span>
            <span class="span-track">
              <span class="span-fill" :style="{ width: spanShare(span) }"></span>
            </span>
            <span class="span-total">{{ duration(spanTime(span)) }}</span>
            <span class="span-cell" :title="spanReachTitle(span)">{{ span.occurrences }}</span>
            <span v-if="spanMode === 'total'" class="span-cell">{{ duration(span.maxNanos) }}</span>
            <span v-else class="span-cell" :title="ownShareTitle(span)">{{ ownShare(span) }}</span>
            <span class="span-cell">
              {{ duration(spanMode === 'self' ? span.p99SelfNanos : span.p99Nanos) }}
            </span>
            <span class="span-cell">
              {{ duration(spanMode === 'self' ? span.p50SelfNanos : span.p50Nanos) }}
            </span>
          </div>
        </div>
      </MainCard>

    <!--
      The slowest traces as the cards Search Traces draws, so a trace reads the same way whichever
      page found it. The zone's tint reads each duration against this operation's own median and
      P95 — thresholds from the operation-wide totals, not the capped sample.
    -->
    <MainCard :bottom-margin="false" :no-padding="true" class="slowest-header-card">
      <template #header>
        <MainCardHeader icon="bi bi-hourglass-split" title="Slowest traces">
          <template #actions>
            <span v-if="sampleNote" class="card-note">{{ sampleNote }}</span>
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
    </MainCard>

    <TraceCardList
      :items="slowestTraces"
      :trace="(trace: TraceRow) => trace"
      :p50-nanos="p50Nanos"
      :p95-nanos="p95Nanos"
      empty-description="This operation has no completed traces."
      @open="(trace: TraceRow) => emit('openTrace', trace)"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceCardList from '@/components/trace/TraceCardList.vue';

import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  latencyHistogram,
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
import {
  contextLabel,
  isMethodEventType,
  operationKey,
  promotedCategory,
  spanEventColor,
  spanFamily,
  spanFamilyLabel
} from '@/services/trace/traceLabels';

/** Upper bound on histogram columns: past this they are too thin to read. */
const MAX_HISTOGRAM_BUCKETS = 24;
/** Lower bound: fewer than this cannot show a shape at all. */
const MIN_HISTOGRAM_BUCKETS = 6;
/** The breakdown is a ranking, not a catalogue; the tail of a long list is never read. */
const DISPLAYED_SPANS = 8;
/**
  * A summary shows the worst few; the Slowest Traces tab is where the whole ranking lives. Twenty
  * rather than five since the row went to one line — five was what fitted when a trace cost 98px,
  * and at 40px twenty is the same amount of page.
  */
const SLOWEST_SHOWN = 20;
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
 * Sorted here rather than left to the list: handing it a pre-cut page means cutting the right one.
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
    icon: 'diagram-2',
    title: 'Threads',
    value: threadsSummary.value?.distinctThreads ?? '—',
    breakdown: threadBreakdown.value
  }
]);

/*
 * The donut's facts as breakdown cells: where the spans ran, in the colours the donut used.
 * Unknown appears only when there is some, so the ordinary two-way split stays a two-way split.
 */
const threadBreakdown = computed(() => {
  const threads = threadsSummary.value;
  if (threads === null) {
    return [];
  }
  return [
    { label: 'Virtual spans', value: threads.virtualSpans, color: 'var(--color-secondary)' },
    { label: 'Platform spans', value: threads.platformSpans, color: 'var(--color-success)' },
    { label: 'Unknown', value: threads.unknownSpans, color: 'var(--color-warning)' }
  ].filter(cell => cell.value > 0);
});

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

/** Rows on show. The tail of a long list is never read; ten is where the reading stops. */
const MAX_SPAN_ROWS = 10;

/** The columns the list can be ranked by. Always descending — a "top" list has no other way up. */
type SpanSortKey = 'total' | 'calls' | 'max' | 'p99' | 'p50';

const SPAN_SORT_OPTIONS: Array<{ key: SpanSortKey; label: string }> = [
  { key: 'total', label: 'Total' },
  { key: 'calls', label: 'Calls' },
  { key: 'max', label: 'Max' },
  { key: 'p99', label: 'P99' },
  { key: 'p50', label: 'P50' }
];

const spanSort = ref<SpanSortKey>('total');

/** In the self reading the third column is Own, and the pill that ranks by it says so too. */
function sortLabel(option: { key: SpanSortKey; label: string }): string {
  return option.key === 'max' && spanMode.value === 'self' ? 'Own' : option.label;
}

/** The numeric share behind the Own column, for ranking what ownShare only formats. */
function ownShareValue(span: TraceOperationSpanRow): number {
  return span.totalNanos <= 0 ? 0 : span.selfNanos / span.totalNanos;
}

/** The chosen column's value for one span, in the chosen reading of time. */
function spanMetric(span: TraceOperationSpanRow, key: SpanSortKey): number {
  switch (key) {
    case 'total':
      return spanTime(span);
    case 'calls':
      return span.occurrences;
    case 'max':
      return spanMode.value === 'self' ? ownShareValue(span) : span.maxNanos;
    case 'p99':
      return spanMode.value === 'self' ? span.p99SelfNanos : span.p99Nanos;
    case 'p50':
      return spanMode.value === 'self' ? span.p50SelfNanos : span.p50Nanos;
  }
}

/**
 * Re-ranked for the chosen key and reading, then cut to the top ten. The server orders by
 * inclusive total, which is only one of the orders on offer — the point of the pills is that the
 * orders disagree. The cut comes after the sort for the same reason: a span can be top-ten by max
 * without being top-ten by total, and it must not be cut before its ranking is applied.
 */
const rankedSpans = computed(() =>
  [...spans.value]
    .sort((left, right) => spanMetric(right, spanSort.value) - spanMetric(left, spanSort.value))
    .slice(0, MAX_SPAN_ROWS)
);

/** Whether the row is a wait the derivation promoted rather than a span the application recorded. */
/*
 * Tested by event type rather than by a `synthesized` flag, which this row does not carry: the
 * breakdown aggregates spans across traces and keeps only what is common to them. A traced method
 * has to be named explicitly because, unlike every promoted wait, it maps to no context category.
 */
function isPromoted(span: TraceOperationSpanRow): boolean {
  return promotedCategory(span.eventType) !== null || isMethodEventType(span.eventType);
}

/**
 * The row's rail and bar colour, straight from the shared span palette — the same colour the
 * waterfall's bar, its row marker and its legend give the identical event type. Nothing is decided
 * here: a breakdown that picked its own hues would be a second palette to keep in step with the
 * first, and the rows most worth recognising across the two screens are exactly the ones that would
 * drift.
 */
function spanColor(span: TraceOperationSpanRow): string {
  return spanEventColor(span.eventType);
}

function spanTypeTitle(span: TraceOperationSpanRow): string {
  const category = promotedCategory(span.eventType);
  if (category !== null) {
    return `${contextLabel(category)}, promoted from the ${span.eventType} events inside these traces`;
  }
  if (isMethodEventType(span.eventType)) {
    return `Traced method, promoted from the ${span.eventType} events inside these traces`;
  }
  return `${spanFamilyLabel(spanFamily(span.eventType))}, recorded by ${span.eventType}`;
}

/**
 * A row's identity. The name alone is not one: the breakdown groups by event type as well, so a
 * name recorded by two different events is two rows rather than one.
 */
function spanKey(span: TraceOperationSpanRow): string {
  return `${span.eventType}|${span.name}`;
}

function spanShare(span: TraceOperationSpanRow): string {
  // Against the widest shown row, not the first: under a Max or P99 ranking the first row is not
  // necessarily the one with the most time, and a bar past 100% would run out of its lane.
  const widest = rankedSpans.value.reduce((most, row) => Math.max(most, spanTime(row)), 0);
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
function spanReachTitle(span: TraceOperationSpanRow): string {
  return `${span.occurrences} occurrences across ${span.traceCount} traces of this operation`;
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

/* The slowest-traces cards, dressed exactly as the Search Traces results dress theirs. */
.slowest-header-card {
  margin-bottom: -0.25rem;
}

/* A header-only card: the header's own divider would double the card's bottom edge. */
.slowest-header-card :deep(.main-card-header) {
  border-bottom: 0;
  border-radius: var(--radius-md);
}

.card-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

/*
 * The card's two pill segments — what to rank by, and which reading of time — in the same visual
 * family as the scope toggle on Search Conditions, so sibling controls wear one costume.
 */
.span-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
}

.span-seg + .span-seg {
  margin-left: var(--spacing-2);
}

.span-seg button {
  border: 0;
  background: transparent;
  border-radius: var(--radius-xs);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text-muted);
  padding: var(--spacing-1) var(--spacing-3);
  white-space: nowrap;
  font-family: inherit;
  cursor: pointer;
}

.span-seg button:hover {
  color: var(--color-dark);
}

.span-seg button.on {
  background: var(--color-white);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.span-seg button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.histogram {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* The same strip the search page's "When the matches happened" draws, so the two read as one. */
.histogram-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 54px;
  margin-top: 1rem;
}

.histogram-bar {
  flex: 1;
  min-width: 2px;
  min-height: 2px;
  background: var(--color-primary);
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
}

.histogram-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 0.35rem;
  font-size: 0.65rem;
  color: var(--color-text-muted);
}

/*
 * A table without table chrome: every fact on one line, columns tabular-aligned down the list.
 * The leading 4px column is the category rail, which is why the head carries an empty cell.
 *
 * Horizontal padding on both head and rows, matched, so the hover tint has an edge to sit inside
 * without the columns shifting between the two.
 */
.span-grid-head,
.span-grid-row {
  display: grid;
  grid-template-columns: 4px minmax(10rem, 22rem) 1fr 6.5rem 4.5rem 6.5rem 6.5rem 6.5rem;
  gap: 0.7rem;
  align-items: center;
  padding: 0.34rem var(--spacing-2);
  margin: 0 calc(var(--spacing-2) * -1);
}

.span-grid-head {
  border-bottom: 1px solid var(--color-border);
  padding-top: 0.3rem;
  padding-bottom: 0.4rem;
}

.span-grid-head span {
  font-size: 0.6rem;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.span-grid-row + .span-grid-row {
  border-top: 1px solid var(--color-border-light);
}

.span-grid-row:hover {
  background: var(--color-bg-hover);
}

.span-end {
  text-align: right;
}

.span-rail {
  height: 17px;
  border-radius: var(--radius-xs);
  background: var(--span-color);
}

.span-name {
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
  min-width: 0;
}

.span-label {
  font-family: var(--font-family-monospace);
  font-size: 0.745rem;
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.span-label.promoted {
  font-family: var(--font-family-base);
  font-size: 0.775rem;
}

/*
 * The event code verbatim — the prefix is half the fact, so it is never trimmed off.
 *
 * Mixed toward the ink rather than worn neat: the context ramp is a palette of fills, and several
 * of its pastels (the monitor purple, the sleeping teal) vanish at this size on a white card. The
 * rail and the bar keep the colour as it is; only the text, which has to be read, is darkened.
 */
.span-type {
  flex: none;
  font-family: var(--font-family-monospace);
  font-size: 0.6rem;
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
  color: color-mix(in srgb, var(--span-color) 62%, var(--color-dark));
}

.span-track {
  height: 6px;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.span-fill {
  display: block;
  height: 100%;
  background: var(--span-color);
  opacity: 0.85;
  border-radius: var(--radius-sm);
}

.span-total {
  font-family: var(--font-family-monospace);
  font-size: 0.735rem;
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
  text-align: right;
  white-space: nowrap;
}

.span-cell {
  font-family: var(--font-family-monospace);
  font-size: 0.735rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
  text-align: right;
  white-space: nowrap;
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

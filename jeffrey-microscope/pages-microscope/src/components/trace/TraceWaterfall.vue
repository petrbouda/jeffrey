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
  <div class="waterfall">
    <div class="wf-toolbar">
      <button
        type="button"
        class="wf-toggle"
        :class="{ active: criticalOnly }"
        :disabled="!hasOffPathSpans"
        :title="criticalOnlyTitle"
        @click="criticalOnly = !criticalOnly"
      >
        <i class="bi bi-signpost-split"></i> Critical path only
      </button>
      <button
        type="button"
        class="wf-toggle"
        :disabled="parents.size === 0"
        :title="allCollapsed ? 'Expand every span' : 'Collapse every span that has children'"
        @click="toggleAll"
      >
        <i :class="allCollapsed ? 'bi bi-arrows-expand' : 'bi bi-arrows-collapse'"></i>
        {{ allCollapsed ? 'Expand all' : 'Collapse all' }}
      </button>
      <span class="wf-count">{{ rowCountLabel }}</span>
    </div>

    <!--
      One lane per kind of pause, above the spans. The lane reuses the row grid, so its track lines
      up with the bars without either side knowing the other's measurements, and it is the lane
      rather than the stripe that carries the labels and the hit targets: the stripe sits behind
      rows that come and go as the detail panel opens.
    -->
    <div v-for="lane in laneGroups" :key="lane.category" class="wf-lane">
      <span class="lane-label">
        <i class="lane-dot" :style="{ background: contextColor(lane.category) }"></i>
        {{ contextLabel(lane.category) }}
      </span>
      <span class="lane-track">
        <span
          v-for="(band, index) in lane.bands"
          :key="index"
          class="lane-band"
          :style="{
            left: band.leftPercent + '%',
            width: band.widthPercent + '%',
            background: contextColor(band.category)
          }"
          :title="bandTitle(band)"
        >
          <span v-if="band.widthPercent > 6" class="lane-band-text">
            {{ FormattingService.formatDuration2Units(band.durationNanos) }}
          </span>
        </span>
      </span>
      <span class="wf-duration">{{ laneTotal(lane.bands) }}</span>
    </div>

    <div class="wf-head">
      <span>Span</span>
      <span class="wf-scale">
        <span>0</span>
        <span>{{ FormattingService.formatDuration2Units(windowNanos) }}</span>
      </span>
      <span class="wf-duration">Duration</span>
    </div>

    <!--
      The same intervals again, washed across the span rows so it is visible which spans a pause
      actually crossed. Inert to the pointer and behind the bars: it is background, and the rows
      underneath stay clickable.
    -->
    <div class="wf-rows">
      <!--
        Laid out with the row grid rather than at a measured offset, so the stripes track the name
        and duration columns however those are sized, and stretched over the rows by a parent that
        is exactly as tall as they are — no pixel arithmetic, and nothing to recompute when the
        detail panel opens a row and makes the list taller.
      -->
      <div v-if="bands.length > 0" class="wf-stripes" aria-hidden="true">
        <span></span>
        <span class="wf-stripes-track">
          <span
            v-for="(band, index) in bands"
            :key="index"
            class="wf-stripe"
            :style="{
              left: band.leftPercent + '%',
              width: band.widthPercent + '%',
              '--stripe-color': contextColor(band.category)
            }"
          ></span>
        </span>
        <span></span>
      </div>

    <template v-for="span in rows" :key="span.spanId">
      <button
        type="button"
        class="wf-row"
        :class="{ selected: span.spanId === selectedSpanId, critical: isCritical(span) }"
        :aria-expanded="span.spanId === selectedSpanId"
        :data-span-id="span.spanId"
        @click="$emit('select', span)"
        @keydown="onRowKeydown($event, span)"
      >
        <span class="wf-name">
          <span class="wf-indent" :style="{ width: indentRem(span.depth) + 'rem' }"></span>
          <!--
            The twistie is a span, not a nested button: the row itself is the button, and nesting one
            inside another is invalid markup that browsers resolve by dropping it. Clicks are stopped
            here so folding a subtree does not also select the row.
          -->
          <span
            v-if="parents.has(span.spanId)"
            class="wf-twist"
            role="presentation"
            :title="twistTitle(span)"
            @click.stop="toggleCollapsed(span.spanId)"
          >
            <i :class="collapsed.has(span.spanId) ? 'bi bi-caret-right-fill' : 'bi bi-caret-down-fill'"></i>
          </span>
          <span v-else class="wf-twist is-leaf"></span>
          <span class="wf-kind" :class="kindClass(span)"></span>
          <span class="wf-label" :title="span.name">{{ span.name }}</span>
          <Badge v-if="span.status === 'ERROR'" variant="danger" size="xs" value="error" />
          <span v-if="collapsed.has(span.spanId)" class="wf-folded">
            +{{ foldedCounts.get(span.spanId) ?? 0 }}
          </span>
        </span>

        <span class="wf-track">
          <span
            class="wf-bar"
            :class="barClass(span)"
            :style="barStyle(span)"
            :title="tooltip(span)"
          >
            <span
              v-for="(segment, index) in bar(span).selfSegments"
              :key="index"
              class="wf-self"
              :style="{ left: segment.leftPercent + '%', width: segment.widthPercent + '%' }"
            ></span>
          </span>
        </span>

        <span class="wf-duration">{{ FormattingService.formatDuration2Units(span.durationNanos) }}</span>
      </button>

      <!--
        The detail belongs to the row above it, so it is drawn as the next row rather than in a panel
        under the waterfall: on a trace of twenty-odd spans, a panel at the bottom scrolls the bar
        that was clicked out of view, which is the one thing the reader is comparing against.
      -->
      <TraceSpanInlineDetail
        v-if="span.spanId === selectedSpanId"
        :span="span"
        :fields="eventFields[span.eventType] ?? []"
        :child-count="childCounts.get(span.spanId) ?? 0"
        :trace-duration-nanos="windowNanos"
        :waits="context?.spanWaits?.[span.spanId] ?? []"
        @view-events="$emit('viewEvents')"
        @view-flamegraph="$emit('viewFlamegraph')"
      />
    </template>

      <EmptyState
        v-if="rows.length === 0"
        icon="bi-signpost-split"
        title="No spans shown"
        description="Every span is hidden by the current filter."
      />
    </div>

    <div class="wf-legend">
      <span><i class="swatch swatch-self"></i> self time</span>
      <span><i class="swatch swatch-children"></i> time in children</span>
      <span><i class="swatch swatch-critical"></i> on the critical path</span>
      <span v-for="lane in laneGroups" :key="lane.category">
        <i class="swatch" :style="{ background: contextColor(lane.category) }"></i>
        {{ contextLabel(lane.category) }}
      </span>
      <span><i class="swatch swatch-server"></i> server</span>
      <span><i class="swatch swatch-client"></i> client</span>
      <span><i class="swatch swatch-internal"></i> internal</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { NANOS_PER_MICRO } from '@/services/trace/timeUnits';
import { computed, ref, watch } from 'vue';
import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceSpanInlineDetail from '@/components/trace/TraceSpanInlineDetail.vue';
import type { EventFieldRow, TraceContext, TraceSpanRow } from '@/services/api/model/trace/TraceModels';
import type { SpanBar } from '@/services/trace/TraceWaterfallLayout';
import { indentRem, traceWindow, waterfallBars } from '@/services/trace/TraceWaterfallLayout';
import { descendantCounts, spansWithChildren, visibleSpans } from '@/services/trace/traceTree';
import type { ContextBand } from '@/services/trace/TraceContextBands';
import { bandLanes, contextBands } from '@/services/trace/TraceContextBands';
import { contextColor, contextLabel } from '@/services/trace/traceLabels';

const props = withDefaults(
  defineProps<{
    spans: TraceSpanRow[];
    selectedSpanId?: string | null;
    /** Field metadata per event type, so an opened span can label and format what its event recorded. */
    eventFields: Record<string, EventFieldRow[]>;
    /**
     * What the JVM was doing to the trace. Arrives after the spans do — it is a slower query — so
     * the waterfall must draw perfectly well without it and simply gain the bands when it lands.
     */
    context?: TraceContext | null;
  }>(),
  { selectedSpanId: null, context: null }
);

const emit = defineEmits<{
  (event: 'select', span: TraceSpanRow): void;
  (event: 'viewEvents'): void;
  (event: 'viewFlamegraph'): void;
}>();


/** A span with no geometry cannot happen for a span that is being drawn, but must not throw. */
const EMPTY_BAR: SpanBar = { leftPercent: 0, widthPercent: 0, selfSegments: [] };

const collapsed = ref<Set<string>>(new Set());
const criticalOnly = ref(false);

// A different trace is a different tree, so nothing folded in the last one still applies.
watch(
  () => props.spans,
  () => {
    collapsed.value = new Set();
    criticalOnly.value = false;
  }
);

const windowNanos = computed(() => {
  const window = traceWindow(props.spans);
  return (window.endMicros - window.startMicros) * NANOS_PER_MICRO;
});

const parents = computed(() => spansWithChildren(props.spans));

const bands = computed(() =>
  contextBands(props.context?.pauses ?? [], traceWindow(props.spans))
);

const laneGroups = computed(() => bandLanes(bands.value));

// Counted once for the whole trace, like the bars and the child counts below: every parent row asks
// for this on each render, and answering per row would rescan the trace for each of them.
const foldedCounts = computed(() => descendantCounts(props.spans));

/**
 * The rows actually drawn: folded subtrees removed first, then the off-path spans when the filter is
 * on. That order is what makes the two compose — collapsing hides a subtree whether or not its spans
 * are critical, and the filter then narrows whatever survived.
 */
const rows = computed(() => {
  const visible = visibleSpans(props.spans, collapsed.value);
  if (!criticalOnly.value) {
    return visible;
  }
  return visible.filter(isCritical);
});

/**
 * Whether the filter would remove anything. In a strictly sequential trace every span is on the
 * critical path — correct, but it makes the toggle a no-op, so it is disabled rather than left to
 * look broken.
 */
const hasOffPathSpans = computed(() => props.spans.some((span) => !isCritical(span)));

const criticalOnlyTitle = computed(() => {
  if (!hasOffPathSpans.value) {
    return 'Every span in this trace is on the critical path — nothing to hide';
  }
  return 'Show only the spans that determined how long this trace took';
});

const allCollapsed = computed(
  () => parents.value.size > 0 && collapsed.value.size === parents.value.size
);

const rowCountLabel = computed(() => {
  const shown = rows.value.length;
  const total = props.spans.length;
  if (shown === total) {
    return total === 1 ? '1 span' : `${total} spans`;
  }
  return `${shown} of ${total} spans`;
});

// Every bar at once: a bar's solid stretches depend on the span's children, so laying them out
// row by row would rescan the whole trace per row.
const bars = computed(() => waterfallBars(props.spans));

// Counted here rather than in the panel, which only ever sees one span: the tree's shape lives in
// this flat list, and counting it once beats scanning every row each time one is opened.
const childCounts = computed(() => {
  const counts = new Map<string, number>();
  for (const span of props.spans) {
    if (span.parentSpanId !== null) {
      counts.set(span.parentSpanId, (counts.get(span.parentSpanId) ?? 0) + 1);
    }
  }
  return counts;
});

function bar(span: TraceSpanRow): SpanBar {
  return bars.value.get(span.spanId) ?? EMPTY_BAR;
}

function isCritical(span: TraceSpanRow): boolean {
  return span.criticalPathNanos > 0;
}

/**
 * What a band says on hover. Clipping is called out because the number would otherwise be read as
 * the pause's whole length, when part of it happened outside the trace entirely.
 */
function bandTitle(band: ContextBand): string {
  const duration = FormattingService.formatDuration2Units(band.durationNanos);
  const name = `${contextLabel(band.category)} · ${band.label} · ${duration}`;
  if (band.clippedStart && band.clippedEnd) {
    return `${name} — ran for the whole trace and beyond it at both ends`;
  }
  if (band.clippedStart) {
    return `${name} — began before this trace did`;
  }
  if (band.clippedEnd) {
    return `${name} — was still running when the trace ended`;
  }
  return name;
}

/** How much of the trace one lane's pauses came to, for the row's duration column. */
function laneTotal(laneBands: ContextBand[]): string {
  const nanos = laneBands.reduce((sum, band) => sum + band.durationNanos, 0);
  return FormattingService.formatDuration2Units(nanos);
}

function toggleCollapsed(spanId: string): void {
  const next = new Set(collapsed.value);
  if (!next.delete(spanId)) {
    next.add(spanId);
  }
  collapsed.value = next;
}

function toggleAll(): void {
  collapsed.value = allCollapsed.value ? new Set() : new Set(parents.value);
}

function twistTitle(span: TraceSpanRow): string {
  const hidden = foldedCounts.value.get(span.spanId) ?? 0;
  const spans = hidden === 1 ? '1 span' : `${hidden} spans`;
  return collapsed.value.has(span.spanId) ? `Expand ${spans}` : `Collapse ${spans}`;
}

/**
 * Arrow-key navigation over the drawn rows. Left and right fold and unfold the way a tree widget is
 * expected to; up and down move the selection, which is also what opens the inline detail, so the
 * keyboard reaches everything the mouse does. The row is a button, so Enter and Space already
 * select through the click handler and are left alone.
 */
function onRowKeydown(event: KeyboardEvent, span: TraceSpanRow): void {
  if (event.key === 'ArrowRight') {
    if (parents.value.has(span.spanId) && collapsed.value.has(span.spanId)) {
      event.preventDefault();
      toggleCollapsed(span.spanId);
    }
    return;
  }
  if (event.key === 'ArrowLeft') {
    if (parents.value.has(span.spanId) && !collapsed.value.has(span.spanId)) {
      event.preventDefault();
      toggleCollapsed(span.spanId);
    }
    return;
  }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
    return;
  }

  const index = rows.value.findIndex((row) => row.spanId === span.spanId);
  const next = rows.value[index + (event.key === 'ArrowDown' ? 1 : -1)];
  if (next === undefined) {
    return;
  }
  event.preventDefault();
  emit('select', next);
  focusRow(next.spanId);
}

/**
 * Moves focus onto a row after the selection follows the keyboard. Deferred to the next frame
 * because selecting a row also mounts its detail panel, which re-renders the list the target row
 * lives in.
 */
function focusRow(spanId: string): void {
  requestAnimationFrame(() => {
    const row = document.querySelector<HTMLElement>(`.wf-row[data-span-id="${spanId}"]`);
    row?.focus();
  });
}

function barStyle(span: TraceSpanRow) {
  const geometry = bar(span);
  return { left: geometry.leftPercent + '%', width: geometry.widthPercent + '%' };
}

function barClass(span: TraceSpanRow): string {
  if (span.status === 'ERROR') {
    return 'bar-error';
  }
  return 'bar-' + span.kind.toLowerCase();
}

function kindClass(span: TraceSpanRow): string {
  return 'kind-' + span.kind.toLowerCase();
}

/**
 * Self time is the number worth surfacing on hover: the duration is already in its own column,
 * so repeating it would say nothing the row does not already show.
 */
function tooltip(span: TraceSpanRow): string {
  const total = FormattingService.formatDuration2Units(span.durationNanos);
  const self = FormattingService.formatDuration2Units(span.selfDurationNanos);
  const thread = span.threadName ? ` · ${span.threadName}` : '';
  const critical = isCritical(span)
    ? `, ${FormattingService.formatDuration2Units(span.criticalPathNanos)} critical`
    : ', off the critical path';
  return `${span.name} — ${total} total, ${self} self${critical}${thread}`;
}
</script>

<style scoped>
/* The panel treatment the Spans views use, so the bars read as one surface rather than as a table. */
.waterfall {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
}

/* Filters sit above the scale rather than in the modal header: they change what this list draws. */
.wf-toolbar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem 0;
}

.wf-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: var(--font-size-xs);
  cursor: pointer;
}

.wf-toggle:hover:not(:disabled) {
  background: var(--color-bg-hover-alt);
  color: var(--color-dark);
}

.wf-toggle.active {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.wf-toggle:disabled {
  opacity: 0.5;
  cursor: default;
}

.wf-count {
  margin-left: auto;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.wf-head,
.wf-row,
.wf-lane,
.wf-stripes {
  display: grid;
  grid-template-columns: 20rem 1fr 5.5rem;
  align-items: center;
  gap: 0.5rem;
}

/* The rows' own stacking context, so the stripe layer can stretch over exactly them. */
.wf-rows {
  position: relative;
  display: flex;
  flex-direction: column;
}

.wf-lane {
  padding: 0.15rem 1rem;
  /* Matches the row's accent gutter so the lane track and the bar track share an origin. */
  border-left: 2px solid transparent;
}

.lane-label {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.35rem;
  font-size: var(--font-size-xs);
  letter-spacing: 0.03em;
  text-transform: uppercase;
  font-weight: 600;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.lane-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-xs);
  flex: none;
}

.lane-track {
  position: relative;
  height: 0.9rem;
}

.lane-track::before {
  content: '';
  position: absolute;
  inset: 0.42rem 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.lane-band {
  position: absolute;
  top: 0.08rem;
  height: 0.72rem;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.lane-band-text {
  font-family: var(--font-family-monospace);
  font-size: 0.55rem;
  font-weight: 600;
  color: var(--color-bg-card);
  white-space: nowrap;
}

/*
 * The wash behind the bars. Inert to the pointer so the rows above stay clickable, and behind them
 * so it reads as ground rather than as another bar.
 */
.wf-stripes {
  position: absolute;
  inset: 0;
  padding: 0 1rem;
  border-left: 2px solid transparent;
  pointer-events: none;
  z-index: 0;
}

.wf-stripes-track {
  position: relative;
  height: 100%;
}

.wf-stripe {
  position: absolute;
  top: 0;
  bottom: 0;
  background: color-mix(in srgb, var(--stripe-color) 9%, transparent);
  border-left: 1px solid color-mix(in srgb, var(--stripe-color) 30%, transparent);
  border-right: 1px solid color-mix(in srgb, var(--stripe-color) 30%, transparent);
}

.wf-head {
  padding: 0.55rem 1rem 0.4rem;
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

.wf-scale {
  display: flex;
  justify-content: space-between;
  font-variant-numeric: tabular-nums;
}

.wf-row {
  /* Above the stripe wash, which is drawn behind the whole block of rows. */
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 0.28rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--color-border-light);
  /*
   * Carried by every row, transparent unless the span is on the critical path, so switching the
   * filter on and off never shifts the names sideways.
   */
  border-left: 2px solid transparent;
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;
}

.wf-row.critical {
  border-left-color: var(--color-warning);
}

.wf-row:hover {
  background: var(--color-bg-hover-alt);
}

.wf-row.selected {
  background: var(--color-primary-light);
}

.wf-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.wf-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.wf-indent {
  flex: none;
}

/*
 * The twistie keeps its width on a leaf, so names stay on one column whether or not a span has
 * children -- a tree whose labels shift left at every leaf is much harder to read down.
 */
.wf-twist {
  flex: none;
  width: 0.8rem;
  font-size: 0.6rem;
  line-height: 1;
  color: var(--color-text-muted);
  cursor: pointer;
}

.wf-twist:hover {
  color: var(--color-dark);
}

.wf-twist.is-leaf {
  cursor: inherit;
}

/* How many rows a fold is hiding, so a collapsed span does not look like a leaf. */
.wf-folded {
  flex: none;
  padding: 0 0.25rem;
  border-radius: var(--radius-xs);
  background: var(--color-lighter);
  color: var(--color-text-muted);
  font-size: 0.6rem;
  font-variant-numeric: tabular-nums;
}

.wf-kind {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: var(--radius-circle);
  flex: none;
}

.kind-server {
  background: var(--color-primary);
}

.kind-client {
  background: var(--color-info);
}

.kind-internal {
  background: var(--color-secondary);
}

.wf-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-dark);
}

/* The track a bar is positioned inside, by percentage of the trace window. */
.wf-track {
  position: relative;
  height: 1.1rem;
}

.wf-track::before {
  content: '';
  position: absolute;
  inset: 0.5rem 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.wf-bar {
  position: absolute;
  top: 0.15rem;
  height: 0.8rem;
  border-radius: var(--radius-xs);
  overflow: hidden;
  display: block;
}

/*
 * The pale body is the whole span; the solid stretches are the span's own work, drawn where it
 * actually happened rather than gathered into a block at the front. The gaps between them are its
 * children, so the row reads as an alternation instead of as two things running at once.
 */
.wf-self {
  position: absolute;
  top: 0;
  height: 100%;
}

.bar-server {
  background: color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent);
}

.bar-server .wf-self {
  background: var(--flamegraph-color-blue);
}

.bar-client {
  background: color-mix(in srgb, var(--flamegraph-color-cyan) 35%, transparent);
}

.bar-client .wf-self {
  background: var(--flamegraph-color-cyan);
}

.bar-internal {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}

.bar-internal .wf-self {
  background: var(--flamegraph-color-green);
}

.bar-error {
  background: var(--color-danger-light);
}

.bar-error .wf-self {
  background: var(--flamegraph-color-red);
}

.wf-duration {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  color: var(--color-text);
}

.wf-legend {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: 0.62rem;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  padding: 0.6rem 1rem;
}

.wf-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}

.swatch-self {
  background: var(--flamegraph-color-green);
}

.swatch-children {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}

/* Matches the row's left accent rather than a bar colour: the critical path marks rows, not spans. */
.swatch-critical {
  background: var(--color-warning);
}

.swatch-server {
  background: var(--color-primary);
}

.swatch-client {
  background: var(--color-info);
}

.swatch-internal {
  background: var(--color-secondary);
}
</style>

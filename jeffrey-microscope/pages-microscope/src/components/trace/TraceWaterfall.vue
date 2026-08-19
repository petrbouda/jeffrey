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
        :aria-pressed="criticalOnly"
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
      <button
        type="button"
        class="wf-toggle"
        :class="{ active: bandCategories.length > 0 && !allContextHidden }"
        :aria-pressed="bandCategories.length > 0 && !allContextHidden"
        :disabled="bandCategories.length === 0"
        :title="contextToggleTitle"
        @click="toggleAllContext"
      >
        <i class="bi bi-cpu"></i> JVM context
      </button>
      <!--
        The promoted waits — I/O, locks, parks — drawn as child bars. On by default because they are
        the feature: a trace that says where its time went. Off is for reading the recorded span
        structure alone, and the per-category legend buttons narrow it finer than this master can.
      -->
      <button
        type="button"
        class="wf-toggle"
        :class="{ active: showBlockingOps && promotedCount > 0 }"
        :aria-pressed="showBlockingOps && promotedCount > 0"
        :disabled="promotedCount === 0"
        :title="blockingToggleTitle"
        @click="showBlockingOps = !showBlockingOps"
      >
        <i class="bi bi-hourglass-split"></i> Blocking ops
      </button>
      <!--
        Only when there is an error to jump to: in a 200-span trace the one red badge can sit three
        folds deep and two screens down, and "this trace failed" without a way to the failure is a
        finding withheld.
      -->
      <button
        v-if="firstErrorSpan"
        type="button"
        class="wf-toggle wf-error-jump"
        title="Expand to and select the first failed span"
        @click="jumpToFirstError"
      >
        <i class="bi bi-exclamation-triangle"></i> First error
      </button>
      <span class="wf-count">{{ rowCountLabel }}</span>
      <!-- The only place the keyboard model is written down; without it, arrows are a secret. -->
      <span class="wf-keys" aria-hidden="true">↑↓ move · ←→ fold · Enter open</span>
    </div>

    <!--
      One lane per kind of pause, above the spans. The lane reuses the row grid, so its track lines
      up with the bars without either side knowing the other's measurements, and it is the lane
      rather than the stripe that carries the labels and the hit targets: the stripe is a wash
      behind rows that come and go as the detail panel opens, with nothing to click.
    -->
    <div v-for="lane in laneGroups" :key="lane.category" class="wf-lane">
      <!--
        The name sits at the left edge, under the Span header, and the gutter between it and the
        track carries what the lane could never say on its own: what share of the trace it cost and
        how many events that was. Twenty rems of empty column was the alternative.
      -->
      <span class="lane-label">
        <span class="lane-name">
          <i class="lane-dot" :style="{ background: contextColor(lane.category) }"></i>
          {{ contextLabel(lane.category) }}
        </span>
        <span class="lane-meter" :title="laneShareTitle(lane)">
          <i
            :style="{
              width: laneSharePercent(lane.bands) + '%',
              background: contextColor(lane.category)
            }"
          ></i>
        </span>
        <span class="lane-stat">
          {{ laneSharePercent(lane.bands).toFixed(1) }}% · {{ lane.bands.length }}×
        </span>
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
    <div class="wf-rows" @pointermove="trackCursor" @pointerleave="clearCursor">
      <!--
        Laid out with the row grid rather than at a measured offset, so the stripes track the name
        and duration columns however those are sized — no pixel arithmetic, and nothing to recompute
        when the detail panel opens a row and makes the list taller.

        It stretches the whole of `.wf-rows`, which is taller than the rows themselves once a detail
        panel is open, so every other child claims a layer above it — see `.wf-rows > :not(...)`.
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

      <!--
        The instant under the pointer, named. The wash can say a pause was crossed but never which
        one or how long it ran: below a pixel every band is drawn at the same floor width, so the
        picture cannot be read as a duration however carefully it is drawn. This reads the one place
        the reader is actually pointing, where the numbers are exact.

        Shares the row grid with the stripes, so the reading and the bands agree on where an instant
        is without either measuring the other. Pointer-only, and so hidden from assistive tech: the
        same offset and wall-clock reach the keyboard through the span detail, which Enter opens.
      -->
      <div class="wf-cursor" aria-hidden="true">
        <span></span>
        <span ref="cursorTrack" class="wf-cursor-track">
          <span v-if="cursorPercent !== null" class="wf-cursor-line" :style="cursorStyle">
            <span class="wf-cursor-chip">
              +{{ cursorOffset }}
              <span v-if="cursorBand !== null" class="wf-cursor-pause">
                <i class="lane-dot" :style="{ background: contextColor(cursorBand.category) }"></i>
                {{ contextLabel(cursorBand.category) }}
                {{ FormattingService.formatDuration2Units(cursorBand.durationNanos) }}
              </span>
            </span>
          </span>
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
          :tabindex="rowTabindex(span)"
          @click="$emit('select', span)"
          @focus="focusedSpanId = span.spanId"
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
              <i
                :class="
                  collapsed.has(span.spanId) ? 'bi bi-caret-right-fill' : 'bi bi-caret-down-fill'
                "
              ></i>
            </span>
            <span v-else class="wf-twist is-leaf"></span>
            <span class="wf-kind" :class="kindClass(span)" :style="kindStyle(span)"></span>
            <span class="wf-label" :title="span.name">{{ span.name }}</span>
            <Badge v-if="span.status === 'ERROR'" variant="danger" size="xs" value="error" />
            <span v-if="collapsed.has(span.spanId)" class="wf-folded">
              +{{ foldedCounts.get(span.spanId) ?? 0 }}
            </span>
            <!-- A fold that swallows a failure must not look like a fold that swallows routine. -->
            <i
              v-if="collapsed.has(span.spanId) && (errorDescendantCounts.get(span.spanId) ?? 0) > 0"
              class="wf-folded-error"
              :title="hiddenErrorTitle(span)"
            ></i>
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

          <span class="wf-duration">{{
            FormattingService.formatDuration2Units(span.durationNanos)
          }}</span>
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
          :trace-duration-nanos="traceDurationNanos ?? windowNanos"
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
      >
        <template #action>
          <!-- The state names the filter as the culprit, so it must also offer the way out. -->
          <button type="button" class="wf-toggle" @click="showAllSpans">Show all spans</button>
        </template>
      </EmptyState>
    </div>

    <div class="wf-legend">
      <!--
        One two-tone example instead of two single-hue entries: a bar's self time is solid and its
        children washed *in the span's own kind colour*, so a lone green "self" swatch was only true
        for internal spans. The example shows the relationship, which is what actually generalises.
      -->
      <span><i class="swatch swatch-selfchildren"></i> solid self · washed children</span>
      <span><i class="swatch swatch-critical"></i> on the critical path</span>
      <span><i class="swatch swatch-error"></i> error span</span>
      <!--
        These entries filter rather than describe, so they are real buttons and are styled to look
        clickable — a legend where half the items respond to a click and all of them look alike
        teaches the reader nothing until they try one.
      -->
      <button
        v-for="category in contextCategories"
        :key="category"
        type="button"
        class="legend-toggle"
        :class="{ off: !isContextVisible(category) }"
        :title="`${isContextVisible(category) ? 'Hide' : 'Show'} ${contextLabel(category)}`"
        @click="toggleContextCategory(category)"
      >
        <i class="swatch" :style="{ background: contextColor(category) }"></i>
        {{ contextLabel(category) }}
      </button>
      <span><i class="swatch swatch-server"></i> server</span>
      <span><i class="swatch swatch-client"></i> client</span>
      <span><i class="swatch swatch-internal"></i> internal</span>
      <!-- Kind dots (row markers) keep their own hues; the swatches above match the bars. -->
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
import type {
  EventFieldRow,
  TraceContext,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';
import type { SpanBar } from '@/services/trace/TraceWaterfallLayout';
import { indentRem, traceWindow, waterfallBars } from '@/services/trace/TraceWaterfallLayout';
import { descendantCounts, spansWithChildren, visibleSpans } from '@/services/trace/traceTree';
import type { ContextBand } from '@/services/trace/TraceContextBands';
import {
  bandAt,
  bandLanes,
  contextBands,
  mergedDurationNanos
} from '@/services/trace/TraceContextBands';
import { contextColor, contextLabel, promotedCategory } from '@/services/trace/traceLabels';

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
    /**
     * Where the context request stands, so the toolbar never asserts "no pauses" about a JVM it has
     * not heard from yet — a null context also means loading, and also means failed.
     */
    contextState?: 'loading' | 'ready' | 'failed';
    /**
     * The trace's recorded duration, for every "% of the trace" this component hands down. The
     * span-derived window can differ from it (a child outliving its parent widens the window), and
     * using both meant one span's share read as two different percentages in the same dialog.
     */
    traceDurationNanos?: number | null;
  }>(),
  { selectedSpanId: null, context: null, contextState: 'ready', traceDurationNanos: null }
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
/** Context categories the reader has switched off, by name. Empty means everything is drawn. */
const hiddenCategories = ref<Set<string>>(new Set());

// A different trace is a different tree, so nothing folded in the last one still applies -- and it
// crossed different pauses, so carrying a hide over would drop bands nobody chose to drop.
watch(
  () => props.spans,
  () => {
    collapsed.value = new Set();
    criticalOnly.value = false;
    hiddenCategories.value = new Set();
    showBlockingOps.value = true;
  }
);

const windowNanos = computed(() => {
  const window = traceWindow(props.spans);
  return (window.endMicros - window.startMicros) * NANOS_PER_MICRO;
});

const parents = computed(() => spansWithChildren(props.spans));

/**
 * Whether the promoted blocking operations — the synthesized leaf spans the derivation built out of
 * I/O, lock, park and sleep events — are drawn. On by default: they are the rows that say where a
 * span's time actually went. Off reads the recorded span structure alone.
 *
 * The levelled GC phases used to have a toggle of their own here; it drew the same stopped world up
 * to five times over and answered a GC question rather than a latency one, so the nested pauses now
 * simply stay out of the lanes.
 */
const showBlockingOps = ref(true);

const promotedCount = computed(() => props.spans.filter(span => span.synthesized).length);

const blockingToggleTitle = computed(() => {
  if (promotedCount.value === 0) {
    return 'No blocking operations were promoted in this trace';
  }
  const ops = promotedCount.value === 1 ? '1 blocking operation' : `${promotedCount.value} blocking operations`;
  return showBlockingOps.value
    ? `Hide the ${ops} drawn as child spans`
    : `Show the ${ops} drawn as child spans`;
});

const allBands = computed(() =>
  contextBands(
    (props.context?.pauses ?? []).filter(pause => !pause.nested),
    traceWindow(props.spans)
  )
);

/**
 * The categories drawn as pause lanes and stripes — the GLOBAL ones. The "JVM context" master
 * toggle governs exactly these, so switching the bands off cannot silently swallow the promoted
 * rows, which have their own master.
 */
const bandCategories = computed(() => bandLanes(allBands.value).map(lane => lane.category));

/**
 * Every category the trace recorded, hidden or not — the legend is driven from this rather than from
 * what is currently drawn, or hiding a category would take away the control that brings it back.
 * Band categories first, then whatever categories the promoted rows add.
 */
const contextCategories = computed(() => {
  const categories = [...bandCategories.value];
  for (const span of props.spans) {
    if (!span.synthesized) {
      continue;
    }
    const category = promotedCategory(span.eventType);
    if (category !== null && !categories.includes(category)) {
      categories.push(category);
    }
  }
  return categories;
});

const bands = computed(() =>
  allBands.value.filter(band => !hiddenCategories.value.has(band.category))
);

const laneGroups = computed(() => bandLanes(bands.value));

/**
 * Derived rather than kept as its own flag, the way {@link allCollapsed} is. A separate boolean would
 * allow the state where the master reads "on" while every category is individually hidden — looking
 * off and saying on.
 */
const allContextHidden = computed(
  () => bandCategories.value.length > 0 && bands.value.length === 0
);

function isContextVisible(category: string): boolean {
  return !hiddenCategories.value.has(category);
}

function toggleContextCategory(category: string): void {
  const hidden = new Set(hiddenCategories.value);
  if (hidden.has(category)) {
    hidden.delete(category);
  } else {
    hidden.add(category);
  }
  hiddenCategories.value = hidden;
}

/**
 * Hides or restores the pause bands only. Working through the band categories rather than through
 * every legend category keeps this master away from the promoted rows, which "Blocking ops" owns —
 * one switch per question, and a per-category choice on the other family survives flipping this one.
 */
function toggleAllContext(): void {
  const hidden = new Set(hiddenCategories.value);
  if (allContextHidden.value) {
    for (const category of bandCategories.value) {
      hidden.delete(category);
    }
  } else {
    for (const category of bandCategories.value) {
      hidden.add(category);
    }
  }
  hiddenCategories.value = hidden;
}

const contextToggleTitle = computed(() => {
  // The absent-categories claim is only true once the request has actually answered. Asserting it
  // while loading or after a failure stated a fact about the JVM nobody had fetched.
  if (props.contextState === 'loading') {
    return 'Loading JVM context…';
  }
  if (props.contextState === 'failed') {
    return 'JVM context could not be loaded';
  }
  if (bandCategories.value.length === 0) {
    return 'No GC pauses or safepoints crossed this trace';
  }
  return allContextHidden.value
    ? 'Show the GC pauses and safepoints that crossed this trace'
    : 'Hide the pause bands drawn over the spans';
});

// Counted once for the whole trace, like the bars and the child counts below: every parent row asks
// for this on each render, and answering per row would rescan the trace for each of them.
const foldedCounts = computed(() => descendantCounts(props.spans));

/**
 * The rows actually drawn: folded subtrees removed first, then the promoted rows the reader has
 * switched off, then the off-path spans when the filter is on. That order is what makes them
 * compose — collapsing hides a subtree whether or not its spans are critical, and each filter then
 * narrows whatever survived. Dropping a synthesized row never breaks the tree: a promoted span is a
 * leaf by construction, so the depth sequence the rendering reads stays intact.
 */
const rows = computed(() => {
  const visible = visibleSpans(props.spans, collapsed.value).filter(isSpanDrawn);
  if (!criticalOnly.value) {
    return visible;
  }
  return visible.filter(isCritical);
});

/** Whether a row survives the blocking-ops master and the per-category legend choices. */
function isSpanDrawn(span: TraceSpanRow): boolean {
  if (!span.synthesized) {
    return true;
  }
  if (!showBlockingOps.value) {
    return false;
  }
  const category = promotedCategory(span.eventType);
  return category === null || !hiddenCategories.value.has(category);
}

/**
 * Whether the filter would remove anything. In a strictly sequential trace every span is on the
 * critical path — correct, but it makes the toggle a no-op, so it is disabled rather than left to
 * look broken.
 */
const hasOffPathSpans = computed(() => props.spans.some(span => !isCritical(span)));

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
    const spans = total === 1 ? '1 span' : `${total} spans`;
    // The promoted rows are counted out loud so a reader knows how much of the tree is synthesized
    // waits rather than recorded structure.
    return promotedCount.value > 0 ? `${spans} · ${promotedCount.value} promoted` : spans;
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

/** The empty state's way out: undo everything that can hide a row. */
function showAllSpans(): void {
  criticalOnly.value = false;
  collapsed.value = new Set();
  showBlockingOps.value = true;
}

/** In drawn order, so "first" means the first the reader would meet scrolling down. */
const firstErrorSpan = computed(() => props.spans.find(span => span.status === 'ERROR') ?? null);

/**
 * How many failed spans each span's subtree holds, counted by walking each error's parent chain —
 * one pass over the errors, which are few, rather than a subtree scan per parent row.
 */
const errorDescendantCounts = computed(() => {
  const byId = new Map(props.spans.map(span => [span.spanId, span]));
  const counts = new Map<string, number>();
  for (const span of props.spans) {
    if (span.status !== 'ERROR') {
      continue;
    }
    let parentId = span.parentSpanId;
    while (parentId !== null) {
      counts.set(parentId, (counts.get(parentId) ?? 0) + 1);
      parentId = byId.get(parentId)?.parentSpanId ?? null;
    }
  }
  return counts;
});

function hiddenErrorTitle(span: TraceSpanRow): string {
  const hidden = errorDescendantCounts.value.get(span.spanId) ?? 0;
  return hidden === 1
    ? 'This folded subtree hides 1 failed span'
    : `This folded subtree hides ${hidden} failed spans`;
}

/**
 * Expands whatever hides the first failed span, then focuses and opens it. Every filter that can
 * conceal a row is undone only as far as needed: ancestors unfold, and the critical-path filter is
 * lifted only when the error is off the path it shows.
 */
function jumpToFirstError(): void {
  const target = firstErrorSpan.value;
  if (!target) {
    return;
  }
  const byId = new Map(props.spans.map(span => [span.spanId, span]));
  const next = new Set(collapsed.value);
  let parentId = target.parentSpanId;
  while (parentId !== null) {
    next.delete(parentId);
    parentId = byId.get(parentId)?.parentSpanId ?? null;
  }
  collapsed.value = next;
  if (criticalOnly.value && !isCritical(target)) {
    criticalOnly.value = false;
  }
  focusedSpanId.value = target.spanId;
  focusRow(target.spanId);
  if (props.selectedSpanId !== target.spanId) {
    emit('select', target);
  }
}

/**
 * What a band says on hover. Clipping is called out because the number would otherwise be read as
 * the pause's whole length, when part of it happened outside the trace entirely. So is a band drawn
 * at the floor width, for the mirror-image reason: there the drawing overstates the pause, and the
 * band is the only thing that knows by how little.
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
  if (band.clamped) {
    return `${name} — too brief to draw to scale, shown at the minimum width`;
  }
  return name;
}

/**
 * Where the pointer is across the track, 0-100, or null when it is not over a row.
 *
 * The waterfall's other readings are per span, and a span is a poor unit for "what stopped us here":
 * the pauses in this view are global, they cross whatever happened to be running, and the interesting
 * question at a dense stretch of bands is what the JVM was doing at *that instant* — which no span
 * boundary answers.
 */
const cursorPercent = ref<number | null>(null);
const cursorTrack = ref<HTMLElement | null>(null);

const cursorStyle = computed(() => ({ left: (cursorPercent.value ?? 0) + '%' }));

/** How far into the trace the pointer is, in the same units every other duration here is written. */
const cursorOffset = computed(() =>
  FormattingService.formatDuration2Units(((cursorPercent.value ?? 0) / 100) * windowNanos.value)
);

const cursorBand = computed(() =>
  cursorPercent.value === null ? null : bandAt(bands.value, cursorPercent.value)
);

/**
 * Follows the pointer along the track.
 *
 * Measured against the cursor layer's own track cell rather than a row's, because rows come and go
 * as the detail panel opens and closes while this cell is always present and always the same width
 * — the grid gives both of them the same column.
 */
function trackCursor(event: PointerEvent): void {
  const track = cursorTrack.value;
  const target = event.target;
  // Only over the rows themselves. An open detail panel is a table rather than a timeline, and a
  // cursor drawn down it points at nothing.
  if (track === null || !(target instanceof Element) || target.closest('.wf-row') === null) {
    cursorPercent.value = null;
    return;
  }

  const box = track.getBoundingClientRect();
  if (box.width <= 0) {
    cursorPercent.value = null;
    return;
  }
  const percent = ((event.clientX - box.left) / box.width) * 100;
  cursorPercent.value = percent < 0 || percent > 100 ? null : percent;
}

function clearCursor(): void {
  cursorPercent.value = null;
}

/**
 * How much of the trace one lane's pauses came to, for the row's duration column.
 *
 * Merged rather than summed, so switching the GC phase breakdown on cannot change the answer: the
 * phases run inside the pauses above them, and adding both counts the same stopped instant twice.
 */
function laneTotal(laneBands: ContextBand[]): string {
  return FormattingService.formatDuration2Units(
    mergedDurationNanos(laneBands, windowNanos.value)
  );
}

/**
 * The denominator every "% of the trace" in this dialog is taken against — the trace's recorded
 * duration where there is one, and the span-derived window otherwise. The two can differ when a
 * child outlives its parent, and using each in turn made one quantity read as two percentages.
 */
const shareDenominatorNanos = computed(() => props.traceDurationNanos ?? windowNanos.value);

/** What share of the trace a lane cost, 0-100. */
function laneSharePercent(laneBands: ContextBand[]): number {
  const denominator = shareDenominatorNanos.value;
  if (denominator <= 0) {
    return 0;
  }
  return (mergedDurationNanos(laneBands, windowNanos.value) / denominator) * 100;
}

function laneShareTitle(lane: { category: string; bands: ContextBand[] }): string {
  const total = FormattingService.formatDuration2Units(
    mergedDurationNanos(lane.bands, windowNanos.value)
  );
  const events = lane.bands.length === 1 ? '1 event' : `${lane.bands.length} events`;
  // Overlapping pauses are called out because the two numbers otherwise look like they should
  // divide into each other, and for a lane whose bands overlap they do not.
  return (
    `${contextLabel(lane.category)} covered ${total} of this trace ` +
    `— ${laneSharePercent(lane.bands).toFixed(1)}%, across ${events}`
  );
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
 * expected to; up and down move focus only, and Enter or Space (the row is a button) opens the
 * inline detail, so reading down a trace does not mount a detail panel per row passed through.
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
  // Arrows only move focus; Enter and Space (the row is a button) open the detail. Selecting on
  // every arrow press force-mounted a detail panel per row passed through, which made the keyboard
  // unusable for simply reading down a trace.
  if (event.key === 'Home' || event.key === 'End') {
    const target = event.key === 'Home' ? rows.value[0] : rows.value[rows.value.length - 1];
    if (target !== undefined) {
      event.preventDefault();
      focusedSpanId.value = target.spanId;
      focusRow(target.spanId);
    }
    return;
  }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
    return;
  }

  const index = rows.value.findIndex(row => row.spanId === span.spanId);
  const next = rows.value[index + (event.key === 'ArrowDown' ? 1 : -1)];
  if (next === undefined) {
    return;
  }
  event.preventDefault();
  focusedSpanId.value = next.spanId;
  focusRow(next.spanId);
}

/**
 * The row that owns the list's single tab stop. One stop for the whole tree, moved by arrows: with
 * every row a tab stop, crossing a 200-span trace to reach the legend cost 200 presses.
 */
const focusedSpanId = ref<string | null>(null);

function rowTabindex(span: TraceSpanRow): number {
  const focusTarget = focusedSpanId.value ?? rows.value[0]?.spanId;
  return span.spanId === focusTarget ? 0 : -1;
}

/**
 * Moves focus onto a row after the keyboard walks the tree. Deferred to the next frame because
 * folding can re-render the list the target row lives in.
 */
function focusRow(spanId: string): void {
  requestAnimationFrame(() => {
    const row = document.querySelector<HTMLElement>(`.wf-row[data-span-id="${spanId}"]`);
    row?.focus();
  });
}

function barStyle(span: TraceSpanRow) {
  const geometry = bar(span);
  const style: Record<string, string> = {
    left: geometry.leftPercent + '%',
    width: geometry.widthPercent + '%'
  };
  // A promoted wait wears its category's colour -- the same one its legend button, the lanes and
  // the threads timeline use -- rather than a span-kind pastel: it is context turned into a bar,
  // not a new kind of span.
  const category = promotedColorCategory(span);
  if (category !== null) {
    style.background = contextColor(category);
  }
  return style;
}

function barClass(span: TraceSpanRow): string {
  if (span.status === 'ERROR') {
    return 'bar-error';
  }
  if (span.synthesized) {
    return 'bar-synthesized';
  }
  return 'bar-' + span.kind.toLowerCase();
}

function kindClass(span: TraceSpanRow): string {
  return 'kind-' + span.kind.toLowerCase();
}

/** The row marker follows the bar for a promoted wait, so the two cannot disagree about what it is. */
function kindStyle(span: TraceSpanRow): Record<string, string> | undefined {
  const category = promotedColorCategory(span);
  return category === null ? undefined : { background: contextColor(category) };
}

function promotedColorCategory(span: TraceSpanRow): string | null {
  return span.synthesized ? promotedCategory(span.eventType) : null;
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
  // The absolute instant is what lines a span up against application logs — the one correlation
  // the recording-relative offsets everywhere else cannot serve.
  const startedMicros = span.startEpochMicros - traceWindow(props.spans).startMicros;
  const offset = FormattingService.formatDuration2Units(startedMicros * NANOS_PER_MICRO);
  const wallClock = FormattingService.formatTimestamp(Math.floor(span.startEpochMicros / 1_000));
  // A promoted row names its source event, so the bar never passes itself off as instrumentation.
  const promoted = span.synthesized ? `\npromoted from ${span.eventType}` : '';
  return `${span.name} — ${total} total, ${self} self${critical}${thread}\nstarted ${offset} into the trace · ${wallClock}${promoted}`;
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
/*
 * The bottom padding is the gap to the pause lanes below. The controls change what the list draws
 * and the lanes are part of the drawing, so they need to read as two things rather than as one
 * crowded strip.
 */
.wf-toolbar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem 1.1rem;
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

.wf-keys {
  margin-left: auto;
  font-size: var(--font-size-xs);
  color: var(--color-text-light);
  white-space: nowrap;
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
.wf-stripes,
.wf-cursor {
  display: grid;
  grid-template-columns: 20rem 1fr 5.5rem;
  align-items: center;
  gap: 0.5rem;
}

/* The rows' own stacking context, which the stripe layer stretches over. */
.wf-rows {
  position: relative;
  display: flex;
  flex-direction: column;
}

/*
 * Everything in the block sits above the stripe wash. The rule is here rather than on each child
 * because the wash is stretched by a parent that also holds the open detail panel and the empty
 * state: a child that does not claim a layer is painted over by it, positioned elements being drawn
 * above static ones whatever background they carry. That is what ran GC bands through the span
 * detail's identity table.
 *
 * The two overlays are excluded because they place themselves: the wash below the rows, the cursor
 * above them.
 */
.wf-rows > :not(.wf-stripes, .wf-cursor) {
  position: relative;
  z-index: 1;
}

/* Above the rows so the line is not cut by each one, below the detail panel for the reason above. */
.wf-rows > .span-detail {
  z-index: 3;
}

.wf-lane {
  padding: 0.15rem 1rem;
  /* Matches the row's accent gutter so the lane track and the bar track share an origin. */
  border-left: 2px solid transparent;
}

/* Left-aligned, so the lane names start on the same column as the Span header beneath them. */
.lane-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: var(--font-size-xs);
  letter-spacing: 0.03em;
  text-transform: uppercase;
  font-weight: 600;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.lane-name {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex: none;
}

/* Fills whatever the name and the figures leave, so the gutter carries a reading instead of air. */
.lane-meter {
  flex: 1 1 auto;
  min-width: 1.5rem;
  height: 0.3rem;
  border-radius: var(--radius-pill);
  background: var(--color-lighter);
  overflow: hidden;
}

.lane-meter i {
  display: block;
  height: 100%;
}

/*
 * Figures, so they take the monospace the duration column uses rather than the label's uppercase
 * treatment — they are numbers that sit beside a number, not part of the name.
 */
.lane-stat {
  flex: none;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  text-transform: none;
  font-weight: 400;
  /* Muted rather than light: these are figures to be read, and --color-text-light against the card
     is under 2:1. Weight and case already tell them apart from the name beside them. */
  color: var(--color-text-muted);
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
  color: var(--color-dark);
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

/*
 * Fill only. The borders this used to carry added a hard 1px edge to each side of every band, which
 * on a trace crossing a hundred pauses drew as a picket fence over the whole track and put 2px on
 * top of a width that is already at its floor for most of them — the two overstatements compounded,
 * and between them the spans underneath stopped being readable.
 *
 * There is deliberately no companion rule giving `clamped` bands a stronger fill to compensate. Most
 * pauses in a real trace are shorter than the floor — a four-second trace puts it at 17ms, and
 * collections are routinely quicker than that — so emphasising the clamped ones emphasises nearly
 * all of them, which is the fence again in another colour. What they get instead is words: the band
 * says in its title that it is drawn at the minimum, and the cursor reads out its real duration.
 */
.wf-stripe {
  position: absolute;
  top: 0;
  bottom: 0;
  background: color-mix(in srgb, var(--stripe-color) 6%, transparent);
}

/*
 * The reading of the instant under the pointer. Inert, like the wash — it follows the pointer and
 * must never be what the pointer lands on.
 */
.wf-cursor {
  position: absolute;
  inset: 0;
  padding: 0 1rem;
  /* Matches the row's accent gutter, so the cursor's track and the bars' share an origin. */
  border-left: 2px solid transparent;
  pointer-events: none;
  z-index: 2;
}

.wf-cursor-track {
  position: relative;
  height: 100%;
}

.wf-cursor-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--color-dark);
}

/*
 * Sits above the rows, over the scale it is a reading of. Pulled to the pointer's own column rather
 * than pinned to a corner: at four seconds across, a readout at the edge is a different measurement
 * from the one being pointed at.
 */
.wf-cursor-chip {
  position: absolute;
  bottom: 100%;
  left: 0;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.15rem;
  padding: 0.1rem 0.45rem;
  border-radius: var(--radius-sm);
  background: var(--color-dark);
  color: var(--color-bg-card);
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.wf-cursor-pause {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

/* In the header the column is a label rather than a figure, so it keeps the header's own size. */
.wf-head .wf-duration {
  font-size: inherit;
}

.wf-head {
  padding: 0.55rem 1rem 0.4rem;
  /*
   * The same accent gutter the rows and lanes carry. Without it the header sat two pixels left of
   * everything it labels — invisible until the lane names moved to this column and had something to
   * be out of line with.
   */
  border-left: 2px solid transparent;
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

/*
 * Two rules rather than a fill. The fill was `--color-bg-hover-alt`, three percent off the card it
 * sits on — a separation that works on a clean table and disappears entirely under a pause wash. It
 * was also opaque, so it painted out the bands on the one row being inspected, while the translucent
 * selected state left them showing: hovering a row and selecting it disagreed about what the JVM had
 * been doing to it. A rule top and bottom is unmissable at any density and hides nothing.
 */
.wf-row:hover {
  box-shadow:
    inset 0 1px 0 var(--color-primary),
    inset 0 -1px 0 var(--color-primary);
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

/* The dot that says a fold is hiding a failure — visible at a glance, explained on hover. */
.wf-folded-error {
  flex: none;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: var(--radius-pill);
  background: var(--color-danger);
}

.wf-error-jump {
  color: var(--color-danger);
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

/*
 * A promoted wait's colour is its category's, set inline where the bar is laid out — the palette
 * lives in traceLabels, not here. The whole bar is solid: a synthesized span is a leaf, so a wash
 * plus self overlay would draw the same stretch twice in two shades of the same colour.
 */
.bar-synthesized {
  opacity: 0.9;
}

.bar-synthesized .wf-self {
  display: none;
}

/*
 * The size is set here rather than left to inherit. This column appears in a row, a lane and the
 * header, and only the row carries a font-size — so a lane's total inherited the page's instead and
 * rendered a quarter larger than the identical column directly beneath it.
 */
.wf-duration {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
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

/*
 * The filtering entries. Given a border and a hit area the descriptive swatches beside them do not
 * have, so the two kinds are told apart before the click rather than by it.
 */
.legend-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.1rem 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: inherit;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  cursor: pointer;
}

.legend-toggle:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.legend-toggle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* Switched off: the row stays legible as a control, and its swatch stops asserting a colour. */
.legend-toggle.off {
  color: var(--color-text-light);
  text-decoration: line-through;
}

.legend-toggle.off .swatch {
  opacity: 0.3;
}

.swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}

/* Half solid, half washed — the anatomy of every bar, in the server hue as the worked example. */
.swatch-selfchildren {
  background: linear-gradient(
    to right,
    var(--flamegraph-color-blue) 50%,
    color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent) 50%
  );
}

.swatch-error {
  background: var(--flamegraph-color-red);
}

/* Matches the row's left accent rather than a bar colour: the critical path marks rows, not spans. */
.swatch-critical {
  background: var(--color-warning);
}

/* The bar wash itself, not the row-marker hue: a legend must show the colour it explains. */
.swatch-server {
  background: color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent);
}

.swatch-client {
  background: color-mix(in srgb, var(--flamegraph-color-cyan) 35%, transparent);
}

.swatch-internal {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}
</style>

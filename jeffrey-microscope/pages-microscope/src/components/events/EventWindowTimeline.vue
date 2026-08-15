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
  <div class="event-window">
    <div class="ev-tl">
      <div class="ev-main" :style="{ height: mainHeight + 'px' }">
        <i
          v-for="(line, i) in gridLines"
          :key="'g' + i"
          class="ev-grid"
          :style="{ left: line + '%' }"
        ></i>
        <span
          v-for="(ev, i) in visibleEvents"
          :key="i"
          class="ev-mk"
          :style="{
            left: mainLeft(ev.offset) + '%',
            width: markerWidth(ev),
            top: rowTop(ev.eventType) + 'px',
            background: `var(${colorOf(ev.eventType)})`
          }"
          :title="markerTitle(ev)"
        ></span>
      </div>
      <div class="ev-axis">
        <span v-for="(tk, i) in axisTicks" :key="i" :style="{ left: tk.pos + '%' }">{{
          tk.label
        }}</span>
      </div>
      <div class="ev-mini-lbl">Drag to select a window · double-click to reset</div>
      <div ref="miniEl" class="ev-mini" @pointerdown="onBrushDown" @dblclick="resetView">
        <span
          v-for="(ev, i) in withOffset"
          :key="i"
          class="ev-mk"
          :style="{ left: miniLeft(ev.offset) + '%', background: `var(${colorOf(ev.eventType)})` }"
        ></span>
        <div class="ev-brush" :style="brushStyle"></div>
      </div>
    </div>

    <!-- Window summary panel -->
    <div class="ev-panel">
      <div class="ev-panel-left">
        <span class="ev-win">{{ windowRangeLabel }}</span>
        <span class="ev-num">{{ eventsInView.length }}</span>
        <span class="ev-lbl">events in window</span>
        <button v-if="!isFull" type="button" class="ev-reset" @click="resetView">
          Reset window
        </button>
      </div>
      <div class="ev-breakdown">
        <!--
          The toggle is a real button and the flamegraph action its sibling, never its child: an
          interactive control inside another is invalid markup that assistive tech cannot resolve,
          and the old role="button" wrapper answered Enter but not Space.
        -->
        <div v-for="b in breakdown" :key="b.type" class="ev-bd">
          <button
            type="button"
            class="ev-bd-toggle"
            :class="{ 'ev-bd-off': !isTypeVisible(b.type) }"
            :aria-pressed="isTypeVisible(b.type)"
            :title="isTypeVisible(b.type) ? `Hide ${b.type}` : `Show ${b.type}`"
            @click="toggleType(b.type)"
          >
            <span class="ev-dot" :style="{ background: `var(${b.color})` }"></span>
            <span class="ev-bd-type">{{ b.type }}</span>
            <span class="ev-bd-bar">
              <i :style="{ width: b.pct + '%', background: `var(${b.color})` }"></i>
            </span>
            <span class="ev-bd-ct">{{ b.count }}</span>
          </button>
          <button
            v-if="b.fg"
            type="button"
            class="ev-fg-btn"
            :class="`ev-fg-${b.fg.tone}`"
            :title="`Open ${b.type} as a flamegraph for this span`"
            @click.stop="emit('flamegraph', b.type)"
          >
            <i class="bi bi-fire"></i> Flamegraph
          </button>
          <span
            v-else
            class="ev-fg-btn ev-fg-empty"
            title="No flamegraph for this event type"
            @click.stop
            >—</span
          >
        </div>
      </div>
    </div>

    <!-- Flat chronological table -->
    <div class="ev-listlbl">
      <span>Events in window — chronological</span>
      <button v-if="selectedTypes.size > 0" type="button" class="ev-clear" @click="clearTypes">
        Showing {{ visibleEvents.length }} of {{ eventsInView.length }} · show all types
      </button>
    </div>
    <div class="ev-twrap">
      <table>
        <thead>
          <tr>
            <th>Type</th>
            <th class="r">+Offset</th>
            <th class="r">Duration</th>
            <th>Fields</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(ev, i) in visibleEvents" :key="i">
            <td>
              <span class="ev-ty">
                <span
                  class="ev-dot"
                  :style="{ background: `var(${colorOf(ev.eventType)})` }"
                ></span>
                {{ ev.eventType }}
              </span>
            </td>
            <td class="r ev-off">+{{ formatOffset(ev.offset) }}</td>
            <td class="r">
              {{
                ev.durationNanos > 0
                  ? FormattingService.formatDuration2Units(ev.durationNanos)
                  : '—'
              }}
            </td>
            <td class="ev-fl" :title="ev.fields || ''">{{ truncate(ev.fields) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

import FormattingService from '@shared/services/FormattingService';
import EventTypes from '@/services/EventTypes';
import {
  axisTicks as axisTicksOf,
  breakdownOf,
  compactMillis,
  gridLinePercents,
  orderTypesByCount,
  positionPercent,
  truncateFields
} from '@/services/events/eventWindow';

/**
 * What the JVM did inside one span: every event that landed on its thread while it was open, as a
 * swim-lane per event type over the span's window, with a mini-map to zoom into part of it.
 *
 * Shared by the async-profiler span drill-down and the trace span drill-down, which ask the same
 * question of the same shape of data. It knows nothing about either feature: it takes rows and a
 * window, and reports upward when the reader asks for a flamegraph of one event type.
 */
export interface EventWindowRow {
  eventType: string;
  startEpochMillis: number;
  durationNanos: number;
  fields: string | null;
}

const NANOS_PER_MILLI = 1_000_000;
/** A drag shorter than this is a mis-click, not a selection. */
const MIN_WINDOW_MS = 50;
/**
 * The effective floor never exceeds a quarter of the span: a fixed 50ms minimum made the brush a
 * silent no-op on any span shorter than that, and short spans are exactly the ones people zoom into.
 */
const minWindowMs = () => Math.min(MIN_WINDOW_MS, Math.max(1, props.windowMillis / 4));
const FIELDS_MAX = 80;
const ROW_STEP = 16;
/** Narrowest a marker may be drawn: an instantaneous event still has to be visible and clickable. */
const MARKER_MIN_WIDTH_PX = 3;
const AXIS_TICKS = 6;

const props = defineProps<{
  events: EventWindowRow[];
  /** Epoch millis the offsets are measured from -- the span's start. */
  windowStartMillis: number;
  /** The whole window, which the mini-map always shows in full. */
  windowMillis: number;
}>();

const emit = defineEmits<{ (event: 'flamegraph', eventType: string): void }>();

const viewS = ref(0);
const viewE = ref(props.windowMillis);
const miniEl = ref<HTMLElement | null>(null);
// Event-type filter for the lane and the table; empty set means "show all types".
const selectedTypes = ref<Set<string>>(new Set());

interface OffsetEvent extends EventWindowRow {
  offset: number;
}

const withOffset = computed<OffsetEvent[]>(() =>
  props.events.map(event => ({
    ...event,
    offset: event.startEpochMillis - props.windowStartMillis
  }))
);

const orderedTypes = computed(() => orderTypesByCount(props.events));

const colorMap = computed(() => {
  const map = new Map<string, string>();
  orderedTypes.value.forEach(type => map.set(type.type, type.color));
  return map;
});

function colorOf(type: string): string {
  return colorMap.value.get(type) ?? orderedTypes.value[0]?.color ?? '--flamegraph-color-blue';
}

// Event types that can be rendered as a span-scoped flamegraph, with the CSS tone used for the row
// button. Only the categories the flamegraph cards offer (execution, wall-clock, allocation) are
// supported; everything else (e.g. ThreadCPULoad) gets no flamegraph.
interface FlamegraphInfo {
  tone: 'exec' | 'wall' | 'alloc';
}

function flamegraphInfo(type: string): FlamegraphInfo | null {
  if (EventTypes.isExecutionEventType(type)) {
    return { tone: 'exec' };
  }
  if (EventTypes.isWallClock(type)) {
    return { tone: 'wall' };
  }
  if (EventTypes.isAllocationEventType(type)) {
    return { tone: 'alloc' };
  }
  return null;
}

function rowTop(type: string): number {
  const index = orderedTypes.value.findIndex(t => t.type === type);
  return 8 + Math.max(0, index) * ROW_STEP;
}

// Grow the timeline so every type row is visible (one row per type, ROW_STEP apart) instead of
// clipping the lower rows. 8px top padding + a row per type + 8px bottom padding.
const mainHeight = computed(() => Math.max(72, 16 + orderedTypes.value.length * ROW_STEP));

const eventsInView = computed(() =>
  withOffset.value.filter(event => event.offset >= viewS.value && event.offset <= viewE.value)
);

const visibleEvents = computed(() =>
  eventsInView.value.filter(event => isTypeVisible(event.eventType))
);

function isTypeVisible(type: string): boolean {
  return selectedTypes.value.size === 0 || selectedTypes.value.has(type);
}

function toggleType(type: string): void {
  if (selectedTypes.value.has(type)) {
    selectedTypes.value.delete(type);
  } else {
    selectedTypes.value.add(type);
  }
}

function clearTypes(): void {
  selectedTypes.value.clear();
}

const isFull = computed(() => viewS.value <= 0 && viewE.value >= props.windowMillis);

const windowLabel = computed(() =>
  FormattingService.formatDuration2Units(props.windowMillis * NANOS_PER_MILLI)
);

const breakdown = computed(() =>
  breakdownOf(eventsInView.value, orderedTypes.value).map(row => ({
    ...row,
    fg: flamegraphInfo(row.type)
  }))
);

const gridLines = computed(() => gridLinePercents(viewS.value, viewE.value));

const axisTicks = computed(() => axisTicksOf(viewS.value, viewE.value, AXIS_TICKS));

const windowRangeLabel = computed(() => {
  if (isFull.value) {
    return `full span · ${windowLabel.value}`;
  }
  return `${compact(viewS.value)} – ${compact(viewE.value)} · ${compact(viewE.value - viewS.value)}`;
});

const brushStyle = computed(() => ({
  left: (viewS.value / props.windowMillis) * 100 + '%',
  width: ((viewE.value - viewS.value) / props.windowMillis) * 100 + '%'
}));

/**
 * How wide an event is drawn: its real duration against the view, floored at the marker's own width.
 *
 * An event that lasted is a stretch of time, not an instant, and drawing a 90ms lock wait as the
 * same 3px tick as a 2µs allocation hid the one thing the reader came for. The floor keeps a genuine
 * instant visible and clickable, so the lane still reads as a series of marks rather than a bar
 * chart with gaps.
 */
function markerWidth(event: OffsetEvent): string {
  if (event.durationNanos <= 0) {
    return `${MARKER_MIN_WIDTH_PX}px`;
  }
  const endOffset = event.offset + event.durationNanos / NANOS_PER_MILLI;
  const widthPercent = mainLeft(endOffset) - mainLeft(event.offset);
  return `max(${MARKER_MIN_WIDTH_PX}px, ${widthPercent}%)`;
}

function markerTitle(event: OffsetEvent): string {
  const at = `${event.eventType} · +${compact(event.offset)}`;
  if (event.durationNanos <= 0) {
    return at;
  }
  return `${at} · ${FormattingService.formatDuration2Units(event.durationNanos)}`;
}

function mainLeft(offset: number): number {
  return positionPercent(offset, viewS.value, viewE.value);
}

function miniLeft(offset: number): number {
  return positionPercent(offset, 0, props.windowMillis);
}

function compact(millis: number): string {
  return compactMillis(millis);
}

function formatOffset(offset: number): string {
  return FormattingService.formatDuration2Units(offset * NANOS_PER_MILLI);
}

function truncate(fields: string | null): string {
  return truncateFields(fields, FIELDS_MAX);
}

function resetView(): void {
  viewS.value = 0;
  viewE.value = props.windowMillis;
}

// A new span starts unzoomed and unfiltered; carrying the previous span's brush over would silently
// hide events the reader never chose to hide.
watch(
  () => [props.events, props.windowMillis] as const,
  () => {
    selectedTypes.value.clear();
    resetView();
  },
  { immediate: true }
);

// Brush drag on the mini-map.
let brushRect: DOMRect | null = null;
let brushAnchor = 0;

function pointerToMillis(clientX: number): number {
  if (!brushRect) {
    return 0;
  }
  const frac = (clientX - brushRect.left) / brushRect.width;
  return Math.min(props.windowMillis, Math.max(0, frac * props.windowMillis));
}

function onBrushDown(event: PointerEvent): void {
  if (!miniEl.value) {
    return;
  }
  brushRect = miniEl.value.getBoundingClientRect();
  brushAnchor = pointerToMillis(event.clientX);
  window.addEventListener('pointermove', onBrushMove);
  window.addEventListener('pointerup', onBrushUp);
}

function onBrushMove(event: PointerEvent): void {
  const current = pointerToMillis(event.clientX);
  let from = Math.min(brushAnchor, current);
  let to = Math.max(brushAnchor, current);
  if (to - from < minWindowMs()) {
    to = Math.min(props.windowMillis, from + minWindowMs());
    from = Math.max(0, to - minWindowMs());
  }
  viewS.value = from;
  viewE.value = to;
}

function onBrushUp(): void {
  brushRect = null;
  window.removeEventListener('pointermove', onBrushMove);
  window.removeEventListener('pointerup', onBrushUp);
}

// A drag interrupted by the view closing would otherwise leave both window listeners attached.
onUnmounted(onBrushUp);
</script>

<style scoped>
/* One flex column: the block that used to supply this spacing stayed behind in the modal. */
.event-window {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ev-tl {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.875rem 1rem;
  background: var(--color-bg-card);
}

.ev-main {
  position: relative;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ev-grid {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--color-border-light);
}

.ev-main .ev-mk {
  position: absolute;
  width: 3px;
  height: 13px;
  border-radius: 2px;
}

.ev-axis {
  position: relative;
  height: 18px;
  margin-top: 4px;
}

.ev-axis span {
  position: absolute;
  transform: translateX(-50%);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.62rem;
  color: var(--color-text-muted);
}

.ev-mini-lbl {
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin: 0.625rem 0 0.35rem;
}

.ev-mini {
  position: relative;
  height: 26px;
  background: var(--color-slate-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: crosshair;
  user-select: none;
  touch-action: none;
}

.ev-mini .ev-mk {
  position: absolute;
  top: 8px;
  height: 10px;
  width: 2px;
  opacity: 0.6;
}

.ev-brush {
  position: absolute;
  top: 0;
  bottom: 0;
  background: var(--color-primary-light);
  border-left: 2px solid var(--color-primary);
  border-right: 2px solid var(--color-primary);
}

/* Window summary panel */
.ev-panel {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 1.125rem;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 1rem 1.125rem;
  background: var(--color-bg-card);
}

.ev-panel-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.ev-win {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-primary);
}

.ev-num {
  font-size: 1.6rem;
  font-weight: 800;
  line-height: 1;
}

.ev-lbl {
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.ev-reset {
  margin-top: 0.4rem;
  align-self: flex-start;
  cursor: pointer;
  font-size: 0.66rem;
  font-weight: 600;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.3rem 0.6rem;
  background: var(--color-white);
  color: var(--color-text-muted);
}

.ev-breakdown {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ev-bd {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  border-radius: var(--radius-sm);
  padding: 0.1rem 0.3rem;
  margin: -0.1rem -0.3rem;
  transition:
    opacity 0.12s ease,
    background 0.12s ease;
}

.ev-bd:hover {
  background: var(--color-light);
}

/* The toggle inherits the row's typography; the row itself is no longer interactive. */
.ev-bd-toggle {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  padding: 0;
  cursor: pointer;
  text-align: left;
}

.ev-bd-toggle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
  border-radius: var(--radius-sm);
}

/* Off is a struck-through label, matching the waterfall's category toggles — not colour alone. */
.ev-bd-off {
  opacity: 0.55;
  text-decoration: line-through;
}

.ev-bd-type {
  min-width: 210px;
  color: var(--color-dark);
}

.ev-bd-bar {
  flex: 1;
  height: 10px;
  background: var(--color-lighter);
  border-radius: 5px;
  overflow: hidden;
}

.ev-bd-bar i {
  display: block;
  height: 100%;
  border-radius: 5px;
}

.ev-bd-ct {
  min-width: 30px;
  text-align: right;
  font-weight: 700;
  color: var(--color-text-muted);
}

.ev-dot {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  flex-shrink: 0;
}

/* Per-row flamegraph button + empty placeholder (tone reuses the flamegraph category palette) */
.ev-fg-exec {
  --ev-fg-tone: var(--flamegraph-color-green);
}

.ev-fg-wall {
  --ev-fg-tone: var(--flamegraph-color-purple);
}

.ev-fg-alloc {
  --ev-fg-tone: var(--flamegraph-color-blue);
}

.ev-fg-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  margin-left: 0.5rem;
  flex-shrink: 0;
  /* Fixed width so the filled buttons and the empty placeholder share the exact same footprint. */
  width: 7.5rem;
  box-sizing: border-box;
  font-size: 0.68rem;
  font-weight: 700;
  /* Tinted chip: a light wash of the category tone with a darker-tone label, derived from the
     pastel flamegraph palette so the button stays on-brand and readable. */
  color: color-mix(in srgb, var(--ev-fg-tone, var(--color-primary)), black 55%);
  background: color-mix(in srgb, var(--ev-fg-tone, var(--color-primary)), white 52%);
  border: 1px solid color-mix(in srgb, var(--ev-fg-tone, var(--color-primary)), white 25%);
  border-radius: var(--radius-md);
  padding: 0.32rem 0.5rem;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.13s ease;
}

.ev-fg-btn:hover {
  background: color-mix(in srgb, var(--ev-fg-tone, var(--color-primary)), white 32%);
}

.ev-fg-btn i {
  font-size: 0.78rem;
  color: color-mix(in srgb, var(--ev-fg-tone, var(--color-primary)), black 40%);
}

.ev-fg-empty {
  color: var(--color-text-muted);
  background: transparent;
  border: 1px dashed var(--color-border);
  opacity: 0.7;
  cursor: default;
}

.ev-fg-empty:hover {
  background: transparent;
}

/* Flamegraph swapped in over the events body */
.ev-listlbl {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.ev-clear {
  cursor: pointer;
  font: inherit;
  letter-spacing: inherit;
  text-transform: none;
  color: var(--color-primary);
  background: transparent;
  border: none;
  padding: 0;
}

.ev-clear:hover {
  text-decoration: underline;
}

.ev-twrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.ev-twrap table {
  width: 100%;
  border-collapse: collapse;
}

.ev-twrap thead th {
  background: var(--color-white);
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  text-align: left;
  padding: 0.5rem 0.625rem;
  border-bottom: 1px solid var(--color-border);
}

.ev-twrap thead th.r {
  text-align: right;
}

.ev-twrap tbody td {
  padding: 0.375rem 0.625rem;
  border-bottom: 1px solid var(--color-border-light);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  white-space: nowrap;
}

.ev-twrap tbody td.r {
  text-align: right;
}

.ev-ty {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.ev-off {
  color: var(--color-primary);
  font-weight: 700;
}

.ev-fl {
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}

@media (max-width: 768px) {
  .ev-panel {
    grid-template-columns: 1fr;
  }
}
</style>

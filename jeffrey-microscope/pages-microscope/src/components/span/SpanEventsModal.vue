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
  <GenericModal
    modal-id="spanEventsModal"
    :show="show"
    title="Events during span"
    icon="bi-list-ul"
    size="fullscreen"
    modal-dialog-class="events-modal-dialog"
    :show-footer="false"
    @update:show="$emit('update:show', $event)"
  >
    <div v-if="show" class="span-events">
      <!-- Header chips -->
      <div class="se-chips">
        <span class="se-chip se-chip-strong">{{ events.length }} events</span>
        <span class="se-chip">{{ orderedTypes.length }} types</span>
        <span class="se-chip"><i class="bi bi-clock"></i> {{ windowLabel }}</span>
        <span class="se-chip">
          <i class="bi bi-cpu"></i> {{ threadName || 'unknown' }}
          <span v-if="isVirtual" class="se-vt">virtual</span>
        </span>
        <span v-if="tag" class="se-chip"><i class="bi bi-tag"></i> {{ tag }}</span>
      </div>

      <LoadingState v-if="loading" message="Loading events..." />

      <EmptyState
        v-else-if="events.length === 0"
        title="No events"
        message="No JFR events were recorded on this thread during the span window."
        icon="bi-inboxes"
      />

      <template v-else>
        <!-- Span-scoped flamegraph swapped in over the events body -->
        <div v-if="mode === 'flamegraph'" class="se-fg-view">
          <div class="se-fg-bar">
            <button type="button" class="se-fg-back" @click="backToEvents">
              <i class="bi bi-arrow-left"></i> Back to events
            </button>
            <span class="se-fg-active"><i class="bi bi-fire"></i> {{ activeEventType }}</span>
          </div>
          <div :id="SPAN_FG_SCROLL_ID" class="se-fg-scroll">
            <TimeSeriesChart
              :graph-updater="graphUpdater"
              :primary-axis-type="
                TimeseriesEventAxeFormatter.resolveAxisFormatter(activeUseWeight, activeEventType)
              "
              :visible-minutes="60"
              :zoom-enabled="true"
              time-unit="seconds"
            />
            <FlamegraphComponent
              :with-timeseries="true"
              :use-weight="activeUseWeight"
              :use-guardian="null"
              :scrollable-wrapper-class="SPAN_FG_SCROLL_ID"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater"
              @loaded="onFlamegraphLoaded"
            />
          </div>
        </div>

        <template v-else>
        <!-- Timeline + brush -->
        <div class="se-tl">
          <div class="se-main" :style="{ height: mainHeight + 'px' }">
            <i
              v-for="(line, i) in gridLines"
              :key="'g' + i"
              class="se-grid"
              :style="{ left: line + '%' }"
            ></i>
            <span
              v-for="(ev, i) in visibleEvents"
              :key="i"
              class="se-mk"
              :style="{ left: mainLeft(ev.offset) + '%', top: rowTop(ev.eventType) + 'px', background: `var(${colorOf(ev.eventType)})` }"
              :title="ev.eventType + ' · +' + compact(ev.offset)"
            ></span>
          </div>
          <div class="se-axis">
            <span v-for="(tk, i) in axisTicks" :key="i" :style="{ left: tk.pos + '%' }">{{ tk.label }}</span>
          </div>
          <div class="se-mini-lbl">Drag to select a window · double-click to reset</div>
          <div ref="miniEl" class="se-mini" @pointerdown="onBrushDown" @dblclick="resetView">
            <span
              v-for="(ev, i) in withOffset"
              :key="i"
              class="se-mk"
              :style="{ left: miniLeft(ev.offset) + '%', background: `var(${colorOf(ev.eventType)})` }"
            ></span>
            <div class="se-brush" :style="brushStyle"></div>
          </div>
        </div>

        <!-- Window summary panel -->
        <div class="se-panel">
          <div class="se-panel-left">
            <span class="se-win">{{ windowRangeLabel }}</span>
            <span class="se-num">{{ eventsInView.length }}</span>
            <span class="se-lbl">events in window</span>
            <button v-if="!isFull" type="button" class="se-reset" @click="resetView">Reset window</button>
          </div>
          <div class="se-breakdown">
            <div
              v-for="b in breakdown"
              :key="b.type"
              class="se-bd"
              :class="{ 'se-bd-off': !isTypeVisible(b.type) }"
              role="button"
              tabindex="0"
              :title="isTypeVisible(b.type) ? `Hide ${b.type}` : `Show ${b.type}`"
              @click="toggleType(b.type)"
              @keydown.enter.prevent="toggleType(b.type)"
            >
              <span class="se-dot" :style="{ background: `var(${b.color})` }"></span>
              <span class="se-bd-type">{{ b.type }}</span>
              <span class="se-bd-bar">
                <i :style="{ width: b.pct + '%', background: `var(${b.color})` }"></i>
              </span>
              <span class="se-bd-ct">{{ b.count }}</span>
              <button
                v-if="b.fg"
                type="button"
                class="se-fg-btn"
                :class="`se-fg-${b.fg.tone}`"
                :title="`Open ${b.type} as a flamegraph for this span`"
                @click.stop="openFlamegraph(b.type)"
                @keydown.enter.stop
              >
                <i class="bi bi-fire"></i> Flamegraph
              </button>
              <span
                v-else
                class="se-fg-btn se-fg-empty"
                title="No flamegraph for this event type"
                @click.stop
              >—</span>
            </div>
          </div>
        </div>

        <!-- Flat chronological table -->
        <div class="se-listlbl">
          <span>Events in window — chronological</span>
          <button
            v-if="selectedTypes.size > 0"
            type="button"
            class="se-clear"
            @click="clearTypes"
          >
            Showing {{ visibleEvents.length }} of {{ eventsInView.length }} · show all types
          </button>
        </div>
        <div class="se-twrap">
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
                  <span class="se-ty">
                    <span class="se-dot" :style="{ background: `var(${colorOf(ev.eventType)})` }"></span>
                    {{ ev.eventType }}
                  </span>
                </td>
                <td class="r se-off">+{{ formatOffset(ev.offset) }}</td>
                <td class="r">{{ ev.durationNanos > 0 ? FormattingService.formatDuration2Units(ev.durationNanos) : '—' }}</td>
                <td class="se-fl" :title="ev.fields || ''">{{ truncate(ev.fields) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        </template>
      </template>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import FormattingService from '@/services/FormattingService';
import EventTypes from '@/services/EventTypes';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import SingleSpanFlamegraphClient from '@/services/api/SingleSpanFlamegraphClient';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import type { SpanEventRow } from '@/services/api/model/span/SpanModels';

const NANOS_PER_MILLI = 1_000_000;
const MIN_WINDOW_MS = 50;
const FIELDS_MAX = 80;
const ROW_STEP = 16;
const AXIS_TICKS = 6;
// Delay so the swapped-in flamegraph + timeseries are rendered and their callbacks registered before
// the graph updater starts fetching (mirrors SpanTagFlamegraphs.vue).
const MODAL_INIT_DELAY_MS = 200;
const SPAN_FG_SCROLL_ID = 'span-fg-scroll';
const TYPE_COLORS = [
  '--flamegraph-color-blue',
  '--flamegraph-color-green',
  '--flamegraph-color-orange',
  '--flamegraph-color-purple',
  '--flamegraph-color-cyan',
  '--flamegraph-color-pink',
  '--flamegraph-color-peach',
  '--flamegraph-color-teal',
  '--flamegraph-color-red'
];

const props = defineProps<{
  show: boolean;
  profileId: string;
  threadHash: string;
  startEpochMillis: number;
  durationNanos: number;
  threadName: string;
  isVirtual?: boolean;
  tag?: string;
}>();

defineEmits<{
  'update:show': [value: boolean];
}>();

const client = new ProfileAsyncProfilerClient(props.profileId);

const loading = ref(false);
const events = ref<SpanEventRow[]>([]);
const viewS = ref(0);
const viewE = ref(0);
const miniEl = ref<HTMLElement | null>(null);
// Event-type filter for the chronological table; empty set means "show all types".
const selectedTypes = ref<Set<string>>(new Set());

// Span-scoped flamegraph swap-in. graphUpdater/flamegraphTooltip are plain (non-reactive) and are
// assigned in openFlamegraph() before `mode` flips to 'flamegraph', so the re-render reads them fresh.
const mode = ref<'events' | 'flamegraph'>('events');
const activeEventType = ref('');
const activeUseWeight = ref(false);
let graphUpdater: GraphUpdater;
let flamegraphTooltip: FlamegraphTooltip;

const windowMillis = computed(() => Math.max(1, Math.round(props.durationNanos / NANOS_PER_MILLI)));
const windowLabel = computed(() => FormattingService.formatDuration2Units(props.durationNanos));

interface OffsetEvent extends SpanEventRow {
  offset: number;
}

const withOffset = computed<OffsetEvent[]>(() =>
  events.value.map(e => ({ ...e, offset: e.startEpochMillis - props.startEpochMillis }))
);

const orderedTypes = computed(() => {
  const counts = new Map<string, number>();
  for (const e of events.value) {
    counts.set(e.eventType, (counts.get(e.eventType) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([type], index) => ({ type, color: TYPE_COLORS[index % TYPE_COLORS.length] }));
});

const colorMap = computed(() => {
  const map = new Map<string, string>();
  orderedTypes.value.forEach(t => map.set(t.type, t.color));
  return map;
});

function colorOf(type: string): string {
  return colorMap.value.get(type) ?? TYPE_COLORS[0];
}

// Event types that can be rendered as a span-scoped flamegraph, with the CSS tone used for the row
// button and the default weight mode. Only the categories the Spans flamegraph tab offers (execution,
// wall-clock, allocation) are supported; everything else (e.g. ThreadCPULoad) gets no flamegraph.
interface FlamegraphInfo {
  tone: 'exec' | 'wall' | 'alloc';
  useWeight: boolean;
}

function flamegraphInfo(type: string): FlamegraphInfo | null {
  if (EventTypes.isExecutionEventType(type)) {
    return { tone: 'exec', useWeight: false };
  }
  if (EventTypes.isWallClock(type)) {
    return { tone: 'wall', useWeight: false };
  }
  if (EventTypes.isAllocationEventType(type)) {
    return { tone: 'alloc', useWeight: false };
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
  withOffset.value.filter(e => e.offset >= viewS.value && e.offset <= viewE.value)
);

const visibleEvents = computed(() =>
  eventsInView.value.filter(e => isTypeVisible(e.eventType))
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

const isFull = computed(() => viewS.value <= 0 && viewE.value >= windowMillis.value);

const breakdown = computed(() => {
  const inView = eventsInView.value;
  const max = Math.max(
    1,
    ...orderedTypes.value.map(t => inView.filter(e => e.eventType === t.type).length)
  );
  return orderedTypes.value.map(t => {
    const count = inView.filter(e => e.eventType === t.type).length;
    return { type: t.type, color: t.color, count, pct: (count / max) * 100, fg: flamegraphInfo(t.type) };
  });
});

const gridLines = computed(() => {
  const span = viewE.value - viewS.value || 1;
  const lines: number[] = [];
  for (let s = Math.ceil(viewS.value / 1000) * 1000; s < viewE.value; s += 1000) {
    lines.push(((s - viewS.value) / span) * 100);
  }
  return lines;
});

const axisTicks = computed(() => {
  const span = viewE.value - viewS.value || 1;
  const ticks = [];
  for (let i = 0; i <= AXIS_TICKS - 1; i++) {
    const t = viewS.value + (span * i) / (AXIS_TICKS - 1);
    ticks.push({ pos: (i / (AXIS_TICKS - 1)) * 100, label: compact(t) });
  }
  return ticks;
});

const windowRangeLabel = computed(() => {
  if (isFull.value) {
    return `full span · ${windowLabel.value}`;
  }
  return `${compact(viewS.value)} – ${compact(viewE.value)} · ${compact(viewE.value - viewS.value)}`;
});

const brushStyle = computed(() => ({
  left: (viewS.value / windowMillis.value) * 100 + '%',
  width: ((viewE.value - viewS.value) / windowMillis.value) * 100 + '%'
}));

function mainLeft(offset: number): number {
  const span = viewE.value - viewS.value || 1;
  return Math.min(100, Math.max(0, ((offset - viewS.value) / span) * 100));
}

function miniLeft(offset: number): number {
  return Math.min(100, Math.max(0, (offset / windowMillis.value) * 100));
}

function compact(ms: number): string {
  return ms >= 1000 ? (ms / 1000).toFixed(2) + 's' : Math.round(ms) + 'ms';
}

function formatOffset(offset: number): string {
  return FormattingService.formatDuration2Units(offset * NANOS_PER_MILLI);
}

function truncate(fields: string | null): string {
  if (!fields) {
    return '';
  }
  return fields.length > FIELDS_MAX ? fields.slice(0, FIELDS_MAX) + '…' : fields;
}

function resetView() {
  viewS.value = 0;
  viewE.value = windowMillis.value;
}

// Brush drag on the mini-map.
let brushRect: DOMRect | null = null;
let brushAnchor = 0;

function pointerToMillis(clientX: number): number {
  if (!brushRect) {
    return 0;
  }
  const frac = (clientX - brushRect.left) / brushRect.width;
  return Math.min(windowMillis.value, Math.max(0, frac * windowMillis.value));
}

function onBrushDown(event: PointerEvent) {
  if (!miniEl.value) {
    return;
  }
  brushRect = miniEl.value.getBoundingClientRect();
  brushAnchor = pointerToMillis(event.clientX);
  window.addEventListener('pointermove', onBrushMove);
  window.addEventListener('pointerup', onBrushUp);
}

function onBrushMove(event: PointerEvent) {
  const current = pointerToMillis(event.clientX);
  let start = Math.min(brushAnchor, current);
  let end = Math.max(brushAnchor, current);
  if (end - start < MIN_WINDOW_MS) {
    end = Math.min(windowMillis.value, start + MIN_WINDOW_MS);
    start = end - MIN_WINDOW_MS;
  }
  viewS.value = Math.max(0, start);
  viewE.value = end;
}

function onBrushUp() {
  window.removeEventListener('pointermove', onBrushMove);
  window.removeEventListener('pointerup', onBrushUp);
}

// Swap the modal body to a flamegraph scoped to this single span (its thread + window) for the given
// event type. The backend derives the scope from the span interval, so no time range/thread is sent.
function openFlamegraph(type: string): void {
  const info = flamegraphInfo(type);
  if (info === null) {
    return;
  }
  activeEventType.value = type;
  activeUseWeight.value = info.useWeight;

  const to = props.startEpochMillis + windowMillis.value;
  const fgClient = new SingleSpanFlamegraphClient(
    props.profileId,
    props.threadHash,
    props.startEpochMillis,
    to,
    type,
    false,
    info.useWeight,
    false,
    false,
    false
  );

  graphUpdater = new FullGraphUpdater(fgClient, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(type, info.useWeight, false);

  mode.value = 'flamegraph';

  setTimeout(() => {
    graphUpdater.initialize();
  }, MODAL_INIT_DELAY_MS);
}

function backToEvents(): void {
  mode.value = 'events';
}

function onFlamegraphLoaded(): void {
  const wrapper = document.getElementById(SPAN_FG_SCROLL_ID);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

async function load() {
  loading.value = true;
  events.value = [];
  mode.value = 'events';
  selectedTypes.value.clear();
  try {
    const to = props.startEpochMillis + windowMillis.value;
    events.value = await client.getSpanEvents(props.threadHash, props.startEpochMillis, to);
  } catch (e: unknown) {
    console.error('Failed to load span events:', e);
    events.value = [];
  } finally {
    resetView();
    loading.value = false;
  }
}

watch(
  () => [props.show, props.threadHash, props.startEpochMillis, props.durationNanos],
  () => {
    if (props.show) {
      load();
    }
  },
  { immediate: true }
);

onUnmounted(onBrushUp);
</script>

<style scoped>
.span-events {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.se-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.se-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-text-muted);
  background: var(--color-light);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.55rem;
}

.se-chip-strong {
  color: var(--color-primary);
  font-weight: 700;
}

.se-vt {
  margin-left: 0.3rem;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-purple);
}

/* Timeline + brush */
.se-tl {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.875rem 1rem;
  background: var(--color-bg-card);
}

.se-main {
  position: relative;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.se-grid {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--color-border-light);
}

.se-main .se-mk {
  position: absolute;
  width: 3px;
  height: 13px;
  border-radius: 2px;
}

.se-axis {
  position: relative;
  height: 18px;
  margin-top: 4px;
}

.se-axis span {
  position: absolute;
  transform: translateX(-50%);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.62rem;
  color: var(--color-text-muted);
}

.se-mini-lbl {
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin: 0.625rem 0 0.35rem;
}

.se-mini {
  position: relative;
  height: 26px;
  background: var(--color-slate-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: crosshair;
  user-select: none;
  touch-action: none;
}

.se-mini .se-mk {
  position: absolute;
  top: 8px;
  height: 10px;
  width: 2px;
  opacity: 0.6;
}

.se-brush {
  position: absolute;
  top: 0;
  bottom: 0;
  background: var(--color-primary-light);
  border-left: 2px solid var(--color-primary);
  border-right: 2px solid var(--color-primary);
}

/* Window summary panel */
.se-panel {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 1.125rem;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 1rem 1.125rem;
  background: var(--color-bg-card);
}

.se-panel-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.se-win {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-primary);
}

.se-num {
  font-size: 1.6rem;
  font-weight: 800;
  line-height: 1;
}

.se-lbl {
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.se-reset {
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

.se-breakdown {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.se-bd {
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
  transition: opacity 0.12s ease, background 0.12s ease;
}

.se-bd:hover {
  background: var(--color-light);
}

.se-bd-off {
  opacity: 0.4;
}

.se-bd-type {
  min-width: 210px;
  color: var(--color-dark);
}

.se-bd-bar {
  flex: 1;
  height: 10px;
  background: var(--color-lighter);
  border-radius: 5px;
  overflow: hidden;
}

.se-bd-bar i {
  display: block;
  height: 100%;
  border-radius: 5px;
}

.se-bd-ct {
  min-width: 30px;
  text-align: right;
  font-weight: 700;
  color: var(--color-text-muted);
}

.se-dot {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  flex-shrink: 0;
}

/* Per-row flamegraph button + empty placeholder (tone reuses the flamegraph category palette) */
.se-fg-exec {
  --se-fg-tone: var(--flamegraph-color-green);
}

.se-fg-wall {
  --se-fg-tone: var(--flamegraph-color-purple);
}

.se-fg-alloc {
  --se-fg-tone: var(--flamegraph-color-blue);
}

.se-fg-btn {
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
  color: color-mix(in srgb, var(--se-fg-tone, var(--color-primary)), black 55%);
  background: color-mix(in srgb, var(--se-fg-tone, var(--color-primary)), white 52%);
  border: 1px solid color-mix(in srgb, var(--se-fg-tone, var(--color-primary)), white 25%);
  border-radius: var(--radius-md);
  padding: 0.32rem 0.5rem;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.13s ease;
}

.se-fg-btn:hover {
  background: color-mix(in srgb, var(--se-fg-tone, var(--color-primary)), white 32%);
}

.se-fg-btn i {
  font-size: 0.78rem;
  color: color-mix(in srgb, var(--se-fg-tone, var(--color-primary)), black 40%);
}

.se-fg-empty {
  color: var(--color-text-muted);
  background: transparent;
  border: 1px dashed var(--color-border);
  opacity: 0.7;
  cursor: default;
}

.se-fg-empty:hover {
  background: transparent;
}

/* Flamegraph swapped in over the events body */
.se-fg-view {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.se-fg-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.se-fg-back {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.3rem 0.6rem;
  cursor: pointer;
}

.se-fg-back:hover {
  background: var(--color-light);
}

.se-fg-active {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-dark);
}

.se-fg-scroll {
  max-height: calc(100vh - 220px);
  overflow: auto;
}

/* Flat table */
.se-listlbl {
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

.se-clear {
  cursor: pointer;
  font: inherit;
  letter-spacing: inherit;
  text-transform: none;
  color: var(--color-primary);
  background: transparent;
  border: none;
  padding: 0;
}

.se-clear:hover {
  text-decoration: underline;
}

.se-twrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.se-twrap table {
  width: 100%;
  border-collapse: collapse;
}

.se-twrap thead th {
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

.se-twrap thead th.r {
  text-align: right;
}

.se-twrap tbody td {
  padding: 0.375rem 0.625rem;
  border-bottom: 1px solid var(--color-border-light);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  white-space: nowrap;
}

.se-twrap tbody td.r {
  text-align: right;
}

.se-ty {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.se-off {
  color: var(--color-primary);
  font-weight: 700;
}

.se-fl {
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}

@media (max-width: 768px) {
  .se-panel {
    grid-template-columns: 1fr;
  }
}
</style>

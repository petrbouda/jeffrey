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
        <span class="se-chip"><i class="bi bi-cpu"></i> {{ threadName || 'unknown' }}</span>
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
        <!-- Timeline + brush -->
        <div class="se-tl">
          <div class="se-legend">
            <span v-for="t in orderedTypes" :key="t.type">
              <i :style="{ background: `var(${t.color})` }"></i>{{ shortType(t.type) }}
            </span>
          </div>
          <div class="se-main">
            <i
              v-for="(line, i) in gridLines"
              :key="'g' + i"
              class="se-grid"
              :style="{ left: line + '%' }"
            ></i>
            <span
              v-for="(ev, i) in eventsInView"
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
            <div v-for="b in breakdown" :key="b.type" class="se-bd">
              <span class="se-dot" :style="{ background: `var(${b.color})` }"></span>
              <span class="se-bd-type">{{ b.type }}</span>
              <span class="se-bd-bar">
                <i :style="{ width: b.pct + '%', background: `var(${b.color})` }"></i>
              </span>
              <span class="se-bd-ct">{{ b.count }}</span>
            </div>
          </div>
        </div>

        <!-- Flat chronological table -->
        <div class="se-listlbl">Events in window — chronological</div>
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
              <tr v-for="(ev, i) in eventsInView" :key="i">
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
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import FormattingService from '@/services/FormattingService';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import type { SpanEventRow } from '@/services/api/model/span/SpanModels';

const NANOS_PER_MILLI = 1_000_000;
const MIN_WINDOW_MS = 50;
const FIELDS_MAX = 80;
const ROW_STEP = 16;
const AXIS_TICKS = 6;
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
  osThreadId: number;
  startEpochMillis: number;
  durationNanos: number;
  threadName: string;
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

function rowTop(type: string): number {
  const index = orderedTypes.value.findIndex(t => t.type === type);
  return 8 + Math.max(0, index) * ROW_STEP;
}

const eventsInView = computed(() =>
  withOffset.value.filter(e => e.offset >= viewS.value && e.offset <= viewE.value)
);

const isFull = computed(() => viewS.value <= 0 && viewE.value >= windowMillis.value);

const breakdown = computed(() => {
  const inView = eventsInView.value;
  const max = Math.max(
    1,
    ...orderedTypes.value.map(t => inView.filter(e => e.eventType === t.type).length)
  );
  return orderedTypes.value.map(t => {
    const count = inView.filter(e => e.eventType === t.type).length;
    return { type: t.type, color: t.color, count, pct: (count / max) * 100 };
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

function shortType(type: string): string {
  return type.replace(/^jdk\./, '').replace(/^profiler\./, '');
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

async function load() {
  loading.value = true;
  events.value = [];
  try {
    const to = props.startEpochMillis + windowMillis.value;
    events.value = await client.getSpanEvents(props.osThreadId, props.startEpochMillis, to);
  } catch (e: unknown) {
    console.error('Failed to load span events:', e);
    events.value = [];
  } finally {
    resetView();
    loading.value = false;
  }
}

watch(
  () => [props.show, props.osThreadId, props.startEpochMillis, props.durationNanos],
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
  height: 100%;
  min-height: 0;
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

/* Timeline + brush */
.se-tl {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.875rem 1rem;
  background: var(--color-bg-card);
}

.se-legend {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.7rem;
}

.se-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-dark);
}

.se-legend i {
  width: 11px;
  height: 11px;
  border-radius: 3px;
}

.se-main {
  position: relative;
  height: 84px;
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

/* Flat table */
.se-listlbl {
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.se-twrap {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.se-twrap table {
  width: 100%;
  border-collapse: collapse;
}

.se-twrap thead th {
  position: sticky;
  top: 0;
  background: var(--color-white);
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  text-align: left;
  padding: 0.5rem 0.625rem;
  border-bottom: 1px solid var(--color-border);
  z-index: 1;
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

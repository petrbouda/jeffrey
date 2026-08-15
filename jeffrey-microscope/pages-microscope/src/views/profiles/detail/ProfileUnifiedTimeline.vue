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
  The whole JVM on one time axis: pinned tracks for what stopped the world, then a track per thread
  carrying the spans that ran on it.

  The view every other trace screen cannot give, because it is the only one where a GC pause and a
  request's spans are drawn against the same clock. Put the cursor on a pause and read straight down
  to see every thread it stopped.
-->
<template>
  <div class="dashboard-container">
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-bar-chart-steps" title="Unified timeline">
          <template #actions>
            <div class="tl-actions">
              <!-- The remedy in visible words with the button that performs it, not in a title
                   attribute nobody hovers: a capped window silently drawn as complete data is a
                   biased sample wearing the costume of the whole truth. -->
              <!-- In a capped window the canvas swaps to density columns, and the pill says so:
                   the count-shape is honest, but only zooming in brings back clickable spans. -->
              <span v-if="timelineWindow?.truncated" class="tl-capped">
                <i class="bi bi-exclamation-triangle"></i> too busy for spans, showing density —
                <button type="button" class="tl-capped-zoom" @click="zoomIn">zoom in</button>
              </span>
              <span v-else-if="timelineWindow?.statesTruncated" class="tl-capped">
                <i class="bi bi-exclamation-triangle"></i> not all waits drawn —
                <button type="button" class="tl-capped-zoom" @click="zoomIn">zoom in</button>
              </span>
              <span v-if="updating" class="tl-updating">
                <i class="bi bi-arrow-repeat"></i> updating…
              </span>
              <button type="button" class="tl-btn" :disabled="isFullView" @click="resetView">
                <i class="bi bi-arrows-angle-expand"></i> Fit all
              </button>
            </div>
          </template>
        </MainCardHeader>
      </template>

      <p class="tl-hint">
        <b>Ctrl+scroll</b> to zoom at the cursor · <b>scroll</b> to move through the threads ·
        <b>drag</b> to pan · <b>click a span</b> to open its trace · <b>click a pool header</b> to
        fold it · keyboard: <b>←→</b> pan · <b>↑↓</b> scroll · <b>+−</b> zoom · <b>Home</b> fit all
      </p>

      <!--
        The canvas host stays mounted through every state. The Konva stage is created once against
        this element; putting it behind v-if meant one empty window or one failed fetch unmounted
        it and the stage drew into a detached node for the rest of the session. The three states
        overlay the canvas instead of replacing it, so "zoom out" remains something the reader can
        actually do while reading the empty-state advice.
      -->
      <div class="tl-stage">
        <div ref="hostRef" class="tl-host"></div>

        <div v-if="loading && !timelineWindow" class="tl-overlay">
          <LoadingState message="Loading timeline..." />
        </div>

        <div v-else-if="error" class="tl-overlay">
          <ErrorState :message="error" @retry="load" />
        </div>

        <div v-else-if="showEmpty" class="tl-overlay tl-overlay-passthrough">
          <EmptyState
            icon="bi-bar-chart-steps"
            title="Nothing ran in this window"
            description="No spans were recorded here. Zoom out, or pick a busier part of the recording."
          />
        </div>
      </div>

      <div class="tl-legend">
        <span v-for="entry in LEGEND" :key="entry.label">
          <i :style="{ background: entry.color }"></i>{{ entry.label }}
        </span>
        <!-- Only the wait categories this window actually recorded — a legend of absent things
             teaches the reader to look for what is not there. -->
        <span v-for="entry in stateLegend" :key="entry.label" class="tl-legend-state">
          <i :style="{ background: entry.color }"></i>{{ entry.label }} (wait)
        </span>
      </div>
    </MainCard>

    <TraceSpansModal
      v-model:show="traceModalOpen"
      :profile-id="profileId"
      :trace-id="openTraceId"
      :root-name="openTraceName"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';

import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import { profileStore } from '@/stores/profileStore';
import TimelineCanvas from '@/services/timeline/TimelineCanvas';
import { TIMELINE_LEGEND } from '@/services/timeline/timelineTheme';
import { contextColor, contextLabel } from '@/services/trace/traceLabels';
import { clampViewport } from '@/services/timeline/TimelineViewport';

import type { TimelineWindow } from '@/services/api/model/TimelineModels';
import type { Viewport } from '@/services/timeline/TimelineViewport';

/** The store speaks milliseconds; the timeline and the stored span bounds speak microseconds. */
const MICROS_PER_MILLI = 1_000;
const LEGEND = TIMELINE_LEGEND;
/** Refetch no faster than this while panning, so a drag is one request rather than sixty. */
const REFETCH_DEBOUNCE_MS = 180;
/** How long the viewport must hold still before it is written into the URL. */
const URL_SYNC_DEBOUNCE_MS = 350;

const props = defineProps<{ profileId: string }>();

const route = useRoute();
const router = useRouter();

const hostRef = ref<HTMLDivElement | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const timelineWindow = shallowRef<TimelineWindow | null>(null);
const traceModalOpen = ref(false);
const openTraceId = ref('');
const openTraceName = ref('');

/** A refresh of a window already on screen — shown as a corner cue, never as a blanking overlay. */
const updating = computed(() => loading.value && timelineWindow.value !== null);

/** Legend entries for the wait categories present in this window's underlay, in context colours. */
const stateLegend = computed(() => {
  const categories = new Set<string>();
  for (const track of timelineWindow.value?.tracks ?? []) {
    for (const state of track.states ?? []) {
      categories.add(state.category);
    }
  }
  return [...categories].sort().map(category => ({
    label: contextLabel(category),
    color: contextColor(category)
  }));
});

let canvas: TimelineCanvas | null = null;
let client: ProfileTracesClient;
let refetchTimer: number | undefined;
let inFlight = 0;

/**
 * The recording's own bounds, which the viewport can never leave. Absolute epoch micros, converted
 * once here from the store's millis so nothing downstream has to think about units.
 */
const bounds = computed<Viewport>(() => {
  // The store is a plain object of refs, so nothing unwraps for us. Reading the ref itself here
  // (without .value) made this branch dead — a ComputedRef is always truthy — and every bound NaN.
  const recording = profileStore.recordingWindow.value;
  if (!recording) {
    return { from: 0, to: 0 };
  }
  const from = recording.startEpochMillis * MICROS_PER_MILLI;
  return { from, to: from + recording.durationMillis * MICROS_PER_MILLI };
});

const view = ref<Viewport>({ from: 0, to: 0 });

const isFullView = computed(
  () => view.value.from <= bounds.value.from && view.value.to >= bounds.value.to
);

const showEmpty = computed(
  () => !loading.value && timelineWindow.value !== null && timelineWindow.value.tracks.length === 0
);

/**
 * Fetches the current viewport.
 *
 * Responses are matched against a request counter rather than awaited in order: panning fires
 * several overlapping requests and the slowest is not the newest, so without this an early window
 * can land last and paint a viewport the reader has already left.
 */
async function load(): Promise<void> {
  if (bounds.value.to <= bounds.value.from) {
    loading.value = false;
    return;
  }
  const ticket = ++inFlight;
  loading.value = true;
  error.value = null;
  try {
    const next = await client.getTimelineWindow(view.value.from, view.value.to);
    if (ticket !== inFlight) {
      return;
    }
    timelineWindow.value = next;
    canvas?.setData(next);
  } catch {
    if (ticket !== inFlight) {
      return;
    }
    error.value = 'Failed to load the timeline.';
  } finally {
    if (ticket === inFlight) {
      loading.value = false;
    }
  }
}

function scheduleLoad(): void {
  window.clearTimeout(refetchTimer);
  refetchTimer = window.setTimeout(load, REFETCH_DEBOUNCE_MS);
}

function onViewChanged(next: Viewport): void {
  view.value = next;
  // The canvas redraws itself from what it already has, so panning stays smooth while the newly
  // uncovered part of the window is still being fetched.
  scheduleLoad();
  scheduleUrlSync();
}

/**
 * The viewport mirrored into the URL once it holds still, so "look at this window" is a link. A
 * replace, debounced: a pan emits dozens of viewports a second, and none of the intermediate ones
 * is a place anyone means to return to. The full view carries no params — the default is clean.
 */
let urlSyncTimer: number | undefined;
function scheduleUrlSync(): void {
  window.clearTimeout(urlSyncTimer);
  urlSyncTimer = window.setTimeout(() => {
    const query = { ...route.query };
    if (isFullView.value) {
      delete query.from;
      delete query.to;
    } else {
      query.from = String(Math.round(view.value.from));
      query.to = String(Math.round(view.value.to));
    }
    router.replace({ query });
  }, URL_SYNC_DEBOUNCE_MS);
}

/**
 * The window a deep link asked for, read once at setup. Applied when the recording's bounds
 * resolve — which on a cold route is after mount — and only the first time, so a later profile
 * switch falls back to the full view rather than replaying a stale link.
 */
let pendingQueryView: Viewport | null = (() => {
  const from = Number(route.query.from);
  const to = Number(route.query.to);
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
    return null;
  }
  return { from, to };
})();

function resetView(): void {
  view.value = { ...bounds.value };
  canvas?.setViewport(view.value);
  load();
  scheduleUrlSync();
}

function zoomIn(): void {
  canvas?.zoomIn();
}

function openTrace(traceId: string, spanName: string): void {
  openTraceId.value = traceId;
  // The clicked span's name titles the modal. For a root span (the usual click target) that is
  // exactly the trace's name; for a child it still names what the reader aimed at, which beats the
  // generic "Spans in trace" the modal falls back to.
  openTraceName.value = spanName;
  traceModalOpen.value = true;
  // Same contract as the two sibling trace pages: the open trace lives in the URL, and Back
  // closes the dialog.
  router.push({ query: { ...route.query, trace: traceId } });
}

// Closing the modal takes the trace back out of the URL, so a reload does not reopen it.
watch(traceModalOpen, open => {
  if (!open && route.query.trace) {
    const query = { ...route.query };
    delete query.trace;
    router.replace({ query });
  }
});

// Reopens the trace named in the URL — a deep link or a Back/Forward step. The modal fetches
// everything it draws from the id alone.
watch(
  () => route.query.trace,
  traceId => {
    if (!traceId) {
      traceModalOpen.value = false;
      return;
    }
    openTraceId.value = traceId as string;
    traceModalOpen.value = true;
  },
  { immediate: true }
);

/**
 * Follows ?from&to while the view is already mounted — the "Show on timeline" edge lands here as a
 * query-only navigation, which remounts nothing. Guarded against our own URL sync: a window this
 * view wrote back is by construction equal to the current viewport and is not re-applied.
 */
watch(
  () => [route.query.from, route.query.to] as const,
  ([fromRaw, toRaw]) => {
    const from = Number(fromRaw);
    const to = Number(toRaw);
    if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
      return;
    }
    if (Math.round(view.value.from) === from && Math.round(view.value.to) === to) {
      return;
    }
    if (bounds.value.to <= bounds.value.from) {
      // Bounds not resolved yet; remember the ask for the bounds watch to honour.
      pendingQueryView = { from, to };
      return;
    }
    view.value = clampViewport({ from, to }, bounds.value);
    canvas?.setViewport(view.value);
    load();
  }
);

onMounted(() => {
  client = new ProfileTracesClient(props.profileId);
  view.value = clampViewport(bounds.value, bounds.value);
  // On a warm navigation the bounds are already known and the watch below never fires, so a deep
  // link's window has to be honoured here; on a cold route the watch picks it up instead.
  if (pendingQueryView !== null && bounds.value.to > bounds.value.from) {
    view.value = clampViewport(pendingQueryView, bounds.value);
    pendingQueryView = null;
  }

  if (hostRef.value) {
    canvas = new TimelineCanvas(hostRef.value, {
      bounds: bounds.value,
      viewport: view.value,
      onViewportChanged: onViewChanged,
      onSpanSelected: openTrace
    });
  }
  load();
});

onBeforeUnmount(() => {
  window.clearTimeout(refetchTimer);
  window.clearTimeout(urlSyncTimer);
  canvas?.destroy();
  canvas = null;
});

// The viewport follows the recording's bounds, not the profile id: on a cold route the profile
// resolves after this component mounts, so watching the id alone reset the view against the
// previous profile's window (or against nothing at all) and the first fetch targeted the wrong
// range. Watching the derived bounds re-seeds exactly when the real window is known.
watch(
  () => [props.profileId, bounds.value.from, bounds.value.to] as const,
  ([profileId]) => {
    client = new ProfileTracesClient(profileId);
    if (pendingQueryView !== null && bounds.value.to > bounds.value.from) {
      view.value = clampViewport(pendingQueryView, bounds.value);
      pendingQueryView = null;
      canvas?.setViewport(view.value);
      load();
      return;
    }
    resetView();
  }
);
</script>

<style scoped>
.tl-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.tl-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-card);
  color: var(--color-text);
  font: inherit;
  font-size: var(--font-size-sm);
  cursor: pointer;
}

.tl-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tl-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.tl-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* Said out loud: a timeline that quietly stops drawing looks like a JVM that quietly stopped. */
.tl-capped {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.15rem 0.45rem;
  border-radius: var(--radius-pill);
  background: var(--color-warning-light);
  color: var(--color-warning);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.tl-capped-zoom {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.tl-capped-zoom:focus-visible {
  outline: 2px solid var(--color-warning);
  outline-offset: 1px;
}

/* The refresh of an already-drawn window, as a cue rather than an overlay that hides the picture. */
.tl-updating {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.tl-updating i {
  animation: tl-rotate 0.9s linear infinite;
}

@keyframes tl-rotate {
  to {
    transform: rotate(360deg);
  }
}

.tl-hint {
  margin: 0 0 0.5rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.tl-hint b {
  color: var(--color-dark);
  font-weight: 600;
}

/* Positioning context for the state overlays, which sit on the canvas rather than replacing it. */
.tl-stage {
  position: relative;
}

.tl-host {
  width: 100%;
  height: 60vh;
  min-height: 24rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-card);
}

/* The canvas is a tab stop now; a focus a keyboard user cannot see is a focus they cannot use. */
.tl-host:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.tl-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-card) 82%, transparent);
}

/* The empty state advises "zoom out" — so the canvas underneath must stay operable through it. */
.tl-overlay-passthrough {
  pointer-events: none;
}

.tl-legend {
  display: flex;
  gap: 0.85rem;
  flex-wrap: wrap;
  padding-top: 0.6rem;
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--color-text-muted);
}

.tl-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.tl-legend i {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}
</style>

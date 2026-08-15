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
              <span v-if="timelineWindow?.truncated" class="tl-capped" :title="CAPPED_TITLE">
                <i class="bi bi-exclamation-triangle"></i> window capped
              </span>
              <button type="button" class="tl-btn" :disabled="isFullView" @click="resetView">
                <i class="bi bi-arrows-angle-expand"></i> Fit all
              </button>
            </div>
          </template>
        </MainCardHeader>
      </template>

      <LoadingState v-if="loading && !timelineWindow" message="Loading timeline..." />

      <ErrorState v-else-if="error" :message="error" @retry="load" />

      <EmptyState
        v-else-if="timelineWindow && timelineWindow.tracks.length === 0 && !loading"
        icon="bi-bar-chart-steps"
        title="Nothing ran in this window"
        description="No spans were recorded here. Zoom out, or pick a busier part of the recording."
      />

      <template v-else>
        <p class="tl-hint">
          <b>Scroll</b> to zoom at the cursor · <b>drag</b> to pan · <b>click a span</b> to open its
          trace
        </p>
        <div ref="hostRef" class="tl-host"></div>
        <div class="tl-legend">
          <span v-for="entry in LEGEND" :key="entry.label">
            <i :style="{ background: entry.color }"></i>{{ entry.label }}
          </span>
        </div>
      </template>
    </MainCard>

    <TraceSpansModal
      v-model:show="traceModalOpen"
      :profile-id="profileId"
      :trace-id="openTraceId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';

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
import { clampViewport } from '@/services/timeline/TimelineViewport';

import type { TimelineWindow } from '@/services/api/model/TimelineModels';
import type { Viewport } from '@/services/timeline/TimelineViewport';

/** The store speaks milliseconds; the timeline and the stored span bounds speak microseconds. */
const MICROS_PER_MILLI = 1_000;
const CAPPED_TITLE =
  'More spans ran in this window than the timeline draws. Zoom in for a complete picture.';
const LEGEND = TIMELINE_LEGEND;
/** Refetch no faster than this while panning, so a drag is one request rather than sixty. */
const REFETCH_DEBOUNCE_MS = 180;

const props = defineProps<{ profileId: string }>();

const hostRef = ref<HTMLDivElement | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const timelineWindow = shallowRef<TimelineWindow | null>(null);
const traceModalOpen = ref(false);
const openTraceId = ref('');

let canvas: TimelineCanvas | null = null;
let client: ProfileTracesClient;
let refetchTimer: number | undefined;
let inFlight = 0;

/**
 * The recording's own bounds, which the viewport can never leave. Absolute epoch micros, converted
 * once here from the store's millis so nothing downstream has to think about units.
 */
const bounds = computed<Viewport>(() => {
  const recording = profileStore.recordingWindow;
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
}

function resetView(): void {
  view.value = { ...bounds.value };
  canvas?.setViewport(view.value);
  load();
}

function openTrace(traceId: string): void {
  openTraceId.value = traceId;
  traceModalOpen.value = true;
}

onMounted(() => {
  client = new ProfileTracesClient(props.profileId);
  view.value = clampViewport(bounds.value, bounds.value);

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
  canvas?.destroy();
  canvas = null;
});

// A different profile is a different recording, so the viewport has to start over.
watch(
  () => props.profileId,
  profileId => {
    client = new ProfileTracesClient(profileId);
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

.tl-hint {
  margin: 0 0 0.5rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.tl-hint b {
  color: var(--color-dark);
  font-weight: 600;
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

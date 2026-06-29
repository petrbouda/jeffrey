<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <LoadingState v-if="loading" message="Loading recording overview..." />

  <ErrorState v-else-if="error" message="Failed to load recording overview" />

  <div v-else>
    <PageHeader
      title="Recording Overview"
      description="Pick a window on the timeline below — every view in this profile follows it."
      icon="bi-clock-history"
    >
      <template #actions>
        <!-- The active analysis window. Stays neutral for the whole recording and
             turns amber (with a reset) the moment a sub-range is selected, so it is
             always clear that the rest of the app is scoped to a slice. -->
        <div class="scope-chip" :class="{ windowed: isWindowed }">
          <span class="scope-label">Analyzing</span>
          <template v-if="isWindowed">
            <span class="scope-range">{{ windowFromLabel }} – {{ windowToLabel }}</span>
            <span class="scope-meta">{{ windowDurationLabel }} · {{ windowPercentLabel }}</span>
            <button class="scope-reset" type="button" @click="resetWindow">
              <i class="bi bi-arrow-counterclockwise"></i> Full recording
            </button>
          </template>
          <template v-else>
            <span class="scope-range">Whole recording</span>
            <span class="scope-meta">{{ recordingDurationLabel }}</span>
          </template>
        </div>
      </template>
    </PageHeader>

    <!-- Extra clarity strip: only shown while a sub-range is active. -->
    <div v-if="isWindowed" class="scope-banner">
      <i class="bi bi-exclamation-triangle"></i>
      <span>
        Showing a <b>{{ windowDurationLabel }}</b> slice (<b
          >{{ windowFromLabel }} → {{ windowToLabel }}</b
        >) of the <b>{{ recordingDurationLabel }}</b> recording. Open any view to analyze just this
        window.
      </span>
      <span class="scope-banner-pct">{{ windowPercentLabel }} of recording</span>
    </div>

    <EmptyState
      v-if="!hasEvents"
      icon="bi-clock-history"
      title="No time-based events in this recording"
      description="This profile has no sampled events to plot over time (for example a heap-dump-only profile). The recording window selector is unavailable."
    />

    <template v-else>
      <TabBar v-model="activeTab" :tabs="tabs" class="overview-tabs" />
      <ChartDescription
        :shows="`${activeLabel} over the whole recording (events per second)`"
        use-case="Pick an event type above; drag on the lower navigator to select a window that drives every other view."
      />
      <TimeSeriesChart
        :key="`${activeTab}-${chartResetKey}`"
        :primary-data="activityData"
        :primary-title="activeLabel"
        :primary-axis-type="AxisFormatType.NUMBER"
        :visible-minutes="overviewVisibleMinutes"
        :zoom-enabled="true"
        @update:time-range="onTimeRangeChange"
      />

      <!-- Jump into a view; the selected window rides along in the URL. -->
      <div class="jump-row">
        <span class="jump-row-title">Open a view scoped to this window</span>
        <div class="jump-tiles">
          <router-link
            v-for="tile in jumpTiles"
            :key="tile.name"
            class="jump-tile"
            :to="{ name: tile.name, params: { profileId }, query: windowQuery }"
          >
            <i class="bi" :class="tile.icon"></i>
            <span>{{ tile.label }}</span>
          </router-link>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router';

import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TabBar from '@shared/components/TabBar.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';
import TimeConverter from '@/services/timeseries/TimeConverter.ts';
import FormattingService from '@shared/services/FormattingService';

import EventTypes from '@/services/EventTypes';
import EventViewerClient from '@/services/api/EventViewerClient';
import ProfileTimeseriesClient from '@/services/api/ProfileTimeseriesClient';
import { useProfileTimeWindow } from '@/composables/useProfileTimeWindow';

// A selection covering at least this fraction of the recording is treated as
// "the whole recording" — so dragging to the edges clears the window cleanly.
const WHOLE_RECORDING_FRACTION = 0.99;
const MILLIS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;

// Upper bound on points the overview navigator requests. The backend aggregates
// the recording into at most this many buckets, so even a multi-hour recording
// stays light instead of returning one point per second.
const OVERVIEW_TARGET_BUCKETS = 1500;

// GC events are stackless; classify them locally since EventTypes has no GC helper.
const GC_EVENT_CODES = [
  'jdk.GarbageCollection',
  'jdk.YoungGarbageCollection',
  'jdk.OldGarbageCollection',
  'jdk.G1GarbageCollection',
  'jdk.ZYoungGarbageCollection',
  'jdk.ZOldGarbageCollection'
];

const route = useRoute();
const router = useRouter();

const {
  activeWindow,
  recordingDurationMillis,
  isWindowed,
  windowFraction,
  setWindow,
  clearWindow,
  initFromQuery,
  syncToRouter
} = useProfileTimeWindow();

const loading = ref(true);
const error = ref<string | null>(null);

const activityData = ref<number[][]>([]);
const presentTypes = ref<{ code: string; count: number }[]>([]);
const activeTab = ref('all');

// Bumping this key remounts the chart, resetting its visible range to the full
// recording when the user clears the window from the header.
const chartResetKey = ref(0);

const profileId = computed(() => route.params.profileId as string);

// Default the visible window (and thus the brush selection) to the whole interval by
// sizing it from the loaded data span — not from profileStore, which is still 0 when
// this child view mounts. The chart re-runs its range init when the data arrives.
const overviewVisibleMinutes = computed(() => {
  const data = activityData.value;
  if (data.length === 0) {
    return 1;
  }
  const maxSecond = data[data.length - 1][0];
  return Math.max(1, Math.ceil(maxSecond / SECONDS_PER_MINUTE) + 1);
});

// Formats a relative offset (millis from recording start) as HH:MM:SS, matching
// the chart's own axis labels.
const offsetFormatter = new TimeConverter('milliseconds');

const windowFromLabel = computed(() =>
  activeWindow.value ? offsetFormatter.formatTime(activeWindow.value.from) : ''
);
const windowToLabel = computed(() =>
  activeWindow.value ? offsetFormatter.formatTime(activeWindow.value.to) : ''
);
const windowDurationLabel = computed(() => {
  if (!activeWindow.value) {
    return '';
  }
  return FormattingService.formatDurationInMillis2Units(
    activeWindow.value.to - activeWindow.value.from
  );
});
const windowPercentLabel = computed(() => FormattingService.formatPercentage(windowFraction.value));
const recordingDurationLabel = computed(() =>
  FormattingService.formatDurationInMillis2Units(recordingDurationMillis.value)
);

const windowQuery = computed<LocationQueryRaw>(() => {
  if (!activeWindow.value) {
    return {};
  }
  return {
    from: String(Math.round(activeWindow.value.from)),
    to: String(Math.round(activeWindow.value.to))
  };
});

interface JumpTile {
  name: string;
  label: string;
  icon: string;
}

const jumpTiles: JumpTile[] = [
  { name: 'profile-flamegraphs-primary', label: 'Flamegraphs', icon: 'bi-fire' },
  { name: 'subsecond', label: 'Sub-second', icon: 'bi-grid-3x3' },
  { name: 'profile-garbage-collection', label: 'Garbage Collection', icon: 'bi-recycle' },
  { name: 'profile-threads-timeline', label: 'Threads', icon: 'bi-diagram-3' }
];

interface OverviewCategory {
  id: string;
  label: string;
  matches: (code: string) => boolean;
}

// Rendered after the always-present "All events" tab, in this order; a category only
// appears when the profile actually contains a matching event type.
const CATEGORIES: OverviewCategory[] = [
  {
    id: 'execution',
    label: 'Execution Samples',
    matches: code => EventTypes.isExecutionEventType(code) || EventTypes.isCpuTimeSample(code)
  },
  {
    id: 'allocation',
    label: 'Allocation',
    matches: code => EventTypes.isAllocationEventType(code)
  },
  { id: 'wallclock', label: 'Wall Clock', matches: code => EventTypes.isWallClock(code) },
  { id: 'locks', label: 'Locks / Monitor', matches: code => EventTypes.isBlockingEventType(code) },
  { id: 'gc', label: 'Garbage Collection', matches: code => GC_EVENT_CODES.includes(code) }
];

const ALL_EVENTS_TAB_ID = 'all';

const hasEvents = computed(() => presentTypes.value.length > 0);

// Most frequent present event type. Used as the (filter-ignored) event-type param for
// the all-events request, which still needs a concrete code for parameter binding.
const fallbackCode = computed(() => {
  if (presentTypes.value.length === 0) {
    return '';
  }
  return presentTypes.value.reduce((best, type) => (type.count > best.count ? type : best)).code;
});

// The representative (most frequent present) code for a category, or null if absent.
function dominantCode(matches: (code: string) => boolean): string | null {
  const candidates = presentTypes.value.filter(type => matches(type.code));
  if (candidates.length === 0) {
    return null;
  }
  return candidates.reduce((best, type) => (type.count > best.count ? type : best)).code;
}

const tabs = computed(() => {
  const items: { id: string; label: string }[] = [{ id: ALL_EVENTS_TAB_ID, label: 'All events' }];
  for (const category of CATEGORIES) {
    if (dominantCode(category.matches) !== null) {
      items.push({ id: category.id, label: category.label });
    }
  }
  return items;
});

const activeLabel = computed(() => tabs.value.find(tab => tab.id === activeTab.value)?.label ?? '');

// Resolves the request parameters (event code + all-events flag) for a tab.
function requestForTab(tabId: string): { code: string; allEventTypes: boolean } {
  if (tabId === ALL_EVENTS_TAB_ID) {
    return { code: fallbackCode.value, allEventTypes: true };
  }
  const category = CATEGORIES.find(item => item.id === tabId);
  return { code: category ? (dominantCode(category.matches) ?? '') : '', allEventTypes: false };
}

function onTimeRangeChange(payload: { start: number; end: number; isZoomed: boolean }): void {
  if (!payload.isZoomed) {
    clearWindow();
    syncToRouter(router);
    return;
  }
  const fromMs = Math.max(0, Math.floor(payload.start * MILLIS_PER_SECOND));
  const toMs = Math.ceil(payload.end * MILLIS_PER_SECOND);
  const duration = recordingDurationMillis.value;
  if (duration > 0 && toMs - fromMs >= duration * WHOLE_RECORDING_FRACTION) {
    clearWindow();
  } else {
    setWindow(fromMs, toMs);
  }
  syncToRouter(router);
}

function resetWindow(): void {
  clearWindow();
  syncToRouter(router);
  chartResetKey.value += 1;
}

async function loadSeries(tabId: string): Promise<void> {
  const { code, allEventTypes } = requestForTab(tabId);
  if (!code) {
    activityData.value = [];
    return;
  }
  // Whole recording, bounded server-side (≈ recording / targetBuckets per point).
  const timeseries = await new ProfileTimeseriesClient(profileId.value).getTimeseries(
    code,
    null,
    OVERVIEW_TARGET_BUCKETS,
    allEventTypes
  );
  activityData.value = timeseries.series?.[0]?.data ?? [];
}

async function load(): Promise<void> {
  try {
    loading.value = true;
    error.value = null;

    presentTypes.value = (await new EventViewerClient(profileId.value).eventTypes())
      .filter(type => type.count > 0)
      .map(type => ({ code: type.code, count: type.count }));

    if (presentTypes.value.length === 0) {
      activityData.value = [];
      return;
    }
    activeTab.value = ALL_EVENTS_TAB_ID;
    await loadSeries(ALL_EVENTS_TAB_ID);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading recording overview:', err);
  } finally {
    loading.value = false;
  }
}

// User switches event type → reload that series (the initial 'all' load runs in load()).
watch(activeTab, tabId => {
  loadSeries(tabId).catch(err => {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading recording overview series:', err);
  });
});

onMounted(() => {
  initFromQuery(route.query);
  load();
});
</script>

<style scoped>
/* ----- Header scope indicator ----- */
.scope-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 6px 12px;
  border-radius: var(--radius-pill, 999px);
  background: var(--color-neutral-light);
  border: 1px solid var(--color-border);
}

.scope-chip.windowed {
  background: var(--color-warning-bg);
  border-color: var(--color-warning-border);
}

.scope-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.scope-chip.windowed .scope-label {
  color: var(--color-warning-text);
}

.scope-range {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-dark);
  font-variant-numeric: tabular-nums;
}

.scope-meta {
  font-size: 11.5px;
  color: var(--color-text-muted);
}

.scope-chip.windowed .scope-meta {
  color: var(--color-warning-text);
}

.scope-reset {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--color-warning-border);
  background: var(--color-white);
  color: var(--color-warning-text);
  font-size: 11.5px;
  font-weight: 600;
  border-radius: var(--radius-pill, 999px);
  padding: 4px 10px;
  cursor: pointer;
}

.scope-reset:hover {
  background: var(--color-warning-light);
}

/* ----- Clarity banner ----- */
.scope-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 9px 14px;
  font-size: 12.5px;
  color: var(--color-text);
  background: var(--color-primary-lighter);
  border: 1px solid var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius-md, 8px);
}

.scope-banner i {
  color: var(--color-primary);
}

.scope-banner b {
  color: var(--color-dark);
  font-variant-numeric: tabular-nums;
}

.scope-banner-pct {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

/* ----- Event-type selector ----- */
.overview-tabs {
  margin-bottom: 12px;
}

/* ----- Jump tiles ----- */
.jump-row {
  margin-top: 18px;
}

.jump-row-title {
  display: block;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--color-dark);
  margin-bottom: 10px;
}

.jump-tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.jump-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  background: var(--color-white);
  color: var(--color-dark);
  text-decoration: none;
  font-size: 12.5px;
  font-weight: 600;
  transition: border-color 0.15s ease;
}

.jump-tile:hover {
  border-color: var(--color-primary);
}

.jump-tile i {
  color: var(--color-primary);
  font-size: 1rem;
}

@media (max-width: 768px) {
  .jump-tiles {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

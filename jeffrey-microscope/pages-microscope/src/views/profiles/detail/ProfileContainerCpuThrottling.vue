<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import Badge from '@shared/components/Badge.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SubSecondComponent from '@/components/SubSecondComponent.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import FormattingService from '@shared/services/FormattingService';
import FeatureType from '@/services/api/model/FeatureType';
import ContainerNotAvailableAlert from '@/components/alerts/ContainerNotAvailableAlert.vue';
import ProfileContainerClient from '@/services/api/ProfileContainerClient';
import ContainerCpuThrottlingData, {
  ThrottledWindow,
  ThrottlingSeverity
} from '@/services/api/model/ContainerCpuThrottlingData';
import type { Variant } from '@shared/types/ui';
import { useTableView } from '@/composables/useTableView';

// Period Detail (heatmap + flamegraph) — mirrors ProfileSubSecondView.vue.
import EventSummariesClient from '@/services/api/EventSummariesClient';
import EventTypes from '@/services/EventTypes';
import SubSecondDataProvider from '@/services/subsecond/SubSecondDataProvider';
import SubSecondDataProviderImpl from '@/services/subsecond/SubSecondDataProviderImpl';
import HeatmapTooltip from '@/services/subsecond/HeatmapTooltip';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import OnlyFlamegraphGraphUpdater from '@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater';
import TimeRange from '@/services/api/model/TimeRange';
import Utils from '@/services/Utils';
import MessageBus from '@/services/MessageBus';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const profileId = route.params.profileId as string;

const isContainerDashboardDisabled = computed(() =>
  props.disabledFeatures.includes(FeatureType.CONTAINER_DASHBOARD)
);

const loading = ref(true);
const error = ref(false);
const data = ref<ContainerCpuThrottlingData | null>(null);

const activeTab = ref('overview');
const tabs = computed<TabBarItem[]>(() => [
  { id: 'overview', label: 'Overview', icon: 'speedometer2' },
  { id: 'period', label: 'Period Detail', icon: 'stopwatch' },
  { id: 'subsecond', label: 'Timeline', icon: 'activity' },
  { id: 'howit', label: 'How It Works', icon: 'book' }
]);

let containerClient: ProfileContainerClient;

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;
    if (!containerClient) {
      containerClient = new ProfileContainerClient(profileId);
    }
    data.value = await containerClient.getCpuThrottling();
    pinnedKey.value = null;
    selectedKey.value = null;
  } catch (err) {
    console.error('Error loading container CPU throttling:', err);
    error.value = true;
  } finally {
    loading.value = false;
  }
};

// ---- verdict presentation ----
const SEVERITY_VARIANT: Record<ThrottlingSeverity, Variant> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'warning',
  NONE: 'success',
  NOT_APPLICABLE: 'secondary'
};
const SEVERITY_ICON: Record<ThrottlingSeverity, string> = {
  HIGH: 'bi-exclamation-triangle-fill',
  MEDIUM: 'bi-thermometer-half',
  LOW: 'bi-thermometer-half',
  NONE: 'bi-check-circle-fill',
  NOT_APPLICABLE: 'bi-dash-circle'
};

const verdict = computed(() => data.value?.verdict ?? null);
const verdictVariant = computed<Variant>(() =>
  verdict.value ? SEVERITY_VARIANT[verdict.value.severity] : 'secondary'
);
const verdictIcon = computed(() => (verdict.value ? SEVERITY_ICON[verdict.value.severity] : 'bi-dash-circle'));

// ---- formatting helpers ----
const formatMillis = (ms: number): string => {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(2)} s`;
  }
  return `${Math.round(ms)} ms`;
};
const formatPct = (pct: number): string => `${pct.toFixed(1)}%`;
// CPU limit shown as the CFS quota in ms (quota = cores × period), consistent with the CFS period.
const formatQuotaMillis = (cores: number | null, cfsPeriodMillis: number | null): string =>
  cores == null || cfsPeriodMillis == null ? 'unlimited' : `${Math.round(cores * cfsPeriodMillis)} ms`;

// ---- KPI strip ----
const kpiMetrics = computed(() => {
  const s = data.value?.summary;
  if (!s) {
    return [];
  }
  return [
    {
      icon: 'thermometer-half',
      title: 'Throttle ratio',
      value: formatPct(s.overallRatioPct),
      variant: 'danger' as const,
      breakdown: [{ label: 'Peak', value: formatPct(s.peakRatioPct) }]
    },
    {
      icon: 'clock-history',
      title: 'Time throttled',
      value: formatMillis(s.throttledTimeMillis),
      variant: 'warning' as const,
      breakdown: [{ label: 'Across the recording window', value: '' }]
    },
    {
      icon: 'diagram-3',
      title: 'Throttled periods',
      value: FormattingService.formatNumber(s.throttledPeriods),
      variant: 'info' as const,
      breakdown: [{ label: 'of elapsed', value: FormattingService.formatNumber(s.elapsedPeriods) }]
    },
    {
      icon: 'cpu',
      title: 'CPU limit',
      value: formatQuotaMillis(s.cpuLimitCores, s.cfsPeriodMillis),
      variant: 'success' as const,
      breakdown: [
        { label: 'CFS period', value: s.cfsPeriodMillis != null ? `${s.cfsPeriodMillis} ms` : '—' },
        { label: 'Host CPUs', value: s.effectiveCpuCount != null ? `${s.effectiveCpuCount}` : '—' }
      ]
    }
  ];
});

// ---- throttle-ratio timeseries ----
// x = seconds from profiling start, y = throttle ratio %
const ratioSeries = computed<number[][]>(() =>
  (data.value?.timeseries ?? []).map(p => [p.timestampMillis / 1000, Number(p.ratioPct.toFixed(2))])
);
const hasTimeseries = computed(() => ratioSeries.value.some(point => point[1] > 0));

// ---- most-throttled windows table ----
const windows = computed<ThrottledWindow[]>(() => data.value?.windows ?? []);

// Brushing a range on the Overview timeseries filters the windows table to windows overlapping it.
// start/end are seconds from profiling start (the chart uses time-unit="seconds"), matching ratioSeries.
const brushRange = ref<{ start: number; end: number } | null>(null);
const filteredWindows = computed<ThrottledWindow[]>(() => {
  const range = brushRange.value;
  if (!range) {
    return windows.value;
  }
  return windows.value.filter(
    w => w.endMillis / 1000 >= range.start && w.startMillis / 1000 <= range.end
  );
});
const onTimeRangeChange = (payload: { start: number; end: number; isZoomed: boolean }) => {
  brushRange.value = payload.isZoomed ? { start: payload.start, end: payload.end } : null;
};
const clearBrush = () => {
  brushRange.value = null;
};

const maxWindowRatio = computed(() => filteredWindows.value.reduce((max, w) => Math.max(max, w.ratioPct), 0));

const windowsView = useTableView(() => filteredWindows.value, {
  searchableText: w => formatWindowRange(w)
});

function formatWindowRange(w: ThrottledWindow): string {
  return `${(w.startMillis / 1000).toFixed(0)} – ${(w.endMillis / 1000).toFixed(0)} s`;
}

const shareBarWidth = (ratioPct: number): number => {
  if (maxWindowRatio.value === 0) {
    return 0;
  }
  return Math.max(2, Math.round((ratioPct / maxWindowRatio.value) * 100));
};

// ---- Period Detail ----
const BUCKET_OPTIONS = [5, 2, 1];
const TOP_PANEL_COUNT = 3;
const bucketSizeMs = ref(5);
const cpuEventType = ref<string | null>(null);

// The three worst windows are shown as panels — ranked by throttled periods (default) or by time.
type RankMode = 'periods' | 'time';
const rankMode = ref<RankMode>('periods');
const rankedWindows = computed<ThrottledWindow[]>(() =>
  [...windows.value].sort((a, b) =>
    rankMode.value === 'periods'
      ? b.throttledPeriods - a.throttledPeriods
      : b.throttledTimeMillis - a.throttledTimeMillis
  )
);
const topPanels = computed<ThrottledWindow[]>(() => rankedWindows.value.slice(0, TOP_PANEL_COUNT));

// Picking an interval on the Overview tab pins Period Detail to that single window (one panel);
// otherwise the top-3 panels are shown.
const pinnedKey = ref<number | null>(null);
const displayedPanels = computed<ThrottledWindow[]>(() => {
  if (pinnedKey.value !== null) {
    const pinned = windows.value.find(w => w.startMillis === pinnedKey.value);
    if (pinned) {
      return [pinned];
    }
  }
  return topPanels.value;
});

// The selected window drives the heatmap: a clicked panel, an Overview row-click, or (fallback) the
// worst of the current ranking.
const selectedKey = ref<number | null>(null);
const selectedWindow = computed<ThrottledWindow | null>(() => {
  const found = windows.value.find(w => w.startMillis === selectedKey.value);
  return found ?? displayedPanels.value[0] ?? null;
});

const severityOf = (ratioPct: number): { variant: Variant; label: string } => {
  if (ratioPct > 25) {
    return { variant: 'danger', label: 'HIGH' };
  }
  if (ratioPct >= 5) {
    return { variant: 'warning', label: 'MEDIUM' };
  }
  return { variant: 'warning', label: 'LOW' };
};

const isPanelSelected = (w: ThrottledWindow): boolean =>
  selectedWindow.value?.startMillis === w.startMillis;

const selectPanel = (w: ThrottledWindow) => {
  selectedKey.value = w.startMillis;
};

// Return from a single Overview-pinned interval to the top-3 panels.
const clearPin = () => {
  pinnedKey.value = null;
  selectedKey.value = null;
};

// Switching the rank metric re-selects the worst of the new ranking.
watch(rankMode, () => {
  selectedKey.value = null;
});

const periodReady = computed(() => cpuEventType.value !== null && selectedWindow.value !== null);
const periodDataProvider = computed<SubSecondDataProvider | null>(() =>
  cpuEventType.value ? new SubSecondDataProviderImpl(profileId, cpuEventType.value, false, bucketSizeMs.value) : null
);
const periodTimeRange = computed<TimeRange | null>(() =>
  selectedWindow.value
    ? new TimeRange(selectedWindow.value.startMillis, selectedWindow.value.endMillis, false)
    : null
);
// Remount the heatmap whenever the period, bucket size, or event type changes.
const periodHeatmapKey = computed(
  () => `${selectedWindow.value?.startMillis}-${bucketSizeMs.value}-${cpuEventType.value}`
);

const selectWindow = (w: ThrottledWindow) => {
  pinnedKey.value = w.startMillis;
  selectedKey.value = w.startMillis;
  activeTab.value = 'period';
};

// ---- flamegraph drill-down (mirrors ProfileSubSecondView.vue) ----
const showDialog = ref(false);
const subSecondRef = ref<InstanceType<typeof SubSecondComponent> | null>(null);
let graphUpdater: GraphUpdater;
let flamegraphTooltip: FlamegraphTooltip;
let flamegraphClient: PrimaryFlamegraphClient;

// ---- Sub-Second tab: the full SubSecond explorer (timeseries brush → heatmap → flamegraph),
// with selectable bucket sizes. Same machinery as ProfileSubSecondView.vue. ----
const SS_BUCKET_OPTIONS = [20, 5, 2, 1];
const ssBucketSizeMs = ref(5);
const ssSubSecondRef = ref<InstanceType<typeof SubSecondComponent> | null>(null);

// The brush timeseries plots the CPU-throttle signal (throttled periods or throttled time),
// switchable — the heatmap below is CPU samples, zoomed to the brushed range.
type SsMetric = 'periods' | 'time';
const ssMetric = ref<SsMetric>('periods');
const ssTimeseriesData = computed<number[][]>(() =>
  (data.value?.timeseries ?? []).map(p => [
    p.timestampMillis / 1000,
    ssMetric.value === 'periods' ? p.throttledPeriodsDelta : Number(p.throttledTimeMillisDelta.toFixed(1))
  ])
);
const ssHasTimeseries = computed(() => ssTimeseriesData.value.length > 0);
const ssTitle = computed(() => (ssMetric.value === 'periods' ? 'Throttled periods' : 'Throttled time'));
const ssAxisType = computed(() =>
  ssMetric.value === 'periods' ? AxisFormatType.NUMBER : AxisFormatType.DURATION_IN_MILLIS
);
const ssDataProvider = computed<SubSecondDataProvider | null>(() =>
  cpuEventType.value ? new SubSecondDataProviderImpl(profileId, cpuEventType.value, false, ssBucketSizeMs.value) : null
);
// Remount the heatmap when the bucket size (or event type) changes.
const ssHeatmapKey = computed(() => `${ssBucketSizeMs.value}-${cpuEventType.value}`);

const onSsTimeRangeChange = (payload: { start: number; end: number; isZoomed: boolean }) => {
  if (payload.isZoomed) {
    const range = new TimeRange(Math.floor(payload.start * 1000), Math.ceil(payload.end * 1000), false);
    ssSubSecondRef.value?.reloadWithTimeRange(range);
  } else {
    ssSubSecondRef.value?.reloadWithTimeRange();
  }
};

const resolveCpuEvent = async () => {
  try {
    const summaries = await EventSummariesClient.primary(profileId).events();
    const withSamples = summaries.filter(s => s.primary && s.primary.samples > 0);
    const pick =
      withSamples.find(s => EventTypes.isExecutionEventType(s.code)) ??
      withSamples.find(s => EventTypes.isCpuTimeSample(s.code)) ??
      withSamples.find(s => EventTypes.isWallClock(s.code));
    if (!pick) {
      return;
    }
    cpuEventType.value = pick.code;
    flamegraphClient = new PrimaryFlamegraphClient(
      profileId,
      pick.code,
      false,
      false,
      false,
      false,
      false,
      null
    );
    graphUpdater = new OnlyFlamegraphGraphUpdater(flamegraphClient, false);
    flamegraphTooltip = FlamegraphTooltipFactory.create(pick.code, false, false);
  } catch (err) {
    console.error('Error resolving CPU event type:', err);
  }
};

function createOnSelectedCallback() {
  return function (startTime: number[], endTime: number[]) {
    const selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    showFlamegraph(selectedTimeRange);
  };
}

function showFlamegraph(timeRange: TimeRange) {
  showDialog.value = true;
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
  setTimeout(() => {
    graphUpdater.updateWithZoom(timeRange);
  }, 200);
}

function scrollToTop() {
  const wrapper = document.querySelector('.flamegraphModal');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

// Give a hidden heatmap container a size when its tab first opens.
watch(activeTab, id => {
  if (id === 'period' || id === 'subsecond') {
    nextTick(() => window.dispatchEvent(new Event('resize')));
  }
});

onMounted(() => {
  loadData();
  resolveCpuEvent();
});
</script>

<template>
  <div class="profile-page">
    <ContainerNotAvailableAlert v-if="isContainerDashboardDisabled" />

    <template v-else>
      <PageHeader
        title="CPU Throttling Detector"
        description="Was this container starved by the CFS scheduler — how often its threads were parked after using up the CPU quota, and when."
        icon="bi-thermometer-half"
      />

      <LoadingState v-if="loading" message="Loading CPU throttling data..." />
      <ErrorState v-else-if="error" message="Failed to load CPU throttling data" />
      <template v-else-if="data">
        <!-- Verdict banner (persistent across tabs) -->
        <div v-if="verdict" class="verdict-card" :class="`verdict-${verdictVariant}`">
          <div class="verdict-stripe"></div>
          <div class="verdict-icon"><i class="bi" :class="verdictIcon"></i></div>
          <div class="verdict-text">
            <h2>{{ verdict.title }}</h2>
            <p>{{ verdict.description }}</p>
          </div>
          <Badge :value="verdict.severity.replace('_', ' ')" :variant="verdictVariant" size="m" />
        </div>

        <!-- KPIs (persistent across tabs) -->
        <div class="mb-4">
          <StatsTable :metrics="kpiMetrics" />
        </div>

        <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

        <!-- ============ OVERVIEW ============ -->
        <div v-show="activeTab === 'overview'">
          <ChartDescription
            shows="Throttle ratio per 30 s sample from jdk.ContainerCPUThrottling."
            use-case="Confirm when the container hit its CPU quota; brush the chart to zoom into a range."
          />
          <EmptyState
            v-if="!hasTimeseries"
            icon="bi-check-circle"
            title="No throttling recorded"
            message="No CFS period was throttled during this recording."
          />
          <div v-else class="chart-container mb-4">
            <TimeSeriesChart
              :primary-data="ratioSeries"
              primary-title="Throttle ratio (%)"
              :primary-axis-type="AxisFormatType.NUMBER"
              :visible-minutes="10"
              :zoom-enabled="true"
              time-unit="seconds"
              @update:timeRange="onTimeRangeChange"
            />
          </div>

          <ChartDescription
            shows="The 30 s windows with the highest throttle ratio."
            use-case="Click a window to inspect its sub-second detail in the Period Detail tab."
          />
          <EmptyState
            v-if="filteredWindows.length === 0"
            icon="bi-check-circle"
            :title="windows.length === 0 ? 'No throttled windows' : 'No windows in the selected range'"
            :message="
              windows.length === 0
                ? 'The container never hit its CPU quota during this recording.'
                : 'Brush a wider range on the chart, or clear the selection.'
            "
          />
          <DataTable v-else>
            <template #toolbar>
              <TableToolbar v-model="windowsView.query" search-placeholder="Filter windows...">
                <span class="toolbar-info">Windows</span>
                <template #filters>
                  <Badge key-label="Total" :value="windowsView.matchCount" variant="secondary" size="s" borderless />
                  <button v-if="brushRange" type="button" class="brush-clear" @click="clearBrush">
                    <i class="bi bi-funnel-fill"></i>
                    {{ brushRange.start.toFixed(0) }}–{{ brushRange.end.toFixed(0) }} s
                    <i class="bi bi-x-lg"></i>
                  </button>
                </template>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th>Window</th>
                <th class="text-end">Throttle ratio</th>
                <th class="text-end">Throttled time</th>
                <th class="text-end">Periods throttled</th>
                <th class="share-col">Share</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="window in windowsView.visible"
                :key="window.startMillis"
                class="window-row"
                @click="selectWindow(window)"
              >
                <td class="window-range">{{ formatWindowRange(window) }}</td>
                <td class="text-end"><strong>{{ formatPct(window.ratioPct) }}</strong></td>
                <td class="text-end">{{ formatMillis(window.throttledTimeMillis) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(window.throttledPeriods) }}</td>
                <td>
                  <div class="share-bar">
                    <div class="share-bar-fill" :style="{ width: shareBarWidth(window.ratioPct) + '%' }"></div>
                  </div>
                </td>
                <td class="text-end"><span class="inspect-link">Inspect ›</span></td>
              </tr>
            </tbody>
            <template #footer>
              <TableShowMore
                :shown="windowsView.visible.length"
                :match-count="windowsView.matchCount"
                :total="windowsView.total"
                :expanded="windowsView.expanded"
                :page-size="windowsView.pageSize"
                @toggle="windowsView.toggle"
              />
            </template>
          </DataTable>
        </div>

        <!-- ============ PERIOD DETAIL ============ -->
        <div v-show="activeTab === 'period'">
          <ChartDescription
            shows="CPU-sample activity across the selected period — seconds on the x-axis, sub-second buckets on the y-axis; quiet gaps are throttle stalls. Select a region to open a flamegraph of the code that ran."
          />

          <EmptyState
            v-if="windows.length === 0"
            icon="bi-check-circle"
            title="No throttled windows to inspect"
            message="The container never hit its CPU quota during this recording."
          />
          <EmptyState
            v-else-if="!cpuEventType"
            icon="bi-cpu"
            title="No CPU samples in this recording"
            message="The sub-second heatmap needs jdk.ExecutionSample / jdk.CPUTimeSample / profiler.WallClockSample events."
          />
          <template v-else>
            <!-- Rank toggle (top-3), or a back-bar when pinned to an Overview-selected interval -->
            <div v-if="pinnedKey === null" class="rank-toggle">
              <span class="rank-label">Top 3 by</span>
              <div class="btn-group btn-group-sm" role="group" aria-label="Rank metric">
                <button
                  type="button"
                  class="btn"
                  :class="rankMode === 'periods' ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="rankMode = 'periods'"
                >
                  Periods
                </button>
                <button
                  type="button"
                  class="btn"
                  :class="rankMode === 'time' ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="rankMode = 'time'"
                >
                  Time
                </button>
              </div>
            </div>

            <!-- Panels: the pinned interval (+ a "show top 3" panel), or the top-3 windows -->
            <div class="period-panels" :class="{ pinned: pinnedKey !== null }">
              <button
                v-for="w in displayedPanels"
                :key="w.startMillis"
                type="button"
                class="period-panel"
                :class="[`sev-${severityOf(w.ratioPct).variant}`, { selected: isPanelSelected(w) }]"
                @click="selectPanel(w)"
              >
                <div class="ratio-ring" :style="{ '--pct': w.ratioPct }">
                  <span>{{ formatPct(w.ratioPct) }}</span>
                </div>
                <div class="panel-main">
                  <div class="panel-range">{{ formatWindowRange(w) }}</div>
                  <div class="panel-stats">
                    <div class="ps">
                      <span class="ps-k">Time</span>
                      <span class="ps-v">{{ formatMillis(w.throttledTimeMillis) }}</span>
                    </div>
                    <div class="ps">
                      <span class="ps-k">Periods</span>
                      <span class="ps-v">{{ FormattingService.formatNumber(w.throttledPeriods) }}</span>
                    </div>
                  </div>
                </div>
              </button>

              <!-- "Show top 3" CTA panel, shown beside the Overview-pinned interval -->
              <button v-if="pinnedKey !== null" type="button" class="show-top3" @click="clearPin">
                <span class="st3-icon"><i class="bi bi-arrow-return-left"></i></span>
                <span class="st3-text">
                  <span class="st3-title">Show top 3</span>
                  <span class="st3-sub">most-throttled windows</span>
                </span>
              </button>
            </div>

            <!-- Heatmap with the bucket control on its right -->
            <div class="heatmap-row">
              <div class="heatmap-col">
                <SubSecondComponent
                  v-if="periodReady && periodDataProvider && activeTab === 'period'"
                  :key="periodHeatmapKey"
                  ref="subSecondRef"
                  :primary-data-provider="periodDataProvider"
                  :primary-selected-callback="createOnSelectedCallback()"
                  :secondary-data-provider="null"
                  :secondary-selected-callback="null"
                  :tooltip="new HeatmapTooltip(cpuEventType!, false)"
                  :event-type="cpuEventType!"
                  :use-weight="false"
                  :bucket-size-ms="bucketSizeMs"
                  :initial-time-range="periodTimeRange ?? undefined"
                />
              </div>
              <div class="bucket-side">
                <span class="bucket-label">Bucket</span>
                <button
                  v-for="option in BUCKET_OPTIONS"
                  :key="option"
                  type="button"
                  class="btn btn-sm bucket-btn"
                  :class="bucketSizeMs === option ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="bucketSizeMs = option"
                >
                  {{ option }} ms
                </button>
              </div>
            </div>
          </template>
        </div>

        <!-- ============ SUB-SECOND ============ -->
        <div v-show="activeTab === 'subsecond'">
          <ChartDescription
            shows="Select a time interval on the throttling timeseries (by throttled periods or time) to see the CPU profile for that range in the sub-second heatmap. Change the bucket size for finer detail, and select a region to open a flamegraph."
          />
          <EmptyState
            v-if="!cpuEventType"
            icon="bi-cpu"
            title="No CPU samples in this recording"
            message="The sub-second heatmap needs jdk.ExecutionSample / jdk.CPUTimeSample / profiler.WallClockSample events."
          />
          <template v-else>
            <!-- Timeseries metric: throttled periods (default) or throttled time -->
            <div class="ss-control ss-compact">
              <div class="btn-group btn-group-sm" role="group" aria-label="Throttle metric">
                <button
                  type="button"
                  class="btn"
                  :class="ssMetric === 'periods' ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="ssMetric = 'periods'"
                >
                  Throttled periods
                </button>
                <button
                  type="button"
                  class="btn"
                  :class="ssMetric === 'time' ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="ssMetric = 'time'"
                >
                  Throttled time
                </button>
              </div>
            </div>

            <div class="ss-timeseries">
              <TimeSeriesChart
                v-if="ssHasTimeseries"
                :key="ssMetric"
                :primary-data="ssTimeseriesData"
                :primary-title="ssTitle"
                :primary-axis-type="ssAxisType"
                :visible-minutes="5"
                :zoom-enabled="true"
                :fixed-window-minutes="5"
                time-unit="seconds"
                @update:timeRange="onSsTimeRangeChange"
              />
            </div>

            <!-- Heatmap bucket size (compact, right-aligned) -->
            <div class="ss-control ss-compact">
              <div class="btn-group btn-group-sm" role="group" aria-label="Bucket size">
                <button
                  v-for="option in SS_BUCKET_OPTIONS"
                  :key="option"
                  type="button"
                  class="btn"
                  :class="ssBucketSizeMs === option ? 'btn-primary' : 'btn-outline-secondary'"
                  @click="ssBucketSizeMs = option"
                >
                  {{ option }} ms
                </button>
              </div>
            </div>

            <SubSecondComponent
              v-if="activeTab === 'subsecond' && ssDataProvider"
              :key="ssHeatmapKey"
              ref="ssSubSecondRef"
              :primary-data-provider="ssDataProvider"
              :primary-selected-callback="createOnSelectedCallback()"
              :secondary-data-provider="null"
              :secondary-selected-callback="null"
              :tooltip="new HeatmapTooltip(cpuEventType!, false)"
              :event-type="cpuEventType!"
              :use-weight="false"
              :bucket-size-ms="ssBucketSizeMs"
            />
          </template>
        </div>

        <!-- ============ HOW IT WORKS ============ -->
        <div v-show="activeTab === 'howit'">
          <AboutPanel
            icon="bi-question-circle"
            title="Understanding CPU Throttling"
            subtitle="What the CFS scheduler does when a container runs out of CPU quota"
          >
            <AboutCallout variant="intro">
              <p>
                A container with a CPU limit gets a slice of CPU time — a <strong>quota</strong> —
                every scheduling <strong>period</strong> (the Linux CFS period, 100&nbsp;ms by default).
                Burn through the quota before the period ends and the kernel <strong>parks every one of
                your threads</strong> until the next period refills it. That pause is throttling.
              </p>
            </AboutCallout>

            <AboutSection icon="bi-cpu" title="Quota, period, and the saw-tooth">
              <p>
                With a <code>0.5</code>-core limit you get 50&nbsp;ms of CPU per 100&nbsp;ms period. A
                burst that wants a full core runs flat-out for ~50&nbsp;ms, then sits idle for the
                remaining ~50&nbsp;ms — a repeating <em>saw-tooth</em>. On the Period Detail heatmap that
                shows up as activity at the start of each 100&nbsp;ms band and a quiet tail after it.
              </p>
              <FeatureGrid>
                <FeatureCard icon="bi-thermometer-half" variant="danger" title="Throttling masquerades as latency">
                  Unexplained periodic latency in a container with a low CPU quota is often throttling,
                  not your code — it looks like GC or lock contention but isn't.
                </FeatureCard>
                <FeatureCard icon="bi-graph-up-arrow" variant="warning" title="Warm-up vs steady-state">
                  A burst of throttling during start-up (JIT warm-up) is benign; sustained throttling
                  long after warm-up means the limit is too low for the workload.
                </FeatureCard>
              </FeatureGrid>
            </AboutSection>

            <AboutSection icon="bi-search" title="Reading the Period Detail heatmap">
              <ul>
                <li><strong>X-axis</strong> — seconds across the selected 30&nbsp;s period.</li>
                <li><strong>Y-axis</strong> — sub-second position, bucketed (1–5&nbsp;ms); darker = more CPU samples.</li>
                <li>
                  <strong>Repeating quiet bands</strong> aligned to the 100&nbsp;ms CFS period are stalls —
                  the quota was spent and threads were parked.
                </li>
                <li>
                  <strong>Select a region</strong> → a flamegraph of the CPU samples in that slice shows
                  exactly which methods burned the quota.
                </li>
              </ul>
            </AboutSection>

            <AboutSection icon="bi-broadcast" title="How JFR emits this">
              <p>
                The verdict and the 30&nbsp;s trend come from <code>jdk.ContainerCPUThrottling</code>,
                emitted every 30&nbsp;s by default with cumulative kernel counters:
              </p>
              <ul>
                <li><code>cpuElapsedSlices</code> — CFS periods elapsed (cgroup <code>nr_periods</code>).</li>
                <li><code>cpuThrottledSlices</code> — periods throttled (cgroup <code>nr_throttled</code>).</li>
                <li><code>cpuThrottledTime</code> — total nanoseconds parked (cgroup <code>throttled_time</code>).</li>
              </ul>
              <p>
                Because the counters are cumulative since the container started, Jeffrey deltas
                consecutive samples to report throttling <em>within</em> the recording window. The
                heatmap instead uses CPU execution samples, so it reveals the intra-period saw-tooth the
                30&nbsp;s counters can't.
              </p>
            </AboutSection>
          </AboutPanel>
        </div>
      </template>

      <!-- Flamegraph drill-down modal (mirrors ProfileSubSecondView.vue) -->
      <GenericModal
        modal-id="flamegraphModal"
        :show="showDialog"
        size="fullscreen"
        :show-footer="false"
        @update:show="showDialog = $event"
      >
        <template #header>
          <button type="button" class="btn-close" @click="showDialog = false" aria-label="Close"></button>
        </template>
        <SearchBarComponent v-if="showDialog" :graph-updater="graphUpdater" :with-timeseries="false" />
        <FlamegraphComponent
          v-if="showDialog"
          :with-timeseries="false"
          :use-weight="false"
          :use-guardian="null"
          scrollable-wrapper-class="flamegraphModal"
          :flamegraph-tooltip="flamegraphTooltip"
          :graph-updater="graphUpdater"
          @loaded="scrollToTop"
        />
      </GenericModal>
    </template>
  </div>
</template>

<style scoped>
.verdict-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  position: relative;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 1.1rem 1.25rem 1.1rem 1.5rem;
  margin-bottom: 1.25rem;
  overflow: hidden;
}
.verdict-stripe {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 7px;
}
.verdict-icon {
  font-size: 1.6rem;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  flex: none;
}
.verdict-text {
  flex: 1;
}
.verdict-text h2 {
  font-size: 1.15rem;
  margin: 0 0 0.15rem;
}
.verdict-text p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
}
.verdict-danger .verdict-stripe {
  background: var(--color-danger);
}
.verdict-danger .verdict-icon {
  background: var(--color-danger-light);
  color: var(--color-danger);
}
.verdict-warning .verdict-stripe {
  background: var(--color-warning);
}
.verdict-warning .verdict-icon {
  background: var(--color-warning-light);
  color: var(--color-warning);
}
.verdict-success .verdict-stripe {
  background: var(--color-success);
}
.verdict-success .verdict-icon {
  background: var(--color-success-light);
  color: var(--color-success);
}
.verdict-secondary .verdict-stripe {
  background: var(--color-secondary);
}
.verdict-secondary .verdict-icon {
  background: var(--color-neutral-light);
  color: var(--color-secondary);
}


.chart-container {
  width: 100%;
}
.ss-timeseries {
  padding: 0 5px;
  margin-bottom: 0.75rem;
}
.ss-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}
.ss-compact {
  justify-content: flex-end;
}
.ss-compact .btn {
  font-size: 0.72rem;
  padding: 0.18rem 0.55rem;
  line-height: 1.35;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.brush-clear {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: 20px;
  padding: 2px 10px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.brush-clear:hover {
  background: var(--color-primary);
  color: #fff;
}

.window-row {
  cursor: pointer;
}
.window-range {
  font-variant-numeric: tabular-nums;
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
}
.inspect-link {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.share-col {
  width: 220px;
}
.share-bar {
  width: 100%;
  height: 8px;
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.share-bar-fill {
  height: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-danger);
}

/* Rank toggle (compact, right-aligned) */
.rank-toggle {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
}
.rank-label {
  font-size: 0.62rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-bold);
}
.rank-toggle .btn {
  font-size: 0.72rem;
  padding: 0.18rem 0.6rem;
  line-height: 1.35;
}

/* Three top-window panels */
.period-panels {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.25rem;
}
@media (max-width: 900px) {
  .period-panels {
    grid-template-columns: 1fr;
  }
}
.period-panels.pinned {
  grid-template-columns: minmax(280px, 420px) minmax(200px, 300px);
}
.show-top3 {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  text-align: left;
  padding: 0.9rem 1.1rem;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-lg);
  background: var(--color-primary-light);
  cursor: pointer;
  transition:
    transform 0.12s,
    box-shadow 0.12s;
}
.show-top3:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}
.st3-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.25rem;
  flex: none;
}
.st3-text {
  display: flex;
  flex-direction: column;
}
.st3-title {
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}
.st3-sub {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-top: 1px;
}
.sev-danger {
  --sev: var(--color-danger);
}
.sev-warning {
  --sev: var(--color-warning);
}
.sev-secondary {
  --sev: var(--color-secondary);
}
.period-panel {
  display: flex;
  align-items: center;
  gap: 1rem;
  text-align: left;
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    border-color 0.12s,
    box-shadow 0.12s;
}
.period-panel:hover {
  border-color: var(--sev, var(--color-primary));
}
.period-panel.selected {
  border-color: var(--sev, var(--color-primary));
  box-shadow: inset 0 0 0 2px var(--sev, var(--color-primary));
}
.ratio-ring {
  --pct: 0;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: conic-gradient(var(--sev, var(--color-primary)) calc(var(--pct) * 1%), var(--color-lighter) 0);
}
.ratio-ring > span {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-bg-card);
  display: grid;
  place-items: center;
  font-weight: var(--font-weight-bold);
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
}
.panel-main {
  min-width: 0;
}
.panel-range {
  font-size: 1.05rem;
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
}
.panel-stats {
  display: flex;
  gap: 1.1rem;
  margin-top: 0.4rem;
}
.ps {
  display: flex;
  flex-direction: column;
}
.ps-k {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-bold);
}
.ps-v {
  font-weight: var(--font-weight-semibold);
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}

/* Heatmap + bucket control on the right */
.heatmap-row {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}
.heatmap-col {
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
}
.bucket-side {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  position: sticky;
  top: 0.5rem;
}
.bucket-label {
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-bold);
}
.bucket-btn {
  min-width: 64px;
}
</style>

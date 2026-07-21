<template>
  <div class="summary-dashboard">
    <PageHeader
      title="Summary"
      description="One-screen overview of the recording — health verdicts, key metrics, and where to look first"
      icon="bi-grid-1x2"
    >
      <!-- Key metrics -->
      <div class="kpi-section">
        <LoadingState v-if="kpiLoading" message="Loading metrics..." />
        <StatsTable v-else :metrics="kpiMetrics" clickable-rows @metric-click="onKpiClick" />
      </div>

      <div class="identity-chips">
        <Badge
          v-if="recordingDuration"
          key-label="Duration"
          :value="recordingDuration"
          variant="light"
        />
        <Badge
          key-label="Created"
          :value="FormattingService.formatDate(profile.createdAt)"
          variant="light"
        />
        <Badge v-if="jvmVersion" key-label="JVM" :value="jvmVersion" variant="light" />
        <Badge v-if="gcName" key-label="GC" :value="gcName" variant="light" />
        <Badge v-if="heapMax" key-label="Heap Max" :value="heapMax" variant="light" />
        <Badge v-if="cpuDescription" key-label="CPU" :value="cpuDescription" variant="light" />
        <Badge v-if="containerType" key-label="Container" :value="containerType" variant="light" />
      </div>

      <!-- Health at a glance -->
      <div class="verdict-grid">
        <div class="verdict-card" @click="navigateTo('/guardian')">
          <span class="verdict-dot" :class="guardianVerdict.tone"></span>
          <div>
            <div class="verdict-title">Guardian</div>
            <div class="verdict-value">{{ guardianVerdict.text }}</div>
          </div>
        </div>
        <div class="verdict-card" @click="navigateTo('/auto-analysis')">
          <span class="verdict-dot" :class="autoAnalysisVerdict.tone"></span>
          <div>
            <div class="verdict-title">Auto Analysis</div>
            <div class="verdict-value">{{ autoAnalysisVerdict.text }}</div>
          </div>
        </div>
        <div class="verdict-card" @click="navigateTo('/container/cpu-throttling')">
          <span class="verdict-dot" :class="throttlingVerdict.tone"></span>
          <div>
            <div class="verdict-title">CPU Throttling</div>
            <div class="verdict-value">{{ throttlingVerdict.text }}</div>
          </div>
        </div>
      </div>

      <!-- Recording timeline sparklines -->
      <div class="spark-grid">
        <div class="spark-card" @click="navigateTo('/system')">
          <div class="spark-label">JVM CPU</div>
          <div class="spark-value">
            {{ jvmCpuHeadline }}
            <span class="spark-unit">avg</span>
          </div>
          <SparklineChart
            :points="jvmCpuPoints"
            :color="ChartColors.chartColor('primary')"
            variant="area"
            aria-label="JVM CPU over recording"
          />
        </div>
        <div class="spark-card" @click="navigateTo('/garbage-collection/timeseries')">
          <div class="spark-label">Heap Used</div>
          <div class="spark-value">
            {{ heapHeadline }}
            <span class="spark-unit">peak</span>
          </div>
          <SparklineChart
            :points="heapPoints"
            :color="ChartColors.chartColor('color-purple')"
            variant="area"
            aria-label="Heap used over recording"
          />
        </div>
        <div class="spark-card" @click="navigateTo('/garbage-collection')">
          <div class="spark-label">GC Pauses</div>
          <div class="spark-value">
            {{ gcPauseHeadline }}
            <span class="spark-unit">worst</span>
          </div>
          <SparklineChart
            :points="gcPausePoints"
            :color="ChartColors.chartColor('color-warning')"
            variant="bars"
            aria-label="GC pauses over recording"
          />
        </div>
        <div class="spark-card" @click="navigateTo('/allocations')">
          <div class="spark-label">Allocation Rate</div>
          <div class="spark-value">
            {{ allocationRateHeadline }}
            <span class="spark-unit">avg</span>
          </div>
          <SparklineChart
            :points="allocationPoints"
            :color="ChartColors.chartColor('color-success')"
            variant="area"
            aria-label="Allocation rate over recording"
          />
        </div>
      </div>

      <!-- Top event types -->
      <MainCard>
        <template #header>
          <MainCardHeader icon="list-check" title="Top Event Types">
            <template #actions>
              <span class="event-hint">by samples · click a row to open it in the Event Viewer</span>
            </template>
          </MainCardHeader>
        </template>
        <LoadingState v-if="eventSummaries.isLoading.value" message="Loading event types..." />
        <template v-else>
          <div
            v-for="event in topEvents"
            :key="event.code"
            class="event-row"
            @click="openEventViewer(event)"
          >
            <div>
              <div class="event-label">{{ event.label }}</div>
              <div class="event-code">{{ event.code }}</div>
            </div>
            <div class="event-bar">
              <span class="event-bar-fill" :style="{ width: `${event.barWidth}%` }"></span>
            </div>
            <div class="event-value event-weight">
              <template v-if="event.weight">
                <div class="event-value-number">{{ event.weight }}</div>
                <div class="event-value-label">allocated</div>
              </template>
            </div>
            <div class="event-value">
              <div class="event-value-number">{{ event.samples }}</div>
              <div class="event-value-label">samples</div>
            </div>
          </div>
          <div class="event-row event-row-footer" @click="navigateTo('/event-types')">
            <div class="event-all-link">Browse all event types…</div>
          </div>
        </template>
      </MainCard>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import Badge from '@shared/components/Badge.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import SparklineChart from '@shared/components/SparklineChart.vue';
import FormattingService from '@shared/services/FormattingService';
import ChartColors from '@shared/services/ChartColors';
import { useTechnologyData } from '@/composables/useTechnologyData';
import InformationClient from '@/services/api/InformationClient';
import GuardianClient from '@/services/api/GuardianClient';
import AutoAnalysisClient from '@/services/api/AutoAnalysisClient';
import ProfileContainerClient from '@/services/api/ProfileContainerClient';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import ProfileAllocationClient from '@/services/api/ProfileAllocationClient';
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import ProfileExceptionsClient from '@/services/api/ProfileExceptionsClient';
import ProfileVmOperationsClient from '@/services/api/ProfileVmOperationsClient';
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import ProfileHeapMemoryClient from '@/services/api/ProfileHeapMemoryClient';
import EventSummariesClient from '@/services/api/EventSummariesClient';
import Profile from '@/services/api/model/Profile';
import GCTimeseriesType from '@/services/api/model/GCTimeseriesType';
import HeapMemoryTimeseriesType from '@/services/api/model/HeapMemoryTimeseriesType';

interface Props {
  profile: Profile;
}

const props = defineProps<Props>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const NOT_AVAILABLE = '-';
const LOADING_TEXT = 'Loading…';

const alwaysEnabled = computed(() => false);

const MILLIS_PER_SECOND = 1000;

const recordingDurationMillis = computed<number | null>(() => {
  const startedAt = props.profile.profilingStartedAt;
  const finishedAt = props.profile.profilingFinishedAt;
  if (!startedAt || !finishedAt) {
    return null;
  }
  const startMillis = Date.parse(startedAt);
  const endMillis = Date.parse(finishedAt);
  if (!Number.isFinite(startMillis) || !Number.isFinite(endMillis) || endMillis <= startMillis) {
    return null;
  }
  return endMillis - startMillis;
});

const recordingDuration = computed<string | null>(() => {
  const millis = recordingDurationMillis.value;
  return millis !== null ? FormattingService.formatDurationInMillis2Units(millis) : null;
});

// Per-section data sources — each loads independently so one slow or missing
// feature never blocks the rest of the dashboard.
const information = useTechnologyData(() => new InformationClient(profileId).info(), alwaysEnabled);
const guardian = useTechnologyData(() => new GuardianClient(profileId).list(), alwaysEnabled);
const autoAnalysis = useTechnologyData(
  () => new AutoAnalysisClient(profileId).rules(),
  alwaysEnabled
);
const gcOverview = useTechnologyData(
  () => new ProfileGCClient(profileId).getOverview(),
  alwaysEnabled
);
const allocationOverview = useTechnologyData(
  () => new ProfileAllocationClient(profileId).getOverview(),
  alwaysEnabled
);
const threadStatistics = useTechnologyData(
  () => new ProfileThreadClient(profileId).statistics(),
  alwaysEnabled
);
const exceptionsOverview = useTechnologyData(
  () => new ProfileExceptionsClient(profileId).getOverview(),
  alwaysEnabled
);
const vmOperations = useTechnologyData(
  () => new ProfileVmOperationsClient(profileId).getOverview(),
  alwaysEnabled
);
const systemOverview = useTechnologyData(
  () => new ProfileSystemClient(profileId).getOverview(),
  alwaysEnabled
);
const eventSummaries = useTechnologyData(
  () => EventSummariesClient.primary(profileId).events(),
  alwaysEnabled
);
const configurationData = useTechnologyData(
  () => new ProfileContainerClient(profileId).getConfiguration(),
  alwaysEnabled
);
const throttlingData = useTechnologyData(
  () => new ProfileContainerClient(profileId).getCpuThrottling(),
  alwaysEnabled
);
const cpuTimeline = useTechnologyData(
  () => new ProfileSystemClient(profileId).getCpuTimeline(),
  alwaysEnabled
);
const heapTimeseries = useTechnologyData(
  () =>
    new ProfileHeapMemoryClient(profileId).getTimeseries(
      HeapMemoryTimeseriesType.HEAP_BEFORE_AFTER_GC
    ),
  alwaysEnabled
);
const gcPauseTimeseries = useTechnologyData(
  () => new ProfileGCClient(profileId).getTimeseries(GCTimeseriesType.MAX_PAUSE),
  alwaysEnabled
);
const allocationTimeline = useTechnologyData(
  () => new ProfileAllocationClient(profileId).getTimeline(),
  alwaysEnabled
);

// ---------------------------------------------------------------------------
// Identity band (from /information configuration sections)
// ---------------------------------------------------------------------------

const INFO_SECTION_JVM = 'JVM Information';
const INFO_SECTION_CPU = 'CPU Information';
const INFO_SECTION_GC = 'GC Configuration';
const INFO_SECTION_HEAP = 'GC Heap Configuration';
const INFO_SECTION_CONTAINER = 'Container Configuration';

const GC_NAME_BY_COLLECTOR: Record<string, string> = {
  G1Old: 'G1',
  G1New: 'G1',
  G1Full: 'G1',
  ParallelOld: 'Parallel',
  ParallelScavenge: 'Parallel',
  SerialOld: 'Serial',
  DefNew: 'Serial',
  ConcurrentMarkSweep: 'CMS',
  ParNew: 'CMS',
  Z: 'ZGC',
  ZGenerational: 'ZGC',
  Shenandoah: 'Shenandoah'
};

const JVM_VERSION_PATTERN = /(\d+(?:\.\d+){1,3})/;

function infoValue(section: string, key: string): string | null {
  const sections = information.data.value as Record<string, Record<string, string>> | null;
  const value = sections?.[section]?.[key];
  return value !== undefined && value !== null && value !== '' ? String(value) : null;
}

const jvmVersion = computed<string | null>(() => {
  const rawVersion = infoValue(INFO_SECTION_JVM, 'JVM Version');
  if (rawVersion) {
    const match = rawVersion.match(JVM_VERSION_PATTERN);
    if (match) {
      return `Java ${match[1]}`;
    }
  }
  return infoValue(INFO_SECTION_JVM, 'JVM Name');
});

const gcName = computed<string | null>(() => {
  const oldCollector = infoValue(INFO_SECTION_GC, 'Old Garbage Collector');
  if (oldCollector === null) {
    return null;
  }
  return GC_NAME_BY_COLLECTOR[oldCollector] ?? oldCollector;
});

const heapMax = computed<string | null>(() => {
  const rawMax = infoValue(INFO_SECTION_HEAP, 'Maximum Heap Size');
  const bytes = Number(rawMax);
  if (rawMax === null || !Number.isFinite(bytes) || bytes <= 0) {
    return null;
  }
  return FormattingService.formatBytes(bytes);
});

const cpuDescription = computed<string | null>(() => {
  const cores = infoValue(INFO_SECTION_CPU, 'Cores');
  const hwThreads = infoValue(INFO_SECTION_CPU, 'Hardware Threads');
  if (cores === null) {
    return null;
  }
  return hwThreads !== null ? `${cores} cores / ${hwThreads} threads` : `${cores} cores`;
});

const containerType = computed<string | null>(() =>
  infoValue(INFO_SECTION_CONTAINER, 'Container Type')
);

// ---------------------------------------------------------------------------
// Health verdicts
// ---------------------------------------------------------------------------

const SEVERITY_WARNING = 'WARNING';
const SEVERITY_INFO = 'INFO';
const SEVERITY_OK = 'OK';

interface VerdictDisplay {
  tone: 'ok' | 'warn' | 'bad' | 'muted';
  text: string;
}

const guardianVerdict = computed<VerdictDisplay>(() => {
  if (guardian.isLoading.value) {
    return { tone: 'muted', text: LOADING_TEXT };
  }
  const categories = guardian.data.value ?? [];
  const results = categories.flatMap(category => category.results);
  if (results.length === 0) {
    return { tone: 'muted', text: 'No results' };
  }
  const warnings = results.filter(result => result.severity === SEVERITY_WARNING).length;
  const passed = results.filter(result => result.severity === SEVERITY_OK).length;
  if (warnings > 0) {
    return { tone: 'warn', text: `${warnings} warnings · ${passed} checks passed` };
  }
  return { tone: 'ok', text: `All good · ${passed} checks passed` };
});

const autoAnalysisVerdict = computed<VerdictDisplay>(() => {
  if (autoAnalysis.isLoading.value) {
    return { tone: 'muted', text: LOADING_TEXT };
  }
  const results = autoAnalysis.data.value ?? [];
  if (results.length === 0) {
    return { tone: 'muted', text: 'Not generated yet' };
  }
  const warnings = results.filter(result => result.severity === SEVERITY_WARNING).length;
  const infos = results.filter(result => result.severity === SEVERITY_INFO).length;
  if (warnings > 0) {
    return { tone: 'warn', text: `${warnings} warnings · ${infos} informational` };
  }
  return { tone: 'ok', text: `All good · ${infos} informational` };
});

const THROTTLING_TONE_BY_SEVERITY: Record<string, VerdictDisplay['tone']> = {
  NONE: 'ok',
  LOW: 'warn',
  MEDIUM: 'warn',
  HIGH: 'bad',
  NOT_APPLICABLE: 'muted'
};

const throttlingVerdict = computed<VerdictDisplay>(() => {
  if (containerDisabled.value) {
    return { tone: 'muted', text: 'Not containerized' };
  }
  if (throttlingData.isLoading.value) {
    return { tone: 'muted', text: LOADING_TEXT };
  }
  const verdict = throttlingData.data.value?.verdict;
  if (!verdict) {
    return { tone: 'muted', text: 'No data' };
  }
  return {
    tone: THROTTLING_TONE_BY_SEVERITY[verdict.severity] ?? 'muted',
    text: verdict.title
  };
});

// ---------------------------------------------------------------------------
// Key metrics
// ---------------------------------------------------------------------------

interface KpiMetric {
  icon: string;
  title: string;
  value: string;
  variant?: 'highlight' | 'danger' | 'warning' | 'info' | 'success';
  breakdown?: { label: string; value: string | number }[];
  targetPath: string;
}

const BASIS_POINTS_PER_PERCENT = 100;

function formatBasisPoints(basisPoints: number): string {
  return `${(basisPoints / BASIS_POINTS_PER_PERCENT).toFixed(1)} %`;
}

function formatPercent(value: number): string {
  return `${value.toFixed(2)} %`;
}

const durationSeconds = computed(() => {
  const millis = recordingDurationMillis.value ?? props.profile.durationInMillis ?? 0;
  return Math.max(1, millis / MILLIS_PER_SECOND);
});

const kpiLoading = computed(
  () =>
    gcOverview.isLoading.value &&
    allocationOverview.isLoading.value &&
    threadStatistics.isLoading.value &&
    exceptionsOverview.isLoading.value &&
    vmOperations.isLoading.value &&
    systemOverview.isLoading.value
);

const kpiMetrics = computed<KpiMetric[]>(() => {
  const metrics: KpiMetric[] = [];

  const system = systemOverview.data.value;
  if (system) {
    metrics.push({
      icon: 'cpu',
      title: 'JVM CPU Load',
      value: `${formatBasisPoints(system.avgJvmCpuBp)} avg`,
      variant: 'highlight',
      breakdown: [
        { label: 'Machine avg', value: formatBasisPoints(system.avgMachineCpuBp) },
        { label: 'Machine max', value: formatBasisPoints(system.maxMachineCpuBp) }
      ],
      targetPath: '/system'
    });
  }

  const gcHeader = gcOverview.data.value?.header;
  if (gcHeader && gcHeader.totalCollections > 0) {
    metrics.push({
      icon: 'recycle',
      title: 'Garbage Collection',
      value: `${gcHeader.totalCollections} collections`,
      variant: 'warning',
      breakdown: [
        { label: 'Throughput', value: formatPercent(gcHeader.gcThroughput) },
        {
          label: 'Max pause',
          value: FormattingService.formatDuration2Units(gcHeader.maxPauseTime)
        },
        { label: 'P99 pause', value: FormattingService.formatDuration2Units(gcHeader.p99PauseTime) }
      ],
      targetPath: '/garbage-collection'
    });
  }

  const allocation = allocationOverview.data.value;
  if (allocation && allocation.totalBytes > 0) {
    metrics.push({
      icon: 'box',
      title: allocation.sampled ? 'Allocations (sampled)' : 'Allocations',
      value: FormattingService.formatBytes(allocation.totalBytes),
      variant: 'highlight',
      breakdown: [
        {
          label: 'Rate',
          value: `${FormattingService.formatBytes(allocation.totalBytes / durationSeconds.value)}/s`
        },
        { label: 'Types', value: allocation.distinctTypes }
      ],
      targetPath: '/allocations'
    });
  }

  const threads = threadStatistics.data.value?.statistics;
  if (threads) {
    metrics.push({
      icon: 'list-task',
      title: 'Threads',
      value: `${threads.peak} peak`,
      variant: 'success',
      breakdown: [
        { label: 'Accumulated', value: threads.accumulated },
        { label: 'Parks', value: threads.parkCount ?? 0 },
        { label: 'Blocked', value: threads.monitorBlockCount ?? 0 }
      ],
      targetPath: '/thread-statistics'
    });
  }

  const exceptions = exceptionsOverview.data.value;
  if (exceptions && exceptions.totalThrowables > 0) {
    metrics.push({
      icon: 'exclamation-octagon',
      title: 'Exceptions',
      value: `${FormattingService.formatNumber(exceptions.totalThrowables)} thrown`,
      variant: 'danger',
      breakdown: [
        {
          label: 'Rate',
          value: `${(exceptions.totalThrowables / durationSeconds.value).toFixed(1)} /s`
        },
        { label: 'Errors', value: exceptions.errorCount },
        { label: 'Types', value: exceptions.distinctTypes }
      ],
      targetPath: '/exceptions'
    });
  }

  const vmOps = vmOperations.data.value;
  if (vmOps && vmOps.hasVmOperations) {
    metrics.push({
      icon: 'stopwatch',
      title: 'Safepoints / VM Ops',
      value: `${vmOps.vmOperationCount} operations`,
      variant: 'info',
      breakdown: [
        {
          label: 'Total pause',
          value: FormattingService.formatDuration2Units(vmOps.totalSafepointPauseNanos)
        },
        { label: 'Longest', value: FormattingService.formatDuration2Units(vmOps.longestPauseNanos) }
      ],
      targetPath: '/vm-operations'
    });
  }

  return metrics;
});

function onKpiClick(index: number): void {
  const metric = kpiMetrics.value[index];
  if (metric) {
    navigateTo(metric.targetPath);
  }
}

// ---------------------------------------------------------------------------
// Sparklines
// ---------------------------------------------------------------------------

const CPU_SERIES_JVM_USER = 'JVM User';
const CPU_SERIES_JVM_SYSTEM = 'JVM System';

const jvmCpuPoints = computed<number[][]>(() => {
  const series = cpuTimeline.data.value?.series ?? [];
  const user = series.find(serie => serie.name === CPU_SERIES_JVM_USER)?.data ?? [];
  const system = series.find(serie => serie.name === CPU_SERIES_JVM_SYSTEM)?.data ?? [];
  if (user.length === 0) {
    return system;
  }
  if (system.length === 0) {
    return user;
  }
  return user.map((point, index) => [point[0], point[1] + (system[index]?.[1] ?? 0)]);
});

const jvmCpuHeadline = computed(() => {
  const system = systemOverview.data.value;
  return system ? formatBasisPoints(system.avgJvmCpuBp) : NOT_AVAILABLE;
});

const heapPoints = computed<number[][]>(() => heapTimeseries.data.value?.data ?? []);

const heapHeadline = computed(() => {
  const data = heapPoints.value;
  if (data.length === 0) {
    return NOT_AVAILABLE;
  }
  const peak = Math.max(...data.map(point => point[1]));
  return FormattingService.formatBytes(peak);
});

const gcPausePoints = computed<number[][]>(() => {
  const series = gcPauseTimeseries.data.value?.series ?? [];
  if (series.length === 0) {
    return [];
  }
  const merged = new Map<number, number>();
  series.forEach(serie => {
    serie.data.forEach(point => {
      const current = merged.get(point[0]) ?? 0;
      merged.set(point[0], Math.max(current, point[1]));
    });
  });
  return Array.from(merged.entries())
    .sort((first, second) => first[0] - second[0])
    .map(entry => [entry[0], entry[1]]);
});

const gcPauseHeadline = computed(() => {
  const header = gcOverview.data.value?.header;
  if (!header) {
    return NOT_AVAILABLE;
  }
  return FormattingService.formatDuration2Units(header.maxPauseTime);
});

const allocationPoints = computed<number[][]>(() => {
  const series = allocationTimeline.data.value?.series ?? [];
  return series[0]?.data ?? [];
});

const allocationRateHeadline = computed(() => {
  const allocation = allocationOverview.data.value;
  if (!allocation || allocation.totalBytes <= 0) {
    return NOT_AVAILABLE;
  }
  return `${FormattingService.formatBytes(allocation.totalBytes / durationSeconds.value)}/s`;
});

// ---------------------------------------------------------------------------
// Container detection (for the CPU Throttling verdict chip)
// ---------------------------------------------------------------------------

// "Containerized" is decided by the recorded data itself (jdk.ContainerConfiguration),
// not by the CONTAINER_DASHBOARD feature flag — that flag also turns off for containers
// running without any CPU/memory limits.
const containerDisabled = computed(
  () => !configurationData.isLoading.value && configurationData.data.value?.configuration == null
);

// ---------------------------------------------------------------------------
// Event inventory
// ---------------------------------------------------------------------------

const TOP_EVENTS_COUNT = 6;
const WEIGHT_BYTES_EVENT_CODES = new Set([
  'jdk.ObjectAllocationInNewTLAB',
  'jdk.ObjectAllocationOutsideTLAB',
  'jdk.ObjectAllocationSample'
]);

interface EventRow {
  code: string;
  label: string;
  samples: string;
  samplesCount: number;
  weight: string | null;
  barWidth: number;
}

const topEvents = computed<EventRow[]>(() => {
  const summaries = eventSummaries.data.value ?? [];
  const withSamples = summaries
    .filter(summary => summary.primary && summary.primary.samples > 0)
    .sort((first, second) => second.primary.samples - first.primary.samples)
    .slice(0, TOP_EVENTS_COUNT);
  const maxSamples = withSamples[0]?.primary.samples ?? 1;
  return withSamples.map(summary => {
    const showBytes = WEIGHT_BYTES_EVENT_CODES.has(summary.code) && summary.primary.weight > 0;
    return {
      code: summary.code,
      label: summary.label,
      samples: FormattingService.formatNumber(summary.primary.samples),
      samplesCount: summary.primary.samples,
      weight: showBytes ? FormattingService.formatBytes(summary.primary.weight) : null,
      barWidth: Math.max(1, Math.round((summary.primary.samples / maxSamples) * 100))
    };
  });
});

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

// Preselects the clicked event type in the Event Viewer — same localStorage
// handoff that ProfileEventTypes.vue uses (matched by `code` on the other side).
function openEventViewer(event: EventRow): void {
  localStorage.setItem(
    'selectedEventType',
    JSON.stringify({ code: event.code, name: event.label, count: event.samplesCount })
  );
  navigateTo('/events');
}

function navigateTo(subPath: string): void {
  router.push(`/profiles/${profileId}${subPath}`);
}
</script>

<style scoped>
.summary-dashboard {
  display: flex;
  flex-direction: column;
}

/* Key metrics as the first, unwrapped component */
.kpi-section {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: var(--spacing-4);
}

/* Identity chips inside the page header */
.identity-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  margin-top: var(--spacing-2);
}

/* Health verdict chips */
.verdict-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: var(--spacing-3);
  margin-top: var(--spacing-4);
  margin-bottom: var(--spacing-4);
}

.verdict-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-base);
}

.verdict-card:hover {
  background: var(--color-bg-hover);
}

.verdict-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.verdict-dot.ok {
  background: var(--color-success);
  box-shadow: 0 0 0 4px var(--color-success-light);
}

.verdict-dot.warn {
  background: var(--color-warning);
  box-shadow: 0 0 0 4px var(--color-warning-light);
}

.verdict-dot.bad {
  background: var(--color-danger);
  box-shadow: 0 0 0 4px var(--color-danger-light);
}

.verdict-dot.muted {
  background: var(--color-text-light);
}

.verdict-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.verdict-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  line-height: var(--line-height-tight);
}

/* Sparkline cards */
.spark-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(215px, 1fr));
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);
}

.spark-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-3) var(--spacing-3) var(--spacing-2);
  cursor: pointer;
  transition: background-color var(--transition-base);
}

.spark-card:hover {
  background: var(--color-bg-hover);
}

.spark-label {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.spark-value {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-dark);
  margin: var(--spacing-1) 0 var(--spacing-1);
  font-variant-numeric: tabular-nums;
}

.spark-unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: var(--font-weight-medium);
}

/* Top event types */
.event-hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.event-row {
  display: grid;
  grid-template-columns: 1fr auto auto auto;
  gap: var(--spacing-4);
  align-items: center;
  padding: var(--spacing-2) var(--spacing-2);
  border-bottom: 1px solid var(--color-border-row);
  cursor: pointer;
  transition: background-color var(--transition-base);
}

.event-row:hover {
  background: var(--color-bg-hover);
}

.event-row-footer {
  border-bottom: none;
  grid-template-columns: 1fr;
}

.event-label {
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  font-size: var(--font-size-base);
}

.event-code {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-family: var(--bs-font-monospace, monospace);
}

.event-bar {
  width: 130px;
  height: 5px;
  border-radius: 999px;
  background: var(--color-lighter);
  overflow: hidden;
}

.event-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--chart-series-1);
}

.event-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
  min-width: 90px;
}

/* Extra column for weight-bearing event types (e.g. allocated bytes) — keeps the
   samples column aligned across rows by reserving the slot even when empty. */
.event-weight {
  min-width: 110px;
}

.event-value-number {
  font-weight: var(--font-weight-bold);
  color: var(--color-dark);
  font-size: var(--font-size-base);
}

.event-value-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.event-all-link {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
}

@media (max-width: 768px) {
  .event-bar {
    display: none;
  }
}
</style>

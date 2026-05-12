<template>
  <LoadingState v-if="loading" message="Loading garbage collection data..." />

  <ErrorState v-else-if="error" message="Failed to load garbage collection data" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
      title="Garbage Collection Analysis"
      description="Comprehensive analysis of garbage collection events and performance"
      icon="bi-recycle"
    />

    <!-- Key Metrics Row -->
    <GCMetricsStatsRow :profile-id="route.params.profileId as string" />

    <!-- GC Analysis Section -->
    <TabBar v-model="activeTab" :tabs="gcTabs" class="mb-3" />

    <!-- Pause Distribution Tab -->
    <div v-show="activeTab === 'distribution'">
      <div class="chart-container">
        <div id="gc-pause-distribution-chart"></div>
      </div>
    </div>

    <!-- GC Efficiency Tab -->
    <div v-show="activeTab === 'efficiency'">
        <div v-if="gcSummary" class="row">
          <div class="col-md-6">
            <div class="chart-container">
              <div id="gc-efficiency-pie-chart"></div>
            </div>
          </div>
          <div class="col-md-6">
            <div class="efficiency-stats">
              <h6 class="mb-3">GC Efficiency Metrics</h6>
              <div class="stat-item mb-3">
                <label class="stat-label">Application Time</label>
                <div class="stat-value">{{ gcSummary.applicationTime }}</div>
                <div class="progress mt-1">
                  <div
                    class="progress-bar bg-success"
                    role="progressbar"
                    :style="{ width: gcSummary.gcThroughput + '%' }"
                  ></div>
                </div>
              </div>
              <div class="stat-item mb-3">
                <label class="stat-label">GC Time</label>
                <div class="stat-value">{{ gcSummary.totalGcTime }}</div>
                <div class="progress mt-1">
                  <div
                    class="progress-bar bg-warning"
                    role="progressbar"
                    :style="{ width: gcSummary.gcOverhead + '%' }"
                  ></div>
                </div>
              </div>
              <div class="stat-item">
                <label class="stat-label">Collection Frequency</label>
                <div class="stat-value">{{ gcSummary.collectionFrequency }}</div>
              </div>
            </div>
          </div>
        </div>
    </div>

    <!-- Longest Pauses Tab -->
    <div v-show="activeTab === 'events'">
        <EmptyState
          v-if="!gcOverviewData?.longestPauses || gcOverviewData.longestPauses.length === 0"
          icon="bi-recycle"
          title="No garbage collection pause events"
        />
        <DataTable v-else>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Cause</th>
                  <th>Sum of Pauses</th>
                  <th>Duration</th>
                  <th>Before GC</th>
                  <th>After GC</th>
                  <th>Difference</th>
                  <th>Efficiency</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="event in gcOverviewData?.longestPauses"
                  :key="event.gcId"
                  @click="showEventDetails(event)"
                  style="cursor: pointer"
                >
                  <td>{{ event.gcId }}</td>
                  <td>
                    <div class="cause-cell">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <Badge
                          :value="event.cause"
                          variant="secondary"
                          size="m"
                          :title="getGCCauseTooltip(event.cause)"
                          class="gc-cause-badge"
                        />
                        <Badge
                          v-if="event.collectorName"
                          :value="event.collectorName"
                          :variant="getGenerationTypeBadgeVariant(event.generationType)"
                          size="s"
                        />
                        <Badge
                          :value="getConcurrentBadgeValue(event.concurrent)"
                          :variant="getConcurrentBadgeVariant(event.concurrent)"
                          size="s"
                        />
                        <Badge v-if="event.type" :value="event.type" variant="secondary" size="s" />
                      </div>
                      <span class="timestamp-path text-muted small">{{
                        FormattingService.formatTimestamp(event.timestamp)
                      }}</span>
                    </div>
                  </td>
                  <td>{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</td>
                  <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
                  <td>{{ FormattingService.formatBytes(event.beforeGC) }}</td>
                  <td>{{ FormattingService.formatBytes(event.afterGC) }}</td>
                  <td>
                    <Badge
                      :value="formatDifference(event.beforeGC, event.afterGC)"
                      :variant="getDifferenceBadgeVariant(event.beforeGC, event.afterGC)"
                      size="m"
                    />
                  </td>
                  <td>
                    <div class="d-flex align-items-center">
                      <div class="progress flex-grow-1 me-2" style="height: 6px; min-width: 40px">
                        <div
                          class="progress-bar"
                          :class="getDifferenceBarClass(event.beforeGC, event.afterGC)"
                          :style="{
                            width: getDifferencePercentage(event.beforeGC, event.afterGC) + '%'
                          }"
                        ></div>
                      </div>
                      <small class="text-muted"
                        >{{
                          getDifferencePercentage(event.beforeGC, event.afterGC).toFixed(1)
                        }}%</small
                      >
                    </div>
                  </td>
                </tr>
              </tbody>
        </DataTable>
    </div>

    <!-- Concurrent Cycles Tab -->
    <div v-show="activeTab === 'concurrent-cycles'">
        <div v-if="!gcOverviewData?.longestConcurrentEvents" class="alert alert-info">
          <i class="bi bi-info-circle me-2"></i>
          This garbage collector does not support concurrent cycles
        </div>
        <EmptyState
          v-else-if="gcOverviewData.longestConcurrentEvents.length === 0"
          icon="bi-recycle"
          title="No concurrent cycle events"
        />
        <DataTable v-else>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Timestamp</th>
                  <th>Collector Name</th>
                  <th>Duration</th>
                  <th>Sum of Pauses</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="event in gcOverviewData?.longestConcurrentEvents"
                  :key="event.gcId"
                  @click="showConcurrentEventDetails(event)"
                  style="cursor: pointer"
                >
                  <td>{{ event.gcId }}</td>
                  <td>{{ FormattingService.formatTimestamp(event.timestamp) }}</td>
                  <td>
                    <Badge
                      v-if="event.collectorName"
                      :value="event.collectorName"
                      :variant="getGenerationTypeBadgeVariant(event.generationType)"
                      size="s"
                      class="ms-2"
                    />
                  </td>
                  <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
                  <td>{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</td>
                </tr>
              </tbody>
        </DataTable>
    </div>

    <!-- Pause Types Reference Tab -->
    <div v-show="activeTab === 'pause-types'">
        <p class="pause-types-intro text-muted">
          Reference for every GC cause the JVM may emit via Java Flight Recorder. Filter by name or
          category to look up an unfamiliar cause from the
          <em>Longest Pauses</em> table.
        </p>

        <div class="pause-types-toolbar">
          <div class="input-group search-container pause-types-search">
            <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
            <input
              type="text"
              class="form-control search-input"
              placeholder="Filter by cause name…"
              v-model="pauseTypeSearch"
              autocomplete="off"
            />
            <button
              v-if="pauseTypeSearch"
              class="btn btn-outline-secondary clear-btn"
              type="button"
              @click="pauseTypeSearch = ''"
              title="Clear filter"
            >
              <i class="bi bi-x-lg"></i>
            </button>
          </div>

          <div class="pause-types-chips">
            <button
              type="button"
              class="pause-type-chip pause-type-chip--all"
              :class="{ active: pauseTypeActiveFilters.size === 0 }"
              @click="clearPauseTypeFilters"
            >
              All
            </button>
            <button
              v-for="group in pauseTypeGroups"
              :key="group.key"
              type="button"
              class="pause-type-chip"
              :class="[
                `pause-type-chip--${group.key}`,
                { active: pauseTypeActiveFilters.has(group.key) }
              ]"
              @click="togglePauseTypeFilter(group.key)"
            >
              <span class="dot"></span>{{ group.title }}
            </button>
          </div>
        </div>

        <div class="pause-types-result-count">
          Showing {{ filteredPauseTypes.length }} of {{ allPauseTypes.length }} causes
        </div>

        <EmptyState
          v-if="filteredPauseTypes.length === 0"
          icon="bi-funnel"
          title="No causes match the current filter"
        />
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover mb-0 pause-types-table">
            <thead>
              <tr>
                <th>Cause</th>
                <th>Category</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredPauseTypes" :key="item.name">
                <td class="pause-type-name">{{ item.name }}</td>
                <td>
                  <Badge :value="item.group.shortLabel" :variant="item.group.variant" size="s" />
                </td>
                <td class="pause-type-desc">{{ item.description }}</td>
              </tr>
            </tbody>
          </table>
        </div>
    </div>

    <!-- GC Event Details Modal -->
    <GCEventDetailsModal
      :event="selectedConcurrentEvent"
      modal-id="gcEventDetailsModal"
      :show="showEventDetailsModal"
      @update:show="showEventDetailsModal = $event"
    />

    <!-- GC Pause Details Modal -->
    <GCPauseDetailsModal
      :event="selectedPauseEvent"
      modal-id="gcPauseDetailsModal"
      :show="showPauseDetailsModal"
      @update:show="showPauseDetailsModal = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import PageHeader from '@/components/layout/PageHeader.vue';
import GCMetricsStatsRow from '@/components/gc/GCMetricsStatsRow.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import TabBar from '@/components/TabBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import DataTable from '@/components/table/DataTable.vue';
import type { Variant } from '@/types/ui';
import GCEventDetailsModal from '@/components/gc/GCEventDetailsModal.vue';
import GCPauseDetailsModal from '@/components/gc/GCPauseDetailsModal.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import GCOverviewData from '@/services/api/model/GCOverviewData';
import ConcurrentEvent from '@/services/api/model/ConcurrentEvent';
import GCEvent from '@/services/api/model/GCEvent';
import FormattingService from '@/services/FormattingService';
import {
  getConcurrentBadgeValue,
  getConcurrentBadgeVariant,
  getGenerationTypeBadgeVariant
} from '@/services/api/model/GarbageCollectionUtils';
import { GarbageCollectionCauseDescriptions } from '@/services/api/model/GarbageCollectionCauseDescriptions';
import '@/styles/shared-components.css';

const route = useRoute();
const loading = ref(true);
const error = ref<string | null>(null);

// Modal state
const showEventDetailsModal = ref(false);
const selectedConcurrentEvent = ref<ConcurrentEvent | null>(null);
const showPauseDetailsModal = ref(false);
const selectedPauseEvent = ref<GCEvent | null>(null);

// Tabs configuration for GC Analysis
const gcTabs = [
  { id: 'distribution', label: 'Pause Distribution', icon: 'bar-chart' },
  { id: 'efficiency', label: 'GC Efficiency', icon: 'pie-chart' },
  { id: 'events', label: 'Longest Pauses', icon: 'table' },
  { id: 'concurrent-cycles', label: 'Concurrent Cycles', icon: 'layers' },
  { id: 'pause-types', label: 'Pause Types', icon: 'info-circle' }
];
const activeTab = ref(gcTabs[0].id);

// Pause Types tab — searchable, category-chip-filterable list of every GC cause
// the JVM can emit. Descriptions come from the shared map, so the per-event tooltip
// on the Longest Pauses table and the rows below cannot drift.
type PauseTypeGroup = {
  key: string;
  title: string;
  shortLabel: string;
  variant: Variant;
  causes: string[];
};

const pauseTypeGroups: PauseTypeGroup[] = [
  {
    key: 'allocation',
    title: 'Allocation-Driven Pauses',
    shortLabel: 'Allocation',
    variant: 'indigo',
    causes: [
      'Allocation Failure',
      'G1 Evacuation Pause',
      'G1 Humongous Allocation',
      'To-space Exhausted',
      'Promotion Failed'
    ]
  },
  {
    key: 'concurrent',
    title: 'Concurrent Cycles',
    shortLabel: 'Concurrent',
    variant: 'green',
    causes: ['Concurrent Mark Start', 'Concurrent Mode Failure']
  },
  {
    key: 'pressure',
    title: 'Memory Pressure & Failure Modes',
    shortLabel: 'Pressure',
    variant: 'danger',
    causes: ['Last Ditch Collection', 'Metadata GC Threshold', 'Metadata GC Clear Soft References']
  },
  {
    key: 'tuning',
    title: 'JVM-Initiated Tuning',
    shortLabel: 'Tuning',
    variant: 'purple',
    causes: ['Ergonomics', 'Proactive', 'Warmup', 'Timer']
  },
  {
    key: 'external',
    title: 'External / Diagnostic Triggers',
    shortLabel: 'External',
    variant: 'orange',
    causes: [
      'System.gc()',
      'Diagnostic Command',
      'JFR Periodic',
      'Heap Inspection/Dump',
      'GCLocker Initiated GC'
    ]
  }
];

const allPauseTypes = (() => {
  const lookup = new Map(
    GarbageCollectionCauseDescriptions.getAllCauses().map(c => [c.name, c.description])
  );
  return pauseTypeGroups.flatMap(group =>
    group.causes.map(name => ({
      name,
      description: lookup.get(name) ?? '',
      group
    }))
  );
})();

const pauseTypeSearch = ref('');
const pauseTypeActiveFilters = ref<Set<string>>(new Set());

const filteredPauseTypes = computed(() => {
  const q = pauseTypeSearch.value.trim().toLowerCase();
  return allPauseTypes.filter(item => {
    if (
      pauseTypeActiveFilters.value.size > 0 &&
      !pauseTypeActiveFilters.value.has(item.group.key)
    ) {
      return false;
    }
    if (q && !item.name.toLowerCase().includes(q)) {
      return false;
    }
    return true;
  });
});

const togglePauseTypeFilter = (key: string) => {
  const next = new Set(pauseTypeActiveFilters.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  pauseTypeActiveFilters.value = next;
};

const clearPauseTypeFilters = () => {
  pauseTypeActiveFilters.value = new Set();
};

// Chart instances
let distributionChart: ApexCharts | null = null;
let efficiencyChart: ApexCharts | null = null;

// GC Overview Data
const gcOverviewData = ref<GCOverviewData>();

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileGCClient;

// GC Summary data (computed from real data)
const gcSummary = computed(() => {
  const data = gcOverviewData.value;
  if (!data?.header) {
    return null;
  }
  const header = data.header;
  return {
    totalCollections: FormattingService.formatNumber(header.totalCollections),
    youngCollections: FormattingService.formatNumber(header.youngCollections),
    oldCollections: FormattingService.formatNumber(header.oldCollections),
    maxPauseTime: FormattingService.formatDuration2Units(header.maxPauseTime),
    p99PauseTime: FormattingService.formatDuration2Units(header.p99PauseTime),
    p95PauseTime: FormattingService.formatDuration2Units(header.p95PauseTime),
    totalMemoryFreed: FormattingService.formatBytes(header.totalMemoryFreed),
    avgMemoryFreed: FormattingService.formatBytes(header.avgMemoryFreed),
    gcThroughput: FormattingService.formatPercentage(header.gcThroughput / 100),
    gcOverhead: FormattingService.formatPercentage(header.gcOverhead / 100),
    applicationTime: FormattingService.formatDuration2Units(data.efficiency.applicationTime),
    totalGcTime: FormattingService.formatDuration2Units(data.efficiency.gcTime),
    collectionFrequency: `${header.collectionFrequency.toFixed(2)} GC/s`,
    manualGCTime: FormattingService.formatDuration2Units(header.manualGCCalls.totalTime),
    systemGCCalls: FormattingService.formatNumber(header.manualGCCalls.systemGCCalls),
    diagnosticCommandCalls: FormattingService.formatNumber(
      header.manualGCCalls.diagnosticCommandCalls
    )
  };
});

const formatDifference = (beforeGC: number, afterGC: number) => {
  const difference = afterGC - beforeGC;
  const absValue = Math.abs(difference);
  const formattedValue = FormattingService.formatBytes(absValue);

  if (difference === 0) {
    return formattedValue;
  }

  return difference > 0 ? `+${formattedValue}` : `-${formattedValue}`;
};

const getDifferenceBadgeVariant = (beforeGC: number, afterGC: number): Variant => {
  const difference = afterGC - beforeGC;

  if (difference === 0) {
    return 'secondary';
  }

  return difference > 0 ? 'danger' : 'success';
};

const getDifferenceBarClass = (beforeGC: number, afterGC: number) => {
  const difference = afterGC - beforeGC;

  if (difference < 0) {
    return 'bg-success'; // Memory decreased (good) - green
  } else {
    return 'bg-danger'; // Memory increased (bad) - red
  }
};

const getDifferencePercentage = (beforeGC: number, afterGC: number) => {
  if (beforeGC === 0) return 0;
  const difference = Math.abs(afterGC - beforeGC);
  return Math.min((difference / beforeGC) * 100, 100);
};

const getGCCauseTooltip = (cause: string) => {
  return GarbageCollectionCauseDescriptions.getTooltipContent(cause);
};

const showEventDetails = (event: GCEvent) => {
  selectedPauseEvent.value = event;
  showPauseDetailsModal.value = true;
};

const showConcurrentEventDetails = (event: ConcurrentEvent) => {
  selectedConcurrentEvent.value = event;
  showEventDetailsModal.value = true;
};

// Create pause distribution chart
const createDistributionChart = async () => {
  await nextTick();

  const chartElement = document.getElementById('gc-pause-distribution-chart');
  if (!chartElement) {
    console.warn('Distribution chart element not found');
    return;
  }

  const distributionData = gcOverviewData.value?.pauseDistribution?.buckets;

  if (!distributionData || distributionData.length === 0) {
    console.warn('No pause distribution data available');
    return;
  }

  const options = {
    chart: {
      type: 'bar' as const,
      height: 380,
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      toolbar: {
        show: false
      }
    },
    series: [
      {
        name: 'GC Events',
        data:
          distributionData?.map(bucket => ({
            x: bucket.range,
            y: bucket.count
          })) || []
      }
    ],
    plotOptions: {
      bar: {
        horizontal: false,
        columnWidth: '70%',
        borderRadius: 4,
        dataLabels: {
          position: 'top'
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => val.toString(),
      offsetY: -20,
      style: {
        fontSize: '10px',
        colors: ['#304758']
      }
    },
    xaxis: {
      title: {
        text: 'Pause Time Range',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Number of Events',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    colors: ['#007bff'],
    tooltip: {
      y: {
        formatter: (value: number) => value + ' events'
      }
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    }
  };

  if (distributionChart) {
    distributionChart.destroy();
  }

  distributionChart = new ApexCharts(chartElement, options);
  distributionChart.render();
};

// Create efficiency pie chart
const createEfficiencyChart = async () => {
  await nextTick();

  const chartElement = document.getElementById('gc-efficiency-pie-chart');
  if (!chartElement) return;

  const efficiency = gcOverviewData.value?.efficiency;
  const throughput = efficiency?.throughputPercentage;
  const overhead = efficiency?.overheadPercentage;

  const options = {
    chart: {
      type: 'donut' as const,
      height: 380,
      fontFamily: 'inherit'
    },
    series: [throughput || 0, overhead || 0],
    labels: ['Application Time', 'GC Time'],
    colors: ['#28a745', '#ffc107'],
    plotOptions: {
      pie: {
        donut: {
          size: '70%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Throughput',
              formatter: () => `${throughput?.toFixed(1) || '0'}%`
            }
          }
        }
      }
    },
    legend: {
      position: 'bottom' as const
    },
    tooltip: {
      y: {
        formatter: (value: number) => value.toFixed(1) + '%'
      }
    }
  } as ApexCharts.ApexOptions;

  if (efficiencyChart) {
    efficiencyChart.destroy();
  }

  efficiencyChart = new ApexCharts(chartElement, options);
  efficiencyChart.render();
};

// Re-render the relevant chart when the user switches into a chart-backed tab.
watch(activeTab, newId => {
  if (newId === 'distribution') {
    createDistributionChart();
  } else if (newId === 'efficiency') {
    createEfficiencyChart();
  }
});

// Load data on component mount
// Load GC data from API
const loadGCData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }

    // Load overview data from API
    gcOverviewData.value = await client.getOverview();

    // Wait for DOM updates
    await nextTick();

    // Create the distribution chart by default since it's the first tab
    // Use a timeout to ensure the DOM is fully rendered
    setTimeout(() => {
      createDistributionChart();
    }, 100);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGCData();
});

onUnmounted(() => {
  // Cleanup charts
  if (distributionChart) {
    distributionChart.destroy();
  }
  if (efficiencyChart) {
    efficiencyChart.destroy();
  }
});
</script>

<style scoped>
.loading-overlay,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* Charts */
.chart-container {
  height: 400px;
  width: 100%;
}

/* Efficiency Stats */
.efficiency-stats {
  padding: 1rem;
  background-color: var(--color-light);
  border-radius: 8px;
  height: 400px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.progress {
  height: 6px;
  border-radius: 3px;
}

/* GC Cause Tooltips */
.gc-cause-badge {
  cursor: help;
}

/* Cause Cell Styles */
.cause-cell {
  display: flex;
  flex-direction: column;
}

.timestamp-path {
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }

  .efficiency-stats {
    height: auto;
    margin-top: 1rem;
  }
}

/* Pause Types tab — searchable, chip-filterable reference table. */
.pause-types-intro {
  font-size: 0.88rem;
  margin-bottom: 1rem;
}

.pause-types-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 0.75rem;
}

.pause-types-search {
  flex: 1;
  min-width: 240px;
  max-width: 360px;
}

.pause-types-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.pause-type-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.75rem;
  color: var(--color-text);
  cursor: pointer;
  user-select: none;
  transition:
    background-color 0.12s,
    border-color 0.12s,
    color 0.12s;
}

.pause-type-chip:hover {
  border-color: var(--chip-color, var(--color-primary));
  color: var(--color-dark);
}

.pause-type-chip.active {
  background: var(--chip-color, var(--color-primary));
  border-color: var(--chip-color, var(--color-primary));
  color: #fff;
}

.pause-type-chip .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--chip-color, var(--color-primary));
}

.pause-type-chip.active .dot {
  background: #fff;
}

.pause-type-chip--all {
  --chip-color: var(--color-secondary);
}
.pause-type-chip--allocation {
  --chip-color: var(--color-primary);
}
.pause-type-chip--concurrent {
  --chip-color: var(--color-success);
}
.pause-type-chip--pressure {
  --chip-color: var(--color-danger);
}
.pause-type-chip--tuning {
  --chip-color: var(--color-violet);
}
.pause-type-chip--external {
  --chip-color: var(--color-amber);
}

.pause-types-result-count {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.pause-types-table .pause-type-name {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  width: 220px;
}

.pause-types-table .pause-type-desc {
  color: var(--color-text-muted);
  line-height: 1.5;
}
</style>

<template>
  <div v-if="loading" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading thread data...</p>
  </div>

  <div v-else class="threads-container">
    <!-- Header Section -->
    <PageHeader
      title="Thread Statistics"
      description="View and analyze thread dumps and states"
      icon="bi-graph-up"
    >
      <!-- Summary Stats -->
      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <!-- Thread Activity Chart -->
      <ChartSection title="Active Threads Over Time" icon="bi-graph-up" :full-width="true">
        <TimeSeriesChart
          :primary-data="threadSerie"
          primary-title="Active Threads"
          :primary-axis-type="AxisFormatType.NUMBER"
          :visible-minutes="60"
          primary-color="#4285F4"
        />
      </ChartSection>

      <!-- Thread Tables Container -->
      <div class="thread-tables-container mb-4">
        <!-- Top Allocating Threads -->
        <div class="data-table-card">
          <div class="chart-card-header">
            <h5><i class="bi bi-memory me-2"></i>Top Allocators</h5>
          </div>
          <div class="thread-list">
            <div v-for="(thread, index) in topAllocatingThreads" :key="index" class="thread-item">
              <div class="thread-info">
                <span class="thread-name" :title="thread.threadInfo.name">
                  {{ thread.threadInfo.name }}
                </span>
              </div>
              <div class="thread-actions">
                <span class="allocation-badge">
                  {{ FormattingService.formatBytes(thread.allocatedBytes) }}
                </span>
                <button
                  class="flame-btn"
                  @click="viewThreadAllocationFlamegraph(thread)"
                  title="View thread allocation flamegraph"
                  :disabled="!allocationType"
                >
                  <i class="bi bi-fire"></i>
                </button>
              </div>
            </div>
            <div v-if="topAllocatingThreads.length === 0" class="empty-message">
              <i class="bi bi-inbox"></i>
              <span>No allocation data available</span>
            </div>
          </div>
        </div>

        <!-- Top CPU Load Threads -->
        <div class="data-table-card">
          <div class="chart-card-header">
            <h5><i class="bi bi-cpu me-2"></i>Top CPU Load Threads</h5>
          </div>

          <!-- User CPU Load Section -->
          <div class="cpu-section">
            <div class="section-header">
              <i class="bi bi-person-fill"></i>
              <span>User CPU Load</span>
            </div>
            <div class="thread-list">
              <div
                v-for="(thread, index) in topUserCpuThreads"
                :key="`user-${index}`"
                class="thread-item cpu-item"
              >
                <div class="thread-info">
                  <span class="timestamp-badge">{{ formatTimestamp(thread.timestamp) }}</span>
                  <span class="thread-name" :title="thread.threadInfo.name">
                    {{ thread.threadInfo.name }}
                  </span>
                </div>
                <div class="thread-actions">
                  <span class="cpu-badge"> {{ (thread.cpuLoad * 100).toFixed(2) }}% </span>
                  <button
                    class="flame-btn"
                    @click="viewThreadCpuProfile(thread)"
                    title="View thread CPU flamegraph"
                  >
                    <i class="bi bi-fire"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- System CPU Load Section -->
          <div class="cpu-section">
            <div class="section-header">
              <i class="bi bi-gear-fill"></i>
              <span>System CPU Load</span>
            </div>
            <div class="thread-list">
              <div
                v-for="(thread, index) in topSystemCpuThreads"
                :key="`system-${index}`"
                class="thread-item cpu-item"
              >
                <div class="thread-info">
                  <span class="timestamp-badge">{{ formatTimestamp(thread.timestamp) }}</span>
                  <span class="thread-name" :title="thread.threadInfo.name">
                    {{ thread.threadInfo.name }}
                  </span>
                </div>
                <div class="thread-actions">
                  <span class="cpu-badge"> {{ (thread.cpuLoad * 100).toFixed(2) }}% </span>
                  <button
                    class="flame-btn"
                    @click="viewThreadCpuProfile(thread)"
                    title="View thread CPU flamegraph"
                  >
                    <i class="bi bi-fire"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Flamegraph Modal -->
      <GenericModal
        modal-id="flamegraphModal"
        :show="showFlamegraphModal"
        size="fullscreen"
        :show-footer="false"
        @update:show="showFlamegraphModal = $event"
      >
        <template #header>
          <button
            type="button"
            class="btn-close"
            @click="showFlamegraphModal = false"
            aria-label="Close"
          ></button>
        </template>
        <div id="scrollable-wrapper" style="padding: 0.75rem" v-if="showFlamegraphModal">
          <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />
          <TimeSeriesChart
            :graph-updater="graphUpdater"
            :primary-axis-type="
              TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeightForModal, selectedEventCode)
            "
            :visible-minutes="60"
            :zoom-enabled="true"
            time-unit="seconds"
          />
          <FlamegraphComponent
            :with-timeseries="true"
            :use-weight="useWeightForModal"
            :use-guardian="null"
            scrollableWrapperClass="scrollable-wrapper"
            :flamegraph-tooltip="flamegraphTooltip"
            :graph-updater="graphUpdater"
            @loaded="scrollToTop"
          />
        </div>
      </GenericModal>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import { useRoute } from 'vue-router';
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import ThreadStats from '@/services/api/model/ThreadStats';
import AllocatingThread from '@/services/api/model/AllocatingThread';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import GenericModal from '@/components/GenericModal.vue';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater.ts';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip.ts';
import ThreadWithCpuLoad from '@/services/api/model/ThreadWithCpuLoad';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter.ts';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

const route = useRoute();
const profileId = route.params.profileId as string;

// State
const chartLoading = ref<boolean>(true);
const loading = ref<boolean>(true);
const threadSerie = ref<number[][]>();
const showFlamegraphModal = ref(false);
const selectedEventCode = ref('jdk.ObjectAllocationSample');
const useWeightForModal = ref(false);

let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

function scrollToTop() {
  const wrapper = document.querySelector('.scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

// Thread statistics - using ThreadStats model
const threadStats = ref<ThreadStats>(new ThreadStats(0, 0, 0, 0));

// Top allocating threads - using AllocatingThread model
const topAllocatingThreads = ref<AllocatingThread[]>([]);

// CPU Load Threads - now using real data from ThreadStatisticsResponse
const topUserCpuThreads = ref<ThreadWithCpuLoad[]>([]);
const topSystemCpuThreads = ref<ThreadWithCpuLoad[]>([]);

// State for allocation type from ThreadStatisticsResponse
const allocationType = ref<string>('');

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!threadStats.value) return [];

  return [
    {
      icon: 'people-fill',
      title: 'Created Threads',
      value: threadStats.value.accumulated,
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Peak Active',
          value: threadStats.value.peak,
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'pause-circle-fill',
      title: 'Blocking Events',
      value:
        (threadStats.value.sleepCount || 0) +
        (threadStats.value.parkCount || 0) +
        (threadStats.value.monitorBlockCount || 0),
      variant: 'danger' as const,
      breakdown: [
        {
          label: 'Sleep',
          value: threadStats.value.sleepCount || 0,
          color: '#EA4335'
        },
        {
          label: 'Parks',
          value: threadStats.value.parkCount || 0,
          color: '#EA4335'
        },
        {
          label: 'Monitor',
          value: threadStats.value.monitorBlockCount || 0,
          color: '#EA4335'
        }
      ]
    }
  ];
});

// Load thread statistics data
const loadThreadStatistics = async (): Promise<void> => {
  try {
    loading.value = true;
    chartLoading.value = true;

    const client = new ProfileThreadClient(profileId);

    // Call both APIs in parallel
    const [statisticsResponse, timeseriesResponse] = await Promise.all([
      client.statistics(),
      client.timeseries()
    ]);

    // Update thread statistics
    threadStats.value = statisticsResponse.statistics;

    // Update allocating threads
    topAllocatingThreads.value = statisticsResponse.allocators;

    // Update CPU load threads with real data
    topUserCpuThreads.value = statisticsResponse.userCpuLoad;
    topSystemCpuThreads.value = statisticsResponse.systemCpuLoad;

    // Store allocation type for flamegraph usage
    allocationType.value = statisticsResponse.allocationType;

    // Update thread serie for chart using the separate timeseries API call
    threadSerie.value = timeseriesResponse.data;
  } catch (error) {
    console.error('Failed to load thread statistics:', error);
    ToastService.error('Thread Statistics', 'Failed to load thread statistics');
  } finally {
    loading.value = false;
    chartLoading.value = false;
  }
};

const viewThreadAllocationFlamegraph = (thread: AllocatingThread) => {
  // Use the allocationType from ThreadStatisticsResponse instead of hardcoded value
  selectedEventCode.value = allocationType.value;

  // Allocations use weight
  useWeightForModal.value = true;

  // Use the threadInfo from the AllocatingThread
  // Create the flamegraph client for allocation data
  const flamegraphClient = new PrimaryFlamegraphClient(
    profileId,
    selectedEventCode.value,
    true,
    true,
    false,
    false,
    false,
    thread.threadInfo // Use the ThreadInfo object
  );

  // Initialize the graph updater with the client
  graphUpdater = new FullGraphUpdater(flamegraphClient, false);

  // Create tooltip for the allocation flamegraph
  flamegraphTooltip = FlamegraphTooltipFactory.create(selectedEventCode.value, false, false);

  // Show the flamegraph modal
  showFlamegraphModal.value = true;

  setTimeout(() => {
    graphUpdater.initialize();
  }, 200);
};

const viewThreadCpuProfile = (thread: ThreadWithCpuLoad) => {
  selectedEventCode.value = 'jdk.ExecutionSample';
  useWeightForModal.value = false;

  const flamegraphClient = new PrimaryFlamegraphClient(
    profileId,
    selectedEventCode.value,
    true,
    false,
    false,
    false,
    false,
    thread.threadInfo
  );

  graphUpdater = new FullGraphUpdater(flamegraphClient, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(selectedEventCode.value, false, false);

  showFlamegraphModal.value = true;

  setTimeout(() => {
    graphUpdater.initialize();
  }, 200);
};

const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

// Initialize component on mount
onMounted(() => {
  loadThreadStatistics();
});
</script>

<style scoped>
.threads-container {
  width: 100%;
  color: var(--color-dark);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans',
    'Helvetica Neue', sans-serif;
}

/* Thread Tables Container */
.thread-tables-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

/* Card Container */
.data-table-card {
  background: var(--color-bg-card);
  overflow: hidden;
  box-shadow: var(--shadow-base);
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-blue-500);
}

.data-table-card:hover {
  box-shadow: var(--shadow-md);
}

.data-table-card .chart-card-header {
  display: flex;
  align-items: center;
  padding: 0.6rem 1rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.data-table-card .chart-card-header h5 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-dark);
  display: flex;
  align-items: center;
}

.data-table-card .chart-card-header h5 i {
  color: var(--color-blue-500);
  font-size: 1rem;
}

/* Thread List */
.thread-list {
  padding: 0;
}

.thread-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid var(--color-border-row);
  transition: all 0.15s ease;
}

.thread-item:last-child {
  border-bottom: none;
}

.thread-item:hover {
  background-color: rgba(66, 133, 244, 0.04);
  padding-left: 1.1rem;
}

.thread-info {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
  min-width: 0;
}

.thread-name {
  font-weight: 500;
  font-size: 0.8rem;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.thread-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-shrink: 0;
}

/* Badges */
.allocation-badge,
.cpu-badge {
  padding: 0.2rem 0.6rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
  border: 1px solid rgba(234, 67, 53, 0.2);
}

.allocation-badge {
  background-color: rgba(230, 55, 87, 0.08);
  color: var(--color-danger);
}

.cpu-badge {
  background-color: rgba(230, 55, 87, 0.08);
  color: var(--color-danger);
  min-width: 55px;
  text-align: center;
}

.timestamp-badge {
  padding: 0.15rem 0.5rem;
  background-color: var(--color-light);
  border: 1px solid var(--color-border);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.65rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

/* Flamegraph Button */
.flame-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--color-border);
  background-color: white;
  color: var(--color-blue-500);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.flame-btn:hover:not(:disabled) {
  background-color: var(--color-blue-500);
  border-color: var(--color-blue-500);
  color: white;
  box-shadow: 0 1px 3px rgba(66, 133, 244, 0.3);
}

.flame-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.flame-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(66, 133, 244, 0.2);
}

/* CPU Sections */
.cpu-section {
  padding: 0;
}

.cpu-section:not(:last-child) {
  border-bottom: 1px solid var(--color-border);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 1rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.section-header i {
  color: var(--color-blue-500);
  font-size: 0.85rem;
}

.cpu-item .thread-info {
  gap: 0.5rem;
}

.cpu-item .thread-name {
  font-size: 0.8rem;
}

/* Empty State */
.empty-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.5rem 1rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  gap: 0.4rem;
}

.empty-message i {
  font-size: 1.5rem;
  color: var(--color-border);
}

/* Responsive Adjustments */
@media (max-width: 992px) {
  .thread-tables-container {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .thread-item {
    padding: 0.4rem 0.8rem;
  }

  .thread-item:hover {
    padding-left: 0.9rem;
  }

  .thread-name {
    font-size: 0.75rem;
  }

  .allocation-badge,
  .cpu-badge {
    font-size: 0.65rem;
    padding: 0.15rem 0.5rem;
  }

  .flame-btn {
    width: 24px;
    height: 24px;
    font-size: 0.7rem;
  }
}

@media (max-width: 576px) {
  .thread-actions {
    flex-direction: column;
    gap: 0.4rem;
  }

  .timestamp-badge {
    font-size: 0.6rem;
  }
}
</style>

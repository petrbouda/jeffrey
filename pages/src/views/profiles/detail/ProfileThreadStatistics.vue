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
        <ApexTimeSeriesChart
          :primary-data="threadSerie"
          primary-title="Active Threads"
          primary-axis-type="number"
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
      <div
        class="modal fade"
        id="flamegraphModal"
        tabindex="-1"
        aria-labelledby="flamegraphModalLabel"
        aria-hidden="true"
      >
        <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%">
          <div class="modal-content">
            <div class="modal-header">
              <button
                type="button"
                class="btn-close"
                @click="closeFlamegraphModal"
                aria-label="Close"
              ></button>
            </div>
            <div id="scrollable-wrapper" class="modal-body p-3" v-if="showFlamegraphModal">
              <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />
              <ApexTimeSeriesChart
                :graph-updater="graphUpdater"
                :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(selectedEventCode)"
                :visible-minutes="60"
                :zoom-enabled="true"
                time-unit="milliseconds"
              />
              <FlamegraphComponent
                :with-timeseries="true"
                :use-weight="useWeightForModal"
                :use-guardian="null"
                scrollableWrapperClass="scrollable-wrapper"
                :flamegraph-tooltip="flamegraphTooltip"
                :graph-updater="graphUpdater"
              />
            </div>
          </div>
        </div>
      </div>
      <div class="modal-backdrop fade show" v-if="showFlamegraphModal"></div>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProfileThreadClient from '@/services/thread/ProfileThreadClient';
import ThreadStats from '@/services/thread/model/ThreadStats';
import AllocatingThread from '@/services/thread/model/AllocatingThread';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import GraphType from '@/services/flamegraphs/GraphType';
import PrimaryFlamegraphClient from '@/services/flamegraphs/client/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import * as bootstrap from 'bootstrap';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater.ts';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip.ts';
import ThreadWithCpuLoad from '@/services/thread/model/ThreadWithCpuLoad';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter.ts';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
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

// Thread statistics - using ThreadStats model
const threadStats = ref<ThreadStats>(new ThreadStats(0, 0, 0, 0));

// Top allocating threads - using AllocatingThread model
const topAllocatingThreads = ref<AllocatingThread[]>([]);

// CPU Load Threads - now using real data from ThreadStatisticsResponse
const topUserCpuThreads = ref<ThreadWithCpuLoad[]>([]);
const topSystemCpuThreads = ref<ThreadWithCpuLoad[]>([]);

// Modal instance for flamegraph modal
let flamegraphModalInstance: bootstrap.Modal | null = null;

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

    if (!workspaceId.value || !projectId.value) return;

    const client = new ProfileThreadClient(workspaceId.value, projectId.value, profileId);

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

const closeFlamegraphModal = () => {
  if (flamegraphModalInstance) {
    flamegraphModalInstance.hide();
  }
  showFlamegraphModal.value = false;
};

// Cleanup on component unmount
onUnmounted(() => {
  if (flamegraphModalInstance) {
    flamegraphModalInstance.dispose();
    flamegraphModalInstance = null;
  }
});

const viewThreadAllocationFlamegraph = (thread: AllocatingThread) => {
  // Use the allocationType from ThreadStatisticsResponse instead of hardcoded value
  selectedEventCode.value = allocationType.value;

  // Allocations use weight
  useWeightForModal.value = true;

  // Use the threadInfo from the AllocatingThread
  // Create the flamegraph client for allocation data
  const flamegraphClient = new PrimaryFlamegraphClient(
    workspaceId.value!,
    projectId.value!,
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

  // Initialize the modal after the DOM is ready
  nextTick(() => {
    // Initialize and show the bootstrap modal
    const modalElement = document.getElementById('flamegraphModal');
    if (modalElement && !flamegraphModalInstance) {
      flamegraphModalInstance = new bootstrap.Modal(modalElement);

      // Add event listener to handle modal close
      modalElement.addEventListener('hidden.bs.modal', () => {
        showFlamegraphModal.value = false;
      });
    }

    if (flamegraphModalInstance) {
      flamegraphModalInstance.show();
    }

    // Initialize the graph updater after a short delay to ensure the modal is rendered
    setTimeout(() => {
      graphUpdater.initialize();
    }, 200);
  });
};

const viewThreadCpuProfile = (thread: ThreadWithCpuLoad) => {
  // Set up the flamegraph data for execution samples for the specific thread
  selectedEventCode.value = 'jdk.ExecutionSample';

  // CPU samples don't use weight
  useWeightForModal.value = false;

  // Create the flamegraph client for execution sample data
  const flamegraphClient = new PrimaryFlamegraphClient(
    workspaceId.value!,
    projectId.value!,
    profileId,
    selectedEventCode.value,
    true,
    false,
    false,
    false,
    false,
    thread.threadInfo // Filter by the specific thread
  );

  // Initialize the graph updater with the client
  graphUpdater = new FullGraphUpdater(flamegraphClient, false);

  // Create tooltip for the execution sample flamegraph
  flamegraphTooltip = FlamegraphTooltipFactory.create(selectedEventCode.value, false, false);

  // Show the flamegraph modal
  showFlamegraphModal.value = true;

  // Initialize the modal after the DOM is ready
  nextTick(() => {
    // Initialize and show the bootstrap modal
    const modalElement = document.getElementById('flamegraphModal');
    if (modalElement && !flamegraphModalInstance) {
      flamegraphModalInstance = new bootstrap.Modal(modalElement);

      // Add event listener to handle modal close
      modalElement.addEventListener('hidden.bs.modal', () => {
        showFlamegraphModal.value = false;
      });
    }

    if (flamegraphModalInstance) {
      flamegraphModalInstance.show();
    }

    // Initialize the graph updater after a short delay to ensure the modal is rendered
    setTimeout(() => {
      graphUpdater.initialize();
    }, 200);
  });
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
  color: #333;
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
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
  border: 1px solid #e9ecef;
  border-left: 3px solid #4285f4;
}

.data-table-card:hover {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.data-table-card .chart-card-header {
  display: flex;
  align-items: center;
  padding: 0.6rem 1rem;
  background-color: #fafbfc;
  border-bottom: 1px solid #e9ecef;
}

.data-table-card .chart-card-header h5 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
}

.data-table-card .chart-card-header h5 i {
  color: #4285f4;
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
  border-bottom: 1px solid #f1f3f5;
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
  color: #2c3e50;
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
  background-color: rgba(234, 67, 53, 0.08);
  color: #dc3545;
}

.cpu-badge {
  background-color: rgba(234, 67, 53, 0.08);
  color: #dc3545;
  min-width: 55px;
  text-align: center;
}

.timestamp-badge {
  padding: 0.15rem 0.5rem;
  background-color: #f7f9fc;
  border: 1px solid #e9ecef;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.65rem;
  color: #6c757d;
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
  border: 1px solid #dee2e6;
  background-color: white;
  color: #4285f4;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.flame-btn:hover:not(:disabled) {
  background-color: #4285f4;
  border-color: #4285f4;
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
  border-bottom: 1px solid #e9ecef;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 1rem;
  background-color: #f7f9fc;
  border-bottom: 1px solid #e9ecef;
  font-weight: 600;
  font-size: 0.75rem;
  color: #555;
  letter-spacing: 0.02em;
}

.section-header i {
  color: #4285f4;
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
  color: #6c757d;
  font-size: 0.8rem;
  gap: 0.4rem;
}

.empty-message i {
  font-size: 1.5rem;
  color: #dee2e6;
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

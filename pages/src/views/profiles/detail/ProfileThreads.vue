<template>
  <div v-if="loading" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading thread data...</p>
  </div>

  <div v-else class="threads-container">
    <!-- Header Section -->
    <DashboardHeader
        title="Thread Statistics"
        description="View and analyze thread dumps and states"
        icon="graph-up"
    />

    <!-- Summary Stats -->
    <div class="statistics-cards mb-4">
      <div class="stat-card stat-primary">
        <div class="kpi-icon">
          <i class="bi bi-people"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Total Threads</div>
          <div class="kpi-value">{{ threadStats.accumulated }}</div>
        </div>
      </div>

      <div class="stat-card stat-success">
        <div class="kpi-icon">
          <i class="bi bi-bar-chart"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Peak Active Threads</div>
          <div class="kpi-value">{{ threadStats.peak }}</div>
        </div>
      </div>

      <div class="stat-card stat-danger">
        <div class="kpi-icon">
          <i class="bi bi-moon"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Thread Sleep</div>
          <div class="kpi-value">{{ threadStats.sleepCount || 0 }}</div>
        </div>
      </div>

      <div class="stat-card stat-danger">
        <div class="kpi-icon">
          <i class="bi bi-p-square"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Thread Parks</div>
          <div class="kpi-value">{{ threadStats.parkCount || 0 }}</div>
        </div>
      </div>

      <div class="stat-card stat-danger">
        <div class="kpi-icon">
          <i class="bi bi-lock"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Monitor Blocks</div>
          <div class="kpi-value">{{ threadStats.monitorBlockCount || 0 }}</div>
        </div>
      </div>
    </div>

    <!-- Thread Activity Chart -->
    <div class="thread-chart-card mb-4">
      <div class="chart-card-header">
        <h5>Active Threads Over Time</h5>
      </div>
      <div class="card-body">
        <div class="chart-container">
          <TimeSeriesLineGraph
              :primaryData="threadSerie"
              primaryTitle="Active Threads"
              :loading="chartLoading"
              :visibleMinutes="15"
          />
        </div>
      </div>
    </div>

    <!-- Thread Tables Container -->
    <div class="thread-tables-container mb-4">
      <!-- Top Allocating Threads -->
      <div class="data-table-card">
        <div class="chart-card-header">
          <h5>Top Allocators</h5>
        </div>
        <div class="table-responsive">
          <table class="table table-hover">
            <thead>
            <tr>
              <th>Thread Name</th>
              <th class="text-end">Allocated Memory</th>
              <th class="text-end pe-3">Actions</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(thread, index) in topAllocatingThreads" :key="index" class="allocation-row">
              <td class="thread-name">{{ thread.name }}</td>
              <td class="allocation-value">{{ FormattingService.formatBytes(thread.allocatedBytes) }}</td>
              <td class="text-end pe-3">
                <button
                    class="btn btn-sm btn-outline-secondary allocation-flame-btn"
                    @click="viewThreadAllocationFlamegraph(thread)"
                    title="View thread allocation flamegraph"
                >
                  <i class="bi bi-fire"></i>
                </button>
              </td>
            </tr>
            <tr v-if="topAllocatingThreads.length === 0">
              <td colspan="3" class="empty-message">No allocation data available</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Top CPU Load Threads -->
      <div class="data-table-card">
        <div class="chart-card-header">
          <h5>Top CPU Load Threads</h5>
        </div>
        <div class="table-responsive">
          <table class="table table-hover">
            <thead>
            <tr>
              <th>Timestamp</th>
              <th>Thread Name</th>
              <th class="text-end">CPU Load (%)</th>
              <th class="text-end pe-3">Actions</th>
            </tr>
            </thead>
            <tbody>
            <!-- User CPU Load Section -->
            <tr class="section-header">
              <td colspan="4" class="section-title">User CPU Load</td>
            </tr>
            <tr v-for="(thread, index) in topUserCpuThreads" :key="`user-${index}`" class="cpu-row">
              <td class="timestamp">{{ formatTimestamp(thread.timestamp) }}</td>
              <td class="thread-name">{{ thread.name }}</td>
              <td class="cpu-value">{{ thread.cpuLoad.toFixed(2) }}%</td>
              <td class="text-end pe-3">
                <button
                    class="btn btn-sm btn-outline-secondary allocation-flame-btn"
                    @click="viewThreadCpuProfile(thread)"
                    title="View thread CPU flamegraph"
                >
                  <i class="bi bi-fire"></i>
                </button>
              </td>
            </tr>

            <!-- System CPU Load Section -->
            <tr class="section-header">
              <td colspan="4" class="section-title">System CPU Load</td>
            </tr>
            <tr v-for="(thread, index) in topSystemCpuThreads" :key="`system-${index}`" class="cpu-row">
              <td class="timestamp">{{ formatTimestamp(thread.timestamp) }}</td>
              <td class="thread-name">{{ thread.name }}</td>
              <td class="cpu-value">{{ thread.cpuLoad.toFixed(2) }}%</td>
              <td class="text-end pe-3">
                <button
                    class="btn btn-sm btn-outline-secondary allocation-flame-btn"
                    @click="viewThreadCpuProfile(thread)"
                    title="View thread CPU flamegraph"
                >
                  <i class="bi bi-fire"></i>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Flamegraph Modal -->
    <div class="modal fade" id="flamegraphModal" tabindex="-1" aria-labelledby="flamegraphModalLabel"
         aria-hidden="true">
      <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="btn-close" @click="closeFlamegraphModal" aria-label="Close"></button>
          </div>
          <div id="scrollable-wrapper" class="modal-body p-3" v-if="showFlamegraphModal">
            <TimeseriesComponent
                :graph-type="GraphType.PRIMARY"
                :event-type="selectedEventCode"
                :use-weight="false"
                :with-search="null"
                :search-enabled="true"
                :zoom-enabled="true"
                :graph-updater="graphUpdater"/>
            <FlamegraphComponent
                :with-timeseries="true"
                :with-search="null"
                :use-weight="false"
                :use-guardian="null"
                :time-range="null"
                :save-enabled="false"
                scrollableWrapperClass="scrollable-wrapper"
                :flamegraph-tooltip="flamegraphTooltip"
                :graph-updater="graphUpdater"/>
          </div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop fade show" v-if="showFlamegraphModal"></div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, onMounted, onUnmounted, ref} from 'vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import {useRoute} from "vue-router";
import ProfileThreadClient from '@/services/thread/ProfileThreadClient';
import ThreadStats from '@/services/thread/model/ThreadStats';
import AllocatingThread from '@/services/thread/model/AllocatingThread';
import DashboardHeader from '@/components/DashboardHeader.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeseriesComponent from '@/components/TimeseriesComponent.vue';
import GraphType from '@/services/flamegraphs/GraphType';
import PrimaryFlamegraphClient from '@/services/flamegraphs/client/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import * as bootstrap from 'bootstrap';
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater.ts";
import FlamegraphTooltip from "@/services/flamegraphs/tooltips/FlamegraphTooltip.ts";
import ThreadInfo from '@/services/thread/model/ThreadInfo';
import ThreadWithCpuLoad from '@/services/thread/model/ThreadWithCpuLoad';

const route = useRoute()

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

// State
const chartLoading = ref<boolean>(true);
const loading = ref<boolean>(true);
const threadSerie = ref<number[][]>();
const showFlamegraphModal = ref(false);
const selectedEventCode = ref("jdk.ObjectAllocationSample");

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

// Load thread statistics data
const loadThreadStatistics = async (): Promise<void> => {
  try {
    loading.value = true;
    chartLoading.value = true;

    const client = new ProfileThreadClient(projectId, profileId);

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
  // Set up the flamegraph data for the specific thread
  selectedEventCode.value = "jdk.ObjectAllocationSample";

  // Create ThreadInfo object from the thread name
  const threadInfo = new ThreadInfo(thread.name, -1, "unknown");

  // Create the flamegraph client for allocation data
  const flamegraphClient = new PrimaryFlamegraphClient(
      projectId,
      profileId,
      selectedEventCode.value,
      true,
      false,
      false,
      false,
      false,
      threadInfo // Use the ThreadInfo object
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
  // This is a placeholder for future implementation
  // In the future, this will open a modal with the thread's CPU profile
  ToastService.info('profileToast', `CPU profile for thread ${thread.name} will be shown in the future`);
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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
}

/* Modern Statistics Cards */
.statistics-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1rem;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 1.25rem;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  border-left: 4px solid transparent;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-primary {
  border-left-color: #4285F4;
}

.stat-success {
  border-left-color: #28a745;
}

.stat-info {
  border-left-color: #17a2b8;
}

.stat-danger {
  border-left-color: #dc3545;
}

.stat-warning {
  border-left-color: #FBBC05;
}

.stat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 600;
  color: #111;
  margin-bottom: 0.25rem;
  line-height: 1.1;
}

.stat-label {
  color: #777;
  font-size: 0.9rem;
  font-weight: 500;
}

/* KPI Icon Styles */
.kpi-icon {
  font-size: 1.5rem;
  line-height: 1;
  margin-right: 0.75rem;
  flex-shrink: 0;
}

.kpi-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: #777;
  margin-bottom: 0.125rem;
}

.kpi-value {
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
}

/* Thread Activity Chart */
.thread-chart-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.thread-chart-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #f0f0f0;
}

.thread-chart-card .card-header h5 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
}

.chart-container {
  width: 100%;
  min-height: 350px;
  position: relative;
  overflow: hidden;
}

/* Common Table Styles for both allocation and CPU tables */

.thread-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 250px;
}

.timestamp {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8rem;
  color: #6c757d;
  white-space: nowrap;
}

.allocation-value,
.cpu-value {
  text-align: right;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8125rem;
}

.cpu-value {
  color: #dc3545;
}

/* Allocation flamegraph button styling */
.allocation-flame-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  padding: 0;
  border-radius: 5px;
  transition: all 0.2s ease;
  border: 1px solid #dee2e6;
  color: #4285F4;
}

.allocation-flame-btn:hover {
  background-color: #f8f9fa;
  border-color: #4285F4;
  color: #4285F4;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(66, 133, 244, 0.2);
}

.allocation-flame-btn:focus {
  box-shadow: 0 0 0 0.2rem rgba(66, 133, 244, 0.25);
}

/* Compact allocation table styling */
.data-table-card .table {
  margin-bottom: 0;
}

.data-table-card .table th {
  background: #f7f9fc;
  font-weight: 600;
  font-size: 0.8rem;
  color: #555;
  border-top: none;
  padding: 0.5rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e9ecef;
  white-space: nowrap;
}

.data-table-card .table th:last-child {
  text-align: right;
  padding-right: 0.75rem;
}

.allocation-row {
  transition: background-color 0.15s;
}

.allocation-row:hover {
  background-color: rgba(66, 133, 244, 0.03);
}

.allocation-row td {
  font-size: 0.8rem;
  padding: 0.4rem 0.75rem;
  border-bottom: 1px solid #f1f3f5;
  color: #495057;
  vertical-align: middle;
  line-height: 1.3;
}

.allocation-row td.thread-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
  color: #2c3e50;
}

.allocation-row td.allocation-value {
  text-align: right;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
  font-weight: 600;
  color: #dc3545;
  white-space: nowrap;
}

.allocation-row td:last-child {
  padding-right: 0.75rem;
  text-align: right;
}

/* CPU Load Table specific styling */
.cpu-row {
  transition: background-color 0.15s;
}

.cpu-row:hover {
  background-color: rgba(66, 133, 244, 0.03);
}

.cpu-row td {
  font-size: 0.8rem;
  padding: 0.4rem 0.75rem;
  border-bottom: 1px solid #f1f3f5;
  color: #495057;
  vertical-align: middle;
  line-height: 1.3;
}

.cpu-row td.timestamp {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
  color: #6c757d;
  white-space: nowrap;
}

.cpu-row td.thread-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
  color: #2c3e50;
}

.cpu-row td.cpu-value {
  text-align: right;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
  font-weight: 600;
  color: #dc3545;
  white-space: nowrap;
}

.cpu-row td:last-child {
  padding-right: 0.75rem;
  text-align: right;
}

/* Section header styling for CPU table */
.section-header td {
  padding: 0.5rem 0.75rem !important;
  border-bottom: 1px solid #e9ecef !important;
}

.section-header .section-title {
  background-color: #f7f9fc;
  font-weight: 600;
  color: #555;
  text-align: left;
  font-size: 0.8rem;
  letter-spacing: 0.03em;
  border-left: 3px solid #4285F4;
  padding-left: 0.5rem;
  margin: 0;
}

/* Thread Tables Container */
.thread-tables-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

.data-table-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  border-left: 4px solid #4285F4;
}

.data-table-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.data-table-card .chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background-color: white;
  border-bottom: 1px solid #f0f0f0;
}

.data-table-card .chart-card-header h5 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
}

/* Responsive Adjustments */
@media (max-width: 992px) {
  .statistics-cards {
    grid-template-columns: repeat(3, 1fr);
  }

  .thread-tables-container {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .statistics-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .statistics-cards {
    grid-template-columns: 1fr;
  }
}
</style>

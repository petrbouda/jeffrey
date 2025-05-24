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

      <div class="stat-card stat-warning">
        <div class="kpi-icon">
          <i class="bi bi-moon"></i>
        </div>
        <div class="stat-content">
          <div class="kpi-title">Thread Sleep</div>
          <div class="kpi-value">{{ threadStats.sleepCount || 0 }}</div>
        </div>
      </div>

      <div class="stat-card stat-warning">
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
          <h5>Top 20 Allocators</h5>
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
                    class="btn btn-sm btn-danger action-btn"
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
                    class="btn btn-sm btn-danger action-btn"
                    @click="viewThreadCpuProfile(thread)"
                    title="View thread CPU flamegraph"
                >
                  <i class="bi bi-fire"></i>
                </button>
              </td>
            </tr>

            <!-- Delimiter -->
            <tr class="section-delimiter">
              <td colspan="4">
                <hr class="my-2">
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
                    class="btn btn-sm btn-danger action-btn"
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

    <!-- Thread Detail Modal -->
    <div class="modal fade" id="threadDetailModal" tabindex="-1"
         :class="{ 'show': showThreadDetailModal }"
         :style="{ display: showThreadDetailModal ? 'block' : 'none' }">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Thread Details</h5>
            <button type="button" class="btn-close" @click="closeThreadDetailModal"></button>
          </div>
          <div class="modal-body" v-if="selectedThread">
            <div class="row mb-3">
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Thread Name</div>
                  <div class="fw-bold">{{ selectedThread.name }}</div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Thread State</div>
                  <div>
                    <span class="badge" :class="getThreadStateBadgeClass(selectedThread.state)">
                      {{ selectedThread.state }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div class="row mb-3">
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Thread ID</div>
                  <div>{{ selectedThread.id }}</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Priority</div>
                  <div>{{ selectedThread.priority }}</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Daemon</div>
                  <div>{{ selectedThread.daemon ? 'Yes' : 'No' }}</div>
                </div>
              </div>
            </div>

            <div class="row mb-3">
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">CPU Time</div>
                  <div>{{ selectedThread.cpuTime }}ms</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Blocked Time</div>
                  <div>{{ selectedThread.blockedTime }}ms</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Wait Time</div>
                  <div>{{ selectedThread.waitTime }}ms</div>
                </div>
              </div>
            </div>

            <hr>

            <h6>Stack Trace</h6>
            <pre class="bg-light p-3 rounded small">{{ selectedThread.stackTrace }}</pre>

            <h6 class="mt-3">Locks</h6>
            <div v-if="selectedThread.locks && selectedThread.locks.length > 0">
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                  <tr>
                    <th>Lock Type</th>
                    <th>Object</th>
                    <th>Status</th>
                  </tr>
                  </thead>
                  <tbody>
                  <tr v-for="(lock, index) in selectedThread.locks" :key="index">
                    <td>{{ lock.type }}</td>
                    <td>{{ lock.object }}</td>
                    <td>{{ lock.status }}</td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div v-else class="text-muted">
              No locks held or waited on by this thread.
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeThreadDetailModal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop fade show" v-if="showThreadDetailModal"></div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import {useRoute} from "vue-router";
import ProfileThreadClient from '@/services/thread/ProfileThreadClient';
import ThreadStats from '@/services/thread/model/ThreadStats';
import AllocatingThread from '@/services/thread/model/AllocatingThread';
import DashboardHeader from '@/components/DashboardHeader.vue';

const route = useRoute()

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

// State
const showThreadDetailModal = ref(false);
const selectedThread = ref<any>(null);
const chartLoading = ref<boolean>(true);
const loading = ref<boolean>(true);
const threadSerie = ref<number[][]>();

// Thread statistics - using ThreadStats model
const threadStats = ref<ThreadStats>(new ThreadStats(0, 0, 0, 0));

// Top allocating threads - using AllocatingThread model
const topAllocatingThreads = ref<AllocatingThread[]>([]);

// CPU Load Threads - Mock data
interface CpuLoadThread {
  timestamp: number;
  name: string;
  cpuLoad: number;
}

// Mock data for User CPU Load threads (max 10)
const topUserCpuThreads = ref<CpuLoadThread[]>([
  {timestamp: Date.now() - 1000, name: 'main', cpuLoad: 87.5},
  {timestamp: Date.now() - 2000, name: 'worker-thread-1', cpuLoad: 73.2},
  {timestamp: Date.now() - 3000, name: 'http-nio-8080-exec-1', cpuLoad: 65.8},
  {timestamp: Date.now() - 4000, name: 'scheduler-thread-1', cpuLoad: 58.9},
  {timestamp: Date.now() - 5000, name: 'database-pool-1', cpuLoad: 52.4},
  {timestamp: Date.now() - 6000, name: 'async-processor-2', cpuLoad: 47.1},
  {timestamp: Date.now() - 7000, name: 'cache-manager', cpuLoad: 41.6},
  {timestamp: Date.now() - 8000, name: 'message-handler-3', cpuLoad: 38.2},
  {timestamp: Date.now() - 9000, name: 'timer-thread', cpuLoad: 34.7},
  {timestamp: Date.now() - 10000, name: 'worker-thread-2', cpuLoad: 29.3}
]);

// Mock data for System CPU Load threads (max 10)
const topSystemCpuThreads = ref<CpuLoadThread[]>([
  {timestamp: Date.now() - 1500, name: 'GC Thread#0', cpuLoad: 94.2},
  {timestamp: Date.now() - 2500, name: 'VM Thread', cpuLoad: 76.8},
  {timestamp: Date.now() - 3500, name: 'C2 CompilerThread0', cpuLoad: 68.5},
  {timestamp: Date.now() - 4500, name: 'G1 Young RemSet Sampling', cpuLoad: 61.7},
  {timestamp: Date.now() - 5500, name: 'G1 Conc#0', cpuLoad: 55.3},
  {timestamp: Date.now() - 6500, name: 'VM Periodic Task Thread', cpuLoad: 49.1},
  {timestamp: Date.now() - 7500, name: 'GC Thread#1', cpuLoad: 43.8},
  {timestamp: Date.now() - 8500, name: 'C1 CompilerThread0', cpuLoad: 37.4},
  {timestamp: Date.now() - 9500, name: 'Signal Dispatcher', cpuLoad: 31.9},
  {timestamp: Date.now() - 10500, name: 'Finalizer', cpuLoad: 26.5}
]);

// Load thread statistics data
const loadThreadStatistics = async (): Promise<void> => {
  try {
    loading.value = true;
    chartLoading.value = true;

    const client = new ProfileThreadClient(projectId, profileId);
    const response = await client.statistics();

    // Update thread statistics
    threadStats.value = response.statistics;

    // Update allocating threads
    topAllocatingThreads.value = response.allocators;

    // Update chart data from serie
    threadSerie.value = response.serie.data;
  } catch (error) {
    console.error('Failed to load thread statistics:', error);
    ToastService.error('Thread Statistics', 'Failed to load thread statistics');
  } finally {
    loading.value = false;
    chartLoading.value = false;
  }
};


const closeThreadDetailModal = () => {
  showThreadDetailModal.value = false;
};

const viewThreadAllocationFlamegraph = (thread: any) => {
  // This is a placeholder for future implementation
  // In the future, this will open a modal with the thread's allocation flamegraph
  ToastService.info('profileToast', `Allocation flamegraph for thread ${thread.name} will be shown in the future`);
};

const viewThreadCpuProfile = (thread: CpuLoadThread) => {
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

const getThreadStateBadgeClass = (state: string) => {
  switch (state) {
    case 'RUNNABLE':
      return 'state-runnable';
    case 'BLOCKED':
      return 'state-blocked';
    case 'WAITING':
      return 'state-waiting';
    case 'TIMED_WAITING':
      return 'state-timed-waiting';
    case 'TERMINATED':
      return 'state-terminated';
    default:
      return 'state-terminated';
  }
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

.threads-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
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

/* Thread State Colors - Keep these for reference */
.state-runnable {
  background-color: #28a745;
  color: white;
}

.state-blocked {
  background-color: #dc3545;
  color: white;
}

.state-waiting {
  background-color: #ffc107;
  color: #212529;
}

.state-timed-waiting {
  background-color: #fd7e14;
  color: white;
}

.state-terminated {
  background-color: #6c757d;
  color: white;
}

/* Common Table Styles for both allocation and CPU tables */
.allocation-table,
.cpu-load-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin-bottom: 0;
}

.allocation-table th,
.cpu-load-table th {
  background: #f7f9fc;
  font-weight: 600;
  font-size: 0.9rem;
  color: #555;
  border-top: none;
  padding: 0.75rem 1.5rem;
  text-align: left;
  border-bottom-width: 1px;
}

.allocation-table th:last-child,
.cpu-load-table th:last-child {
  text-align: right;
}

.allocation-row,
.cpu-row {
  transition: background-color 0.15s;
}

.allocation-row:hover,
.cpu-row:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.allocation-row td,
.cpu-row td {
  font-size: 0.9rem;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid #f1f1f1;
  color: #495057;
  vertical-align: middle;
}

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
  font-weight: 600;
}

.cpu-value {
  color: #dc3545;
}

/* Action button styling */
.action-btn {
  min-width: 36px;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  padding: 0;
  white-space: nowrap;
  border-radius: 50%;
  transition: all 0.2s;
  background-color: rgba(220, 53, 69, 0.9);
  border: none;
}

.action-btn:hover {
  background-color: #dc3545;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(220, 53, 69, 0.3);
}

/* CPU Load Table Section Styles */
.section-header .section-title {
  background-color: #f7f9fc;
  font-weight: 600;
  color: #555;
  text-align: left;
  padding: 0.75rem 1.5rem;
  font-size: 0.9rem;
  letter-spacing: 0.03em;
  border-left: 4px solid #4285F4;
}

.section-delimiter td {
  padding: 0;
  border-bottom: none;
}

.section-delimiter hr {
  margin: 0.25rem 1rem;
  border-color: rgba(222, 226, 230, 0.5);
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
  transform: translateY(-3px);
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

<template>
  <div v-if="false" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading thread data...</p>
  </div>

  <div v-else class="threads-container">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="threads-title">
        <i class="bi bi-graph-up me-2"></i>
        Thread Statistics
      </h2>
      <p class="text-muted fs-6">View and analyze thread dumps and states</p>
    </div>
    <!-- Summary Stats -->
    <div class="statistics-cards mb-4">
      <div class="stat-card stat-primary">
        <div class="stat-content">
          <div class="stat-value">{{ threadStats.totalThreads }}</div>
          <div class="stat-label">Accumulated Count</div>
        </div>
      </div>

      <div class="stat-card stat-success">
        <div class="stat-content">
          <div class="stat-value">{{ threadStats.activeThreads }}</div>
          <div class="stat-label">Peak Count</div>
        </div>
      </div>

      <div class="stat-card stat-danger">
        <div class="stat-content">
          <div class="stat-value">{{ threadStats.blockedThreads }}</div>
          <div class="stat-label">Max Active Threads</div>
        </div>
      </div>

      <div class="stat-card stat-warning">
        <div class="stat-content">
          <div class="stat-value">{{ threadStats.waitingThreads }}</div>
          <div class="stat-label">Max Daemon Threads</div>
        </div>
      </div>
    </div>

    <!-- Thread Activity Chart -->
    <div class="thread-chart-card mb-4">
      <div class="card-header">
        <h5 class="m-0">Active Threads Over Time</h5>
      </div>
      <div class="card-body">
        <div class="chart-container">
          <TimeSeriesLineGraph
              :data="threadActivityData"
              yAxisTitle="Active Threads"
              :loading="chartLoading"
              :visibleMinutes="15"
          />
        </div>
      </div>
    </div>

    <!-- Top Allocating Threads -->
    <div class="allocating-threads-card mb-4">
      <div class="card-header">
        <h5 class="m-0">Top 10 Allocators</h5>
      </div>
      <div class="card-body p-0">
        <div class="table-responsive">
          <table class="allocation-table">
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
                  <i class="bi bi-graph-up me-1"></i> Flame
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
import TimeSeriesLineGraph, {TimeSeriesDataPoint} from '@/components/TimeSeriesLineGraph.vue';
import {useRoute} from "vue-router";

const route = useRoute()

const projectId = route.params.projectId as string
const profileId = route.params.profileId as string

// State
const showThreadDetailModal = ref(false);
const selectedThread = ref<any>(null);
const chartLoading = ref<boolean>(true);
const threadActivityData = ref<TimeSeriesDataPoint[]>([]);

// Thread statistics
const threadStats = ref({
  totalThreads: 24,
  activeThreads: 18,
  blockedThreads: 2,
  waitingThreads: 4
});

// Sample data for top allocating threads
const topAllocatingThreads = ref([
  {name: 'main', allocatedBytes: 1073741824}, // 1 GiB
  {name: 'worker-pool-1', allocatedBytes: 536870912}, // 512 MiB
  {name: 'http-request-handler-3', allocatedBytes: 268435456}, // 256 MiB
  {name: 'async-executor-2', allocatedBytes: 134217728}, // 128 MiB
  {name: 'database-connection-pool-1', allocatedBytes: 67108864}, // 64 MiB
  {name: 'file-processor-thread', allocatedBytes: 33554432}, // 32 MiB
  {name: 'scheduler-1', allocatedBytes: 16777216}, // 16 MiB
  {name: 'cache-manager', allocatedBytes: 8388608}, // 8 MiB
  {name: 'metrics-collector', allocatedBytes: 4194304}, // 4 MiB
  {name: 'cleaner-daemon', allocatedBytes: 2097152}, // 2 MiB
]);

// Generate thread activity data for chart
const generateThreadActivityData = (durationInMinutes: number): void => {
  const data: TimeSeriesDataPoint[] = [];
  const secondsTotal = durationInMinutes * 60;

  // Start with a middle value representing the average active threads
  let value = threadStats.value.activeThreads;

  // Create data points at 1-second intervals with random walk
  for (let i = 0; i <= secondsTotal; i++) {
    // Random walk with bounds for more realistic looking data
    // Add some periodic patterns to make it more interesting
    const periodicComponent = 3 * Math.sin(i / 120) + 2 * Math.cos(i / 30);
    const randomComponent = Math.floor(Math.random() * 4) - 2;

    value += randomComponent + (periodicComponent / 10);
    value = Math.max(1, Math.min(threadStats.value.totalThreads, value)); // Keep within valid range

    data.push({
      time: i,
      value: Math.round(value)
    });
  }

  threadActivityData.value = data;
};


const closeThreadDetailModal = () => {
  showThreadDetailModal.value = false;
};

const viewThreadAllocationFlamegraph = (thread: any) => {
  // This is a placeholder for future implementation
  // In the future, this will open a modal with the thread's allocation flamegraph
  ToastService.info('profileToast', `Allocation flamegraph for thread ${thread.name} will be shown in the future`);
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

// Initialize thread activity chart data on component mount
onMounted(() => {
  // Show loading indicator
  chartLoading.value = true;

  // Generate data immediately - 3 hours of thread activity data
  generateThreadActivityData(180);

  // Simulate loading delay for UI feedback
  setTimeout(() => {
    // Set loading to false to show the chart
    chartLoading.value = false;
  }, 500);
});
</script>

<style scoped>
.threads-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.threads-container .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Modern Statistics Cards */
.statistics-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.stat-card {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  background-color: #fff;
  border-left: 4px solid transparent;
  text-align: center;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-primary {
  border-left-color: #5e64ff;
}

.stat-success {
  border-left-color: #28a745;
}

.stat-danger {
  border-left-color: #dc3545;
}

.stat-warning {
  border-left-color: #ffc107;
}

.stat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 1.8rem;
  font-weight: 600;
  line-height: 1.1;
  margin-bottom: 0.5rem;
}

.stat-label {
  color: #6c757d;
  font-size: 0.9rem;
  font-weight: 500;
}

/* Thread Activity Chart */
.thread-chart-card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.thread-chart-card .card-header {
  padding: 1.25rem;
  background-color: white;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.thread-chart-card .card-body {
  padding: 1rem;
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

/* Top Allocating Threads Table Styles */
.allocating-threads-card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  padding: 1.25rem;
  background-color: white;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.allocation-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.allocation-table th {
  padding: 1rem;
  font-size: 0.875rem;
  font-weight: 600;
  text-align: left;
  color: #495057;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.allocation-table th:last-child {
  text-align: right;
}

.allocation-row {
  transition: background-color 0.15s;
}

.allocation-row:hover {
  background-color: rgba(94, 100, 255, 0.03);
}

.allocation-row td {
  padding: 0.85rem 1rem;
  font-size: 0.875rem;
  border-bottom: 1px solid #f1f1f1;
  color: #495057;
}

.thread-name {
  font-weight: 500;
}

.allocation-value {
  text-align: right;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8125rem;
}

.empty-message {
  text-align: center;
  padding: 2rem;
  color: #6c757d;
}

.action-btn {
  min-width: 75px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  white-space: nowrap;
}

.cursor-pointer {
  cursor: pointer;
}

.modal-backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}

/* Make the table rows have a hover effect for better interaction */
.table-hover tbody tr:hover {
  background-color: rgba(63, 81, 181, 0.05);
}

/* Style pagination to match the design */
.pagination .page-item.active .page-link {
  background-color: #3f51b5;
  border-color: #3f51b5;
}

.pagination .page-link {
  color: #3f51b5;
}

/* Style the pre tag for stack traces */
pre {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8125rem;
  white-space: pre-wrap;
  word-break: break-all;
  margin-bottom: 0;
  max-height: 250px;
  overflow-y: auto;
}
</style>

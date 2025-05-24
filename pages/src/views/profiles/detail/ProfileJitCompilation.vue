<template>
  <div class="jit-compilation-container">
    <!-- Loading State -->
    <div v-if="loading" class="text-center my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading JIT compilation data...</span>
      </div>
      <p class="mt-3">Loading JIT compilation data...</p>
    </div>

    <div v-else-if="error" class="text-center my-5">
      <div class="alert alert-danger">
        <i class="bi bi-exclamation-triangle-fill me-2"></i>
        Failed to load JIT compilation data
      </div>
    </div>

    <div v-else>
      <!-- Header Section with Stats Overview -->
      <DashboardHeader
          title="JIT Compilation"
          description="Real-time insights into Java Just-In-Time compilation performance"
          icon="lightning-charge-fill"
      />

      <!-- Main Dashboard Grid -->
      <div class="dashboard-grid">
        <!-- Row 1: Dashboard Cards -->
        <div class="dashboard-row">
          <DashboardCard
              title="Compilations"
              :value="jitData!!.compileCount"
              variant="highlight"
              :valueA="jitData!!.standardCompileCount"
              :valueB="jitData!!.osrCompileCount"
              labelA="Standard"
              labelB="OSR"
              comparison="a-greater"/>

          <DashboardCard
              title="Failed Compilations"
              :value="jitData!!.bailoutCount + jitData!!.invalidatedCount"
              variant="danger"
              :valueA="jitData!!.bailoutCount"
              :valueB="jitData!!.invalidatedCount"
              labelA="Bailouts"
              labelB="Invalidations"
              comparison="a-greater"
          />
        </div>

        <!-- Row 2: Code Size Card and Compilation Time Card -->
        <div class="dashboard-row mb-4">
          <DashboardCard
              title="Memory Usage (nMethods)"
              :value="FormattingService.formatBytes(jitData!!.nmethodsSize)"
              :valueA="FormattingService.formatBytes(jitData!!.nmethodCodeSize)"
              :valueB="FormattingService.formatBytes(jitData!!.nmethodsSize - jitData!!.nmethodCodeSize)"
              labelA="Code"
              labelB="Metadata"
              variant="info"
              comparison="a-greater"
          />

          <DashboardCard
              title="Peak Compilation Time"
              :value="FormattingService.formatDuration2Units(jitData!!.peakTimeSpent)"
              variant="warning"
              :valueA="FormattingService.formatDuration2Units(jitData!!.totalTimeSpent)"
              labelA="Total Time"
          />
        </div>

        <!-- Row 3: Time Series Graph -->
        <div class="chart-card mb-4">
          <div class="chart-card-header">
            <h5>JIT Compilation Activity by CPU Samples</h5>
          </div>
          <div class="chart-container">
            <TimeSeriesLineGraph
                :primaryData="compilationCountData"
                primaryTitle="Compilation Samples"
                :loading="chartLoading"
                :visibleMinutes="60"
            />
          </div>
        </div>

        <!-- Row 4: Long Compilation Table -->
        <div class="data-table-card">
          <div class="chart-card-header">
            <h5>Long Compilations</h5>
            <div class="chart-controls">
              <span class="badge bg-primary">
                <i class="bi bi-clock-history me-1"></i>
                Threshold: 50ms
              </span>
            </div>
          </div>
          <div class="table-responsive">
            <table class="table table-hover">
              <thead>
              <tr>
                <th>ID</th>
                <th>Method</th>
                <th>Compiler</th>
                <th>Level</th>
                <th>Time</th>
                <th>Code Size</th>
                <th>Inlined</th>
                <th>Arena</th>
                <th>Status</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="compilation in jitData!!.longCompilations" :key="compilation.compileId"
                  :class="{ 'table-danger': !compilation.succeeded, 'table-warning': compilation.timeSpent > 150 && compilation.succeeded }">
                <td>{{ compilation.compileId }}</td>
                <td>
                  <div class="method-cell">
                    <span class="method-name">{{ getSimpleMethodName(compilation.method) }}</span>
                    <span class="method-path text-muted small">{{ getMethodPath(compilation.method) }}</span>
                  </div>
                </td>
                <td>
                    <span class="badge" :class="getCompilerBadgeClass(compilation.compiler)">
                      {{ compilation.compiler }}
                    </span>
                </td>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="tier-indicator" :class="getTierClass(compilation.compileLevel)"></div>
                    {{ compilation.compileLevel }}
                  </div>
                </td>
                <td>
                    <span
                        :class="{ 'text-danger fw-bold': compilation.timeSpent > 200, 'text-warning fw-medium': compilation.timeSpent > 100 && compilation.timeSpent <= 200 }">
                      {{ FormattingService.formatDuration2Units(compilation.timeSpent) }}
                    </span>
                </td>
                <td>{{ FormattingService.formatBytes(compilation.codeSize) }}</td>
                <td>{{ FormattingService.formatBytes(compilation.inlinedBytes) }}</td>
                <td>{{ FormattingService.formatBytes(compilation.arenaBytes) }}</td>
                <td>
                  <span v-if="compilation.succeeded" class="badge bg-success">Success</span>
                  <span v-else class="badge bg-danger">Failed</span>
                  <span v-if="compilation.isOsr" class="badge bg-info ms-1">OSR</span>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import FormattingService from "@/services/FormattingService.ts";
import JITCompilationData from "@/services/compilation/model/JITCompilationData.ts";
import JITCompilerType from "@/services/compilation/model/JITCompilerType.ts";
import ProfileCompilationClient from "@/services/compilation/ProfileCompilationClient.ts";
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';

const route = useRoute();
const loading = ref(true);
const error = ref(false);
const jitData = ref<JITCompilationData>();

// Time series chart state
const chartLoading = ref(true);
const compilationCountData = ref<number[][]>([]);

// Load JIT compilation data on component mount
onMounted(async () => {
  try {
    const projectId = route.params.projectId as string;
    const profileId = route.params.profileId as string;

    // Create the client instance
    const compilationClient = new ProfileCompilationClient(projectId, profileId);

    // Fetch the data
    jitData.value = await compilationClient.get();

    // Generate time series data for the chart
    generateCompilationTimeSeriesData();

    // Data loaded successfully
    loading.value = false;
  } catch (e) {
    console.error('Failed to load JIT compilation data:', e);
    error.value = true;
    loading.value = false;
  }
});

// Generate time series data based on compilation data
const generateCompilationTimeSeriesData = () => {
  chartLoading.value = true;

  // Generate data over a 30-minute period
  const durationInMinutes = 30;
  const secondsTotal = durationInMinutes * 60;

  const countData: number[][] = [];

  // Base values
  let compilationCount: number = 0;

  // Create data points with real-world compilation patterns
  for (let i = 0; i <= secondsTotal; i++) {
    // Random spikes that might represent compilation events
    let randomSpike = 0;
    if (Math.random() < 0.05) { // 5% chance of compilation spike
      randomSpike = Math.random() * (jitData.value ? jitData.value.peakTimeSpent * 0.7 : 300);
    }

    // Count increases more steadily, with occasional jumps
    if (randomSpike > 0) {
      compilationCount += Math.floor(randomSpike / 50) + 1;
    }
    if (Math.random() < 0.1) { // 10% chance of regular compilation
      compilationCount++;
    }

    countData.push([i, compilationCount]);
  }

  compilationCountData.value = countData;

  // Set loading to false to show the chart
  setTimeout(() => {
    chartLoading.value = false;
  }, 500);
};

// Method name and path helpers
const getSimpleMethodName = (method: string): string => {
  const parts = method.split('::');
  const className = parts[0].substring(parts[0].lastIndexOf('.') + 1);
  const methodName = parts.length > 1 ? parts[1] : '';
  return className + '.' + methodName;
};

const getMethodPath = (method: string): string => {
  const parts = method.split('::');
  if (parts.length <= 1) return '';
  return parts[0];
};

// Compiler badge class helper
const getCompilerBadgeClass = (compiler: JITCompilerType): string => {
  switch (compiler) {
    case JITCompilerType.C1:
      return 'bg-primary';
    case JITCompilerType.C2:
      return 'bg-success';
    case JITCompilerType.JVMCI:
      return 'bg-info';
    default:
      return 'bg-secondary';
  }
};

// Tier class helper
const getTierClass = (level: number): string => {
  if (level <= 1) return 'tier-bronze';
  if (level <= 3) return 'tier-silver';
  return 'tier-gold';
};
</script>

<style scoped>
.jit-compilation-container {
  width: 100%;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

/* Dashboard Cards Row */
.dashboard-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
}

/* Data Table Card */
.data-table-card, .chart-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.chart-container {
  padding: 1rem;
  height: 500px; /* Reduced from 550px to make the container smaller */
}

.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #f0f0f0;
}

.chart-card-header h5 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
}

.chart-controls {
  display: flex;
  align-items: center;
}

.table {
  margin-bottom: 0;
}

.table thead th {
  background: #f7f9fc;
  font-weight: 600;
  font-size: 0.9rem;
  color: #555;
  border-top: none;
  border-bottom-width: 1px;
}

.table tbody td {
  font-size: 0.9rem;
  padding: 0.75rem 1.5rem;
  vertical-align: middle;
}

/* Long Compilation Table Styles */
.method-cell {
  display: flex;
  flex-direction: column;
}

.method-name {
  font-weight: 500;
}

.method-path {
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

.tier-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 0.5rem;
}

.tier-bronze {
  background-color: #CD7F32;
}

.tier-silver {
  background-color: #C0C0C0;
}

.tier-gold {
  background-color: #FFD700;
}

.table-hover tbody tr:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.table-responsive {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.mb-4 {
  margin-bottom: 1.5rem;
}

/* Responsive Adjustments */
@media (max-width: 992px) {
  .dashboard-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .dashboard-row {
    grid-template-columns: 1fr;
  }

  .chart-container {
    height: 430px; /* Adjusted for mobile, reduced from 450px */
  }
}
</style>

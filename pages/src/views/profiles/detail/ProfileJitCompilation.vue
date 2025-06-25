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
              :value="statisticsData!!.compileCount"
              variant="highlight"
              :valueA="statisticsData!!.standardCompileCount"
              :valueB="statisticsData!!.osrCompileCount"
              labelA="Standard"
              labelB="OSR"
              comparison="a-greater"/>

          <DashboardCard
              title="Failed Compilations"
              :value="statisticsData!!.bailoutCount + statisticsData!!.invalidatedCount"
              variant="danger"
              :valueA="statisticsData!!.bailoutCount"
              :valueB="statisticsData!!.invalidatedCount"
              labelA="Bailouts"
              labelB="Invalidations"
              comparison="a-greater"
          />
        </div>

        <!-- Row 2: Code Size Card and Compilation Time Card -->
        <div class="dashboard-row mb-4">
          <DashboardCard
              title="Memory Usage (nMethods)"
              :value="FormattingService.formatBytes(statisticsData!!.nmethodsSize)"
              :valueA="FormattingService.formatBytes(statisticsData!!.nmethodCodeSize)"
              :valueB="FormattingService.formatBytes(statisticsData!!.nmethodsSize - statisticsData!!.nmethodCodeSize)"
              labelA="Code"
              labelB="Metadata"
              variant="info"
              comparison="a-greater"
          />

          <DashboardCard
              title="Peak Compilation Time"
              :value="FormattingService.formatDuration2Units(statisticsData!!.peakTimeSpent)"
              variant="warning"
              :valueA="FormattingService.formatDuration2Units(statisticsData!!.totalTimeSpent)"
              labelA="Total Time"
          />
        </div>

        <!-- Row 3: Time Series Graph -->
        <div class="chart-card mb-4">
          <div class="chart-card-header">
            <h5>JIT Compilation Activity by CPU Samples</h5>
          </div>
          <div class="chart-container">
            <ApexTimeSeriesChart
                :primaryData="timeseriesData?.data"
                :primaryTitle="timeseriesData?.name"
                :visibleMinutes="15" />
          </div>
        </div>

        <!-- Row 4: Long Compilation Table -->
        <div class="data-table-card">
          <div class="chart-card-header">
            <h5>Long Compilations</h5>
            <div class="chart-controls">
              <span class="badge bg-primary">
                <i class="bi bi-clock-history me-1"></i>
                Threshold: {{ statisticsData?.compileMethodThreshold }}ms
              </span>
            </div>
          </div>
          <div class="table-responsive">
            <table class="table table-hover">
              <thead>
              <tr>
                <th>ID</th>
                <th>Method</th>
                <th>Level</th>
                <th>Time</th>
                <th>Code Size</th>
                <th>Status</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="compilation in compilationsData" :key="compilation.compileId"
                  :class="{ 'table-danger': !compilation.succeded }"
                  :title="compilation.method">
                <td>{{ compilation.compileId }}</td>
                <td>
                  <div class="method-cell">
                    <div class="d-flex align-items-center gap-2 mb-1">
                      <span class="method-name">{{ getClassMethodName(compilation.method) }}</span>
                      <span class="badge badge-primary-opacity-50">
                        {{ compilation.compiler }}
                      </span>
                      <span v-if="compilation.isOsr" class="badge badge-info-opacity-50">
                        OSR
                      </span>
                    </div>
                    <span class="method-path text-muted small">{{ getPackage(compilation.method) }}</span>
                  </div>
                </td>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="tier-indicator" :class="getTierClass(compilation.compileLevel)"></div>
                    {{ compilation.compileLevel }}
                  </div>
                </td>
                <td>{{ FormattingService.formatDuration2Units(compilation.duration) }}</td>
                <td>{{ FormattingService.formatBytes(compilation.codeSize) }}</td>
                <td>
                  <span v-if="compilation.succeded" class="badge bg-success">Success</span>
                  <span v-else class="badge bg-danger">Failed</span>
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
import ProfileCompilationClient from "@/services/compilation/ProfileCompilationClient.ts";
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import Serie from "@/services/timeseries/model/Serie.ts";
import JITLongCompilation from "@/services/compilation/model/JITLongCompilation.ts";

const route = useRoute();
const loading = ref(true);
const error = ref(false);
const statisticsData = ref<JITCompilationData>();
const compilationsData = ref<JITLongCompilation[]>([]);

// Time series chart state
const chartLoading = ref(true);
const timeseriesData = ref<Serie>();

// Load JIT compilation data on component mount
onMounted(async () => {
  try {
    const projectId = route.params.projectId as string;
    const profileId = route.params.profileId as string;

    // Create the client instance
    const compilationClient = new ProfileCompilationClient(projectId, profileId);

    // Fetch all data sets in parallel
    const [statisticsDataResult, timeseriesDataResult, compilationsDataResult] = await Promise.all([
      compilationClient.getStatistics(),
      compilationClient.getTimeseries(),
      compilationClient.getCompilations()
    ]);

    // Update the component state with real data
    statisticsData.value = statisticsDataResult;
    timeseriesData.value = timeseriesDataResult;
    compilationsData.value = compilationsDataResult;
    
    // Data loaded successfully
    loading.value = false;
    chartLoading.value = false;
  } catch (e) {
    console.error('Failed to load JIT compilation data:', e);
    error.value = true;
    loading.value = false;
    chartLoading.value = false;
  }
});

// Method name and path helpers
const getClassMethodName = (method: string): string => {
  if (!method) return '';

  // Extract the method name with parameters
  const lastDotIndex = method.lastIndexOf('#');
  if (lastDotIndex === -1) return method;

  // Get the part after the last dot (method name with params)
  const methodNameWithParams = method.substring(lastDotIndex + 1);

  // Get the class path (everything before the method)
  const packagePath = method.substring(0, lastDotIndex);

  // Get only the class name (last segment before the method)
  const lastClassDotIndex = packagePath.lastIndexOf('.');
  const className = lastClassDotIndex !== -1 ?
    packagePath.substring(lastClassDotIndex + 1) :
    packagePath;

  return `${className}.${methodNameWithParams}`;
};

const getPackage = (method: string): string => {
  if (!method) return '';

  // Extract the package path (everything up to the last two segments)
  const segments = method.split('.');
  if (segments.length <= 1) return method;

  // Return everything except the last two segments (class and method)
  return segments.slice(0, segments.length - 1).join('.');
};

// Tier class helper
const getTierClass = (level: number): string => {
  if (level <= 1) return 'tier-bronze';
  if (level <= 3) return 'tier-silver';
  if (level === 4) return 'tier-green';
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
  height: 550px;
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

.tier-green {
  background-color: #4CAF50;
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

/* Primary opacity badge */
.badge-primary-opacity-50 {
  background-color: rgba(13, 110, 253, 0.1);
  color: #0d6efd;
  font-weight: 500;
  font-size: 0.75rem;
}

/* OSR badge with opacity */
.badge-info-opacity-50 {
  background-color: rgba(25, 135, 84, 0.1);
  color: #198754;
  font-weight: 500;
  font-size: 0.75rem;
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
    height: 430px;
  }
}
</style>

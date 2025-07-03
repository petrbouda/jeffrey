<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading heap memory data...</span>
    </div>
    <p class="mt-2">Loading heap memory data...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load heap memory data
    </div>
  </div>

  <div v-else>
      <!-- Header Section -->
      <DashboardHeader
          title="Heap Memory Analysis"
          description="Real-time heap memory usage, allocation patterns, and memory pool analysis"
          icon="memory"
      >
        <template #actions>
          <div class="d-flex gap-2">
            <select v-model="selectedTimeRange" @change="updateCharts" class="form-select form-select-sm">
              <option value="1h">Last Hour</option>
              <option value="6h">Last 6 Hours</option>
              <option value="24h">Last 24 Hours</option>
              <option value="all">All Data</option>
            </select>
            <button class="btn btn-sm btn-outline-primary" @click="refreshData">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
        </template>
      </DashboardHeader>

      <!-- Key Metrics Row -->
      <div class="metrics-grid mb-4">
        <StatCard
          title="Current Heap Usage"
          :value="heapMetrics.currentUsage"
          icon="hdd-stack"
          variant="primary"
        />

        <StatCard
          title="Allocation Rate"
          :value="allocationMetrics.rate"
          icon="arrow-up-circle"
          variant="success"
        />

        <StatCard
          title="GC Frequency"
          :value="gcMetrics.frequency"
          icon="recycle"
          variant="info"
        />
      </div>

      <!-- Chart Section with Tabs -->
      <ChartSectionWithTabs
        :full-width="true"
        :tabs="heapAnalysisTabs"
        @tab-change="onHeapAnalysisTabChange"
      >
        <!-- Memory Timeline Tab -->
        <template #timeline>
          <ApexTimeSeriesChart
            :primary-data="memoryTimelineData"
            primary-title="Heap Usage"
            primary-axis-type="bytes"
            :visible-minutes="60"
          />
        </template>

        <!-- Memory Pools Tab -->
        <template #pools>
          <div class="memory-pools-container">
            <div class="pools-chart-container">
              <h6 class="chart-title">
                <i class="bi bi-arrow-repeat me-2"></i>Young Generation (Eden + Survivor)
              </h6>
              <ApexTimeSeriesChart
                :primary-data="youngGenerationData.edenData"
                :secondary-data="youngGenerationData.survivorData"
                primary-title="Eden Space"
                secondary-title="Survivor Space"
                primary-axis-type="bytes"
                secondary-axis-type="bytes"
                :visible-minutes="60"
                :stacked="true"
              />
            </div>
            
            <div class="pools-chart-container">
              <h6 class="chart-title">
                <i class="bi bi-archive me-2"></i>Old Generation
              </h6>
              <ApexTimeSeriesChart
                :primary-data="oldGenerationData"
                primary-title="Old Generation"
                primary-axis-type="bytes"
                :visible-minutes="60"
              />
            </div>
            
            <!-- Current Pool Status -->
            <div class="pools-status-section mt-4">
              <h6 class="mb-3">
                <i class="bi bi-speedometer2 me-2"></i>Latest Memory Pool Usage
              </h6>
              <div class="row">
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Eden Space</label>
                    <div class="stat-value">{{ currentPoolStatus.eden.used }}</div>
                    <div class="progress mt-1" style="height: 6px;">
                      <div class="progress-bar bg-success" 
                           :style="{ width: currentPoolStatus.eden.percentage + '%' }">
                      </div>
                    </div>
                    <small class="text-muted">{{ currentPoolStatus.eden.percentage }}% of {{ currentPoolStatus.eden.capacity }}</small>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Survivor Space</label>
                    <div class="stat-value">{{ currentPoolStatus.survivor.used }}</div>
                    <div class="progress mt-1" style="height: 6px;">
                      <div class="progress-bar bg-info" 
                           :style="{ width: currentPoolStatus.survivor.percentage + '%' }">
                      </div>
                    </div>
                    <small class="text-muted">{{ currentPoolStatus.survivor.percentage }}% of {{ currentPoolStatus.survivor.capacity }}</small>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Old Generation</label>
                    <div class="stat-value">{{ currentPoolStatus.oldGen.used }}</div>
                    <div class="progress mt-1" style="height: 6px;">
                      <div class="progress-bar bg-warning" 
                           :style="{ width: currentPoolStatus.oldGen.percentage + '%' }">
                      </div>
                    </div>
                    <small class="text-muted">{{ currentPoolStatus.oldGen.percentage }}% of {{ currentPoolStatus.oldGen.capacity }}</small>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- Allocation Patterns Tab -->
        <template #allocation>
          <ApexTimeSeriesChart
            :primary-data="allocationTimelineData"
            primary-title="Allocation Rate"
            primary-axis-type="bytes"
            :visible-minutes="60"
            primary-color="#28a745"
          />
          
          <!-- Allocation Statistics -->
          <div class="allocation-stats-section mt-4">
            <h6 class="mb-3">
              <i class="bi bi-graph-up me-2"></i>Allocation Statistics
            </h6>
            <div class="row">
              <div class="col-md-3">
                <div class="stat-item mb-3">
                  <label class="stat-label">Peak Rate</label>
                  <div class="stat-value">{{ allocationStats.peakRate }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="stat-item mb-3">
                  <label class="stat-label">Average Rate</label>
                  <div class="stat-value">{{ allocationStats.avgRate }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="stat-item mb-3">
                  <label class="stat-label">Objects Allocated</label>
                  <div class="stat-value">{{ allocationStats.objectCount }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="stat-item mb-3">
                  <label class="stat-label">Total Allocated</label>
                  <div class="stat-value">{{ allocationMetrics.total }}</div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </ChartSectionWithTabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick, computed } from 'vue';
import { useRoute } from 'vue-router';
import StatCard from '@/components/StatCard.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import DashboardHeader from '@/components/DashboardHeader.vue';

const route = useRoute();
const loading = ref(true);
const error = ref(false);
const selectedTimeRange = ref('6h');

// Memory pools data for ApexTimeSeriesChart
const youngGenerationData = ref({
  edenData: [] as number[][],
  survivorData: [] as number[][]
});
const oldGenerationData = ref([] as number[][]);
const memoryTimelineData = ref([] as number[][]);
const allocationTimelineData = ref([] as number[][]);

// Heap metrics
const heapMetrics = ref({
  currentUsage: '1.8 GB',
  usagePercentage: '72',
  maxHeap: '2.5 GB'
});

const allocationMetrics = ref({
  rate: '42.3 MB/s',
  total: '15.2 GB'
});


const gcMetrics = ref({
  frequency: '2.1/min',
  totalCollections: '1,234'
});

// Current pool status for the summary cards
const currentPoolStatus = ref({
  eden: {
    used: '245 MB',
    capacity: '512 MB',
    percentage: 48
  },
  survivor: {
    used: '32 MB',
    capacity: '64 MB',
    percentage: 50
  },
  oldGen: {
    used: '1.5 GB',
    capacity: '1.8 GB',
    percentage: 83
  }
});

// Allocation statistics
const allocationStats = ref({
  peakRate: '156.7 MB/s',
  peakTime: '14:23:45',
  avgRate: '42.3 MB/s',
  objectCount: '2.4M',
  pressure: 'Medium',
  pressureLevel: 65
});

// Heap analysis tabs configuration
const heapAnalysisTabs = computed(() => [
  { id: 'timeline', label: 'Memory Timeline', icon: 'graph-up' },
  { id: 'pools', label: 'Memory Pools', icon: 'layers' },
  { id: 'allocation', label: 'Allocation Patterns', icon: 'activity' }
]);

// Handle heap analysis tab changes
const onHeapAnalysisTabChange = (tabIndex: number, tab: any) => {
  console.log(`Switching to heap analysis tab: ${tab.label}`);
  // Additional logic for tab changes if needed
};

// Generate memory usage timeline data
const generateMemoryUsageData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  const interval = timeRange === '1h' ? 30 * 1000 : 5 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const heapData = [];
  const allocationData = [];
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate heap usage with realistic patterns
    const baseHeap = 1500 + Math.sin(i * 0.1) * 200;
    const gcVariation = i % 20 === 0 ? -300 : 0; // GC drops
    const heapUsage = Math.max(800, baseHeap + gcVariation + Math.random() * 100);
    
    // Simulate allocation rate
    const baseAllocation = 40 + Math.sin(i * 0.05) * 20;
    const allocationRate = Math.max(5, baseAllocation + Math.random() * 30);
    
    heapData.push([timestamp, Math.round(heapUsage)]);
    allocationData.push([timestamp, Math.round(allocationRate * 10) / 10]);
  }
  
  return { heapData, allocationData };
};

// Generate memory pools timeline data
const generateMemoryPoolsData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  const interval = timeRange === '1h' ? 30 * 1000 : 5 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const edenData = [];
  const survivorData = [];
  const oldGenData = [];
  
  let currentEden = 100;
  let currentSurvivor = 20;
  let currentOldGen = 600;
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate realistic memory pool behavior
    if (i % 15 === 0) {
      // Minor GC - Eden drops, Survivor and Old Gen may increase
      currentEden = Math.max(10, currentEden * 0.1);
      currentSurvivor = Math.min(60, currentSurvivor + Math.random() * 20);
      currentOldGen = Math.min(1000, currentOldGen + Math.random() * 50);
    } else if (i % 40 === 0) {
      // Major GC - Old Gen drops significantly
      currentOldGen = Math.max(300, currentOldGen * 0.4);
      currentSurvivor = Math.max(5, currentSurvivor * 0.3);
    } else {
      // Normal allocation
      currentEden = Math.min(250, currentEden + Math.random() * 25);
      currentSurvivor += (Math.random() - 0.5) * 5;
      currentOldGen += Math.random() * 10;
    }
    
    // Keep values in reasonable bounds
    currentEden = Math.max(10, Math.min(250, currentEden));
    currentSurvivor = Math.max(5, Math.min(60, currentSurvivor));
    currentOldGen = Math.max(200, Math.min(1500, currentOldGen));
    
    edenData.push([timestamp, Math.round(currentEden)]);
    survivorData.push([timestamp, Math.round(currentSurvivor)]);
    oldGenData.push([timestamp, Math.round(currentOldGen)]);
  }
  
  return { edenData, survivorData, oldGenData };
};

// Generate memory timeline data for ApexTimeSeriesChart
const generateMemoryTimelineData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  const interval = timeRange === '1h' ? 30 * 1000 : 5 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const heapData = [];
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate heap usage with realistic patterns
    const baseHeap = 1500 + Math.sin(i * 0.1) * 200;
    const gcVariation = i % 20 === 0 ? -300 : 0; // GC drops
    const heapUsage = Math.max(800, baseHeap + gcVariation + Math.random() * 100);
    
    // Convert timestamp to seconds and heap usage to bytes for ApexTimeSeriesChart
    heapData.push([timestamp / 1000, Math.round(heapUsage) * 1024 * 1024]);
  }
  
  return heapData;
};

// Generate allocation timeline data for ApexTimeSeriesChart
const generateAllocationTimelineData = (timeRange: string) => {
  const { allocationData } = generateMemoryUsageData(timeRange);
  
  // Convert to ApexTimeSeriesChart format (seconds and bytes per second)
  return allocationData.map(([timestamp, rateMBs]) => [
    timestamp / 1000, // Convert to seconds
    rateMBs * 1024 * 1024 // Convert MB/s to bytes/s
  ]);
};


// Generate and update memory pools data for ApexTimeSeriesChart
const updateMemoryPoolsData = () => {
  const { edenData, survivorData, oldGenData } = generateMemoryPoolsData(selectedTimeRange.value);
  
  // Convert to seconds for ApexTimeSeriesChart format and convert MB to bytes
  youngGenerationData.value.edenData = edenData.map(([timestamp, valueMB]) => [
    timestamp / 1000, // Convert to seconds
    valueMB * 1024 * 1024 // Convert MB to bytes
  ]);
  
  youngGenerationData.value.survivorData = survivorData.map(([timestamp, valueMB]) => [
    timestamp / 1000, // Convert to seconds
    valueMB * 1024 * 1024 // Convert MB to bytes
  ]);
  
  oldGenerationData.value = oldGenData.map(([timestamp, valueMB]) => [
    timestamp / 1000, // Convert to seconds
    valueMB * 1024 * 1024 // Convert MB to bytes
  ]);
};



// Update all charts
const updateCharts = () => {
  // Update memory timeline data
  memoryTimelineData.value = generateMemoryTimelineData(selectedTimeRange.value);
  
  // Update allocation timeline data
  allocationTimelineData.value = generateAllocationTimelineData(selectedTimeRange.value);
  
  updateMemoryPoolsData();
};

// Refresh data
const refreshData = () => {
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    updateCharts();
  }, 800);
};

// Load data on component mount
onMounted(async () => {
  try {
    const projectId = route.params.projectId as string;
    const profileId = route.params.profileId as string;
    
    console.log(`Loading heap memory data for project ${projectId}, profile ${profileId}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    loading.value = false;
    
    // Create charts after loading
    await nextTick();
    updateCharts();
  } catch (err) {
    console.error('Failed to load heap memory data:', err);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.loading-overlay, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* Metrics Grid */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}


/* Charts */
.chart-container {
  height: 400px;
  width: 100%;
}

/* Memory Pools */
.memory-pools-container {
  width: 100%;
}

.pools-header {
  display: flex;
  justify-content: between;
  align-items: center;
}

.pools-controls {
  margin-left: auto;
}

.pools-chart-container {
  width: 100%;
  margin-bottom: 1rem;
}

.chart-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e9ecef;
}

.pools-status-section {
  background-color: #f8f9fa;
  border-radius: 8px;
  padding: 1rem;
  border-top: 1px solid #e9ecef;
}

/* Allocation Stats */
.allocation-stats-section {
  background-color: #f8f9fa;
  border-radius: 8px;
  padding: 1rem;
  border-top: 1px solid #e9ecef;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-item .stat-label {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-item .stat-value {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.badge {
  font-size: 0.7rem;
  padding: 0.25em 0.5em;
}

/* Override ApexTimeSeriesChart colors for Memory Pools */
.pools-chart-container .graph-title-icon:first-child {
  background-color: #198754 !important; /* Eden Space - Green */
}

.pools-chart-container .graph-title-icon:nth-child(2) {
  background-color: #0dcaf0 !important; /* Survivor Space - Cyan */
}

.pools-chart-container:has(.graph-title-item:only-child) .graph-title-icon {
  background-color: #ffc107 !important; /* Old Generation - Yellow */
}

/* Responsive Design */
@media (max-width: 992px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .pools-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .pools-controls {
    margin-left: 0;
  }
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-container,
  .pools-chart-container {
    height: 300px;
  }
  
}
</style>

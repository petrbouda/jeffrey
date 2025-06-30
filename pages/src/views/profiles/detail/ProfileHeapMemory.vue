<template>
  <div class="heap-memory-container">
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

    <div v-else class="heap-content">
      <!-- Header Section -->
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h4 class="mb-1 d-flex align-items-center">
            <i class="bi bi-memory me-2 text-primary"></i>
            Heap Memory Analysis
          </h4>
          <p class="text-muted mb-0 small">Real-time heap memory usage, allocation patterns, and memory pool analysis</p>
        </div>
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
      </div>

      <!-- Key Metrics Row -->
      <div class="metrics-grid mb-4">
        <MetricCard
          icon="bi bi-hdd-stack"
          icon-class="bg-primary-soft"
          :value="heapMetrics.currentUsage"
          :details="`${heapMetrics.usagePercentage}% of ${heapMetrics.maxHeap}`"
          label="Current Heap Usage"
        />

        <MetricCard
          icon="bi bi-arrow-up-circle"
          icon-class="bg-success-soft"
          :value="allocationMetrics.rate"
          :details="`${allocationMetrics.total} total allocated`"
          label="Allocation Rate"
        />

        <MetricCard
          icon="bi bi-exclamation-triangle"
          icon-class="bg-warning-soft"
          :value="memoryPressure.level"
          :details="memoryPressure.description"
          label="Memory Pressure"
        />

        <MetricCard
          icon="bi bi-database"
          icon-class="bg-info-soft"
          :value="metaspaceMetrics.usage"
          :details="`${metaspaceMetrics.capacity} capacity`"
          label="Metaspace Usage"
        />
      </div>

      <!-- Tabbed Content -->
      <div class="dashboard-tabs mb-4">
        <ul class="nav nav-tabs" role="tablist">
          <li class="nav-item" role="presentation">
            <button class="nav-link active" id="usage-timeline-tab" data-bs-toggle="tab" data-bs-target="#usage-timeline-pane" type="button" role="tab" aria-controls="usage-timeline-pane" aria-selected="true">
              <i class="bi bi-graph-up me-2"></i>Memory Timeline
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="memory-pools-tab" data-bs-toggle="tab" data-bs-target="#memory-pools-pane" type="button" role="tab" aria-controls="memory-pools-pane" aria-selected="false">
              <i class="bi bi-layers me-2"></i>Memory Pools
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="allocation-patterns-tab" data-bs-toggle="tab" data-bs-target="#allocation-patterns-pane" type="button" role="tab" aria-controls="allocation-patterns-pane" aria-selected="false">
              <i class="bi bi-activity me-2"></i>Allocation Patterns
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="metaspace-tab" data-bs-toggle="tab" data-bs-target="#metaspace-pane" type="button" role="tab" aria-controls="metaspace-pane" aria-selected="false">
              <i class="bi bi-database me-2"></i>Metaspace
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="code-cache-tab" data-bs-toggle="tab" data-bs-target="#code-cache-pane" type="button" role="tab" aria-controls="code-cache-pane" aria-selected="false">
              <i class="bi bi-cpu me-2"></i>Code Cache
            </button>
          </li>
        </ul>

        <div class="tab-content">
          <!-- Memory Timeline Tab -->
          <div class="tab-pane fade show active" id="usage-timeline-pane" role="tabpanel" aria-labelledby="usage-timeline-tab" tabindex="0">
            <div class="chart-container">
              <div id="memory-usage-chart"></div>
            </div>
          </div>

          <!-- Memory Pools Tab -->
          <div class="tab-pane fade" id="memory-pools-pane" role="tabpanel" aria-labelledby="memory-pools-tab" tabindex="0">
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
          </div>

          <!-- Allocation Patterns Tab -->
          <div class="tab-pane fade" id="allocation-patterns-pane" role="tabpanel" aria-labelledby="allocation-patterns-tab" tabindex="0">
            <div class="chart-container">
              <div id="allocation-timeline-chart"></div>
            </div>
            
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
          </div>

          <!-- Metaspace Tab -->
          <div class="tab-pane fade" id="metaspace-pane" role="tabpanel" aria-labelledby="metaspace-tab" tabindex="0">
            <div class="chart-container">
              <div id="metaspace-usage-chart"></div>
            </div>
            
            <!-- Metaspace Statistics -->
            <div class="metaspace-stats-section mt-4">
              <h6 class="mb-3">
                <i class="bi bi-bar-chart-fill me-2"></i>Metaspace Statistics
              </h6>
              <div class="row">
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Latest Usage</label>
                    <div class="stat-value">{{ metaspaceStats.currentUsage }}</div>
                    <div class="progress mt-1" style="height: 6px;">
                      <div class="progress-bar" 
                           :class="getMetaspaceProgressClass(metaspaceStats.usagePercentage)"
                           :style="{ width: metaspaceStats.usagePercentage + '%' }">
                      </div>
                    </div>
                    <small class="text-muted">{{ metaspaceStats.usagePercentage }}% of {{ metaspaceStats.capacity }}</small>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Latest Committed Memory</label>
                    <div class="stat-value">{{ metaspaceStats.committed }}</div>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item mb-3">
                    <label class="stat-label">Latest Reserved Memory</label>
                    <div class="stat-value">{{ metaspaceStats.reserved }}</div>
                  </div>
                </div>
              </div>
              <div class="row">
                <div class="col-md-4">
                  <div class="stat-item">
                    <label class="stat-label">Loaded Classes</label>
                    <div class="stat-value">{{ metaspaceStats.classesLoaded }}</div>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item">
                    <label class="stat-label">Unloaded Classes</label>
                    <div class="stat-value">{{ metaspaceStats.classesUnloaded }}</div>
                  </div>
                </div>
                <div class="col-md-4">
                  <div class="stat-item">
                    <label class="stat-label">Class Loading Rate</label>
                    <div class="stat-value">{{ metaspaceStats.loadingRate }}</div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- Metaspace Events Table -->
            <div class="metaspace-events mt-4">
              <h6 class="mb-3">
                <i class="bi bi-exclamation-triangle me-2"></i>Recent Metaspace Events
              </h6>
              <div class="table-responsive">
                <table class="table table-sm table-hover">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Event Type</th>
                      <th>Classes Affected</th>
                      <th>Memory Change</th>
                      <th>Trigger</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="event in metaspaceEvents" :key="event.id">
                      <td>{{ formatTimestamp(event.timestamp) }}</td>
                      <td>
                        <span class="badge" :class="getMetaspaceEventBadgeClass(event.type)">
                          {{ event.type }}
                        </span>
                      </td>
                      <td>{{ event.classesAffected }}</td>
                      <td>
                        <span :class="event.memoryChange.startsWith('+') ? 'text-danger' : 'text-success'">
                          {{ event.memoryChange }}
                        </span>
                      </td>
                      <td>{{ event.trigger }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <!-- Code Cache Tab -->
          <div class="tab-pane fade" id="code-cache-pane" role="tabpanel" aria-labelledby="code-cache-tab" tabindex="0">
            <div class="row">
              <div class="col-md-6">
                <div class="chart-container">
                  <div id="code-cache-chart"></div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="code-cache-details">
                  <h6 class="mb-3">Code Cache Status</h6>
                  <div class="cache-region" v-for="region in codeCacheRegions" :key="region.name">
                    <div class="region-title">
                      <strong>{{ region.name }}</strong>
                      <span class="badge" :class="region.statusClass">{{ region.status }}</span>
                    </div>
                    <div class="region-stats mt-2">
                      <div class="stat-row">
                        <span>Size:</span>
                        <span>{{ region.size }}</span>
                      </div>
                      <div class="stat-row">
                        <span>Used:</span>
                        <span>{{ region.used }}</span>
                      </div>
                      <div class="stat-row">
                        <span>Free:</span>
                        <span>{{ region.free }}</span>
                      </div>
                    </div>
                    <div class="progress mt-2" style="height: 6px;">
                      <div class="progress-bar bg-info" 
                           :style="{ width: region.utilization + '%' }">
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import MetricCard from '@/components/MetricCard.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';

interface MemoryPool {
  name: string;
  type: string;
  icon: string;
  used: string;
  committed: string;
  max: string;
  utilization: number;
}

interface CodeCacheRegion {
  name: string;
  status: string;
  statusClass: string;
  size: string;
  used: string;
  free: string;
  utilization: number;
}

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

// Chart instances
let memoryUsageChart: ApexCharts | null = null;
let allocationChart: ApexCharts | null = null;
let codeCacheChart: ApexCharts | null = null;
let metaspaceChart: ApexCharts | null = null;

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

const memoryPressure = ref({
  level: 'Medium',
  description: 'Moderate allocation pressure'
});

const metaspaceMetrics = ref({
  usage: '89 MB',
  capacity: '128 MB'
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

// Code cache regions
const codeCacheRegions = ref<CodeCacheRegion[]>([
  {
    name: 'Non-NMethod',
    status: 'Healthy',
    statusClass: 'bg-success',
    size: '5.6 MB',
    used: '2.1 MB',
    free: '3.5 MB',
    utilization: 38
  },
  {
    name: 'Profiled',
    status: 'Normal',
    statusClass: 'bg-info',
    size: '122 MB',
    used: '87 MB',
    free: '35 MB',
    utilization: 71
  },
  {
    name: 'Non-Profiled',
    status: 'Warning',
    statusClass: 'bg-warning',
    size: '122 MB',
    used: '108 MB',
    free: '14 MB',
    utilization: 89
  }
]);

// Metaspace statistics
const metaspaceStats = ref({
  currentUsage: '89 MB',
  capacity: '128 MB',
  usagePercentage: 69,
  committed: '96 MB',
  reserved: '128 MB',
  classesLoaded: '8,432',
  classesUnloaded: '156',
  loadingRate: '2.3 classes/sec'
});

// Metaspace events
const metaspaceEvents = ref([
  {
    id: 1,
    timestamp: Date.now() - 1000 * 45,
    type: 'Class Loading',
    classesAffected: '23',
    memoryChange: '+1.2 MB',
    trigger: 'Application Startup'
  },
  {
    id: 2,
    timestamp: Date.now() - 1000 * 120,
    type: 'Class Unloading',
    classesAffected: '8',
    memoryChange: '-0.8 MB',
    trigger: 'GC Cleanup'
  },
  {
    id: 3,
    timestamp: Date.now() - 1000 * 300,
    type: 'Metaspace Expansion',
    classesAffected: '0',
    memoryChange: '+8.0 MB',
    trigger: 'Capacity Threshold'
  },
  {
    id: 4,
    timestamp: Date.now() - 1000 * 450,
    type: 'Class Loading',
    classesAffected: '45',
    memoryChange: '+2.1 MB',
    trigger: 'Dynamic Loading'
  }
]);

// Helper functions
const getPressureBarClass = (level: number) => {
  if (level > 80) return 'bg-danger';
  if (level > 60) return 'bg-warning';
  return 'bg-success';
};

const getMetaspaceProgressClass = (percentage: number) => {
  if (percentage > 80) return 'bg-danger';
  if (percentage > 60) return 'bg-warning';
  return 'bg-success';
};

const getMetaspaceEventBadgeClass = (type: string) => {
  switch (type) {
    case 'Class Loading':
      return 'bg-primary';
    case 'Class Unloading':
      return 'bg-success';
    case 'Metaspace Expansion':
      return 'bg-warning';
    default:
      return 'bg-secondary';
  }
};

const formatTimestamp = (timestamp: number) => {
  return new Date(timestamp).toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
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

// Create memory usage chart
const createMemoryUsageChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('memory-usage-chart');
  if (!chartElement) return;
  
  const { heapData } = generateMemoryUsageData(selectedTimeRange.value);
  
  const options = {
    chart: {
      type: 'area',
      height: '100%',
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      zoom: {
        enabled: true,
        type: 'x'
      },
      toolbar: {
        show: true
      }
    },
    series: [{
      name: 'Heap Usage',
      data: heapData,
      color: '#007bff'
    }],
    stroke: {
      curve: 'smooth',
      width: 2
    },
    fill: {
      type: 'gradient',
      gradient: {
        opacityFrom: 0.4,
        opacityTo: 0.1
      }
    },
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'HH:mm:ss'
      }
    },
    yaxis: {
      title: {
        text: 'Memory Usage (MB)'
      },
      labels: {
        formatter: (value: number) => Math.round(value) + ' MB'
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      x: {
        format: 'HH:mm:ss'
      },
      y: {
        formatter: (value: number) => Math.round(value) + ' MB'
      }
    },
    annotations: {
      yaxis: [
        {
          y: 2048,
          borderColor: '#ff6b6b',
          label: {
            text: 'Max Heap (2GB)',
            style: {
              color: '#fff',
              background: '#ff6b6b'
            }
          }
        }
      ]
    }
  };
  
  if (memoryUsageChart) {
    memoryUsageChart.destroy();
  }
  
  memoryUsageChart = new ApexCharts(chartElement, options);
  memoryUsageChart.render();
};

// Create allocation timeline chart
const createAllocationChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('allocation-timeline-chart');
  if (!chartElement) return;
  
  const { allocationData } = generateMemoryUsageData(selectedTimeRange.value);
  
  const options = {
    chart: {
      type: 'line',
      height: '100%',
      fontFamily: 'inherit'
    },
    series: [{
      name: 'Allocation Rate',
      data: allocationData,
      color: '#28a745'
    }],
    stroke: {
      curve: 'smooth',
      width: 3
    },
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'HH:mm:ss'
      }
    },
    yaxis: {
      title: {
        text: 'Allocation Rate (MB/s)'
      },
      labels: {
        formatter: (value: number) => value.toFixed(1) + ' MB/s'
      }
    },
    tooltip: {
      x: {
        format: 'HH:mm:ss'
      },
      y: {
        formatter: (value: number) => value.toFixed(1) + ' MB/s'
      }
    }
  };
  
  if (allocationChart) {
    allocationChart.destroy();
  }
  
  allocationChart = new ApexCharts(chartElement, options);
  allocationChart.render();
};

// Create code cache chart
const createCodeCacheChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('code-cache-chart');
  if (!chartElement) return;
  
  const options = {
    chart: {
      type: 'donut',
      height: '100%',
      fontFamily: 'inherit'
    },
    series: [87, 108, 2.1],
    labels: ['Profiled', 'Non-Profiled', 'Non-NMethod'],
    colors: ['#17a2b8', '#ffc107', '#28a745'],
    plotOptions: {
      pie: {
        donut: {
          size: '70%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Total Used',
              formatter: () => '197.1 MB'
            }
          }
        }
      }
    },
    legend: {
      position: 'bottom'
    },
    tooltip: {
      y: {
        formatter: (value: number) => value + ' MB'
      }
    }
  };
  
  if (codeCacheChart) {
    codeCacheChart.destroy();
  }
  
  codeCacheChart = new ApexCharts(chartElement, options);
  codeCacheChart.render();
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

// Generate metaspace usage data
const generateMetaspaceData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  const interval = timeRange === '1h' ? 60 * 1000 : 10 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const metaspaceUsageData = [];
  const classLoadingData = [];
  
  let currentUsage = 60; // Start at 60MB
  let currentClasses = 7500;
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate metaspace growth patterns
    if (i % 30 === 0) {
      // Occasional class loading spike
      currentUsage += Math.random() * 8 + 2; // +2-10MB
      currentClasses += Math.floor(Math.random() * 200 + 50); // +50-250 classes
    } else {
      // Gradual growth
      currentUsage += Math.random() * 2 - 0.5; // -0.5 to +1.5MB
      currentClasses += Math.floor(Math.random() * 10 - 2); // -2 to +8 classes
    }
    
    // Occasional class unloading (rare)
    if (Math.random() > 0.98) {
      currentUsage = Math.max(50, currentUsage - Math.random() * 5);
      currentClasses = Math.max(6000, currentClasses - Math.floor(Math.random() * 100));
    }
    
    // Keep in reasonable bounds
    currentUsage = Math.max(50, Math.min(120, currentUsage));
    currentClasses = Math.max(6000, Math.min(10000, currentClasses));
    
    metaspaceUsageData.push([timestamp, Math.round(currentUsage * 10) / 10]);
    classLoadingData.push([timestamp, currentClasses]);
  }
  
  return { metaspaceUsageData, classLoadingData };
};

// Create metaspace usage chart
const createMetaspaceChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('metaspace-usage-chart');
  if (!chartElement) return;
  
  const { metaspaceUsageData } = generateMetaspaceData(selectedTimeRange.value);
  
  const series = [
    {
      name: 'Metaspace Usage',
      data: metaspaceUsageData,
      color: '#6f42c1'
    }
  ];
  
  const options = {
    chart: {
      type: 'area',
      height: '100%',
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      zoom: {
        enabled: true,
        type: 'x'
      },
      toolbar: {
        show: true
      }
    },
    plotOptions: {
      area: {
        fillTo: 'origin'
      }
    },
    series,
    dataLabels: {
      enabled: false
    },
    stroke: {
      curve: 'smooth',
      width: 2
    },
    fill: {
      type: 'gradient',
      gradient: {
        opacityFrom: 0.6,
        opacityTo: 0.1
      }
    },
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'HH:mm',
        style: {
          fontSize: '9px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Memory (MB)',
        style: {
          fontSize: '11px'
        }
      },
      labels: {
        formatter: (value: number) => value.toFixed(1) + ' MB',
        style: {
          fontSize: '9px'
        }
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      x: {
        format: 'HH:mm:ss'
      },
      y: {
        formatter: (value: number) => value.toFixed(1) + ' MB'
      }
    },
    legend: {
      position: 'top',
      horizontalAlign: 'left',
      fontSize: '10px',
      offsetY: 5
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    },
    annotations: {
      yaxis: [
        {
          y: 128,
          borderColor: '#ff6b6b',
          label: {
            text: 'Capacity (128MB)',
            style: {
              color: '#fff',
              background: '#ff6b6b',
              fontSize: '9px'
            }
          }
        }
      ]
    }
  };
  
  if (metaspaceChart) {
    metaspaceChart.destroy();
  }
  
  metaspaceChart = new ApexCharts(chartElement, options);
  metaspaceChart.render();
};

// Update all charts
const updateCharts = () => {
  createMemoryUsageChart();
  createAllocationChart();
  createCodeCacheChart();
  createMetaspaceChart();
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
.heap-memory-container {
  position: relative;
  padding: 1rem;
  height: 100%;
}

.loading-overlay, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.heap-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* Metrics Grid */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

/* Dashboard Tabs */
.dashboard-tabs {
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  flex-grow: 1;
}

.nav-tabs {
  border-bottom: 1px solid #e9ecef;
  padding: 0 1rem;
}

.nav-tabs .nav-link {
  margin-bottom: -1px;
  border-radius: 0;
  padding: 0.75rem 1rem;
  font-size: 0.9rem;
  color: #6c757d;
  border: none;
  border-bottom: 2px solid transparent;
}

.nav-tabs .nav-link.active {
  background-color: transparent;
  color: #0d6efd;
  border-bottom: 2px solid #0d6efd;
}

.nav-tabs .nav-link:hover:not(.active) {
  border-color: transparent;
  color: #212529;
}

.tab-content {
  padding: 1.5rem;
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

/* Code Cache */
.code-cache-details {
  padding: 1rem;
  background-color: #f8f9fa;
  border-radius: 8px;
  height: 400px;
  overflow-y: auto;
}

.cache-region {
  margin-bottom: 1.5rem;
  padding: 1rem;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.region-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.region-stats {
  font-size: 0.85rem;
}

.region-stats .stat-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.25rem;
}

.badge {
  font-size: 0.7rem;
  padding: 0.25em 0.5em;
}

/* Metaspace */
.metaspace-stats-section {
  background-color: #f8f9fa;
  border-radius: 8px;
  padding: 1rem;
  border-top: 1px solid #e9ecef;
}

.metaspace-events {
  background-color: #fff;
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.metaspace-events .table-responsive {
  max-height: 300px;
  overflow-y: auto;
}

.metaspace-events .table thead th {
  position: sticky;
  top: 0;
  background-color: #f8f9fa;
  font-weight: 600;
  font-size: 0.8rem;
  padding: 0.5rem;
}

.metaspace-events .table td {
  font-size: 0.8rem;
  padding: 0.5rem;
  vertical-align: middle;
}

.metaspace-events .table tbody tr:hover {
  background-color: rgba(111, 66, 193, 0.05);
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
  
  .code-cache-details {
    height: auto;
    margin-top: 1rem;
  }
}
</style>
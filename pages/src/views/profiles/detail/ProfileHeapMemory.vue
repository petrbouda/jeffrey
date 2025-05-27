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
      <!-- Compact Header Section -->
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h4 class="mb-1 d-flex align-items-center">
            <i class="bi bi-memory me-2 text-primary"></i>
            Heap Memory Analysis
          </h4>
          <p class="text-muted mb-0 small">Analysis of Java heap memory usage and garbage collection patterns</p>
        </div>
        <div class="d-flex gap-2">
          <select v-model="selectedTimeRange" @change="updateBothCharts" class="form-select form-select-sm">
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
        <div class="metric-card">
          <div class="metric-icon bg-primary-soft">
            <i class="bi bi-hdd-stack"></i>
          </div>
          <div class="metric-content">
            <h3 class="metric-value">{{ heapUsageData.usedMemory }}</h3>
            <div class="metric-details">
              <span class="young-gen">{{ heapUsageData.youngGenUsed }}</span>
              <span class="separator">|</span>
              <span class="old-gen">{{ heapUsageData.oldGenUsed }}</span>
            </div>
            <p class="metric-label">Heap Usage</p>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon bg-success-soft">
            <i class="bi bi-arrow-up-circle"></i>
          </div>
          <div class="metric-content">
            <h3 class="metric-value">{{ allocationData.allocationRate }}</h3>
            <div class="metric-details">
              <span>{{ allocationData.totalAllocations }} total</span>
            </div>
            <p class="metric-label">Allocation Rate</p>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon bg-warning-soft">
            <i class="bi bi-recycle"></i>
          </div>
          <div class="metric-content">
            <h3 class="metric-value">{{ gcData.totalCollections }}</h3>
            <div class="metric-details">
              <span class="young-count">{{ gcData.youngCollections }} Young</span>
              <span class="separator">|</span>
              <span class="old-count">{{ gcData.oldCollections }} Old</span>
            </div>
            <p class="metric-label">GC Collections</p>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon bg-danger-soft">
            <i class="bi bi-pause-circle"></i>
          </div>
          <div class="metric-content">
            <h3 class="metric-value">{{ gcData.averagePauseTime }}</h3>
            <div class="metric-details">
              <span>Max: {{ gcData.maxPauseTime }}</span>
            </div>
            <p class="metric-label">Avg Pause Time</p>
          </div>
        </div>
      </div>

      <!-- Modern Tabbed Navigation for Memory Regions and Charts -->
      <div class="dashboard-tabs mb-4">
        <ul class="nav nav-tabs" role="tablist">
          <li class="nav-item" role="presentation">
            <button class="nav-link active" id="charts-tab" data-bs-toggle="tab" data-bs-target="#charts-tab-pane" type="button" role="tab" aria-controls="charts-tab-pane" aria-selected="true">
              <i class="bi bi-graph-up me-2"></i>Memory Timeline
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="regions-tab" data-bs-toggle="tab" data-bs-target="#regions-tab-pane" type="button" role="tab" aria-controls="regions-tab-pane" aria-selected="false">
              <i class="bi bi-layers me-2"></i>Memory Regions
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="gc-events-tab" data-bs-toggle="tab" data-bs-target="#gc-events-tab-pane" type="button" role="tab" aria-controls="gc-events-tab-pane" aria-selected="false">
              <i class="bi bi-clock-history me-2"></i>GC Events
            </button>
          </li>
        </ul>

        <div class="tab-content">
          <!-- Memory Timeline Charts Tab -->
          <div class="tab-pane fade show active" id="charts-tab-pane" role="tabpanel" aria-labelledby="charts-tab" tabindex="0">
            <div class="chart-container memory-timeline">
              <div id="memory-timeline-chart"></div>
            </div>
          </div>

          <!-- Memory Regions Tab -->
          <div class="tab-pane fade" id="regions-tab-pane" role="tabpanel" aria-labelledby="regions-tab" tabindex="0">
            <div class="memory-regions-grid">
              <div class="region-card">
                <div class="region-header eden">
                  <i class="bi bi-box me-2"></i>
                  <span>Eden Space</span>
                </div>
                <div class="region-body">
                  <h4>{{ memoryRegions.edenSpace.used }}</h4>
                  <div class="progress">
                    <div class="progress-bar bg-success" role="progressbar" 
                         :style="{ width: getEdenPercentage() + '%' }" 
                         :aria-valuenow="getEdenPercentage()"
                         aria-valuemin="0" aria-valuemax="100">
                    </div>
                  </div>
                  <p class="region-capacity">Capacity: {{ memoryRegions.edenSpace.capacity }}</p>
                </div>
              </div>

              <div class="region-card">
                <div class="region-header survivor">
                  <i class="bi bi-layers me-2"></i>
                  <span>Survivor Space</span>
                </div>
                <div class="region-body">
                  <h4>{{ memoryRegions.survivorSpace.used }}</h4>
                  <div class="d-flex justify-content-between mb-2">
                    <span class="badge bg-info">S0: {{ memoryRegions.survivorSpace.s0Used }}</span>
                    <span class="badge bg-info">S1: {{ memoryRegions.survivorSpace.s1Used }}</span>
                  </div>
                </div>
              </div>

              <div class="region-card">
                <div class="region-header old-gen">
                  <i class="bi bi-archive me-2"></i>
                  <span>Old Generation</span>
                </div>
                <div class="region-body">
                  <h4>{{ memoryRegions.oldGeneration.used }}</h4>
                  <div class="progress">
                    <div class="progress-bar bg-warning" role="progressbar" 
                         :style="{ width: getOldGenPercentage() + '%' }" 
                         :aria-valuenow="getOldGenPercentage()"
                         aria-valuemin="0" aria-valuemax="100">
                    </div>
                  </div>
                  <p class="region-capacity">Capacity: {{ memoryRegions.oldGeneration.capacity }}</p>
                </div>
              </div>

              <div class="region-card">
                <div class="region-header metaspace">
                  <i class="bi bi-database me-2"></i>
                  <span>Metaspace</span>
                </div>
                <div class="region-body">
                  <h4>{{ memoryRegions.metaspace.used }}</h4>
                  <div class="progress">
                    <div class="progress-bar bg-info" role="progressbar" 
                         :style="{ width: getMetaspacePercentage() + '%' }" 
                         :aria-valuenow="getMetaspacePercentage()"
                         aria-valuemin="0" aria-valuemax="100">
                    </div>
                  </div>
                  <p class="region-capacity">Capacity: {{ memoryRegions.metaspace.capacity }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- GC Events Tab -->
          <div class="tab-pane fade" id="gc-events-tab-pane" role="tabpanel" aria-labelledby="gc-events-tab" tabindex="0">
            <div class="gc-events-container">
              <div class="gc-chart-filters mb-3">
                <select v-model="selectedGCType" @change="updateGCChart" class="form-select form-select-sm">
                  <option value="all">All GC Types</option>
                  <option value="young">Young Generation</option>
                  <option value="old">Old Generation</option>
                  <option value="mixed">Mixed Collections</option>
                </select>
              </div>
              
              <div class="chart-container gc-chart">
                <div id="gc-events-chart"></div>
              </div>
              
              <div class="gc-events-table">
                <h6 class="table-header">
                  <i class="bi bi-table me-2"></i>Recent GC Events
                </h6>
                <div class="table-responsive">
                  <table class="table table-sm table-hover">
                    <thead>
                      <tr>
                        <th>Time</th>
                        <th>Type</th>
                        <th>Cause</th>
                        <th>Duration</th>
                        <th>Before</th>
                        <th>After</th>
                        <th>Freed</th>
                        <th>Efficiency</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="event in recentGCEvents" :key="event.id">
                        <td>{{ event.timestamp }}</td>
                        <td>
                          <span class="badge" :class="getGCTypeBadgeClass(event.type)">
                            {{ getShortGCType(event.type) }}
                          </span>
                        </td>
                        <td>{{ event.cause }}</td>
                        <td>{{ event.duration }}</td>
                        <td>{{ event.beforeGC }}</td>
                        <td>{{ event.afterGC }}</td>
                        <td>{{ event.freed }}</td>
                        <td>
                          <div class="d-flex align-items-center">
                            <div class="progress flex-grow-1 me-2" style="height: 6px;">
                              <div class="progress-bar" 
                                  :class="getEfficiencyBarClass(event.efficiency)"
                                  :style="{ width: event.efficiency + '%' }">
                              </div>
                            </div>
                            <small class="text-muted">{{ event.efficiency }}%</small>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
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

const route = useRoute();
const loading = ref(true);
const error = ref(false);
const selectedTimeRange = ref('6h');
const selectedGCType = ref('all');

// Chart instances
let memoryChart: ApexCharts | null = null;
let gcChart: ApexCharts | null = null;

// Mock data structures - these would be populated from actual heap memory data
const heapUsageData = ref({
  usedMemory: '1.2 GB',
  youngGenUsed: '256 MB', 
  oldGenUsed: '944 MB'
});

const allocationData = ref({
  allocationRate: '45.2 MB/s',
  totalAllocations: '2.3 GB'
});

const gcData = ref({
  totalCollections: '127',
  youngCollections: '95',
  oldCollections: '32',
  averagePauseTime: '8.5ms',
  maxPauseTime: '24.3ms'
});

const memoryRegions = ref({
  edenSpace: {
    used: '128 MB',
    capacity: '256 MB'
  },
  survivorSpace: {
    used: '32 MB',
    s0Used: '16 MB',
    s1Used: '16 MB'
  },
  oldGeneration: {
    used: '944 MB',
    capacity: '1.5 GB'
  },
  metaspace: {
    used: '64 MB',
    capacity: '128 MB'
  }
});

const recentGCEvents = ref([
  {
    id: 1,
    timestamp: '14:23:45.123',
    type: 'Young Generation',
    cause: 'Allocation Failure',
    duration: '12.3ms',
    beforeGC: '1.1 GB',
    afterGC: '0.8 GB',
    freed: '300 MB',
    efficiency: 27
  },
  {
    id: 2,
    timestamp: '14:23:42.856',
    type: 'Mixed',
    cause: 'G1 Evacuation Pause',
    duration: '18.7ms',
    beforeGC: '1.3 GB',
    afterGC: '0.9 GB',
    freed: '400 MB',
    efficiency: 31
  },
  {
    id: 3,
    timestamp: '14:23:38.445',
    type: 'Young Generation',
    cause: 'Allocation Failure',
    duration: '8.9ms',
    beforeGC: '0.9 GB',
    afterGC: '0.7 GB',
    freed: '200 MB',
    efficiency: 22
  }
]);

// Memory region percentage calculations for progress bars
const getEdenPercentage = () => {
  const used = parseInt(memoryRegions.value.edenSpace.used);
  const capacity = parseInt(memoryRegions.value.edenSpace.capacity);
  return Math.round((used / capacity) * 100);
};

const getOldGenPercentage = () => {
  const used = parseInt(memoryRegions.value.oldGeneration.used);
  const capacity = parseInt(memoryRegions.value.oldGeneration.capacity);
  return Math.round((used / capacity) * 100);
};

const getMetaspacePercentage = () => {
  const used = parseInt(memoryRegions.value.metaspace.used);
  const capacity = parseInt(memoryRegions.value.metaspace.capacity);
  return Math.round((used / capacity) * 100);
};

// Get shortened GC type name
const getShortGCType = (type: string) => {
  if (type === 'Young Generation') return 'Young';
  if (type === 'Old Generation') return 'Old';
  return type;
};

// Generate realistic heap memory data
const generateMemoryTimelineData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,      // 1 hour
    '6h': 6 * 60 * 60 * 1000,  // 6 hours
    '24h': 24 * 60 * 60 * 1000, // 24 hours
    'all': 7 * 24 * 60 * 60 * 1000  // 7 days
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  
  // Generate data points every 30 seconds for 1h, every 5 minutes for longer periods
  const interval = timeRange === '1h' ? 30 * 1000 : 5 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const data = [];
  let currentHeapUsed = 800; // Start at 800MB
  let currentEdenUsed = 100;
  let currentOldGenUsed = 600;
  let currentMetaspaceUsed = 60;
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate realistic heap behavior with GC cycles
    if (i % 40 === 0) {
      // Major GC - significant drop in old gen
      currentOldGenUsed = Math.max(300, currentOldGenUsed * 0.4);
      currentEdenUsed = Math.max(20, currentEdenUsed * 0.1);
    } else if (i % 8 === 0) {
      // Minor GC - Eden space cleanup
      currentEdenUsed = Math.max(20, currentEdenUsed * 0.2);
      currentOldGenUsed = Math.min(1000, currentOldGenUsed + (currentEdenUsed * 0.1));
    } else {
      // Normal allocation
      currentEdenUsed = Math.min(250, currentEdenUsed + Math.random() * 15);
      currentOldGenUsed = Math.min(1000, currentOldGenUsed + Math.random() * 2);
    }
    
    // Add some noise for realism
    currentEdenUsed += (Math.random() - 0.5) * 10;
    currentOldGenUsed += (Math.random() - 0.5) * 20;
    currentMetaspaceUsed += (Math.random() - 0.5) * 2;
    
    // Keep values in reasonable bounds
    currentEdenUsed = Math.max(10, Math.min(250, currentEdenUsed));
    currentOldGenUsed = Math.max(200, Math.min(1000, currentOldGenUsed));
    currentMetaspaceUsed = Math.max(50, Math.min(120, currentMetaspaceUsed));
    
    currentHeapUsed = currentEdenUsed + currentOldGenUsed;
    
    data.push({
      timestamp,
      heapUsed: Math.round(currentHeapUsed),
      edenSpace: Math.round(currentEdenUsed),
      oldGeneration: Math.round(currentOldGenUsed),
      metaspace: Math.round(currentMetaspaceUsed)
    });
  }
  
  return data;
};

// Generate GC events data
const generateGCEventsData = (gcType: string, timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  
  const data = [];
  const eventCount = timeRange === '1h' ? 15 : timeRange === '6h' ? 60 : 200;
  
  for (let i = 0; i < eventCount; i++) {
    const timestamp = startTime + (Math.random() * timeSpan);
    const eventType = Math.random() < 0.7 ? 'Young' : Math.random() < 0.8 ? 'Old' : 'Mixed';
    
    // Filter by selected GC type
    if (gcType !== 'all') {
      if (gcType === 'young' && eventType !== 'Young') continue;
      if (gcType === 'old' && eventType !== 'Old') continue;
      if (gcType === 'mixed' && eventType !== 'Mixed') continue;
    }
    
    const pauseTime = eventType === 'Young' ? 
      Math.random() * 20 + 5 :  // 5-25ms for young
      eventType === 'Old' ? 
        Math.random() * 100 + 20 : // 20-120ms for old
        Math.random() * 40 + 15;   // 15-55ms for mixed
    
    data.push({
      timestamp,
      type: eventType,
      pauseTime: Math.round(pauseTime * 10) / 10
    });
  }
  
  return data.sort((a, b) => a.timestamp - b.timestamp);
};

// Create memory timeline chart
const createMemoryChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('memory-timeline-chart');
  if (!chartElement) return;
  
  const data = generateMemoryTimelineData(selectedTimeRange.value);
  
  const series = [
    {
      name: 'Total Heap Used',
      data: data.map(d => [d.timestamp, d.heapUsed]),
      color: '#007bff'
    },
    {
      name: 'Eden Space',
      data: data.map(d => [d.timestamp, d.edenSpace]),
      color: '#28a745'
    },
    {
      name: 'Old Generation',
      data: data.map(d => [d.timestamp, d.oldGeneration]),
      color: '#ffc107'
    },
    {
      name: 'Metaspace',
      data: data.map(d => [d.timestamp, d.metaspace]),
      color: '#6f42c1'
    }
  ];
  
  const options = {
    chart: {
      type: 'area',
      height: '100%',
      width: '100%',
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
        show: true,
        offsetX: -10,
        tools: {
          download: true,
          selection: true,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        }
      }
    },
    series,
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
        format: 'HH:mm:ss',
        style: {
          fontSize: '10px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Memory Usage (MB)',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        formatter: (value: number) => Math.round(value) + ' MB',
        style: {
          fontSize: '10px'
        }
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      x: {
        format: 'HH:mm:ss.fff'
      },
      y: {
        formatter: (value: number) => Math.round(value) + ' MB'
      }
    },
    legend: {
      position: 'top',
      horizontalAlign: 'left',
      fontSize: '12px',
      offsetY: 5,
      itemMargin: {
        horizontal: 8
      }
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    },
    markers: {
      size: 0,
      hover: {
        sizeOffset: 4
      }
    }
  };
  
  if (memoryChart) {
    memoryChart.destroy();
  }
  
  memoryChart = new ApexCharts(chartElement, options);
  memoryChart.render();
};

// Create GC events chart
const createGCChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('gc-events-chart');
  if (!chartElement) return;
  
  const data = generateGCEventsData(selectedGCType.value, selectedTimeRange.value);
  
  // Group by type for the chart
  const youngEvents = data.filter(d => d.type === 'Young');
  const oldEvents = data.filter(d => d.type === 'Old');
  const mixedEvents = data.filter(d => d.type === 'Mixed');
  
  const series = [];
  
  if (selectedGCType.value === 'all' || selectedGCType.value === 'young') {
    series.push({
      name: 'Young Generation GC',
      data: youngEvents.map(d => [d.timestamp, d.pauseTime]),
      color: '#28a745'
    });
  }
  
  if (selectedGCType.value === 'all' || selectedGCType.value === 'old') {
    series.push({
      name: 'Old Generation GC',
      data: oldEvents.map(d => [d.timestamp, d.pauseTime]),
      color: '#dc3545'
    });
  }
  
  if (selectedGCType.value === 'all' || selectedGCType.value === 'mixed') {
    series.push({
      name: 'Mixed GC',
      data: mixedEvents.map(d => [d.timestamp, d.pauseTime]),
      color: '#ffc107'
    });
  }
  
  const options = {
    chart: {
      type: 'scatter',
      height: '100%',
      width: '100%',
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      zoom: {
        enabled: true,
        type: 'xy'
      },
      toolbar: {
        show: true,
        offsetX: -10,
      }
    },
    series,
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'HH:mm:ss',
        style: {
          fontSize: '10px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Pause Time (ms)',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        formatter: (value: number) => value.toFixed(1) + ' ms',
        style: {
          fontSize: '10px'
        }
      }
    },
    tooltip: {
      shared: false,
      intersect: true,
      x: {
        format: 'HH:mm:ss.fff'
      },
      y: {
        formatter: (value: number) => value.toFixed(1) + ' ms'
      }
    },
    legend: {
      position: 'top',
      horizontalAlign: 'left',
      fontSize: '12px',
      offsetY: 5
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    },
    markers: {
      size: 5,
      hover: {
        sizeOffset: 2
      }
    }
  };
  
  if (gcChart) {
    gcChart.destroy();
  }
  
  gcChart = new ApexCharts(chartElement, options);
  gcChart.render();
};

// Update both charts together
const updateBothCharts = () => {
  createMemoryChart();
  createGCChart();
};

// Update charts individually
const updateMemoryChart = () => {
  createMemoryChart();
};

const updateGCChart = () => {
  createGCChart();
};

// Refresh data (simulated)
const refreshData = () => {
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    updateBothCharts();
  }, 800);
};

// Helper methods for styling
const getGCTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'Young Generation':
      return 'bg-info text-white';
    case 'Old Generation':
      return 'bg-warning text-dark';
    case 'Mixed':
      return 'bg-primary text-white';
    default:
      return 'bg-secondary text-white';
  }
};

const getEfficiencyBarClass = (efficiency: number) => {
  if (efficiency > 30) return 'bg-success';
  if (efficiency > 15) return 'bg-warning';
  return 'bg-danger';
};

// Load heap memory data on component mount
onMounted(async () => {
  try {
    const projectId = route.params.projectId as string;
    const profileId = route.params.profileId as string;
    
    console.log(`Loading heap memory data for project ${projectId}, profile ${profileId}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Data loaded successfully
    loading.value = false;
    
    // Create charts after loading
    await nextTick();
    createMemoryChart();
    createGCChart();
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

.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  font-size: 1.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 1rem;
}

.bg-primary-soft { background-color: rgba(13, 110, 253, 0.1); color: #0d6efd; }
.bg-success-soft { background-color: rgba(25, 135, 84, 0.1); color: #198754; }
.bg-warning-soft { background-color: rgba(255, 193, 7, 0.1); color: #ffc107; }
.bg-danger-soft { background-color: rgba(220, 53, 69, 0.1); color: #dc3545; }

.metric-content {
  flex-grow: 1;
}

.metric-value {
  font-size: 1.2rem;
  font-weight: 600;
  margin: 0;
}

.metric-details {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0.2rem;
}

.separator {
  margin: 0 6px;
  color: #ced4da;
}

.young-gen, .young-count {
  color: #198754;
}

.old-gen, .old-count {
  color: #ffc107;
}

.metric-label {
  font-size: 0.75rem;
  color: #6c757d;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
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
  height: 450px;
  width: 100%;
}

.memory-timeline {
  height: 450px;
}

.gc-chart {
  height: 300px;
  margin-bottom: 1.5rem;
}

/* Memory Regions */
.memory-regions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.region-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  background-color: #fff;
}

.region-header {
  padding: 0.75rem 1rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  color: white;
}

.region-header.eden { background-color: #198754; }
.region-header.survivor { background-color: #0dcaf0; }
.region-header.old-gen { background-color: #ffc107; }
.region-header.metaspace { background-color: #6f42c1; }

.region-body {
  padding: 1rem;
}

.region-body h4 {
  margin-bottom: 0.75rem;
  font-weight: 600;
  font-size: 1.2rem;
}

.region-capacity {
  font-size: 0.8rem;
  margin-top: 0.5rem;
  color: #6c757d;
}

.progress {
  height: 8px;
  border-radius: 4px;
}

/* GC Events Table */
.gc-events-table {
  background-color: #fff;
  border-radius: 8px;
}

.table-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e9ecef;
  font-weight: 600;
  margin: 0;
}

.table-responsive {
  max-height: 300px;
  overflow-y: auto;
}

.table {
  margin-bottom: 0;
}

.table thead th {
  position: sticky;
  top: 0;
  background-color: #f8f9fa;
  font-weight: 600;
  color: #495057;
  font-size: 0.8rem;
  padding: 0.5rem;
}

.table td {
  font-size: 0.8rem;
  padding: 0.5rem;
  vertical-align: middle;
}

.badge {
  font-weight: 500;
  font-size: 0.7rem;
  padding: 0.2em 0.6em;
}

/* Responsive Design */
@media (max-width: 992px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-container {
    height: 350px;
  }
  
  .nav-tabs {
    padding: 0;
  }
  
  .nav-tabs .nav-link {
    padding: 0.5rem;
    font-size: 0.8rem;
  }
  
  .tab-content {
    padding: 1rem;
  }
}
</style>
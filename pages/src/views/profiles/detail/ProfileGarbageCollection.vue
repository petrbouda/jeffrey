<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading garbage collection data...</span>
    </div>
    <p class="mt-2">Loading garbage collection data...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load garbage collection data
    </div>
  </div>

  <div v-else>
      <!-- Header Section -->
      <DashboardHeader
          title="Garbage Collection Analysis"
          description="Comprehensive analysis of garbage collection events and performance"
          icon="recycle"
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
          title="Total Collections"
          :value="gcSummary.totalCollections"
          icon="clock-history"
          variant="primary"
        />

        <StatCard
          title="Avg Pause Time"
          :value="gcSummary.avgPauseTime"
          icon="pause-circle"
          variant="warning"
        />

        <StatCard
          title="Memory Freed"
          :value="gcSummary.totalMemoryFreed"
          icon="arrow-down-circle"
          variant="success"
        />

        <StatCard
          title="GC Throughput"
          :value="`${gcSummary.gcThroughput}%`"
          icon="speedometer"
          variant="info"
        />
      </div>

      <!-- Tabbed Content -->
      <div class="dashboard-tabs mb-4">
        <ul class="nav nav-tabs" role="tablist">
          <li class="nav-item" role="presentation">
            <button class="nav-link active" id="timeline-tab" data-bs-toggle="tab" data-bs-target="#timeline-tab-pane" type="button" role="tab" aria-controls="timeline-tab-pane" aria-selected="true">
              <i class="bi bi-graph-up me-2"></i>GC Timeline
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="distribution-tab" data-bs-toggle="tab" data-bs-target="#distribution-tab-pane" type="button" role="tab" aria-controls="distribution-tab-pane" aria-selected="false">
              <i class="bi bi-bar-chart me-2"></i>Pause Distribution
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="efficiency-tab" data-bs-toggle="tab" data-bs-target="#efficiency-tab-pane" type="button" role="tab" aria-controls="efficiency-tab-pane" aria-selected="false">
              <i class="bi bi-pie-chart me-2"></i>GC Efficiency
            </button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="events-tab" data-bs-toggle="tab" data-bs-target="#events-tab-pane" type="button" role="tab" aria-controls="events-tab-pane" aria-selected="false">
              <i class="bi bi-table me-2"></i>Recent Events
            </button>
          </li>
        </ul>

        <div class="tab-content">
          <!-- GC Timeline Tab -->
          <div class="tab-pane fade show active" id="timeline-tab-pane" role="tabpanel" aria-labelledby="timeline-tab" tabindex="0">
            <ApexTimeSeriesChart
              :primary-data="gcTimelineData"
              primary-title="GC Events"
              primary-axis-type="durationInMillis"
              :visible-minutes="60"
              primary-color="#28a745"
              :show-points="true"
            />
          </div>

          <!-- Pause Distribution Tab -->
          <div class="tab-pane fade" id="distribution-tab-pane" role="tabpanel" aria-labelledby="distribution-tab" tabindex="0">
            <div class="chart-container">
              <div id="pause-distribution-chart"></div>
            </div>
          </div>

          <!-- GC Efficiency Tab -->
          <div class="tab-pane fade" id="efficiency-tab-pane" role="tabpanel" aria-labelledby="efficiency-tab" tabindex="0">
            <div class="row">
              <div class="col-md-6">
                <div class="chart-container">
                  <div id="efficiency-pie-chart"></div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="efficiency-stats">
                  <h6 class="mb-3">GC Efficiency Metrics</h6>
                  <div class="stat-item mb-3">
                    <label class="stat-label">Application Time</label>
                    <div class="stat-value">{{ gcSummary.applicationTime }}</div>
                    <div class="progress mt-1">
                      <div class="progress-bar bg-success" role="progressbar" :style="{ width: gcSummary.gcThroughput + '%' }"></div>
                    </div>
                  </div>
                  <div class="stat-item mb-3">
                    <label class="stat-label">GC Time</label>
                    <div class="stat-value">{{ gcSummary.totalGcTime }}</div>
                    <div class="progress mt-1">
                      <div class="progress-bar bg-warning" role="progressbar" :style="{ width: gcSummary.gcOverhead + '%' }"></div>
                    </div>
                  </div>
                  <div class="stat-item">
                    <label class="stat-label">Collection Frequency</label>
                    <div class="stat-value">{{ gcSummary.collectionFrequency }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Recent Events Tab -->
          <div class="tab-pane fade" id="events-tab-pane" role="tabpanel" aria-labelledby="events-tab" tabindex="0">
            <div class="gc-events-table">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <h6 class="mb-0">
                  <i class="bi bi-table me-2"></i>Recent GC Events
                </h6>
                <select v-model="selectedGCType" @change="filterEvents" class="form-select form-select-sm" style="width: auto;">
                  <option value="all">All Types</option>
                  <option value="young">Young Generation</option>
                  <option value="old">Old Generation</option>
                  <option value="mixed">Mixed Collections</option>
                </select>
              </div>
              
              <div class="table-responsive">
                <table class="table table-sm table-hover">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Type</th>
                      <th>Cause</th>
                      <th>Duration</th>
                      <th>Before GC</th>
                      <th>After GC</th>
                      <th>Freed</th>
                      <th>Efficiency</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="event in filteredGCEvents" :key="event.id" @click="showEventDetails(event)" style="cursor: pointer;">
                      <td>{{ formatTimestamp(event.timestamp) }}</td>
                      <td>
                        <span class="badge" :class="getGCTypeBadgeClass(event.type)">
                          {{ getShortGCType(event.type) }}
                        </span>
                      </td>
                      <td>{{ event.cause }}</td>
                      <td>{{ event.duration }}ms</td>
                      <td>{{ event.beforeGC }}</td>
                      <td>{{ event.afterGC }}</td>
                      <td>{{ event.freed }}</td>
                      <td>
                        <div class="d-flex align-items-center">
                          <div class="progress flex-grow-1 me-2" style="height: 6px; min-width: 40px;">
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
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick, computed } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import StatCard from '@/components/StatCard.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import DashboardHeader from '@/components/DashboardHeader.vue';

interface GCEvent {
  id: number;
  timestamp: number;
  type: string;
  cause: string;
  duration: number;
  beforeGC: string;
  afterGC: string;
  freed: string;
  efficiency: number;
}

const route = useRoute();
const loading = ref(true);
const error = ref(false);
const selectedTimeRange = ref('6h');
const selectedGCType = ref('all');

// Timeline data for ApexTimeSeriesChart
const gcTimelineData = ref<number[][]>([]);

// Chart instances
let timelineChart: ApexCharts | null = null;
let distributionChart: ApexCharts | null = null;
let efficiencyChart: ApexCharts | null = null;

// GC Summary data  
const gcSummary = ref({
  totalCollections: '1,247',
  youngCollections: '1,089',
  oldCollections: '148',
  avgPauseTime: '8.7ms',
  maxPauseTime: '94.2ms',
  totalMemoryFreed: '42.8 GB',
  avgMemoryFreed: '35.2 MB',
  gcThroughput: '98.4',
  gcOverhead: '1.6',
  applicationTime: '4h 49m 18s',
  totalGcTime: '4m 42s',
  collectionFrequency: '1.18 per second'
});

// GC Events data
const gcEvents = ref<GCEvent[]>([
  {
    id: 1,
    timestamp: Date.now() - 1000 * 30,
    type: 'Young Generation',
    cause: 'Allocation Failure',
    duration: 8.5,
    beforeGC: '1.2 GB',
    afterGC: '0.8 GB',
    freed: '400 MB',
    efficiency: 33
  },
  {
    id: 2,
    timestamp: Date.now() - 1000 * 120,
    type: 'Mixed',
    cause: 'G1 Evacuation Pause',
    duration: 24.7,
    beforeGC: '1.5 GB',
    afterGC: '0.9 GB',
    freed: '600 MB',
    efficiency: 40
  },
  {
    id: 3,
    timestamp: Date.now() - 1000 * 180,
    type: 'Old Generation',
    cause: 'Allocation Failure',
    duration: 76.2,
    beforeGC: '1.8 GB',
    afterGC: '0.6 GB',
    freed: '1.2 GB',
    efficiency: 67
  },
  {
    id: 4,
    timestamp: Date.now() - 1000 * 300,
    type: 'Young Generation',
    cause: 'Allocation Failure',
    duration: 12.1,
    beforeGC: '0.9 GB',
    afterGC: '0.5 GB',
    freed: '400 MB',
    efficiency: 44
  },
  {
    id: 5,
    timestamp: Date.now() - 1000 * 450,
    type: 'Young Generation',
    cause: 'Allocation Failure',
    duration: 6.8,
    beforeGC: '1.1 GB',
    afterGC: '0.7 GB',
    freed: '400 MB',
    efficiency: 36
  }
]);

// Computed property for filtered events
const filteredGCEvents = computed(() => {
  if (selectedGCType.value === 'all') {
    return gcEvents.value;
  }
  
  const typeMap: { [key: string]: string } = {
    'young': 'Young Generation',
    'old': 'Old Generation',
    'mixed': 'Mixed'
  };
  
  const targetType = typeMap[selectedGCType.value];
  return gcEvents.value.filter(event => event.type === targetType);
});

// Helper functions
const formatTimestamp = (timestamp: number) => {
  return new Date(timestamp).toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

const getShortGCType = (type: string) => {
  if (type === 'Young Generation') return 'Young';
  if (type === 'Old Generation') return 'Old';
  return type;
};

const getGCTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'Young Generation':
      return 'bg-success text-white';
    case 'Old Generation':
      return 'bg-warning text-dark';
    case 'Mixed':
      return 'bg-info text-white';
    default:
      return 'bg-secondary text-white';
  }
};

const getEfficiencyBarClass = (efficiency: number) => {
  if (efficiency > 50) return 'bg-success';
  if (efficiency > 25) return 'bg-warning';
  return 'bg-danger';
};

const showEventDetails = (event: GCEvent) => {
  // Placeholder for event details modal
  console.log('Show event details:', event);
};

const filterEvents = () => {
  // Events are filtered via computed property
};

// Generate timeline data
const generateTimelineData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000,
    'all': 7 * 24 * 60 * 60 * 1000
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  // More frequent intervals to capture more GC events
  const interval = timeRange === '1h' ? 30 * 1000 : timeRange === '6h' ? 2 * 60 * 1000 : 5 * 60 * 1000; // 30sec, 2min, or 5min intervals
  const pointCount = Math.floor(timeSpan / interval);
  
  const youngData = [];
  const oldData = [];
  const mixedData = [];
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Young GC events (very frequent - typical in real applications)
    if (Math.random() > 0.3) { // 70% chance
      youngData.push([timestamp, Math.random() * 20 + 5]); // 5-25ms
    }
    
    // Additional young GC events for high-activity periods
    if (Math.random() > 0.6) { // 40% chance for second young GC in same interval
      const offset = Math.random() * interval * 0.8; // Random offset within interval
      youngData.push([timestamp + offset, Math.random() * 15 + 3]); // 3-18ms
    }
    
    // Old GC events (more frequent than before)
    if (Math.random() > 0.85) { // 15% chance
      oldData.push([timestamp, Math.random() * 80 + 20]); // 20-100ms
    }
    
    // Mixed GC events (more frequent)
    if (Math.random() > 0.92) { // 8% chance
      mixedData.push([timestamp, Math.random() * 40 + 15]); // 15-55ms
    }
    
    // Minor GC bursts during high allocation periods
    if (Math.random() > 0.95) { // 5% chance for burst
      for (let j = 0; j < 3; j++) {
        const burstOffset = j * (interval / 4) + Math.random() * (interval / 8);
        if (timestamp + burstOffset < now) {
          youngData.push([timestamp + burstOffset, Math.random() * 12 + 2]); // 2-14ms for burst events
        }
      }
    }
  }
  
  return { youngData, oldData, mixedData };
};

// Generate timeline data for ApexTimeSeriesChart (combines all GC events)
const generateGCTimelineData = (timeRange: string) => {
  const { youngData, oldData, mixedData } = generateTimelineData(timeRange);
  
  // Combine all GC events into a single dataset and convert to ApexTimeSeriesChart format
  const allEvents = [
    ...youngData.map(([timestamp, duration]) => [timestamp / 1000, duration]), // Convert to seconds
    ...oldData.map(([timestamp, duration]) => [timestamp / 1000, duration]),
    ...mixedData.map(([timestamp, duration]) => [timestamp / 1000, duration])
  ];
  
  // Sort by timestamp
  allEvents.sort((a, b) => a[0] - b[0]);
  
  return allEvents;
};

// Create GC timeline chart
const createTimelineChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('gc-timeline-chart');
  if (!chartElement) return;
  
  const { youngData, oldData, mixedData } = generateTimelineData(selectedTimeRange.value);
  
  const series = [
    {
      name: 'Young Generation GC',
      data: youngData,
      color: '#28a745'
    },
    {
      name: 'Old Generation GC',
      data: oldData,
      color: '#ffc107'
    },
    {
      name: 'Mixed GC',
      data: mixedData,
      color: '#17a2b8'
    }
  ];
  
  const options = {
    chart: {
      type: 'scatter',
      height: '100%',
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
        show: true
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
      },
      title: {
        text: 'Time'
      }
    },
    yaxis: {
      title: {
        text: 'Pause Time (ms)'
      },
      labels: {
        formatter: (value: number) => value.toFixed(1) + ' ms'
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
      horizontalAlign: 'left'
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
  
  if (timelineChart) {
    timelineChart.destroy();
  }
  
  timelineChart = new ApexCharts(chartElement, options);
  timelineChart.render();
};

// Generate pause distribution data based on GC events
const generateDistributionData = () => {
  // Simulate realistic pause time distribution
  const buckets = [
    { range: '0-5ms', count: 0 },
    { range: '5-10ms', count: 0 },
    { range: '10-20ms', count: 0 },
    { range: '20-50ms', count: 0 },
    { range: '50-100ms', count: 0 },
    { range: '100ms+', count: 0 }
  ];
  
  // Generate sample pause times and categorize them
  for (let i = 0; i < 200; i++) {
    let pauseTime;
    const eventType = Math.random();
    
    if (eventType < 0.7) {
      // Young GC - typically shorter pauses
      pauseTime = Math.random() * 25 + 2; // 2-27ms
    } else if (eventType < 0.9) {
      // Mixed GC - medium pauses
      pauseTime = Math.random() * 40 + 10; // 10-50ms
    } else {
      // Old GC - longer pauses
      pauseTime = Math.random() * 80 + 20; // 20-100ms
    }
    
    // Categorize into buckets
    if (pauseTime < 5) buckets[0].count++;
    else if (pauseTime < 10) buckets[1].count++;
    else if (pauseTime < 20) buckets[2].count++;
    else if (pauseTime < 50) buckets[3].count++;
    else if (pauseTime < 100) buckets[4].count++;
    else buckets[5].count++;
  }
  
  return buckets;
};

// Create pause distribution chart
const createDistributionChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('pause-distribution-chart');
  if (!chartElement) return;
  
  const distributionData = generateDistributionData();
  
  const options = {
    chart: {
      type: 'bar',
      height: '100%',
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      toolbar: {
        show: true
      }
    },
    series: [{
      name: 'GC Events',
      data: distributionData.map(bucket => ({
        x: bucket.range,
        y: bucket.count
      }))
    }],
    plotOptions: {
      bar: {
        horizontal: false,
        columnWidth: '70%',
        borderRadius: 4,
        dataLabels: {
          position: 'top'
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => val.toString(),
      offsetY: -20,
      style: {
        fontSize: '10px',
        colors: ['#304758']
      }
    },
    xaxis: {
      title: {
        text: 'Pause Time Range',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Number of Events',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    colors: ['#007bff'],
    tooltip: {
      y: {
        formatter: (value: number) => value + ' events'
      }
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    }
  };
  
  if (distributionChart) {
    distributionChart.destroy();
  }
  
  distributionChart = new ApexCharts(chartElement, options);
  distributionChart.render();
};

// Create efficiency pie chart
const createEfficiencyChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('efficiency-pie-chart');
  if (!chartElement) return;
  
  const options = {
    chart: {
      type: 'donut',
      height: '100%',
      fontFamily: 'inherit'
    },
    series: [97.8, 2.2],
    labels: ['Application Time', 'GC Time'],
    colors: ['#28a745', '#ffc107'],
    plotOptions: {
      pie: {
        donut: {
          size: '70%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Throughput',
              formatter: () => '97.8%'
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
        formatter: (value: number) => value.toFixed(1) + '%'
      }
    }
  };
  
  if (efficiencyChart) {
    efficiencyChart.destroy();
  }
  
  efficiencyChart = new ApexCharts(chartElement, options);
  efficiencyChart.render();
};

// Update all charts
const updateCharts = () => {
  // Update GC timeline data for ApexTimeSeriesChart
  gcTimelineData.value = generateGCTimelineData(selectedTimeRange.value);
  
  // Note: createTimelineChart() is no longer needed as we use ApexTimeSeriesChart component
  createDistributionChart();
  createEfficiencyChart();
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
    
    console.log(`Loading GC data for project ${projectId}, profile ${profileId}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    loading.value = false;
    
    // Create charts after loading
    await nextTick();
    updateCharts();
  } catch (err) {
    console.error('Failed to load GC data:', err);
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

/* Efficiency Stats */
.efficiency-stats {
  padding: 1rem;
  background-color: #f8f9fa;
  border-radius: 8px;
  height: 400px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.progress {
  height: 6px;
  border-radius: 3px;
}

/* GC Events Table */
.gc-events-table {
  background-color: #fff;
  border-radius: 8px;
}

.table-responsive {
  max-height: 400px;
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

.table tbody tr:hover {
  background-color: rgba(0, 123, 255, 0.05);
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
    height: 300px;
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
  
  .efficiency-stats {
    height: auto;
    margin-top: 1rem;
  }
}
</style>
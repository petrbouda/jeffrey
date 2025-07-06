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
          icon="recycle">
        <template #actions>
          <div class="d-flex gap-2">
            <button class="btn btn-sm btn-outline-primary" @click="refreshData">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
        </template>
      </DashboardHeader>

      <!-- Key Metrics Row -->
      <div class="metrics-grid mb-4">
        <DashboardCard
          title="GC Collections"
          :value="gcSummary.totalCollections"
          :valueA="gcSummary.youngCollections"
          :valueB="gcSummary.oldCollections"
          labelA="Young"
          labelB="Old"
          variant="highlight"
        />

        <DashboardCard
          title="GC Pauses"
          :value="gcSummary.maxPauseTime"
          :valueA="gcSummary.p99PauseTime"
          :valueB="gcSummary.p95PauseTime"
          labelA="99th"
          labelB="95th"
          variant="warning"
        />

        <DashboardCard
          title="Memory Freed"
          :value="gcSummary.totalMemoryFreed"
          :valueA="gcSummary.avgMemoryFreed"
          labelA="Average"
          variant="success"
        />

        <DashboardCard
          title="GC Overhead"
          :value="gcSummary.gcOverhead"
          :valueA="gcSummary.collectionFrequency"
          labelA="Frequency"
          variant="info"
        />
      </div>

      <!-- GC Analysis Section -->
      <ChartSectionWithTabs
        icon="recycle"
        :tabs="gcTabs"
        :full-width="true"
        id-prefix="gc-"
        @tab-change="onTabChange"
      >
        <!-- Pause Distribution Tab -->
        <template #distribution>
          <div class="chart-container">
            <div id="gc-pause-distribution-chart"></div>
          </div>
        </template>

        <!-- GC Efficiency Tab -->
        <template #efficiency>
          <div class="row">
            <div class="col-md-6">
              <div class="chart-container">
                <div id="gc-efficiency-pie-chart"></div>
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
        </template>

        <!-- Longest Pauses Tab -->
        <template #events>
          <div class="gc-events-table">
            <div class="table-responsive">
              <table class="table table-sm table-hover">
                <thead>
                  <tr>
                    <th>GC ID</th>
                    <th>Timestamp</th>
                    <th>Cause</th>
                    <th>Duration</th>
                    <th>Before GC</th>
                    <th>After GC</th>
                    <th>Freed</th>
                    <th>Efficiency</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="event in longestPauses" :key="event.gcId" @click="showEventDetails(event)" style="cursor: pointer;">
                    <td>{{ event.gcId }}</td>
                    <td>{{ FormattingService.formatTimestamp(event.timestamp) }}</td>
                    <td>
                      {{ event.cause }}
                      <Badge 
                        v-if="event.generation"
                        :value="event.generation"
                        :variant="getGenerationTypeBadgeVariant(event.generationType)"
                        size="xs"
                        class="ms-1"
                      />
                      <Badge
                        v-if="event.type"
                        :value="event.type"
                        variant="secondary"
                        size="xs"
                        class="ms-2"
                      />
                    </td>
                    <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
                    <td>{{ FormattingService.formatBytes(event.beforeGC) }}</td>
                    <td>{{ FormattingService.formatBytes(event.afterGC) }}</td>
                    <td>{{ FormattingService.formatBytes(event.freed) }}</td>
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
        </template>

        <!-- Concurrent Cycles Tab -->
        <template #concurrent-cycles>
          <div v-if="!gcOverviewData?.longestConcurrentEvents" class="alert alert-info">
            <i class="bi bi-info-circle me-2"></i>
            This garbage collector does not support concurrent cycles
          </div>
          <div v-else class="concurrent-cycles-table">
            <div class="table-responsive">
              <table class="table table-sm table-hover">
                <thead>
                  <tr>
                    <th>GC ID</th>
                    <th>Timestamp</th>
                    <th>Collector Name</th>
                    <th>Duration</th>
                    <th>Sum of Pauses</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="event in longestConcurrentEvents" :key="event.gcId" @click="showConcurrentEventDetails(event)" style="cursor: pointer;">
                    <td>{{ event.gcId }}</td>
                    <td>{{ FormattingService.formatTimestamp(event.timestamp) }}</td>
                    <td>{{ event.name }}</td>
                    <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
                    <td>{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </ChartSectionWithTabs>

      <!-- GC Event Details Modal -->
      <GCEventDetailsModal
        :event="selectedConcurrentEvent"
        modal-id="gcEventDetailsModal"
        :show="showEventDetailsModal"
        @update:show="showEventDetailsModal = $event"
      />

      <!-- GC Pause Details Modal -->
      <GCPauseDetailsModal
        :event="selectedPauseEvent"
        modal-id="gcPauseDetailsModal"
        :show="showPauseDetailsModal"
        @update:show="showPauseDetailsModal = $event"
      />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick, computed } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import DashboardCard from '@/components/DashboardCard.vue';
import DashboardHeader from '@/components/DashboardHeader.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import Badge from '@/components/Badge.vue';
import GCEventDetailsModal from '@/components/gc/GCEventDetailsModal.vue';
import GCPauseDetailsModal from '@/components/gc/GCPauseDetailsModal.vue';
import ProfileGCClient from '@/services/profile/custom/gc/ProfileGCClient';
import GCOverviewData from '@/services/profile/custom/gc/GCOverviewData';
import GCGenerationType from '@/services/profile/custom/gc/GCGenerationType';
import ConcurrentEvent from '@/services/profile/custom/gc/ConcurrentEvent';
import GCEvent from '@/services/profile/custom/gc/GCEvent';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const loading = ref(true);
const error = ref<string | null>(null);

// Modal state
const showEventDetailsModal = ref(false);
const selectedConcurrentEvent = ref<ConcurrentEvent | null>(null);
const showPauseDetailsModal = ref(false);
const selectedPauseEvent = ref<GCEvent | null>(null);

// Tabs configuration for GC Analysis
const gcTabs = [
  { id: 'distribution', label: 'Pause Distribution', icon: 'bar-chart' },
  { id: 'efficiency', label: 'GC Efficiency', icon: 'pie-chart' },
  { id: 'events', label: 'Longest Pauses', icon: 'table' },
  { id: 'concurrent-cycles', label: 'Concurrent Cycles', icon: 'layers' }
];

// Chart instances
let distributionChart: ApexCharts | null = null;
let efficiencyChart: ApexCharts | null = null;

// GC Overview Data
const gcOverviewData = ref<GCOverviewData | null>(null);

// Client initialization
const client = new ProfileGCClient(route.params.projectId as string, route.params.profileId as string);

// GC Summary data (computed from real data)
const gcSummary = computed(() => {
  if (!gcOverviewData.value) {
    return {
      totalCollections: '0',
      youngCollections: '0',
      oldCollections: '0',
      maxPauseTime: '0ms',
      p99PauseTime: '0ms',
      p95PauseTime: '0ms',
      totalMemoryFreed: '0 MB',
      avgMemoryFreed: '0 MB',
      gcThroughput: '0',
      gcOverhead: '0',
      applicationTime: '0s',
      totalGcTime: '0s',
      collectionFrequency: '0 per second'
    };
  }

  const header = gcOverviewData.value.header;
  return {
    totalCollections: FormattingService.formatNumber(header.totalCollections),
    youngCollections: FormattingService.formatNumber(header.youngCollections),
    oldCollections: FormattingService.formatNumber(header.oldCollections),
    maxPauseTime: FormattingService.formatDuration2Units(header.maxPauseTime),
    p99PauseTime: FormattingService.formatDuration2Units(header.p99PauseTime),
    p95PauseTime: FormattingService.formatDuration2Units(header.p95PauseTime),
    totalMemoryFreed: FormattingService.formatBytes(header.totalMemoryFreed),
    avgMemoryFreed: FormattingService.formatBytes(header.avgMemoryFreed),
    gcThroughput: FormattingService.formatPercentage(header.gcThroughput / 100),
    gcOverhead: FormattingService.formatPercentage(header.gcOverhead / 100),
    applicationTime: FormattingService.formatDuration2Units(gcOverviewData.value.efficiency.applicationTime),
    totalGcTime: FormattingService.formatDuration2Units(gcOverviewData.value.efficiency.gcTime),
    collectionFrequency: `${header.collectionFrequency.toFixed(2)} GC/s`
  };
});

// Longest pauses data (computed from real data) - longest GC pauses
const longestPauses = computed(() => {
  if (!gcOverviewData.value) return [];
  
  return gcOverviewData.value.longestPauses;
});

// Longest concurrent events data (computed from real data)
const longestConcurrentEvents = computed(() => {
  if (!gcOverviewData.value?.longestConcurrentEvents) return [];
  
  return gcOverviewData.value.longestConcurrentEvents;
});

const getGenerationTypeBadgeVariant = (generationType: GCGenerationType) => {
  switch (generationType) {
    case GCGenerationType.YOUNG:
      return 'blue' as const;
    case GCGenerationType.OLD:
      return 'orange' as const;
    default:
      return 'grey' as const;
  }
};


const getEfficiencyBarClass = (efficiency: number) => {
  if (efficiency > 50) return 'bg-success';
  if (efficiency > 25) return 'bg-warning';
  return 'bg-danger';
};

const showEventDetails = (event: GCEvent) => {
  selectedPauseEvent.value = event;
  showPauseDetailsModal.value = true;
};

const showConcurrentEventDetails = (event: ConcurrentEvent) => {
  selectedConcurrentEvent.value = event;
  showEventDetailsModal.value = true;
};

// Create pause distribution chart
const createDistributionChart = async () => {
  await nextTick();
  
  const chartElement = document.getElementById('gc-pause-distribution-chart');
  if (!chartElement) {
    console.warn('Distribution chart element not found');
    return;
  }
  
  const distributionData = gcOverviewData.value?.pauseDistribution?.buckets;
  
  if (!distributionData || distributionData.length === 0) {
    console.warn('No pause distribution data available');
    return;
  }

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
        show: false
      }
    },
    series: [{
      name: 'GC Events',
      data: distributionData?.map(bucket => ({
        x: bucket.range,
        y: bucket.count
      })) || []
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
  
  const chartElement = document.getElementById('gc-efficiency-pie-chart');
  if (!chartElement) return;
  
  const efficiency = gcOverviewData.value?.efficiency;
  const throughput = efficiency?.throughputPercentage;
  const overhead = efficiency?.overheadPercentage;
  
  const options = {
    chart: {
      type: 'donut',
      height: '100%',
      fontFamily: 'inherit'
    },
    series: [throughput || 0, overhead || 0],
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
              formatter: () => `${throughput?.toFixed(1) || '0'}%`
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

// Handle tab change
const onTabChange = (_tabIndex: number, tab: any) => {
  // When switching to distribution or efficiency tabs, ensure charts are rendered
  if (tab.id === 'distribution' || tab.id === 'efficiency') {
      if (tab.id === 'distribution') {
        createDistributionChart();
      } else if (tab.id === 'efficiency') {
        createEfficiencyChart();
      }
  }
};

// Refresh data
const refreshData = () => {
  loadGCData();
};

// Load data on component mount
// Load GC data from API
const loadGCData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // Load overview data from API
    gcOverviewData.value = await client.getOverview();

    // Wait for DOM updates
    await nextTick();
    
    // Create the distribution chart by default since it's the first tab
    // Use a timeout to ensure the DOM is fully rendered
    setTimeout(() => {
      createDistributionChart();
    }, 100);
    
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGCData();
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
.gc-events-table, .concurrent-cycles-table {
  background-color: #fff;
  border-radius: 8px;
}


.table {
  margin-bottom: 0;
}

.table thead th {
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



/* Responsive Design */
@media (max-width: 1200px) {
  .metrics-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

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
  
  .efficiency-stats {
    height: auto;
    margin-top: 1rem;
  }
}
</style>

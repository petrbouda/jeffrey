<template>
  <div>
    <DashboardHeader
      title="HTTP Request / Response"
      icon="globe"
    />
    
    <!-- Loading state -->
    <div v-if="isLoading" class="p-4 text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center">
      <div class="alert alert-danger" role="alert">
        Error loading HTTP data: {{ error }}
      </div>
    </div>

    <!-- Dashboard content -->
    <div v-else-if="httpData.length > 0" class="dashboard-container">
      <!-- HTTP Overview Cards -->
      <section class="dashboard-section">
        <div class="dashboard-grid">
          <DashboardCard
            title="Total Requests"
            :value="totalRequests"
            :valueA="uniqueHosts.length"
            labelA="Unique Hosts"
            variant="info"
          />
          <DashboardCard
            title="Average Response Time"
            :value="FormattingService.formatDuration2Units(averageResponseTime)"
            :valueA="FormattingService.formatDuration2Units(p95ResponseTime)"
            labelA="P95 Response Time"
            variant="highlight"
          />
          <DashboardCard
            title="Success Rate"
            :value="`${successRate.toFixed(1)}%`"
            :valueA="errorCount"
            labelA="Total Errors"
            :variant="successRate >= 95 ? 'success' : successRate >= 90 ? 'warning' : 'danger'"
          />
          <DashboardCard
            title="Data Transferred"
            :value="FormattingService.formatBytes(totalDataTransferred)"
            :valueA="FormattingService.formatBytes(averageRequestSize)"
            labelA="Avg Request Size"
            variant="info"
          />
        </div>
      </section>

      <!-- HTTP Method and Status Code Cards -->
      <section class="dashboard-section">
        <div class="endpoints-controls">
          <div class="sort-controls">
            <div class="sort-button-group">
              <button 
                @click="sortBy = 'maxTime'"
                :class="['sort-btn', { 'active': sortBy === 'maxTime' }]"
              >
                MAX
              </button>
              <button 
                @click="sortBy = 'p99Time'"
                :class="['sort-btn', { 'active': sortBy === 'p99Time' }]"
              >
                P99
              </button>
              <button 
                @click="sortBy = '4xx'"
                :class="['sort-btn', { 'active': sortBy === '4xx' }]"
              >
                4xx
              </button>
              <button 
                @click="sortBy = '5xx'"
                :class="['sort-btn', { 'active': sortBy === '5xx' }]"
              >
                5xx
              </button>
              <button 
                @click="sortBy = 'requests'"
                :class="['sort-btn', { 'active': sortBy === 'requests' }]"
              >
                Requests
              </button>
            </div>
          </div>
          
          <button 
            v-if="allEndpoints.length > 9"
            @click="showAllEndpoints = !showAllEndpoints"
            class="show-all-link"
          >
            {{ showAllEndpoints ? 'Show Less' : `Show All (${allEndpoints.length})` }}
          </button>
        </div>
        
        <div class="http-endpoint-grid">
          <div 
            v-for="endpoint in displayedEndpoints" 
            :key="endpoint.uri"
            class="endpoint-card"
            @click="selectedEndpoint = selectedEndpoint === endpoint.uri ? null : endpoint.uri"
            :class="{ 'selected': selectedEndpoint === endpoint.uri }"
          >
            <div class="endpoint-header">
              <div class="uri-container">
                <div class="uri-path" :title="endpoint.uri">{{ truncateUri(endpoint.uri, 35) }}</div>
              </div>
              <div class="request-badge">{{ endpoint.count }}</div>
            </div>
            <div class="metrics-grid">
              <div class="metric-item">
                <div class="metric-label">Max</div>
                <div class="metric-value">{{ FormattingService.formatDuration2Units(endpoint.maxTime) }}</div>
              </div>
              <div class="metric-item">
                <div class="metric-label">P99</div>
                <div class="metric-value">{{ FormattingService.formatDuration2Units(endpoint.p99Time) }}</div>
              </div>
              <div class="metric-item" :class="{ 'has-4xx': endpoint.count4xx > 0 }">
                <div class="metric-label">4xx</div>
                <div class="metric-value" :class="{ 'has-errors': endpoint.count4xx > 0 }">{{ endpoint.count4xx }}</div>
              </div>
              <div class="metric-item" :class="{ 'has-5xx': endpoint.count5xx > 0 }">
                <div class="metric-label">5xx</div>
                <div class="metric-value" :class="{ 'has-errors': endpoint.count5xx > 0, 'critical-errors': endpoint.count5xx > 0 }">{{ endpoint.count5xx }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Charts Section -->
      <section class="dashboard-section">
        <div class="charts-grid">
          <!-- Response Time Timeline -->
          <div class="chart-card">
            <div class="chart-header">
              <h4><i class="bi bi-graph-up me-2"></i>Response Time Timeline</h4>
            </div>
            <div class="chart-container">
              <TimeSeriesLineGraph
                :primary-data="responseTimeTimeline"
                primary-title="Response Time (ms)"
                :visible-minutes="15"
              />
            </div>
          </div>

          <!-- Request Count Timeline -->
          <div class="chart-card">
            <div class="chart-header">
              <h4><i class="bi bi-bar-chart me-2"></i>Request Count Timeline</h4>
            </div>
            <div class="chart-container">
              <TimeSeriesLineGraph
                :primary-data="requestCountTimeline"
                primary-title="Requests per Minute"
                :visible-minutes="15"
              />
            </div>
          </div>
        </div>
      </section>

      <!-- Status Codes and Methods Distribution -->
      <section class="dashboard-section">
        <div class="charts-grid">
          <!-- Status Codes Pie Chart -->
          <div class="chart-card">
            <div class="chart-header">
              <h4><i class="bi bi-pie-chart me-2"></i>Status Code Distribution</h4>
            </div>
            <div class="chart-container">
              <div ref="statusCodeChart" class="apex-chart"></div>
            </div>
          </div>

          <!-- HTTP Methods Pie Chart -->
          <div class="chart-card">
            <div class="chart-header">
              <h4><i class="bi bi-diagram-3 me-2"></i>HTTP Methods Distribution</h4>
            </div>
            <div class="chart-container">
              <div ref="httpMethodChart" class="apex-chart"></div>
            </div>
          </div>
        </div>
      </section>

      <!-- Slowest Requests Table -->
      <section class="dashboard-section">
        <h3 class="section-title">Slowest HTTP Requests</h3>
        <div class="card">
          <div class="card-body p-0">
            <table class="table table-hover mb-0 http-table">
              <thead>
                <tr>
                  <th>URI</th>
                  <th>Status</th>
                  <th class="text-center">Duration</th>
                  <th class="text-center">Request Size</th>
                  <th class="text-center">Response Size</th>
                  <th>Host</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="request in slowestRequests" :key="request.id" class="request-row">
                  <td class="uri-cell">
                    <div class="uri-with-method">
                      <span class="http-method-badge" :class="`method-${request.method.toLowerCase()}`">
                        {{ request.method }}
                      </span>
                      <span class="uri-text" :title="request.uri">{{ truncateUri(request.uri, 50) }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="status-badge" :class="getStatusBadgeClass(request.status)">
                      {{ request.status }}
                    </span>
                  </td>
                  <td class="text-center">{{ FormattingService.formatDuration2Units(request.duration) }}</td>
                  <td class="text-center">{{ FormattingService.formatBytes(request.requestLength) }}</td>
                  <td class="text-center">{{ FormattingService.formatBytes(request.responseLength) }}</td>
                  <td>{{ request.remoteHost }}:{{ request.remotePort }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Host Analysis Table -->
      <section class="dashboard-section">
        <h3 class="section-title">Host Analysis</h3>
        <div class="card">
          <div class="card-body p-0">
            <table class="table table-hover mb-0 http-table">
              <thead>
                <tr>
                  <th>Host</th>
                  <th class="text-center">Requests</th>
                  <th class="text-center">Avg Response Time</th>
                  <th class="text-center">Success Rate</th>
                  <th class="text-center">Total Data</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="host in hostAnalysis" :key="host.host" class="host-row">
                  <td class="host-cell">{{ host.host }}</td>
                  <td class="text-center">{{ host.requestCount.toLocaleString() }}</td>
                  <td class="text-center">{{ host.avgResponseTime.toFixed(0) }}ms</td>
                  <td class="text-center">
                    <span :class="getStatusClass(host.successRate)">
                      {{ host.successRate.toFixed(1) }}%
                    </span>
                  </td>
                  <td class="text-center">{{ FormattingService.formatBytes(host.totalData) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>

    <!-- No data state -->
    <div v-else class="p-4 text-center">
      <h3 class="text-muted">No HTTP Data Available</h3>
      <p class="text-muted">No HTTP exchange events found for this profile</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import ApexCharts from 'apexcharts';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import FormattingService from '@/services/FormattingService.ts';

// Props definition
defineProps({
  profile: {
    type: Object,
    required: true
  },
  secondaryProfile: {
    type: Object,
    default: null
  }
});

// Reactive state
const httpData = ref<any[]>([]);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedEndpoint = ref<string | null>(null);
const showAllEndpoints = ref(false);
const sortBy = ref('maxTime'); // 'requests', 'maxTime', 'p99Time', '4xx', '5xx'

// Chart refs
const statusCodeChart = ref<HTMLElement | null>(null);
const httpMethodChart = ref<HTMLElement | null>(null);
let statusCodeChartInstance: ApexCharts | null = null;
let httpMethodChartInstance: ApexCharts | null = null;

// Mock data generation (replace with actual API calls)
const generateMockHttpData = () => {
  const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
  const statuses = [200, 201, 204, 400, 401, 403, 404, 500, 502, 503];
  const hosts = ['api.example.com', 'service.app.com', 'external-api.net', 'backend.local'];
  
  // 13 URIs with different performance characteristics
  const uriProfiles = [
    { uri: '/api/users', baseTime: 50, variance: 200, errorRate: 0.02 }, // Fast, reliable
    { uri: '/api/orders', baseTime: 120, variance: 800, errorRate: 0.05 }, // Medium, some spikes
    { uri: '/api/products/search', baseTime: 200, variance: 1500, errorRate: 0.08 }, // Slower search
    { uri: '/api/reports/analytics', baseTime: 800, variance: 4000, errorRate: 0.15 }, // Heavy analytics
    { uri: '/api/upload/files', baseTime: 1200, variance: 8000, errorRate: 0.12 }, // File uploads
    { uri: '/api/auth/login', baseTime: 80, variance: 300, errorRate: 0.06 }, // Auth endpoint
    { uri: '/api/notifications', baseTime: 30, variance: 100, errorRate: 0.01 }, // Very fast
    { uri: '/api/admin/users', baseTime: 300, variance: 2000, errorRate: 0.10 }, // Admin heavy
    { uri: '/api/integrations/external', baseTime: 2000, variance: 10000, errorRate: 0.20 }, // External calls
    { uri: '/health', baseTime: 5, variance: 20, errorRate: 0.001 }, // Health check
    { uri: '/api/payments/process', baseTime: 600, variance: 3000, errorRate: 0.08 }, // Payment processing
    { uri: '/api/inventory/sync', baseTime: 1500, variance: 6000, errorRate: 0.18 }, // Inventory sync
    { uri: '/api/cache/refresh', baseTime: 400, variance: 2500, errorRate: 0.05 } // Cache operations
  ];

  const data = [];
  const now = Date.now();
  
  for (let i = 0; i < 1500; i++) {
    const timestamp = now - Math.random() * 24 * 60 * 60 * 1000; // Last 24 hours
    const method = methods[Math.floor(Math.random() * methods.length)];
    const host = hosts[Math.floor(Math.random() * hosts.length)];
    
    // Select URI profile with weighted distribution
    const uriProfile = uriProfiles[Math.floor(Math.random() * uriProfiles.length)];
    
    // Generate duration based on URI profile characteristics
    const baseDuration = uriProfile.baseTime + (Math.random() * uriProfile.variance);
    const duration = Math.max(5, baseDuration + (Math.random() - 0.5) * uriProfile.variance * 0.5);
    
    // Generate status based on error rate
    let status;
    if (Math.random() < uriProfile.errorRate) {
      // Generate error status
      const errorStatuses = [400, 401, 403, 404, 500, 502, 503];
      status = errorStatuses[Math.floor(Math.random() * errorStatuses.length)];
    } else {
      // Generate success status
      const successStatuses = [200, 201, 204];
      status = successStatuses[Math.floor(Math.random() * successStatuses.length)];
    }
    
    data.push({
      id: i,
      timestamp,
      method,
      status,
      remoteHost: host,
      remotePort: 443,
      uri: uriProfile.uri,
      duration,
      requestLength: Math.random() * 10000,
      responseLength: Math.random() * 50000,
      mediaType: 'application/json'
    });
  }
  
  return data.sort((a, b) => b.timestamp - a.timestamp);
};

// Computed properties
const totalRequests = computed(() => httpData.value.length);

const uniqueHosts = computed(() => 
  [...new Set(httpData.value.map(req => req.remoteHost))]
);

const averageResponseTime = computed(() => {
  if (httpData.value.length === 0) return 0;
  return httpData.value.reduce((sum, req) => sum + req.duration, 0) / httpData.value.length;
});

const p95ResponseTime = computed(() => {
  if (httpData.value.length === 0) return 0;
  const sorted = [...httpData.value].sort((a, b) => a.duration - b.duration);
  const p95Index = Math.floor(sorted.length * 0.95);
  return sorted[p95Index]?.duration || 0;
});

const successRate = computed(() => {
  if (httpData.value.length === 0) return 0;
  const successCount = httpData.value.filter(req => req.status >= 200 && req.status < 400).length;
  return (successCount / httpData.value.length) * 100;
});

const errorCount = computed(() => 
  httpData.value.filter(req => req.status >= 400).length
);

const totalDataTransferred = computed(() => 
  httpData.value.reduce((sum, req) => sum + req.requestLength + req.responseLength, 0)
);

const averageRequestSize = computed(() => {
  if (httpData.value.length === 0) return 0;
  return httpData.value.reduce((sum, req) => sum + req.requestLength, 0) / httpData.value.length;
});

const allEndpoints = computed(() => {
  const endpointMap = new Map();
  
  httpData.value.forEach(req => {
    const uri = req.uri;
    if (!endpointMap.has(uri)) {
      endpointMap.set(uri, {
        uri: uri,
        count: 0,
        totalTime: 0,
        durations: [],
        count4xx: 0,
        count5xx: 0
      });
    }
    
    const endpoint = endpointMap.get(uri);
    endpoint.count++;
    endpoint.totalTime += req.duration;
    endpoint.durations.push(req.duration);
    
    if (req.status >= 400 && req.status < 500) {
      endpoint.count4xx++;
    } else if (req.status >= 500) {
      endpoint.count5xx++;
    }
  });
  
  const endpoints = Array.from(endpointMap.values())
    .map(endpoint => {
      const sortedDurations = endpoint.durations.sort((a, b) => a - b);
      const p99Index = Math.floor(sortedDurations.length * 0.99);
      
      return {
        ...endpoint,
        maxTime: Math.max(...endpoint.durations),
        p99Time: sortedDurations[p99Index] || 0
      };
    });
  
  // Sort based on selected criteria
  switch (sortBy.value) {
    case 'maxTime':
      return endpoints.sort((a, b) => b.maxTime - a.maxTime);
    case 'p99Time':
      return endpoints.sort((a, b) => b.p99Time - a.p99Time);
    case '4xx':
      return endpoints.sort((a, b) => b.count4xx - a.count4xx);
    case '5xx':
      return endpoints.sort((a, b) => b.count5xx - a.count5xx);
    default: // 'requests'
      return endpoints.sort((a, b) => b.count - a.count);
  }
});

const displayedEndpoints = computed(() => {
  return showAllEndpoints.value ? allEndpoints.value : allEndpoints.value.slice(0, 9);
});

const slowestRequests = computed(() => 
  [...httpData.value]
    .sort((a, b) => b.duration - a.duration)
    .slice(0, 20)
);

const hostAnalysis = computed(() => {
  const hostMap = new Map();
  
  httpData.value.forEach(req => {
    const host = req.remoteHost;
    if (!hostMap.has(host)) {
      hostMap.set(host, {
        host,
        requestCount: 0,
        totalTime: 0,
        successCount: 0,
        totalData: 0
      });
    }
    
    const hostData = hostMap.get(host);
    hostData.requestCount++;
    hostData.totalTime += req.duration;
    hostData.totalData += req.requestLength + req.responseLength;
    if (req.status >= 200 && req.status < 400) {
      hostData.successCount++;
    }
  });
  
  return Array.from(hostMap.values())
    .map(host => ({
      ...host,
      avgResponseTime: host.totalTime / host.requestCount,
      successRate: (host.successCount / host.requestCount) * 100
    }))
    .sort((a, b) => b.requestCount - a.requestCount);
});

const responseTimeTimeline = computed(() => {
  // Group by minute and calculate average response time
  const timeMap = new Map();
  
  httpData.value.forEach(req => {
    const minute = Math.floor(req.timestamp / 60000) * 60000;
    if (!timeMap.has(minute)) {
      timeMap.set(minute, { total: 0, count: 0 });
    }
    const data = timeMap.get(minute);
    data.total += req.duration;
    data.count++;
  });
  
  return Array.from(timeMap.entries())
    .map(([timestamp, data]) => [timestamp, data.total / data.count])
    .sort((a, b) => a[0] - b[0]);
});

const requestCountTimeline = computed(() => {
  // Group by minute and count requests
  const timeMap = new Map();
  
  httpData.value.forEach(req => {
    const minute = Math.floor(req.timestamp / 60000) * 60000;
    timeMap.set(minute, (timeMap.get(minute) || 0) + 1);
  });
  
  return Array.from(timeMap.entries())
    .sort((a, b) => a[0] - b[0]);
});

// Helper functions
const truncateUri = (uri: string, maxLength: number = 30): string => {
  if (uri.length <= maxLength) return uri;
  return uri.substring(0, maxLength - 3) + '...';
};

const getStatusClass = (rate: number): string => {
  if (rate >= 95) return 'text-success';
  if (rate >= 90) return 'text-warning';
  return 'text-danger';
};

const getStatusBadgeClass = (status: number): string => {
  if (status >= 200 && status < 300) return 'status-success';
  if (status >= 300 && status < 400) return 'status-redirect';
  if (status >= 400 && status < 500) return 'status-client-error';
  return 'status-server-error';
};

// Chart creation functions
const createStatusCodeChart = async () => {
  if (!statusCodeChart.value || httpData.value.length === 0) return;
  
  // Destroy existing chart if it exists
  if (statusCodeChartInstance) {
    statusCodeChartInstance.destroy();
    statusCodeChartInstance = null;
  }
  
  const statusMap = new Map();
  httpData.value.forEach(req => {
    const statusGroup = Math.floor(req.status / 100) * 100;
    const label = `${statusGroup}s`;
    statusMap.set(label, (statusMap.get(label) || 0) + 1);
  });
  
  const series = Array.from(statusMap.values());
  const labels = Array.from(statusMap.keys());
  
  if (series.length === 0) return;
  
  const options = {
    series,
    chart: {
      type: 'donut',
      height: 350,
      animations: {
        enabled: true
      }
    },
    labels,
    colors: ['#28a745', '#17a2b8', '#ffc107', '#dc3545'],
    legend: {
      position: 'bottom',
      fontSize: '14px'
    },
    plotOptions: {
      pie: {
        donut: {
          size: '60%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Total',
              formatter: () => httpData.value.length.toString()
            }
          }
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => Math.round(val) + '%'
    },
    responsive: [{
      breakpoint: 480,
      options: {
        chart: {
          height: 300
        },
        legend: {
          position: 'bottom'
        }
      }
    }]
  };
  
  try {
    statusCodeChartInstance = new ApexCharts(statusCodeChart.value, options);
    await statusCodeChartInstance.render();
  } catch (error) {
    console.error('Error creating status code chart:', error);
  }
};

const createHttpMethodChart = async () => {
  if (!httpMethodChart.value || httpData.value.length === 0) return;
  
  // Destroy existing chart if it exists
  if (httpMethodChartInstance) {
    httpMethodChartInstance.destroy();
    httpMethodChartInstance = null;
  }
  
  const methodMap = new Map();
  httpData.value.forEach(req => {
    methodMap.set(req.method, (methodMap.get(req.method) || 0) + 1);
  });
  
  const series = Array.from(methodMap.values());
  const labels = Array.from(methodMap.keys());
  
  if (series.length === 0) return;
  
  const options = {
    series,
    chart: {
      type: 'pie',
      height: 350,
      animations: {
        enabled: true
      }
    },
    labels,
    colors: ['#007bff', '#28a745', '#ffc107', '#dc3545', '#6f42c1'],
    legend: {
      position: 'bottom',
      fontSize: '14px'
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => Math.round(val) + '%'
    },
    tooltip: {
      y: {
        formatter: (val: number) => val + ' requests'
      }
    },
    responsive: [{
      breakpoint: 480,
      options: {
        chart: {
          height: 300
        },
        legend: {
          position: 'bottom'
        }
      }
    }]
  };
  
  try {
    httpMethodChartInstance = new ApexCharts(httpMethodChart.value, options);
    await httpMethodChartInstance.render();
  } catch (error) {
    console.error('Error creating HTTP method chart:', error);
  }
};

// Lifecycle methods
const loadHttpData = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    
    // Replace with actual API call
    httpData.value = generateMockHttpData();
    
    // Wait for DOM updates and chart container to be available
    await nextTick();
    
    // Add a small delay to ensure DOM is fully rendered
    setTimeout(async () => {
      try {
        await createStatusCodeChart();
        await createHttpMethodChart();
      } catch (chartError) {
        console.error('Error creating charts:', chartError);
      }
    }, 100);
    
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading HTTP data:', err);
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  loadHttpData();
});

onUnmounted(() => {
  if (statusCodeChartInstance) {
    statusCodeChartInstance.destroy();
  }
  if (httpMethodChartInstance) {
    httpMethodChartInstance.destroy();
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 1.5rem;
}

.dashboard-section {
  margin-bottom: 2rem;
}

.section-title {
  color: #2c3e50;
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e9ecef;
}

.endpoints-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: #f8f9fa;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.sort-controls {
  display: flex;
  align-items: center;
}

.sort-button-group {
  display: flex;
  gap: 0.5rem;
}

.sort-btn {
  background: #f8faff;
  border: 1px solid #d1d9f0;
  color: #667eea;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  padding: 0.375rem 0.75rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.sort-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.sort-btn.active {
  background: #667eea;
  color: white;
  border-color: #667eea;
  font-weight: 600;
}

.sort-btn.active:hover {
  background: #5a67d8;
  border-color: #5a67d8;
}

.show-all-link {
  background: #f8faff;
  border: 1px solid #d1d9f0;
  color: #667eea;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  padding: 0.375rem 0.75rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.show-all-link:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.http-endpoint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
  max-width: none;
}

@media (min-width: 1200px) {
  .http-endpoint-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .http-endpoint-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .http-endpoint-grid {
    grid-template-columns: 1fr;
  }
}

.endpoint-card {
  background: white;
  border: 1px solid #e1e5e9;
  border-radius: 8px;
  padding: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  position: relative;
  overflow: hidden;
}

.endpoint-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.endpoint-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  border-color: #c3d4f4;
}

.endpoint-card:hover::before {
  opacity: 1;
}

.endpoint-card.selected {
  border-color: #667eea;
  background: #f8faff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
}

.endpoint-card.selected::before {
  opacity: 1;
}

.endpoint-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.uri-container {
  flex: 1;
  background: #f8f9fa;
  padding: 0.4rem 0.75rem;
  border-radius: 4px;
  margin-right: 0.75rem;
}

.uri-path {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: #495057;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}

.request-badge {
  background: #667eea;
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  min-width: 2rem;
  text-align: center;
  box-shadow: 0 1px 2px rgba(102, 126, 234, 0.3);
}

.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}

.metric-item {
  text-align: center;
  padding: 0.4rem;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.metric-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.025em;
  margin-bottom: 0.2rem;
}

.metric-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: #2d3748;
  line-height: 1;
}

.metric-item.has-4xx {
  background: #fffbeb;
  border-color: #fbbf24;
}

.metric-item.has-5xx {
  background: #fef2f2;
  border-color: #f87171;
}

.metric-value.has-errors {
  color: #d69e2e;
}

.metric-value.critical-errors {
  color: #e53e3e;
}


.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
}

.chart-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.chart-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
}

.chart-container {
  height: 400px;
  padding: 1rem;
}

.apex-chart {
  height: 100%;
}

.http-table {
  width: 100%;
  table-layout: fixed;
}

.http-table th:nth-child(1) { width: 40%; }
.http-table th:nth-child(2) { width: 7%; }
.http-table th:nth-child(3) { width: 13%; }
.http-table th:nth-child(4) { width: 10%; }
.http-table th:nth-child(5) { width: 10%; }
.http-table th:nth-child(6) { width: 10%; }

.http-method {
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.uri-cell {
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}

.uri-with-method {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.uri-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.http-method-badge {
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
}

.http-method-badge.method-get { background-color: #cce5ff; color: #004085; }
.http-method-badge.method-post { background-color: #d4edda; color: #155724; }
.http-method-badge.method-put { background-color: #fff3cd; color: #856404; }
.http-method-badge.method-delete { background-color: #f8d7da; color: #721c24; }
.http-method-badge.method-patch { background-color: #e2e3e5; color: #383d41; }

.status-badge {
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.status-success { background-color: #d4edda; color: #155724; }
.status-redirect { background-color: #cce5ff; color: #004085; }
.status-client-error { background-color: #fff3cd; color: #856404; }
.status-server-error { background-color: #f8d7da; color: #721c24; }

.host-cell {
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
}

.request-row:hover,
.host-row:hover {
  background-color: #f8f9fa;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.alert {
  border-radius: 8px;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.spinner-border {
  width: 3rem;
  height: 3rem;
}

@media (max-width: 768px) {
  .dashboard-grid,
  .http-endpoint-grid {
    grid-template-columns: 1fr;
  }
  
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .dashboard-container {
    padding: 1rem;
  }
  
  .chart-container {
    height: 300px;
  }
}
</style>

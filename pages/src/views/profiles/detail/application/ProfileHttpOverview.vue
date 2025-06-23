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
    <div v-else-if="httpOverviewData" class="dashboard-container">
      <!-- HTTP Overview Cards -->
      <section class="dashboard-section">
        <div class="dashboard-grid">
          <DashboardCard
            title="Total Requests"
            :value="httpOverviewData?.header.requestCount || 0"
            variant="info"
          />
          <DashboardCard
            title="Response Time"
            :value="FormattingService.formatDuration2Units(httpOverviewData?.header.maxResponseTime)"
            :valueA="FormattingService.formatDuration2Units(httpOverviewData?.header.p99ResponseTime)"
            :valueB="FormattingService.formatDuration2Units(httpOverviewData?.header.p95ResponseTime)"
            labelA="P99"
            labelB="P95"
            variant="highlight"
          />
          <DashboardCard
            title="Success Rate"
            :value="`${(httpOverviewData?.header.successRate * 100 || 0).toFixed(1)}%`"
            :valueA="httpOverviewData?.header.count4xx"
            :valueB="httpOverviewData?.header.count5xx"
            labelA="4xx Errors"
            labelB="5xx Errors"
            :variant="(httpOverviewData?.header.successRate || 0) == 1 ? 'success' : httpOverviewData?.header.count5xx > 0 ? 'danger' : 'warning'"
          />
          <DashboardCard
            title="Data Transferred"
            :value="httpOverviewData?.header.totalBytesTransferred < 0 ? '?' : FormattingService.formatBytes(httpOverviewData?.header.totalBytesTransferred)"
            :valueA="httpOverviewData?.header.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(httpOverviewData?.header.totalBytesReceived)"
            :valueB="httpOverviewData?.header.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(httpOverviewData?.header.totalBytesSent)"
            labelA="Received"
            labelB="Sent"
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
                @click="sortBy = 'maxResponseTime'"
                :class="['sort-btn', { 'active': sortBy === 'maxResponseTime' }]"
              >
                MAX
              </button>
              <button 
                @click="sortBy = 'p95ResponseTime'"
                :class="['sort-btn', { 'active': sortBy === 'p95ResponseTime' }]"
              >
                P95
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
                :class="['sort-btn', { 'active': sortBy === 'requestCount' }]"
              >
                Requests
              </button>
            </div>
          </div>
          
          <button 
            v-if="getAllEndpoints().length > maxDisplayedEndpoints"
            @click="showAllEndpoints = !showAllEndpoints"
            class="show-all-link"
          >
            {{ showAllEndpoints ? 'Show Less' : `Show All (${getAllEndpoints().length})` }}
          </button>
        </div>
        
        <div class="endpoint-list">
          <div 
            v-for="endpoint in getDisplayedEndpoints()" 
            :key="endpoint.uri"
            class="endpoint-row"
            @click="navigateToUri(endpoint.uri)"
            :class="{ 
              'selected': selectedEndpoint === endpoint.uri,
              'has-4xx-errors': endpoint.count4xx > 0 && endpoint.count5xx === 0,
              'has-5xx-errors': endpoint.count5xx > 0
            }"
          >
            <div class="endpoint-primary">
              <div class="endpoint-uri">
                <div class="uri-display" :title="endpoint.uri">
                  <span class="uri-separator">/</span>
                  <span v-for="(part, index) in parseUri(endpoint.uri)" :key="index" class="uri-part">
                    <span v-if="index > 0" class="uri-separator">/</span>
                    <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
                    <span v-else class="uri-segment">{{ part.text }}</span>
                  </span>
                </div>
                <div class="endpoint-badges">
                  <div class="request-count-badge">
                    <span class="count-number">{{ endpoint.requestCount.toLocaleString() }}</span>
                    <span class="count-label">requests</span>
                  </div>
                  <div class="metric-badge info-badge">
                    <span class="metric-number">{{ FormattingService.formatDuration2Units(endpoint.maxResponseTime) }}</span>
                    <span class="metric-label">Max</span>
                  </div>
                  <div class="metric-badge info-badge">
                    <span class="metric-number">{{ FormattingService.formatDuration2Units(endpoint.p99ResponseTime) }}</span>
                    <span class="metric-label">P99</span>
                  </div>
                  <div class="metric-badge info-badge">
                    <span class="metric-number">{{ FormattingService.formatDuration2Units(endpoint.p95ResponseTime) }}</span>
                    <span class="metric-label">P95</span>
                  </div>
                  <div class="metric-badge secondary-badge" 
                       :title="endpoint.totalBytesReceived < 0 ? 'Cannot determine transmitted bytes, e.g. missing Content-Length header' : ''">
                    <span class="metric-number">{{ endpoint.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(endpoint.totalBytesReceived) }}</span>
                    <span class="metric-label">Received</span>
                  </div>
                  <div class="metric-badge secondary-badge"
                       :title="endpoint.totalBytesSent < 0 ? 'Cannot determine transmitted bytes, e.g. missing Content-Length header' : ''">
                    <span class="metric-number">{{ endpoint.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(endpoint.totalBytesSent) }}</span>
                    <span class="metric-label">Sent</span>
                  </div>
                  <div class="metric-badge" :class="endpoint.count4xx > 0 ? 'warning-badge' : 'success-badge'">
                    <span class="metric-number">{{ endpoint.count4xx }}</span>
                    <span class="metric-label">4xx</span>
                  </div>
                  <div class="metric-badge" :class="endpoint.count5xx > 0 ? 'danger-badge' : 'success-badge'">
                    <span class="metric-number">{{ endpoint.count5xx }}</span>
                    <span class="metric-label">5xx</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Charts Section -->
      <section class="dashboard-section">
        <div class="charts-grid">
          <!-- Combined Response Time and Request Count Timeline -->
          <div class="chart-card full-width">
            <div class="chart-header">
              <h4><i class="bi bi-graph-up me-2"></i>HTTP Metrics Timeline</h4>
            </div>
            <div class="chart-container">
              <TimeSeriesLineGraph
                :primary-data="httpOverviewData?.responseTimeSerie.data || []"
                primary-title="Response Time"
                :secondary-data="httpOverviewData?.requestCountSerie.data || []"
                secondary-title="Request Count"
                :visible-minutes="15"
                :independentSecondaryAxis="true"
                primary-axis-type="duration"
                secondary-axis-type="number"
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
                  <th class="text-center">Response Time</th>
                  <th class="text-center">Data Transferred</th>
                  <th>Host</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="request in getSortedSlowRequests()"
                    class="request-row">
                  <td class="uri-cell">
                    <div class="request-uri-display">
                      <span class="http-method-badge" :class="`method-${request.method.toLowerCase()}`">
                        {{ request.method }}
                      </span>
                      <div class="uri-display-table" :title="request.uri">
                        <span class="uri-separator">/</span>
                        <span v-for="(part, index) in parseUri(request.uri)" :key="index" class="uri-part">
                          <span v-if="index > 0" class="uri-separator">/</span>
                          <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
                          <span v-else class="uri-segment">{{ part.text }}</span>
                        </span>
                      </div>
                    </div>
                    <div class="uri-meta">
                      <div class="uri-timestamp">
                        <i class="bi bi-clock"></i>
                        <span class="timestamp-value">{{ FormattingService.formatTimestamp(request.timestamp).replace('T', ' ') }}</span>
                      </div>
                      <span class="status-badge" :class="getStatusBadgeClass(request.statusCode)">
                        {{ request.statusCode }}
                      </span>
                    </div>
                  </td>
                  <td class="text-center">{{ FormattingService.formatDuration2Units(request.responseTime) }}</td>
                  <td class="text-center">
                    <div class="data-transferred">
                      <div class="data-item">
                        <span class="data-label">Req:</span>
                        <span class="data-value">{{ FormattingService.formatBytes(request.requestSize) }}</span>
                      </div>
                      <div class="data-item">
                        <span class="data-label">Res:</span>
                        <span class="data-value">{{ FormattingService.formatBytes(request.responseSize) }}</span>
                      </div>
                    </div>
                  </td>
                  <td>{{ request.host }}:{{ request.port }}</td>
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ApexCharts from 'apexcharts';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import FormattingService from '@/services/FormattingService.ts';
import ProfileHttpOverviewClient from '@/services/profile/custom/jdbc/ProfileHttpOverviewClient.ts';
import HttpOverviewData from '@/services/profile/custom/http/HttpOverviewData.ts';

const route = useRoute();
const router = useRouter();

// Reactive state
const httpOverviewData = ref<HttpOverviewData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const selectedEndpoint = ref<string | null>(null);
const showAllEndpoints = ref(false);
const sortBy = ref('maxResponseTime'); // 'requestCount', 'maxResponseTime', 'p95ResponseTime', '4xx', '5xx'
const maxDisplayedEndpoints = 10;

// Client initialization
const client = new ProfileHttpOverviewClient(route.params.projectId as string, route.params.profileId as string);

// Chart refs
const statusCodeChart = ref<HTMLElement | null>(null);
const httpMethodChart = ref<HTMLElement | null>(null);
let statusCodeChartInstance: ApexCharts | null = null;
let httpMethodChartInstance: ApexCharts | null = null;

// Helper functions to get data directly
const getAllEndpoints = () => {
  if (!httpOverviewData.value) return [];
  
  const endpoints = httpOverviewData.value.uris;
  
  // Sort based on selected criteria
  switch (sortBy.value) {
    case 'maxResponseTime':
      return endpoints.sort((a, b) => b.maxResponseTime - a.maxResponseTime);
    case 'p95ResponseTime':
      return endpoints.sort((a, b) => b.p95ResponseTime - a.p95ResponseTime);
    case '4xx':
      return endpoints.sort((a, b) => b.count4xx - a.count4xx);
    case '5xx':
      return endpoints.sort((a, b) => b.count5xx - a.count5xx);
    default: // 'requests'
      return endpoints.sort((a, b) => b.requestCount - a.requestCount);
  }
};

const getDisplayedEndpoints = () => {
  const allEndpoints = getAllEndpoints();
  return showAllEndpoints.value ? allEndpoints : allEndpoints.slice(0, maxDisplayedEndpoints);
};

const getSortedSlowRequests = () => {
  if (!httpOverviewData.value) return [];
  
  // Sort slow requests by response time in descending order (slowest first)
  return [...httpOverviewData.value.slowRequests].sort((a, b) => b.responseTime - a.responseTime);
};

const parseUri = (uri: string) => {
  if (!uri) return [];
  
  const segments = uri.split('/').filter(segment => segment.length > 0);
  
  return segments.map(segment => ({
    text: segment,
    isVariable: segment.startsWith('{') && segment.endsWith('}')
  }));
};

const getStatusBadgeClass = (status: number): string => {
  if (status >= 200 && status < 300) return 'status-success';
  if (status >= 300 && status < 400) return 'status-redirect';
  if (status >= 400 && status < 500) return 'status-client-error';
  return 'status-server-error';
};

// Navigation method
const navigateToUri = (uri: string) => {
  router.push({ 
    name: 'profile-application-http-endpoints',
    query: { uri: encodeURIComponent(uri) } 
  });
};

// Chart creation functions
const createStatusCodeChart = async () => {
  if (!statusCodeChart.value || !httpOverviewData.value) return;
  
  // Destroy existing chart if it exists
  if (statusCodeChartInstance) {
    statusCodeChartInstance.destroy();
    statusCodeChartInstance = null;
  }
  
  const series = httpOverviewData.value.statusCodes.map(status => status.count);
  const labels = httpOverviewData.value.statusCodes.map(status => status.code.toString());
  
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
    colors: labels.map(statusCode => {
      const code = parseInt(statusCode);
      if (code >= 200 && code < 300) return '#5cb85c'; // medium green (success)
      if (code >= 300 && code < 400) return '#5a9fd4'; // medium blue (redirect)
      if (code >= 400 && code < 500) return '#f0ad4e'; // medium orange (client error)
      if (code >= 500) return '#d9534f'; // medium red (server error)
      return '#6c757d'; // medium gray for other codes
    }),
    legend: {
      position: 'right',
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
              formatter: () => httpOverviewData.value?.header.requestCount.toString() || '0'
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
          position: 'right'
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
  if (!httpMethodChart.value || !httpOverviewData.value) return;
  
  // Destroy existing chart if it exists
  if (httpMethodChartInstance) {
    httpMethodChartInstance.destroy();
    httpMethodChartInstance = null;
  }
  
  const series = httpOverviewData.value.methods.map(method => method.count);
  const labels = httpOverviewData.value.methods.map(method => method.method);
  
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
    colors: labels.map(method => {
      switch (method.toUpperCase()) {
        case 'GET': return '#5a9fd4'; // medium blue
        case 'POST': return '#5cb85c'; // medium green
        case 'PUT': return '#f0ad4e'; // medium orange
        case 'DELETE': return '#d9534f'; // medium red
        case 'PATCH': return '#6c757d'; // medium gray
        case 'OPTIONS': return '#9b59b6'; // medium purple
        default: return '#95a5a6'; // medium gray for other methods
      }
    }),
    legend: {
      position: 'right',
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
              formatter: () => httpOverviewData.value?.header.requestCount.toString() || '0'
            }
          }
        }
      }
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
          position: 'right'
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
    
    // Load data from API
    httpOverviewData.value = await client.getOverview();
    
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

.endpoint-list {
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.endpoint-row {
  border-bottom: 1px solid #f1f3f4;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.endpoint-row:last-child {
  border-bottom: none;
}

.endpoint-row:hover {
  background: #f8f9fa;
}

.endpoint-row.selected {
  background: #f8faff;
  border-left: 4px solid #667eea;
}

.endpoint-row.has-4xx-errors {
  background: #fffbeb !important;
}

.endpoint-row.has-5xx-errors {
  background: #fef2f2 !important;
}

.endpoint-row.selected.has-4xx-errors {
  background: #fef7e8;
  border-left: 4px solid #667eea;
}

.endpoint-row.selected.has-5xx-errors {
  background: #fceded;
  border-left: 4px solid #667eea;
}

.endpoint-primary {
  padding: 0.75rem 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.endpoint-uri {
  flex: 1;
  min-width: 0;
}

.uri-display {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  font-style: italic;
  background: #f7fafc;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.125rem;
  max-width: 100%;
}

.uri-part {
  display: flex;
  align-items: center;
}

.uri-separator {
  color: #000000;
  font-weight: 400;
  margin: 0 0.125rem;
}

.uri-segment {
  color: #2d3748;
  font-weight: 500;
}

.uri-variable {
  color: #718096;
  font-weight: 400;
  font-style: italic;
}

.request-uri-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.uri-display-table {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  font-style: italic;
  background: #f7fafc;
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.125rem;
  flex: 1;
}

.endpoint-badges {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
}

.request-count-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #5e64ff;
  color: white;
  padding: 0.375rem 0.75rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.2);
  min-width: 70px;
}

.count-number {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 0.125rem;
}

.count-label {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.9;
  font-weight: 500;
}


.metric-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.375rem 0.75rem;
  border-radius: 8px;
  min-width: 70px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.metric-badge .metric-number {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 0.125rem;
}

.metric-badge .metric-label {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.9;
  font-weight: 500;
}

/* Badge variants */
.info-badge {
  background: #39afd1;
  color: white;
}

.secondary-badge {
  background: #7780bf;
  color: white;
}

.success-badge {
  background: #00d27a;
  color: white;
}

.warning-badge {
  background: #f5803e;
  color: white;
}

.danger-badge {
  background: #e63757;
  color: white;
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

.chart-card.full-width {
  grid-column: 1 / -1;
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

.chart-card.full-width .chart-container {
  height: 500px;
}

.apex-chart {
  height: 100%;
}

.http-table {
  width: 100%;
  table-layout: fixed;
}

.http-table th:nth-child(1) { width: 58%; }
.http-table th:nth-child(2) { width: 15%; }
.http-table th:nth-child(3) { width: 12%; }
.http-table th:nth-child(4) { width: 15%; }

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

.uri-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.http-method-badge {
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  min-height: 2rem;
}

.http-method-badge.method-get { background-color: #cce5ff; color: #004085; }
.http-method-badge.method-post { background-color: #d4edda; color: #155724; }
.http-method-badge.method-put { background-color: #fff3cd; color: #856404; }
.http-method-badge.method-delete { background-color: #f8d7da; color: #721c24; }
.http-method-badge.method-patch { background-color: #e2e3e5; color: #383d41; }
.http-method-badge.method-options { background-color: #f3e2f3; color: #6f2c91; }

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

.data-transferred {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.75rem;
}

.data-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.data-label {
  font-weight: 600;
  color: #6b7280;
  margin-right: 0.5rem;
}

.data-value {
  font-weight: 500;
  color: #374151;
}

.uri-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
}

.uri-timestamp {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0.25rem 0.6rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
  width: fit-content;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  gap: 0.4rem;
}


.uri-timestamp i {
  font-size: 0.7rem;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.timestamp-value {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-weight: 500;
  letter-spacing: 0.015em;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
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
  .dashboard-grid {
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
  
  .endpoint-badges {
    gap: 0.25rem;
  }
  
  .metric-badge {
    min-width: 60px;
    padding: 0.3rem 0.6rem;
  }
  
  .metric-badge .metric-number {
    font-size: 0.8rem;
  }
  
  .metric-badge .metric-label {
    font-size: 0.55rem;
  }
}
</style>

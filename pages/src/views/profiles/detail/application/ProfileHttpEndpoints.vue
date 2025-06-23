<template>
  <div>
    <DashboardHeader title="Endpoint Details" icon="share"/>

    <!-- URI Display with Navigation -->
    <div v-if="selectedUriForDetail" class="uri-display-large">
      <div class="uri-content">
        <span class="uri-separator">/</span>
        <span v-for="(part, index) in parseUri(decodeURIComponent(selectedUriForDetail))" :key="index" class="uri-part">
          <span v-if="index > 0" class="uri-separator">/</span>
          <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
          <span v-else class="uri-segment">{{ part.text }}</span>
        </span>
      </div>
      
      <button 
        @click="clearUriSelection"
        class="btn btn-secondary uri-back-button"
      >
        <i class="bi bi-arrow-left me-2"></i>
        Back to All URIs
      </button>
    </div>
    
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

    <!-- Single URI Dashboard content -->
    <div v-else-if="selectedUriForDetail && selectedUriData" class="dashboard-container">
      <!-- URI Overview Cards -->
      <section class="dashboard-section">
        <div class="dashboard-grid">
          <DashboardCard
            title="Total Requests"
            :value="selectedUriData.requestCount || 0"
            variant="info"
          />
          <DashboardCard
            title="Response Time"
            :value="FormattingService.formatDuration2Units(selectedUriData.maxResponseTime)"
            :valueA="FormattingService.formatDuration2Units(selectedUriData.p99ResponseTime)"
            :valueB="FormattingService.formatDuration2Units(selectedUriData.p95ResponseTime)"
            labelA="P99"
            labelB="P95"
            variant="highlight"
          />
          <DashboardCard
              title="Success Rate"
              :value="`${(selectedUriData.successRate * 100 || 0).toFixed(1)}%`"
              :valueA="selectedUriData.count4xx"
              :valueB="selectedUriData.count5xx"
              labelA="4xx Errors"
              labelB="5xx Errors"
              :variant="(selectedUriData.successRate || 0) == 1 ? 'success' : selectedUriData.count5xx > 0 ? 'danger' : 'warning'"
          />
          <DashboardCard
            title="Data Transferred"
            :value="selectedUriData.totalBytesTransferred < 0 ? '?' : FormattingService.formatBytes(selectedUriData.totalBytesTransferred)"
            :valueA="selectedUriData.totalBytesReceived < 0 ? '?' : FormattingService.formatBytes(selectedUriData.totalBytesReceived)"
            :valueB="selectedUriData.totalBytesSent < 0 ? '?' : FormattingService.formatBytes(selectedUriData.totalBytesSent)"
            labelA="Received"
            labelB="Sent"
            variant="info"
          />
        </div>
      </section>


      <!-- Related Requests from All URIs -->
      <section class="dashboard-section" v-if="relatedRequests.length > 0">
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
                <tr v-for="request in relatedRequests.slice(0, 20)"
                    :key="request.timestamp"
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

    <!-- Endpoint List (copied from ProfileHttpOverview) -->
    <div v-else-if="httpOverviewData" class="dashboard-container">
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
            @click="selectUriForDetail(endpoint.uri)"
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
    </div>

    <!-- No data state -->
    <div v-else class="p-4 text-center">
      <h3 class="text-muted">No HTTP Data Available</h3>
      <p class="text-muted">No HTTP exchange events found for this profile</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
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
const selectedUriForDetail = ref<string | null>(null);

// Client initialization
const client = new ProfileHttpOverviewClient(route.params.projectId as string, route.params.profileId as string);

// Computed properties for single URI view
const selectedUriData = computed(() => {
  if (!httpOverviewData.value || !selectedUriForDetail.value) return null;
  
  const targetUri = decodeURIComponent(selectedUriForDetail.value);
  return httpOverviewData.value.uris.find(uri => uri.uri === targetUri) || null;
});

const relatedRequests = computed(() => {
  if (!httpOverviewData.value || !selectedUriForDetail.value) return [];
  
  const targetUri = decodeURIComponent(selectedUriForDetail.value);
  return httpOverviewData.value.slowRequests
    .filter(request => request.uri === targetUri)
    .sort((a, b) => b.responseTime - a.responseTime);
});

// Helper functions to get data directly (copied from ProfileHttpOverview)
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

// URI selection methods
const selectUriForDetail = (uri: string) => {
  selectedUriForDetail.value = uri;
  router.push({ 
    name: 'profile-application-http-endpoints',
    query: { uri: encodeURIComponent(uri) } 
  });
};

const clearUriSelection = () => {
  selectedUriForDetail.value = null;
  router.push({ 
    name: 'profile-application-http-endpoints'
  });
};

// Watch for route changes to handle direct navigation
watch(() => route.query.uri, (newUri) => {
  if (newUri && typeof newUri === 'string') {
    selectedUriForDetail.value = newUri;
  } else {
    selectedUriForDetail.value = null;
  }
}, { immediate: true });

// Lifecycle methods
const loadHttpData = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    
    // Load data from API
    httpOverviewData.value = await client.getOverview();
    
    // Check if the URI exists in the data when in detail view
    if (selectedUriForDetail.value && !selectedUriData.value) {
      error.value = `URI not found: ${decodeURIComponent(selectedUriForDetail.value)}`;
    }
    
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

.uri-display-large {
  background: #ffffff;
  padding: 1rem 1.5rem;
  border-radius: 8px;
  border: 2px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.uri-content {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 1.25rem;
  font-weight: 600;
  font-style: italic;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.25rem;
  flex: 1;
  min-width: 0;
}

.uri-back-button {
  flex-shrink: 0;
  white-space: nowrap;
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


.uri-cell {
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
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

.uri-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
}

.http-table {
  width: 100%;
  table-layout: fixed;
}

.http-table th:nth-child(1) { width: 58%; }
.http-table th:nth-child(2) { width: 15%; }
.http-table th:nth-child(3) { width: 12%; }
.http-table th:nth-child(4) { width: 15%; }

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

.request-row:hover {
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
  

  .uri-display-large {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
    padding: 0.75rem 1rem;
  }

  .uri-content {
    font-size: 1rem;
  }

  .uri-back-button {
    align-self: flex-start;
    order: -1;
  }
}
</style>

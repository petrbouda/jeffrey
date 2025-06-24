<template>
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
        @click="handleEndpointClick(endpoint.uri)"
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
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import FormattingService from '@/services/FormattingService.ts';

interface Endpoint {
  uri: string;
  requestCount: number;
  maxResponseTime: number;
  p99ResponseTime: number;
  p95ResponseTime: number;
  totalBytesReceived: number;
  totalBytesSent: number;
  count4xx: number;
  count5xx: number;
}

interface Props {
  endpoints: Endpoint[];
  selectedEndpoint?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedEndpoint: null
});

const emit = defineEmits<{
  endpointClick: [uri: string]
}>();

// Reactive state
const showAllEndpoints = ref(false);
const sortBy = ref('maxResponseTime');
const maxDisplayedEndpoints = 10;

// Helper functions
const getAllEndpoints = () => {
  if (!props.endpoints) return [];
  
  const endpoints = props.endpoints;
  
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

const handleEndpointClick = (uri: string) => {
  emit('endpointClick', uri);
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
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
  font-style: italic;
}

.uri-separator {
  color: #000000;
  font-weight: 400;
  margin: 0 0.125rem;
  font-style: italic;
}

.uri-segment {
  color: #2d3748;
  font-weight: 500;
  font-style: italic;
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

@media (max-width: 768px) {
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
<template>
  <section v-if="requests.length > 0" class="dashboard-section">
    <div class="chart-card">
      <div class="chart-header">
        <h4><i class="bi bi-clock-history me-2"></i>Slowest HTTP Requests</h4>
        <span class="badge bg-info">{{ requests.length }} of {{ totalRequestCount }} requests</span>
      </div>
      <div class="table-container">
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
            <tr v-for="request in displayedRequests"
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
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@/services/FormattingService.ts';

interface SlowRequest {
  uri: string;
  method: string;
  timestamp: number;
  statusCode: number;
  responseTime: number;
  requestSize: number;
  responseSize: number;
  host: string;
  port: number;
}

interface Props {
  requests: SlowRequest[];
  totalRequestCount: number;
  maxDisplayed?: number;
}

const props = withDefaults(defineProps<Props>(), {
  maxDisplayed: 20
});

const displayedRequests = computed(() => {
  return props.requests.slice(0, props.maxDisplayed);
});

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
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
}

.table-container {
  padding: 0;
}

.http-table {
  width: 100%;
  table-layout: fixed;
}

.http-table th:nth-child(1) { width: 58%; }
.http-table th:nth-child(2) { width: 15%; }
.http-table th:nth-child(3) { width: 12%; }
.http-table th:nth-child(4) { width: 15%; }

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

.uri-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
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
</style>
<template>
  <ChartSection v-if="requests.length > 0" title="Slowest HTTP Requests" icon="clock-history" :full-width="true">
    <div class="table-responsive">
      <table class="table table-hover http-table">
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
                  <Badge :value="request.method" :variant="getMethodVariant(request.method)" size="l" />
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
                  <Badge 
                    :value="FormattingService.formatTimestamp(request.timestamp).replace('T', ' ')"
                    icon="bi-clock"
                    variant="grey"
                    size="s"
                  />
                  <Badge :value="request.statusCode.toString()" :variant="getStatusVariant(request.statusCode)" size="s" />
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
  </ChartSection>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@/services/FormattingService.ts';
import Badge from '@/components/Badge.vue';
import ChartSection from '@/components/ChartSection.vue';

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

const getMethodVariant = (method: string): string => {
  const methodLower = method.toLowerCase();
  const variants: Record<string, string> = {
    get: 'blue',
    post: 'green', 
    put: 'yellow',
    delete: 'red',
    patch: 'grey',
    options: 'purple'
  };
  return variants[methodLower] || 'secondary';
};

const getStatusVariant = (status: number): string => {
  if (status >= 200 && status < 300) return 'success';
  if (status >= 300 && status < 400) return 'info';
  if (status >= 400 && status < 500) return 'warning';
  return 'danger';
};
</script>

<style scoped>
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
  padding: 0.375rem 0.625rem;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.125rem;
  flex: 1;
  min-height: 2rem;
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

</style>

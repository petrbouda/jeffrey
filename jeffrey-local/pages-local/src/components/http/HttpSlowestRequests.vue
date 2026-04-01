<template>
  <div v-if="requests.length > 0" class="slowest-list">
    <div v-for="request in displayedRequests"
         :key="request.timestamp"
         class="slowest-row">
      <div class="left-accent" :class="getAccentClass(request.statusCode)"></div>
      <div class="row-content">
        <div class="row-header">
          <div class="row-header-left">
            <Badge :value="request.method" :variant="getMethodVariant(request.method)" size="s" borderless />
            <div class="uri-path" :title="request.uri">
              <span class="uri-sep">/</span>
              <span v-for="(part, index) in parseUri(request.uri)" :key="index" class="uri-part">
                <span v-if="index > 0" class="uri-sep">/</span>
                <span v-if="part.isVariable" class="uri-var">{{ part.text }}</span>
                <span v-else>{{ part.text }}</span>
              </span>
            </div>
          </div>
          <div class="time-bar-wrap">
            <span class="time-bar-value">{{ FormattingService.formatDuration2Units(request.responseTime) }}</span>
            <div class="time-bar-track">
              <div class="time-bar-fill" :style="{ width: getTimePercentage(request.responseTime) + '%' }"></div>
            </div>
          </div>
        </div>
        <div class="row-details">
          <span class="detail-chip"><i class="bi bi-clock"></i> {{ FormattingService.formatTimestamp(request.timestamp).replace('T', ' ') }}</span>
          <span class="detail-dot">&middot;</span>
          <Badge :value="request.statusCode.toString()" :variant="getStatusVariant(request.statusCode)" size="xs" borderless />
          <span class="detail-dot">&middot;</span>
          <span class="detail-chip"><i class="bi bi-hdd-network"></i> {{ request.host }}:{{ request.port }}</span>
          <span class="detail-dot">&middot;</span>
          <span class="detail-chip"><i class="bi bi-arrow-up"></i> {{ FormattingService.formatBytes(request.requestSize) }}</span>
          <span class="detail-chip"><i class="bi bi-arrow-down"></i> {{ FormattingService.formatBytes(request.responseSize) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FormattingService from '@/services/FormattingService.ts';
import Badge from '@/components/Badge.vue';
import type {Variant} from '@/types/ui';

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

const maxResponseTime = computed(() => {
  if (props.requests.length === 0) return 1;
  return Math.max(...props.requests.map(r => r.responseTime));
});

const getTimePercentage = (responseTime: number): number => {
  return Math.max((responseTime / maxResponseTime.value) * 100, 2);
};

const getAccentClass = (statusCode: number): string => {
  if (statusCode >= 500) return 'accent-danger';
  if (statusCode >= 400) return 'accent-warning';
  return 'accent-success';
};

const parseUri = (uri: string) => {
  if (!uri) return [];
  const segments = uri.split('/').filter(segment => segment.length > 0);
  return segments.map(segment => ({
    text: segment,
    isVariable: segment.startsWith('{') && segment.endsWith('}')
  }));
};

const getMethodVariant = (method: string): Variant => {
  const variants: Record<string, Variant> = {
    get: 'blue', post: 'green', put: 'yellow', delete: 'red', patch: 'grey', options: 'purple'
  };
  return variants[method.toLowerCase()] || 'secondary';
};

const getStatusVariant = (status: number): Variant => {
  if (status >= 500) return 'danger';
  if (status >= 400) return 'warning';
  return 'success';
};
</script>

<style scoped>
.slowest-list {
  padding: 0.5rem 1rem;
}

.slowest-row {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--color-border-light);
  padding: 0.75rem 0;
}

.slowest-row:last-child {
  border-bottom: none;
}

.slowest-row:hover {
  background: var(--color-bg-hover);
}

.left-accent {
  width: 4px;
  border-radius: 2px;
  flex-shrink: 0;
  margin-right: 1rem;
}

.accent-success {
  background: var(--color-success);
}

.accent-warning {
  background: var(--color-warning);
}

.accent-danger {
  background: var(--color-danger);
}

.row-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.row-header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.uri-path {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uri-part {
  display: inline;
}

.uri-sep {
  color: var(--color-text-muted);
  font-weight: 400;
}

.uri-var {
  color: var(--color-purple);
  font-weight: 400;
}

.time-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
  min-width: 120px;
  flex-shrink: 0;
}

.time-bar-track {
  width: 100%;
  height: 6px;
  background: var(--color-lighter);
  border-radius: 3px;
  overflow: hidden;
}

.time-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-secondary));
}

.time-bar-value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-dark);
  min-width: 70px;
  text-align: right;
}

.row-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-family-base);
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text-muted);
  letter-spacing: 0.01em;
}

.detail-chip i {
  font-size: 0.6rem;
  opacity: 0.7;
}

.detail-dot {
  color: var(--color-text-light);
  font-size: 0.8rem;
  line-height: 1;
}


@media (max-width: 768px) {
  .row-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }

  .time-bar-wrap {
    min-width: 0;
  }
}
</style>

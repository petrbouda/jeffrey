<template>
  <div class="endpoint-list">
    <!-- Controls -->
    <div class="endpoint-controls">
      <div class="sort-controls">
        <label class="sort-label">Sort by:</label>
        <div class="btn-group" role="group">
          <button
            v-for="option in sortOptions"
            :key="option.key"
            type="button"
            class="btn btn-outline-secondary btn-sm"
            :class="{ active: currentSort === option.key }"
            @click="onSortChange(option.key)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <button
        v-if="endpoints.length > maxDisplayedEndpoints"
        @click="showAllEndpoints = !showAllEndpoints"
        class="btn btn-sm btn-outline-secondary"
      >
        {{ showAllEndpoints ? 'Show Less' : `Show All (${endpoints.length})` }}
      </button>
    </div>

    <!-- Endpoint Cards -->
    <div class="endpoint-cards">
      <div
        v-for="endpoint in displayedEndpoints"
        :key="endpoint.uri"
        class="ep-card"
        @click="handleEndpointClick(endpoint)"
      >
        <!-- Left: Request Count Pill -->
        <div class="ep-count-pill">
          <span class="ep-count-num">{{
            FormattingService.formatNumber(endpoint.requestCount)
          }}</span>
          <span class="ep-count-label">requests</span>
        </div>

        <!-- Center: URI + Metrics -->
        <div class="ep-main">
          <div class="ep-uri" :title="endpoint.uri">
            <span class="uri-sep">/</span>
            <span v-for="(part, index) in parseUri(endpoint.uri)" :key="index">
              <span v-if="index > 0" class="uri-sep">/</span>
              <span v-if="part.isVariable" class="uri-var">{{ part.text }}</span>
              <span v-else class="uri-segment">{{ part.text }}</span>
            </span>
          </div>
          <div class="ep-metrics">
            <Badge
              key-label="Max"
              :value="FormattingService.formatDuration2Units(endpoint.maxResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P99"
              :value="FormattingService.formatDuration2Units(endpoint.p99ResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P95"
              :value="FormattingService.formatDuration2Units(endpoint.p95ResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              v-if="endpoint.totalBytesReceived >= 0"
              key-label="Recv"
              :value="FormattingService.formatBytes(endpoint.totalBytesReceived)"
              variant="secondary"
              size="s"
              borderless
            />
            <Badge
              v-if="endpoint.totalBytesSent >= 0"
              key-label="Sent"
              :value="FormattingService.formatBytes(endpoint.totalBytesSent)"
              variant="secondary"
              size="s"
              borderless
            />
          </div>
        </div>

        <!-- Right: Error Boxes + Arrow -->
        <div class="ep-right">
          <div v-if="endpoint.count4xx > 0" class="ep-err ep-err-4xx">
            <span class="ep-err-num">{{ endpoint.count4xx }}</span>
            <span class="ep-err-label">4xx</span>
          </div>
          <div v-if="endpoint.count5xx > 0" class="ep-err ep-err-5xx">
            <span class="ep-err-num">{{ endpoint.count5xx }}</span>
            <span class="ep-err-label">5xx</span>
          </div>
          <i class="bi bi-chevron-right ep-arrow"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import FormattingService from '@/services/FormattingService.ts';
import Badge from '@/components/Badge.vue';

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
  endpointClick: [uri: string];
}>();

const showAllEndpoints = ref(false);
const currentSort = ref('maxResponseTime');
const maxDisplayedEndpoints = 10;

const sortOptions = [
  {
    key: 'maxResponseTime',
    label: 'MAX',
    compare: (a: Endpoint, b: Endpoint) => b.maxResponseTime - a.maxResponseTime
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    compare: (a: Endpoint, b: Endpoint) => b.p95ResponseTime - a.p95ResponseTime
  },
  { key: 'count4xx', label: '4xx', compare: (a: Endpoint, b: Endpoint) => b.count4xx - a.count4xx },
  { key: 'count5xx', label: '5xx', compare: (a: Endpoint, b: Endpoint) => b.count5xx - a.count5xx },
  {
    key: 'requestCount',
    label: 'Requests',
    compare: (a: Endpoint, b: Endpoint) => b.requestCount - a.requestCount
  }
];

const sortedEndpoints = computed(() => {
  const option = sortOptions.find(o => o.key === currentSort.value);
  if (!option) return props.endpoints;
  return [...props.endpoints].sort(option.compare);
});

const displayedEndpoints = computed(() => {
  return showAllEndpoints.value
    ? sortedEndpoints.value
    : sortedEndpoints.value.slice(0, maxDisplayedEndpoints);
});

const parseUri = (uri: string) => {
  if (!uri) return [];
  const segments = uri.split('/').filter(segment => segment.length > 0);
  return segments.map(segment => ({
    text: segment,
    isVariable: segment.startsWith('{') && segment.endsWith('}')
  }));
};

const handleEndpointClick = (endpoint: Endpoint) => {
  emit('endpointClick', endpoint.uri);
};

const onSortChange = (key: string) => {
  currentSort.value = key;
};
</script>

<style scoped>
.endpoint-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sort-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
}

.endpoint-cards {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ep-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.875rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.ep-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

/* Left: Request Count Pill */
.ep-count-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.5rem 0.75rem;
  background: var(--color-primary-light);
  border-radius: var(--radius-md);
  min-width: 60px;
  flex-shrink: 0;
}

.ep-count-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-primary);
}

.ep-count-label {
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-text-muted);
  letter-spacing: 0.5px;
}

/* Center: URI + Metrics */
.ep-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.ep-uri {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uri-sep {
  color: var(--color-text-muted);
  font-weight: 400;
}

.uri-var {
  color: var(--color-purple);
  font-weight: 400;
}

.uri-segment {
  color: var(--color-dark);
  font-weight: 500;
}

.ep-metrics {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

/* Right: Errors + Arrow */
.ep-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.ep-err {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius-base);
  min-width: 40px;
}

.ep-err-4xx {
  background: rgba(245, 128, 62, 0.1);
}

.ep-err-5xx {
  background: rgba(230, 55, 87, 0.1);
}

.ep-err-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.85rem;
  font-weight: 700;
}

.ep-err-4xx .ep-err-num {
  color: var(--color-warning-hover);
}

.ep-err-5xx .ep-err-num {
  color: var(--color-danger);
}

.ep-err-label {
  font-size: 0.5rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.ep-err-4xx .ep-err-label {
  color: var(--color-warning);
}

.ep-err-5xx .ep-err-label {
  color: var(--color-danger);
}

.ep-arrow {
  color: var(--color-text-light);
  font-size: 1rem;
}

@media (max-width: 768px) {
  .ep-card {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .endpoint-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
}
</style>

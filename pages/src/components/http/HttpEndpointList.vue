<template>
  <section class="dashboard-section">
    <MetricsList
      :items="displayedEndpoints"
      :metrics="endpointMetrics"
      :sort-options="sortOptions"
      :default-sort="'maxResponseTime'"
      :item-key="'uri'"
      :item-class="getEndpointClass"
      title-key="uri"
      :loading="false"
      loading-text="Loading endpoints..."
      empty-text="No HTTP endpoints found"
      :show-controls="true"
      :show-metrics="true"
      :show-subtitle="false"
      :sortable="true"
      :selectable="true"
      @item-click="handleEndpointClick"
      @sort-change="onSortChange"
    >
      <template #controls-right>
        <button 
          v-if="getAllEndpoints().length > maxDisplayedEndpoints"
          @click="showAllEndpoints = !showAllEndpoints"
          class="btn btn-sm btn-outline-secondary"
        >
          {{ showAllEndpoints ? 'Show Less' : `Show All (${getAllEndpoints().length})` }}
        </button>
      </template>
      
      <template #item-title="{ item }">
        <div class="uri-display" :title="item.uri">
          <span class="uri-separator">/</span>
          <span v-for="(part, index) in parseUri(item.uri)" :key="index" class="uri-part">
            <span v-if="index > 0" class="uri-separator">/</span>
            <span v-if="part.isVariable" class="uri-variable">{{ part.text }}</span>
            <span v-else class="uri-segment">{{ part.text }}</span>
          </span>
        </div>
      </template>
    </MetricsList>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import FormattingService from '@/services/FormattingService.ts';
import MetricsList from '@/components/MetricsList.vue';
import type { MetricDefinition, SortOption } from '@/components/MetricsList.vue';

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
const currentSort = ref('maxResponseTime');
const maxDisplayedEndpoints = 10;

// Metrics configuration
const endpointMetrics: MetricDefinition[] = [
  {
    key: 'requestCount',
    label: 'Requests',
    type: 'number',
    class: 'metric-primary'
  },
  {
    key: 'maxResponseTime',
    label: 'Max',
    formatter: (value: number) => FormattingService.formatDuration2Units(value),
    class: 'metric-info'
  },
  {
    key: 'p99ResponseTime',
    label: 'P99',
    formatter: (value: number) => FormattingService.formatDuration2Units(value),
    class: 'metric-info'
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    formatter: (value: number) => FormattingService.formatDuration2Units(value),
    class: 'metric-info'
  },
  {
    key: 'totalBytesReceived',
    label: 'Received',
    formatter: (value: number) => value < 0 ? '?' : FormattingService.formatBytes(value),
    class: 'metric-secondary'
  },
  {
    key: 'totalBytesSent',
    label: 'Sent',
    formatter: (value: number) => value < 0 ? '?' : FormattingService.formatBytes(value),
    class: 'metric-secondary'
  },
  {
    key: 'count4xx',
    label: '4xx',
    type: 'number',
    class: (value: number) => value > 0 ? 'metric-warning' : 'metric-success'
  },
  {
    key: 'count5xx',
    label: '5xx',
    type: 'number',
    class: (value: number) => value > 0 ? 'metric-danger' : 'metric-success'
  }
];

// Sort options
const sortOptions: SortOption[] = [
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
  {
    key: 'count4xx',
    label: '4xx',
    compare: (a: Endpoint, b: Endpoint) => b.count4xx - a.count4xx
  },
  {
    key: 'count5xx',
    label: '5xx',
    compare: (a: Endpoint, b: Endpoint) => b.count5xx - a.count5xx
  },
  {
    key: 'requestCount',
    label: 'Requests',
    compare: (a: Endpoint, b: Endpoint) => b.requestCount - a.requestCount
  }
];

// Helper functions
const getAllEndpoints = () => {
  return props.endpoints || [];
};

const displayedEndpoints = computed(() => {
  const allEndpoints = getAllEndpoints();
  return showAllEndpoints.value ? allEndpoints : allEndpoints.slice(0, maxDisplayedEndpoints);
});

const parseUri = (uri: string) => {
  if (!uri) return [];
  
  const segments = uri.split('/').filter(segment => segment.length > 0);
  
  return segments.map(segment => ({
    text: segment,
    isVariable: segment.startsWith('{') && segment.endsWith('}')
  }));
};

const getEndpointClass = (endpoint: Endpoint) => {
  const classes = [];
  
  if (props.selectedEndpoint === endpoint.uri) {
    classes.push('selected');
  }
  
  if (endpoint.count5xx > 0) {
    classes.push('has-5xx-errors');
  } else if (endpoint.count4xx > 0) {
    classes.push('has-4xx-errors');
  }
  
  return classes.join(' ');
};

const handleEndpointClick = (endpoint: Endpoint) => {
  emit('endpointClick', endpoint.uri);
};

const onSortChange = (sortKey: string) => {
  currentSort.value = sortKey;
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

/* URI Display styling */
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

/* Item state styling */
:deep(.metrics-item.selected) {
  background: #f8faff;
  border-left: 4px solid #667eea;
}

:deep(.metrics-item.has-4xx-errors) {
  background: #fffbeb !important;
}

:deep(.metrics-item.has-5xx-errors) {
  background: #fef2f2 !important;
}

:deep(.metrics-item.selected.has-4xx-errors) {
  background: #fcf8ea !important;
  border-left: 4px solid #667eea;
}

:deep(.metrics-item.selected.has-5xx-errors) {
  background: #fceded !important;
  border-left: 4px solid #667eea;
}
</style>

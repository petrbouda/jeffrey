<template>
  <section class="dashboard-section">
    <MetricsList
        :items="methods"
        :metrics="methodMetrics"
        :sort-options="sortOptions"
        :default-sort="'maxResponseTime'"
        :item-key="'method'"
        title-key="method"
        :loading="false"
        loading-text="Loading methods..."
        empty-text="No gRPC methods found"
        :show-controls="true"
        :show-metrics="true"
        :show-subtitle="false"
        :sortable="true"
        :selectable="false"
        @sort-change="onSortChange"
    >
      <template #item-title="{ item }">
        <div class="method-display" :title="item.method">
          <i class="bi bi-gear me-2 method-icon"></i>
          <span class="method-name">{{ item.method }}</span>
        </div>
      </template>
    </MetricsList>
  </section>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import FormattingService from '@/services/FormattingService';
import MetricsList from '@/components/MetricsList.vue';
import type {MetricDefinition, SortOption} from '@/components/MetricsList.vue';
import type {GrpcMethodInfo} from '@/services/api/ProfileGrpcClient';

interface Props {
  methods: GrpcMethodInfo[];
}

defineProps<Props>();

// Reactive state
const currentSort = ref('maxResponseTime');

// Metrics configuration
const methodMetrics: MetricDefinition[] = [
  {
    key: 'callCount',
    label: 'Calls',
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
    key: 'successRate',
    label: 'Success',
    formatter: (value: number) => `${((value || 0) * 100).toFixed(1)}%`,
    class: (value: number) => (value || 0) >= 1 ? 'metric-success' : 'metric-warning'
  },
  {
    key: 'avgRequestSize',
    label: 'Avg Req',
    formatter: (value: number) => value < 0 ? '?' : FormattingService.formatBytes(value),
    class: 'metric-secondary'
  },
  {
    key: 'avgResponseSize',
    label: 'Avg Resp',
    formatter: (value: number) => value < 0 ? '?' : FormattingService.formatBytes(value),
    class: 'metric-secondary'
  }
];

// Sort options
const sortOptions: SortOption[] = [
  {
    key: 'maxResponseTime',
    label: 'MAX',
    compare: (a: GrpcMethodInfo, b: GrpcMethodInfo) => b.maxResponseTime - a.maxResponseTime
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    compare: (a: GrpcMethodInfo, b: GrpcMethodInfo) => b.p95ResponseTime - a.p95ResponseTime
  },
  {
    key: 'callCount',
    label: 'Calls',
    compare: (a: GrpcMethodInfo, b: GrpcMethodInfo) => b.callCount - a.callCount
  },
  {
    key: 'successRate',
    label: 'Success',
    compare: (a: GrpcMethodInfo, b: GrpcMethodInfo) => a.successRate - b.successRate
  }
];

const onSortChange = (sortKey: string) => {
  currentSort.value = sortKey;
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

/* Method Display styling */
.method-display {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  background: #f7fafc;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  max-width: 100%;
}

.method-icon {
  color: #5e64ff;
  font-size: 0.9rem;
}

.method-name {
  font-family: 'Courier New', monospace;
  color: #2d3748;
  font-weight: 500;
}
</style>

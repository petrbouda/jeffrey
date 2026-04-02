<template>
  <ChartSection title="gRPC Methods" icon="gear" :full-width="true">
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
  </ChartSection>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import FormattingService from '@/services/FormattingService';
import MetricsList from '@/components/MetricsList.vue';
import ChartSection from '@/components/ChartSection.vue';
import type { MetricDefinition, SortOption } from '@/components/MetricsList.vue';
import type { GrpcMethodInfo } from '@/services/api/ProfileGrpcClient';

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
    class: (value: number) => ((value || 0) >= 1 ? 'metric-success' : 'metric-warning')
  },
  {
    key: 'avgRequestSize',
    label: 'Avg Req',
    formatter: (value: number) => (value < 0 ? '?' : FormattingService.formatBytes(value)),
    class: 'metric-secondary'
  },
  {
    key: 'avgResponseSize',
    label: 'Avg Resp',
    formatter: (value: number) => (value < 0 ? '?' : FormattingService.formatBytes(value)),
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
/* Method Display styling */
.method-display {
  font-size: 0.85rem;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  padding: 0.45rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}

.method-icon {
  color: var(--color-primary);
  font-size: 0.8rem;
  opacity: 0.7;
}

.method-name {
  color: #1e293b;
  font-weight: 600;
  font-size: 0.85rem;
}
</style>

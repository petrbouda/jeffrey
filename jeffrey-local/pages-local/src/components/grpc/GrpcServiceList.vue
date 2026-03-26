<template>
  <section class="dashboard-section">
    <MetricsList
        :items="displayedServices"
        :metrics="serviceMetrics"
        :sort-options="sortOptions"
        :default-sort="'maxResponseTime'"
        :item-key="'service'"
        title-key="service"
        :loading="false"
        loading-text="Loading services..."
        empty-text="No gRPC services found"
        :show-controls="true"
        :show-metrics="true"
        :show-subtitle="false"
        :sortable="true"
        :selectable="true"
        @item-click="handleServiceClick"
        @sort-change="onSortChange"
    >
      <template #controls-right>
        <button
            v-if="getAllServices().length > maxDisplayedServices"
            @click="showAllServices = !showAllServices"
            class="btn btn-sm btn-outline-secondary"
        >
          {{ showAllServices ? 'Show Less' : `Show All (${getAllServices().length})` }}
        </button>
      </template>

      <template #item-title="{ item }">
        <div class="service-display" :title="item.service">
          <i class="bi bi-hdd-network me-2 service-icon"></i>
          <span class="service-name">{{ item.service }}</span>
        </div>
      </template>
    </MetricsList>
  </section>
</template>

<script setup lang="ts">
import {ref, computed} from 'vue';
import FormattingService from '@/services/FormattingService';
import MetricsList from '@/components/MetricsList.vue';
import type {MetricDefinition, SortOption} from '@/components/MetricsList.vue';
import type {GrpcServiceInfo} from '@/services/api/ProfileGrpcClient';

interface Props {
  services: GrpcServiceInfo[];
  selectedService?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedService: null
});

const emit = defineEmits<{
  serviceClick: [service: string]
}>();

// Reactive state
const showAllServices = ref(false);
const currentSort = ref('maxResponseTime');
const maxDisplayedServices = 10;

// Metrics configuration
const serviceMetrics: MetricDefinition[] = [
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
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.maxResponseTime - a.maxResponseTime
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.p95ResponseTime - a.p95ResponseTime
  },
  {
    key: 'callCount',
    label: 'Calls',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.callCount - a.callCount
  },
  {
    key: 'successRate',
    label: 'Success',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => a.successRate - b.successRate
  }
];

// Helper functions
const getAllServices = () => {
  return props.services || [];
};

const displayedServices = computed(() => {
  const allServices = getAllServices();
  return showAllServices.value ? allServices : allServices.slice(0, maxDisplayedServices);
});

const handleServiceClick = (service: GrpcServiceInfo) => {
  emit('serviceClick', service.service);
};

const onSortChange = (sortKey: string) => {
  currentSort.value = sortKey;
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

/* Service Display styling */
.service-display {
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

.service-icon {
  color: #5e64ff;
  font-size: 0.9rem;
}

.service-name {
  font-family: 'Courier New', monospace;
  color: #2d3748;
  font-weight: 500;
}
</style>

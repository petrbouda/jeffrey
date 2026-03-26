<template>
  <div class="distribution-container">
    <!-- Status Code Pie Chart -->
    <PieChart
        title="Status Code Distribution"
        icon="pie-chart"
        :data="statusCodeData"
        :total="totalCalls"
        :color-mapping="statusCodeColorMapping"
        :value-formatter="(val: number) => val + ' calls'"
    />

    <!-- Service Distribution Pie Chart -->
    <PieChart
        title="Service Distribution"
        icon="diagram-3"
        :data="serviceData"
        :total="totalCalls"
        :value-formatter="(val: number) => val + ' calls'"
    />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import PieChart from '@/components/PieChart.vue';
import type {GrpcStatusStats, GrpcServiceInfo} from '@/services/api/ProfileGrpcClient';

interface Props {
  statusCodes: GrpcStatusStats[];
  services: GrpcServiceInfo[];
  totalCalls: number;
}

const props = defineProps<Props>();

// Status code color mapping for gRPC status codes
const statusCodeColorMapping = (label: string): string => {
  switch (label) {
    case 'OK':
      return '#5cb85c';
    case 'CANCELLED':
      return '#f0ad4e';
    case 'INVALID_ARGUMENT':
    case 'NOT_FOUND':
    case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED':
    case 'FAILED_PRECONDITION':
    case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED':
      return '#f0ad4e';
    case 'UNKNOWN':
    case 'DEADLINE_EXCEEDED':
    case 'RESOURCE_EXHAUSTED':
    case 'ABORTED':
    case 'UNIMPLEMENTED':
    case 'INTERNAL':
    case 'UNAVAILABLE':
    case 'DATA_LOSS':
      return '#d9534f';
    default:
      return '#6c757d';
  }
};

// Computed data for charts
const statusCodeData = computed(() =>
    props.statusCodes.map(status => ({
      label: status.status,
      value: status.count
    }))
);

const serviceData = computed(() =>
    props.services
        .sort((a, b) => b.callCount - a.callCount)
        .map(service => ({
          label: service.service,
          value: service.callCount
        }))
);
</script>

<style scoped>
.distribution-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

@media (max-width: 768px) {
  .distribution-container {
    grid-template-columns: 1fr;
  }
}
</style>

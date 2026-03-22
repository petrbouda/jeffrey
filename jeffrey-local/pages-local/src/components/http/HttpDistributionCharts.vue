<template>
  <div class="distribution-container">
    <!-- Status Codes Pie Chart -->
    <PieChart
      title="Status Code Distribution"
      icon="pie-chart"
      :data="statusCodeData"
      :total="totalRequests"
      :color-mapping="statusCodeColorMapping"
      :value-formatter="(val: number) => val + ' requests'"
    />

    <!-- HTTP Methods Pie Chart -->
    <PieChart
      title="HTTP Methods Distribution"
      icon="diagram-3"
      :data="httpMethodData"
      :total="totalRequests"
      :color-mapping="httpMethodColorMapping"
      :value-formatter="(val: number) => val + ' requests'"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import PieChart from '@/components/PieChart.vue';

interface StatusCode {
  code: number;
  count: number;
}

interface HttpMethod {
  method: string;
  count: number;
}

interface Props {
  statusCodes: StatusCode[];
  methods: HttpMethod[];
  totalRequests: number;
}

const props = defineProps<Props>();

// Status code color mapping
const statusCodeColorMapping = (label: string): string => {
  const code = parseInt(label);
  if (code >= 200 && code < 300) return '#5cb85c'; // medium green (success)
  if (code >= 300 && code < 400) return '#5a9fd4'; // medium blue (redirect)
  if (code >= 400 && code < 500) return '#f0ad4e'; // medium orange (client error)
  if (code >= 500) return '#d9534f'; // medium red (server error)
  return '#6c757d'; // medium gray for other codes
};

// HTTP method color mapping
const httpMethodColorMapping = (label: string): string => {
  switch (label.toUpperCase()) {
    case 'GET': return '#5a9fd4'; // medium blue
    case 'POST': return '#5cb85c'; // medium green
    case 'PUT': return '#f0ad4e'; // medium orange
    case 'DELETE': return '#d9534f'; // medium red
    case 'PATCH': return '#6c757d'; // medium gray
    case 'OPTIONS': return '#9b59b6'; // medium purple
    default: return '#95a5a6'; // medium gray for other methods
  }
};

// Computed data for charts
const statusCodeData = computed(() => 
  props.statusCodes.map(status => ({
    label: status.code.toString(),
    value: status.count
  }))
);

const httpMethodData = computed(() => 
  props.methods.map(method => ({
    label: method.method,
    value: method.count
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
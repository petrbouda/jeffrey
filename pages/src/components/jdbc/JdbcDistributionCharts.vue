<template>
  <div class="distribution-container">
    <!-- Operation Types Pie Chart -->
    <PieChart
      title="Operation Types Distribution"
      icon="pie-chart"
      :data="operationData"
      :total="totalOperations"
      :color-mapping="operationColorMapping"
      :value-formatter="(val: number) => formatNumber(val) + ' invocations'"
    />

    <!-- Statement Groups Pie Chart -->
    <PieChart
      title="Statement Groups Distribution"
      icon="collection"
      :data="groupData"
      :total="totalStatements"
      :value-formatter="(val: number) => formatNumber(val) + ' invocations'"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import PieChart from '@/components/PieChart.vue';

interface JdbcOperation {
  operation: string;
  count: number;
}

interface JdbcStatementGroup {
  group: string;
  count: number;
}

interface Props {
  operations: JdbcOperation[];
  statementGroups: JdbcStatementGroup[];
  totalOperations: number;
  totalStatements: number;
}

const props = defineProps<Props>();

// Helper function
const formatNumber = (num: number): string => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
};

// JDBC operation color mapping
const operationColorMapping = (label: string): string => {
  switch (label) {
    case 'SELECT': return '#5a9fd4'; // medium blue
    case 'INSERT': return '#5cb85c'; // medium green
    case 'UPDATE': return '#f0ad4e'; // medium orange
    case 'DELETE': return '#d9534f'; // medium red
    case 'EXECUTE': return '#6c757d'; // medium gray
    default: return '#95a5a6'; // medium gray for other operations
  }
};

// Computed data for charts
const operationData = computed(() => 
  props.operations.map(operation => ({
    label: operation.operation,
    value: operation.count
  }))
);

const groupData = computed(() => 
  props.statementGroups.map(group => ({
    label: group.group,
    value: group.count
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

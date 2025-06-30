<template>
  <div class="distribution-container">
    <!-- Operation Types Pie Chart -->
    <PieChart
      title="Operation Types Distribution"
      icon="pie-chart"
      :data="operationData"
      :total="total"
      :value-formatter="(val: number) => formatNumber(val) + ' invocations'"
    />

    <!-- Second Distribution Pie Chart -->
    <PieChart
      :title="secondChartTitle"
      icon="collection"
      :data="secondChartData"
      :total="total"
      :value-formatter="(val: number) => formatNumber(val) + ' invocations'"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import PieChart from '@/components/PieChart.vue';
import JdbcUtils from '@/services/profile/custom/jdbc/JdbcUtils.ts';

interface Props {
  operations: { label: string; value: number }[];
  secondChartTitle: string;
  secondChartData: { label: string; value: number }[];
  total: number;
}

const props = defineProps<Props>();

// Helper function
const formatNumber = (num: number): string => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
};

// Computed data for charts
const operationData = computed(() => 
  props.operations.map(operation => ({
    label: JdbcUtils.cleanOperationName(operation.label),
    value: operation.value
  }))
);

const secondChartData = computed(() => props.secondChartData);
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

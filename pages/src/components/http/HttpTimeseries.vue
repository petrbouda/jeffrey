<template>
  <ChartSection title="HTTP Metrics Timeline" icon="graph-up" :full-width="true" container-class="apex-chart-container">
    <ApexTimeSeriesChart
      :primary-data="responseTimeData"
      primary-title="Response Time"
      :secondary-data="requestCountData"
      secondary-title="Request Count"
      :visible-minutes="15"
      :independentSecondaryAxis="true"
      primary-axis-type="duration"
      secondary-axis-type="number"
    />
  </ChartSection>
</template>

<script setup lang="ts">
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';

interface Props {
  responseTimeData: any[];
  requestCountData: any[];
}

const props = defineProps<Props>();
</script>

<style scoped>
.apex-chart-container {
  height: 400px;
}

@media (max-width: 768px) {
  .apex-chart-container {
    height: 300px;
  }
}
</style>

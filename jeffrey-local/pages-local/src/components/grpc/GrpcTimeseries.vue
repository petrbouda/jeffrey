<template>
  <ChartSection
      title="gRPC Metrics Timeline"
      icon="graph-up"
      :full-width="true"
      container-class="apex-chart-container"
  >
    <TimeSeriesChart
        :primary-data="props.responseTimeData"
        primary-title="Response Time"
        :secondary-data="props.callCountData"
        secondary-title="Call Count"
        :visible-minutes="60"
        :independentSecondaryAxis="true"
        :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
        :secondary-axis-type="AxisFormatType.NUMBER"
    />
  </ChartSection>
</template>

<script setup lang="ts">
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';

interface Props {
  responseTimeData: any[];
  callCountData: any[];
}

const props = defineProps<Props>();
</script>

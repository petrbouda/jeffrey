<template>
  <ChartSection
      title="Message Size Timeline"
      icon="graph-up"
      :full-width="true"
      container-class="apex-chart-container"
  >
    <TimeSeriesChart
        :primary-data="props.requestSizeData"
        primary-title="Request Size"
        :secondary-data="props.responseSizeData"
        secondary-title="Response Size"
        :visible-minutes="60"
        :independentSecondaryAxis="true"
        :primary-axis-type="AxisFormatType.BYTES"
        :secondary-axis-type="AxisFormatType.BYTES"
    />
  </ChartSection>
</template>

<script setup lang="ts">
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';

interface Props {
  requestSizeData: any[];
  responseSizeData: any[];
}

const props = defineProps<Props>();
</script>

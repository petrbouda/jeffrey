<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <ChartSection
    title="HTTP Metrics Timeline"
    icon="graph-up"
    :full-width="true"
    container-class="apex-chart-container"
  >
    <TimeSeriesChart
      :primary-data="props.responseTimeData"
      primary-title="Response Time"
      :secondary-data="props.requestCountData"
      secondary-title="Request Count"
      :visible-minutes="60"
      :independent-secondary-axis="true"
      :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
      :secondary-axis-type="AxisFormatType.NUMBER"
    />
  </ChartSection>
</template>

<script setup lang="ts">
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartSection from '@/components/ChartSection.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

interface Props {
  responseTimeData: any[];
  requestCountData: any[];
}

const props = defineProps<Props>();
</script>

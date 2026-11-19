<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
      :independent-secondary-axis="true"
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

<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
      :independentSecondaryAxis="true"
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

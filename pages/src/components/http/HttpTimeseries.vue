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
  <ChartSection title="HTTP Metrics Timeline" icon="graph-up" :full-width="true" container-class="apex-chart-container">
    <ApexTimeSeriesChart
      :primary-data="props.responseTimeData"
      primary-title="Response Time"
      :secondary-data="props.requestCountData"
      secondary-title="Request Count"
      :visible-minutes="15"
      :independentSecondaryAxis="true"
      primary-axis-type="durationInNanos"
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

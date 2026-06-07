<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <apexchart type="heatmap" :height="height" :options="options" :series="series" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { buildHeatmapSeries } from '@/services/span/spanHeatmapSeries';
import type { SpanHeatmapMetric } from '@/services/span/spanHeatmapSeries';
import type { SpanHeatmap } from '@/services/api/model/span/SpanModels';

// Mirrors design token --color-purple; ApexCharts config takes a color string (see HeatmapGraph.ts).
const ACCENT_COLOR = '#6f42c1';
const ROW_HEIGHT = 38;

const props = defineProps<{
  data: SpanHeatmap;
  metric: SpanHeatmapMetric;
}>();

const series = computed(() => buildHeatmapSeries(props.data, props.metric));

const height = computed(() => Math.max(220, props.data.rows.length * ROW_HEIGHT + 90));

const options = computed(() => ({
  chart: {
    type: 'heatmap',
    toolbar: { show: false },
    fontFamily: 'inherit'
  },
  dataLabels: { enabled: false },
  colors: [ACCENT_COLOR],
  plotOptions: {
    heatmap: {
      radius: 3,
      enableShades: true,
      shadeIntensity: 0.65,
      useFillColorAsStroke: false
    }
  },
  stroke: { width: 2, colors: ['#ffffff'] },
  xaxis: {
    type: 'category',
    labels: {
      rotate: -45,
      hideOverlappingLabels: true,
      style: { fontSize: '10px' }
    },
    tickAmount: 12
  },
  yaxis: {
    labels: { style: { fontSize: '11px' } }
  },
  legend: { show: false },
  tooltip: {
    y: {
      formatter: (value: number) =>
        props.metric === 'count' ? `${value} spans` : `${value} ms p95`
    }
  }
}));
</script>

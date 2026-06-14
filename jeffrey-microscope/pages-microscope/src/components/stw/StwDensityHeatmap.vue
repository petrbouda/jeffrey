<template>
  <div ref="chartEl" class="stw-density"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import ApexCharts from 'apexcharts';
import FormattingService from '@/services/FormattingService';
import { STW_LANES } from '@/services/stw/stwLanes';
import type { StwEvent } from '@/services/api/model/stw/StwModels';

const props = defineProps<{
  events: StwEvent[];
}>();

const chartEl = ref<HTMLElement | null>(null);
let chart: ApexCharts | null = null;

const NANOS_PER_MILLI = 1_000_000;
const BUCKETS = 40;

interface HeatPoint {
  x: string;
  y: number;
}

function buildSeries(): { name: string; data: HeatPoint[] }[] {
  if (props.events.length === 0) {
    return [];
  }
  const maxTime = props.events.reduce((max, event) => Math.max(max, event.timeOffsetMillis), 0) + 1;
  const bucketWidth = Math.max(1, Math.ceil(maxTime / BUCKETS));
  const bucketLabels: string[] = [];
  for (let i = 0; i < BUCKETS; i++) {
    bucketLabels.push(FormattingService.formatDurationMillisCoarse(i * bucketWidth));
  }

  // Lanes are listed bottom-to-top by ApexCharts, so reverse to keep GC on top.
  return [...STW_LANES].reverse().map((lane) => {
    const data: HeatPoint[] = bucketLabels.map((label) => ({ x: label, y: 0 }));
    for (const event of props.events) {
      if (event.category === lane.category) {
        const bucket = Math.min(BUCKETS - 1, Math.floor(event.timeOffsetMillis / bucketWidth));
        data[bucket].y += Math.round(event.durationNanos / NANOS_PER_MILLI);
      }
    }
    return { name: lane.label, data };
  });
}

function buildOptions(): ApexCharts.ApexOptions {
  return {
    chart: { type: 'heatmap', height: 280, toolbar: { show: false }, animations: { enabled: false } },
    series: buildSeries(),
    dataLabels: { enabled: false },
    xaxis: {
      type: 'category',
      labels: { rotate: -45, hideOverlappingLabels: true }
    },
    tooltip: {
      y: { formatter: (value: number) => FormattingService.formatDurationInMillis2Units(value) }
    },
    plotOptions: {
      heatmap: { radius: 2, colorScale: { inverse: false } }
    },
    colors: ['rgb(228,87,46)']
  };
}

function renderChart() {
  if (!chartEl.value) {
    return;
  }
  if (chart) {
    chart.destroy();
    chart = null;
  }
  chart = new ApexCharts(chartEl.value, buildOptions());
  chart.render();
}

onMounted(renderChart);
watch(() => props.events, renderChart);
onUnmounted(() => {
  if (chart) {
    chart.destroy();
    chart = null;
  }
});
</script>

<style scoped>
.stw-density {
  width: 100%;
}
</style>

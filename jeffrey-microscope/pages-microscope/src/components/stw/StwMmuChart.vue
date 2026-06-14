<template>
  <div ref="chartEl" class="stw-mmu"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import ApexCharts from 'apexcharts';
import FormattingService from '@/services/FormattingService';
import type { StwEvent } from '@/services/api/model/stw/StwModels';

const props = defineProps<{
  events: StwEvent[];
}>();

const chartEl = ref<HTMLElement | null>(null);
let chart: ApexCharts | null = null;

const NANOS_PER_MILLI = 1_000_000;
// Window sizes (ms) for the Minimum Mutator Utilization curve.
const WINDOWS_MS = [10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000];

interface Stop {
  start: number;
  durMs: number;
}

function globalStops(): Stop[] {
  return props.events
    .filter((event) => event.category === 'GC_PAUSE' || event.category === 'VM_OPERATION')
    .map((event) => ({ start: event.timeOffsetMillis, durMs: event.durationNanos / NANOS_PER_MILLI }))
    .sort((a, b) => a.start - b.start);
}

// Worst-case total pause time within any window of length w, anchored at a pause start (sliding window).
function maxPauseInWindow(stops: Stop[], windowMs: number): number {
  let max = 0;
  let j = 0;
  let sum = 0;
  for (let i = 0; i < stops.length; i++) {
    if (j < i) {
      j = i;
      sum = 0;
    }
    while (j < stops.length && stops[j].start < stops[i].start + windowMs) {
      sum += stops[j].durMs;
      j++;
    }
    max = Math.max(max, sum);
    sum -= stops[i].durMs;
  }
  return max;
}

function mmuSeries(): number[] {
  const stops = globalStops();
  return WINDOWS_MS.map((windowMs) => {
    const worst = Math.min(maxPauseInWindow(stops, windowMs), windowMs);
    return Math.max(0, (1 - worst / windowMs) * 100);
  });
}

function buildOptions(): ApexCharts.ApexOptions {
  return {
    chart: { type: 'line', height: 320, toolbar: { show: false }, animations: { enabled: false } },
    series: [{ name: 'Min Mutator Utilization', data: mmuSeries() }],
    xaxis: {
      categories: WINDOWS_MS.map((windowMs) => FormattingService.formatDurationMillisCoarse(windowMs)),
      title: { text: 'Window size' }
    },
    yaxis: {
      min: 0,
      max: 100,
      title: { text: 'Mutator utilization (%)' },
      labels: { formatter: (value: number) => `${value.toFixed(0)}%` }
    },
    stroke: { curve: 'straight', width: 3 },
    markers: { size: 4 },
    dataLabels: { enabled: false },
    colors: ['rgb(76,175,80)']
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
.stw-mmu {
  width: 100%;
}
</style>

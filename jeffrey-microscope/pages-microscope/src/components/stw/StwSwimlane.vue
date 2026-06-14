<template>
  <div ref="chartEl" class="stw-swimlane"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import ApexCharts from 'apexcharts';
import FormattingService from '@/services/FormattingService';
import { STW_LANES, laneFor } from '@/services/stw/stwLanes';
import type { StwEvent } from '@/services/api/model/stw/StwModels';

const props = defineProps<{
  events: StwEvent[];
}>();

const emit = defineEmits<{
  select: [event: StwEvent];
}>();

const chartEl = ref<HTMLElement | null>(null);
let chart: ApexCharts | null = null;

// Parallel array: data point index -> source event, for click + tooltip lookups.
let pointEvents: StwEvent[] = [];

const ROW_HEIGHT_PX = 46;
const MIN_HEIGHT_PX = 160;
const NANOS_PER_MILLI = 1_000_000;

interface RangePoint {
  x: string;
  y: [number, number];
  fillColor: string;
}

function buildPoints(): RangePoint[] {
  const points: RangePoint[] = [];
  pointEvents = [];
  for (const lane of STW_LANES) {
    for (const event of props.events) {
      if (event.category === lane.category) {
        const start = event.timeOffsetMillis;
        const end = start + event.durationNanos / NANOS_PER_MILLI;
        points.push({ x: lane.label, y: [start, end], fillColor: lane.color });
        pointEvents.push(event);
      }
    }
  }
  return points;
}

function presentLaneCount(): number {
  const present = new Set(props.events.map((event) => event.category));
  return STW_LANES.filter((lane) => present.has(lane.category)).length;
}

function buildOptions(): ApexCharts.ApexOptions {
  return {
    chart: {
      type: 'rangeBar',
      height: Math.max(MIN_HEIGHT_PX, presentLaneCount() * ROW_HEIGHT_PX),
      animations: { enabled: false },
      toolbar: { show: false },
      events: {
        dataPointSelection: (_event: unknown, _ctx: unknown, config: { dataPointIndex: number }) => {
          const selected = pointEvents[config.dataPointIndex];
          if (selected) {
            emit('select', selected);
          }
        }
      }
    },
    series: [{ name: 'Pauses', data: buildPoints() }],
    plotOptions: {
      bar: { horizontal: true, rangeBarGroupRows: true, barHeight: '60%' }
    },
    dataLabels: { enabled: false },
    xaxis: {
      type: 'numeric',
      labels: {
        formatter: (value: string | number) => FormattingService.formatDurationMillisCoarse(Number(value))
      }
    },
    tooltip: {
      custom: ({ dataPointIndex }: { dataPointIndex: number }) => {
        const event = pointEvents[dataPointIndex];
        if (!event) {
          return '';
        }
        const lane = laneFor(event.category);
        const duration = FormattingService.formatDuration2Units(event.durationNanos);
        const at = FormattingService.formatDurationMillisCoarse(event.timeOffsetMillis);
        const thread = event.thread ? `<div>Thread: ${event.thread}</div>` : '';
        return (
          `<div class="stw-tooltip">` +
          `<div><strong>${lane.label}</strong></div>` +
          `<div>${event.label}</div>` +
          `<div>Duration: ${duration}</div>` +
          `<div>At: +${at}</div>` +
          thread +
          `</div>`
        );
      }
    },
    legend: { show: false }
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
watch(() => props.events, renderChart, { deep: false });
onUnmounted(() => {
  if (chart) {
    chart.destroy();
    chart = null;
  }
});
</script>

<style scoped>
.stw-swimlane {
  width: 100%;
}
</style>

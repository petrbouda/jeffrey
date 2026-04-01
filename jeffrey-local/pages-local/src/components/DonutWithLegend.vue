<template>
  <div>
    <div ref="chartRef" :style="{ height: chartHeight + 'px' }"></div>
    <table v-if="data.legendItems.length > 0" class="legend-table">
      <tbody>
      <tr v-for="(item, index) in data.legendItems" :key="index">
        <td><span class="legend-dot" :style="{ backgroundColor: item.color }"></span></td>
        <td>{{ item.label }}</td>
        <td>{{ item.value }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onUnmounted, watch, nextTick} from 'vue';
import ApexCharts from 'apexcharts';

export interface DonutLegendItem {
  color: string;
  label: string;
  value: string;
}

export interface DonutChartData {
  series: number[];
  labels: string[];
  colors: string[];
  legendItems: DonutLegendItem[];
  totalLabel?: string;
  totalValue: string;
}

const props = withDefaults(defineProps<{
  data: DonutChartData;
  tooltipFormatter?: (val: number) => string;
  chartHeight?: number;
}>(), {
  tooltipFormatter: (val: number) => val + ' items',
  chartHeight: 220
});

const chartRef = ref<HTMLElement | null>(null);
let chart: ApexCharts | null = null;

const renderChart = async () => {
  await nextTick();
  if (!chartRef.value || props.data.series.length === 0) return;

  if (chart) chart.destroy();

  chart = new ApexCharts(chartRef.value, {
    chart: {
      type: 'donut' as const,
      height: props.chartHeight,
      fontFamily: 'inherit'
    },
    series: [...props.data.series],
    labels: [...props.data.labels],
    colors: [...props.data.colors],
    legend: {show: false},
    dataLabels: {
      enabled: true,
      formatter: (val: number) => val.toFixed(1) + '%',
      style: {fontSize: '10px'}
    },
    plotOptions: {
      pie: {
        donut: {
          size: '60%',
          labels: {
            show: true,
            total: {
              show: true,
              label: props.data.totalLabel || 'Total',
              fontSize: '11px',
              fontWeight: 500,
              color: '#748194',
              formatter: () => props.data.totalValue
            }
          }
        }
      }
    },
    tooltip: {
      y: {formatter: props.tooltipFormatter}
    },
    stroke: {width: 2, colors: ['#fff']}
  });
  chart.render();
};

watch(() => props.data, renderChart, {deep: true});
onMounted(renderChart);
onUnmounted(() => {
  if (chart) {
    chart.destroy();
    chart = null;
  }
});
</script>

<style scoped>
.legend-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0.5rem;
}

.legend-table td {
  padding: 0.3rem 0.5rem;
  border: none;
  font-size: 0.8rem;
  color: var(--color-text);
}

.legend-table td:first-child {
  width: 20px;
  padding-right: 0;
}

.legend-table td:last-child {
  text-align: right;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: var(--color-dark);
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>

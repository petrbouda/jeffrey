<template>
  <BasePanel :title="title">
    <div ref="chartRef" :style="{ height: height + 'px' }"></div>
    <table v-if="legendItems.length > 0" class="legend-table">
      <tbody>
        <tr v-for="(item, index) in legendItems" :key="index">
          <td><span class="legend-dot" :style="{ backgroundColor: item.color }"></span></td>
          <td>{{ item.label }}</td>
          <td class="text-end font-monospace">{{ item.value }}</td>
        </tr>
      </tbody>
    </table>
  </BasePanel>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue';
import ApexCharts from 'apexcharts';
import BasePanel from '@/components/BasePanel.vue';

export interface LegendItem {
  color: string;
  label: string;
  value: string;
}

const props = withDefaults(
  defineProps<{
    title: string;
    series: number[];
    labels: string[];
    colors: string[];
    legendItems?: LegendItem[];
    totalLabel?: string;
    totalValue: string;
    tooltipFormatter: (val: number) => string;
    height?: number;
  }>(),
  {
    legendItems: () => [],
    totalLabel: 'Total',
    height: 250
  }
);

const chartRef = ref<HTMLElement | null>(null);
let chart: ApexCharts | null = null;

const renderChart = async () => {
  await nextTick();

  if (!chartRef.value) return;

  if (chart) {
    chart.destroy();
  }

  const options = {
    chart: {
      type: 'donut' as const,
      height: props.height
    },
    series: [...props.series],
    labels: [...props.labels],
    colors: [...props.colors],
    legend: {
      show: false
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => val.toFixed(1) + '%'
    },
    tooltip: {
      y: {
        formatter: props.tooltipFormatter
      }
    },
    plotOptions: {
      pie: {
        donut: {
          labels: {
            show: true,
            value: {
              formatter: () => props.totalValue
            },
            total: {
              show: true,
              label: props.totalLabel,
              formatter: () => props.totalValue
            }
          }
        }
      }
    }
  };

  chart = new ApexCharts(chartRef.value, options);
  chart.render();
};

watch(() => [props.series, props.labels, props.colors, props.totalValue], renderChart, {
  deep: true
});

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
  margin-top: 0.5rem;
  width: 100%;
  border-collapse: collapse;
}

.legend-table td {
  padding: 0.25rem 0.5rem;
  border: none;
  font-size: 0.85rem;
}

.legend-table td:first-child {
  width: 20px;
  padding-right: 0;
}

.legend-table td:last-child {
  text-align: right;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>

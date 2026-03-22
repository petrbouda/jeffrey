<template>
  <ChartSection :title="title" :icon="icon" container-class="apex-chart-container">
    <div ref="chartRef" class="apex-chart"></div>
  </ChartSection>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
import ApexCharts from 'apexcharts';
import ChartSection from '@/components/ChartSection.vue';

interface ChartDataItem {
  label: string;
  value: number;
}

interface Props {
  title: string;
  icon: string;
  data: ChartDataItem[];
  total: number;
  colorMapping?: (label: string, index: number) => string;
  valueFormatter?: (value: number) => string;
}

const props = withDefaults(defineProps<Props>(), {
  valueFormatter: (value: number) => value + ' items'
});

// Chart ref
const chartRef = ref<HTMLElement | null>(null);
let chartInstance: ApexCharts | null = null;


// Chart creation function
const createChart = async () => {
  if (!chartRef.value || !props.data?.length) return;
  
  // Destroy existing chart if it exists
  if (chartInstance) {
    chartInstance.destroy();
    chartInstance = null;
  }
  
  const series = props.data.map(item => item.value);
  const labels = props.data.map(item => item.label);
  
  if (series.length === 0) return;
  
  const options = {
    series,
    chart: {
      type: 'donut',
      height: 350,
      animations: {
        enabled: true
      }
    },
    labels,
    ...(props.colorMapping && {
      colors: labels.map((label, index) => props.colorMapping!(label, index))
    }),
    legend: {
      position: 'right',
      fontSize: '14px'
    },
    plotOptions: {
      pie: {
        donut: {
          size: '60%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Total',
              formatter: () => props.total.toString()
            }
          }
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => Math.round(val) + '%'
    },
    tooltip: {
      y: {
        formatter: (val: number) => props.valueFormatter(val)
      }
    },
    responsive: [{
      breakpoint: 480,
      options: {
        chart: {
          height: 300
        },
        legend: {
          position: 'right'
        }
      }
    }]
  };
  
  try {
    chartInstance = new ApexCharts(chartRef.value, options);
    await chartInstance.render();
  } catch (error) {
    console.error(`Error creating chart "${props.title}":`, error);
  }
};

// Watch for prop changes and recreate chart
watch(
  () => [props.data, props.total, props.title],
  async () => {
    await nextTick();
    // Add a small delay to ensure DOM is fully rendered
    setTimeout(async () => {
      await createChart();
    }, 100);
  },
  { deep: true }
);

onMounted(async () => {
  await nextTick();
  // Add a small delay to ensure DOM is fully rendered
  setTimeout(async () => {
    await createChart();
  }, 100);
});

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.destroy();
  }
});
</script>

<style scoped>
.apex-chart {
  height: 100%;
}

.apex-chart-container {
  height: 350px;
}

@media (max-width: 768px) {
  .apex-chart-container {
    height: 300px;
  }
}
</style>

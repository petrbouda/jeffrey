<template>
  <section class="dashboard-section">
    <div class="charts-grid">
      <!-- Status Codes Pie Chart -->
      <div class="chart-card">
        <div class="chart-header">
          <h4><i class="bi bi-pie-chart me-2"></i>Status Code Distribution</h4>
        </div>
        <div class="chart-container">
          <div ref="statusCodeChart" class="apex-chart"></div>
        </div>
      </div>

      <!-- HTTP Methods Pie Chart -->
      <div class="chart-card">
        <div class="chart-header">
          <h4><i class="bi bi-diagram-3 me-2"></i>HTTP Methods Distribution</h4>
        </div>
        <div class="chart-container">
          <div ref="httpMethodChart" class="apex-chart"></div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
import ApexCharts from 'apexcharts';

interface StatusCode {
  code: number;
  count: number;
}

interface HttpMethod {
  method: string;
  count: number;
}

interface Props {
  statusCodes: StatusCode[];
  methods: HttpMethod[];
  totalRequests: number;
}

const props = defineProps<Props>();

// Chart refs
const statusCodeChart = ref<HTMLElement | null>(null);
const httpMethodChart = ref<HTMLElement | null>(null);
let statusCodeChartInstance: ApexCharts | null = null;
let httpMethodChartInstance: ApexCharts | null = null;

// Chart creation functions
const createStatusCodeChart = async () => {
  if (!statusCodeChart.value || !props.statusCodes?.length) return;
  
  // Destroy existing chart if it exists
  if (statusCodeChartInstance) {
    statusCodeChartInstance.destroy();
    statusCodeChartInstance = null;
  }
  
  const series = props.statusCodes.map(status => status.count);
  const labels = props.statusCodes.map(status => status.code.toString());
  
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
    colors: labels.map(statusCode => {
      const code = parseInt(statusCode);
      if (code >= 200 && code < 300) return '#5cb85c'; // medium green (success)
      if (code >= 300 && code < 400) return '#5a9fd4'; // medium blue (redirect)
      if (code >= 400 && code < 500) return '#f0ad4e'; // medium orange (client error)
      if (code >= 500) return '#d9534f'; // medium red (server error)
      return '#6c757d'; // medium gray for other codes
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
              formatter: () => props.totalRequests.toString()
            }
          }
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => Math.round(val) + '%'
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
    statusCodeChartInstance = new ApexCharts(statusCodeChart.value, options);
    await statusCodeChartInstance.render();
  } catch (error) {
    console.error('Error creating status code chart:', error);
  }
};

const createHttpMethodChart = async () => {
  if (!httpMethodChart.value || !props.methods?.length) return;
  
  // Destroy existing chart if it exists
  if (httpMethodChartInstance) {
    httpMethodChartInstance.destroy();
    httpMethodChartInstance = null;
  }
  
  const series = props.methods.map(method => method.count);
  const labels = props.methods.map(method => method.method);
  
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
    colors: labels.map(method => {
      switch (method.toUpperCase()) {
        case 'GET': return '#5a9fd4'; // medium blue
        case 'POST': return '#5cb85c'; // medium green
        case 'PUT': return '#f0ad4e'; // medium orange
        case 'DELETE': return '#d9534f'; // medium red
        case 'PATCH': return '#6c757d'; // medium gray
        case 'OPTIONS': return '#9b59b6'; // medium purple
        default: return '#95a5a6'; // medium gray for other methods
      }
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
              formatter: () => props.totalRequests.toString()
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
        formatter: (val: number) => val + ' requests'
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
    httpMethodChartInstance = new ApexCharts(httpMethodChart.value, options);
    await httpMethodChartInstance.render();
  } catch (error) {
    console.error('Error creating HTTP method chart:', error);
  }
};

// Watch for prop changes and recreate charts
watch(
  () => [props.statusCodes, props.methods, props.totalRequests],
  async () => {
    await nextTick();
    // Add a small delay to ensure DOM is fully rendered
    setTimeout(async () => {
      await createStatusCodeChart();
      await createHttpMethodChart();
    }, 100);
  },
  { deep: true }
);

onMounted(async () => {
  await nextTick();
  // Add a small delay to ensure DOM is fully rendered
  setTimeout(async () => {
    await createStatusCodeChart();
    await createHttpMethodChart();
  }, 100);
});

onUnmounted(() => {
  if (statusCodeChartInstance) {
    statusCodeChartInstance.destroy();
  }
  if (httpMethodChartInstance) {
    httpMethodChartInstance.destroy();
  }
});
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
}

.chart-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.chart-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
}

.chart-container {
  height: 400px;
  padding: 1rem;
}

.apex-chart {
  height: 100%;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-container {
    height: 300px;
  }
}
</style>
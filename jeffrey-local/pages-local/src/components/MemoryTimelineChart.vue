<template>
  <div class="memory-timeline-chart-wrapper">
    <div class="chart-controls mb-2">
      <div class="d-flex justify-content-between align-items-center">
        <h5 class="mb-0">
          <i class="bi bi-graph-up text-primary me-2"></i>
          Memory Usage Timeline
        </h5>
        <select v-model="selectedTimeRange" @change="updateChart" class="form-select form-select-sm" style="width: auto; min-width: 120px;">
          <option value="1h">Last Hour</option>
          <option value="6h">Last 6 Hours</option>
          <option value="24h">Last 24 Hours</option>
          <option value="all">All Data</option>
        </select>
      </div>
    </div>
    <div class="chart-container" ref="chartContainer"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue';
import ApexCharts from 'apexcharts';
import FormattingService from '@/services/FormattingService';

const props = defineProps<{
  data?: any[];
  height?: number;
}>();

const chartContainer = ref<HTMLElement | null>(null);
const selectedTimeRange = ref('6h');
let chart: ApexCharts | null = null;

// Generate mock data if not provided
const generateMemoryData = (timeRange: string) => {
  const now = Date.now();
  const ranges = {
    '1h': 60 * 60 * 1000,      // 1 hour
    '6h': 6 * 60 * 60 * 1000,  // 6 hours
    '24h': 24 * 60 * 60 * 1000, // 24 hours
    'all': 7 * 24 * 60 * 60 * 1000  // 7 days
  };
  
  const timeSpan = ranges[timeRange as keyof typeof ranges] || ranges['6h'];
  const startTime = now - timeSpan;
  
  // Generate data points every 30 seconds for 1h, every 5 minutes for longer periods
  const interval = timeRange === '1h' ? 30 * 1000 : 5 * 60 * 1000;
  const pointCount = Math.floor(timeSpan / interval);
  
  const data = [];
  let heapUsed = 800; // Start at 800MB
  let edenSpace = 100;
  let oldGen = 600;
  let metaspace = 60;
  
  for (let i = 0; i < pointCount; i++) {
    const timestamp = startTime + (i * interval);
    
    // Simulate realistic heap behavior with GC cycles
    if (i % 40 === 0) {
      // Major GC - significant drop in old gen
      oldGen = Math.max(300, oldGen * 0.4);
      edenSpace = Math.max(20, edenSpace * 0.1);
    } else if (i % 8 === 0) {
      // Minor GC - Eden space cleanup
      edenSpace = Math.max(20, edenSpace * 0.2);
      oldGen = Math.min(1000, oldGen + (edenSpace * 0.1));
    } else {
      // Normal allocation
      edenSpace = Math.min(250, edenSpace + Math.random() * 15);
      oldGen = Math.min(1000, oldGen + Math.random() * 2);
    }
    
    // Add some noise for realism
    edenSpace += (Math.random() - 0.5) * 10;
    oldGen += (Math.random() - 0.5) * 20;
    metaspace += (Math.random() - 0.5) * 2;
    
    // Keep values in reasonable bounds
    edenSpace = Math.max(10, Math.min(250, edenSpace));
    oldGen = Math.max(200, Math.min(1000, oldGen));
    metaspace = Math.max(50, Math.min(120, metaspace));
    
    heapUsed = edenSpace + oldGen;
    
    data.push({
      timestamp,
      heapUsed: Math.round(heapUsed),
      edenSpace: Math.round(edenSpace),
      oldGen: Math.round(oldGen),
      metaspace: Math.round(metaspace)
    });
  }
  
  return data;
};

const createChart = async () => {
  await nextTick();
  if (!chartContainer.value) return;
  
  // Use provided data or generate mock data
  const chartData = props.data || generateMemoryData(selectedTimeRange.value);
  
  const series = [
    {
      name: 'Total Heap Used',
      data: chartData.map(d => [d.timestamp, d.heapUsed]),
      color: '#007bff'
    },
    {
      name: 'Eden Space',
      data: chartData.map(d => [d.timestamp, d.edenSpace]),
      color: '#28a745'
    },
    {
      name: 'Old Generation',
      data: chartData.map(d => [d.timestamp, d.oldGen]),
      color: '#ffc107'
    },
    {
      name: 'Metaspace',
      data: chartData.map(d => [d.timestamp, d.metaspace]),
      color: '#6f42c1'
    }
  ];
  
  const options = {
    chart: {
      type: 'area',
      height: props.height || '100%',
      width: '100%',
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      zoom: {
        enabled: true,
        type: 'x'
      },
      toolbar: {
        show: true,
        tools: {
          download: true,
          selection: true,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        }
      }
    },
    series,
    stroke: {
      curve: 'smooth',
      width: 2
    },
    fill: {
      type: 'gradient',
      gradient: {
        opacityFrom: 0.3,
        opacityTo: 0.1
      }
    },
    xaxis: {
      type: 'datetime',
      labels: {
        format: 'HH:mm:ss'
      }
    },
    yaxis: {
      title: {
        text: 'Memory Usage (MB)'
      },
      labels: {
        formatter: (value: number) => Math.round(value) + ' MB'
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      x: {
        format: 'HH:mm:ss.fff'
      },
      y: {
        formatter: (value: number) => Math.round(value) + ' MB'
      }
    },
    legend: {
      position: 'top',
      horizontalAlign: 'left'
    },
    grid: {
      borderColor: '#e7e7e7',
      row: {
        colors: ['#f3f3f3', 'transparent'],
        opacity: 0.5
      }
    },
    markers: {
      size: 0,
      hover: {
        sizeOffset: 4
      }
    }
  };
  
  if (chart) {
    chart.destroy();
  }
  
  chart = new ApexCharts(chartContainer.value, options);
  chart.render();
};

const updateChart = () => {
  createChart();
};

onMounted(() => {
  createChart();
});

onUnmounted(() => {
  if (chart) {
    chart.destroy();
  }
});

watch(() => props.data, () => {
  createChart();
}, { deep: true });
</script>

<style scoped>
.memory-timeline-chart-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chart-container {
  flex: 1;
  min-height: 500px; /* Minimum height */
  width: 100%;
}

.chart-controls {
  background-color: #f8f9fa;
  border-radius: 0.375rem;
  padding: 0.75rem;
}
</style>
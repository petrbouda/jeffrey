<template>
  <div class="apex-time-series-chart" ref="chartContainer">
    <div class="chart-content">
      <!-- Main chart -->
      <div class="main-chart-container">
        <apexchart
          ref="mainChart"
          type="area"
          height="300"
          :options="mainChartOptions"
          :series="mainChartSeries"
        />
      </div>

      <!-- Brush/navigator chart -->
      <div class="brush-chart-container">
        <apexchart
          ref="brushChart"
          type="area"
          height="120"
          :options="brushChartOptions"
          :series="brushChartSeries"
        />
      </div>

      <!-- Time range display -->
      <div class="time-labels">
        <span class="time-label-start">{{ formatTime(dataMinTime) }}</span>
        <span class="time-label-center">Showing: {{ formatTimeRange(visibleStartTime, visibleEndTime) }}</span>
        <span class="time-label-end">{{ formatTime(dataMaxTime) }}</span>
      </div>

      <!-- Title with colored icons -->
      <div class="graph-title">
        <div v-if="props.primaryTitle" class="graph-title-item">
          <span class="graph-title-icon" style="background-color: #2E93fA;"></span>
          <span class="graph-title-text">{{ props.primaryTitle }}</span>
        </div>
        <div v-if="props.secondaryTitle" class="graph-title-item">
          <span class="graph-title-icon" style="background-color: #8E44AD;"></span>
          <span class="graph-title-text">{{ props.secondaryTitle }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import FormattingService from '@/services/FormattingService.ts';

// Define props
const props = defineProps<{
  primaryData?: number[][];
  secondaryData?: number[][];
  primaryTitle?: string;
  secondaryTitle?: string;
  secondaryUnit?: string;
  visibleMinutes?: number;
  independentSecondaryAxis?: boolean;
  primaryAxisType?: 'number' | 'duration' | 'bytes';
  secondaryAxisType?: 'number' | 'duration' | 'bytes';
}>();

// Default values
const defaultVisibleMinutes = 15;

// Refs
const chartContainer = ref<HTMLDivElement | null>(null);
const mainChart = ref();
const brushChart = ref();

// Data processing
const dataMinTime = ref(0);
const dataMaxTime = ref(0);
const visibleStartTime = ref(0);
const visibleEndTime = ref(0);
let selectionTimeout: NodeJS.Timeout | null = null;

// Colors
const primaryColor = '#2E93fA';
const secondaryColor = '#8E44AD';

// Calculate min/max time values
const calculateMinMaxTimeValues = (): void => {
  if (!Array.isArray(props.primaryData) || props.primaryData.length === 0) {
    dataMinTime.value = 0;
    dataMaxTime.value = 0;
    return;
  }
  
  let min = props.primaryData[0][0];
  let max = props.primaryData[0][0];
  
  for (let i = 0; i < props.primaryData.length; i++) {
    const point = props.primaryData[i];
    if (point[0] < min) min = point[0];
    if (point[0] > max) max = point[0];
  }
  
  // Include secondary data in time range if available
  if (props.secondaryData && props.secondaryData.length > 0) {
    for (let i = 0; i < props.secondaryData.length; i++) {
      const point = props.secondaryData[i];
      if (point[0] < min) min = point[0];
      if (point[0] > max) max = point[0];
    }
  }
  
  dataMinTime.value = min;
  dataMaxTime.value = max;
  
  // Initialize visible range
  const totalRange = max - min;
  const visibleRange = Math.min((props.visibleMinutes || defaultVisibleMinutes) * 60, totalRange);
  visibleStartTime.value = min;
  visibleEndTime.value = min + visibleRange;
};

// Format value based on axis type
const formatValue = (value: number, axisType?: 'number' | 'duration' | 'bytes'): string => {
  switch (axisType) {
    case 'duration':
      return FormattingService.formatDuration2Units(value);
    case 'bytes':
      return FormattingService.formatBytes(value);
    default:
      return Math.round(value).toString();
  }
};

// Format time functions
const formatTime = (seconds: number): string => {
  const date = new Date(seconds * 1000);
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const secs = String(date.getUTCSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${secs}`;
};

const formatTimeRange = (startSeconds: number, endSeconds: number): string => {
  return `${formatTime(startSeconds)} - ${formatTime(endSeconds)}`;
};

// Convert data format for ApexCharts with optional downsampling
const processDataForApex = (data: number[][] = [], maxPoints: number = 5000): Array<{x: number, y: number}> => {
  if (data.length <= maxPoints) {
    return data.map(point => ({
      x: point[0] * 1000, // Convert to milliseconds for ApexCharts
      y: point[1]
    }));
  }
  
  // Downsample using every nth point
  const step = Math.ceil(data.length / maxPoints);
  const downsampled = [];
  
  for (let i = 0; i < data.length; i += step) {
    downsampled.push({
      x: data[i][0] * 1000,
      y: data[i][1]
    });
  }
  
  return downsampled;
};

// Filter data for visible range with downsampling
const getVisibleData = (data: number[][] = []): Array<{x: number, y: number}> => {
  const filtered = data.filter(point => point[0] >= visibleStartTime.value && point[0] <= visibleEndTime.value);
  
  // Downsample if too many points for main chart
  if (filtered.length > 5000) {
    const step = Math.ceil(filtered.length / 5000);
    const downsampled = [];
    for (let i = 0; i < filtered.length; i += step) {
      downsampled.push({
        x: filtered[i][0] * 1000,
        y: filtered[i][1]
      });
    }
    return downsampled;
  }
  
  return filtered.map(point => ({
    x: point[0] * 1000,
    y: point[1]
  }));
};

// Main chart series
const mainChartSeries = computed(() => {
  const series = [];
  
  if (props.primaryData && props.primaryData.length > 0) {
    series.push({
      name: props.primaryTitle || 'Primary',
      data: getVisibleData(props.primaryData),
      color: primaryColor
    });
  }
  
  if (props.secondaryData && props.secondaryData.length > 0) {
    series.push({
      name: props.secondaryTitle || 'Secondary',
      data: getVisibleData(props.secondaryData),
      color: secondaryColor
    });
  }
  
  return series;
});

// Brush chart series (full data)
const brushChartSeries = computed(() => {
  const series = [];
  
  if (props.primaryData && props.primaryData.length > 0) {
    series.push({
      name: props.primaryTitle || 'Primary',
      data: processDataForApex(props.primaryData, 750),
      color: primaryColor
    });
  }
  
  if (props.secondaryData && props.secondaryData.length > 0) {
    series.push({
      name: props.secondaryTitle || 'Secondary',
      data: processDataForApex(props.secondaryData, 750),
      color: secondaryColor
    });
  }
  return series;
});

// Main chart options
const mainChartOptions = computed(() => ({
  chart: {
    id: 'main-chart',
    type: 'area',
    height: 300,
    toolbar: {
      show: false
    },
    zoom: {
      enabled: false,
      type: 'x',
      autoScaleYaxis: true
    },
    interactions: {
      enabled: false
    }
  },
  dataLabels: {
    enabled: false
  },
  stroke: {
    curve: 'smooth',
    width: 1
  },
  fill: {
    type: 'gradient',
    gradient: {
      opacityFrom: 0.4,
      opacityTo: 0.1
    }
  },
  xaxis: {
    type: 'datetime',
    min: visibleStartTime.value * 1000,
    max: visibleEndTime.value * 1000,
    labels: {
      formatter: function(value: number) {
        return formatTime(value / 1000);
      }
    }
  },
  yaxis: props.independentSecondaryAxis && props.secondaryData ? [
    {
      title: {
        text: props.primaryTitle || 'Primary'
      },
      min: 0,
      labels: {
        formatter: function(value: number) {
          return formatValue(value, props.primaryAxisType);
        }
      }
    },
    {
      opposite: true,
      title: {
        text: props.secondaryTitle || 'Secondary'
      },
      min: 0,
      labels: {
        formatter: function(value: number) {
          return formatValue(value, props.secondaryAxisType);
        }
      }
    }
  ] : {
    min: 0,
    labels: {
      formatter: function(value: number) {
        return formatValue(value, props.primaryAxisType);
      }
    }
  },
  tooltip: {
    x: {
      formatter: function(value: number) {
        return formatTime(value / 1000);
      }
    },
    y: {
      formatter: function(value: number, { seriesIndex }: { seriesIndex: number }) {
        const axisType = seriesIndex === 0 ? props.primaryAxisType : props.secondaryAxisType;
        return formatValue(value, axisType);
      }
    }
  },
  legend: {
    show: false
  },
  grid: {
    borderColor: '#e9ecef'
  },
  animations: {
    enabled: false
  }
}));

// Brush chart options with selection functionality
const brushChartOptions = computed(() => ({
  chart: {
    id: 'brush-chart',
    type: 'area',
    height: 100,
    brush: {
      target: 'main-chart',
      enabled: true
    },
    toolbar: {
      show: false
    },
    zoom: {
      enabled: false
    },
    selection: {
      enabled: true,
      xaxis: {
        min: visibleStartTime.value * 1000,
        max: visibleEndTime.value * 1000
      }
    },
    events: {
      selection: function(_: any, { xaxis }: { xaxis: { min: number, max: number } }) {
        // Clear existing timeout to prevent multiple updates
        if (selectionTimeout) {
          clearTimeout(selectionTimeout);
        }
        
        // Debounce the update to prevent twitching and loops
        selectionTimeout = setTimeout(() => {
          const selectionStart = xaxis.min / 1000;
          const selectionEnd = xaxis.max / 1000;
          
          // Use the exact selection without minimum constraints
          visibleStartTime.value = selectionStart;
          visibleEndTime.value = selectionEnd;
          selectionTimeout = null;
        }, 1000);
      }
    },
    sparkline: {
      enabled: true
    }
  },
  dataLabels: {
    enabled: false
  },
  stroke: {
    curve: 'smooth',
    width: 1
  },
  fill: {
    type: 'solid',
    opacity: 0.3
  },
  xaxis: {
    type: 'datetime',
    labels: {
      show: false
    },
    axisTicks: {
      show: false
    },
    axisBorder: {
      show: false
    }
  },
  yaxis: props.independentSecondaryAxis && props.secondaryData ? [
    {
      min: 0,
      labels: {
        show: false
      },
      axisTicks: {
        show: false
      },
      axisBorder: {
        show: false
      }
    },
    {
      opposite: true,
      min: 0,
      labels: {
        show: false
      },
      axisTicks: {
        show: false
      },
      axisBorder: {
        show: false
      }
    }
  ] : {
    min: 0,
    labels: {
      show: false
    },
    axisTicks: {
      show: false
    },
    axisBorder: {
      show: false
    }
  },
  grid: {
    show: false
  },
  legend: {
    show: false
  },
  tooltip: {
    enabled: false
  },
  animations: {
    enabled: false
  }
}));

// Watch for data changes
watch(() => props.primaryData, (newData) => {
  if (newData && newData.length > 0) {
    calculateMinMaxTimeValues();
  }
}, { deep: true, immediate: true });

watch(() => props.secondaryData, () => {
  // Secondary data change doesn't need special handling
}, { deep: true, immediate: true });

// Initialize on mount
onMounted(async () => {
  calculateMinMaxTimeValues();
  
  // Force chart refresh after DOM is updated to ensure proper positioning
  await nextTick();
  if (brushChart.value?.updateOptions) {
    brushChart.value.updateOptions(brushChartOptions.value, false);
  }
});
</script>

<style scoped>
.apex-time-series-chart {
  width: 100%;
  min-height: 460px;
  position: relative;
  padding: 0 10px;
  box-sizing: border-box;
}

.chart-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.main-chart-container {
  width: 100%;
  height: 320px;
  position: relative;
  overflow: hidden;
}

.brush-chart-container {
  width: 100%;
  height: 100px;
  position: relative;
  margin-top: 20px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
}

.brush-chart-container :deep(.apexcharts-canvas) {
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
}

.time-labels {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #666;
  margin-top: -2px;
  width: 100%;
}

.time-label-start {
  text-align: left;
  flex: 1;
}

.time-label-center {
  text-align: center;
  font-size: 12px;
  flex: 2;
}

.time-label-end {
  text-align: right;
  flex: 1;
}

.graph-title {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
  gap: 16px;
}

.graph-title-item {
  display: flex;
  align-items: center;
}

.graph-title-icon {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 6px;
  border-radius: 2px;
}

.graph-title-text {
  font-size: 11px;
  color: #555;
  font-weight: 500;
}

@media (max-width: 768px) {
  .apex-time-series-chart {
    padding: 0 5px;
  }

  .main-chart-container {
    height: 250px;
  }

  .brush-chart-container {
    height: 80px;
  }
}
</style>

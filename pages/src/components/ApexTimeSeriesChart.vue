<template>
  <div class="apex-time-series-chart" ref="chartContainer">
    <div class="chart-content">
      <!-- Main chart -->
      <div class="main-chart-container">
        <apexchart
          ref="mainChart"
          :type="props.showPoints ? 'scatter' : 'area'"
          height="300"
          :options="mainChartOptions"
          :series="mainChartSeries"
        />
      </div>

      <!-- Brush/navigator chart -->
      <div class="brush-chart-container">
        <!-- Reset button in top right corner of brush chart -->
        <button class="reset-zoom-btn reset-zoom-btn-corner" @click="resetBrushSelection" title="Reset to full time range">
          <i class="bi bi-zoom-out"></i>
        </button>
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
        <span class="time-label-center">
          Showing: {{ formatTimeRange(visibleStartTime, visibleEndTime) }}
        </span>
        <span class="time-label-end">{{ formatTime(dataMaxTime) }}</span>
      </div>

      <!-- Title with colored icons -->
      <div class="graph-title">
        <div v-if="props.primaryTitle" class="graph-title-item">
          <span class="graph-title-icon" :style="`background-color: ${primaryColor};`"></span>
          <span class="graph-title-text">{{ props.primaryTitle }}</span>
        </div>
        <div v-if="props.secondaryTitle" class="graph-title-item">
          <span class="graph-title-icon" :style="`background-color: ${secondaryColor};`"></span>
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
  primaryAxisType?: 'number' | 'durationInNanos' | 'bytes' | 'durationInMillis';
  secondaryAxisType?: 'number' | 'durationInNanos' | 'bytes' | 'durationInMillis';
  stacked?: boolean;
  primaryColor?: string;
  secondaryColor?: string;
  showPoints?: boolean;
  timeUnit?: 'seconds' | 'milliseconds'; // New prop to control time unit
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
const primaryColor = props.primaryColor || '#2E93fA';
const secondaryColor = props.secondaryColor || '#8E44AD';

// Calculate max Y-axis values for consistent scaling
const primaryMaxValue = ref(0);
const secondaryMaxValue = ref(0);

// Helper function to find max value in a data series with padding
const findMaxValueInSeries = (data: number[][] | undefined): number => {
  if (!data || data.length === 0) return 0;
  
  let max = 0;
  for (let i = 0; i < data.length; i++) {
    const value = data[i][1];
    if (value > max) max = value;
  }
  
  // Add 10% padding if max value is greater than 0
  return max > 0 ? max * 1.1 : 0;
};

const calculateMaxYValues = (): void => {
  if (props.stacked && props.primaryData && props.secondaryData) {
    // For stacked charts, calculate the maximum sum at any point
    let maxSum = 0;
    const minLength = Math.min(props.primaryData.length, props.secondaryData.length);
    
    for (let i = 0; i < minLength; i++) {
      const primaryValue = props.primaryData[i][1] || 0;
      const secondaryValue = props.secondaryData[i][1] || 0;
      const sum = primaryValue + secondaryValue;
      if (sum > maxSum) maxSum = sum;
    }
    
    // Add 10% padding
    primaryMaxValue.value = maxSum > 0 ? maxSum * 1.1 : 0;
    secondaryMaxValue.value = primaryMaxValue.value; // Same scale for stacked
  } else {
    // Calculate axis max values with padding applied in helper
    primaryMaxValue.value = findMaxValueInSeries(props.primaryData);
    secondaryMaxValue.value = findMaxValueInSeries(props.secondaryData);
  }
};

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
  const isMilliseconds = props.timeUnit === 'milliseconds';
  const visibleRangeInSeconds = (props.visibleMinutes || defaultVisibleMinutes) * 60;
  const visibleRange = Math.min(isMilliseconds ? visibleRangeInSeconds * 1000 : visibleRangeInSeconds, totalRange);
  visibleStartTime.value = min;
  visibleEndTime.value = min + visibleRange;

  // Calculate Y-axis max values
  calculateMaxYValues();
};

// Format value based on axis type
const formatValue = (value: number, axisType?: 'number' | 'durationInNanos' | 'bytes' | 'durationInMillis'): string => {
  switch (axisType) {
    case 'durationInNanos':
      return FormattingService.formatDuration2Units(value);
    case 'durationInMillis':
      return FormattingService.formatDuration2Units(value * 1_000_000); // Convert ms to ns
    case 'bytes':
      return FormattingService.formatBytes(value);
    default:
      return Math.round(value).toString();
  }
};

// Format time functions
const formatTime = (timeValue: number): string => {
  const isMilliseconds = props.timeUnit === 'milliseconds';
  const date = new Date(isMilliseconds ? timeValue : timeValue * 1000);
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const secs = String(date.getUTCSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${secs}`;
};

const formatTimeRange = (startTime: number, endTime: number): string => {
  return `${formatTime(startTime)} - ${formatTime(endTime)}`;
};

const resetBrushSelection = async (): Promise<void> => {
  visibleStartTime.value = dataMinTime.value;
  visibleEndTime.value = dataMaxTime.value;
  
  await nextTick();
  
  if (brushChart.value?.updateOptions) {
    const isMilliseconds = props.timeUnit === 'milliseconds';
    brushChart.value.updateOptions({
      chart: {
        selection: {
          xaxis: {
            min: isMilliseconds ? dataMinTime.value : dataMinTime.value * 1000,
            max: isMilliseconds ? dataMaxTime.value : dataMaxTime.value * 1000
          }
        }
      }
    }, false);
  }
};

// Convert data format for ApexCharts with optional downsampling
const processDataForApex = (data: number[][] = [], maxPoints: number = 5000): Array<{x: number, y: number}> => {
  const isMilliseconds = props.timeUnit === 'milliseconds';
  
  if (data.length <= maxPoints) {
    return data.map(point => ({
      x: isMilliseconds ? point[0] : point[0] * 1000,
      y: point[1]
    }));
  }
  
  // Downsample using every nth point
  const step = Math.ceil(data.length / maxPoints);
  const downsampled = [];
  
  for (let i = 0; i < data.length; i += step) {
    downsampled.push({
      x: isMilliseconds ? data[i][0] : data[i][0] * 1000,
      y: data[i][1]
    });
  }
  
  return downsampled;
};

// Filter data for visible range with downsampling
const getVisibleData = (data: number[][] = []): Array<{x: number, y: number}> => {
  const filtered = data.filter(point => point[0] >= visibleStartTime.value && point[0] <= visibleEndTime.value);
  const isMilliseconds = props.timeUnit === 'milliseconds';
  
  // Downsample if too many points for main chart
  if (filtered.length > 5000) {
    const step = Math.ceil(filtered.length / 5000);
    const downsampled = [];
    for (let i = 0; i < filtered.length; i += step) {
      downsampled.push({
        x: isMilliseconds ? filtered[i][0] : filtered[i][0] * 1000,
        y: filtered[i][1]
      });
    }
    return downsampled;
  }
  
  return filtered.map(point => ({
    x: isMilliseconds ? point[0] : point[0] * 1000,
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
      data: processDataForApex(props.primaryData, 1500), // Increased from 750 to 1500 for better resolution
      color: primaryColor
    });
  }
  
  if (props.secondaryData && props.secondaryData.length > 0) {
    series.push({
      name: props.secondaryTitle || 'Secondary',
      data: processDataForApex(props.secondaryData, 1500), // Increased from 750 to 1500 for better resolution
      color: secondaryColor
    });
  }
  return series;
});

// Main chart options
const mainChartOptions = computed(() => ({
  chart: {
    id: 'main-chart',
    type: props.showPoints ? 'scatter' : 'area',
    height: 300,
    stacked: props.stacked || false,
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
  fill: props.showPoints ? {
    type: 'solid',
    opacity: 0.8
  } : {
    type: 'gradient',
    gradient: {
      opacityFrom: 0.4,
      opacityTo: 0.1
    }
  },
  markers: props.showPoints ? {
    size: 6,
    strokeWidth: 2,
    strokeColors: '#fff',
    hover: {
      size: 8
    }
  } : {
    size: 0
  },
  xaxis: {
    type: 'datetime',
    min: props.timeUnit === 'milliseconds' ? visibleStartTime.value : visibleStartTime.value * 1000,
    max: props.timeUnit === 'milliseconds' ? visibleEndTime.value : visibleEndTime.value * 1000,
    labels: {
      formatter: function(value: number) {
        return formatTime(props.timeUnit === 'milliseconds' ? value : value / 1000);
      }
    }
  },
  yaxis: props.independentSecondaryAxis && props.secondaryData ? [
    {
      title: {
        text: props.primaryTitle || 'Primary'
      },
      min: 0,
      max: primaryMaxValue.value || undefined,
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
      max: secondaryMaxValue.value || undefined,
      labels: {
        formatter: function(value: number) {
          return formatValue(value, props.secondaryAxisType);
        }
      }
    }
  ] : {
    min: 0,
    max: primaryMaxValue.value || undefined,
    labels: {
      formatter: function(value: number) {
        return formatValue(value, props.primaryAxisType);
      }
    }
  },
  tooltip: {
    x: {
      formatter: function(value: number) {
        return formatTime(props.timeUnit === 'milliseconds' ? value : value / 1000);
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
    type: 'area', // Always use area for brush chart to ensure proper selection visualization
    height: 100,
    stacked: props.stacked || false,
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
      type: 'x',
      fill: {
        color: '#2E93fA',
        opacity: 0.15
      },
      stroke: {
        width: 2,
        color: '#2E93fA',
        opacity: 0.6,
        dashArray: 4
      },
      xaxis: {
        min: props.timeUnit === 'milliseconds' ? visibleStartTime.value : visibleStartTime.value * 1000,
        max: props.timeUnit === 'milliseconds' ? visibleEndTime.value : visibleEndTime.value * 1000
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
          const isMilliseconds = props.timeUnit === 'milliseconds';
          const selectionStart = isMilliseconds ? xaxis.min : xaxis.min / 1000;
          const selectionEnd = isMilliseconds ? xaxis.max : xaxis.max / 1000;
          
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
    width: 1.5
  },
  fill: {
    type: 'gradient',
    gradient: {
      opacityFrom: 0.4,
      opacityTo: 0.1
    }
  },
  markers: {
    size: 0
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
      max: primaryMaxValue.value || undefined,
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
      max: secondaryMaxValue.value || undefined,
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
    max: primaryMaxValue.value || undefined,
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
  // Recalculate max values when secondary data changes
  calculateMaxYValues();
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
  height: 106px;
  padding: 2px;
  position: relative;
  margin-top: 20px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.brush-chart-container :deep(.apexcharts-canvas) {
  width: 100% !important;
  position: absolute !important;
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

.reset-zoom-btn {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  color: #666;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
}

.reset-zoom-btn:hover {
  background: #e9ecef;
  border-color: #adb5bd;
  color: #495057;
}

.reset-zoom-btn:active {
  background: #dee2e6;
  transform: translateY(1px);
}

.reset-zoom-btn-corner {
  position: absolute;
  top: 5px;
  right: 5px;
  z-index: 10;
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

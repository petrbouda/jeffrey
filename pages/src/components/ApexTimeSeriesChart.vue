<template>
  <LoadingIndicator v-if="isLoading" text="Generating Timeseries..."/>
  <div class="apex-time-series-chart" ref="chartContainer" v-show="!isLoading && effectivePrimaryData && effectivePrimaryData.length > 0">
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
        <!-- Select all button in top right corner of brush chart -->
        <button class="reset-zoom-btn reset-zoom-btn-corner" @click="onSelectEntireRange" title="Select entire range">
          <i class="bi bi-arrows-angle-expand"></i>
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
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/flamegraphs/model/TimeRange';
import LoadingIndicator from '@/components/LoadingIndicator.vue';

// Define props
const props = defineProps<{
  primaryData?: number[][];
  secondaryData?: number[][];
  searchData?: number[][];
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
  zoomEnabled?: boolean; // Whether to emit time range updates on brush selection
  graphUpdater?: GraphUpdater; // Optional: When provided, component manages data internally
}>();

// Define emits
const emit = defineEmits<{
  'update:timeRange': [payload: { start: number, end: number, isZoomed: boolean }]
}>();

// Search highlight color (purple)
const searchHighlightColor = '#8E44AD';

// Internal data refs (used when graphUpdater is provided)
const internalPrimaryData = ref<number[][]>([]);
const internalSecondaryData = ref<number[][] | undefined>(undefined);
const internalSearchData = ref<number[][] | undefined>(undefined);
const isLoading = ref(false);

// Extract primary data from TimeseriesData (first series)
const extractPrimaryTimeseriesData = (data: TimeseriesData): number[][] => {
  if (data.series && data.series.length > 0) {
    return data.series[0].data;
  }
  return [];
};

// Extract secondary data from TimeseriesData (second series, for differential mode)
const extractSecondaryTimeseriesData = (data: TimeseriesData): number[][] | undefined => {
  if (data.series && data.series.length > 1) {
    return data.series[1].data;
  }
  return undefined;
};

// Computed to use either props data or internal data (when graphUpdater is used)
const effectivePrimaryData = computed(() => props.graphUpdater ? internalPrimaryData.value : props.primaryData);
const effectiveSecondaryData = computed(() => props.graphUpdater ? internalSecondaryData.value : props.secondaryData);
const effectiveSearchData = computed(() => props.graphUpdater ? internalSearchData.value : props.searchData);

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
let isUpdatingSelection = false; // Flag to prevent re-entrant selection events from updateOptions
let lastProcessedSelection = { min: 0, max: 0 }; // Track last processed selection to avoid duplicates

// Colors
const primaryColor = props.primaryColor || '#2E93fA';
const secondaryColor = props.secondaryColor || '#E53935';

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
  const primaryData = effectivePrimaryData.value;
  const secondaryData = effectiveSecondaryData.value;

  if (props.stacked && primaryData && secondaryData) {
    // For stacked charts, calculate the maximum sum at any point
    let maxSum = 0;
    const minLength = Math.min(primaryData.length, secondaryData.length);

    for (let i = 0; i < minLength; i++) {
      const primaryValue = primaryData[i][1] || 0;
      const secondaryValue = secondaryData[i][1] || 0;
      const sum = primaryValue + secondaryValue;
      if (sum > maxSum) maxSum = sum;
    }

    // Add 10% padding
    primaryMaxValue.value = maxSum > 0 ? maxSum * 1.1 : 0;
    secondaryMaxValue.value = primaryMaxValue.value; // Same scale for stacked
  } else {
    // Calculate axis max values with padding applied in helper
    primaryMaxValue.value = findMaxValueInSeries(primaryData);
    secondaryMaxValue.value = findMaxValueInSeries(secondaryData);
  }
};

// Calculate min/max time values
const calculateMinMaxTimeValues = (): void => {
  const primaryData = effectivePrimaryData.value;
  const secondaryData = effectiveSecondaryData.value;

  if (!Array.isArray(primaryData) || primaryData.length === 0) {
    dataMinTime.value = 0;
    dataMaxTime.value = 0;
    return;
  }

  let min = primaryData[0][0];
  let max = primaryData[0][0];

  for (let i = 0; i < primaryData.length; i++) {
    const point = primaryData[i];
    if (point[0] < min) min = point[0];
    if (point[0] > max) max = point[0];
  }

  // Include secondary data in time range if available
  if (secondaryData && secondaryData.length > 0) {
    for (let i = 0; i < secondaryData.length; i++) {
      const point = secondaryData[i];
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

// Reset just the brush visual (used by callbacks to avoid duplicate resetZoom calls)
const resetBrushSelection = async (): Promise<void> => {
  visibleStartTime.value = dataMinTime.value;
  visibleEndTime.value = dataMaxTime.value;

  await nextTick();

  if (brushChart.value?.updateOptions) {
    isUpdatingSelection = true;
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
    setTimeout(() => { isUpdatingSelection = false; }, 100);
  }
};

// Handler for the "Select entire range" button - resets brush AND flamegraph
const onSelectEntireRange = async (): Promise<void> => {
  await resetBrushSelection();

  // Reset flamegraph zoom when graphUpdater is provided
  if (props.graphUpdater) {
    props.graphUpdater.resetZoom();
  }

  // Emit event for parent components
  if (props.zoomEnabled) {
    emit('update:timeRange', {
      start: dataMinTime.value,
      end: dataMaxTime.value,
      isZoomed: false
    });
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
  const primaryData = effectivePrimaryData.value;
  const secondaryData = effectiveSecondaryData.value;
  const searchData = effectiveSearchData.value;

  if (primaryData && primaryData.length > 0) {
    series.push({
      name: props.primaryTitle || 'Primary',
      data: getVisibleData(primaryData),
      color: primaryColor
    });
  }

  if (secondaryData && secondaryData.length > 0) {
    series.push({
      name: props.secondaryTitle || 'Secondary',
      data: getVisibleData(secondaryData),
      color: secondaryColor
    });
  }

  // Add search highlight data if provided
  if (searchData && searchData.length > 0) {
    series.push({
      name: 'Search Results',
      data: getVisibleData(searchData),
      color: searchHighlightColor
    });
  }

  return series;
});

// Brush chart series (full data)
const brushChartSeries = computed(() => {
  const series = [];
  const primaryData = effectivePrimaryData.value;
  const secondaryData = effectiveSecondaryData.value;
  const searchData = effectiveSearchData.value;

  if (primaryData && primaryData.length > 0) {
    series.push({
      name: props.primaryTitle || 'Primary',
      data: processDataForApex(primaryData, 1500),
      color: primaryColor
    });
  }

  if (secondaryData && secondaryData.length > 0) {
    series.push({
      name: props.secondaryTitle || 'Secondary',
      data: processDataForApex(secondaryData, 1500),
      color: secondaryColor
    });
  }

  // Add search highlight data if provided
  if (searchData && searchData.length > 0) {
    series.push({
      name: 'Search Results',
      data: processDataForApex(searchData, 1500),
      color: searchHighlightColor
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
        // Skip selection events during programmatic updates
        if (isUpdatingSelection) {
          return;
        }

        // Skip if selection hasn't meaningfully changed (within 1ms tolerance)
        if (Math.abs(xaxis.min - lastProcessedSelection.min) < 1 &&
            Math.abs(xaxis.max - lastProcessedSelection.max) < 1) {
          return;
        }

        // Clear existing timeout to prevent multiple updates
        if (selectionTimeout) {
          clearTimeout(selectionTimeout);
        }

        // Debounce the update to prevent twitching and loops
        selectionTimeout = setTimeout(() => {
          const isMilliseconds = props.timeUnit === 'milliseconds';
          const selectionStart = isMilliseconds ? xaxis.min : xaxis.min / 1000;
          const selectionEnd = isMilliseconds ? xaxis.max : xaxis.max / 1000;

          // Clamp selection to actual data bounds
          const clampedStart = Math.max(selectionStart, dataMinTime.value);
          const clampedEnd = Math.min(selectionEnd, dataMaxTime.value);

          // Check if clamping was needed
          const wasClamped = selectionStart !== clampedStart || selectionEnd !== clampedEnd;

          visibleStartTime.value = clampedStart;
          visibleEndTime.value = clampedEnd;
          selectionTimeout = null;

          // Track this selection as processed
          lastProcessedSelection = { min: xaxis.min, max: xaxis.max };

          // Update brush chart visual selection if clamping occurred
          if (wasClamped && brushChart.value?.updateOptions) {
            isUpdatingSelection = true;
            brushChart.value.updateOptions({
              chart: {
                selection: {
                  xaxis: {
                    min: isMilliseconds ? clampedStart : clampedStart * 1000,
                    max: isMilliseconds ? clampedEnd : clampedEnd * 1000
                  }
                }
              }
            }, false);
            // Reset flag after a short delay to allow the event to be processed
            setTimeout(() => { isUpdatingSelection = false; }, 100);
          }

          // Handle zoom - either via graphUpdater or emit
          if (props.zoomEnabled) {
            const isZoomed = Math.abs(clampedEnd - clampedStart) < Math.abs(dataMaxTime.value - dataMinTime.value) * 0.99;

            // If graphUpdater is provided, call updateWithZoom directly
            if (props.graphUpdater && isZoomed) {
              props.graphUpdater.updateWithZoom(new TimeRange(
                  Math.floor(clampedStart),
                  Math.ceil(clampedEnd),
                  true
              ));
            }

            // Also emit for any parent component listeners
            emit('update:timeRange', {
              start: clampedStart,
              end: clampedEnd,
              isZoomed
            });
          }
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

// Watch for data changes - watch effective data (handles both prop-driven and graphUpdater-driven modes)
watch(effectivePrimaryData, (newData) => {
  if (newData && newData.length > 0) {
    calculateMinMaxTimeValues();
    // Set lastProcessedSelection to match the current visible range to prevent duplicate loads
    const isMilliseconds = props.timeUnit === 'milliseconds';
    lastProcessedSelection = {
      min: isMilliseconds ? visibleStartTime.value : visibleStartTime.value * 1000,
      max: isMilliseconds ? visibleEndTime.value : visibleEndTime.value * 1000
    };
  }
}, { deep: true, immediate: true });

watch(effectiveSecondaryData, () => {
  // Recalculate max values when secondary data changes
  calculateMaxYValues();
}, { deep: true, immediate: true });

// Initialize on mount
onMounted(async () => {
  // If graphUpdater is provided, register callbacks for automatic data management
  if (props.graphUpdater) {
    // Set initial visible minutes so flamegraph loads with the correct initial zoom
    if (props.visibleMinutes) {
      props.graphUpdater.setInitialVisibleMinutes(props.visibleMinutes);
    }

    props.graphUpdater.registerTimeseriesCallbacks(
        () => isLoading.value = true,
        () => isLoading.value = false,
        (data: TimeseriesData) => {
          internalPrimaryData.value = extractPrimaryTimeseriesData(data);
          internalSecondaryData.value = extractSecondaryTimeseriesData(data);
          internalSearchData.value = undefined;
        },
        (data: TimeseriesData) => {
          internalSearchData.value = extractPrimaryTimeseriesData(data);
        },
        () => {
          internalSearchData.value = undefined;
        },
        () => {},
        () => {
          resetBrushSelection();
        }
    );

    // Register control callbacks for SearchBarComponent
    props.graphUpdater.registerTimeseriesControlCallbacks(
        () => {
          resetBrushSelection();
        }
    );
  }

  calculateMinMaxTimeValues();

  // Force chart refresh after DOM is updated to ensure proper positioning
  await nextTick();
  if (brushChart.value?.updateOptions) {
    brushChart.value.updateOptions(brushChartOptions.value, false);
  }
});

// Expose methods for parent component access
defineExpose({
  resetBrush: resetBrushSelection
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
  margin-top: 4px;
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
  margin-top: 5px;
  margin-bottom: 10px;
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

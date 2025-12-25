<template>
  <LoadingIndicator v-if="isLoading" text="Generating Timeseries..." />
  <div
    class="apex-time-series-chart"
    ref="chartContainer"
    v-show="!isLoading && effectivePrimaryData && effectivePrimaryData.length > 0"
  >
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
        <button
          class="reset-zoom-btn reset-zoom-btn-corner"
          @click="onSelectEntireRange"
          title="Select entire range"
        >
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
        <span class="time-label-start">{{ timeConverter.formatTime(dataMinTime) }}</span>
        <span class="time-label-center">
          Showing: {{ timeConverter.formatTimeRange(visibleStartTime, visibleEndTime) }}
        </span>
        <span class="time-label-end">{{ timeConverter.formatTime(dataMaxTime) }}</span>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import FormattingService from '@/services/FormattingService.ts';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/api/model/TimeRange';
import LoadingIndicator from '@/components/LoadingIndicator.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';
import TimeConverter, { type TimeUnit } from '@/services/timeseries/TimeConverter.ts';

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
  primaryAxisType?: AxisFormatType;
  secondaryAxisType?: AxisFormatType;
  stacked?: boolean;
  primaryColor?: string;
  secondaryColor?: string;
  showPoints?: boolean;
  timeUnit?: TimeUnit;
  zoomEnabled?: boolean; // Whether to emit time range updates on brush selection
  graphUpdater?: GraphUpdater; // Optional: When provided, component manages data internally
  fixedWindowMinutes?: number; // Optional: When provided, selection window is fixed to this many minutes
}>();

// Define emits
const emit = defineEmits<{
  'update:timeRange': [payload: { start: number; end: number; isZoomed: boolean }];
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
const effectivePrimaryData = computed(() =>
  props.graphUpdater ? internalPrimaryData.value : props.primaryData
);
const effectiveSecondaryData = computed(() =>
  props.graphUpdater ? internalSecondaryData.value : props.secondaryData
);
const effectiveSearchData = computed(() =>
  props.graphUpdater ? internalSearchData.value : props.searchData
);

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
let selectionTimeout:  NodeJS.Timeout | null = null;
let isUpdatingSelection = false; // Flag to prevent re-entrant selection events from updateOptions
let lastProcessedSelection = { min: 0, max: 0 }; // Track last processed selection to avoid duplicates

// Colors
const primaryColor = props.primaryColor || '#2E93fA';
const secondaryColor = props.secondaryColor || '#E53935';

// Time converter for consistent time handling
const timeConverter = computed(() => new TimeConverter(props.timeUnit));

// Calculate max Y-axis values for consistent scaling
const primaryMaxValue = ref(0);
const secondaryMaxValue = ref(0);

// Round bytes to a "nice" value (uses base-2: 1, 2, 4, 8 multipliers of KB/MB/GB)
const roundToNiceBytes = (value: number): number => {
  if (value <= 0) return 0;

  // Find the appropriate unit (1024^n)
  const units = [1, 1024, 1024 * 1024, 1024 * 1024 * 1024, 1024 * 1024 * 1024 * 1024];
  let unitIndex = 0;
  for (let i = units.length - 1; i >= 0; i--) {
    if (value >= units[i]) {
      unitIndex = i;
      break;
    }
  }

  const unit = units[unitIndex];
  const valueInUnit = value / unit;

  // Nice multipliers for bytes: 1, 2, 4, 5, 8, 10, 16, 20, 32, 50, 64, 100, 128, 200, 256, 500, 512
  const niceMultipliers = [1, 2, 4, 5, 8, 10, 16, 20, 32, 50, 64, 100, 128, 200, 256, 500, 512, 1024];

  // Find the smallest nice multiplier >= valueInUnit
  for (const mult of niceMultipliers) {
    if (mult >= valueInUnit) {
      return mult * unit;
    }
  }

  // If value is larger than 1024 of current unit, move to next unit
  return roundToNiceBytes(value);
};

// Helper function to find max value in a data series with padding
const findMaxValueInSeries = (data: number[][] | undefined, axisType?: AxisFormatType): number => {
  if (!data || data.length === 0) return 0;

  let max = 0;
  for (let i = 0; i < data.length; i++) {
    const value = data[i][1];
    if (value > max) max = value;
  }

  if (max <= 0) return 0;

  // For bytes, round to nice byte values
  if (axisType === AxisFormatType.BYTES) {
    return roundToNiceBytes(max * 1.1);
  }

  // For other types, add 10% padding and let ApexCharts handle nice scaling
  return max * 1.1;
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

    // For bytes, round to nice byte values; otherwise add 10% padding
    if (props.primaryAxisType === AxisFormatType.BYTES) {
      primaryMaxValue.value = roundToNiceBytes(maxSum * 1.1);
    } else {
      primaryMaxValue.value = maxSum > 0 ? maxSum * 1.1 : 0;
    }
    secondaryMaxValue.value = primaryMaxValue.value; // Same scale for stacked
  } else {
    // Calculate axis max values with padding applied in helper
    primaryMaxValue.value = findMaxValueInSeries(primaryData, props.primaryAxisType);
    secondaryMaxValue.value = findMaxValueInSeries(secondaryData, props.secondaryAxisType);
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
  const visibleRange = Math.min(
    timeConverter.value.getVisibleRangeFromMinutes(props.visibleMinutes || defaultVisibleMinutes),
    totalRange
  );
  visibleStartTime.value = min;
  visibleEndTime.value = min + visibleRange;

  // Calculate Y-axis max values
  calculateMaxYValues();
};

// Format value based on axis type
const formatValue = (value: number, axisType?: AxisFormatType): string => {
  switch (axisType) {
    case AxisFormatType.DURATION_IN_NANOS:
      return FormattingService.formatDuration2Units(value);
    case AxisFormatType.DURATION_IN_MILLIS:
      return FormattingService.formatDuration2Units(value * 1_000_000); // Convert ms to ns
    case AxisFormatType.BYTES:
      return FormattingService.formatBytes(value);
    default:
      return Math.round(value).toString();
  }
};

// Reset just the brush visual (used by callbacks to avoid duplicate resetZoom calls)
const resetBrushSelection = async (): Promise<void> => {
  visibleStartTime.value = dataMinTime.value;
  visibleEndTime.value = dataMaxTime.value;

  await nextTick();

  if (brushChart.value?.updateOptions) {
    isUpdatingSelection = true;
    brushChart.value.updateOptions(
      {
        chart: {
          selection: {
            xaxis: {
              min: timeConverter.value.toChartTime(dataMinTime.value),
              max: timeConverter.value.toChartTime(dataMaxTime.value)
            }
          }
        }
      },
      false
    );
    setTimeout(() => {
      isUpdatingSelection = false;
    }, 100);
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
const processDataForApex = (
  data: number[][] = [],
  maxPoints: number = 5000
): Array<{ x: number; y: number }> => {
  const tc = timeConverter.value;

  if (data.length <= maxPoints) {
    return data.map(point => ({
      x: tc.toChartTime(point[0]),
      y: point[1]
    }));
  }

  // Downsample using every nth point
  const step = Math.ceil(data.length / maxPoints);
  const downsampled = [];

  for (let i = 0; i < data.length; i += step) {
    downsampled.push({
      x: tc.toChartTime(data[i][0]),
      y: data[i][1]
    });
  }

  return downsampled;
};

// Filter data for visible range with downsampling
const getVisibleData = (data: number[][] = []): Array<{ x: number; y: number }> => {
  const filtered = data.filter(
    point => point[0] >= visibleStartTime.value && point[0] <= visibleEndTime.value
  );
  const tc = timeConverter.value;

  // Downsample if too many points for main chart
  if (filtered.length > 5000) {
    const step = Math.ceil(filtered.length / 5000);
    const downsampled = [];
    for (let i = 0; i < filtered.length; i += step) {
      downsampled.push({
        x: tc.toChartTime(filtered[i][0]),
        y: filtered[i][1]
      });
    }
    return downsampled;
  }

  return filtered.map(point => ({
    x: tc.toChartTime(point[0]),
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
  fill: props.showPoints
    ? {
        type: 'solid',
        opacity: 0.8
      }
    : {
        type: 'gradient',
        gradient: {
          opacityFrom: 0.4,
          opacityTo: 0.1
        }
      },
  markers: props.showPoints
    ? {
        size: 6,
        strokeWidth: 2,
        strokeColors: '#fff',
        hover: {
          size: 8
        }
      }
    : {
        size: 0
      },
  xaxis: {
    type: 'datetime',
    min: timeConverter.value.toChartTime(visibleStartTime.value),
    max: timeConverter.value.toChartTime(visibleEndTime.value),
    labels: {
      formatter: function (value: number) {
        return timeConverter.value.formatTime(timeConverter.value.fromChartTime(value));
      }
    }
  },
  yaxis:
    props.independentSecondaryAxis && props.secondaryData
      ? [
          {
            title: {
              text: props.primaryTitle || 'Primary'
            },
            min: 0,
            max: props.primaryAxisType === AxisFormatType.BYTES ? primaryMaxValue.value : undefined,
            forceNiceScale: true,
            tickAmount: 5,
            labels: {
              formatter: function (value: number) {
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
            max: props.secondaryAxisType === AxisFormatType.BYTES ? secondaryMaxValue.value : undefined,
            forceNiceScale: true,
            tickAmount: 5,
            labels: {
              formatter: function (value: number) {
                return formatValue(value, props.secondaryAxisType);
              }
            }
          }
        ]
      : {
          min: 0,
          max: props.primaryAxisType === AxisFormatType.BYTES ? Math.max(primaryMaxValue.value, secondaryMaxValue.value) : undefined,
          forceNiceScale: true,
          tickAmount: 5,
          labels: {
            formatter: function (value: number) {
              return formatValue(value, props.primaryAxisType);
            }
          }
        },
  tooltip: {
    x: {
      formatter: function (value: number) {
        return timeConverter.value.formatTime(timeConverter.value.fromChartTime(value));
      }
    },
    y: {
      formatter: function (value: number, { seriesIndex }: { seriesIndex: number }) {
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
        min: timeConverter.value.toChartTime(visibleStartTime.value),
        max: timeConverter.value.toChartTime(visibleEndTime.value)
      }
    },
    events: {
      selection: function (_: any, { xaxis }: { xaxis: { min: number; max: number } }) {
        // Skip selection events during programmatic updates
        if (isUpdatingSelection) {
          return;
        }

        // Skip if selection hasn't meaningfully changed (within 1ms tolerance)
        if (
          Math.abs(xaxis.min - lastProcessedSelection.min) < 1 &&
          Math.abs(xaxis.max - lastProcessedSelection.max) < 1
        ) {
          return;
        }

        // Clear existing timeout to prevent multiple updates
        if (selectionTimeout) {
          clearTimeout(selectionTimeout);
        }

        // Debounce the update to prevent twitching and loops
        selectionTimeout = setTimeout(() => {
          const tc = timeConverter.value;
          let selectionStart = tc.fromChartTime(xaxis.min);
          let selectionEnd = tc.fromChartTime(xaxis.max);

          // If fixed window is specified, enforce it as maximum
          if (props.fixedWindowMinutes) {
            const maxRangeSeconds = props.fixedWindowMinutes * 60;
            const currentRange = selectionEnd - selectionStart;

            // Only adjust if selection exceeds maximum
            if (currentRange > maxRangeSeconds) {
              selectionEnd = selectionStart + maxRangeSeconds;
              if (selectionEnd > dataMaxTime.value) {
                selectionEnd = dataMaxTime.value;
                selectionStart = Math.max(dataMinTime.value, dataMaxTime.value - maxRangeSeconds);
              }
            }
          }

          // Store original selection to check if adjustment occurred
          const originalStart = tc.fromChartTime(xaxis.min);
          const originalEnd = tc.fromChartTime(xaxis.max);

          // Clamp selection to actual data bounds
          const clampedStart = Math.max(selectionStart, dataMinTime.value);
          const clampedEnd = Math.min(selectionEnd, dataMaxTime.value);

          // Check if any adjustment was made (clamping or max window enforcement)
          const wasAdjusted =
            Math.abs(originalStart - clampedStart) > 0.1 ||
            Math.abs(originalEnd - clampedEnd) > 0.1;

          visibleStartTime.value = clampedStart;
          visibleEndTime.value = clampedEnd;
          selectionTimeout = null;

          // Track this selection as processed
          lastProcessedSelection = { min: xaxis.min, max: xaxis.max };

          // Update brush chart visual selection if adjustment occurred
          if (wasAdjusted && brushChart.value?.updateOptions) {
            isUpdatingSelection = true;
            brushChart.value.updateOptions(
              {
                chart: {
                  selection: {
                    xaxis: {
                      min: tc.toChartTime(clampedStart),
                      max: tc.toChartTime(clampedEnd)
                    }
                  }
                }
              },
              false
            );
            // Reset flag after a short delay to allow the event to be processed
            setTimeout(() => {
              isUpdatingSelection = false;
            }, 100);
          }

          // Update main chart's xaxis to reflect the selection zoom
          if (mainChart.value?.updateOptions) {
            mainChart.value.updateOptions({
              xaxis: {
                min: tc.toChartTime(clampedStart),
                max: tc.toChartTime(clampedEnd)
              }
            }, false, false);
          }

          // Handle zoom - either via graphUpdater or emit
          if (props.zoomEnabled) {
            const isZoomed =
              Math.abs(clampedEnd - clampedStart) <
              Math.abs(dataMaxTime.value - dataMinTime.value) * 0.99;

            // If graphUpdater is provided, call updateWithZoom directly
            // Convert to milliseconds for backend API (which expects milliseconds)
            // Use absoluteTime=false because timeseries data is relative to recording start
            if (props.graphUpdater && isZoomed) {
              const startMs = Math.floor(tc.toChartTime(clampedStart));
              const endMs = Math.ceil(tc.toChartTime(clampedEnd));
              props.graphUpdater.updateWithZoom(
                new TimeRange(startMs, endMs, false)
              );
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
  yaxis:
    props.independentSecondaryAxis && props.secondaryData
      ? [
          {
            min: 0,
            max: props.primaryAxisType === AxisFormatType.BYTES ? primaryMaxValue.value : undefined,
            forceNiceScale: true,
            tickAmount: 5,
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
            max: props.secondaryAxisType === AxisFormatType.BYTES ? secondaryMaxValue.value : undefined,
            forceNiceScale: true,
            tickAmount: 5,
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
        ]
      : {
          min: 0,
          max: props.primaryAxisType === AxisFormatType.BYTES ? Math.max(primaryMaxValue.value, secondaryMaxValue.value) : undefined,
          forceNiceScale: true,
          tickAmount: 5,
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

// Watch for data changes - use shallow watch on data reference/length to avoid expensive deep comparison
// The data arrays are replaced entirely when updated, so watching the reference is sufficient
watch(
  () => effectivePrimaryData.value,
  newData => {
    if (newData && newData.length > 0) {
      calculateMinMaxTimeValues();
      // Set lastProcessedSelection to match the current visible range to prevent duplicate loads
      lastProcessedSelection = {
        min: timeConverter.value.toChartTime(visibleStartTime.value),
        max: timeConverter.value.toChartTime(visibleEndTime.value)
      };
    }
  },
  { immediate: true }
);

watch(
  () => effectiveSecondaryData.value,
  () => {
    // Recalculate max values when secondary data changes
    calculateMaxYValues();
  },
  { immediate: true }
);

// Initialize on mount
onMounted(async () => {
  // If graphUpdater is provided, register callbacks for automatic data management
  if (props.graphUpdater) {
    // Set initial visible minutes so flamegraph loads with the correct initial zoom
    if (props.visibleMinutes) {
      props.graphUpdater.setInitialVisibleMinutes(props.visibleMinutes);
    }

    props.graphUpdater.registerTimeseriesCallbacks(
      () => (isLoading.value = true),
      () => (isLoading.value = false),
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
    props.graphUpdater.registerTimeseriesControlCallbacks(() => {
      resetBrushSelection();
    });
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

<template>
  <div class="time-series-chart" ref="chartContainer">
    <div class="chart-content">
      <!-- Main chart showing selected range -->
      <div class="main-chart-container"
           @mousemove="handleChartMouseMove"
           @mouseleave="handleChartMouseLeave">
        <div ref="mainChartContainer" :style="{ width: canvasWidth + 'px', height: '300px' }"></div>
        <div v-if="pointerVisible" class="chart-line-indicator" :style="{ left: `${pointerX}px` }">
          <div class="vertical-line"></div>
          <div class="timestamp-display" :class="{ 'timestamp-right': isValueRight }">{{ hoveredTimeFormatted }}</div>
        </div>
      </div>

      <!-- Brush/navigator chart for time range selection -->
      <div class="brush-chart-container">
        <div ref="brushChartContainer" :style="{ width: canvasWidth + 'px', height: '80px' }"></div>
        <div
            class="brush-selector"
            :style="{
            left: `${brushStartPercent}%`, 
            width: `${brushWidthPercent}%` 
          }"
            @mousedown="startBrushDrag"
        >
          <div class="brush-handle left" @mousedown.stop="startHandleDrag('start')"></div>
          <div class="brush-handle right" @mousedown.stop="startHandleDrag('end')"></div>
        </div>
      </div>

      <!-- Time range values and info below the graph -->
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
import {computed, onMounted, onUnmounted, ref, watch} from 'vue';
import Konva from 'konva';
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

// Define default values for optional props
const defaultVisibleMinutes = 15; // Default visible time range in minutes

// Container refs
const chartContainer = ref<HTMLDivElement | null>(null);
const mainChartContainer = ref<HTMLDivElement | null>(null);
const brushChartContainer = ref<HTMLDivElement | null>(null);
const canvasWidth = ref(800);

// Extracted min/max time values for the data
const dataMinTime = ref(0);
const dataMaxTime = ref(0);

// Calculate min/max time values using a for loop
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
  
  dataMinTime.value = min;
  dataMaxTime.value = max;
};

// Konva stages and layers
let mainStage: Konva.Stage | null = null;
let brushStage: Konva.Stage | null = null;
let mainLayer: Konva.Layer | null = null;
let brushLayer: Konva.Layer | null = null;

// State for brush selection
const brushStartPercent = ref(0);
const brushWidthPercent = ref(0); // Initialize with zero width, will be calculated properly
const draggingBrush = ref(false);
const draggingHandle = ref<'start' | 'end' | null>(null);
const dragStartX = ref(0);
const dragStartLeft = ref(0);
const dragStartWidth = ref(0);

// State for chart pointer
const pointerVisible = ref(false);
const pointerX = ref(0);
const pointerValue = ref<string | number>(0);
const hoveredTime = ref<number>(0);
const hoveredTimeFormatted = computed(() => formatDetailedTime(hoveredTime.value));
const isValueRight = ref(false); // Controls whether value is displayed to the right of the line
let tooltipLayer: Konva.Layer | null = null;

// Primary series tooltip elements
let primaryTooltipText: Konva.Text | null = null;
let primaryTooltipRect: Konva.Rect | null = null;

// Secondary series tooltip elements
let secondaryTooltipText: Konva.Text | null = null;
let secondaryTooltipRect: Konva.Rect | null = null;

// Colors for each series
const primaryColor = '#2e49fa';
const secondaryColor = '#8E44AD'; // Purple color for secondary series

// Calculate the visible time range
const visibleMinutes = computed(() => props.visibleMinutes || defaultVisibleMinutes);
const totalSeconds = computed(() => {
  if (!Array.isArray(props.primaryData) || props.primaryData.length === 0) return 0;
  return dataMaxTime.value - dataMinTime.value;
});

// Calculate visible start and end times
const visibleStartTime = computed(() => {
  if (!Array.isArray(props.primaryData) || props.primaryData.length === 0) return 0;
  const totalRange = totalSeconds.value;
  return dataMinTime.value + (brushStartPercent.value / 100) * totalRange;
});

const visibleEndTime = computed(() => {
  if (!Array.isArray(props.primaryData) || props.primaryData.length === 0) return 0;
  const totalRange = totalSeconds.value;
  const endPercent = brushStartPercent.value + brushWidthPercent.value;
  return dataMinTime.value + (endPercent / 100) * totalRange;
});

// Calculate global max value for consistent y-axis across the entire chart
const globalMaxValue = computed(() => {
  // If secondary axis is independent, only use primary data for scaling
  let allValues: number[] = [];

  if (props.primaryData && props.primaryData.length > 0) {
    allValues = [...allValues, ...props.primaryData.map(point => point[1])];
  }

  // Only include secondary data if not using independent axis
  if (!props.independentSecondaryAxis && props.secondaryData && props.secondaryData.length > 0) {
    allValues = [...allValues, ...props.secondaryData.map(point => point[1])];
  }

  // Calculate max with 20% padding
  const rawMax = allValues.length > 0 ? Math.max(...allValues) : 100;
  return Math.ceil(rawMax * 1.2);
});

// Calculate max value for secondary axis when independent
const secondaryMaxValue = computed(() => {
  if (!props.independentSecondaryAxis || !props.secondaryData || props.secondaryData.length === 0) {
    return globalMaxValue.value;
  }

  const secondaryValues = props.secondaryData.map(point => point[1]);
  const rawMax = Math.max(...secondaryValues);
  return Math.ceil(rawMax * 1.2);
});

// Get visible data for primary series
const visibleData = computed(() => {
  if (!props.primaryData) return [];
  return props.primaryData.filter(point =>
      point[0] >= visibleStartTime.value &&
      point[0] <= visibleEndTime.value
  );
});

// Get visible data for secondary series (if provided)
const visibleSecondaryData = computed(() => {
  if (!props.secondaryData) return [];
  return props.secondaryData.filter(point =>
      point[0] >= visibleStartTime.value &&
      point[0] <= visibleEndTime.value
  );
});

// Format time for display
const formatTime = (seconds: number): string => {
  const date = new Date(seconds * 1000);
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const secs = String(date.getUTCSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${secs}`;
};

// Format time with more detail for hover display
const formatDetailedTime = (seconds: number): string => {

  const date = new Date(seconds * 1000);
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const secs = String(date.getUTCSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${secs}`;
};

// Format time range for display
const formatTimeRange = (startSeconds: number, endSeconds: number): string => {
  return `${formatTime(startSeconds)} - ${formatTime(endSeconds)}`;
};

// Format value based on axis type
const formatValue = (value: number, axisType?: 'number' | 'duration' | 'bytes', forAxis: boolean = false): string => {
  switch (axisType) {
    case 'duration':
      const durationStr = FormattingService.formatDuration2Units(value);
      // If duration contains two units (has a space), split them into two lines only for axis labels
      if (forAxis && durationStr.includes(' ')) {
        return durationStr.replace(' ', '\n');
      }
      return durationStr;
    case 'bytes':
      return FormattingService.formatBytes(value);
    default:
      return Math.round(value).toString();
  }
};

// Resize observer
let resizeObserver: ResizeObserver | null = null;

// Initialize and set up resize handling
onMounted(() => {
  calculateMinMaxTimeValues()

  // Set the brush width immediately for 15 minutes
  initializeBrushSelection();

  // Update canvas size on mount
  updateCanvasSize();

  // Initialize Konva stages
  initializeKonvaStages();

  // Set up resize observer for responsive canvas
  if (chartContainer.value) {
    resizeObserver = new ResizeObserver(entries => {
      for (const entry of entries) {
        if (entry.target === chartContainer.value) {
          updateCanvasSize();
          if (props.primaryData && props.primaryData.length > 0) {
            drawMainChart();
            drawBrushChart();
          }
        }
      }
    });

    resizeObserver.observe(chartContainer.value);
  }

  // Set up window resize listener as backup
  window.addEventListener('resize', handleResize);

  // Set up global mouse event listeners for brush dragging
  window.addEventListener('mousemove', handleMouseMove);
  window.addEventListener('mouseup', handleMouseUp);

  // Ensure the canvas size is updated and redrawn when the component is mounted
  // Use a short timeout to ensure DOM is fully ready
  setTimeout(() => {
    updateCanvasSize();

    // Make sure brush selection is set correctly
    initializeBrushSelection();

    // Draw charts if data is available
    if (props.primaryData && props.primaryData.length > 0) {
      drawMainChart();
      drawBrushChart();
    }
  }, 50);
});

// Initialize Konva stages
const initializeKonvaStages = () => {
  if (!mainChartContainer.value || !brushChartContainer.value) return;

  // Create main chart stage
  mainStage = new Konva.Stage({
    container: mainChartContainer.value,
    width: canvasWidth.value,
    height: 300
  });

  // Create brush chart stage
  brushStage = new Konva.Stage({
    container: brushChartContainer.value,
    width: canvasWidth.value,
    height: 80
  });

  // Create layers
  mainLayer = new Konva.Layer();
  brushLayer = new Konva.Layer();
  tooltipLayer = new Konva.Layer();

  // Create tooltip elements for primary series
  primaryTooltipRect = new Konva.Rect({
    fill: primaryColor,
    cornerRadius: 3,
    visible: false,
    shadowColor: 'black',
    shadowBlur: 3,
    shadowOffset: {x: 1, y: 1},
    shadowOpacity: 0.3
  });

  primaryTooltipText = new Konva.Text({
    text: '',
    fontFamily: 'Arial',
    fontSize: 12,
    fontStyle: 'bold',
    padding: 2,
    fill: 'white',
    visible: false,
    align: 'center'
  });

  // Create tooltip elements for secondary series
  secondaryTooltipRect = new Konva.Rect({
    fill: secondaryColor,
    cornerRadius: 3,
    visible: false,
    shadowColor: 'black',
    shadowBlur: 3,
    shadowOffset: {x: 1, y: 1},
    shadowOpacity: 0.3
  });

  secondaryTooltipText = new Konva.Text({
    text: '',
    fontFamily: 'Arial',
    fontSize: 12,
    fontStyle: 'bold',
    padding: 2,
    fill: 'white',
    visible: false,
    align: 'center'
  });

  // Add tooltip elements to the tooltip layer
  tooltipLayer.add(primaryTooltipRect);
  tooltipLayer.add(primaryTooltipText);
  tooltipLayer.add(secondaryTooltipRect);
  tooltipLayer.add(secondaryTooltipText);

  mainStage.add(mainLayer);
  mainStage.add(tooltipLayer);
  brushStage.add(brushLayer);
};

// Initialize brush selection to show exactly 15 minutes
const initializeBrushSelection = () => {
  if (!props.primaryData || props.primaryData.length === 0) return;

  const totalMinutes = totalSeconds.value / 60;
  if (totalMinutes <= 0) return;

  // Ensure the brush width represents exactly 15 minutes
  const exactWidth = Math.min(visibleMinutes.value / totalMinutes, 1) * 100;

  // Set initial values - start at beginning, width for 15 minutes
  brushStartPercent.value = 0;
  brushWidthPercent.value = exactWidth;
};

// Clean up event listeners on unmount
onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('mousemove', handleMouseMove);
  window.removeEventListener('mouseup', handleMouseUp);

  // Destroy Konva stages
  if (mainStage) {
    mainStage.destroy();
  }

  if (brushStage) {
    brushStage.destroy();
  }

  tooltipLayer = null;
  primaryTooltipText = null;
  primaryTooltipRect = null;
  secondaryTooltipText = null;
  secondaryTooltipRect = null;
});

// Handle window resize
const handleResize = () => {
  updateCanvasSize();
  if (props.primaryData && props.primaryData.length > 0) {
    drawMainChart();
    drawBrushChart();
  }
};

// Update canvas size based on container width
const updateCanvasSize = () => {
  if (!chartContainer.value) return;

  // Get container width accounting for padding/borders
  const containerWidth = chartContainer.value.clientWidth;

  // Update canvas width with a more aggressive approach to ensure it fills the container
  const newWidth = Math.max(300, containerWidth - 20); // Subtract padding

  // Only update if the width has changed significantly (prevents unnecessary redraws)
  if (Math.abs(canvasWidth.value - newWidth) > 5) {
    canvasWidth.value = newWidth;

    // Update Konva stage sizes
    if (mainStage) {
      mainStage.width(newWidth);
    }

    if (brushStage) {
      brushStage.width(newWidth);
    }
  }
};

// Brush drag handlers
const startBrushDrag = (event: MouseEvent) => {
  if (!event) return;
  event.preventDefault();

  // Start tracking drag operation
  draggingBrush.value = true;
  dragStartX.value = event.clientX;
  dragStartLeft.value = brushStartPercent.value;
  dragStartWidth.value = brushWidthPercent.value;
};

const startHandleDrag = (handle: 'start' | 'end', event?: MouseEvent) => {
  if (!event) return;
  event.preventDefault();
  event.stopPropagation();
  draggingHandle.value = handle;
  dragStartX.value = event.clientX;
  dragStartLeft.value = brushStartPercent.value;
  dragStartWidth.value = brushWidthPercent.value;
};

const handleMouseMove = (event: MouseEvent) => {
  if (!chartContainer.value || (!draggingBrush.value && !draggingHandle.value)) return;

  const containerRect = chartContainer.value.getBoundingClientRect();
  const containerWidth = containerRect.width;
  const deltaX = event.clientX - dragStartX.value;
  const deltaPercent = (deltaX / containerWidth) * 100;

  if (draggingBrush.value) {
    // Move the entire brush
    let newLeft = dragStartLeft.value + deltaPercent;

    // Constrain to valid range
    newLeft = Math.max(0, Math.min(100 - brushWidthPercent.value, newLeft));
    brushStartPercent.value = newLeft;
  } else if (draggingHandle.value) {
    if (draggingHandle.value === 'start') {
      // Dragging left handle
      let newLeft = dragStartLeft.value + deltaPercent;

      // Constraints
      newLeft = Math.max(0, newLeft);
      let newWidth = Math.max(5, dragStartWidth.value - (newLeft - dragStartLeft.value));

      if (newLeft + newWidth <= 100) {
        brushStartPercent.value = newLeft;
        brushWidthPercent.value = newWidth;
      }
    } else {
      // Dragging right handle
      let newWidth = dragStartWidth.value + deltaPercent;

      // Constraints
      newWidth = Math.max(5, Math.min(100 - brushStartPercent.value, newWidth));

      brushWidthPercent.value = newWidth;
    }
  }

  // Redraw main chart with updated visible data
  drawMainChart();
};

const handleMouseUp = () => {
  draggingBrush.value = false;
  draggingHandle.value = null;
};

// Handle mouse movement over the main chart
const handleChartMouseMove = (event: MouseEvent) => {
  if (!mainStage || !props.primaryData || props.primaryData.length === 0 || !visibleData.value.length ||
      !primaryTooltipText || !primaryTooltipRect || !secondaryTooltipText ||
      !secondaryTooltipRect || !tooltipLayer) return;

  const containerRect = mainChartContainer.value?.getBoundingClientRect();
  if (!containerRect) return;

  const mouseX = event.clientX - containerRect.left;
  const mouseY = event.clientY - containerRect.top;

  // Chart dimensions and padding from the drawMainChart function
  const width = mainStage.width();
  const height = mainStage.height();
  const paddingLeft = 40;
  const paddingRight = 40;
  const paddingTop = 40;
  const paddingBottom = 50;

  // Chart area bounds
  const chartLeft = paddingLeft;
  const chartRight = width - paddingRight;
  const chartTop = paddingTop;
  const chartBottom = height - paddingBottom;

  // Check if mouse is within chart area
  if (mouseX >= chartLeft && mouseX <= chartRight && mouseY >= chartTop && mouseY <= chartBottom) {
    // Calculate relative X position in chart (0-1)
    const relativeX = (mouseX - chartLeft) / (chartRight - chartLeft);

    // Find the corresponding time
    const times = visibleData.value.map(point => point[0]);
    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);
    const hoverTime = minTime + relativeX * (maxTime - minTime);

    // Find the closest data point
    let closestPoint = visibleData.value[0];
    let minDistance = Math.abs(closestPoint[0] - hoverTime);

    for (const point of visibleData.value) {
      const distance = Math.abs(point[0] - hoverTime);
      if (distance < minDistance) {
        minDistance = distance;
        closestPoint = point;
      }
    }

    // Calculate the x,y position on the chart for the closest point
    const pointX = chartLeft + ((closestPoint[0] - minTime) / (maxTime - minTime)) * (chartRight - chartLeft);

    // Add minimal padding around the text
    const paddingH = 4; // Horizontal padding
    const paddingV = 4; // Vertical padding

    // Update primary tooltip
    const primaryValue = closestPoint[1];
    const primaryText = formatValue(primaryValue, props.primaryAxisType);
    primaryTooltipText.text(primaryText);

    // Ensure primary tooltip is visible
    primaryTooltipText.visible(true);
    primaryTooltipRect.visible(true);

    // Force the tooltip text to update its dimensions before measuring
    primaryTooltipText.width(undefined);
    tooltipLayer.batchDraw(); // Force an update to get accurate dimensions

    // Measure the primary text dimensions
    const primaryTextWidth = primaryTooltipText.width();
    const primaryTextHeight = primaryTooltipText.height();

    // Update primary rectangle size with proper padding
    primaryTooltipRect.width(primaryTextWidth + paddingH * 2);
    primaryTooltipRect.height(primaryTextHeight + paddingV * 2);

    let hasSecondaryValue = false;

    // Find the closest point in the secondary series if it exists
    if (props.secondaryData && visibleSecondaryData.value.length > 0) {
      // Find the closest secondary data point to the current time
      let closestSecondaryPoint = visibleSecondaryData.value[0];
      let minSecondaryDistance = Math.abs(closestSecondaryPoint[0] - closestPoint[0]);

      for (const point of visibleSecondaryData.value) {
        const distance = Math.abs(point[0] - closestPoint[0]);
        if (distance < minSecondaryDistance) {
          minSecondaryDistance = distance;
          closestSecondaryPoint = point;
        }
      }

      // Add secondary value to tooltip if the time is close enough
      // (Within 5% of the total visible time range)
      const visibleTimeRange = visibleEndTime.value - visibleStartTime.value;
      const closeThreshold = visibleTimeRange * 0.05;

      if (minSecondaryDistance <= closeThreshold) {
        hasSecondaryValue = true;

        // Update secondary tooltip with formatting based on axis type
        const secondaryText = formatValue(closestSecondaryPoint[1], props.secondaryAxisType);
        secondaryTooltipText.text(secondaryText);
        secondaryTooltipText.visible(true);
        secondaryTooltipRect.visible(true);

        // Force the secondary tooltip text to update its dimensions
        secondaryTooltipText.width(undefined);
        tooltipLayer.batchDraw();

        // Measure the secondary text dimensions
        const secondaryTextWidth = secondaryTooltipText.width();
        const secondaryTextHeight = secondaryTooltipText.height();

        // Update secondary rectangle size
        secondaryTooltipRect.width(secondaryTextWidth + paddingH * 2);
        secondaryTooltipRect.height(secondaryTextHeight + paddingV * 2);
      } else {
        // Hide secondary tooltip if no matching point
        secondaryTooltipText.visible(false);
        secondaryTooltipRect.visible(false);
      }
    } else {
      // Hide secondary tooltip if no secondary data
      secondaryTooltipText.visible(false);
      secondaryTooltipRect.visible(false);
    }

    // Position the tooltips - either side by side or just the primary
    if (hasSecondaryValue) {
      // Position them side by side, centered on the point
      const totalWidth = primaryTooltipRect.width() + secondaryTooltipRect.width() + 4; // 4px gap between tooltips
      const startX = pointX - (totalWidth / 2);

      // Position primary tooltip
      primaryTooltipRect.position({
        x: startX,
        y: paddingTop - primaryTooltipRect.height() - 8
      });

      // Position primary text - center it vertically within the rect + 1px lower
      primaryTooltipText.position({
        x: primaryTooltipRect.x() + paddingH,
        y: primaryTooltipRect.y() + (primaryTooltipRect.height() - primaryTooltipText.height()) / 2 + 1
      });

      // Position secondary tooltip to the right of primary
      secondaryTooltipRect.position({
        x: primaryTooltipRect.x() + primaryTooltipRect.width() + 4,
        y: paddingTop - secondaryTooltipRect.height() - 8
      });

      // Position secondary text - center it vertically within the rect + 1px lower
      secondaryTooltipText.position({
        x: secondaryTooltipRect.x() + paddingH,
        y: secondaryTooltipRect.y() + (secondaryTooltipRect.height() - secondaryTooltipText.height()) / 2 + 1
      });
    } else {
      // Just position the primary tooltip centered on the point
      primaryTooltipRect.position({
        x: pointX - (primaryTooltipRect.width() / 2),
        y: paddingTop - primaryTooltipRect.height() - 8
      });

      // Position the text inside the rectangle - center it vertically + 1px lower
      primaryTooltipText.position({
        x: primaryTooltipRect.x() + paddingH,
        y: primaryTooltipRect.y() + (primaryTooltipRect.height() - primaryTooltipText.height()) / 2 + 1
      });
    }

    // Draw the tooltip layer
    tooltipLayer.draw();

    // Update the line indicator position
    pointerVisible.value = true;
    pointerX.value = pointX;
    pointerValue.value = primaryValue;
    hoveredTime.value = closestPoint[0];

    // Update isValueRight to position timestamp
    // If mouse is on right half of chart, put timestamp on left side of line
    // If mouse is on left half of chart, put timestamp on right side of line
    const chartWidth = chartRight - chartLeft;
    const linePosition = pointerX.value - chartLeft;
    isValueRight.value = linePosition < (chartWidth / 2);
  } else {
    // Hide tooltips when mouse is outside chart area
    primaryTooltipText.visible(false);
    primaryTooltipRect.visible(false);
    secondaryTooltipText.visible(false);
    secondaryTooltipRect.visible(false);
    tooltipLayer.draw();

    // Hide pointer when mouse is outside chart area
    pointerVisible.value = false;
  }
};

// Hide pointer when mouse leaves chart
const handleChartMouseLeave = () => {
  pointerVisible.value = false;

  // Hide tooltips
  if (primaryTooltipText && primaryTooltipRect &&
      secondaryTooltipText && secondaryTooltipRect && tooltipLayer) {
    primaryTooltipText.visible(false);
    primaryTooltipRect.visible(false);
    secondaryTooltipText.visible(false);
    secondaryTooltipRect.visible(false);
    tooltipLayer.draw();
  }
};

// Watch for data changes
watch(() => props.primaryData, (newData) => {
  if (newData && newData.length > 0) {
    // Call the same initialization function to ensure consistent behavior
    initializeBrushSelection();

    // Redraw charts with new data
    drawMainChart();
    drawBrushChart();
  }
}, {deep: true});

// Draw the main chart showing the selected time range
const drawMainChart = () => {
  if (!mainLayer || !props.primaryData || props.primaryData.length === 0) return;

  // Clear the layer
  mainLayer.destroyChildren();

  // Set canvas dimensions
  const width = canvasWidth.value;
  const height = 300;
  const paddingLeft = 40;
  const paddingRight = 40;
  const paddingTop = 40;
  const paddingBottom = 50;

  // Chart dimensions
  const chartWidth = width - (paddingLeft + paddingRight);
  const chartHeight = height - (paddingTop + paddingBottom);

  // Draw background
  const background = new Konva.Rect({
    x: 0,
    y: 0,
    width: width,
    height: height,
    fill: '#ffffff'
  });
  mainLayer.add(background);

  // Always start y-axis from zero
  const minValue = 0; // Fixed at zero

  // Use the global max value for consistent y-axis scale
  const maxValue = globalMaxValue.value;
  const valueRange = Math.max(1, maxValue - minValue); // Prevent division by zero

  // Find time range
  const times = visibleData.value.map(point => point[0]);
  const minTime = times.length > 0 ? Math.min(...times) : 0;
  const maxTime = times.length > 0 ? Math.max(...times) : 60;
  const timeRange = Math.max(1, maxTime - minTime); // Prevent division by zero

  // Draw axes
  const yAxis = new Konva.Line({
    points: [paddingLeft, paddingTop, paddingLeft, height - paddingBottom],
    stroke: primaryColor,
    strokeWidth: 1
  });

  const xAxis = new Konva.Line({
    points: [paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom],
    stroke: '#999',
    strokeWidth: 1
  });

  mainLayer.add(yAxis);
  mainLayer.add(xAxis);

  // Draw y-axis grid lines and labels
  const yGridCount = 5;
  for (let i = 0; i <= yGridCount; i++) {
    const y = paddingTop + (chartHeight - (chartHeight * (i / yGridCount)));
    const value = minValue + (valueRange * (i / yGridCount));

    // Grid line
    const gridLine = new Konva.Line({
      points: [paddingLeft, y, width - paddingRight, y],
      stroke: '#eee',
      strokeWidth: 1
    });

    // Label - use darker shade of primary color with proper formatting
    const label = new Konva.Text({
      x: paddingLeft - 60,
      y: y - 5,
      text: formatValue(value, props.primaryAxisType, true),
      fontSize: 10,
      fontFamily: 'Arial',
      fill: primaryColor,
      align: 'right',
      width: 55,
      lineHeight: 1.1
    });

    mainLayer.add(gridLine);
    mainLayer.add(label);
  }

  // Draw x-axis labels
  const xGridCount = Math.min(Math.floor(width / 100), 10);
  for (let i = 0; i <= xGridCount; i++) {
    const x = paddingLeft + (chartWidth * (i / xGridCount));
    const time = minTime + (timeRange * (i / xGridCount));

    // Convert seconds to HH:MM:SS
    const timeStr = formatTime(time);

    // Grid line
    const gridLine = new Konva.Line({
      points: [x, paddingTop, x, height - paddingBottom],
      stroke: '#eee',
      strokeWidth: 1
    });

    // Label
    const label = new Konva.Text({
      x: x - 30,
      y: height - paddingBottom + 12,
      text: timeStr,
      fontSize: 12,
      fontFamily: 'Arial',
      fill: '#666',
      align: 'center',
      width: 60
    });

    mainLayer.add(gridLine);
    mainLayer.add(label);
  }

  // Draw the primary line chart
  if (visibleData.value.length > 1) { // Need at least 2 points to draw a line
    // Create line points array
    const linePoints: number[] = [];
    const areaPoints: number[] = [];

    visibleData.value.forEach((point) => {
      const x = paddingLeft + (chartWidth * ((point[0] - minTime) / timeRange));
      const y = height - paddingBottom - (chartHeight * ((point[1] - minValue) / valueRange));

      linePoints.push(x, y);
      areaPoints.push(x, y);
    });

    // Add bottom points for area
    if (areaPoints.length > 0) {
      const firstX = areaPoints[0];
      const lastX = areaPoints[areaPoints.length - 2];
      areaPoints.push(lastX, height - paddingBottom);
      areaPoints.push(firstX, height - paddingBottom);
    }

    // Create area fill
    const area = new Konva.Line({
      points: areaPoints,
      fill: 'rgba(46, 147, 250, 0.1)',
      closed: true,
      listening: false // Optimize performance by disabling event listening
    });

    // Create line
    const line = new Konva.Line({
      points: linePoints,
      stroke: primaryColor,
      strokeWidth: 1,
      lineCap: 'round',
      lineJoin: 'round',
      listening: false // Optimize performance by disabling event listening
    });

    // Add to layer (add area first so line appears on top)
    mainLayer.add(area);
    mainLayer.add(line);
  }

  // Draw secondary Y-axis if independent scaling is enabled
  if (props.independentSecondaryAxis && visibleSecondaryData.value.length > 0) {
    const secondaryMinValue = 0;
    const secondaryValueRange = Math.max(1, secondaryMaxValue.value - secondaryMinValue);
    
    // Draw secondary y-axis on the right
    const secondaryYAxis = new Konva.Line({
      points: [width - paddingRight, paddingTop, width - paddingRight, height - paddingBottom],
      stroke: secondaryColor,
      strokeWidth: 1
    });
    mainLayer.add(secondaryYAxis);

    // Draw secondary y-axis grid lines and labels
    for (let i = 0; i <= yGridCount; i++) {
      const y = paddingTop + (chartHeight - (chartHeight * (i / yGridCount)));
      const value = secondaryMinValue + (secondaryValueRange * (i / yGridCount));

      // Secondary axis label with proper formatting based on axis type
      const labelText = formatValue(value, props.secondaryAxisType, true);
      
      const secondaryLabel = new Konva.Text({
        x: width - paddingRight + 5,
        y: y - 8,
        text: labelText,
        fontSize: 10,
        fontFamily: 'Arial',
        fill: secondaryColor,
        align: 'left',
        width: 35,
        lineHeight: 1.1
      });

      mainLayer.add(secondaryLabel);
    }
  }

  // Draw the secondary line chart if data is available
  if (visibleSecondaryData.value.length > 1) {
    // Use independent scaling if enabled, otherwise use primary scaling
    const secondaryMinValue = 0;
    const secondaryValueRange = props.independentSecondaryAxis 
      ? Math.max(1, secondaryMaxValue.value - secondaryMinValue)
      : valueRange;

    // Create line points array for secondary series
    const secondaryLinePoints: number[] = [];
    const secondaryAreaPoints: number[] = [];

    visibleSecondaryData.value.forEach((point) => {
      const x = paddingLeft + (chartWidth * ((point[0] - minTime) / timeRange));
      const y = height - paddingBottom - (chartHeight * ((point[1] - secondaryMinValue) / secondaryValueRange));

      secondaryLinePoints.push(x, y);
      secondaryAreaPoints.push(x, y);
    });

    // Add bottom points for area
    if (secondaryAreaPoints.length > 0) {
      const firstX = secondaryAreaPoints[0];
      const lastX = secondaryAreaPoints[secondaryAreaPoints.length - 2];
      secondaryAreaPoints.push(lastX, height - paddingBottom);
      secondaryAreaPoints.push(firstX, height - paddingBottom);
    }

    // Create area fill for secondary series
    const secondaryArea = new Konva.Line({
      points: secondaryAreaPoints,
      fill: 'rgba(142, 68, 173, 0.1)', // Lighter purple with transparency
      closed: true,
      listening: false
    });

    // Create line for secondary series
    const secondaryLine = new Konva.Line({
      points: secondaryLinePoints,
      stroke: secondaryColor,
      strokeWidth: 1,
      lineCap: 'round',
      lineJoin: 'round',
      listening: false
    });

    // Add secondary series to layer
    mainLayer.add(secondaryArea);
    mainLayer.add(secondaryLine);
  }

  // Make sure to draw the layer
  mainLayer.draw();
};

// Draw the brush chart showing the entire dataset
const drawBrushChart = () => {
  if (!brushLayer || !props.primaryData || props.primaryData.length === 0) return;

  // Clear the layer
  brushLayer.destroyChildren();

  // Set canvas dimensions
  const width = canvasWidth.value;
  const height = 80;

  // Chart dimensions
  const chartWidth = width;
  const chartHeight = height;

  // Draw background
  const background = new Konva.Rect({
    x: 0,
    y: 0,
    width: width,
    height: height,
    fill: '#f8f9fa'
  });
  brushLayer.add(background);

  // Draw outline
  const outline = new Konva.Rect({
    x: 0,
    y: 0,
    width: chartWidth,
    height: chartHeight,
    stroke: '#ccc',
    strokeWidth: 1
  });
  brushLayer.add(outline);

  // Find y-axis range for the entire dataset
  const minValue = 0; // Always start from zero
  // Use the global max value for consistent y-axis scale
  const maxValue = globalMaxValue.value;
  const valueRange = maxValue - minValue;
  
  // For secondary data with independent axis
  const secondaryMinValue = 0;
  const secondaryValueRange = props.independentSecondaryAxis 
    ? Math.max(1, secondaryMaxValue.value - secondaryMinValue)
    : valueRange;

  // Find time range for the entire dataset
  let allTimes = props.primaryData.map(point => point[0]);
  if (props.secondaryData && props.secondaryData.length > 0) {
    allTimes = [...allTimes, ...props.secondaryData.map(point => point[0])];
  }
  const minTime = Math.min(...allTimes);
  const maxTime = Math.max(...allTimes);
  const timeRange = maxTime - minTime;

  // Draw primary series
  // Create line points array
  const linePoints: number[] = [];
  const areaPoints: number[] = [];

  props.primaryData.forEach((point) => {
    const x = chartWidth * ((point[0] - minTime) / timeRange);
    const y = height - (chartHeight * ((point[1] - minValue) / valueRange));

    linePoints.push(x, y);
    areaPoints.push(x, y);
  });

  // Add bottom points for area
  if (areaPoints.length > 0) {
    const firstX = areaPoints[0];
    const lastX = areaPoints[areaPoints.length - 2];
    areaPoints.push(lastX, height);
    areaPoints.push(firstX, height);
  }

  // Create line
  const line = new Konva.Line({
    points: linePoints,
    stroke: primaryColor,
    strokeWidth: 1,
    lineCap: 'round',
    lineJoin: 'round'
  });

  // Create area fill
  const area = new Konva.Line({
    points: areaPoints,
    fill: 'rgba(46, 147, 250, 0.05)',
    closed: true
  });

  brushLayer.add(area);
  brushLayer.add(line);

  // Draw secondary series if data exists
  if (props.secondaryData && props.secondaryData.length > 1) {
    // Create line points array for secondary series
    const secondaryLinePoints: number[] = [];
    const secondaryAreaPoints: number[] = [];

    props.secondaryData.forEach((point) => {
      const x = chartWidth * ((point[0] - minTime) / timeRange);
      const y = height - (chartHeight * ((point[1] - secondaryMinValue) / secondaryValueRange));

      secondaryLinePoints.push(x, y);
      secondaryAreaPoints.push(x, y);
    });

    // Add bottom points for area
    if (secondaryAreaPoints.length > 0) {
      const firstX = secondaryAreaPoints[0];
      const lastX = secondaryAreaPoints[secondaryAreaPoints.length - 2];
      secondaryAreaPoints.push(lastX, height);
      secondaryAreaPoints.push(firstX, height);
    }

    // Create secondary line
    const secondaryLine = new Konva.Line({
      points: secondaryLinePoints,
      stroke: secondaryColor,
      strokeWidth: 1,
      lineCap: 'round',
      lineJoin: 'round'
    });

    // Create secondary area fill
    const secondaryArea = new Konva.Line({
      points: secondaryAreaPoints,
      fill: 'rgba(142, 68, 173, 0.05)', // Light purple
      closed: true
    });

    brushLayer.add(secondaryArea);
    brushLayer.add(secondaryLine);
  }

  brushLayer.draw();
};
</script>

<style scoped>
.time-series-chart {
  width: 100%;
  min-height: 420px;
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
  height: 300px;
  position: relative;
  overflow: hidden;
}

.brush-chart-container {
  width: 100%;
  height: 80px;
  position: relative;
  margin-top: 10px;
  overflow: hidden;
}

.brush-selector {
  position: absolute;
  top: 0;
  height: 100%;
  background-color: rgba(0, 123, 255, 0.1);
  border: 1px solid rgba(0, 123, 255, 0.5);
  cursor: move;
  box-sizing: border-box;
  z-index: 10;
}

.brush-handle {
  position: absolute;
  top: 0;
  width: 0;
  height: 100%;
  background-color: rgba(0, 123, 255, 0.5);
  cursor: col-resize;
}

.brush-handle.left {
  left: 0;
}

.brush-handle.right {
  right: 0;
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

.chart-line-indicator {
  position: absolute;
  pointer-events: none;
  z-index: 20;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.vertical-line {
  width: 1px;
  background-color: #2E93fA;
  position: absolute;
  top: 40px; /* Match paddingTop from chart */
  height: calc(100% - 90px); /* Adjust height to stay within chart area (paddingTop + paddingBottom) */
}

.timestamp-display {
  position: absolute;
  /* Position at the top of the line */
  top: 45px;
  /* Default position to the left of the line */
  right: 5px;
  transform: translateX(0);
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  border-radius: 2px;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: normal;
  white-space: nowrap;
  text-align: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
  z-index: 30;
}

/* Position to the right of the line when on left side of graph */
.timestamp-display.timestamp-right {
  right: auto;
  left: 5px;
}

@media (max-width: 768px) {
  .time-series-chart {
    padding: 0 5px;
  }

  .main-chart-container {
    height: 250px;
  }

  .brush-chart-container {
    height: 60px;
  }
}
</style>

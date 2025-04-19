<template>
  <div class="time-series-chart" ref="chartContainer">
    <h4 v-if="title" class="chart-title">{{ title }}</h4>
    
    <div v-if="loading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-2">Loading chart data...</p>
    </div>
    
    <div v-else class="chart-content">
      <!-- Main chart showing selected range -->
      <div class="main-chart-container">
        <canvas ref="mainChartCanvas" :width="canvasWidth" height="300"></canvas>
      </div>
      
      <!-- Brush/navigator chart for time range selection -->
      <div class="brush-chart-container">
        <canvas ref="brushChartCanvas" :width="canvasWidth" height="80"></canvas>
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
      
      <!-- Time range info -->
      <div class="range-info">
        <span>Showing: {{ formatTimeRange(visibleStartTime, visibleEndTime) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';

// Define type for time series data points
export interface TimeSeriesDataPoint {
  time: number; // Time in seconds
  value: number;
}

// Define props
const props = defineProps<{
  data?: TimeSeriesDataPoint[];
  title?: string;
  yAxisTitle?: string;
  loading?: boolean;
  visibleMinutes?: number;
}>();

// Define default values for optional props
const defaultVisibleMinutes = 15; // Default visible time range in minutes

// Canvas and container refs
const chartContainer = ref<HTMLDivElement | null>(null);
const mainChartCanvas = ref<HTMLCanvasElement | null>(null);
const brushChartCanvas = ref<HTMLCanvasElement | null>(null);
const canvasWidth = ref(800);

// State for brush selection
const brushStartPercent = ref(0);
const brushWidthPercent = ref(0); // Initialize with zero width, will be calculated properly
const draggingBrush = ref(false);
const draggingHandle = ref<'start' | 'end' | null>(null);
const dragStartX = ref(0);
const dragStartLeft = ref(0);
const dragStartWidth = ref(0);

// Calculate the visible time range
const visibleMinutes = computed(() => props.visibleMinutes || defaultVisibleMinutes);
const totalSeconds = computed(() => props.data ? 
  Math.max(...props.data.map(point => point.time)) - 
  Math.min(...props.data.map(point => point.time)) : 0);

// Calculate visible start and end times
const visibleStartTime = computed(() => {
  if (!props.data || props.data.length === 0) return 0;
  const minTime = Math.min(...props.data.map(point => point.time));
  const totalRange = totalSeconds.value;
  return minTime + (brushStartPercent.value / 100) * totalRange;
});

const visibleEndTime = computed(() => {
  if (!props.data || props.data.length === 0) return 0;
  const minTime = Math.min(...props.data.map(point => point.time));
  const totalRange = totalSeconds.value;
  const endPercent = brushStartPercent.value + brushWidthPercent.value;
  return minTime + (endPercent / 100) * totalRange;
});

// Get visible data
const visibleData = computed(() => {
  if (!props.data) return [];
  return props.data.filter(point => 
    point.time >= visibleStartTime.value && 
    point.time <= visibleEndTime.value
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

// Format time range for display
const formatTimeRange = (startSeconds: number, endSeconds: number): string => {
  return `${formatTime(startSeconds)} - ${formatTime(endSeconds)}`;
};

// Resize observer
let resizeObserver: ResizeObserver | null = null;

// Initialize and set up resize handling
onMounted(() => {
  // Set the brush width immediately for 15 minutes
  initializeBrushSelection();
  
  // Update canvas size on mount
  updateCanvasSize();
  
  // Set up resize observer for responsive canvas
  if (chartContainer.value) {
    resizeObserver = new ResizeObserver(entries => {
      for (const entry of entries) {
        if (entry.target === chartContainer.value) {
          updateCanvasSize();
          if (!props.loading && props.data && props.data.length > 0) {
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
    
    if (!props.loading && props.data && props.data.length > 0) {
      drawMainChart();
      drawBrushChart();
    }
  }, 10);
});

// Initialize brush selection to show exactly 15 minutes
const initializeBrushSelection = () => {
  if (!props.data || props.data.length === 0) return;
  
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
});

// Handle window resize
const handleResize = () => {
  updateCanvasSize();
  if (!props.loading && props.data && props.data.length > 0) {
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
    
    // Update canvas elements directly to ensure proper scaling
    if (mainChartCanvas.value) {
      mainChartCanvas.value.style.width = '100%';
    }
    
    if (brushChartCanvas.value) {
      brushChartCanvas.value.style.width = '100%';
    }
  }
};

// Brush drag handlers
const startBrushDrag = (event: MouseEvent) => {
  event.preventDefault();
  
  // Start tracking drag operation
  draggingBrush.value = true;
  dragStartX.value = event.clientX;
  dragStartLeft.value = brushStartPercent.value;
  dragStartWidth.value = brushWidthPercent.value;
};

const startHandleDrag = (handle: 'start' | 'end', event: MouseEvent) => {
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
      let newWidth = dragStartWidth.value - deltaPercent;
      
      // Constraints
      newLeft = Math.max(0, newLeft);
      newWidth = Math.max(5, dragStartWidth.value - (newLeft - dragStartLeft.value));
      
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

// Watch for data changes
watch(() => props.data, (newData) => {
  if (!props.loading && newData && newData.length > 0) {
    // Call the same initialization function to ensure consistent behavior
    initializeBrushSelection();
    
    // Redraw charts with new data
    drawMainChart();
    drawBrushChart();
  }
}, { deep: true });

// Watch for loading state changes
watch(() => props.loading, (newValue) => {
  if (!newValue && props.data && props.data.length > 0) {
    // Make sure brush selection is properly initialized
    initializeBrushSelection();
    
    // Redraw both charts
    drawMainChart();
    drawBrushChart();
  }
});

// Draw the main chart showing the selected time range
const drawMainChart = () => {
  if (!mainChartCanvas.value || !props.data || props.data.length === 0) return;
  
  const canvas = mainChartCanvas.value;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  
  // Clear the canvas
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  
  // Set canvas dimensions
  const width = canvas.width;
  const height = canvas.height;
  const padding = 50; // Padding for axes
  
  // Chart dimensions
  const chartWidth = width - (padding * 2);
  const chartHeight = height - (padding * 2);
  
  // Draw background
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, width, height);
  
  // Calculate y-axis range based on visible data
  const values = visibleData.value.map(point => point.value);
  const minValue = Math.max(0, Math.min(...values) - 50); // Add some padding
  const maxValue = Math.min(1000, Math.max(...values) + 50);
  const valueRange = maxValue - minValue;
  
  // Find time range
  const times = visibleData.value.map(point => point.time);
  const minTime = Math.min(...times);
  const maxTime = Math.max(...times);
  const timeRange = maxTime - minTime;
  
  // Draw axes
  ctx.beginPath();
  ctx.strokeStyle = '#999';
  ctx.lineWidth = 1;
  
  // Y-axis
  ctx.moveTo(padding, padding);
  ctx.lineTo(padding, height - padding);
  
  // X-axis
  ctx.moveTo(padding, height - padding);
  ctx.lineTo(width - padding, height - padding);
  ctx.stroke();
  
  // Draw y-axis grid lines and labels
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  ctx.font = '12px Arial';
  ctx.fillStyle = '#666';
  
  const yGridCount = 5;
  for (let i = 0; i <= yGridCount; i++) {
    const y = padding + (chartHeight - (chartHeight * (i / yGridCount)));
    const value = minValue + (valueRange * (i / yGridCount));
    
    // Grid line
    ctx.beginPath();
    ctx.strokeStyle = '#eee';
    ctx.moveTo(padding, y);
    ctx.lineTo(width - padding, y);
    ctx.stroke();
    
    // Label
    ctx.fillText(Math.round(value).toString(), padding - 5, y);
  }
  
  // Draw x-axis labels
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  
  // Adjust x grid count based on canvas width
  const xGridCount = Math.min(Math.floor(width / 100), 10);
  for (let i = 0; i <= xGridCount; i++) {
    const x = padding + (chartWidth * (i / xGridCount));
    const time = minTime + (timeRange * (i / xGridCount));
    
    // Convert seconds to HH:MM:SS
    const timeStr = formatTime(time);
    
    // Grid line
    ctx.beginPath();
    ctx.strokeStyle = '#eee';
    ctx.moveTo(x, padding);
    ctx.lineTo(x, height - padding);
    ctx.stroke();
    
    // Label
    ctx.fillText(timeStr, x, height - padding + 5);
  }
  
  // Draw the line chart
  if (visibleData.value.length > 0) {
    ctx.beginPath();
    ctx.strokeStyle = '#2E93fA';
    ctx.lineWidth = 2;
    
    visibleData.value.forEach((point, index) => {
      const x = padding + (chartWidth * ((point.time - minTime) / timeRange));
      const y = height - padding - (chartHeight * ((point.value - minValue) / valueRange));
      
      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });
    
    ctx.stroke();
    
    // Add area fill below the line
    ctx.lineTo(padding + chartWidth, height - padding); // Bottom right corner
    ctx.lineTo(padding, height - padding); // Bottom left corner
    ctx.closePath();
    ctx.fillStyle = 'rgba(46, 147, 250, 0.1)';
    ctx.fill();
  }
  
  // Add y-axis title if provided
  if (props.yAxisTitle) {
    ctx.save();
    ctx.translate(10, height / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.textAlign = 'center';
    ctx.fillStyle = '#666';
    ctx.fillText(props.yAxisTitle, 0, 0);
    ctx.restore();
  }
  
  // Title or other decorations can be added here if needed
};

// Draw the brush chart showing the entire dataset
const drawBrushChart = () => {
  if (!brushChartCanvas.value || !props.data || props.data.length === 0) return;
  
  const canvas = brushChartCanvas.value;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  
  // Clear the canvas
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  
  // Set canvas dimensions
  const width = canvas.width;
  const height = canvas.height;
  const padding = 10; // Less padding for brush chart
  
  // Chart dimensions
  const chartWidth = width - (padding * 2);
  const chartHeight = height - (padding * 2);
  
  // Draw background
  ctx.fillStyle = '#f8f9fa';
  ctx.fillRect(0, 0, width, height);
  
  // Find y-axis range for the entire dataset
  const minValue = 0;
  const maxValue = 1000;
  const valueRange = maxValue - minValue;
  
  // Find time range for the entire dataset
  const times = props.data.map(point => point.time);
  const minTime = Math.min(...times);
  const maxTime = Math.max(...times);
  const timeRange = maxTime - minTime;
  
  // Draw axes
  ctx.beginPath();
  ctx.strokeStyle = '#ccc';
  ctx.lineWidth = 1;
  
  // Draw the outline
  ctx.strokeRect(padding, padding, chartWidth, chartHeight);
  
  // Draw the line chart for the entire dataset
  ctx.beginPath();
  ctx.strokeStyle = '#2E93fA';
  ctx.lineWidth = 1;
  
  props.data.forEach((point, index) => {
    const x = padding + (chartWidth * ((point.time - minTime) / timeRange));
    const y = height - padding - (chartHeight * ((point.value - minValue) / valueRange));
    
    if (index === 0) {
      ctx.moveTo(x, y);
    } else {
      ctx.lineTo(x, y);
    }
  });
  
  ctx.stroke();
  
  // Add area fill below the line
  ctx.lineTo(padding + chartWidth, height - padding); // Bottom right corner
  ctx.lineTo(padding, height - padding); // Bottom left corner
  ctx.closePath();
  ctx.fillStyle = 'rgba(46, 147, 250, 0.05)';
  ctx.fill();
  
  // Add labels for start and end time
  ctx.font = '10px Arial';
  ctx.fillStyle = '#666';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'bottom';
  ctx.fillText(formatTime(minTime), padding, height - 2);
  
  ctx.textAlign = 'right';
  ctx.fillText(formatTime(maxTime), width - padding, height - 2);
};

// Generate mock data if none provided
function generateMockData(durationInMinutes: number = 30): TimeSeriesDataPoint[] {
  const secondsTotal = durationInMinutes * 60;
  const data: TimeSeriesDataPoint[] = [];
  let value = 500; // Start at middle value
  
  for (let i = 0; i <= secondsTotal; i++) {
    // Random walk algorithm for more natural looking data
    value += Math.floor(Math.random() * 60) - 30;
    value = Math.max(0, Math.min(1000, value)); // Keep within 0-1000 range
    
    data.push({
      time: i,
      value: value
    });
  }
  
  return data;
}

// Export mock data generator for reuse
defineExpose({
  generateMockData
});
</script>

<style scoped>
.time-series-chart {
  width: 100%;
  min-height: 420px;
  position: relative;
  padding: 0 10px;
  box-sizing: border-box;
}

.chart-title {
  margin-bottom: 1rem;
  font-size: 1.2rem;
  font-weight: 500;
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

canvas {
  display: block;
  max-width: 100%;
  height: auto;
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
  width: 8px;
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

.range-info {
  text-align: center;
  font-size: 12px;
  color: #666;
  margin-top: 5px;
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

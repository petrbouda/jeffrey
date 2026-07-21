<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup lang="ts">
import { onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';
import HeatmapGraph from '@/services/subsecond/HeatmapGraph';
import DifferenceHeatmapGraph from '@/services/subsecond/DifferenceHeatmapGraph';
import HeatmapTooltip from '@/services/subsecond/HeatmapTooltip';
import DifferenceHeatmapTooltip from '@/services/subsecond/DifferenceHeatmapTooltip';
import MessageBus from '@/services/MessageBus';
import SubSecondDataProvider from '@/services/subsecond/SubSecondDataProvider';
import TimeRange from '@/services/api/model/TimeRange';
import { computeDifference } from '@/services/subsecond/SubSecondDifferenceUtils';
import SubSecondData from '@/services/subsecond/model/SubSecondData';
import MarginalRenderer from '@/services/subsecond/MarginalRenderer';
import EventTypes from '@/services/EventTypes';
import FormattingService from '@shared/services/FormattingService';

const props = defineProps<{
  primaryDataProvider: SubSecondDataProvider;
  primarySelectedCallback: any;
  secondaryDataProvider: SubSecondDataProvider | null;
  secondarySelectedCallback: any | null;
  tooltip: HeatmapTooltip;
  eventType: string;
  useWeight: boolean;
  bucketSizeMs?: number;
  initialTimeRange?: TimeRange;
}>();

let primaryHeatmap: HeatmapGraph | null = null;
let secondaryHeatmap: HeatmapGraph | null = null;
let differenceHeatmap: DifferenceHeatmapGraph | null = null;

let heatmapComponent: HTMLElement;
let resizeTimer: number | null = null;

const initialized = ref(false);
const mode = ref<'absolute' | 'difference'>('difference');

// Cached data for mode switching
let cachedPrimaryData: SubSecondData | null = null;
let cachedSecondaryData: SubSecondData | null = null;
let currentTimeRange: TimeRange | undefined = undefined;

const isDifferential = props.secondaryDataProvider !== null;

// Marginal ("Σ per bucket") bars + value colorbar — only for the single-heatmap (non-differential) view.
const COLORBAR_BINS = 6;
const showMarginal = !isDifferential;
const marginalCanvas = ref<HTMLCanvasElement | null>(null);
const colorbarCanvas = ref<HTMLCanvasElement | null>(null);
let marginalRenderer: MarginalRenderer | null = null;
let marginalRowSums: number[] | null = null;
let marginalMax = 0;

function computeRowSums(data: SubSecondData): number[] {
  return data.series.map(serie => {
    let sum = 0;
    for (const point of serie.data as any[]) {
      sum += point?.y ?? point?.[1] ?? 0;
    }
    return sum;
  });
}

function marginalValueMeta(): { label: string; format: (value: number) => string } {
  if (EventTypes.isAllocationEventType(props.eventType) && props.useWeight) {
    return { label: 'allocated', format: (value: number) => FormattingService.formatBytes(value) };
  }
  if (EventTypes.isBlockingEventType(props.eventType) && props.useWeight) {
    return { label: 'blocked time', format: (value: number) => FormattingService.formatDuration(value) };
  }
  return { label: 'samples', format: (value: number) => FormattingService.formatNumber(value) };
}

function renderMarginal() {
  if (!showMarginal || marginalRenderer == null || marginalRowSums == null) {
    return;
  }
  marginalRenderer.render(marginalRowSums);
}

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

function parseRgb(hex: string): [number, number, number] {
  let normalized = hex.replace('#', '');
  if (normalized.length === 3) {
    normalized = normalized
      .split('')
      .map(ch => ch + ch)
      .join('');
  }
  const num = parseInt(normalized, 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

function mixRgb(a: [number, number, number], b: [number, number, number], t: number): string {
  return `rgb(${Math.round(a[0] + (b[0] - a[0]) * t)},${Math.round(a[1] + (b[1] - a[1]) * t)},${Math.round(a[2] + (b[2] - a[2]) * t)})`;
}

// Value colorbar under the heatmap: stepped swatches (white -> cell hue) with bin-boundary labels.
function renderColorbar() {
  if (!showMarginal) {
    return;
  }
  const canvas = colorbarCanvas.value;
  if (canvas == null) {
    return;
  }
  const format = marginalValueMeta().format;
  const cssWidth = 384;
  const cssHeight = 40;
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  canvas.style.width = cssWidth + 'px';
  canvas.style.height = cssHeight + 'px';
  canvas.width = Math.round(cssWidth * dpr);
  canvas.height = Math.round(cssHeight * dpr);
  const ctx = canvas.getContext('2d')!;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssWidth, cssHeight);

  const cell = parseRgb(cssVar('--color-subsecond-cell', '#0022ff'));
  const white = parseRgb(cssVar('--color-white', '#ffffff'));
  const border = cssVar('--color-border', '#eaedf1');
  const text = cssVar('--color-text', '#5e6e82');

  const x = 14;
  const y = 6;
  const h = 14;
  const w = cssWidth - x - 24;
  const binWidth = w / COLORBAR_BINS;

  for (let i = 0; i < COLORBAR_BINS; i++) {
    ctx.fillStyle = mixRgb(white, cell, (i + 1) / COLORBAR_BINS);
    ctx.fillRect(x + i * binWidth, y, binWidth - 1, h);
    ctx.strokeStyle = border;
    ctx.lineWidth = 1;
    ctx.strokeRect(x + i * binWidth + 0.5, y + 0.5, binWidth - 1, h - 1);
  }

  ctx.fillStyle = text;
  ctx.font = "600 10px ui-monospace, monospace";
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  for (let i = 0; i <= COLORBAR_BINS; i++) {
    ctx.fillText(format(Math.round((marginalMax * i) / COLORBAR_BINS)), x + i * binWidth, y + h + 5);
  }
}

// Wrapper to add time range offset to selection callbacks
function createOffsetCallback(callback: any) {
  return (startTime: number[], endTime: number[]) => {
    if (currentTimeRange) {
      // Add the full time range offset (in milliseconds) if zoomed
      const offsetMs = currentTimeRange.start;
      // Convert [seconds, millis] to total milliseconds, add offset, convert back
      const startMs = startTime[0] * 1000 + startTime[1] + offsetMs;
      const endMs = endTime[0] * 1000 + endTime[1] + offsetMs;
      callback(
        [Math.floor(startMs / 1000), startMs % 1000],
        [Math.floor(endMs / 1000), endMs % 1000]
      );
    } else {
      callback(startTime, endTime);
    }
  };
}

onMounted(() => {
  MessageBus.on(MessageBus.SIDEBAR_CHANGED, () => handleResize(null, 200));

  heatmapComponent = document.getElementById('heatmaps')!;
  if (showMarginal && marginalCanvas.value) {
    marginalRenderer = new MarginalRenderer(marginalCanvas.value, 'primary');
  }
  handleResize(null);

  // Add window resize event listener
  window.addEventListener('resize', event => handleResize(event));

  initializeHeatmaps(props.initialTimeRange);
  MessageBus.on(MessageBus.SUBSECOND_SELECTION_CLEAR, () => heatmapsCleanup());
});

// Watch for mode changes
watch(mode, () => {
  if (isDifferential && cachedPrimaryData && cachedSecondaryData) {
    destroyAllHeatmaps();
    if (mode.value === 'difference') {
      renderDifferenceHeatmap(cachedPrimaryData, cachedSecondaryData);
    } else {
      renderAbsoluteHeatmaps(cachedPrimaryData, cachedSecondaryData);
    }
  }
});

function handleResize(event: any, delay: number = 100) {
  if (event != null) {
    event.preventDefault();
  }

  // The heatmap sizing is handled by flexbox (heatmap-container is flex: 1, min-width: 0);
  // on layout changes we only need to re-align the pinned marginal to the heatmap rows.
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }

  resizeTimer = window.setTimeout(() => {
    renderMarginal();
    renderColorbar();
  }, delay);
}

onBeforeUnmount(() => {
  const primary = document.getElementById('primary');
  if (primary != null) {
    primary.innerHTML = '';
  }

  const secondary = document.getElementById('secondary');
  if (secondary != null) {
    secondary.innerHTML = '';
  }

  const difference = document.getElementById('difference');
  if (difference != null) {
    difference.innerHTML = '';
  }
});

onUnmounted(() => {
  heatmapsCleanup();
  marginalRenderer?.destroy();
  marginalRenderer = null;
  MessageBus.off(MessageBus.SUBSECOND_SELECTION_CLEAR);
  MessageBus.off(MessageBus.SIDEBAR_CHANGED);
});

window.addEventListener('resize', () => {
  heatmapsCleanup();
});

function heatmapsCleanup() {
  if (primaryHeatmap != null) {
    primaryHeatmap.cleanup();
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.cleanup();
  }
  if (differenceHeatmap != null) {
    differenceHeatmap.cleanup();
  }
}

function destroyAllHeatmaps() {
  if (primaryHeatmap != null) {
    primaryHeatmap.destroy();
    primaryHeatmap = null;
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.destroy();
    secondaryHeatmap = null;
  }
  if (differenceHeatmap != null) {
    differenceHeatmap.destroy();
    differenceHeatmap = null;
  }

  // Clear containers
  const primary = document.getElementById('primary');
  if (primary) primary.innerHTML = '';
  const secondary = document.getElementById('secondary');
  if (secondary) secondary.innerHTML = '';
  const difference = document.getElementById('difference');
  if (difference) difference.innerHTML = '';
}

const initializeHeatmaps = (timeRange?: TimeRange) => {
  destroyAllHeatmaps();
  initialized.value = false;
  currentTimeRange = timeRange;

  if (props.secondaryDataProvider == null) {
    props.primaryDataProvider
      .provide(timeRange)
      .then(subSecondData => {
        primaryHeatmap = new HeatmapGraph(
          'primary',
          subSecondData,
          heatmapComponent,
          createOffsetCallback(props.primarySelectedCallback),
          props.tooltip,
          props.bucketSizeMs
        );
        primaryHeatmap.render();

        if (showMarginal) {
          marginalRowSums = computeRowSums(subSecondData);
          marginalMax = subSecondData.maxvalue;
          renderMarginal();
          renderColorbar();
        }
      })
      .catch(error => console.error('Error loading primary heatmap data:', error))
      .finally(() => (initialized.value = true));
  } else {
    downloadAndProcessHeatmaps(timeRange);
  }
};

function reloadWithTimeRange(timeRange?: TimeRange) {
  initializeHeatmaps(timeRange);
}

defineExpose({
  reloadWithTimeRange
});

function downloadAndProcessHeatmaps(timeRange?: TimeRange) {
  Promise.all([
    props.primaryDataProvider.provide(timeRange),
    props.secondaryDataProvider?.provide(timeRange)
  ])
    .then(([primaryData, secondaryData]) => {
      if (!primaryData || !secondaryData) {
        throw new Error('Failed to load heatmap data');
      }

      // Cache the data for mode switching
      cachedPrimaryData = primaryData;
      cachedSecondaryData = secondaryData;

      if (mode.value === 'difference') {
        renderDifferenceHeatmap(primaryData, secondaryData);
      } else {
        renderAbsoluteHeatmaps(primaryData, secondaryData);
      }
    })
    .catch(error => console.error('Error loading heatmap data:', error))
    .finally(() => (initialized.value = true));
}

function renderAbsoluteHeatmaps(primaryData: SubSecondData, secondaryData: SubSecondData) {
  // Sync max values for consistent color scaling
  const maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
  primaryData.maxvalue = maxvalue;
  secondaryData.maxvalue = maxvalue;

  primaryHeatmap = new HeatmapGraph(
    'primary',
    primaryData,
    heatmapComponent,
    createOffsetCallback(props.primarySelectedCallback),
    props.tooltip,
    props.bucketSizeMs
  );
  primaryHeatmap.render();

  secondaryHeatmap = new HeatmapGraph(
    'secondary',
    secondaryData,
    heatmapComponent,
    createOffsetCallback(props.secondarySelectedCallback),
    props.tooltip,
    props.bucketSizeMs
  );
  secondaryHeatmap.render();
}

function renderDifferenceHeatmap(primaryData: SubSecondData, secondaryData: SubSecondData) {
  const diffResult = computeDifference(primaryData, secondaryData);
  const diffTooltip = new DifferenceHeatmapTooltip(props.eventType, props.useWeight);

  differenceHeatmap = new DifferenceHeatmapGraph(
    'difference',
    diffResult.data,
    diffResult.minValue,
    diffResult.maxValue,
    heatmapComponent,
    createOffsetCallback(props.primarySelectedCallback),
    diffTooltip
  );
  differenceHeatmap.render();
}
</script>

<template>
  <!-- Toggle buttons for differential mode -->
  <div v-if="isDifferential" class="d-flex justify-content-center mb-2">
    <div class="btn-group" role="group">
      <button
        type="button"
        :class="['btn', 'btn-sm', mode === 'difference' ? 'btn-primary' : 'btn-outline-primary']"
        @click="mode = 'difference'"
      >
        Difference
      </button>
      <button
        type="button"
        :class="['btn', 'btn-sm', mode === 'absolute' ? 'btn-primary' : 'btn-outline-primary']"
        @click="mode = 'absolute'"
      >
        Absolute
      </button>
    </div>
  </div>

  <!-- Bootstrap Spinner Preloader -->
  <div v-show="!initialized">
    <div
      id="preloaderComponent"
      class="d-flex justify-content-center align-items-center"
      style="min-height: 200px"
    >
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <span class="ms-2">Loading data...</span>
    </div>
  </div>

  <div class="subsecond-stage">
    <!-- Pinned Σ-per-bucket bars (outside the horizontal scroll) -->
    <div v-if="showMarginal" class="subsecond-marginal">
      <canvas ref="marginalCanvas"></canvas>
    </div>

    <div class="subsecond-main">
      <div class="heatmap-container" id="heatmaps" style="overflow-x: scroll">
        <!-- Difference heatmap (shown in difference mode) -->
        <div v-show="mode === 'difference' && isDifferential" id="difference"></div>

        <!-- Absolute heatmaps (shown in absolute mode or non-differential) -->
        <div v-show="mode === 'absolute' || !isDifferential" id="primary"></div>
        <div v-show="mode === 'absolute' && isDifferential" id="secondary"></div>
      </div>

      <!-- Value colorbar legend, below the heatmap -->
      <div v-if="showMarginal" class="subsecond-colorbar">
        <canvas ref="colorbarCanvas"></canvas>
      </div>
    </div>
  </div>

  <!-- Fixed centered legend for difference mode -->
  <div v-if="isDifferential && mode === 'difference'" class="heatmap-legend">
    <div class="legend-item">
      <span class="legend-color" style="background-color: #155724"></span>
      <span class="legend-label">Large Decrease</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #28a745"></span>
      <span class="legend-label">Decrease</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #e6e6e6; border: 1px solid #ccc"></span>
      <span class="legend-label">No Change</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #dc3545"></span>
      <span class="legend-label">Increase</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #721c24"></span>
      <span class="legend-label">Large Increase</span>
    </div>
  </div>
</template>

<style>
.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none;
}
</style>

<style scoped lang="scss">
/* Marginal ("Σ per bucket") panel — pinned left, outside the heatmap's horizontal scroll */
.subsecond-stage {
  display: flex;
  align-items: flex-start;
  width: 100%;
}

.subsecond-main {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.subsecond-stage .heatmap-container {
  min-width: 0;
}

.subsecond-marginal {
  flex: none;
  background-color: var(--color-white);
  margin-right: 10px;
}

.subsecond-marginal canvas {
  display: block;
}

.subsecond-colorbar {
  padding: 8px 0 0 36px;
}

.subsecond-colorbar canvas {
  display: block;
}

/* Bootstrap preloader styles */
#preloaderComponent {
  background-color: var(--color-white);
  min-height: 200px;
  transition: all 0.3s ease;
}

#preloaderComponent .spinner-border {
  color: var(--color-primary);
  width: 2rem;
  height: 2rem;
}

#preloaderComponent span {
  color: var(--color-text);
  font-weight: 500;
}

.heatmap-legend {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  padding: 12px 0;
  background-color: var(--color-white);
  position: sticky;
  left: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-color {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-label {
  font-size: 0.8rem;
  color: var(--color-text);
  white-space: nowrap;
}
</style>

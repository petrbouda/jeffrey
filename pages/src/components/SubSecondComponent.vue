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
import {onBeforeUnmount, onMounted, onUnmounted, ref, watch} from 'vue';
import HeatmapGraph from '@/services/subsecond/HeatmapGraph';
import DifferenceHeatmapGraph from '@/services/subsecond/DifferenceHeatmapGraph';
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip";
import DifferenceHeatmapTooltip from "@/services/subsecond/DifferenceHeatmapTooltip";
import MessageBus from "@/services/MessageBus";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import {computeDifference} from "@/services/subsecond/SubSecondDifferenceUtils";
import SubSecondData from "@/services/subsecond/model/SubSecondData";

const props = defineProps<{
  primaryDataProvider: SubSecondDataProvider
  primarySelectedCallback: any,
  secondaryDataProvider: SubSecondDataProvider | null
  secondarySelectedCallback: any | null,
  tooltip: HeatmapTooltip,
  eventType: string,
  useWeight: boolean
}>()

let primaryHeatmap: HeatmapGraph | null = null;
let secondaryHeatmap: HeatmapGraph | null = null;
let differenceHeatmap: DifferenceHeatmapGraph | null = null;

let heatmapComponent: HTMLElement
let resizeTimer: number | null = null;

const initialized = ref(false);
const mode = ref<'absolute' | 'difference'>('difference');

// Cached data for mode switching
let cachedPrimaryData: SubSecondData | null = null;
let cachedSecondaryData: SubSecondData | null = null;
let currentTimeRange: TimeRange | undefined = undefined;

const isDifferential = props.secondaryDataProvider !== null;

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

  heatmapComponent = document.getElementById("heatmaps")!
  handleResize(null)

  // Add window resize event listener
  window.addEventListener('resize', (event) => handleResize(event));

  initializeHeatmaps();
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

  heatmapComponent.style.width = "0px";
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }

  resizeTimer = window.setTimeout(() => {
    // 75 cumulative padding to avoid browser's scrollbar
    let clientWidth = (heatmapComponent?.parentElement?.clientWidth as number) - 75 || 0;
    heatmapComponent.style.width = clientWidth + "px";
  }, delay);
}

onBeforeUnmount(() => {
  const primary = document.getElementById("primary");
  if (primary != null) {
    primary.innerHTML = '';
  }

  const secondary = document.getElementById("secondary");
  if (secondary != null) {
    secondary.innerHTML = '';
  }

  const difference = document.getElementById("difference");
  if (difference != null) {
    difference.innerHTML = '';
  }
})

onUnmounted(() => {
  heatmapsCleanup()
  MessageBus.off(MessageBus.SUBSECOND_SELECTION_CLEAR);
  MessageBus.off(MessageBus.SIDEBAR_CHANGED)
})

window.addEventListener("resize", () => {
  heatmapsCleanup()
});

function heatmapsCleanup() {
  if (primaryHeatmap != null) {
    primaryHeatmap.cleanup()
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.cleanup()
  }
  if (differenceHeatmap != null) {
    differenceHeatmap.cleanup()
  }
}

function destroyAllHeatmaps() {
  if (primaryHeatmap != null) {
    primaryHeatmap.destroy()
    primaryHeatmap = null;
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.destroy()
    secondaryHeatmap = null;
  }
  if (differenceHeatmap != null) {
    differenceHeatmap.destroy()
    differenceHeatmap = null;
  }

  // Clear containers
  const primary = document.getElementById("primary");
  if (primary) primary.innerHTML = '';
  const secondary = document.getElementById("secondary");
  if (secondary) secondary.innerHTML = '';
  const difference = document.getElementById("difference");
  if (difference) difference.innerHTML = '';
}

const initializeHeatmaps = (timeRange?: TimeRange) => {
  destroyAllHeatmaps();
  initialized.value = false;
  currentTimeRange = timeRange;

  if (props.secondaryDataProvider == null) {
    props.primaryDataProvider.provide(timeRange)
        .then((subSecondData) => {
          primaryHeatmap = new HeatmapGraph('primary', subSecondData, heatmapComponent, createOffsetCallback(props.primarySelectedCallback), props.tooltip);
          primaryHeatmap.render();
        })
        .catch((error) => console.error('Error loading primary heatmap data:', error))
        .finally(() => initialized.value = true);
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
          throw new Error("Failed to load heatmap data");
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
      .catch((error) => console.error('Error loading heatmap data:', error))
      .finally(() => initialized.value = true);
}

function renderAbsoluteHeatmaps(primaryData: SubSecondData, secondaryData: SubSecondData) {
  // Sync max values for consistent color scaling
  let maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
  primaryData.maxvalue = maxvalue;
  secondaryData.maxvalue = maxvalue;

  primaryHeatmap = new HeatmapGraph(
      'primary', primaryData, heatmapComponent, createOffsetCallback(props.primarySelectedCallback), props.tooltip);
  primaryHeatmap.render();

  secondaryHeatmap = new HeatmapGraph(
      'secondary', secondaryData, heatmapComponent, createOffsetCallback(props.secondarySelectedCallback), props.tooltip);
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
    <div id="preloaderComponent" class="d-flex justify-content-center align-items-center" style="min-height: 200px;">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <span class="ms-2">Loading data...</span>
    </div>
  </div>

  <div class="heatmap-container" id="heatmaps" style="overflow-x: scroll">
    <!-- Difference heatmap (shown in difference mode) -->
    <div v-show="mode === 'difference' && isDifferential" id="difference"></div>

    <!-- Absolute heatmaps (shown in absolute mode or non-differential) -->
    <div v-show="mode === 'absolute' || !isDifferential" id="primary"></div>
    <div v-show="mode === 'absolute' && isDifferential" id="secondary"></div>
  </div>

  <!-- Fixed centered legend for difference mode -->
  <div v-if="isDifferential && mode === 'difference'" class="heatmap-legend">
    <div class="legend-item">
      <span class="legend-color" style="background-color: #155724;"></span>
      <span class="legend-label">Large Decrease</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #28a745;"></span>
      <span class="legend-label">Decrease</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #E6E6E6; border: 1px solid #ccc;"></span>
      <span class="legend-label">No Change</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #dc3545;"></span>
      <span class="legend-label">Increase</span>
    </div>
    <div class="legend-item">
      <span class="legend-color" style="background-color: #721c24;"></span>
      <span class="legend-label">Large Increase</span>
    </div>
  </div>

</template>

<style>
.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none
}
</style>

<style scoped lang="scss">
/* Bootstrap preloader styles */
#preloaderComponent {
  background-color: #fff;
  min-height: 200px;
  transition: all 0.3s ease;
}

#preloaderComponent .spinner-border {
  color: #5e64ff;
  width: 2rem;
  height: 2rem;
}

#preloaderComponent span {
  color: #5e6e82;
  font-weight: 500;
}

.heatmap-legend {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  padding: 12px 0;
  background-color: #fff;
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
  color: #5e6e82;
  white-space: nowrap;
}
</style>

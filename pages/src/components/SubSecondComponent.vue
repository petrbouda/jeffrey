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
import {onBeforeUnmount, onMounted, onUnmounted, ref} from 'vue';
import HeatmapGraph from '@/services/subsecond/HeatmapGraph';
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip";
import MessageBus from "@/services/MessageBus";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";
import TimeRange from "@/services/flamegraphs/model/TimeRange";

const props = defineProps<{
  primaryDataProvider: SubSecondDataProvider
  primarySelectedCallback: any,
  secondaryDataProvider: SubSecondDataProvider | null
  secondarySelectedCallback: any | null,
  tooltip: HeatmapTooltip
}>()

let primaryHeatmap: HeatmapGraph | null = null;
let secondaryHeatmap: HeatmapGraph | null = null;

let heatmapComponent: HTMLElement
let resizeTimer: number | null = null;

const initialized = ref(false);

onMounted(() => {
  MessageBus.on(MessageBus.SIDEBAR_CHANGED, () => handleResize(null, 200));

  heatmapComponent = document.getElementById("heatmaps")!
  handleResize(null)

  // Add window resize event listener
  window.addEventListener('resize', (event) => handleResize(event));

  initializeHeatmaps();
  MessageBus.on(MessageBus.SUBSECOND_SELECTION_CLEAR, () => heatmapsCleanup());
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
}

const initializeHeatmaps = (timeRange?: TimeRange) => {
  if (primaryHeatmap != null) {
    primaryHeatmap.destroy()
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.destroy()
  }
  initialized.value = false;

  if (props.secondaryDataProvider == null) {
    props.primaryDataProvider.provide(timeRange)
        .then((subSecondData) => {
          primaryHeatmap = new HeatmapGraph('primary', subSecondData, heatmapComponent, props.primarySelectedCallback, props.tooltip);
          primaryHeatmap.render();
        })
        .catch((error) => console.error('Error loading primary heatmap data:', error))
        .finally(() => initialized.value = true);
  } else {
    downloadAndSyncHeatmaps(timeRange);
  }
};

function reloadWithTimeRange(timeRange: TimeRange) {
  initializeHeatmaps(timeRange);
}

defineExpose({
  reloadWithTimeRange
});

/*
 * Heatmaps have the different maximum value. We need to download both, and set up the higher number to both
 * datasets to have the same colors in both heatmaps.
 */
function downloadAndSyncHeatmaps(timeRange?: TimeRange) {
  Promise.all([
    props.primaryDataProvider.provide(timeRange),
    props.secondaryDataProvider?.provide(timeRange)
  ])
      .then(([primaryData, secondaryData]) => {
        if (!primaryData || !secondaryData) {
          throw new Error("Failed to load heatmap data");
        }

        let maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
        primaryData.maxvalue = maxvalue;
        secondaryData.maxvalue = maxvalue;

        primaryHeatmap = new HeatmapGraph(
            'primary', primaryData, heatmapComponent, props.primarySelectedCallback, props.tooltip);
        primaryHeatmap.render();

        secondaryHeatmap = new HeatmapGraph(
            'secondary', secondaryData, heatmapComponent, props.secondarySelectedCallback, props.tooltip);
        secondaryHeatmap.render();
      })
      .catch((error) => console.error('Error loading primary heatmap data:', error))
      .finally(() => initialized.value = true);
}
</script>

<template>
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
    <div id="primary"></div>
    <div id="secondary"></div>
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
</style>

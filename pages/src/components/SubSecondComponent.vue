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
import {onBeforeUnmount, onMounted, onUnmounted} from 'vue';
import HeatmapGraph from '@/services/subsecond/HeatmapGraph';
import HeatmapTooltip from "@/services/subsecond/HeatmapTooltip";
import MessageBus from "@/services/MessageBus";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";

const props = defineProps<{
  primaryDataProvider: SubSecondDataProvider
  primarySelectedCallback: any,
  secondaryDataProvider: SubSecondDataProvider | null
  secondarySelectedCallback: any | null,
  tooltip: HeatmapTooltip
}>()

let primaryHeatmap: HeatmapGraph | null = null;
let secondaryHeatmap: HeatmapGraph | null = null;

let preloaderComponent: HTMLElement
let heatmapComponent: HTMLElement

onMounted(() => {
  preloaderComponent = document.getElementById("preloaderComponent")!!
  heatmapComponent = document.getElementById("heatmaps")!!

  initializeHeatmaps();

  MessageBus.on(MessageBus.SUBSECOND_SELECTION_CLEAR, () => heatmapsCleanup());
});

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

const initializeHeatmaps = () => {
  if (primaryHeatmap != null) {
    primaryHeatmap.destroy()
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.destroy()
  }
  preloaderComponent.style.display = 'block';

  if (props.secondaryDataProvider == null) {
    props.primaryDataProvider.provide().then((subSecondData) => {
      primaryHeatmap = new HeatmapGraph('primary', subSecondData, heatmapComponent, props.primarySelectedCallback, props.tooltip);
      primaryHeatmap.render();

      preloaderComponent.style.display = 'none';
    });
  } else {
    downloadAndSyncHeatmaps();
  }
};

/*
 * Heatmaps have the different maximum value. We need to download both, and set up the higher number to both
 * datasets to have the same colors in both heatmaps.
 */
function downloadAndSyncHeatmaps() {
  props.primaryDataProvider.provide().then((primaryData) => {
    props.secondaryDataProvider?.provide().then((secondaryData) => {
      let maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
      primaryData.maxvalue = maxvalue;
      secondaryData.maxvalue = maxvalue;

      primaryHeatmap = new HeatmapGraph(
          'primary', primaryData, heatmapComponent, props.primarySelectedCallback, props.tooltip);
      primaryHeatmap.render();

      secondaryHeatmap = new HeatmapGraph(
          'secondary', secondaryData, heatmapComponent, props.secondarySelectedCallback, props.tooltip);
      secondaryHeatmap.render();

      preloaderComponent.style.display = 'none';
    });
  });
}
</script>

<template>
  <div class="card">
    <div class="flex justify-content-center h-full">
      <div id="preloaderComponent" class="layout-preloader-container">
        <div class="layout-preloader">
          <span></span>
        </div>
      </div>
    </div>

    <div style="overflow: auto;" id="heatmaps">
      <div id="primary"></div>
      <div id="secondary"></div>
    </div>
  </div>

  <div id="subsecond-highlight-area"/>
</template>

<style>
.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none
}
</style>

<style scoped lang="scss"></style>

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

<script setup>
import {onBeforeUnmount, onMounted, onUnmounted, ref} from 'vue';
import HeatmapService from '@/service/heatmap/HeatmapService';
import {useToast} from 'primevue/usetoast';
import Utils from '@/service/Utils';
import HeatmapGraph from '@/service/heatmap/HeatmapGraph';
import GraphType from "@/service/flamegraphs/GraphType";
import HeatmapTooltip from "@/service/heatmap/HeatmapTooltip";
import MessageBus from "@/service/MessageBus";

const props = defineProps([
  'primaryProfileId',
  'primarySelectedCallback',
  'secondaryProfileId',
  'secondarySelectedCallback',
  'eventType',
  'useWeight',
  'graphType',
  'generated'
]);

const flamegraphName = ref(null);
const toast = useToast();

let primaryHeatmap = null;
let secondaryHeatmap = null;

let preloaderComponent
let heatmapComponent

const useWeight = Utils.parseBoolean(props.useWeight)

onMounted(() => {
  preloaderComponent = document.getElementById("preloaderComponent")
  heatmapComponent = document.getElementById("heatmaps")

  initializeHeatmaps();

  MessageBus.on(MessageBus.SUBSECOND_SELECTION_CLEAR, () => heatmapsCleanup());
});

onBeforeUnmount(() => {
  document.getElementById("primary").innerHTML = '';
  document.getElementById("secondary").innerHTML = '';
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

  if (props.graphType === GraphType.PRIMARY) {
    HeatmapService.startup(props.primaryProfileId, props.eventType, useWeight).then((json) => {
      primaryHeatmap = new HeatmapGraph('primary', json, heatmapComponent, props.primarySelectedCallback,
          new HeatmapTooltip(props.eventType, useWeight));
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
  HeatmapService.startup(props.primaryProfileId, props.eventType, useWeight).then((primaryData) => {
    HeatmapService.startup(props.secondaryProfileId, props.eventType, useWeight).then((secondaryData) => {
      let maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
      primaryData.maxvalue = maxvalue;
      secondaryData.maxvalue = maxvalue;

      primaryHeatmap = new HeatmapGraph(
          'primary', primaryData, heatmapComponent,
          props.primarySelectedCallback, new HeatmapTooltip(props.eventType, useWeight));
      primaryHeatmap.render();

      secondaryHeatmap = new HeatmapGraph(
          'secondary', secondaryData, heatmapComponent,
          props.secondarySelectedCallback, new HeatmapTooltip(props.eventType, useWeight));
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

  <Toast/>
</template>

<style>
.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none
}

.apexcharts-tooltip {
  padding: 5px;
}
</style>

<style scoped lang="scss"></style>

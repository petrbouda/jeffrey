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
import {onMounted, ref} from 'vue';
import TimeseriesGraph from "@/services/timeseries/TimeseriesGraph";
import GraphType from "@/services/flamegraphs/GraphType";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import ToastService from "@/services/ToastService.ts";
import LoadingIndicator from "@/components/LoadingIndicator.vue";

const props = defineProps<{
  useWeight: boolean
  eventType: string
  graphType: string
  zoomEnabled: boolean
  graphUpdater: GraphUpdater
}>()

const graphTypeValue = ref('Area');
const isLoading = ref(false);

const timeseriesZoomCallback = (minX: number, maxX: number) => {
  if (props.zoomEnabled) {
    props.graphUpdater.updateWithZoom(new TimeRange(Math.floor(minX), Math.ceil(maxX), true))
  } else {
    ToastService.info('Flamegraph not updated', 'Generated flamegraph doesn\'t get updated after zooming of timeseries graph')
  }
};

let timeseries: TimeseriesGraph

const resetTimeseriesZoom = () => {
  props.graphUpdater.resetZoom();
};

onMounted(() => {
  props.graphUpdater.registerTimeseriesCallbacks(
      () => isLoading.value = true,
      () => isLoading.value = false,
      (data) => timeseries.render(data),
      (data: TimeseriesData) => timeseries.search(data),
      () => timeseries.resetSearch(),
      () => {},
      () => timeseries.resetZoom()
  )

  // must be kept in `onMounted` to correctly resolve the element `timeseries`
  timeseries = new TimeseriesGraph(
      props.eventType,
      'timeseries',
      timeseriesZoomCallback,
      props.graphType === GraphType.PRIMARY,
      props.useWeight,
      props.zoomEnabled);
});

const changeGraphType = () => {
  timeseries.changeGraphType(graphTypeValue.value);
}
</script>

<template>
  <div class="row align-items-center py-2">
    <div class="col-6 d-flex align-items-center">
      <button class="icon-btn me-2" title="Reset Zoom" @click="resetTimeseriesZoom()">
        <i class="bi bi-arrows-angle-expand"></i>
      </button>
      <div class="icon-toggle">
        <button class="toggle-icon"
                :class="{ active: graphTypeValue === 'Area' }"
                title="Area Graph"
                @click="graphTypeValue = 'Area'; changeGraphType()">
          <i class="bi bi-graph-up"></i>
        </button>
        <button class="toggle-icon"
                :class="{ active: graphTypeValue === 'Bar' }"
                title="Bar Graph"
                @click="graphTypeValue = 'Bar'; changeGraphType()">
          <i class="bi bi-bar-chart"></i>
        </button>
      </div>
    </div>
  </div>

  <LoadingIndicator v-if="isLoading" text="Generating Timeseries..."/>

  <div id="timeseries"></div>
</template>

<style scoped>
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #e2e8f0;
  background-color: #ffffff;
  color: #64748b;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.icon-btn:hover {
  background-color: #f1f5f9;
  border-color: #cbd5e1;
  color: #374151;
}

.icon-toggle {
  display: inline-flex;
  background-color: #f1f5f9;
  border-radius: 4px;
  padding: 2px;
}

.toggle-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 24px;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-icon:hover:not(.active) {
  color: #374151;
}

.toggle-icon.active {
  background-color: #ffffff;
  color: #1e293b;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}
</style>

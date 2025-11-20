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
  <div class="row">
    <div class="col-6 d-flex">
      <button class="btn btn-outline-secondary mt-2 me-2" title="Reset Zoom" @click="resetTimeseriesZoom()">
        <i class="bi bi-arrows-angle-expand"></i> Reset Zoom
      </button>
      <div class="btn-group mt-2" role="group">
        <button
          type="button"
          class="btn"
          :class="graphTypeValue === 'Area' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="graphTypeValue = 'Area'; changeGraphType()">
          Area
        </button>
        <button
          type="button"
          class="btn"
          :class="graphTypeValue === 'Bar' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="graphTypeValue = 'Bar'; changeGraphType()">
          Bar
        </button>
      </div>
    </div>
    <div class="col-6 d-flex justify-content-end align-items-center">
      <div class="spinner-border spinner-border-sm text-primary me-4" style="height: 20px; width: 20px" role="status" v-if="isLoading">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
  </div>

  <div id="timeseries"></div>
</template>

<style scoped>
</style>

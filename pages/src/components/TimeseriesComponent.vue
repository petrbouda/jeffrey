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

  // Register control callbacks for SearchBarComponent
  props.graphUpdater.registerTimeseriesControlCallbacks(
      () => resetTimeseriesZoom(),
      (type: string) => {
        graphTypeValue.value = type;
        timeseries.changeGraphType(type);
      }
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
</script>

<template>
  <LoadingIndicator v-if="isLoading" text="Generating Timeseries..."/>
  <div id="timeseries"></div>
</template>

<style scoped>
</style>

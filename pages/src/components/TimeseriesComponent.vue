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
import Utils from "@/services/Utils";
import TimeRange from "@/services/flamegraphs/model/TimeRange";
import GraphUpdater from "@/services/flamegraphs/updater/GraphUpdater";
import TimeseriesData from "@/services/timeseries/model/TimeseriesData";
import ToastService from "@/services/ToastService.ts";

const props = defineProps<{
  withSearch: string | null
  useWeight: boolean
  eventType: string
  graphType: string
  searchEnabled: boolean
  zoomEnabled: boolean
  graphUpdater: GraphUpdater
}>()

const searchValue = ref<string | null>(null);

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let searchPreloader: HTMLElement

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
  searchPreloader = document.getElementById("searchPreloader") as HTMLElement;

  props.graphUpdater.registerTimeseriesCallbacks(
      () => searchPreloader.style.display = '',
      () => searchPreloader.style.display = 'none',
      (data) => timeseries.render(data),
      (data: TimeseriesData) => {
        timeseries.search(data);
        searchValue.value = null;
      },
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

function search() {
  if (searchValue.value != null) {
    _search(searchValue.value)
  }
}

function _search(content: string) {
  if (Utils.isNotBlank(content)) {
    searchValue.value = content.trim()
    props.graphUpdater.updateWithSearch(searchValue.value)
  } else {
    searchValue.value = null
  }
}
</script>

<template>
  <div class="grid">
    <div class="col-6 flex flex-row">
      <Button class="p-button-filled p-button-info mt-2" title="Reset Zoom" @click="resetTimeseriesZoom()">
        <span class="material-symbols-outlined text-xl">home</span>
      </Button>
      <SelectButton v-model="graphTypeValue" :options="graphTypeOptions" @click="changeGraphType"
                    aria-labelledby="basic" class="pt-2 ml-2" :allowEmpty="false"/>
    </div>
    <div class="flex" :class="props.searchEnabled ? 'col-1' : 'col-6'">
      <div id="searchPreloader" class="layout-preloader-container w-full"
           style="padding: 0; align-items: center; justify-content: end">
        <div class="layout-preloader mr-4" style="height: 20px; width: 20px">
          <span></span>
        </div>
      </div>
    </div>

    <div class="col-5 p-inputgroup flex justify-items-end" v-if="props.searchEnabled">
      <Button class="p-button-info mt-2" label="Search" @click="search()"/>
      <InputText v-model="searchValue" @keydown.enter="search"
                 placeholder="Full-text search in Timeseries and Flamegraph" class="mt-2"/>
    </div>
  </div>

  <div id="timeseries"></div>
</template>

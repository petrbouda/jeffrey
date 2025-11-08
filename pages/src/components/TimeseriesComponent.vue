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
    <div class="d-flex" :class="props.searchEnabled ? 'col-1' : 'col-6'">
      <div id="searchPreloader" class="d-flex justify-content-end align-items-center w-100" style="padding: 0;">
        <div class="spinner-border spinner-border-sm text-primary me-4" style="height: 20px; width: 20px" role="status" v-show="false">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>
    </div>

    <div class="col-5 d-flex" v-if="props.searchEnabled">
      <div class="input-group mt-2">
        <button class="btn btn-primary d-flex align-items-center" @click="search()">Search</button>
        <input type="text" class="form-control" v-model="searchValue" @keydown.enter="search"
               placeholder="Full-text search in Timeseries and Flamegraph">
      </div>
    </div>
  </div>

  <div id="timeseries"></div>
</template>

<style scoped>
/* Fix for equal height of button and input */
.input-group {
  display: flex;
  align-items: stretch;
}

.input-group .btn,
.input-group .form-control {
  height: 38px; /* Standard Bootstrap input height */
  line-height: 1.5;
}

.input-group .btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 0;
  padding-bottom: 0;
}

/* Remove blue border and shadow from search input on focus */
.input-group .form-control:focus {
  border-color: #ced4da !important;
  box-shadow: none !important;
}
</style>

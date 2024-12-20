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
import {onBeforeUnmount, onMounted, ref} from 'vue';
import MessageBus from '@/service/MessageBus';
import TimeseriesGraph from "@/service/timeseries/TimeseriesGraph";
import GraphType from "@/service/flamegraphs/GraphType";
import {useToast} from "primevue/usetoast";
import ToastUtils from "@/service/ToastUtils";
import Utils from "@/service/Utils";
import FlamegraphDataProvider from "@/service/flamegraphs/service/FlamegraphDataProvider";

const props = defineProps<{
  withSearch: string | null
  useWeight: boolean
  eventType: string
  graphType: string
  searchEnabled: boolean
  zoomEnabled: boolean
  flamegraphDataProvider: FlamegraphDataProvider
}>()

const toast = useToast();
const searchValue = ref<string | null>(null);

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let searchPreloader: HTMLElement

const timeseriesZoomCallback = (minX: number, maxX: number) => {
  if (!props.zoomEnabled) {
    ToastUtils.notUpdatableAfterZoom(toast)
    return
  }

  const timeRange = {
    start: Math.floor(minX),
    end: Math.ceil(maxX),
    absoluteTime: true
  }
  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, {timeRange: timeRange});
};

let timeseries: TimeseriesGraph

const resetTimeseriesZoom = () => {
  timeseries.resetZoom();
  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, {});
};

onMounted(() => {
  searchPreloader = document.getElementById("searchPreloader") as HTMLElement;

  // must be kept in `onMounted` to correctly resolve the element `timeseries`
  timeseries = new TimeseriesGraph(
      props.eventType,
      'timeseries',
      timeseriesZoomCallback,
      props.graphType === GraphType.PRIMARY,
      props.useWeight);

  drawTimeseries(props.withSearch);

  MessageBus.on(MessageBus.TIMESERIES_RESET_SEARCH, () => timeseries.resetSearch());
  MessageBus.on(MessageBus.TIMESERIES_SEARCH, (content: any) => _search(content));
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.TIMESERIES_RESET_SEARCH);
  MessageBus.off(MessageBus.TIMESERIES_SEARCH);
});

function drawTimeseries(initialSearchValue: string | null) {
  searchPreloader.style.display = '';
  props.flamegraphDataProvider.provideTimeseries(initialSearchValue).then((data) => {
    timeseries.render(data);
    searchPreloader.style.display = 'none';
  });
}

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

    MessageBus.emit(MessageBus.FLAMEGRAPH_SEARCH, {searchValue: searchValue.value});

    searchPreloader.style.display = '';
    props.flamegraphDataProvider.provideTimeseries(searchValue.value)
        .then((data) => {
          timeseries.search(data);
          searchPreloader.style.display = 'none';
          searchValue.value = null;
        });
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
  <Toast/>
</template>

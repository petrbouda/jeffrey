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
import {onBeforeUnmount, onMounted, ref} from 'vue';
import MessageBus from '@/service/MessageBus';
import TimeseriesService from "@/service/timeseries/TimeseriesService";
import TimeseriesGraph from "@/service/timeseries/TimeseriesGraph";
import GraphType from "@/service/flamegraphs/GraphType";
import {useToast} from "primevue/usetoast";
import ToastUtils from "@/service/ToastUtils";
import ReplaceResolver from "@/service/replace/ReplaceResolver";
import Utils from "@/service/Utils";
import GuardianFlamegraphService from "@/service/guardian/GuardianFlamegraphService";
import GuardianTimeseriesService from "@/service/guardian/GuardianTimeseriesService";

const props = defineProps([
  'primaryProfileId',
  'secondaryProfileId',
  'graphType',
  'eventType',
  'useWeight',
  'useGuardian',
  'withSearch',
  'generated'
]);

// These values can be replaced by CLI tool
const resolvedWeight = ReplaceResolver.resolveWeight(props.generated, props.useWeight)

const toast = useToast();
const searchValue = ref(null);

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let searchPreloader

const resolvedGraphType = ReplaceResolver.resolveGraphType(props.graphType, props.generated);

// Search bar is enabled only for Primary Graph-Type and not for statically generated graphs
const searchEnabled = resolvedGraphType === GraphType.PRIMARY && !props.generated

let timeseriesService

const timeseriesZoomCallback = (minX, maxX) => {
  if (props.generated) {
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

let timeseries
const resetTimeseriesZoom = () => {
  timeseries.resetZoom();
  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, {});
};

onMounted(() => {
  if (props.useGuardian == null) {
    timeseriesService = new TimeseriesService(
        props.primaryProfileId,
        props.secondaryProfileId,
        props.eventType,
        resolvedWeight,
        resolvedGraphType,
        props.generated
    )
  } else {
    timeseriesService = new GuardianTimeseriesService(props.useGuardian)
  }

  searchPreloader = document.getElementById("searchPreloader")

  // must be kept in `onMounted` to correctly resolve the element `timeseries`
  timeseries = new TimeseriesGraph(
      props.eventType,
      'timeseries',
      timeseriesZoomCallback,
      resolvedGraphType === GraphType.PRIMARY,
      resolvedWeight);

  drawTimeseries(props.withSearch);

  MessageBus.on(MessageBus.TIMESERIES_RESET_SEARCH, () => {
    timeseries.resetSearch()
  });

  MessageBus.on(MessageBus.TIMESERIES_SEARCH, (content) => {
    _search(content)
  });
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.TIMESERIES_RESET_SEARCH);
  MessageBus.off(MessageBus.TIMESERIES_SEARCH);
});

function drawTimeseries(initialSearchValue) {
  searchPreloader.style.display = '';
  let generatePromise
  if (Utils.isNotBlank(initialSearchValue)) {
    generatePromise = timeseriesService.generateWithSearch(initialSearchValue)
  } else {
    generatePromise = timeseriesService.generate()
  }
  generatePromise.then((data) => {
    timeseries.render(data);
    searchPreloader.style.display = 'none';
  });
}

const changeGraphType = () => {
  resetTimeseriesZoom()
  timeseries.changeGraphType(graphTypeValue.value);
}

function search() {
  _search(searchValue.value)
}

function _search(content) {
  if (Utils.isNotBlank(content)) {
    searchValue.value = content.trim()

    MessageBus.emit(MessageBus.FLAMEGRAPH_SEARCH, {searchValue: searchValue.value});

    searchPreloader.style.display = '';
    timeseriesService.generateWithSearch(searchValue.value)
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
    <div class="flex" :class="searchEnabled ? 'col-1' : 'col-6'">
      <div id="searchPreloader" class="layout-preloader-container w-full"
           style="padding: 0; align-items: center; justify-content: end">
        <div class="layout-preloader mr-4" style="height: 20px; width: 20px">
          <span></span>
        </div>
      </div>
    </div>

    <div class="col-5 p-inputgroup flex justify-items-end" v-if="searchEnabled">
      <Button class="p-button-info mt-2" label="Search" @click="search()"/>
      <InputText v-model="searchValue" @keydown.enter="search"
                 placeholder="Full-text search in Timeseries and Flamegraph" class="mt-2"/>
    </div>
  </div>

  <div id="timeseries"></div>
  <Toast/>
</template>

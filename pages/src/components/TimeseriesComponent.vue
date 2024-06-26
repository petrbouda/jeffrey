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
import Flamegraph from '@/service/flamegraphs/Flamegraph';
import MessageBus from '@/service/MessageBus';
import TimeseriesService from "@/service/timeseries/TimeseriesService";
import TimeseriesGraph from "@/service/timeseries/TimeseriesGraph";
import GraphType from "@/service/flamegraphs/GraphType";

const props = defineProps(['primaryProfileId', 'secondaryProfileId', 'graphMode', 'eventType', 'useWeight']);

const searchValue = ref(null);
let timeseries = null;

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let primaryProfileId, secondaryProfileId, graphMode, eventType, useWeight;
let searchPreloader

onMounted(() => {
  searchPreloader = document.getElementById("searchPreloader")

  updateTimeseriesInfo(props)

  drawTimeseries(
      primaryProfileId,
      secondaryProfileId,
      graphMode,
      eventType,
      useWeight
  );

  MessageBus.on(MessageBus.TIMESERIES_RESET_SEARCH, () => {
    timeseries.resetSearch()
  });

  MessageBus.on(MessageBus.TIMESERIES_SEARCH, (content) => {
    searchValue.value = content
    search()
  });
});

function updateTimeseriesInfo(content) {
  primaryProfileId = content.primaryProfileId
  secondaryProfileId = content.secondaryProfileId
  graphMode = content.graphMode
  eventType = content.eventType

  if (typeof useWeight == "boolean") {
    useWeight = content.useWeight
  } else {
    useWeight = content.useWeight === "true"
  }
}

const resetTimeseriesZoom = () => {
  timeseries.resetZoom();

  const content = {
    eventType: eventType,
    primaryProfileId: primaryProfileId,
    secondaryProfileId: secondaryProfileId,
    graphMode: graphMode.value,
    resetSearch: false
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
};

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.TIMESERIES_RESET_SEARCH);
  MessageBus.off(MessageBus.TIMESERIES_SEARCH);
});

const updateFlamegraphByTimeseries = (minX, maxX) => {
  const timeRange = {
    start: Math.floor(minX),
    end: Math.ceil(maxX),
    absoluteTime: true
  };

  const content = {
    timeRange: timeRange,
    resetSearch: false
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
};

const drawTimeseries = (primaryProfile, secondaryProfile, graphMode, eventType, useWeight) => {
  searchPreloader.style.display = '';

  if (graphMode === GraphType.PRIMARY) {
    TimeseriesService.generate(primaryProfile, eventType, useWeight)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph(eventType, 'timeseries', data, updateFlamegraphByTimeseries, true, useWeight);
            timeseries.render();
          } else {
            timeseries.update(data, true);
          }
          searchPreloader.style.display = 'none';
        });
  } else if (graphMode === GraphType.DIFFERENTIAL) {
    TimeseriesService.generateDiff(primaryProfile, secondaryProfile, eventType, useWeight)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph(eventType,'timeseries', data, updateFlamegraphByTimeseries, false, useWeight);
            timeseries.render();
          } else {
            timeseries.update(data, false);
          }
          searchPreloader.style.display = 'none';
        });
  } else {
    console.log("Invalid selected mode: " + graphMode)
  }
}

const changeGraphType = () => {
  resetTimeseriesZoom()
  timeseries.changeGraphType(graphTypeValue.value);
}

function search() {
  const searchContent = {
    searchValue: searchValue.value,
    zoomOut: true
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_SEARCH, searchContent);

  searchPreloader.style.display = '';

  TimeseriesService.generateWithSearch(primaryProfileId, eventType, searchValue.value, useWeight)
      .then((data) => {
        timeseries.search(data);
        searchPreloader.style.display = 'none';
      });

  searchValue.value = null;
}
</script>

<template>
  <div class="grid">
    <div class="col-6 flex flex-row">
      <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" style="height: 40px" title="Reset Zoom" @click="resetTimeseriesZoom()"/>
      <SelectButton v-model="graphTypeValue" :options="graphTypeOptions" @click="changeGraphType" aria-labelledby="basic" class="pt-2 ml-2" :allowEmpty="false"/>
    </div>
    <div class="flex" :class="props.graphMode === GraphType.PRIMARY ? 'col-1' : 'col-6'">
      <div id="searchPreloader" class="layout-preloader-container w-full"
           style="padding: 0; align-items: center; justify-content: end">
        <div class="layout-preloader mr-4" style="height: 20px; width: 20px">
          <span></span>
        </div>
      </div>
    </div>

    <div class="col-5 p-inputgroup flex justify-items-end" v-if="props.graphMode === GraphType.PRIMARY">
      <Button class="p-button-info mt-2" label="Search" @click="search()"/>
      <InputText v-model="searchValue" @keydown.enter="search"
                 placeholder="Full-text search in Timeseries and Flamegraph" class="mt-2"/>
    </div>
  </div>

  <div id="timeseries"></div>
  <Toast/>
</template>

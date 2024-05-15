<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';
import TimeseriesService from "@/service/TimeseriesService";
import TimeseriesGraph from "@/service/TimeseriesGraph";

const props = defineProps(['primaryProfileId', 'secondaryProfileId', 'graphMode', 'eventType']);

const toast = useToast();
const searchValue = ref(null);
let timeseries = null;
const graphMode = ref(null)

const graphTypeValue = ref('Area');
const graphTypeOptions = ref(['Area', 'Bar']);

let primaryProfileId, secondaryProfileId, eventType;
let valueMode = Flamegraph.EVENTS_MODE
let searchPreloader

onMounted(() => {
  searchPreloader = document.getElementById("searchPreloader")

  updateTimeseriesInfo(props)

  drawTimeseries(
      primaryProfileId,
      secondaryProfileId,
      graphMode.value,
      eventType,
      valueMode
  );

  MessageBus.on(MessageBus.TIMESERIES_RESET_SEARCH, () => {
    timeseries.resetSearch()
  });

  MessageBus.on(MessageBus.TIMESERIES_SEARCH, (content) => {
    searchValue.value = content.searchValue
    search()
  });

  MessageBus.on(MessageBus.TIMESERIES_CHANGED, (content) => {
    timeseries.resetSearch()
    updateTimeseriesInfo(content)

    drawTimeseries(
        content.primaryProfileId,
        content.secondaryProfileId,
        content.graphMode,
        content.eventType,
        content.valueMode
    );
  });

  MessageBus.on(MessageBus.VALUE_MODE_CHANGED, (content) => {
    valueMode = content

    if (timeseries != null) {
      timeseries.setValueMode(valueMode)
    }

    drawTimeseries(
        primaryProfileId,
        secondaryProfileId,
        graphMode.value,
        eventType,
        valueMode
    );
  })
});

function updateTimeseriesInfo(content) {
  primaryProfileId = content.primaryProfileId
  secondaryProfileId = content.secondaryProfileId
  graphMode.value = content.graphMode
  eventType = content.eventType

  if (content.valueMode != null) {
    valueMode = content.valueMode
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
  MessageBus.off(MessageBus.TIMESERIES_CHANGED);
  MessageBus.off(MessageBus.VALUE_MODE_CHANGED);
});

const updateFlamegraphByTimeseries = (chartContext, {xaxis, yaxis}) => {
  const timeRange = {
    start: Math.floor(xaxis.min),
    end: Math.ceil(xaxis.max),
    absoluteTime: true
  };

  const content = {
    eventType: eventType,
    primaryProfileId: primaryProfileId,
    secondaryProfileId: secondaryProfileId,
    graphMode: graphMode.value,
    timeRange: timeRange,
    resetSearch: false
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
};

const drawTimeseries = (primaryProfile, secondaryProfile, graphMode, eventType, valueMode) => {
  searchPreloader.style.display = '';

  if (graphMode === Flamegraph.PRIMARY) {
    TimeseriesService.generate(primaryProfile, eventType, valueMode)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries, true, valueMode);
            timeseries.render();
          } else {
            timeseries.update(data, true);
          }
          searchPreloader.style.display = 'none';
        });
  } else if (graphMode === Flamegraph.DIFFERENTIAL) {
    TimeseriesService.generateDiff(primaryProfile, secondaryProfile, eventType, valueMode)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries, false, valueMode);
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

  TimeseriesService.generateWithSearch(primaryProfileId, eventType, searchValue.value, valueMode)
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
    <div class="flex" :class="graphMode === Flamegraph.PRIMARY ? 'col-1' : 'col-6'">
      <div id="searchPreloader" class="layout-preloader-container w-full"
           style="padding: 0; align-items: center; justify-content: end">
        <div class="layout-preloader mr-4" style="height: 20px; width: 20px">
          <span></span>
        </div>
      </div>
    </div>
    <div class="col-5 p-inputgroup flex justify-items-end" v-if="graphMode === Flamegraph.PRIMARY">
      <Button class="p-button-info mt-2" label="Search" @click="search()"/>
      <InputText v-model="searchValue" @keydown.enter="search"
                 placeholder="Full-text search in Timeseries and Flamegraph" class="mt-2"/>
    </div>
  </div>

  <div id="timeseries"></div>
  <Toast/>
</template>

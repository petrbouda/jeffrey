<script setup>
import {onBeforeMount, onMounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import {useToast} from 'primevue/usetoast';
import TimeseriesService from '@/service/TimeseriesService';
import TimeseriesGraph from '@/service/TimeseriesGraph';
import MessageBus from '@/service/MessageBus';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import HeatmapService from "@/service/HeatmapService";
import HeatmapGraph from "@/service/HeatmapGraph";
import SecondaryProfileService from "@/service/SecondaryProfileService";

const router = useRouter();
const route = useRoute();
const selectedEventType = ref(null);
const toast = useToast();
let jfrEventTypes
let timeseries = null;

const flamegraphModes = ref([{name: 'Primary'}, {name: 'Differential'}]);
const selectedHeatmapMode = ref(flamegraphModes.value[0]);

const updateFlamegraphByTimeseries = (chartContext, {xaxis, yaxis}) => {
  const timeRange = {
    start: Math.floor(xaxis.min),
    end: Math.ceil(xaxis.max)
  };
  MessageBus.emit(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED, timeRange);
};

const initializeHeatmaps = () => {
  console.log("switched")
};

const updateTimeseries = (eventType) => {
  TimeseriesService.generate(PrimaryProfileService.id(), eventType)
      .then((data) => {
        if (timeseries == null) {
          timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries);
          timeseries.render();
        } else {
          timeseries.update(data);
        }
      });
};

const resetTimeseriesZoom = () => {
  timeseries.resetZoom();
  updateFlamegraph(selectedEventType.value.code);
};

function updateFlamegraph(eventType) {
  MessageBus.emit(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED, eventType);
}

onBeforeMount(() => {
  jfrEventTypes = ref(GlobalVars.jfrTypes());
  selectedEventType.value = jfrEventTypes.value[0];

  updateTimeseries(selectedEventType.value.code);
});

const clickEventTypeSelected = () => {
  updateFlamegraph(selectedEventType.value.code);
  updateTimeseries(selectedEventType.value.code);
};
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <div class="mb-4 p-1 overflow-hidden">
      <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                    optionLabel="label" :multiple="false" style="float: left"/>

      <div style="float: right">
        <SelectButton v-model="selectedHeatmapMode" :disabled="SecondaryProfileService.id() == null" :options="flamegraphModes" @change="initializeHeatmaps"
                      optionLabel="name"/>
      </div>
    </div>

    <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
            @click="resetTimeseriesZoom()"/>

    <div id="timeseries"></div>

    <FlamegraphComponent :profileId="PrimaryProfileService.id()" :eventType="selectedEventType.code"/>
  </div>

  <Toast/>
</template>

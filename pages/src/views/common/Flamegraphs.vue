<script setup>
import {onBeforeMount, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import {useToast} from 'primevue/usetoast';
import TimeseriesService from '@/service/TimeseriesService';
import TimeseriesGraph from '@/service/TimeseriesGraph';
import MessageBus from '@/service/MessageBus';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/Flamegraph";

const router = useRouter();
const route = useRoute();
const selectedEventType = ref(GlobalVars.jfrTypes()[0]);
const toast = useToast();
let jfrEventTypes
let timeseries = null;

const selectedMode = ref(Flamegraph.PRIMARY);

const updateFlamegraphByTimeseries = (chartContext, {xaxis, yaxis}) => {
  const timeRange = {
    start: Math.floor(xaxis.min),
    end: Math.ceil(xaxis.max),
    absoluteTime: true
  };

  const content = {
    eventType: selectedEventType.value.code,
    primaryProfileId: PrimaryProfileService.id(),
    secondaryProfileId: SecondaryProfileService.id(),
    flamegraphMode: selectedMode.value,
    timeRange: timeRange
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
};

const switchModes = () => {
  updateTimeseries(selectedEventType.value.code)

  const content = {
    primaryProfileId: PrimaryProfileService.id(),
    secondaryProfileId: SecondaryProfileService.id(),
    eventType: selectedEventType.value.code,
    flamegraphMode: selectedMode.value
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
};

const updateTimeseries = (eventType) => {
  if (selectedMode.value === Flamegraph.PRIMARY) {
    TimeseriesService.generate(PrimaryProfileService.id(), eventType)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries);
            timeseries.render();
          } else {
            timeseries.update(data);
          }
        });
  } else if (selectedMode.value === Flamegraph.DIFFERENTIAL) {
    TimeseriesService.generateDiff(PrimaryProfileService.id(), SecondaryProfileService.id(), eventType)
        .then((data) => {
          if (timeseries == null) {
            timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries);
            timeseries.render();
          } else {
            timeseries.update(data);
          }
        });
  } else {
    console.log("Invalid selected mode")
  }
}

const resetTimeseriesZoom = () => {
  timeseries.resetZoom();
  updateFlamegraph(selectedEventType.value.code);
};

function updateFlamegraph(eventType) {
  const content = {
    eventType: eventType,
    primaryProfileId: PrimaryProfileService.id(),
    secondaryProfileId: SecondaryProfileService.id(),
    flamegraphMode: selectedMode.value
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
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
        <SelectButton v-model="selectedMode" :disabled="SecondaryProfileService.id() == null"
                      :options="Flamegraph.MODES" @change="switchModes"/>
      </div>
    </div>

    <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
            @click="resetTimeseriesZoom()"/>

    <div id="timeseries"></div>

    <FlamegraphComponent :primary-profile-id="PrimaryProfileService.id()" :flamegraph-type="Flamegraph.PRIMARY"
                         :eventType="selectedEventType.code"/>
  </div>

  <Toast/>
</template>

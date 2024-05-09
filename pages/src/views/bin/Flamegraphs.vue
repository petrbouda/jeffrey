<script setup>
import {onBeforeMount, ref} from 'vue';
import MessageBus from '@/service/MessageBus';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/Flamegraph";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";
import FlamegraphService from "@/service/FlamegraphService";

let selectedEventType
let jfrEventTypes

const selectedMode = ref(Flamegraph.PRIMARY);

const valueMode = ref(Flamegraph.EVENTS_MODE);
const valueTypeOptions = ref(Flamegraph.VALUE_MODES);
const valueSelectButtonEnabled = ref(null)

onBeforeMount(() => {
  FlamegraphService.getSupportedEvents(PrimaryProfileService.id())
      .then((data) => {
        jfrEventTypes = data
        selectedEventType = jfrEventTypes[0]
        valueSelectButtonEnabled.value = Flamegraph.VALUE_MODES_EVENTS.includes(selectedEventType.code)
      })
});

const clickGraphChanged = () => {
  // Differential cannot have WeightMode (at least at this moment)
  valueSelectButtonEnabled.value =
      !(selectedMode.value === Flamegraph.DIFFERENTIAL)
      && Flamegraph.VALUE_MODES_EVENTS.includes(selectedEventType.code)

  valueMode.value = Flamegraph.EVENTS_MODE

  const content = {
    eventType: selectedEventType.code,
    primaryProfileId: PrimaryProfileService.id(),
    secondaryProfileId: SecondaryProfileService.id(),
    graphMode: selectedMode.value,
    valueMode: valueMode.value,
    resetSearch: true
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
  MessageBus.emit(MessageBus.TIMESERIES_CHANGED, content);
};

const changeGraphType = () => {
  MessageBus.emit(MessageBus.VALUE_MODE_CHANGED, valueMode.value);
}
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <div class="grid">
      <div class="col-5">
        <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickGraphChanged"
                      optionLabel="label" :multiple="false"/>

      </div>
      <div id="search_output" class="col-7 flex flex-row-reverse">
        <SelectButton v-model="valueMode" :disabled="!valueSelectButtonEnabled" class="px-2" :options="valueTypeOptions"
                      @click="changeGraphType" aria-labelledby="basic"/>
      </div>
    </div>

    <TimeseriesComponent :primary-profile-id="PrimaryProfileService.id()" :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventType.code"/>
    <FlamegraphComponent :primary-profile-id="PrimaryProfileService.id()" :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventType.code"/>
  </div>
</template>

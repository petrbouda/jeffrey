<script setup>
import {onBeforeMount, ref} from 'vue';
import GlobalVars from '@/service/GlobalVars';
import MessageBus from '@/service/MessageBus';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import PrimaryProfileService from "@/service/PrimaryProfileService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import Flamegraph from "@/service/Flamegraph";
import TimeseriesComponent from "@/components/TimeseriesComponent.vue";

const selectedEventType = ref(GlobalVars.jfrTypes()[0]);
let jfrEventTypes

const selectedMode = ref(Flamegraph.PRIMARY);

onBeforeMount(() => {
  jfrEventTypes = ref(GlobalVars.jfrTypes());
  selectedEventType.value = jfrEventTypes.value[0];
});

const clickGraphChanged = () => {
  const content = {
    eventType: selectedEventType.value.code,
    primaryProfileId: PrimaryProfileService.id(),
    secondaryProfileId: SecondaryProfileService.id(),
    graphMode: selectedMode.value,
    resetSearch: true
  }

  MessageBus.emit(MessageBus.FLAMEGRAPH_CHANGED, content);
  MessageBus.emit(MessageBus.TIMESERIES_CHANGED, content);
};
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <div class="mb-4 p-1 overflow-hidden">
      <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickGraphChanged"
                    optionLabel="label" :multiple="false" style="float: left"/>

      <div style="float: right">
        <SelectButton v-model="selectedMode" :disabled="SecondaryProfileService.id() == null"
                      :options="Flamegraph.MODES" @change="clickGraphChanged"/>
      </div>
    </div>

    <TimeseriesComponent :primary-profile-id="PrimaryProfileService.id()" :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventType.code"/>
    <FlamegraphComponent :primary-profile-id="PrimaryProfileService.id()" :graph-mode="Flamegraph.PRIMARY"
                         :eventType="selectedEventType.code"/>
  </div>
</template>

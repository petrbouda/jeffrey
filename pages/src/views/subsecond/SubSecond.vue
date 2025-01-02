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
import {onBeforeMount, ref} from 'vue';
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import MessageBus from '@/service/MessageBus';
import {useToast} from 'primevue/usetoast';
import Utils from '@/service/Utils';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import router from "@/router";
import GraphType from "@/service/flamegraphs/GraphType";
import SubSecondComponent from "@/components/SubSecondComponent.vue";
import {useRoute} from "vue-router";
import FlamegraphClient from "@/service/flamegraphs/client/FlamegraphClient";
import PrimaryFlamegraphClient from "@/service/flamegraphs/client/PrimaryFlamegraphClient";
import DifferentialFlamegraphClient from "@/service/flamegraphs/client/DifferentialFlamegraphClient";
import FlamegraphTooltip from "@/service/flamegraphs/tooltips/FlamegraphTooltip";
import FlamegraphTooltipFactory from "@/service/flamegraphs/tooltips/FlamegraphTooltipFactory";
import SubSecondDataProvider from "@/service/subsecond/SubSecondDataProvider";
import SubSecondDataProviderImpl from "@/service/subsecond/SubSecondDataProviderImpl";
import HeatmapTooltip from "@/service/subsecond/HeatmapTooltip";

const route = useRoute()

const timeRangeLabel = ref<string | null>(null);
const flamegraphName = ref<string | null>(null);
const saveDialog = ref(false);

const showDialog = ref(false);
const toast = useToast();

let selectedProfileId: string | null = null;
let selectedTimeRange: any = null;

const queryParams = router.currentRoute.value.query

let flamegraphClient: FlamegraphClient
let flamegraphTooltip: FlamegraphTooltip

let primarySubSecondDataProvider: SubSecondDataProvider
let secondarySubSecondDataProvider: SubSecondDataProvider | null = null

let isDifferential: boolean = queryParams.graphMode === GraphType.DIFFERENTIAL

let useWeight = queryParams.useWeight === 'true'

onBeforeMount(() => {
  primarySubSecondDataProvider = new SubSecondDataProviderImpl(
      route.params.projectId as string,
      route.params.profileId as string,
      queryParams.eventType,
      useWeight,
  )

  if (isDifferential) {
    secondarySubSecondDataProvider = new SubSecondDataProviderImpl(
        route.params.projectId as string,
        SecondaryProfileService.id(),
        queryParams.eventType,
        useWeight,
    )
  }
})

const flamegraphService = new FlamegraphService(
    route.params.projectId,
    route.params.profileId,
    SecondaryProfileService.id(),
    queryParams.eventType,
    false,
    useWeight,
    queryParams.graphMode,
    false,
    false
)

function createOnSelectedCallback(profileId: string) {

  return function (startTime: number[], endTime: number[]) {
    timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
    selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    selectedProfileId = profileId;

    flamegraphName.value = `${selectedProfileId}-${queryParams.graphMode.toLowerCase()}-${queryParams.eventType.toLowerCase()}-${selectedTimeRange.start}-${selectedTimeRange.end}`;
    saveDialog.value = true
  };
}

function assembleRangeLabel(time: number[]) {
  return 'seconds: ' + time[0] + ' millis: ' + time[1];
}

function afterFlamegraphSaved() {
  MessageBus.emit(MessageBus.FLAMEGRAPH_CREATED, selectedProfileId);
  toast.add({severity: 'success', summary: 'Successful', detail: 'Flamegraph saved', life: 3000});

  saveDialog.value = false;
  flamegraphName.value = null;
  timeRangeLabel.value = null;
  selectedTimeRange = null;
  selectedProfileId = null;
}

const saveFlamegraph = () => {
  flamegraphService.saveEventTypeRange(flamegraphName.value, selectedTimeRange)
      .then(() => afterFlamegraphSaved());

  subSecondGraphsCleanup()
};

const subSecondGraphsCleanup = () => {
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
}

function showFlamegraph() {
  saveDialog.value = false
  subSecondGraphsCleanup()

  let isPrimary = queryParams.graphMode === GraphType.PRIMARY

  if (isPrimary) {
    flamegraphClient = new PrimaryFlamegraphClient(
        route.params.projectId as string,
        selectedProfileId!,
        queryParams.eventType,
        false,
        useWeight,
        false,
        false,
        false,
        null
    )
  } else {
    flamegraphClient = new DifferentialFlamegraphClient(
        route.params.projectId as string,
        route.params.profileId as string,
        SecondaryProfileService.id(),
        queryParams.eventType,
        useWeight,
        false,
        false,
        false,
    )
  }

  flamegraphTooltip = FlamegraphTooltipFactory.create(queryParams.eventType, useWeight, !isPrimary)
  showDialog.value = true
}
</script>

<template>
  <SubSecondComponent
      :primary-data-provider="primarySubSecondDataProvider"
      :primary-selected-callback="createOnSelectedCallback(route.params.profileId as string)"
      :secondary-data-provider="secondarySubSecondDataProvider"
      :secondary-selected-callback="createOnSelectedCallback(SecondaryProfileService.id())"
      :tooltip="new HeatmapTooltip(queryParams.eventType, useWeight)"
  />
  <Toast/>

  <Dialog v-model:visible="saveDialog" modal :style="{ width: '50rem', border: '0px' }">
    <template #container>
      <div class="card">
        <div class="grid p-fluid mt-3">
          <div class="field mb-4 col-12">
            <label for="filename" class="font-medium text-900">Time-range</label>
            <input class="p-inputtext p-component" style="color: black" id="filename"
                   v-model="timeRangeLabel"
                   disabled type="text">
          </div>

          <div class="field mb-4 col-12">
            <label for="filename" class="font-medium text-900">Filename</label>
            <input class="p-inputtext p-component" id="filename" v-model="flamegraphName" type="text">
          </div>

          <hr/>
          <div class="field col-4">
            <Button label="Show" severity="success" style="color: white"
                    @click="showFlamegraph"></Button>
          </div>
          <div class="field col-4">
            <Button label="Save" style="color: white" @click="saveFlamegraph"
                    :disabled="flamegraphName == null || flamegraphName.trim().length === 0"></Button>
          </div>
          <div class="field col-4">
            <Button type="button" label="Cancel" severity="secondary"
                    @click="saveDialog = false; subSecondGraphsCleanup()"></Button>
          </div>
        </div>
      </div>
    </template>
  </Dialog>

  <Dialog class="scrollable" header=" " :pt="{root: 'overflow-hidden'}" v-model:visible="showDialog" modal
          :style="{ width: '95%' }" style="overflow-y: auto">
    <FlamegraphComponent
        :with-timeseries="false"
        :with-search="null"
        :use-weight="useWeight"
        :use-guardian="null"
        :time-range="selectedTimeRange"
        :export-enabled="false"
        scrollable-wrapper-class="p-dialog-content"
        :flamegraph-tooltip="flamegraphTooltip"
        :flamegraph-client="flamegraphClient"/>
  </Dialog>
</template>

<style scoped lang="scss"></style>

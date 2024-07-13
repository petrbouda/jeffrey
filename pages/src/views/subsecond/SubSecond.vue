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
import {ref} from 'vue';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import MessageBus from '@/service/MessageBus';
import {useToast} from 'primevue/usetoast';
import Utils from '@/service/Utils';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import router from "@/router";
import GraphType from "@/service/flamegraphs/GraphType";
import SubSecondComponent from "@/components/SubSecondComponent.vue";

const timeRangeLabel = ref(null);
const flamegraphName = ref(null);
const saveDialog = ref(false);

const showDialog = ref(false);
const toast = useToast();

let selectedProfileId = null;
let selectedProfileName = null;
let selectedTimeRange = null;

const queryParams = router.currentRoute.value.query

const flamegraphService = new FlamegraphService(
    PrimaryProfileService.id(),
    SecondaryProfileService.id(),
    queryParams.eventType,
    false,
    queryParams.useWeight,
    queryParams.graphMode,
    false
)

function createOnSelectedCallback(profileId, profileName) {

  return function (heatmapId, event, startTime, endTime) {
    timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
    selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    selectedProfileId = profileId;
    selectedProfileName = profileName;

    flamegraphName.value = `${selectedProfileName}-${queryParams.graphMode.toLowerCase()}-${queryParams.eventType.toLowerCase()}-${selectedTimeRange.start}-${selectedTimeRange.end}`;
    saveDialog.value = true
  };
}

function assembleRangeLabel(time) {
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
  selectedProfileName = null;
}

const saveFlamegraph = () => {
  flamegraphService.saveEventTypeRange(flamegraphName.value, selectedTimeRange)
      .then(() => afterFlamegraphSaved());

  heatmapsCleanup()
};

const heatmapsCleanup = () => {
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
}
</script>

<template>
  <SubSecondComponent
      :primary-profile-id="PrimaryProfileService.id()"
      :primary-selected-callback="createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name())"
      :secondary-profile-id="SecondaryProfileService.id()"
      :secondary-selected-callback="createOnSelectedCallback(SecondaryProfileService.id(), SecondaryProfileService.name())"
      :event-type="queryParams.eventType"
      :use-weight="queryParams.useWeight"
      :graph-type="queryParams.graphMode"
      :generated="false"/>

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
                    @click="showDialog = true; saveDialog = false; heatmapsCleanup()"></Button>
          </div>
          <div class="field col-4">
            <Button label="Save" style="color: white" @click="saveFlamegraph"
                    :disabled="flamegraphName == null || flamegraphName.trim().length === 0"></Button>
          </div>
          <div class="field col-4">
            <Button type="button" label="Cancel" severity="secondary"
                    @click="saveDialog = false; heatmapsCleanup()"></Button>
          </div>
        </div>
      </div>
    </template>
  </Dialog>

  <Dialog header=" " :pt="{root: 'p-dialog-maximized'}" v-model:visible="showDialog" modal>
    <div v-if="queryParams.graphMode === GraphType.PRIMARY">
      <!-- we can display the flamegraph of primary or secondary profile, it will be a primary-profile-id from the perspective of the flamegraph component -->
      <FlamegraphComponent
          :primary-profile-id="selectedProfileId"
          :with-timeseries="false"
          :event-type="queryParams.eventType"
          :time-range="selectedTimeRange"
          :use-thread-mode="queryParams.useThreadMode"
          :use-weight="queryParams.useWeight"
          scrollable-wrapper-class="p-dialog-content"
          :generated="false"
          :export-enabled="false"
          :graph-type="GraphType.PRIMARY"/>
    </div>
    <div v-else-if="queryParams.graphMode === GraphType.DIFFERENTIAL">
      <FlamegraphComponent
          :primary-profile-id="PrimaryProfileService.id()"
          :secondary-profile-id="SecondaryProfileService.id()"
          :with-timeseries="false"
          :event-type="queryParams.eventType"
          :time-range="selectedTimeRange"
          :use-thread-mode="false"
          :use-weight="queryParams.useWeight"
          scrollable-wrapper-class="p-dialog-content"
          :generated="false"
          :export-enabled="false"
          :graph-type="GraphType.DIFFERENTIAL"/>
    </div>
  </Dialog>
</template>

<style scoped lang="scss"></style>

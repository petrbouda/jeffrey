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

const route = useRoute()

const timeRangeLabel = ref(null);
const flamegraphName = ref(null);
const saveDialog = ref(false);

const showDialog = ref(false);
const toast = useToast();

let selectedProfileId = null;
let selectedTimeRange = null;

const queryParams = router.currentRoute.value.query

const flamegraphService = new FlamegraphService(
    route.params.projectId,
    route.params.profileId,
    SecondaryProfileService.id(),
    queryParams.eventType,
    false,
    queryParams.useWeight,
    queryParams.graphMode,
    false
)

function createOnSelectedCallback(profileId) {

  return function (startTime, endTime) {
    timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
    selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    selectedProfileId = profileId;

    flamegraphName.value = `${selectedProfileId}-${queryParams.graphMode.toLowerCase()}-${queryParams.eventType.toLowerCase()}-${selectedTimeRange.start}-${selectedTimeRange.end}`;
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
}

const saveFlamegraph = () => {
  flamegraphService.saveEventTypeRange(flamegraphName.value, selectedTimeRange)
      .then(() => afterFlamegraphSaved());

  subSecondGraphsCleanup()
};

const subSecondGraphsCleanup = () => {
  MessageBus.emit(MessageBus.SUBSECOND_SELECTION_CLEAR, {});
}
</script>

<template>
  <SubSecondComponent
      :project-id="route.params.projectId"
      :primary-profile-id="route.params.profileId"
      :primary-selected-callback="createOnSelectedCallback(route.params.profileId)"
      :secondary-profile-id="SecondaryProfileService.id()"
      :secondary-selected-callback="createOnSelectedCallback(SecondaryProfileService.id())"
      :event-type="queryParams.eventType"
      :use-weight="Utils.parseBoolean(queryParams.useWeight)"
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
                    @click="showDialog = true; saveDialog = false; subSecondGraphsCleanup()"></Button>
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
    <div v-if="queryParams.graphMode === GraphType.PRIMARY">
      <!-- we can display the flamegraph of primary or secondary profile, it will be a primary-profile-id from the perspective of the flamegraph component -->
      <FlamegraphComponent
          :project-id="route.params.projectId"
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
          :project-id="route.params.projectId"
          :primary-profile-id="route.params.profileId"
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

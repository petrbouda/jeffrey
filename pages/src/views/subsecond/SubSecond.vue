<script setup>
import {onBeforeUnmount, onMounted, onUnmounted, ref} from 'vue';
import HeatmapService from '@/service/HeatmapService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import MessageBus from '@/service/MessageBus';
import {useToast} from 'primevue/usetoast';
import Utils from '@/service/Utils';
import HeatmapGraph from '@/service/HeatmapGraph';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import Flamegraph from "@/service/flamegraphs/Flamegraph";
import router from "@/router";
import DiffFlamegraphComponent from "@/components/DiffFlamegraphComponent.vue";
import GraphType from "@/service/flamegraphs/GraphType";

const timeRangeLabel = ref(null);
const flamegraphName = ref(null);
const saveDialog = ref(false);

const showDialog = ref(false);
const toast = useToast();
let selectedProfileId = null;

let selectedProfileName = null;

let selectedTimeRange = null;
let primaryHeatmap = null;

let secondaryHeatmap = null;
let preloaderComponent

const queryParams = router.currentRoute.value.query

const selectedEventType = queryParams.eventType
const selectedHeatmapMode = queryParams.graphMode
// Can enable PRIMARY Flamegraph even in DIFFERENTIAL MODE
const selectedFlamegraphMode = queryParams.graphMode

onMounted(() => {
  preloaderComponent = document.getElementById("preloaderComponent")

  initializeHeatmaps();
});

onBeforeUnmount(() => {
  document.getElementById("primary").innerHTML = '';
  document.getElementById("secondary").innerHTML = '';
})

onUnmounted(() => {
  heatmapsCleanup()
})

window.addEventListener("resize", () => {
  heatmapsCleanup()
});

function heatmapsCleanup() {
  if (primaryHeatmap != null) {
    primaryHeatmap.cleanup()
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.cleanup()
  }
}

function createOnSelectedCallback(profileId, profileName) {

  return function (heatmapId, event, startTime, endTime) {
    timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
    selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    selectedProfileId = profileId;
    selectedProfileName = profileName;

    setupFlamegraphName()
    saveDialog.value = true
  };
}

const initializeHeatmaps = () => {
  if (primaryHeatmap != null) {
    primaryHeatmap.destroy()
  }
  if (secondaryHeatmap != null) {
    secondaryHeatmap.destroy()
  }
  preloaderComponent.style.display = 'block';

  if (selectedHeatmapMode === GraphType.PRIMARY) {
    HeatmapService.startup(PrimaryProfileService.id(), selectedEventType).then((json) => {
      primaryHeatmap = new HeatmapGraph('primary', json, document.getElementById("heatmaps"), createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()));
      primaryHeatmap.render();
      preloaderComponent.style.display = 'none';
    });
  } else {
    downloadAndSyncHeatmaps();
  }
};

/*
 * Heatmaps have the different maximum value. We need to download both, and set up the higher number to both
 * datasets to have the same colors in both heatmaps.
 */
function downloadAndSyncHeatmaps() {
  HeatmapService.startup(PrimaryProfileService.id(), selectedEventType).then((primaryData) => {
    HeatmapService.startup(SecondaryProfileService.id(), selectedEventType).then((secondaryData) => {
      let maxvalue = Math.max(primaryData.maxvalue, secondaryData.maxvalue);
      primaryData.maxvalue = maxvalue;
      secondaryData.maxvalue = maxvalue;

      primaryHeatmap = new HeatmapGraph('primary', primaryData, document.getElementById("heatmaps"), createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()));
      primaryHeatmap.render();

      secondaryHeatmap = new HeatmapGraph('secondary', secondaryData, document.getElementById("heatmaps"), createOnSelectedCallback(SecondaryProfileService.id(), SecondaryProfileService.name()));
      secondaryHeatmap.render();

      preloaderComponent.style.display = 'none';
    });
  });
}

function setupFlamegraphName() {
  let mode = ""
  if (selectedFlamegraphMode === GraphType.DIFFERENTIAL) {
    mode = "diff-"
  }

  flamegraphName.value = `${selectedProfileName}-${mode}${selectedEventType.toLowerCase()}-${selectedTimeRange.start}-${selectedTimeRange.end}`;
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
  if (selectedFlamegraphMode === GraphType.PRIMARY) {
    FlamegraphService.saveEventTypeRange(
        selectedProfileId,
        flamegraphName.value,
        selectedEventType,
        selectedTimeRange,
        queryParams.useThreadMode,
        queryParams.useWeight
    )
        .then(() => afterFlamegraphSaved());
  } else {
    FlamegraphService.saveEventTypeDiffRange(
        PrimaryProfileService.id(),
        SecondaryProfileService.id(),
        flamegraphName.value,
        selectedEventType,
        selectedTimeRange,
        queryParams.useWeight)
        .then(() => afterFlamegraphSaved());
  }

  heatmapsCleanup()
};
</script>

<template>
  <div class="card">
    <div class="flex justify-content-center h-full">
      <div id="preloaderComponent" class="layout-preloader-container">
        <div class="layout-preloader">
          <span></span>
        </div>
      </div>
    </div>

    <div style="overflow: auto;" id="heatmaps">
      <div id="primary"></div>
      <div id="secondary"></div>
    </div>
  </div>

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
            <Button label="Show" severity="success" style="color: white" @click="showDialog = true; saveDialog = false; heatmapsCleanup()"></Button>
          </div>
          <div class="field col-4">
            <Button label="Save" style="color: white" @click="saveFlamegraph"
                    :disabled="flamegraphName == null || flamegraphName.trim().length === 0"></Button>
          </div>
          <div class="field col-4">
            <Button type="button" label="Cancel" severity="secondary" @click="saveDialog = false; heatmapsCleanup()"></Button>
          </div>
        </div>
      </div>
    </template>
  </Dialog>

  <Dialog header=" " :pt="{root: 'p-dialog-maximized'}" v-model:visible="showDialog" modal>
    <div v-if="selectedFlamegraphMode === GraphType.PRIMARY">
      <!-- we can display the flamegraph of primary or secondary profile, it will be a primary-profile-id from the perspective of the flamegraph component -->
      <FlamegraphComponent
          :primary-profile-id="selectedProfileId"
          :event-type="selectedEventType"
          :time-range="selectedTimeRange"
          :use-thread-mode="queryParams.useThreadMode"
          :use-weight="queryParams.useWeight"
          scrollable-wrapper-class="p-dialog-content"/>
    </div>
    <div v-else-if="selectedFlamegraphMode === GraphType.DIFFERENTIAL">
      <DiffFlamegraphComponent
          :primary-profile-id="PrimaryProfileService.id()"
          :secondary-profile-id="SecondaryProfileService.id()"
          :event-type="selectedEventType"
          :time-range="selectedTimeRange"
          :use-weight="queryParams.useWeight"
          scrollable-wrapper-class="p-dialog-content"/>
    </div>
  </Dialog>
</template>

<style>
.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none
}

.apexcharts-tooltip {
  padding: 5px;
}
</style>

<style scoped lang="scss"></style>

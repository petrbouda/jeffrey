<script setup>
import {onBeforeUnmount, onMounted, onUnmounted, ref} from 'vue';
import HeatmapService from '@/service/HeatmapService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import GlobalVars from '@/service/GlobalVars';
import MessageBus from '@/service/MessageBus';
import {useToast} from 'primevue/usetoast';
import Utils from '@/service/Utils';
import HeatmapGraph from '@/service/HeatmapGraph';
import FlamegraphComponent from "@/components/FlamegraphComponent.vue";
import Flamegraph from "@/service/Flamegraph";

const timeRangeLabel = ref(null);
const flamegraphName = ref(null);
const selectedEventType = ref(null);

const selectedHeatmapMode = ref(Flamegraph.PRIMARY);
const selectedFlamegraphMode = ref(Flamegraph.PRIMARY);
const saveDialog = ref(false);
const showDialog = ref(false);

const toast = useToast();

let selectedProfileId = null;
let selectedProfileName = null;
let selectedTimeRange = null;

const heatmapModelWidth = 350;
let heatmapModal, chart;
const heatmapModalActive = ref(false);
let heatmapModalHelper = false;

let primaryHeatmap = null;
let secondaryHeatmap = null;

let preloaderComponent

onMounted(() => {
  heatmapModal = document.getElementById('heatmapModal');
  preloaderComponent = document.getElementById("preloaderComponent")

  // --- Movable Modal Dialog
  function onMouseDrag({movementX, movementY}) {
    let getContainerStyle = window.getComputedStyle(heatmapModal);
    let leftValue = parseInt(getContainerStyle.left);
    let topValue = parseInt(getContainerStyle.top);
    heatmapModal.style.left = `${leftValue + movementX}px`;
    heatmapModal.style.top = `${topValue + movementY}px`;
  }

  heatmapModal.addEventListener("mousedown", () => {
    heatmapModal.addEventListener("mousemove", onMouseDrag);
  });

  document.addEventListener("mouseup", () => {
    heatmapModal.removeEventListener("mousemove", onMouseDrag);
  });
  // ---------------------------

  chart = document.getElementById('chart');

  selectedEventType.value = jfrEventTypes.value[0];
  initializeHeatmaps();
});

onBeforeUnmount(() => {
  document.getElementById("primary").innerHTML = '';
  document.getElementById("secondary").innerHTML = '';
})

onUnmounted(() => {
  heatmapsCleanup()
})

window.addEventListener("resize", (e) => {
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

window.addEventListener('click', (e) => {
  if (heatmapModalHelper === true && !heatmapModal.contains(e.target)) {
    heatmapModal.style.display = 'none';
    heatmapModalActive.value = false;
    heatmapModalHelper = false;
  }

  // Modal panel was recently displayed
  if (heatmapModalActive.value === true && heatmapModalHelper === false) {
    heatmapModalHelper = true;
  }
});

const closeHeatmapModal = () => {
  heatmapModal.style.display = 'none';
  heatmapModalActive.value = false;
  heatmapsCleanup()
};

function createOnSelectedCallback(profileId, profileName) {

  return function (heatmapId, event, startTime, endTime) {
    timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
    selectedTimeRange = Utils.toTimeRange(startTime, endTime, false);
    selectedProfileId = profileId;
    selectedProfileName = profileName;

    heatmapModalActive.value = true;
    heatmapModal.style.display = 'block';
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

  if (selectedHeatmapMode.value === Flamegraph.PRIMARY) {
    HeatmapService.startup(PrimaryProfileService.id(), selectedEventType.value.code).then((json) => {
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
  HeatmapService.startup(PrimaryProfileService.id(), selectedEventType.value.code).then((primaryData) => {
    HeatmapService.startup(SecondaryProfileService.id(), selectedEventType.value.code).then((secondaryData) => {
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
  if (selectedFlamegraphMode.value === Flamegraph.DIFFERENTIAL) {
    mode = "diff-"
  }

  flamegraphName.value = selectedProfileName + '-' + mode + selectedEventType.value.code.toLowerCase() + '-' + selectedTimeRange.start + '-' + selectedTimeRange.end;
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
  closeHeatmapModal();
}

const saveFlamegraph = () => {
  if (selectedFlamegraphMode.value === Flamegraph.PRIMARY) {
    FlamegraphService.saveEventTypeRange(
        selectedProfileId,
        flamegraphName.value,
        selectedEventType.value.code,
        selectedTimeRange)
        .then(() => afterFlamegraphSaved());
  } else {
    FlamegraphService.saveEventTypeDiffRange(
        PrimaryProfileService.id(),
        SecondaryProfileService.id(),
        flamegraphName.value,
        selectedEventType.value.code,
        selectedTimeRange)
        .then(() => afterFlamegraphSaved());
  }

  heatmapsCleanup()
};

const jfrEventTypes = ref(GlobalVars.jfrTypes());

const clickEventTypeSelected = () => {
  initializeHeatmaps();
};
</script>

<template>
  <div class="card">
    <div style="overflow: hidden; padding: 3px">
      <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                    optionLabel="label" :multiple="false" style="float: left"/>

      <div style="float: right">
        <SelectButton :disabled="SecondaryProfileService.id() == null" v-model="selectedHeatmapMode"
                      :options="Flamegraph.MODES" @change="initializeHeatmaps"/>
      </div>
    </div>

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

  <div id="heatmapModal" class="card"
       :style="{ 'z-index': '999', width: heatmapModelWidth + 'px', 'background-color':'var(--blue-50)', 'border':'1px solid var(--blue-200)'}">
    <div class="grid p-fluid mt-3">
      <div class="field col-10">
        <SelectButton v-model="selectedFlamegraphMode" :options="Flamegraph.MODES"
                      :disabled="selectedHeatmapMode === Flamegraph.PRIMARY"/>
      </div>
      <div class="field col-2">
        <Button icon="pi pi-times" outlined severity="secondary" @click="closeHeatmapModal"></Button>
      </div>
        <div class="field col-6">
          <Button v-if="Flamegraph.isModeSelected(selectedFlamegraphMode)" label="Show" style="color: white" class="p-button-success"
                  @click="showDialog = true; closeHeatmapModal()"></Button>
        </div>
        <div class="field col-6">
          <Button v-if="Flamegraph.isModeSelected(selectedFlamegraphMode)" label="Save" style="color: white" class="p-button-success"
                  @click="saveDialog = true; closeHeatmapModal(); setupFlamegraphName()"></Button>
        </div>
      </div>
  </div>


  <Dialog v-model:visible="saveDialog" modal :style="{ width: '50rem', border: '0px' }">
    <template #container="{ closeCallback }">
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
          <div class="field col-6">
            <Button label="Save" style="color: white" @click="saveFlamegraph"
                    :disabled="flamegraphName == null || flamegraphName.trim().length === 0"></Button>
          </div>
          <div class="field col-6">
            <Button type="button" label="Cancel" severity="secondary" @click="saveDialog = false"></Button>
          </div>
        </div>
      </div>
    </template>
  </Dialog>

  <Dialog header=" " maximizable v-model:visible="showDialog" modal :style="{ width: '95%' }" style="overflow-y: auto"
          :modal="true">
    <div v-if="selectedFlamegraphMode === Flamegraph.PRIMARY">
      <!-- we can display the flamegraph of primary or secondary profile, it will be a primary-profile-id from the perspective of the flamegraph component -->
      <FlamegraphComponent
          :primary-profile-id="selectedProfileId"
          :event-type="selectedEventType.code"
          :time-range="selectedTimeRange"
          :graph-mode="Flamegraph.PRIMARY"
          scrollable-wrapper-class="p-dialog-content"/>
    </div>
    <div v-else-if="selectedFlamegraphMode === Flamegraph.DIFFERENTIAL">
      <FlamegraphComponent
          :primary-profile-id="PrimaryProfileService.id()"
          :secondary-profile-id="SecondaryProfileService.id()"
          :event-type="selectedEventType.code"
          :time-range="selectedTimeRange"
          :graph-mode="Flamegraph.DIFFERENTIAL"
          scrollable-wrapper-class="p-dialog-content"/>
    </div>
  </Dialog>
</template>

<style>
#heatmapModal {
  position: absolute;
  display: none;
  overflow: hidden;
  padding-top: 0;
  padding-bottom: 0;
  border-radius: 5px;
}

.p-dialog {
  box-shadow: none;
}

.apexcharts-xaxistooltip {
  display: none
}

.apexcharts-tooltip {
  padding: 5px;
}

#heatmapModal {
  display: none;
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>

<style scoped lang="scss"></style>

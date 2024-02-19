<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import HeatmapService from '@/service/HeatmapService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import HeatmapGraph from '@/service/HeatmapGraph';
import GlobalVars from '@/service/GlobalVars';
import MessageBus from '@/service/MessageBus';
import { useToast } from 'primevue/usetoast';
import Utils from '@/service/Utils';
import HeatmapGraphApex from '@/service/HeatmapGraphApex';

const timeRangeLabel = ref(null);
const flamegraphName = ref(null);
const selectedEventType = ref(null);

const heatmapModes = ref([{ name: 'Single' }, { name: 'Dual' }]);
const flamegraphModes = ref([{ name: 'Regular' }, { name: 'Differential' }]);
const selectedHeatmapMode = ref(heatmapModes.value[0]);
const selectedFlamegraphMode = ref(flamegraphModes.value[0]);
const saveDialog = ref(false);

const toast = useToast();

let selectedProfileId = null;
let selectedTimeRange = null;

const heatmapModelWidth = 350;
let heatmapModal, chart;
const heatmapModalActive = ref(false);
let heatmapModalHelper = false;

let heatmap1 = null;

onMounted(() => {
    heatmapModal = document.getElementById('heatmapModal');
    chart = document.getElementById('chart');

    selectedEventType.value = jfrEventTypes.value[0];
    initializeHeatmaps();
});
onUnmounted(() => {
    if (heatmap1 != null) {
        heatmap1.cleanup()
    }
})

window.addEventListener('click', function(e) {
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
};

const activateHeatmapModal = (heatmapId, event) => {
    let heatmapRect = document.getElementById(heatmapId).getBoundingClientRect();
    let left = event.offsetX + heatmapRect.x;
    let top = event.offsetY + heatmapRect.y;

    // When the selected area is too much to right and modal window would overflow the browser,
    // then place the modal on the left side from the selected point.
    let right = left + heatmapModelWidth;
    if (right > window.innerWidth) {
        left = left - heatmapModelWidth;
    }

    heatmapModalActive.value = true;
    heatmapModal.style.left = left + 'px';
    heatmapModal.style.top = top + 'px';
    heatmapModal.style.display = 'block';
};

function createOnSelectedCallback(profileId, profileName) {

    return function(heatmapId, event, startTime, endTime) {
        flamegraphName.value = generateFlamegraphName(profileName, startTime, endTime);
        timeRangeLabel.value = HeatmapGraph.assembleRangeLabel(startTime) + ' - ' + HeatmapGraph.assembleRangeLabel(endTime);
        selectedTimeRange = Utils.toTimeRange(startTime, endTime);
        selectedProfileId = profileId;

        activateHeatmapModal(heatmapId, event);
    };
}

const initializeHeatmaps = () => {
    if (selectedHeatmapMode.value === heatmapModes.value[0]) {
        HeatmapService.startup(PrimaryProfileService.id(), selectedEventType.value.code).then((json) => {
            heatmap1 = new HeatmapGraphApex('heatmap1', json);
            heatmap1.render();
            // let heatmap = new HeatmapGraph('primary', json, createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()));
            // heatmap.render('chart');
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

            new HeatmapGraph('primary', primaryData, createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()))
                .render('chart');

            new HeatmapGraph('secondary', secondaryData, createOnSelectedCallback(SecondaryProfileService.id(), SecondaryProfileService.name()))
                .render('chart');
        });
    });
}

function generateFlamegraphName(profileName, startTime, endTime) {
    return profileName + '-' + selectedEventType.value.code.toLowerCase() + '-' + startTime[0] + '-' + startTime[1] + '-' + endTime[0] + '-' + endTime[1];
}

function afterFlamegraphGenerated() {
    MessageBus.emit(MessageBus.FLAMEGRAPH_CREATED, selectedProfileId);
    toast.add({ severity: 'success', summary: 'Successful', detail: 'Flamegraph generated', life: 3000 });

    saveDialog.value = false;
    flamegraphName.value = null;
    timeRangeLabel.value = null;
    selectedTimeRange = null;
    selectedProfileId = null;
    closeHeatmapModal();
}

const saveFlamegraph = () => {
    if (selectedFlamegraphMode.value === flamegraphModes.value[0]) {
        FlamegraphService.generateRange(
            selectedProfileId,
            flamegraphName.value,
            selectedEventType.value.code,
            selectedTimeRange)
            .then(() => afterFlamegraphGenerated());
    } else {
        FlamegraphService.generateDiff(
            PrimaryProfileService.id(),
            SecondaryProfileService.id(),
            flamegraphName.value,
            selectedEventType.value.code,
            selectedTimeRange)
            .then(() => afterFlamegraphGenerated());
    }
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
                          optionLabel="label" :multiple="false" style="float: left" />

            <div style="float: right">
                <SelectButton v-model="selectedHeatmapMode" :options="heatmapModes" @change="initializeHeatmaps"
                              optionLabel="name" />
            </div>
        </div>

        <div style="overflow: auto;">
            <div id="heatmap1"></div>
            <!--                    <div id="heatmap2"></div>-->
        </div>
    </div>

    <div class="card">
        <TabView>
            <TabPanel header="Primary">
                <FlamegraphList :profile-id="PrimaryProfileService.id()" profile-type="primary" />
            </TabPanel>

            <div v-if="selectedHeatmapMode === heatmapModes[1]">
                <TabPanel header="Secondary">
                    <FlamegraphList :profile-id="SecondaryProfileService.id()" profile-type="secondary" />
                </TabPanel>
            </div>
        </TabView>
    </div>

    <Toast />

    <div id="heatmapModal" class="card"
         :style="{ width: heatmapModelWidth + 'px', 'background-color':'var(--blue-50)', 'border':'1px solid var(--blue-200)'}">
        <div class="grid p-fluid mt-3">
            <div class="field col-10">
                <SelectButton v-model="selectedFlamegraphMode" :options="flamegraphModes"
                              :disabled="selectedHeatmapMode.name === 'Single'" optionLabel="name" />
            </div>
            <div class="field col-2">
                <Button icon="pi pi-times" outlined severity="secondary" @click="closeHeatmapModal"></Button>
            </div>
            <div class="field col-6">
                <Button label="Show" style="color: white" class="p-button-success"
                        @click="saveDialog = true; closeHeatmapModal()"></Button>
            </div>
            <div class="field col-6">
                <Button label="Save" style="color: white" class="p-button-success"
                        @click="saveDialog = true; closeHeatmapModal()"></Button>
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

                    <hr />
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

.details {
    padding-top: 25px;
}

.p-dialog {
    box-shadow: none;
}

.legend {
    width: 316px;
    height: 60px;
}
</style>

<style scoped lang="scss"></style>

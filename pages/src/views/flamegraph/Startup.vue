<script setup>
import { onMounted, ref } from 'vue';
import HeatmapService from '@/service/HeatmapService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import HeatmapGraph from '@/service/HeatmapGraph';
import GlobalVars from '@/service/GlobalVars';
import MessageBus from '@/service/MessageBus';
import { useToast } from 'primevue/usetoast';

const timeRangeLabel = ref(null);
const generateDisabled = ref(true);
const flamegraphName = ref(null);
const selectedEventType = ref(null);

const heatmapModes = ref([{ name: 'Single' }, { name: 'Dual' }]);
const flamegraphModes = ref([{ name: 'Regular' }, { name: 'Differential' }]);
const selectedHeatmapMode = ref(heatmapModes.value[0]);
const selectedFlamegraphMode = ref(flamegraphModes.value[0]);

const toast = useToast();

let selectedProfileId = null;
let selectedTimeRange = null;

onMounted(() => {
    selectedEventType.value = jfrEventTypes.value[0];
    initializeHeatmaps();
});

function createOnSelectedCallback(profileId, profileName) {

    return function(startTime, endTime) {
        flamegraphName.value = generateFlamegraphName(profileName, startTime, endTime);
        timeRangeLabel.value = HeatmapGraph.assembleRangeLabel(startTime) + ' - ' + HeatmapGraph.assembleRangeLabel(endTime);
        generateDisabled.value = false;
        selectedTimeRange = [startTime, endTime];
        selectedProfileId = profileId;
    };
}

const initializeHeatmaps = () => {
    document.getElementById('chart').innerHTML = '';

    if (selectedHeatmapMode.value === heatmapModes.value[0]) {
        HeatmapService.startup(PrimaryProfileService.id(), selectedEventType.value.code).then((json) => {
            let heatmap = new HeatmapGraph(json, createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()));
            heatmap.render('chart');
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

            new HeatmapGraph(primaryData, createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()))
                .render('chart');

            new HeatmapGraph(secondaryData, createOnSelectedCallback(SecondaryProfileService.id(), SecondaryProfileService.name()))
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

    generateDisabled.value = true;
    flamegraphName.value = null;
    timeRangeLabel.value = null;
    selectedTimeRange = null;
    selectedProfileId = null;
}

const generateFlamegraph = () => {
    if (selectedFlamegraphMode.value === flamegraphModes.value[0]) {
        FlamegraphService.generateRange(
            selectedProfileId,
            flamegraphName.value,
            selectedEventType.value.code,
            selectedTimeRange[0],
            selectedTimeRange[1])
            .then(() => afterFlamegraphGenerated());
    } else {
        FlamegraphService.generateDiff(
            PrimaryProfileService.id(),
            SecondaryProfileService.id(),
            flamegraphName.value,
            selectedEventType.value.code,
            selectedTimeRange[0],
            selectedTimeRange[1])
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

        <div id="chart" class="chart" style="overflow-x: auto"></div>

        <div style="overflow: hidden">
            <div id="legend" class="legend" style="float: right"></div>
            <div id="details" class="details" style="float: left"></div>
        </div>
    </div>
    <hr />

    <div class="card">
        <h5 v-if="!generateDisabled">
            Generate a flamegraph for a time-range: <span style="color: blue">{{ timeRangeLabel }}</span>
        </h5>
        <h5 v-else>No time-range selected</h5>

        <SelectButton v-model="selectedFlamegraphMode" :options="flamegraphModes" optionLabel="name" />

        <div class="grid p-fluid mt-3">
            <div class="field col-12 md:col-4">
                <label for="filename" class="p-sr-only">Flamegraph Name</label>
                <InputText id="filename" type="text" placeholder="Flamegraph Name" v-model="flamegraphName"
                           :disabled="generateDisabled" />
            </div>
            <div class="field col-12 md:col-2">
                <Button label="Save" style="color: white" @click="generateFlamegraph"
                        :disabled="generateDisabled || flamegraphName == null || flamegraphName.trim().length === 0"></Button>
            </div>
            <div class="field col-12 md:col-2">
                <Button label="Show" style="color: white" class="p-button-success"
                        @click="generateFlamegraph"
                        :disabled="generateDisabled || flamegraphName == null || flamegraphName.trim().length === 0"></Button>
            </div>
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
</template>

<style>
.details {
    padding-top: 25px;
}

.legend {
    width: 316px;
    height: 60px;
}
</style>

<style scoped lang="scss"></style>

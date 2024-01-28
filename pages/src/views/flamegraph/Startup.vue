<script setup>
import { onMounted, ref } from 'vue';
import HeatmapService from '@/service/HeatmapService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FlamegraphService from '@/service/FlamegraphService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import HeatmapGraph from '@/service/HeatmapGraph';
import GlobalVars from '@/service/GlobalVars';
import MessageBus from '@/service/MessageBus';

const timeRangeLabel = ref(null);
const generateDisabled = ref(true);
const flamegraphName = ref(null);
const selectedEventType = ref(null);

const comparisonEnabled = ref(false);

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

    if (comparisonEnabled.value) {
        downloadAndSyncHeatmaps();
    } else {
        HeatmapService.startup(PrimaryProfileService.id(), selectedEventType.value.code).then((json) => {
            let heatmap = new HeatmapGraph(json, createOnSelectedCallback(PrimaryProfileService.id(), PrimaryProfileService.name()));
            heatmap.render('chart');
        });
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

            console.log(primaryData)
            console.log(secondaryData)

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

const generateFlamegraph = () => {
    FlamegraphService.generateRange(
        selectedProfileId,
        flamegraphName.value,
        selectedEventType.value.code,
        selectedTimeRange[0],
        selectedTimeRange[1])
        .then(() => {
            MessageBus.emit(MessageBus.FLAMEGRAPH_CREATED, selectedProfileId);

            generateDisabled.value = true;
            flamegraphName.value = null;
            timeRangeLabel.value = null;
            selectedTimeRange = null;
            selectedProfileId = null;
        });
};

const jfrEventTypes = ref(GlobalVars.jfrTypes());

const clickEventTypeSelected = () => {
    initializeHeatmaps();
};
</script>

<template>
    <div class="card">
        <div style="overflow: hidden">
            <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                          optionLabel="label" :multiple="false" style="float: left" />

            <ToggleButton v-model="comparisonEnabled" onLabel="Comparison Enabled" offLabel="Comparison Disabled"
                          @click="initializeHeatmaps" style="float: right" />
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

        <div class="grid p-fluid mt-3">
            <div class="field col-12 md:col-4">
                <label for="filename" class="p-sr-only">Flamegraph Name</label>
                <InputText id="filename" type="text" placeholder="Flamegraph Name" v-model="flamegraphName"
                           :disabled="generateDisabled" />
            </div>
            <div class="field col-12 md:col-2">
                <Button label="Save Flamegraph" style="color: white" @click="generateFlamegraph"
                        :disabled="generateDisabled || flamegraphName == null || flamegraphName.trim().length === 0"></Button>
            </div>
            <div class="field col-12 md:col-2">
                <Button label="Show Flamegraph" style="color: white" class="p-button-success"
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

            <div v-if="comparisonEnabled">
                <TabPanel header="Secondary">
                    <FlamegraphList :profile-id="SecondaryProfileService.id()" profile-type="secondary" />
                </TabPanel>
            </div>
        </TabView>
    </div>
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

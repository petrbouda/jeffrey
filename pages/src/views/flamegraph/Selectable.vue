<script setup>
import { onMounted, ref } from 'vue';
import HeatmapService from '@/service/HeatmapService';
import SelectedProfileService from '@/service/SelectedProfileService';
import FlamegraphService from '@/service/FlamegraphService';

let timeRange;
const timeRangeLabel = ref(null);
const flamegraphs = ref(null);
const generateDisabled = ref(true);
const flamegraphName = ref(null);
const selectedEventType = ref(null);
let data = null;


onMounted(() => {
    selectedEventType.value = jfrEventTypes.value[0]
    updateHeatmap(jfrEventTypes.value[0]);
});

function updateHeatmap(selectedEventType) {
    HeatmapService.startup(selectedEventType.code).then((json) => {
        const chartTag = document.getElementById('chart');
        chartTag.innerHTML = '';

        render(json);
        data = json;
    });
}

function assembleRangeLabelWithSamples(samples, i, j) {
    return assembleRangeLabel(calculateStartTime([i, j])) + ', samples: ' + samples;
}

function assembleRangeLabel(time) {
    return 'seconds: ' + time[0] + ' millis: ' + time[1];
}

function render(data) {
    function onClick(samples, i, j) {
        select(samples, [i, j]);
    }

    function onMouseOver(samples, i, j) {
        document.getElementById('details').innerHTML = assembleRangeLabelWithSamples(samples, i, j);
        hover([i, j]);
    }

    const heatmapNode = document.getElementById('chart');

    let width = heatmapNode.offsetWidth;
    let gridSize = width / data.columns.length;

    if (gridSize > 10) {
        width = data.columns.length * 10;
    } else if (gridSize < 6) {
        width = data.columns.length * 6;
    }

    const ticks = Math.floor(width / 50);

    let legendWidth = Math.min(width * 0.8, 400);
    let legendTicks = legendWidth > 100 ? Math.floor(legendWidth / 50) : 2;

    chart = d3
        .heatmap()
        .title('')
        .subtitle('')
        .width(width)
        .legendScaleTicks(legendTicks)
        .xAxisScale([data.columns[0], data.columns[data.columns.length - 1]])
        .xAxisScaleTicks(ticks)
        .highlightColor('#936EB5')
        .highlightOpacity('0.4')
        .gridStrokeOpacity(0.0)
        .invertHighlightRows(true)
        .xAxisLabels(data.columns)
        .yAxisScale(20)

        .onClick(onClick)
        .onMouseOver(onMouseOver)
        .colorScale(
            d3
                .scaleLinear()
                .domain([0, data.maxvalue / 2, data.maxvalue])
                .range(['#FFFFFF', '#FF5032', '#E50914'])
        )
        .margin({
            top: 40,
            right: 0,
            bottom: 10,
            left: 0
        })
        .legendElement('#legend')
        .legendHeight(50)
        .legendWidth(300)
        .legendMargin({ top: 5, right: 0, bottom: 30, left: 0 });

    d3.select('#chart').datum(data.values).call(chart);
}

function generateFlamegraphName(start, end) {
    let startTime = calculateStartTime(start);
    let endTime = calculateEndTime(end);
    return SelectedProfileService.profileName() + '-' + selectedEventType.value.code.toLowerCase() + '-' + startTime[0] + '-' + startTime[1] + '-' + endTime[0] + '-' + endTime[1];
}

const generateFlamegraph = () => {
    let startTime = calculateStartTime(timeRange[0]);
    let endTime = calculateEndTime(timeRange[1]);

    FlamegraphService.generateRange(flamegraphName.value, selectedEventType.value.code, startTime, endTime).then(() => {
        flamegraphs.value.updateFlamegraphList();
        generateDisabled.value = true;
        flamegraphName.value = null;
        timeRangeLabel.value = null;
        timeRange = null;
    });
};

function calculateStartTime(start) {
    return [data.columns[start[0]], data.rows[start[1]]];
}

// Include the end cell to the generation of flamegraph
function calculateEndTime(end) {
    let bucketInMillis = data.rows[0] - data.rows[1];
    let endTimeMillis = data.rows[end[1]] + bucketInMillis;
    if (endTimeMillis >= 1000) {
        return [data.columns[end[0]] + 1, 0];
    } else {
        return [data.columns[end[0]], endTimeMillis];
    }
}

let chart = null;
let selectStart = null;
let selectEnd = null;

function select(samples, cell) {
    if (!selectStart) {
        selectStart = cell;
        chart.setHighlight([{ start: selectStart, end: selectStart }]);
    } else if (!selectEnd) {
        timeRange = [selectStart, cell];

        let startTime = calculateStartTime(timeRange[0]);
        let endTime = calculateEndTime(timeRange[1]);
        console.log(endTime)
        timeRangeLabel.value = assembleRangeLabel(startTime) + ' - ' + assembleRangeLabel(endTime);
        flamegraphName.value = generateFlamegraphName(selectStart, cell);
        generateDisabled.value = false;

        selectStart = null;
        selectEnd = null;
        chart.setHighlight([]);
    } else {
        selectStart = cell;
        selectEnd = null;
        chart.setHighlight([{ start: selectStart, end: selectStart }]);
    }

    chart.updateHighlight();
}

function hover(cell) {
    if (selectStart && !selectEnd) {
        if (cell[0] > selectStart[0]) {
            // column is higher
            chart.setHighlight([{ start: selectStart, end: cell }]);
            chart.updateHighlight();
        } else if (cell[0] === selectStart[0]) {
            // same column
            if (cell[1] < selectStart[1]) {
                // row is higher or equal
                chart.setHighlight([{ start: selectStart, end: cell }]);
                chart.updateHighlight();
            } else {
                chart.setHighlight([{ start: selectStart, end: selectStart }]);
                chart.updateHighlight();
            }
        } else {
            chart.setHighlight([{ start: selectStart, end: selectStart }]);
            chart.updateHighlight();
        }
    }
}

const jfrEventTypes = ref([
    {
        index: 0,
        label: 'Execution Samples (CPU)',
        code: 'jdk.ExecutionSample'
    },
    {
        index: 1,
        label: 'Allocations',
        code: 'jdk.ObjectAllocationInNewTLAB'
    },
    {
        index: 2,
        label: 'Locks',
        code: 'jdk.ThreadPark'
    },
    {
        index: 3,
        label: 'Live Objects',
        code: 'profiler.LiveObject'
    }
]);

const clickEventTypeSelected = () => {
    updateHeatmap(selectedEventType.value);
};
</script>

<template>
    <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected" optionLabel="label" :multiple="false" />

    <div id="chart" class="chart" style="overflow-x: auto"></div>
    <div style="overflow: hidden">
        <div id="legend" class="legend" style="float: right"></div>
        <div id="details" class="details" style="float: left"></div>
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
                <Button label="Show Flamegraph" style="color: white" class="p-button-success" @click="generateFlamegraph"
                        :disabled="generateDisabled || flamegraphName == null || flamegraphName.trim().length === 0"></Button>
            </div>
        </div>
    </div>

    <FlamegraphList ref="flamegraphs" />
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

<style scoped lang="scss">
@import '@/assets/heatmap/d3-heatmap2.css';
@import '@/assets/heatmap/bootstrap.min.css';
</style>

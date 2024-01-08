<script setup>
import { onMounted, ref } from 'vue';
import FormattingService from '@/service/FormattingService';
import HeatmapService from '@/service/HeatmapService';
import router from '@/router';
import SelectedProfileService from '@/service/SelectedProfileService';
import GenerateFlamegraphService from '@/service/FlamegraphService';

let timeRange;
const timeRangeLabel = ref(null);
const flamegraphs = ref(null);
const generateDisabled = ref(true);
const flamegraphName = ref(null);
let data = null;

onMounted(() => {
    GenerateFlamegraphService.list().then((json) => (flamegraphs.value = json));

    HeatmapService.getSingle().then((json) => {
        render(json);
        data = json;
    });
});

function assembleRangeLabelWithSamples(samples, i, j) {
    return assembleRangeLabel(i, j) + ', samples: ' + samples;
}

function assembleRangeLabel(i, j) {
    return 'seconds: ' + data.columns[i] + ' millis: ' + data.rows[j];
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

const selectFlamegraph = (flamegraph) => {
    router.push({ name: 'flamegraph-show', params: { flamegraphFile: flamegraph.filename } });
};

function generateFlamegraphName(start, end) {
    const currentProfile = SelectedProfileService.profile.value;
    let startTime = [data.columns[start[0]], data.rows[start[1]]];
    let endTime = [data.columns[end[0]], data.rows[end[1]]];
    return currentProfile.replace('.jfr', '') + '-' + startTime[0] + '-' + startTime[1] + '-' + endTime[0] + '-' + endTime[1];
}
const generateFlamegraph = () => {
    let start = timeRange[0];
    let end = timeRange[1];
    let startTime = [data.columns[start[0]], data.rows[start[1]]];
    let endTime = [data.columns[end[0]], data.rows[end[1]]];

    GenerateFlamegraphService.generateRange(flamegraphName.value, 'EXECUTION_SAMPLE', startTime, endTime).then((json) => {
        flamegraphs.value = json;
        generateDisabled.value = true;
        flamegraphName.value = null;
        timeRangeLabel.value = null;
        timeRange = null;
    });
};

let chart = null;
let selectStart = null;
let selectEnd = null;

function select(samples, cell) {
    if (!selectStart) {
        selectStart = cell;
        chart.setHighlight([{ start: selectStart, end: selectStart }]);
    } else if (!selectEnd) {
        timeRange = [selectStart, cell];
        timeRangeLabel.value = assembleRangeLabel(selectStart[0], selectStart[1]) + ' - ' + assembleRangeLabel(cell[0], cell[1]);
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
</script>

<template>
    <div id="chart" class="chart" style="overflow-x: auto"></div>
    <div style="overflow: hidden">
        <div id="legend" class="legend" style="float: right"></div>
        <div id="details" class="details" style="float: left"></div>
    </div>
    <hr />
    &nbsp;

    <div class="card p-fluid">
        <h5 v-if="!generateDisabled">
            Generate a flamegraph for a time-range: <span style="color: blue">{{ timeRangeLabel }}</span>
        </h5>
        <h5 v-else>No time-range selected</h5>

        <div class="field">
            <label for="filename" class="p-sr-only">Flamegraph Name</label>
            <InputText id="filename" type="text" placeholder="Flamegraph Name" v-model="flamegraphName" :disabled="generateDisabled" />
        </div>
        <Button label="Generate Flamegraph" style="color: white" @click="generateFlamegraph" :disabled="generateDisabled || flamegraphName == null || flamegraphName.trim().length === 0"></Button>
    </div>

    <div class="grid">
        <div class="col-12">
            <div class="card">
                <DataTable
                    ref="dt"
                    :value="flamegraphs"
                    dataKey="id"
                    :paginator="true"
                    :rows="10"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll"
                >
                    <Column field="code" header="Name" headerStyle="width:60%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.filename }}
                        </template>
                    </Column>
                    <Column field="name" header="Date" headerStyle="width:15%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Date</span>
                            {{ slotProps.data.dateTime }}
                        </template>
                    </Column>
                    <Column header="Size" headerStyle="width:10%; min-width:15rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Size</span>
                            {{ FormattingService.formatBytes(slotProps.data.sizeInBytes) }}
                        </template>
                    </Column>
                    <Column headerStyle="min-width:10rem;">
                        <template #body="slotProps">
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2" @click="selectFlamegraph(slotProps.data)" />
                            &nbsp;
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2" @click="confirmDeleteProduct(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>

                <!--                <Dialog v-model:visible="deleteProductDialog" :style="{ width: '450px' }" header="Confirm"-->
                <!--                        :modal="true">-->
                <!--                    <div class="flex align-items-center justify-content-center">-->
                <!--                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />-->
                <!--                        <span v-if="profile">Are you sure you want to delete <b>{{ profile.name }}</b>?</span>-->
                <!--                    </div>-->
                <!--                    <template #footer>-->
                <!--                        <Button label="No" icon="pi pi-times" class="p-button-text"-->
                <!--                                @click="deleteProductDialog = false" />-->
                <!--                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProduct" />-->
                <!--                    </template>-->
                <!--                </Dialog>-->
            </div>
        </div>
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

<style scoped lang="scss">
@import '@/assets/heatmap/d3-heatmap2.css';
@import '@/assets/heatmap/bootstrap.min.css';
</style>

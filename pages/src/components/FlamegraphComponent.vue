<script setup>
import FlamegraphService from '@/service/FlamegraphService';
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useToast } from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['profileId', 'flamegraphId', 'eventType']);

const toast = useToast();
const searchValue = ref(null);
const matchedValue = ref(null);
let flamegraph = null;

let profileId, flamegraphId, eventType;

function onResize({ width, height }) {
    let w = document.getElementById("flamegraphCanvas")
        .parentElement.clientWidth

    // minus padding
    flamegraph.resizeCanvas(w - 50);
}

onMounted(() => {
    profileId = props.profileId;
    flamegraphId = props.flamegraphId;
    eventType = props.eventType;

    let request = null;
    if (flamegraphId != null) {
        request = FlamegraphService.getById(profileId, flamegraphId);
    } else if (eventType != null) {
        request = FlamegraphService.getByEventType(profileId, eventType);
    } else {
        console.error('FlamegraphID or EventType needs to be propagated to load the flamegraph');
        return;
    }

    request.then((data) => {
        flamegraph = new Flamegraph(data, 'flamegraphCanvas');
        flamegraph.drawRoot();
    });

    MessageBus.on(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED, (type) => {
        eventType = type;

        FlamegraphService.getByEventType(profileId, eventType)
            .then((data) => {
                flamegraph = new Flamegraph(data, 'flamegraphCanvas');
                flamegraph.drawRoot();
            });
    });

    MessageBus.on(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED, (timeRange) => {
        FlamegraphService.generateRange(profileId, null, eventType, timeRange)
            .then((data) => {
                flamegraph = new Flamegraph(data, 'flamegraphCanvas');
                flamegraph.drawRoot();
            });
    });
});

onBeforeUnmount(() => {
    MessageBus.off(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED);
    MessageBus.off(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED);
});

function search() {
    const matched = flamegraph.search(searchValue.value);
    searchValue.value = null;
    matchedValue.value = 'Matched: ' + matched + '%';
}

const exportFlamegraph = () => {
    let request = null;
    if (flamegraphId != null) {
        request = FlamegraphService.exportById(profileId, flamegraphId);
    } else if (eventType != null) {
        request = FlamegraphService.exportByEventType(profileId, eventType);
    } else {
        console.error('FlamegraphID or EventType needs to be propagated to load the flamegraph');
        return;
    }

    request.then(() => {
        toast.add({ severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000 });
    });
};
</script>

<template>
    <!--  resizes Canvas according to parent component to avoid sending message from parent to child component  -->
    <div v-resize="onResize" style="text-align: left; padding-bottom: 10px;padding-top: 10px">
        <div class="grid">
            <div class="col-2">
                <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
                        @click="flamegraph.resetZoom()" />&nbsp;
                <Button id="reverse" icon="pi pi-arrows-v" class="p-button-filled p-button-info mt-2"
                        @click="flamegraph.reverse()" title="Reverse" />&nbsp;
                <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2"
                        @click="exportFlamegraph()" title="Export" />
            </div>
            <div id="search_output" class="col-2 col-offset-3 relative">
                <Button class="p-button-help mt-2 absolute right-0 font-bold" outlined severity="help"
                        @click="flamegraph.resetSearch(); matchedValue = null" v-if="matchedValue != null"
                        title="Reset Search">{{ matchedValue }}
                </Button>
            </div>
            <div class="col-5 p-inputgroup" style="float: right">
                <Button class="p-button-info mt-2" label="Search" @click="search()" />
                <InputText v-model="searchValue" @keydown.enter="search" placeholder="Pattern" class="mt-2" />
            </div>
        </div>
    </div>

    <canvas id="flamegraphCanvas" style="width: 100%"></canvas>
    <Toast />
</template>

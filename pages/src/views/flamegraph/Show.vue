<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import { useToast } from 'primevue/usetoast';
import TimeseriesService from '@/service/TimeseriesService';
import TimeseriesGraph from '@/service/TimeseriesGraph';
import MessageBus from '@/service/MessageBus';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';

const router = useRouter();
const route = useRoute();
const selectedEventType = ref(null);
const jfrEventTypes = ref(GlobalVars.jfrTypes());
const toast = useToast();
let timeseries = null;

const updateFlamegraphByTimeseries = (chartContext, { xaxis, yaxis }) => {
    const timeRange = {
        start: Math.floor(xaxis.min),
        end: Math.ceil(xaxis.max)
    };
    MessageBus.emit(MessageBus.FLAMEGRAPH_TIMESERIES_RANGE_CHANGED, timeRange);
};

const updateTimeseries = (eventType) => {
    TimeseriesService.generate(route.query.profileId, eventType)
        .then((data) => {
            if (timeseries == null) {
                timeseries = new TimeseriesGraph('timeseries', data, updateFlamegraphByTimeseries);
                timeseries.render();
            } else {
                timeseries.update(data);
            }
        });
};

const resetTimeseriesZoom = () => {
    timeseries.resetZoom();
    updateFlamegraph(selectedEventType.value.code);
};

function updateFlamegraph(eventType) {
    MessageBus.emit(MessageBus.FLAMEGRAPH_EVENT_TYPE_CHANGED, eventType);
}

onMounted(() => {
    selectedEventType.value = GlobalVars.eventTypeByCode(route.query.eventType);

    updateTimeseries(selectedEventType.value.code);
});

const clickEventTypeSelected = () => {
    router.push({
        name: 'flamegraph-show',
        query: {
            mode: 'predefined',
            profileId: route.query.profileId,
            eventType: selectedEventType.value.code
        }
    });

    updateFlamegraph(selectedEventType.value.code);
    updateTimeseries(selectedEventType.value.code);
};
</script>

<template>
    <div class="card card-w-title" style="padding: 20px 25px 25px;">
        <SelectButton style="padding-bottom: 25px;" v-model="selectedEventType" :options="jfrEventTypes"
                      @click="clickEventTypeSelected"
                      optionLabel="label" :multiple="false" />

        <Button icon="pi pi-home" class="p-button-filled p-button-info mt-2" title="Reset Zoom"
                @click="resetTimeseriesZoom()" />

        <div id="timeseries"></div>

        <FlamegraphComponent :profileId="route.query.profileId" :eventType="route.query.eventType" />
    </div>

    <Toast />
</template>

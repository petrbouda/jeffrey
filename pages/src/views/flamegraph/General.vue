<script setup>
import { ref } from 'vue';
import FlamegraphList from '@/components/FlamegraphList.vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const flamegraphs = ref(null);
const selectedEventType = ref(0);

const clickEventTypeSelected = () => {
    router.push({ name: 'flamegraph-show', query: { mode: 'predefined', eventType: selectedEventType.value.index } });
};

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
</script>

<template>
    <div class="card">
        <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                      optionLabel="label" :multiple="false" />
    </div>

    <div class="card card-w-title">
        <h5>Generate Flamegraph</h5>
        <div class="grid p-fluid mt-3">
            <div class="field col-12 md:col-6">
                <span class="p-float-label">
                    <Textarea inputId="textarea" rows="10" cols="30" v-model="value10"></Textarea>
                    <label for="textarea">Insert JFR SQL</label>
                </span>
                <span class="p-float-label">
                    <InputText type="text" id="profilename" v-model="value1" />
                    <label for="profilename">Profile's name</label>
                </span>
            </div>
        </div>
    </div>

    <FlamegraphList ref="flamegraphs" />
</template>

<style scoped lang="scss"></style>

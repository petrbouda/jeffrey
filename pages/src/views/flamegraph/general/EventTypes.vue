<script setup>
import { ref } from 'vue';
import FlamegraphService from '@/service/FlamegraphService';

const graphTypes = ref([
    { name: 'Execution Samples (CPU)', code: 'EXECUTION_SAMPLES' },
    { name: 'Allocations', code: 'ALLOCATIONS' },
    { name: 'Locks', code: 'LOCKS' },
    { name: 'Live Objects', code: 'LIVE_OBJECTS' }
]);
const selectedTypes = ref(null);

const emitFlamegraphs = defineEmits(['flamegraph-change']);

function propagateToParent(data) {
    emitFlamegraphs('flamegraph-change', data);
}

const generateFlamegraphs = () => {
    FlamegraphService.generate(selectedTypes.value)
        .then((data) => {
            propagateToParent(data);
            selectedTypes.value = null
        });
};
</script>

<template>
    <div class="field col-12 md:col-4">
        <span class="p-float-label">
            <MultiSelect id="multiselect" :options="graphTypes" v-model="selectedTypes" optionLabel="label"
                         :filter="false"></MultiSelect>
            <label for="multiselect">Choose event types</label>
        </span>
    </div>
    <div class="field col-12 md:col-2">
        <Button label="Generate" @click="generateFlamegraphs()" class="mr-2 mb-2"></Button>
    </div>
</template>

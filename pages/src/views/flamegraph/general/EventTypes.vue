<script setup>
import { ref } from 'vue';
import GenerateFlamegraphService from '@/service/FlamegraphService';

const graphTypes = ref([
    { name: 'Execution Samples', code: 'EXECUTION_SAMPLE' },
    { name: 'Allocations', code: 'ALLOCATION' },
    { name: 'Locks', code: 'LOCK' },
    { name: 'Live Objects', code: 'LIVE_OBJECT' }
]);
const selectedTypes = ref(null);

const generateFlamegraphs = () => {
    GenerateFlamegraphService.generate(selectedTypes.value);
    // graphTypes.value = "";
};
</script>

<template>
    <div class="field col-12 md:col-4">
        <span class="p-float-label">
            <MultiSelect id="multiselect" :options="graphTypes" v-model="selectedTypes" optionLabel="name" :filter="false"></MultiSelect>
            <label for="multiselect">Choose event types</label>
        </span>
    </div>
    <div class="field col-12 md:col-2">
        <Button label="Generate" @click="generateFlamegraphs()" class="mr-2 mb-2"></Button>
    </div>
</template>

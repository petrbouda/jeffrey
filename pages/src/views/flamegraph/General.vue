<script setup>
import { ref } from 'vue';
import FlamegraphList from '@/components/FlamegraphList.vue';

const flamegraphs = ref(null);

const flamegraphsGenerated = () => {
    flamegraphs.value.updateFlamegraphList();
};

const nestedRouteItems = ref([
    {
        label: 'Event Types',
        to: '/flamegraph/general'
    },
    {
        label: 'JFR SQL',
        to: '/flamegraph/general/sql'
    }
]);
</script>

<template>
    <div class="card card-w-title">
        <h5>Generate Flamegraph</h5>
        <TabMenu :model="nestedRouteItems" />
        <div class="grid p-fluid mt-3">
            <router-view @flamegraph-change="flamegraphsGenerated" />
        </div>
    </div>

    <FlamegraphList ref="flamegraphs" />
</template>

<style scoped lang="scss"></style>

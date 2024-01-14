<script setup>

import AddScripts from '@/components/AddScripts.vue';

const scripts = [
    "js/heatmap/d3-heatmap2.js",
    "js/heatmap/d3.js",
]

</script>

<template>
    <add-scripts :scripts="scripts"></add-scripts>

    <router-view />
</template>

<style scoped></style>

<script setup>
import { onMounted, ref } from 'vue';
import FlamegraphService from '@/service/FlamegraphService';
import { useRoute } from 'vue-router';

const htmlFileText = ref('');

onMounted(() => {
    const route = useRoute();
    let filename = route.params.flamegraphFile;
    FlamegraphService.getSingle(filename)
        .then((data) => htmlFileText.value = data)
});
</script>

<template>
    <div v-html="htmlFileText"></div>
</template>

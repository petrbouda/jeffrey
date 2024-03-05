<script setup>
import { useRoute } from 'vue-router';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';

const route = useRoute();
</script>

<template>
    <div class="card card-w-title" style="padding: 20px 25px 25px;">
        <FlamegraphComponent :profileId="route.query.profileId" :flamegraphId="route.query.flamegraphId" />
    </div>
</template>

<script setup>
import {useRoute} from 'vue-router';
import SimpleFlamegraphComponent from "@/components/SimpleFlamegraphComponent.vue";

const route = useRoute();
</script>

<template>
  <div class="card card-w-title" style="padding: 20px 25px 25px;">
    <SimpleFlamegraphComponent :profileId="route.query.profileId" :flamegraphId="route.query.flamegraphId"/>
  </div>
</template>

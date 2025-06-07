<template>
  <div>
    <DashboardHeader
      title="HTTP Request / Response"
      icon="globe"
    />
    <div class="p-4 text-center">
      <h3 class="text-muted">HTTP Analysis</h3>
      <p class="text-muted">HTTP connections monitoring and analysis</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import DashboardHeader from '@/components/DashboardHeader.vue';

// Props definition
const props = defineProps({
  profile: {
    type: Object,
    required: true
  },
  secondaryProfile: {
    type: Object,
    default: null
  }
});
</script>

<style scoped>
</style>

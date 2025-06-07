<template>
  <div>
    <DashboardHeader
      title="JDBC Statements"
      icon="database"
    />
    <div class="p-4 text-center">
      <h3 class="text-muted">JDBC Analysis</h3>
      <p class="text-muted">Database connections and query monitoring</p>
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

<template>
  <div class="p-4 text-center">
    <h3 class="text-muted">JDBC Analysis</h3>
    <p class="text-muted">Database connections and query monitoring</p>
  </div>
</template>

<script setup lang="ts">
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

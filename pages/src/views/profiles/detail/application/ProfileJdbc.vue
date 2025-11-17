<template>
  <div>
    <PageHeader
      title="JDBC Statements"
      icon="bi-database"
    >
    <div class="p-4 text-center">
      <h3 class="text-muted">JDBC Analysis</h3>
      <p class="text-muted">Database connections and query monitoring</p>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue';

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

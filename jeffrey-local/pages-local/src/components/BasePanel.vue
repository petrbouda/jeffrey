<template>
  <div class="base-panel">
    <h6 class="base-panel-title">{{ title }}</h6>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
}>();
</script>

<style scoped>
.base-panel {
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 0;
  padding: 1rem 1.25rem;
  height: 100%;
}

.base-panel-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.75rem;
}
</style>

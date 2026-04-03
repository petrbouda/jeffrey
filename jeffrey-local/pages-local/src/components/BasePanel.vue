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
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-lg);
  box-shadow: var(--shadow-base);
  padding: 1rem 1.25rem;
  height: 100%;
}

.base-panel-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 0.75rem;
}
</style>

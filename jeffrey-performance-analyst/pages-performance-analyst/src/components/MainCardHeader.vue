<template>
  <div class="page-header">
    <div class="page-header-info">
      <i :class="['page-header-icon', icon]"></i>
      <span class="page-header-title">{{ title }}</span>
      <span v-if="badge != null && badge > 0" class="page-header-badge">{{ badge }}</span>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import '@/styles/shared-components.css';

defineProps<{
  icon: string;
  title: string;
  badge?: number;
}>();
</script>

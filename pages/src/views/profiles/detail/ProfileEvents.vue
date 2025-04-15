<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="events-title">
        <i class="bi bi-collection me-2"></i>
        Events
      </h2>
      <p class="text-muted fs-6">View and analyze profile events</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from 'vue';
import { Profile } from '@/types';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();
</script>

<style scoped>
.events-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .events-title {
    font-size: 1.5rem;
  }
}
</style>
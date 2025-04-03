<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading profile information...</p>
  </div>
  
  <div v-else class="profile-information">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">Profile Information</h5>
      </div>
      <div class="card-body">
        <p class="text-muted">This page will display detailed information about the profile.</p>
      </div>
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
.profile-information .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}
</style>
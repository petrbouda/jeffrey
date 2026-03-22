<template>
  <div class="card h-100">
    <div class="card-body">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h5 class="card-title mb-0 text-primary">{{ profile.name }}</h5>
        <div v-if="!profile.enabled" class="status-indicator initializing">
          <div class="spinner-border spinner-border-sm text-warning me-1" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span class="text-muted small">Initializing</span>
        </div>
        <span v-else class="badge bg-success">Ready</span>
      </div>
      
      <div class="d-flex align-items-center mb-3">
        <div class="text-muted small">
          <i class="bi bi-calendar3 me-1"></i>
          {{ formatDate(profile.createdAt) }}
        </div>
      </div>
      
      <div class="d-flex gap-2 mt-3">
        <button 
          class="btn btn-sm btn-primary flex-grow-1"
          :disabled="!profile.enabled"
          @click="$emit('select', profile)">
          <template v-if="!profile.enabled">
            <span class="spinner-border spinner-border-sm me-1" role="status"></span>
            Initializing...
          </template>
          <template v-else>
            <i class="bi bi-play-fill me-1"></i>
            Select
          </template>
        </button>
        <button 
          class="btn btn-sm btn-outline-secondary"
          @click="$emit('edit', profile)">
          <i class="bi bi-pencil"></i>
        </button>
        <button 
          class="btn btn-sm btn-outline-danger"
          @click="$emit('delete', profile)">
          <i class="bi bi-trash"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineProps, defineEmits } from 'vue';

interface Profile {
  id: string;
  name: string;
  createdAt: string;
  enabled: boolean;
}

const props = defineProps<{
  profile: Profile
}>();

defineEmits(['select', 'edit', 'delete']);

// No longer needed as we're using inline templates

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};
</script>

<style scoped>
.card {
  transition: transform 0.2s, box-shadow 0.2s;
  
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
  }
}

.status-indicator {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 4px;
  background-color: rgba(255, 193, 7, 0.1);
}

.status-indicator.initializing {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.7;
  }
}
</style>
<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="flamegraphs-saved-title">
        <i class="bi bi-bookmark me-2"></i>
        Saved Flamegraphs
      </h2>
      <p class="text-muted fs-6">View and manage your saved flamegraphs</p>
    </div>

    <!-- Placeholder content - empty state -->
    <div class="empty-state">
      <div class="card">
        <div class="card-body text-center py-5">
          <i class="bi bi-bookmark-star fs-1 text-muted mb-3"></i>
          <h5>No saved flamegraphs yet</h5>
          <p class="text-muted">Saved flamegraphs will appear here after you save them from any flamegraph view.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

// Define props
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

// Component state
const savedFlamegraphs = ref([]);
</script>

<style scoped>
.flamegraphs-saved-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.empty-state .card {
  border: none;
  border-radius: 0.5rem;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.empty-state .bi {
  display: block;
  margin: 0 auto 1rem;
}
</style>

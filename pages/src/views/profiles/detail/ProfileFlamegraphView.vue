<template>
  <div class="flamegraph-view-container">
    <h4 class="mb-4">Flamegraph Visualization</h4>
    
    <div class="card shadow-sm mb-4">
      <div class="card-body">
        <h5 class="card-title mb-3">Parameters</h5>
        <div class="row">
          <div class="col-md-3">
            <div class="mb-3">
              <div class="small text-muted mb-1">Project ID</div>
              <div class="fw-bold">{{ projectId || 'Not provided' }}</div>
            </div>
          </div>
          <div class="col-md-3">
            <div class="mb-3">
              <div class="small text-muted mb-1">Profile ID</div>
              <div class="fw-bold">{{ profileId || 'Not provided' }}</div>
            </div>
          </div>
          <div class="col-md-2">
            <div class="mb-3">
              <div class="small text-muted mb-1">Type</div>
              <div class="fw-bold">{{ type || 'Not provided' }}</div>
            </div>
          </div>
          <div class="col-md-2">
            <div class="mb-3">
              <div class="small text-muted mb-1">Source</div>
              <div class="fw-bold">{{ source || 'Not provided' }}</div>
            </div>
          </div>
          <div class="col-md-2">
            <div class="mb-3">
              <div class="small text-muted mb-1">Mode</div>
              <div class="fw-bold">{{ mode || 'Not provided' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="card shadow-sm">
      <div class="card-body">
        <div class="text-center py-5">
          <i class="bi bi-braces-asterisk display-1 text-primary mb-3"></i>
          <h5>Flamegraph Placeholder</h5>
          <p class="text-muted">This is where the actual flamegraph visualization would be rendered.</p>
          <p class="fw-bold">Parameters received:</p>
          <div class="d-inline-block text-start">
            <ul class="mb-0">
              <li><strong>Project ID:</strong> {{ projectId || 'Not provided' }}</li>
              <li><strong>Profile ID:</strong> {{ profileId || 'Not provided' }}</li>
              <li><strong>Type:</strong> {{ type || 'Not provided' }}</li>
              <li><strong>Source:</strong> {{ source || 'Not provided' }}</li>
              <li><strong>Mode:</strong> {{ mode || 'Not provided' }}</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
    
    <div class="mt-3">
      <button class="btn btn-outline-secondary" @click="goBack">
        <i class="bi bi-arrow-left me-1"></i> Back to Profiles
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { computed } from 'vue';

const route = useRoute();
const router = useRouter();

// Extract route parameters
const projectId = computed(() => route.params.projectId as string);
const profileId = computed(() => route.params.profileId as string);

// Extract query parameters
const type = computed(() => route.query.type as string);
const source = computed(() => route.query.source as string);
const mode = computed(() => route.query.mode as string);

// Navigation function
const goBack = () => {
  router.back();
};
</script>

<style scoped>
.flamegraph-view-container .card {
  border: none;
  border-radius: 0.5rem;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075) !important;
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #495057;
}

.display-1 {
  font-size: 4rem;
}

.fw-bold {
  font-weight: 600 !important;
}

.small {
  font-size: 0.875rem;
}

.text-muted {
  color: #6c757d !important;
}
</style>
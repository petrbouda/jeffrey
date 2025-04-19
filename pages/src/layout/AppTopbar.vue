<template>
  <nav class="navbar navbar-light bg-white sticky-top navbar-expand-lg navbar-glass-shadow">
    <div class="container-fluid">
      <button class="navbar-toggler btn-sm border-0 me-2" type="button" @click="toggleSidebar">
        <span class="navbar-toggler-icon"></span>
      </button>
      
      <a class="navbar-brand d-flex align-items-center py-0" href="/">
        <div class="d-flex align-items-center">
          <img src="/jeffrey_small.png" alt="Jeffrey Logo" height="45" class="me-2 logo-image">
          <div class="d-flex flex-column">
            <span class="fs-4 fw-bold text-primary">Jeffrey</span>
            <span class="navbar-subtitle d-none d-md-inline text-secondary" style="font-size: 0.7rem;">JDK Flight Recorder Analysis</span>
          </div>
        </div>
      </a>
      
      <div class="d-flex align-items-center ms-auto">
        
        <!-- Back to Profiles button (only shown on profile pages) -->
        <router-link v-if="isProfilePage" 
                    :to="`/projects/${projectId}/profiles`" 
                    class="btn btn-phoenix-primary me-2">
          <i class="bi bi-arrow-left me-1"></i>
          <span class="d-none d-md-inline">Back to Profiles</span>
        </router-link>
        
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

// Check if current route is a profile detail page
const isProfilePage = computed(() => {
  return route.meta.layout === 'profile';
});

// Get project ID from route params
const projectId = computed(() => {
  return route.params.projectId;
});

const toggleSidebar = () => {
  if (window.toggleSidebar) {
    window.toggleSidebar();
  }
};
</script>

<style scoped>
.navbar-brand {
  padding: 0;
  margin-right: 2rem;
}

.navbar-brand img {
  max-height: 45px;
}

.navbar-subtitle {
  opacity: 0.8;
  line-height: 1;
}

.logo-image {
  border-radius: 8px;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.logo-image:hover {
  transform: scale(1.05);
  box-shadow: 0 0.25rem 0.5rem rgba(0, 0, 0, 0.15);
}

.navbar {
  z-index: 1040;
  box-shadow: 0 0.25rem 0.375rem -0.0625rem rgba(0, 0, 0, 0.1), 0 0.125rem 0.25rem -0.0625rem rgba(0, 0, 0, 0.06);
  height: 64px;
  padding-top: 0.5rem;
  padding-bottom: 0.5rem;
}

.navbar-glass-shadow {
  border-bottom: 1px solid #eaedf1;
}

/* Buttons */
.btn-phoenix-primary {
  color: #5e64ff;
  background-color: #eaebff;
  border-color: transparent;
}

.btn-phoenix-primary:hover {
  color: #fff;
  background-color: #5e64ff;
}

/* Avatar styles */
.avatar {
  width: 2rem;
  height: 2rem;
  position: relative;
  display: inline-block;
}

.avatar-s {
  width: 1.75rem;
  height: 1.75rem;
  font-size: 0.75rem;
}

.avatar-l {
  width: 2.5rem;
  height: 2.5rem;
  font-size: 1rem;
}

.fs-7 {
  font-size: 0.75rem !important;
}

.dropdown-toggle::after {
  display: none;
}

/* Notification dropdown */
.dropdown-menu-card {
  padding: 0;
  overflow: hidden;
}

.dropdown-menu-card .card {
  box-shadow: none;
  margin-bottom: 0;
}

.bg-soft-primary {
  background-color: rgba(94, 100, 255, 0.1) !important;
}

.bg-soft-warning {
  background-color: rgba(245, 128, 62, 0.1) !important;
}
</style>
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
        <!-- Search icon -->
        <button class="btn btn-link text-secondary px-2 me-1">
          <i class="bi bi-search fs-5"></i>
        </button>
        
        <!-- Notifications -->
        <div class="dropdown me-2">
          <button class="btn btn-link text-secondary px-2 position-relative" id="notificationsDropdown" data-bs-toggle="dropdown">
            <i class="bi bi-bell fs-5"></i>
            <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">
              2
            </span>
          </button>
          <div class="dropdown-menu dropdown-menu-end dropdown-menu-card" style="width: 320px">
            <div class="card">
              <div class="card-header d-flex justify-content-between align-items-center">
                <h6 class="mb-0">Notifications</h6>
                <a href="#" class="text-decoration-none small">Mark all as read</a>
              </div>
              <div class="card-body p-0">
                <div class="list-group list-group-flush">
                  <a href="#" class="list-group-item list-group-item-action px-3 py-2">
                    <div class="d-flex">
                      <div class="avatar avatar-s bg-soft-primary me-2 rounded-circle d-flex align-items-center justify-content-center">
                        <i class="bi bi-graph-up text-primary"></i>
                      </div>
                      <div class="flex-1">
                        <p class="mb-0 fs-7">New profile analysis complete</p>
                        <p class="text-muted mb-0 fs-7">5 minutes ago</p>
                      </div>
                    </div>
                  </a>
                  <a href="#" class="list-group-item list-group-item-action px-3 py-2">
                    <div class="d-flex">
                      <div class="avatar avatar-s bg-soft-warning me-2 rounded-circle d-flex align-items-center justify-content-center">
                        <i class="bi bi-exclamation-triangle text-warning"></i>
                      </div>
                      <div class="flex-1">
                        <p class="mb-0 fs-7">Memory usage alert</p>
                        <p class="text-muted mb-0 fs-7">20 minutes ago</p>
                      </div>
                    </div>
                  </a>
                </div>
              </div>
              <div class="card-footer text-center p-2">
                <a href="#" class="text-decoration-none">View all</a>
              </div>
            </div>
          </div>
        </div>
        
        <!-- Back to Profiles button (only shown on profile pages) -->
        <router-link v-if="isProfilePage" 
                    :to="`/projects/${projectId}/profiles`" 
                    class="btn btn-phoenix-primary me-2">
          <i class="bi bi-arrow-left me-1"></i>
          <span class="d-none d-md-inline">Back to Profiles</span>
        </router-link>
        
        <!-- User dropdown -->
        <div class="dropdown">
          <button class="btn p-0 dropdown-toggle" type="button" id="userDropdown" data-bs-toggle="dropdown" aria-expanded="false">
            <div class="avatar avatar-l rounded-circle border border-2 border-white bg-soft-primary d-flex align-items-center justify-content-center">
              <span class="text-primary">A</span>
            </div>
          </button>
          <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="userDropdown">
            <li class="px-3 py-2 d-flex flex-column">
              <span class="fw-bold">Admin User</span>
              <span class="text-muted fs-7">admin@example.com</span>
            </li>
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item" href="#"><i class="bi bi-person me-2"></i>Profile</a></li>
            <li><a class="dropdown-item" href="#"><i class="bi bi-gear me-2"></i>Settings</a></li>
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item" href="#"><i class="bi bi-box-arrow-right me-2"></i>Sign out</a></li>
          </ul>
        </div>
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
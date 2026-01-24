<template>
  <div class="w-100 bg-light">
    <!-- Header -->
    <div class="bg-white border-bottom py-4 mb-0 shadow-sm">
      <div class="container-fluid">
        <div class="d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center">
            <img src="/jeffrey_small.png" alt="Jeffrey Logo" height="60" class="me-3 header-logo">
            <div>
              <h1 class="mb-0 text-primary fw-bold">Jeffrey</h1>
              <p class="text-muted mb-0">JDK Flight Recorder Analysis Tool</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <MainNavigation />

    <!-- Main Content -->
    <div class="container-fluid px-4">
      <router-view />
    </div>

  </div>
</template>

<script setup lang="ts">
import MainNavigation from '@/components/MainNavigation.vue';
</script>

<style scoped>
.header-logo {
  border-radius: 8px;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease;
}

.header-logo:hover {
  transform: scale(1.05);
}
</style>

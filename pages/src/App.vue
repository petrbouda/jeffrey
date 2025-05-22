<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import ToastService from './services/ToastService';

onMounted(() => {
  // Initialize Bootstrap tooltips
  if (window.bootstrap && window.bootstrap.Tooltip) {
    const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    [...tooltipTriggerList].map(tooltipTriggerEl => 
      new window.bootstrap.Tooltip(tooltipTriggerEl)
    );
  }
});
</script>

<style>
/* Global styles */
.app-container {
  min-height: 100vh;
  background-color: #f8f9fa;
}

/* Custom scrollbar */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
}

::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}
</style>

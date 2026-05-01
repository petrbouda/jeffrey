<template>
  <div class="main-container">
    <AppTopbar />
    <div class="content-container">
      <div class="w-100">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppTopbar from './AppTopbar.vue';
</script>

<style lang="scss" scoped>
.main-container {
  display: flex;
  flex-direction: column;
}

.content-container {
  min-height: calc(100vh - 72px); // Hero topbar ≈ 66px tall + 6px buffer
  background-color: var(--color-bg-body);
  padding: 1rem;
}
</style>

<template>
  <div class="main-container">
    <AppTopbar />
    
    <!-- Only show sidebar for non-profile layouts -->
    <div class="d-flex vh-100">
      <div class="sidebar-container" v-show="sidebarActive && !isProfileLayout">
        <AppSidebar />
      </div>
      
      <div class="content-container flex-grow-1">
        <div class="w-100">
          <router-view />
        </div>
      </div>
    </div>
    
    <div v-if="sidebarActive && !isProfileLayout" class="sidebar-overlay d-md-none" @click="sidebarActive = false"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue';
import { useRoute } from 'vue-router';
import AppTopbar from './AppTopbar.vue';
import AppSidebar from './AppSidebar.vue';

const route = useRoute();
const sidebarActive = ref(true);
const outsideClickListener = ref<EventListener | null>(null);

// Check if current route is a profile or project detail page
const isProfileLayout = computed(() => {
  return route.meta.layout === 'profile' || route.meta.layout === 'project';
});

// Hide sidebar on mobile by default
onMounted(() => {
  if (window.innerWidth < 768) {
    sidebarActive.value = false;
  }
  
  bindOutsideClickListener();
  
  // Make sidebar toggle accessible to Topbar component
  window.toggleSidebar = () => {
    sidebarActive.value = !sidebarActive.value;
  };
});

onBeforeUnmount(() => {
  unbindOutsideClickListener();
});

const bindOutsideClickListener = () => {
  if (!outsideClickListener.value) {
    outsideClickListener.value = (event: Event) => {
      if (window.innerWidth < 768 && isOutsideClicked(event)) {
        sidebarActive.value = false;
      }
    };
    document.addEventListener('click', outsideClickListener.value);
  }
};

const unbindOutsideClickListener = () => {
  if (outsideClickListener.value) {
    document.removeEventListener('click', outsideClickListener.value);
    outsideClickListener.value = null;
  }
};

const isOutsideClicked = (event: Event) => {
  const target = event.target as HTMLElement;
  const sidebarEl = document.querySelector('.sidebar');
  const topbarEl = document.querySelector('.navbar-toggler');
  
  if (!sidebarEl || !topbarEl) return false;
  
  return !(
    sidebarEl.contains(target) || 
    topbarEl.contains(target) || 
    sidebarEl === target || 
    topbarEl === target
  );
};
</script>

<style lang="scss" scoped>
.main-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.sidebar-container {
  width: 280px;
  min-width: 280px;
  max-width: 280px;
  flex: 0 0 280px;
  z-index: 1030;
  background-color: #fff;
  border-right: 1px solid #eaedf1;
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
}

.content-container {
  min-height: calc(100vh - 64px); // Adjust based on navbar height
  background-color: #edf2f9;
  padding: 1rem;
}

.sidebar-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1025;
}

@media (max-width: 991.98px) {
  .sidebar-container {
    position: fixed;
    left: 0;
    top: 64px; // Adjust based on navbar height
    height: calc(100vh - 64px);
    box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
  }
  
  .content-container {
    padding: 1rem;
  }
}
</style>

<script lang="ts">
// Declare global toggle function for sidebar
declare global {
  interface Window {
    toggleSidebar: () => void;
  }
}
</script>

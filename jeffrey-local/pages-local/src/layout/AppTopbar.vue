<template>
  <nav class="topbar">
    <!-- Decorative circle -->
    <div class="topbar-decoration"></div>

    <div class="container-fluid d-flex align-items-center" style="height: 100%">
      <button v-if="!isProfilePage" class="topbar-toggle me-2" type="button" @click="toggleSidebar">
        <i class="bi bi-list"></i>
      </button>

      <a class="topbar-brand" href="/">
        <img src="/jeffrey-icon.svg" alt="Jeffrey" class="topbar-logo" />
        <span class="topbar-title">Jeffrey</span>
        <span class="topbar-subtitle d-none d-md-inline">JDK Flight Recorder Analysis</span>
        <span v-if="version" class="topbar-version">{{ version }}</span>
      </a>

      <div class="d-flex align-items-center ms-auto">
        <!-- Back to Profiles/Home button (only shown on profile pages) -->
        <button
          v-if="isProfilePage"
          class="topbar-back-btn me-2"
          @click="
            isQuickAnalysisProfile
              ? $router.push('/quick-analysis')
              : $router.push(generateProjectUrl('recordings'))
          "
          :title="isQuickAnalysisProfile ? 'Back to Quick Analysis' : 'Back to Recordings'"
        >
          <i class="bi bi-arrow-return-left"></i>
          <span>{{ isQuickAnalysisProfile ? 'Quick Analysis' : 'Recordings' }}</span>
        </button>

        <!-- Back to Workspaces button (only shown on project pages) -->
        <button
          v-if="isProjectPage"
          class="topbar-back-btn me-2"
          @click="$router.push('/workspaces')"
          title="Back to workspaces"
        >
          <i class="bi bi-arrow-return-left"></i>
          <span>Workspaces</span>
        </button>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import VersionClient from '@/services/api/VersionClient';

const route = useRoute();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

const version = ref('');
const versionClient = new VersionClient();

// Check if current route is a profile detail page
const isProfilePage = computed(() => {
  return route.meta.layout === 'profile';
});

// Check if current profile is a Quick Analysis profile (no workspace/project context)
const isQuickAnalysisProfile = computed(() => {
  return !workspaceId.value || !projectId.value;
});

// Check if current route is a project detail page
const isProjectPage = computed(() => {
  return route.meta.layout === 'project' || route.path.includes('/projects/');
});

const toggleSidebar = () => {
  if (window.toggleSidebar) {
    window.toggleSidebar();
  }
};

onMounted(async () => {
  try {
    version.value = await versionClient.getVersion();
  } catch {
    version.value = '';
  }
});
</script>

<style scoped>
.topbar {
  background: linear-gradient(
    135deg,
    #1e1b4b 0%,
    #312e81 40%,
    #4338ca 70%,
    var(--color-primary) 100%
  );
  height: 50px;
  position: sticky;
  top: 0;
  z-index: 1040;
  overflow: hidden;
}

.topbar-decoration {
  position: absolute;
  top: -20px;
  right: -20px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.03);
  pointer-events: none;
}

.topbar-toggle {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.8);
  padding: 4px 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: none;
}

.topbar-toggle:hover {
  background: rgba(255, 255, 255, 0.18);
  color: white;
}

@media (max-width: 991.98px) {
  .topbar-toggle {
    display: flex;
    align-items: center;
  }
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  padding: 0;
  margin-right: 2rem;
}

.topbar-logo {
  width: 22px;
  height: 22px;
}

.topbar-title {
  font-size: 1rem;
  font-weight: 700;
  color: white;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.topbar-subtitle {
  font-size: 0.68rem;
  color: rgba(255, 255, 255, 0.5);
  font-weight: 400;
  letter-spacing: 0.3px;
}

.topbar-version {
  padding: 2px 7px;
  background: rgba(0, 210, 122, 0.2);
  border: 1px solid rgba(0, 210, 122, 0.3);
  border-radius: 10px;
  font-size: 0.5rem;
  font-weight: 600;
  color: #6ee7b7;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

/* Back buttons — glassmorphic style */
.topbar-back-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 0.72rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  white-space: nowrap;
}

.topbar-back-btn:hover {
  background: rgba(255, 255, 255, 0.22);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.topbar-back-btn:active {
  transform: translateY(0);
}

.topbar-back-btn i {
  font-size: 0.65rem;
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

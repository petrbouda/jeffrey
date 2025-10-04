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
        <button v-if="isProfilePage"
                class="back-to-profiles-btn me-2"
                @click="$router.push(generateProjectUrl('profiles'))"
                title="Back to profiles">
          <i class="bi bi-arrow-return-left"></i>
          <span>Profiles</span>
        </button>

        <!-- Back to Workspaces button (only shown on project pages) -->
        <button v-if="isProjectPage"
                class="back-to-workspace-btn me-2"
                :class="{
                  'btn-sandbox': workspaceInfo?.type === WorkspaceType.SANDBOX,
                  'btn-remote': workspaceInfo?.type === WorkspaceType.REMOTE,
                  'btn-local': workspaceInfo?.type === WorkspaceType.LOCAL
                }"
                @click="$router.push('/workspaces')"
                title="Back to workspaces">
          <i class="bi bi-arrow-return-left"></i>
          <span>Workspaces</span>
        </button>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import WorkspaceType from '@/services/workspace/model/WorkspaceType';
import Workspace from '@/services/workspace/model/Workspace';

const route = useRoute();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

// Workspace info for styling the button
const workspaceInfo = ref<Workspace | null>(null);

// Check if current route is a profile detail page
const isProfilePage = computed(() => {
  return route.meta.layout === 'profile';
});

// Check if current route is a project detail page
const isProjectPage = computed(() => {
  return route.meta.layout === 'project' || route.path.includes('/projects/');
});

// Load workspace info when workspaceId changes
watch(workspaceId, async (newWorkspaceId) => {
  if (newWorkspaceId) {
    try {
      const workspaces = await WorkspaceClient.list();
      workspaceInfo.value = workspaces.find(w => w.id === newWorkspaceId) || null;
    } catch (error) {
      console.error('Failed to load workspace info:', error);
    }
  }
}, { immediate: true });

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

/* Back to Profiles Button */
.back-to-profiles-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  text-decoration: none;
  align-self: flex-start;
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border: 1px solid rgba(94, 100, 255, 0.3);
  color: #1a237e;

  &:hover {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    color: white;
    border-color: #4c52ff;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(94, 100, 255, 0.3);
  }

  &:active {
    transform: translateY(0);
  }

  i {
    font-size: 0.7rem;
  }
}

/* Back to Workspace Button */
.back-to-workspace-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  text-decoration: none;
  align-self: flex-start;

  /* Local Workspace Button */
  &.btn-local {
    background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
    border: 1px solid rgba(94, 100, 255, 0.3);
    color: #1a237e;

    &:hover {
      background: linear-gradient(135deg, #5e64ff, #4c52ff);
      color: white;
      border-color: #4c52ff;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(94, 100, 255, 0.3);
    }
  }

  /* Sandbox Workspace Button */
  &.btn-sandbox {
    background: linear-gradient(135deg, #fff9e6, #fef3cd);
    border: 1px solid rgba(255, 193, 7, 0.3);
    color: #856404;

    &:hover {
      background: linear-gradient(135deg, #ffc107, #ffb300);
      color: white;
      border-color: #ffb300;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(255, 193, 7, 0.3);
    }
  }

  /* Remote Workspace Button */
  &.btn-remote {
    background: linear-gradient(135deg, #e6fffa, #b2f5ea);
    border: 1px solid rgba(56, 178, 172, 0.3);
    color: #234e52;

    &:hover {
      background: linear-gradient(135deg, #38b2ac, #319795);
      color: white;
      border-color: #319795;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(56, 178, 172, 0.3);
    }
  }

  &:active {
    transform: translateY(0);
  }

  i {
    font-size: 0.7rem;
  }
}
</style>

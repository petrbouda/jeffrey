<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="project-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="hamburger-toggle d-flex align-items-center justify-content-center">
          <button class="btn btn-sm hamburger-btn" @click="toggleSidebar">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </button>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Project Header -->
          <div class="p-3 border-bottom">
            <div v-if="!sidebarCollapsed">
              <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 260px;">{{ projectInfo?.name || 'Loading...' }}</h5>
              <p class="text-muted mb-0 fs-7">Project details</p>
            </div>
          </div>

          <div class="sidebar-menu" v-if="!sidebarCollapsed">
            <div class="nav-section">
              <div class="nav-section-title">OVERVIEW</div>
              <div class="nav-items">
                <router-link
                    :to="`/projects/${projectId}/profiles`"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-file-earmark-text"></i>
                  <span>Profiles</span>
                  <div v-if="hasInitializingProfiles" class="ms-auto">
                    <Badge value="Initializing" variant="orange" size="xs" icon="spinner-border spinner-border-sm"/>
                  </div>
                  <Badge v-else-if="projectInfo != null && projectInfo.profileCount > 0" :value="projectInfo.profileCount.toString()" variant="primary" size="xs"
                         class="ms-auto"/>
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/recordings`"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-record-circle"></i>
                  <span>Recordings</span>
                  <Badge v-if="projectInfo != null && projectInfo.recordingCount > 0" :value="projectInfo.recordingCount.toString()" variant="info" size="xs"
                         class="ms-auto"/>
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/repository`"
                    class="nav-item"
                    :class="{ 'disabled-feature': isSandboxWorkspace }"
                    active-class="active">
                  <i class="bi bi-folder"></i>
                  <span>Repository</span>
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/scheduler`"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                  <Badge v-if="projectInfo != null && projectInfo.jobCount > 0" :value="projectInfo.jobCount" variant="warning" size="xs" class="ms-auto"/>
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/settings`"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-sliders"></i>
                  <span>Settings</span>
                </router-link>
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="project-main-content">
      <!-- Content Area without tabs -->
      <div class="project-content-container mb-4">
        <div class="card">
          <div class="card-body">
            <router-view></router-view>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import ToastService from '@/services/ToastService';
import MessageBus from "@/services/MessageBus.ts";
import Badge from '@/components/Badge.vue';
import ProjectClient from "@/services/ProjectClient.ts";
import Project from "@/services/model/Project.ts";
import WorkspaceType from "@/services/workspace/model/WorkspaceType.ts";

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId as string;

const projectInfo = ref<Project | null>(null);
const sidebarCollapsed = ref(false);

// Initialization state variables
const hasInitializingProfiles = ref(false);
const pollInterval = ref<number | null>(null);

// Computed property to check if project is in LOCAL workspace
const isSandboxWorkspace = computed(() => {
  return projectInfo.value?.workspaceType === WorkspaceType.SANDBOX;
});

// Create service client
const projectClient = new ProjectClient(projectId);

// Check if project has initializing profiles
async function checkInitializingProfiles() {
  try {
    hasInitializingProfiles.value = await projectClient.isInitializing();
  } catch (error) {
    console.error('Failed to check initializing profiles:', error);
    hasInitializingProfiles.value = false;
  }
}


// Set up message bus listeners for count updates
function handleJobCountChange(count: number) {
  if (projectInfo.value) {
    projectInfo.value.jobCount = count;
  }
}

function handleProfileCountChange(count: number) {
  if (projectInfo.value) {
    projectInfo.value.profileCount = count;
  }
}

function handleRecordingCountChange(count: number) {
  if (projectInfo.value) {
    projectInfo.value.recordingCount = count;
  }
}


// Start polling for profile status when initialization starts
function startPolling() {
  if (pollInterval.value !== null) return;

  // Set initializing flag immediately
  hasInitializingProfiles.value = true;

  pollInterval.value = window.setInterval(async () => {
    try {
      await checkInitializingProfiles();

      // If no profiles are initializing anymore, stop polling
      if (!hasInitializingProfiles.value) {
        stopPolling();
      }
    } catch (error) {
      console.error('Error while polling profiles:', error);
    }
  }, 5000) as unknown as number;
}

function stopPolling() {
  if (pollInterval.value !== null) {
    window.clearInterval(pollInterval.value);
    pollInterval.value = null;
  }
}

function handleProfileInitializationStarted() {
  // Start polling immediately when a profile initialization starts
  hasInitializingProfiles.value = true;
  startPolling();
}

onMounted(async () => {
  try {
    // Fetch project data (includes all counts)
    projectInfo.value = await projectClient.get();

    // Check for initializing profiles
    await checkInitializingProfiles();

    // Set up message bus listeners
    MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
    MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
    MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
    MessageBus.on(MessageBus.PROFILE_INITIALIZATION_STARTED, handleProfileInitializationStarted);
  } catch (error) {
    ToastService.error('Failed to load projects', 'Cannot load projects from the server. Please try again later.');
    await router.push('/projects');
  }
});

onUnmounted(() => {
  // Clean up message bus listeners
  MessageBus.off(MessageBus.JOBS_COUNT_CHANGED);
  MessageBus.off(MessageBus.PROFILES_COUNT_CHANGED);
  MessageBus.off(MessageBus.RECORDINGS_COUNT_CHANGED);
  MessageBus.off(MessageBus.PROFILE_INITIALIZATION_STARTED);

  // Ensure polling is stopped when component unmounts
  stopPolling();
});

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  MessageBus.emit(MessageBus.SIDEBAR_CHANGED, null);
};
</script>

<style scoped>
/* Content container styles */
.project-content-container {
  width: 100%;
}

.card {
  border: none;
  border-radius: 0;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Sidebar styles */
.project-sidebar {
  width: 280px;
  min-height: 100vh;
  background-color: #fff;
  border-right: 1px solid #dee2e6;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
}

.project-sidebar.collapsed {
  width: 50px;
}

.sidebar {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 50px;
}

.scrollbar {
  overflow-y: auto;
  height: calc(100% - 40px);

  &::-webkit-scrollbar {
    width: 5px;
    height: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }

  &:hover::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.2);
  }
}

.hamburger-toggle {
  position: absolute;
  right: -16px;
  top: 20px;
  z-index: 10;
}

.hamburger-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  background-color: #fff;
  border: 1px solid #dee2e6;
  border-radius: 50%;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Modern sidebar styling */
.sidebar-menu {
  padding: 0.5rem 0;
}

.nav-section {
  margin-bottom: 1.25rem;
}

.nav-section-title {
  color: #748194;
  font-weight: 600;
  font-size: 0.7rem;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  padding: 0 1.25rem;
  margin-bottom: 0.5rem;
}

.nav-items {
  display: flex;
  flex-direction: column;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 1.25rem;
  color: #5e6e82;
  font-weight: 500;
  font-size: 0.85rem;
  border-radius: 0;
  transition: all 0.2s ease;
  position: relative;
  text-decoration: none;
  margin: 0.125rem 0;

  &:hover {
    color: #5e64ff;
    background-color: rgba(94, 100, 255, 0.06);
  }

  &.active {
    color: #5e64ff;
    background-color: rgba(94, 100, 255, 0.1);
    border-left: 3px solid #5e64ff;
    padding-left: calc(1.25rem - 3px);

    i {
      color: #5e64ff;
    }
  }

  i {
    color: #7d899b;
    font-size: 0.9rem;
    width: 1.5rem;
    text-align: center;
    margin-right: 0.5rem;
  }
}

.fs-7 {
  font-size: 0.75rem !important;
}

.project-main-content {
  flex: 1;
  padding-left: 1rem;
  transition: all 0.3s ease;
  overflow: hidden;
}

/* Disabled features styling */
.disabled-feature {
  border-right: 2px solid #ffc107 !important;
  border-bottom-right-radius: 4px;
  border-top-right-radius: 4px;
  position: relative;
}

.disabled-feature::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background-color: #ffc107;
  box-shadow: 0 0 8px rgba(255, 193, 7, 0.3);
}
</style>

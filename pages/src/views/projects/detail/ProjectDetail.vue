<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="project-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="edge-toggle" @click="toggleSidebar">
          <div class="edge-toggle-line">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </div>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Workspace Header -->
          <div class="workspace-context p-2 border"
               :class="{
                 'workspace-sandbox': workspaceInfo?.type === WorkspaceType.SANDBOX,
                 'workspace-remote': workspaceInfo?.type === WorkspaceType.REMOTE,
                 'workspace-live': workspaceInfo?.type === WorkspaceType.LIVE
               }"

               v-if="!sidebarCollapsed">
            <div class="workspace-info">
              <div class="workspace-title-container">
                <i v-if="workspaceInfo?.type === WorkspaceType.REMOTE" class="bi bi-display workspace-type-icon" title="Remote Workspace"></i>
                <i v-else-if="workspaceInfo?.type === WorkspaceType.SANDBOX" class="bi bi-house workspace-type-icon" title="Sandbox Workspace"></i>
                <i v-else class="bi bi-folder workspace-type-icon" title="Live Workspace"></i>
                <span class="workspace-name">{{ workspaceInfo?.name || 'Loading...' }}</span>
                <Badge
                    v-if="workspaceInfo?.status === WorkspaceStatus.UNAVAILABLE"
                    :value="'UNAVAILABLE'"
                    variant="red"
                    size="xs"
                />
                <Badge
                    v-else-if="workspaceInfo?.status === WorkspaceStatus.OFFLINE"
                    :value="'OFFLINE'"
                    variant="red"
                    size="xs"
                />
                <Badge
                    v-else-if="workspaceInfo?.status === WorkspaceStatus.UNKNOWN"
                    :value="'UNKNOWN'"
                    variant="yellow"
                    size="xs"
                />
              </div>
            </div>
          </div>

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
                    :to="generateProjectUrl('profiles')"
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
                    :to="generateProjectUrl('recordings')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-record-circle"></i>
                  <span>Recordings</span>
                  <Badge v-if="projectInfo != null && projectInfo.recordingCount > 0" :value="projectInfo.recordingCount.toString()" variant="info" size="xs"
                         class="ms-auto"/>
                </router-link>
                <router-link
                    :to="generateProjectUrl('repository')"
                    class="nav-item"
                    :class="{ 'disabled-feature': isSandboxWorkspace }"
                    :title="isSandboxWorkspace ? 'Repository is not available in Sandbox workspaces' : ''"
                    :tabindex="isSandboxWorkspace ? -1 : 0"
                    @click="isSandboxWorkspace ? $event.preventDefault() : null"
                    active-class="active">
                  <i class="bi bi-folder"></i>
                  <span>Repository</span>
                </router-link>
                <router-link
                    :to="generateProjectUrl('profiler-settings')"
                    class="nav-item"
                    :class="{ 'disabled-feature': isSandboxWorkspace }"
                    :title="isSandboxWorkspace ? 'Profiler Settings is not available in Sandbox workspaces' : ''"
                    :tabindex="isSandboxWorkspace ? -1 : 0"
                    @click="isSandboxWorkspace ? $event.preventDefault() : null"
                    active-class="active">
                  <i class="bi bi-cpu"></i>
                  <span>Profiler Settings</span>
                </router-link>
                <router-link
                    :to="generateProjectUrl('scheduler')"
                    class="nav-item"
                    :class="{ 'disabled-feature': isSchedulerDisabled }"
                    :title="isSchedulerDisabled ? 'Scheduler is not available in this workspace type' : ''"
                    :tabindex="isSchedulerDisabled ? -1 : 0"
                    @click="isSchedulerDisabled ? $event.preventDefault() : null"
                    active-class="active">
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                  <Badge v-if="projectInfo != null && projectInfo.jobCount > 0 && !isSchedulerDisabled" :value="projectInfo.jobCount" variant="warning" size="xs" class="ms-auto"/>
                </router-link>
                <router-link
                    :to="generateProjectUrl('settings')"
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
import {computed, onMounted, onUnmounted, ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import ToastService from '@/services/ToastService';
import MessageBus from "@/services/MessageBus.ts";
import Badge from '@/components/Badge.vue';
import ProjectClient from "@/services/api/ProjectClient.ts";
import Project from "@/services/api/model/Project.ts";
import WorkspaceType from "@/services/api/model/WorkspaceType.ts";
import WorkspaceClient from "@/services/api/WorkspaceClient.ts";
import Workspace from "@/services/api/model/Workspace.ts";
import WorkspaceStatus from "@/services/api/model/WorkspaceStatus.ts";
import { useNavigation } from '@/composables/useNavigation';

const router = useRouter();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

const projectInfo = ref<Project | null>(null);
const workspaceInfo = ref<Workspace | null>(null);
const sidebarCollapsed = ref(false);

// Initialization state variables
const hasInitializingProfiles = ref(false);
const pollInterval = ref<number | null>(null);

// Computed property to check if project is in SANDBOX workspace
const isSandboxWorkspace = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.SANDBOX;
});

// Computed property to check if Scheduler should be disabled
const isSchedulerDisabled = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.SANDBOX ||
         workspaceInfo.value?.type === WorkspaceType.REMOTE;
});

// Create service client - will be initialized when projectId is available
let projectClient: ProjectClient | null = null;

// Check if project has initializing profiles
async function checkInitializingProfiles() {
  try {
    if (!projectClient) return;
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

// Initialize workspace and project data when IDs become available
async function initializeProject() {
  if (!projectId.value || !workspaceId.value) return;

  try {
    // Initialize project client with both workspace ID and project ID
    projectClient = new ProjectClient(workspaceId.value, projectId.value);

    // Fetch project data and workspace list in parallel
    const [workspaces, project] = await Promise.all([
      WorkspaceClient.list(),
      projectClient.get()
    ]);

    // Find the current workspace from the list
    workspaceInfo.value = workspaces.find(w => w.id === workspaceId.value) || null;
    projectInfo.value = project;

    // Check for initializing profiles
    await checkInitializingProfiles();
  } catch (error) {
    console.error('Failed to load project:', error);
    ToastService.error('Failed to load project', 'Cannot load project from the server.');
    await router.push('/workspaces');
  }
}

// Watch for both projectId and workspaceId changes and initialize
watch([projectId, workspaceId], async ([newProjectId, newWorkspaceId]) => {
  if (newProjectId && newWorkspaceId) {
    await initializeProject();
  }
}, { immediate: true });

onMounted(async () => {
  // Set up message bus listeners
  MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
  MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
  MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
  MessageBus.on(MessageBus.PROFILE_INITIALIZATION_STARTED, handleProfileInitializationStarted);

  // Initialize project if both IDs are already available
  if (projectId.value && workspaceId.value) {
    await initializeProject();
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

.edge-toggle {
  position: absolute;
  right: -8px;
  top: 0;
  bottom: 0;
  width: 20px;
  cursor: pointer;
  opacity: 0.3;
  transition: opacity 0.2s ease;
  z-index: 10;

  &:hover {
    opacity: 1;
  }

  &:hover .edge-toggle-line {
    background: linear-gradient(135deg, #5e64ff, #4338ca);
    width: 7px;
    box-shadow: -2px 0 8px rgba(94, 100, 255, 0.2);
  }

  &:hover .edge-toggle-line i {
    opacity: 1;
  }
}

.edge-toggle-line {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 90px;
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  border-radius: 4px 0 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  box-shadow: -1px 0 3px rgba(0, 0, 0, 0.05);
  pointer-events: none;

  i {
    position: absolute;
    font-size: 0.8rem;
    color: white;
    opacity: 0;
    transition: opacity 0.2s ease;
  }
}

/* Show edge toggle on sidebar hover */
.project-sidebar:hover .edge-toggle {
  opacity: 0.7;
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

/* Workspace Context Styling */
.workspace-context {
  border: 1px solid rgba(94, 100, 255, 0.12) !important;
  border-radius: 0.375rem;
  margin: 0.5rem 0.5rem 0 0.5rem;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  /* Live Workspace Styling */
  &.workspace-live {
    background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
    border-color: rgba(94, 100, 255, 0.3) !important;

    .workspace-type-icon {
      color: #5e64ff;
    }

    .workspace-name {
      color: #1a237e;
    }
  }

  /* Sandbox Workspace Styling */
  &.workspace-sandbox {
    background: linear-gradient(135deg, #fff9e6, #fef3cd);
    border-color: rgba(255, 193, 7, 0.3) !important;

    .workspace-type-icon {
      color: #856404;
    }

    .workspace-name {
      color: #856404;
    }
  }

  /* Remote Workspace Styling */
  &.workspace-remote {
    background: linear-gradient(135deg, #e6fffa, #b2f5ea);
    border-color: rgba(56, 178, 172, 0.3) !important;

    .workspace-type-icon {
      color: #38b2ac;
    }

    .workspace-name {
      color: #234e52;
    }
  }

  /* Default styling */
  background: linear-gradient(135deg, #f8f9fb, #ffffff);
}

.workspace-info {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.workspace-title-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.workspace-type-icon {
  font-size: 0.85rem;
  opacity: 0.8;
  transition: all 0.2s ease;
}

.workspace-name {
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  transition: all 0.2s ease;
}


/* Disabled features styling */
.disabled-feature {
  opacity: 0.5;
  cursor: not-allowed !important;
  position: relative;
  pointer-events: none;

  &:hover {
    background-color: transparent !important;
    color: #6b7280 !important;
    transform: none !important;
  }

  i {
    color: #9ca3af !important;
  }

  span {
    color: #9ca3af !important;
  }

  /* Add subtle indication that it's disabled */
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: linear-gradient(135deg, #fbbf24, #f59e0b);
    border-radius: 0 3px 3px 0;
  }

  /* Override active state for disabled items */
  &.active {
    background-color: transparent !important;
    border-left: none !important;
    padding-left: 1.25rem !important;

    i, span {
      color: #9ca3af !important;
    }
  }
}
</style>

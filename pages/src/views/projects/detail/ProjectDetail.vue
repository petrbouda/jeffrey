<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="detail-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="edge-toggle" @click="toggleSidebar">
          <div class="edge-toggle-line">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </div>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Sidebar Header -->
          <div class="sidebar-header" :class="workspaceTypeClass" v-if="!sidebarCollapsed">
            <h5 class="project-name">{{ projectInfo?.name || 'Loading...' }}</h5>
            <div class="header-badges" v-if="workspaceInfo?.status === WorkspaceStatus.UNAVAILABLE || workspaceInfo?.status === WorkspaceStatus.OFFLINE || workspaceInfo?.status === WorkspaceStatus.UNKNOWN">
              <Badge v-if="workspaceInfo?.status === WorkspaceStatus.UNAVAILABLE" value="UNAVAILABLE" variant="red" size="xs"/>
              <Badge v-else-if="workspaceInfo?.status === WorkspaceStatus.OFFLINE" value="OFFLINE" variant="red" size="xs"/>
              <Badge v-else-if="workspaceInfo?.status === WorkspaceStatus.UNKNOWN" value="UNKNOWN" variant="yellow" size="xs"/>
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
                    v-if="!isSandboxWorkspace"
                    :to="generateProjectUrl('repository')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-folder"></i>
                  <span>Repository</span>
                </router-link>
                <div
                    v-else
                    class="nav-item disabled-feature"
                    title="Repository is not available in Sandbox workspaces">
                  <i class="bi bi-folder"></i>
                  <span>Repository</span>
                </div>
                <!-- Instances with 2-level submenu -->
                <div class="nav-item-group">
                  <div class="nav-item nav-item-parent"
                       @click="isSandboxWorkspace ? null : toggleInstancesSubmenu()"
                       :class="{ 'active': $route.path.includes('/instances'), 'expanded': instancesSubmenuExpanded, 'disabled-feature': isSandboxWorkspace }"
                       :title="isSandboxWorkspace ? 'Instances are not available in Sandbox workspaces' : ''">
                    <i class="bi bi-box"></i>
                    <span>Instances</span>
                    <i v-if="!isSandboxWorkspace" class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': instancesSubmenuExpanded }"></i>
                  </div>
                  <div v-if="!isSandboxWorkspace" class="nav-submenu" :class="{ 'expanded': instancesSubmenuExpanded }">
                    <router-link
                        :to="generateProjectUrl('instances')"
                        class="nav-item nav-subitem"
                        active-class="active">
                      <i class="bi bi-circle-fill"></i>
                      <span>Active</span>
                    </router-link>
                    <router-link
                        :to="generateProjectUrl('instances/history')"
                        class="nav-item nav-subitem"
                        active-class="active">
                      <i class="bi bi-clock-history"></i>
                      <span>History</span>
                    </router-link>
                    <router-link
                        :to="generateProjectUrl('instances/timeline')"
                        class="nav-item nav-subitem"
                        active-class="active">
                      <i class="bi bi-bar-chart-steps"></i>
                      <span>Timeline</span>
                    </router-link>
                  </div>
                </div>
                <router-link
                    v-if="!isSandboxWorkspace"
                    :to="generateProjectUrl('profiler-settings')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-cpu"></i>
                  <span>Profiler Settings</span>
                </router-link>
                <div
                    v-else
                    class="nav-item disabled-feature"
                    title="Profiler Settings is not available in Sandbox workspaces">
                  <i class="bi bi-cpu"></i>
                  <span>Profiler Settings</span>
                </div>
                <router-link
                    v-if="!isSchedulerDisabled"
                    :to="generateProjectUrl('scheduler')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                  <Badge v-if="projectInfo != null && projectInfo.jobCount > 0" :value="projectInfo.jobCount" variant="warning" size="xs" class="ms-auto"/>
                </router-link>
                <div
                    v-else
                    class="nav-item disabled-feature"
                    title="Scheduler is not available in this workspace type">
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                </div>
                <router-link
                    v-if="!isSandboxWorkspace"
                    :to="generateProjectUrl('alerts')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-bell"></i>
                  <span>Alerts</span>
                </router-link>
                <div
                    v-else
                    class="nav-item disabled-feature"
                    title="Alerts not available in Sandbox workspaces">
                  <i class="bi bi-bell"></i>
                  <span>Alerts</span>
                </div>
                <router-link
                    v-if="!isSandboxWorkspace"
                    :to="generateProjectUrl('messages')"
                    class="nav-item"
                    active-class="active">
                  <i class="bi bi-chat-square-text"></i>
                  <span>Messages</span>
                </router-link>
                <div
                    v-else
                    class="nav-item disabled-feature"
                    title="Messages not available in Sandbox workspaces">
                  <i class="bi bi-chat-square-text"></i>
                  <span>Messages</span>
                </div>
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
    <div class="detail-main-content">
      <!-- Content Area without tabs -->
      <div class="detail-content-container mb-4">
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
import {useRoute, useRouter} from 'vue-router';
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

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

const projectInfo = ref<Project | null>(null);
const workspaceInfo = ref<Workspace | null>(null);
const sidebarCollapsed = ref(false);

// Submenu expansion state
const instancesSubmenuExpanded = ref(false);

// Toggle function for Instances submenu
const toggleInstancesSubmenu = () => {
  instancesSubmenuExpanded.value = !instancesSubmenuExpanded.value;
};

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

// Computed property for sidebar header styling
const workspaceTypeClass = computed(() => {
  const type = workspaceInfo.value?.type;
  if (type === WorkspaceType.SANDBOX) return 'avatar-sandbox';
  if (type === WorkspaceType.REMOTE) return 'avatar-remote';
  return 'avatar-live';
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

// Watch for route changes to auto-expand submenus
watch(() => route.path, (newPath) => {
  if (newPath.includes('/instances')) {
    instancesSubmenuExpanded.value = true;
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
/* ProjectDetail-specific styles */
/* Common sidebar styles are in @/assets/_sidebar-menu.scss */

/* Sidebar Header - Light Tinted with Accent Border */
.sidebar-header {
  padding: 1rem;
  border-bottom: 1px solid #e5e7eb;
  border-left: 4px solid;

  &.avatar-live {
    background: linear-gradient(135deg, #f5f5ff, #eef0ff);
    border-left-color: #4f46e5;
  }

  &.avatar-sandbox {
    background: linear-gradient(135deg, #fffbf0, #fef7e6);
    border-left-color: #f59e0b;
  }

  &.avatar-remote {
    background: linear-gradient(135deg, #f0fdfb, #e6faf7);
    border-left-color: #38b2ac;
  }
}

.project-name {
  font-size: 0.9375rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-badges {
  display: flex;
  gap: 0.375rem;
  margin-top: 0.5rem;
}
</style>

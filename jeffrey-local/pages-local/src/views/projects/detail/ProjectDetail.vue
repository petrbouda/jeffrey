<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="detail-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="edge-toggle" @click="toggleSidebar">
          <div class="edge-toggle-line">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </div>
        </div>

        <div class="scrollbar" style="height: 100%">
          <!-- Sidebar Header -->
          <div class="sidebar-header" v-if="!sidebarCollapsed">
            <h5 class="project-name">{{ projectInfo?.name || 'Loading...' }}</h5>
          </div>

          <div class="sidebar-menu" v-if="!sidebarCollapsed">
            <div class="nav-section">
              <div class="nav-section-title">OVERVIEW</div>
              <div class="nav-items">
                <router-link
                  v-if="!isCollectorOnly"
                  :to="generateProjectUrl('recordings')"
                  class="nav-item"
                  active-class="active"
                >
                  <i class="bi bi-record-circle"></i>
                  <span>Recordings</span>
                  <div v-if="hasInitializingProfiles" class="ms-auto">
                    <Badge
                      value="Initializing"
                      variant="orange"
                      size="xs"
                      icon="spinner-border spinner-border-sm"
                    />
                  </div>
                  <Badge
                    v-else-if="projectInfo != null && projectInfo.recordingCount > 0"
                    :value="projectInfo.recordingCount.toString()"
                    variant="info"
                    size="xs"
                    class="ms-auto"
                  />
                </router-link>
                <div
                  v-else
                  class="nav-item disabled-feature"
                  title="Recordings are not available in collector-only mode"
                >
                  <i class="bi bi-record-circle"></i>
                  <span>Recordings</span>
                </div>
                <!-- Instances with 2-level submenu -->
                <div class="nav-item-group">
                  <div
                    class="nav-item nav-item-parent"
                    @click="toggleInstancesSubmenu()"
                    :class="{
                      active: $route.path.includes('/instances'),
                      expanded: instancesSubmenuExpanded
                    }"
                  >
                    <i class="bi bi-box"></i>
                    <span>Instances</span>
                    <i
                      class="bi bi-chevron-right submenu-arrow"
                      :class="{ rotated: instancesSubmenuExpanded }"
                    ></i>
                  </div>
                  <div class="nav-submenu" :class="{ expanded: instancesSubmenuExpanded }">
                    <router-link
                      :to="generateProjectUrl('instances')"
                      class="nav-item nav-subitem"
                      active-class="active"
                    >
                      <i class="bi bi-grid"></i>
                      <span>Overview</span>
                    </router-link>
                    <router-link
                      :to="generateProjectUrl('instances/timeline')"
                      class="nav-item nav-subitem"
                      active-class="active"
                    >
                      <i class="bi bi-bar-chart-steps"></i>
                      <span>Timeline</span>
                    </router-link>
                  </div>
                </div>
                <router-link
                  :to="generateProjectUrl('profiler-settings')"
                  class="nav-item"
                  active-class="active"
                >
                  <i class="bi bi-cpu"></i>
                  <span>Profiler Settings</span>
                </router-link>
                <router-link
                  v-if="!isSchedulerDisabled"
                  :to="generateProjectUrl('scheduler')"
                  class="nav-item"
                  active-class="active"
                >
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                </router-link>
                <router-link
                  :to="generateProjectUrl('alerts')"
                  class="nav-item"
                  active-class="active"
                >
                  <i class="bi bi-bell"></i>
                  <span>Alerts</span>
                </router-link>
                <router-link
                  :to="generateProjectUrl('messages')"
                  class="nav-item"
                  active-class="active"
                >
                  <i class="bi bi-chat-square-text"></i>
                  <span>Messages</span>
                </router-link>
                <router-link
                  :to="generateProjectUrl('settings')"
                  class="nav-item"
                  active-class="active"
                >
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
      <!-- Blocked Project Banner -->
      <div v-if="projectInfo?.isBlocked" class="blocked-banner">
        <i class="bi bi-slash-circle"></i>
        <span>This project is blocked. No events are being processed.</span>
      </div>

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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ToastService from '@/services/ToastService';
import MessageBus from '@/services/MessageBus.ts';
import Badge from '@/components/Badge.vue';
import ProjectClient from '@/services/api/ProjectClient.ts';
import Project from '@/services/api/model/Project.ts';
import { useNavigation } from '@/composables/useNavigation';
const route = useRoute();
const router = useRouter();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

const projectInfo = ref<Project | null>(null);
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

// Scheduler is always disabled in local mode
const isSchedulerDisabled = computed(() => {
  return true;
});

// Collector-only mode is never active in local mode
const isCollectorOnly = computed(() => {
  return false;
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

    // Fetch project data
    projectInfo.value = await projectClient.get();

    // Check for initializing profiles
    await checkInitializingProfiles();
  } catch (error) {
    console.error('Failed to load project:', error);
    ToastService.error('Failed to load project', 'Cannot load project from the server.');
    await router.push('/workspaces');
  }
}

// Watch for both projectId and workspaceId changes and initialize
watch(
  [projectId, workspaceId],
  async ([newProjectId, newWorkspaceId]) => {
    if (newProjectId && newWorkspaceId) {
      await initializeProject();
    }
  },
  { immediate: true }
);

// Watch for route changes to auto-expand submenus
watch(
  () => route.path,
  newPath => {
    if (newPath.includes('/instances')) {
      instancesSubmenuExpanded.value = true;
    }
  },
  { immediate: true }
);

onMounted(() => {
  // Set up message bus listeners
  MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
  MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
  MessageBus.on(MessageBus.PROFILE_INITIALIZATION_STARTED, handleProfileInitializationStarted);
});

onUnmounted(() => {
  // Clean up message bus listeners
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

/* Blocked Project Banner */
.blocked-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 8px;
  color: #92400e;
  font-size: 0.85rem;
  font-weight: 500;
}

.blocked-banner i {
  font-size: 1rem;
  color: #f59e0b;
}

/* Sidebar Header - Light Tinted with Accent Border */
.sidebar-header {
  padding: 1rem;
  border-bottom: 1px solid #e5e7eb;
  border-left: 4px solid var(--color-primary);
  background: linear-gradient(135deg, #f0f0ff, #e8e8ff);
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
</style>

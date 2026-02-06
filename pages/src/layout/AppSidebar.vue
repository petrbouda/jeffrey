<template>
  <div class="sidebar">
    <div class="scrollbar" style="height: 100%;">
      <div class="p-3 border-bottom">
        <div v-if="isLoading" class="d-flex align-items-center">
          <div class="spinner-border spinner-border-sm text-primary me-2" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span>Loading project...</span>
        </div>
        <div v-else>
          <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 240px;">{{ projectInfo?.name || 'Loading...' }}</h5>
          <p class="text-muted mb-0 fs-7">Project dashboard</p>
        </div>
      </div>
      
      <div class="py-3">
        <div class="nav-category px-3">Navigation</div>
        <ul class="nav flex-column">
          <li class="nav-item px-3 py-1" v-for="(item, index) in menuItems" :key="index">
            <router-link 
              :to="getItemRoute(item.path)" 
              class="nav-link d-flex align-items-center py-2"
              :class="{ 'active': isActive(item.path) }"
            >
              <i :class="item.icon" class="me-2"></i>
              <span>{{ item.label }}</span>
              <div v-if="item.badge" class="ms-auto d-flex align-items-center">
                <div v-if="item.badge.type === 'initializing'" class="spinner-border spinner-border-sm me-1" style="width: 0.5rem; height: 0.5rem;"></div>
                <Badge :value="item.badge.text" :variant="getBadgeVariant(item.badge.type)" size="xs" />
              </div>
            </router-link>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import Badge from '@/components/Badge.vue';
import ProjectClient from "@/services/api/ProjectClient.ts";
import Project from "@/services/api/model/Project.ts";
import MessageBus from "@/services/MessageBus";

const route = useRoute();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();
const projectInfo = ref<Project | null>(null);
const hasInitializingProfiles = ref(false);
const isLoading = ref(true);
const pollInterval = ref<number | null>(null);

// Create service client with workspace context
const projectClient = new ProjectClient(workspaceId.value, projectId.value)

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


// Fetch project details
async function fetchProjectDetails() {
  try {
    projectInfo.value = await projectClient.get();
    isLoading.value = false;
    MessageBus.emit(MessageBus.JOBS_COUNT_CHANGED, projectInfo.value.jobCount);
    MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, projectInfo.value.profileCount);
    MessageBus.emit(MessageBus.RECORDINGS_COUNT_CHANGED, projectInfo.value.recordingCount);
  } catch (error) {
    console.error('Failed to load project details:', error);
    isLoading.value = false;
  }
}

// Component lifecycle hooks
onMounted(async () => {
  MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
  MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
  MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
  MessageBus.on(MessageBus.UPDATE_PROJECT_SETTINGS, fetchProjectDetails);
  MessageBus.on(MessageBus.PROFILE_INITIALIZATION_STARTED, handleProfileInitializationStarted);
  await fetchProjectDetails();
  await checkInitializingProfiles();
});

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

onUnmounted(() => {
  stopPolling(); // Ensure polling is stopped when component unmounts
  MessageBus.off(MessageBus.JOBS_COUNT_CHANGED);
  MessageBus.off(MessageBus.PROFILES_COUNT_CHANGED);
  MessageBus.off(MessageBus.RECORDINGS_COUNT_CHANGED);
  MessageBus.off(MessageBus.UPDATE_PROJECT_SETTINGS);
  MessageBus.off(MessageBus.PROFILE_INITIALIZATION_STARTED);
});

const menuItems = computed(() => [
  { label: 'Profiles', icon: 'bi bi-person-vcard', path: 'profiles',
    badge: hasInitializingProfiles.value
      ? { type: 'initializing', text: 'Initializing' }
      : (projectInfo.value && projectInfo.value.profileCount > 0 ? { type: 'primary', text: projectInfo.value.profileCount.toString() } : null) },
  { label: 'Recordings', icon: 'bi bi-record-circle', path: 'recordings',
    badge: projectInfo.value && projectInfo.value.recordingCount > 0 ? { type: 'info', text: projectInfo.value.recordingCount.toString() } : null },
{ label: 'Scheduler', icon: 'bi bi-clock-history', path: 'scheduler',
    badge: projectInfo.value && projectInfo.value.jobCount > 0 ? { type: 'warning', text: projectInfo.value.jobCount.toString() } : null },
  { label: 'Settings', icon: 'bi bi-gear', path: 'settings' }
]);

const getItemRoute = (path: string) => {
  return generateProjectUrl(path);
};

const isActive = (path: string) => {
  return route.path.includes(path);
};

const getBadgeVariant = (type: string): string => {
  switch (type) {
    case 'linked':
      return 'green';
    case 'initializing':
      return 'warning';
    case 'primary':
      return 'primary';
    case 'info':
      return 'info';
    case 'warning':
      return 'warning';
    default:
      return 'secondary';
  }
};
</script>

<style lang="scss" scoped>
.sidebar {
  height: 100%;
  width: 280px;
  min-width: 280px;
  max-width: 280px;
  overflow: hidden;
  background-color: #fff;
}

.scrollbar {
  overflow-y: auto;
  
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

.nav-category {
  color: #748194;
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.02em;
}

.nav-link {
  color: #5e6e82;
  font-weight: 500;
  font-size: 0.9rem;
  border-radius: 0.25rem;
  
  &:hover {
    color: #5e64ff;
    background-color: #edf2f9;
  }
  
  &.active {
    color: #5e64ff;
    background-color: #eaebff;
    
    i {
      color: #5e64ff;
    }
  }
  
  i {
    color: #7d899b;
    font-size: 0.9rem;
    width: 1rem;
    text-align: center;
  }
}

.avatar {
  width: 2rem;
  height: 2rem;
  position: relative;
  display: inline-block;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-l {
  width: 2.5rem;
  height: 2.5rem;
  font-size: 1rem;
}

.bg-soft-primary {
  background-color: rgba(94, 100, 255, 0.1) !important;
}



@keyframes pulse {
  0% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.7;
  }
}

.fs-7 {
  font-size: 0.75rem !important;
}
</style>

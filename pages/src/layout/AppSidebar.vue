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
          <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 240px;">{{ projectName }}</h5>
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
              <span v-if="item.badge" class="badge rounded-pill ms-auto" :class="'bg-' + item.badge.type">{{ item.badge.text }}</span>
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
import ProjectSchedulerClient from "@/services/project/ProjectSchedulerClient.ts";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient";
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient";
import ProjectProfileClient from "@/services/ProjectProfileClient";
import ProjectRecordingClient from "@/services/ProjectRecordingClient";
import MessageBus from "@/services/MessageBus";

const route = useRoute();

const projectId = computed(() => route.params.projectId);
const projectName = ref('Loading...'); // Will be populated from the API
const jobCount = ref(0);
const profileCount = ref(0);
const recordingCount = ref(0);
const hasLinkedRepository = ref(false);
const isLoading = ref(true);

// Create services
const schedulerService = new ProjectSchedulerClient(projectId.value as string);
const settingsClient = new ProjectSettingsClient(projectId.value as string);
const repositoryClient = new ProjectRepositoryClient(projectId.value as string);
const profileClient = new ProjectProfileClient(projectId.value as string);
const recordingClient = new ProjectRecordingClient(projectId.value as string);

// Fetch active jobs count
async function fetchJobCount() {
  try {
    const jobs = await schedulerService.all();
    jobCount.value = jobs.length;
  } catch (error) {
    console.error('Failed to fetch job count:', error);
    jobCount.value = 0;
  }
}

// Fetch profile count
async function fetchProfileCount() {
  try {
    const profiles = await profileClient.list();
    profileCount.value = profiles.length;
  } catch (error) {
    console.error('Failed to fetch profile count:', error);
    profileCount.value = 0;
  }
}

// Fetch recording count
async function fetchRecordingCount() {
  try {
    const recordings = await recordingClient.list();
    recordingCount.value = recordings.length;
  } catch (error) {
    console.error('Failed to fetch recording count:', error);
    recordingCount.value = 0;
  }
}

// Fetch repository status
async function fetchRepositoryStatus() {
  try {
    await repositoryClient.get();
    hasLinkedRepository.value = true;
  } catch (error) {
    // 404 means no repository linked
    if (error.response && error.response.status === 404) {
      hasLinkedRepository.value = false;
    } else {
      console.error('Failed to fetch repository status:', error);
      hasLinkedRepository.value = false;
    }
  }
}

// Set up message bus listeners for count updates
function handleJobCountChange(count: number) {
  jobCount.value = count;
}

function handleProfileCountChange(count: number) {
  profileCount.value = count;
}

function handleRecordingCountChange(count: number) {
  recordingCount.value = count;
}

function handleRepositoryStatusChange(status: boolean) {
  hasLinkedRepository.value = status;
}

// Fetch project details
async function fetchProjectDetails() {
  try {
    const settings = await settingsClient.get();
    projectName.value = settings.name;
    isLoading.value = false;
  } catch (error) {
    console.error('Failed to load project settings:', error);
    projectName.value = 'Project';
    isLoading.value = false;
  }
}

// Component lifecycle hooks
onMounted(() => {
  fetchJobCount();
  fetchProfileCount();
  fetchRecordingCount();
  fetchRepositoryStatus();
  fetchProjectDetails();
  MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
  MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
  MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
  MessageBus.on(MessageBus.REPOSITORY_STATUS_CHANGED, handleRepositoryStatusChange);
  MessageBus.on(MessageBus.UPDATE_PROJECT_SETTINGS, fetchProjectDetails);
});

onUnmounted(() => {
  MessageBus.off(MessageBus.JOBS_COUNT_CHANGED);
  MessageBus.off(MessageBus.PROFILES_COUNT_CHANGED);
  MessageBus.off(MessageBus.RECORDINGS_COUNT_CHANGED);
  MessageBus.off(MessageBus.REPOSITORY_STATUS_CHANGED);
  MessageBus.off(MessageBus.UPDATE_PROJECT_SETTINGS);
});

const menuItems = computed(() => [
  { label: 'Profiles', icon: 'bi bi-person-vcard', path: 'profiles', 
    badge: profileCount.value > 0 ? { type: 'primary', text: profileCount.value.toString() } : null },
  { label: 'Recordings', icon: 'bi bi-record-circle', path: 'recordings',
    badge: recordingCount.value > 0 ? { type: 'info', text: recordingCount.value.toString() } : null },
  { label: 'Remote Repository', icon: 'bi bi-database', path: 'repository',
    badge: hasLinkedRepository.value ? { type: 'success', text: 'Linked' } : null },
  { label: 'Scheduler', icon: 'bi bi-clock-history', path: 'scheduler', 
    badge: jobCount.value > 0 ? { type: 'warning', text: jobCount.value.toString() } : null },
  { label: 'Settings', icon: 'bi bi-gear', path: 'settings' }
]);

const getItemRoute = (path: string) => {
  return `/projects/${projectId.value}/${path}`;
};

const isActive = (path: string) => {
  return route.path.includes(path);
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

.fs-7 {
  font-size: 0.75rem !important;
}
</style>

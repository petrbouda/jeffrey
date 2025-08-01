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
              <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 260px;">{{
                  project?.name || 'Loading...'
                }}</h5>
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
                    active-class="active"
                >
                  <i class="bi bi-file-earmark-text"></i>
                  <span>Profiles</span>
                  <div v-if="hasInitializingProfiles" class="ms-auto">
                    <Badge value="Initializing" variant="orange" size="xs" icon="spinner-border spinner-border-sm" />
                  </div>
                  <Badge v-else-if="profileCount > 0" :value="profileCount.toString()" variant="primary" size="xs" class="ms-auto" />
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/recordings`"
                    class="nav-item"
                    active-class="active"
                >
                  <i class="bi bi-record-circle"></i>
                  <span>Recordings</span>
                  <Badge v-if="recordingCount > 0" :value="recordingCount.toString()" variant="info" size="xs" class="ms-auto" />
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/repository`"
                    class="nav-item"
                    active-class="active"
                >
                  <i class="bi bi-folder"></i>
                  <span>Repository</span>
                  <Badge v-if="hasLinkedRepository" value="Linked" variant="green" size="xs" class="ms-auto" />
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/scheduler`"
                    class="nav-item"
                    active-class="active"
                >
                  <i class="bi bi-calendar-check"></i>
                  <span>Scheduler</span>
                  <Badge v-if="jobCount > 0" :value="jobCount.toString()" variant="warning" size="xs" class="ms-auto" />
                </router-link>
                <router-link
                    :to="`/projects/${projectId}/settings`"
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
    <div class="project-main-content">
      <!-- Content Area without tabs -->
      <div class="project-content-container mb-4">
        <div class="card">
          <div class="card-body">
            <router-view
                :project="project"
            ></router-view>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import ToastService from '@/services/ToastService';
import ProjectClient from "@/services/ProjectClient.ts";
import Project from "@/services/model/Project.ts";
import ProjectsClient from "@/services/ProjectsClient.ts";
import MessageBus from "@/services/MessageBus.ts";
import ProjectSchedulerClient from "@/services/project/ProjectSchedulerClient.ts";
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient";
import ProjectProfileClient from "@/services/ProjectProfileClient";
import Badge from '@/components/Badge.vue';
import ProjectRecordingClient from "@/services/ProjectRecordingClient";

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId as string;
const projectClient = new ProjectClient(projectId);

const project = ref<Project | null>(null);
const projects = ref<Project[]>([]);
const loading = ref(true);
const sidebarCollapsed = ref(false);

// Badge state variables
const jobCount = ref(0);
const profileCount = ref(0);
const recordingCount = ref(0);
const hasLinkedRepository = ref(false);
const hasInitializingProfiles = ref(false);
const pollInterval = ref<number | null>(null);

// Create service clients
const schedulerService = new ProjectSchedulerClient(projectId);
const repositoryClient = new ProjectRepositoryClient(projectId);
const profileClient = new ProjectProfileClient(projectId);
const recordingClient = new ProjectRecordingClient(projectId);

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

// Fetch profile count and check for initializing profiles
async function fetchProfileCount() {
  try {
    const profiles = await profileClient.list();
    profileCount.value = profiles.length;

    // Check if any profiles are initializing (not enabled)
    hasInitializingProfiles.value = profiles.some(profile => !profile.enabled);
  } catch (error) {
    console.error('Failed to fetch profile count:', error);
    profileCount.value = 0;
    hasInitializingProfiles.value = false;
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

// Start polling for profile status when initialization starts
function startPolling() {
  if (pollInterval.value !== null) return;

  // Set initializing flag immediately
  hasInitializingProfiles.value = true;

  pollInterval.value = window.setInterval(async () => {
    try {
      await fetchProfileCount();

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
    // Fetch all projects
    projects.value = await ProjectsClient.list();

    // Find the current project from the list
    project.value = projects.value.find(p => p.id === projectId) || null;

    if (!project.value) {
      throw new Error(`Project with ID ${projectId} not found`);
    }

    // Fetch data for badges
    fetchJobCount();
    fetchProfileCount();
    fetchRecordingCount();
    fetchRepositoryStatus();

    // Set up message bus listeners
    MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
    MessageBus.on(MessageBus.PROFILES_COUNT_CHANGED, handleProfileCountChange);
    MessageBus.on(MessageBus.RECORDINGS_COUNT_CHANGED, handleRecordingCountChange);
    MessageBus.on(MessageBus.REPOSITORY_STATUS_CHANGED, handleRepositoryStatusChange);
    MessageBus.on(MessageBus.PROFILE_INITIALIZATION_STARTED, handleProfileInitializationStarted);
  } catch (error) {
    ToastService.error('Failed to load projects', 'Cannot load projects from the server. Please try again later.');
    router.push('/projects');
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  // Clean up message bus listeners
  MessageBus.off(MessageBus.JOBS_COUNT_CHANGED);
  MessageBus.off(MessageBus.PROFILES_COUNT_CHANGED);
  MessageBus.off(MessageBus.RECORDINGS_COUNT_CHANGED);
  MessageBus.off(MessageBus.REPOSITORY_STATUS_CHANGED);
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
</style>

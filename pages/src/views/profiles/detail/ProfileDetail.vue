<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="profile-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="hamburger-toggle d-flex align-items-center justify-content-center">
          <button class="btn btn-sm hamburger-btn" @click="toggleSidebar">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </button>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Profile Header -->
          <div class="p-3 border-bottom">
            <div v-if="!sidebarCollapsed">
              <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 260px;">{{
                  profile?.name || 'Loading...'
                }}</h5>
              <p class="text-muted mb-0 fs-7">Profile details</p>
            </div>
          </div>

          <!-- Mode Switcher -->
          <div v-if="!sidebarCollapsed" class="mode-switcher p-2">
            <div class="toggle-switch-container">
              <div class="toggle-switch">
                <button
                  class="toggle-button"
                  :class="{ 'active': selectedMode === 'JDK' }"
                  @click="selectedMode = 'JDK'"
                >
                  JDK
                </button>
                <button
                  class="toggle-button"
                  :class="{ 'active': selectedMode === 'Custom' }"
                  @click="selectedMode = 'Custom'"
                >
                  Custom
                </button>
              </div>
            </div>
          </div>

          <div class="sidebar-menu" v-if="!sidebarCollapsed">
            <!-- JDK Mode Menu -->
            <template v-if="selectedMode === 'JDK'">
              <div class="nav-section">
                <div class="nav-section-title">OVERVIEW</div>
                <div class="nav-items">
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/overview`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-gear"></i>
                    <span>Configuration</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/guardian`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-shield-check"></i>
                    <span>Guardian Analysis</span>
                    <span v-if="warningCount > 0" class="nav-badge nav-badge-danger bg-danger text-white">{{
                        warningCount
                      }}</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/auto-analysis`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-robot"></i>
                    <span>Auto Analysis</span>
                    <span v-if="autoAnalysisWarningCount > 0"
                          class="nav-badge nav-badge-danger bg-danger text-white">{{ autoAnalysisWarningCount }}</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/event-types`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-list-check"></i>
                    <span>Event Types</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/events`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-collection"></i>
                    <span>Events</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/information`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-info-circle"></i>
                    <span>Information</span>
                  </router-link>
                  <router-link
                      v-if="hasPerformanceCounters"
                      :to="`/projects/${projectId}/profiles/${profileId}/performance-counters`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-speedometer2"></i>
                    <span>Performance Counters</span>
                  </router-link>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">RUNTIME</div>
                <div class="nav-items">
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/threads`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-graph-up"></i>
                    <span>Thread Statistics</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/threads-timeline`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-clock-history"></i>
                    <span>Thread Timeline</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/jit-compilation`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-lightning"></i>
                    <span>JIT Compilation</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/heap-memory`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-memory"></i>
                    <span>Heap Memory</span>
                  </router-link>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">FLAMEGRAPHS</div>
                <div class="nav-items">
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/flamegraphs/primary`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-fire"></i>
                    <span>Primary</span>
                  </router-link>
                  <a href="#"
                     @click.prevent="navigateToDifferentialPage('flamegraphs')"
                     class="nav-item"
                     :class="{ 'active': $route.path.includes('/flamegraphs/differential') }"
                  >
                    <i class="bi bi-file-diff"></i>
                    <span>Differential</span>
                    <i v-if="!secondaryProfile" class="bi bi-lock ms-auto"
                       title="Select a secondary profile to enable this page"></i>
                  </a>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/flamegraphs/saved`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-bookmark"></i>
                    <span>Saved</span>
                  </router-link>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">SUBSECOND GRAPHS</div>
                <div class="nav-items">
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/subsecond/primary`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-bar-chart"></i>
                    <span>Primary</span>
                  </router-link>
                  <a href="#"
                     @click.prevent="navigateToDifferentialPage('subsecond')"
                     class="nav-item"
                     :class="{ 'active': $route.path.includes('/subsecond/differential') }"
                  >
                    <i class="bi bi-file-bar-graph"></i>
                    <span>Differential</span>
                    <i v-if="!secondaryProfile" class="bi bi-lock ms-auto"
                       title="Select a secondary profile to enable this page"></i>
                  </a>
                </div>
              </div>
            </template>

            <!-- Application Mode Menu -->
            <template v-else>
              <div class="nav-section">
                <div class="nav-section-title">CUSTOM</div>
                <div class="nav-items">
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/application/http`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-globe"></i>
                    <span>HTTP Request / Response</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/application/jdbc`"
                      class="nav-item"
                      active-class="active">
                    <i class="bi bi-database"></i>
                    <span>JDBC Statements</span>
                  </router-link>
                  <router-link
                      :to="`/projects/${projectId}/profiles/${profileId}/application/jdbc-pool`"
                      class="nav-item"
                      active-class="active">
                    <i class="bi bi-diagram-3"></i>
                    <span>JDBC Connection Pools</span>
                  </router-link>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="profile-main-content">
      <!-- Secondary Profile Selection Bar -->
      <div class="secondary-profile-bar p-3 mb-3 border-bottom" v-if="!sidebarCollapsed">
        <div class="d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center">
            <span class="me-2 fw-bold">Secondary Profile:</span>

            <!-- Secondary Profile Status -->
            <div v-if="!secondaryProfile" class="secondary-profile-placeholder">
              <span class="text-muted">No secondary profile selected</span>
              <button class="btn btn-sm btn-primary ms-3" @click="showSecondaryProfileModal" data-bs-toggle="modal"
                      data-bs-target="#secondaryProfileModal">
                <i class="bi bi-plus-circle me-1"></i> Select Profile
              </button>
            </div>

            <!-- Selected Secondary Profile Info -->
            <div v-else class="selected-profile-info">
              <span class="badge bg-info text-dark me-2 px-2 py-1">
                <i class="bi bi-file-earmark-text me-1"></i> {{ secondaryProfile.name }}
              </span>
              <span v-if="selectedProjectId !== projectId" class="badge bg-secondary text-white me-2 px-2 py-1">
                <i class="bi bi-folder me-1"></i> {{ selectedProjectId }}
              </span>
            </div>
          </div>

          <!-- Clear button positioned at the right -->
          <div v-if="secondaryProfile">
            <button
                class="btn btn-sm btn-outline-danger"
                @click="clearSecondaryProfile"
                title="Clear secondary profile"
            >
              <i class="bi bi-x"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- Profile Selection Modal -->
      <div class="modal fade" id="profileSelectionModal" tabindex="-1" aria-labelledby="profileSelectionModalLabel"
           aria-hidden="true">
        <div class="modal-dialog modal-lg">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title" id="profileSelectionModalLabel">
                Select Secondary Profile
              </h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
              <!-- Project Selection -->
              <div class="mb-3">
                <label for="projectSelection" class="form-label">Project:</label>
                <select
                    id="projectSelection"
                    v-model="selectedProjectId"
                    class="form-select"
                    :disabled="loadingProjects"
                    @change="handleProjectChange"
                >
                  <option
                      v-for="project in availableProjects"
                      :key="project.id"
                      :value="project.id"
                  >
                    {{ project.name }} {{ project.id === projectId ? '(Current)' : '' }}
                  </option>
                </select>
              </div>

              <!-- Loading Indicator -->
              <div v-if="loadingProfiles" class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                  <span class="visually-hidden">Loading profiles...</span>
                </div>
                <p class="mt-2">Loading profiles from project {{ selectedProjectId }}...</p>
              </div>

              <!-- Profiles Table -->
              <div v-else class="table-responsive">
                <table class="table table-hover">
                  <thead>
                  <tr>
                    <th>Profile Name</th>
                    <th>Created</th>
                    <th>Duration</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                  </thead>
                  <tbody>
                  <tr v-for="p in availableProfiles" :key="p.id"
                      :class="{ 'table-primary': p.id === profileId && selectedProjectId === projectId }">
                    <td>{{ p.name }}</td>
                    <td>{{ p.createdAt }}</td>
                    <td>{{ FormattingService.formatDurationInMillis2Units(p.durationInMillis) }}</td>
                    <td>
                      <span v-if="p.id === profileId && selectedProjectId === projectId" class="badge bg-primary">Primary</span>
                      <span v-else-if="p.id === selectedSecondaryProfileId" class="badge bg-success">Selected</span>
                    </td>
                    <td>
                      <button
                          v-if="!(p.id === profileId && selectedProjectId === projectId)"
                          class="btn btn-sm btn-primary"
                          @click="selectSecondaryProfile(p.id)"
                          :disabled="p.id === selectedSecondaryProfileId"
                      >
                        {{ p.id === selectedSecondaryProfileId ? 'Selected' : 'Select' }}
                      </button>
                      <span v-else class="text-muted">Cannot select primary profile</span>
                    </td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Content Area without tabs -->
      <div class="profile-content-container mb-4">
        <div class="card">
          <div class="card-body">
            <router-view
                :profile="profile"
                :secondaryProfile="secondaryProfile"
            ></router-view>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Secondary Profile Modal -->
  <div class="modal fade" id="secondaryProfileModal" tabindex="-1" aria-labelledby="secondaryProfileModalLabel"
       aria-hidden="true">
    <div class="modal-dialog modal-xl" style="max-width: 80%; width: 80%;">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="secondaryProfileModalLabel">Select Profile</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <!-- Project Selection Dropdown -->
          <div class="mb-3">
            <label for="projectSelection" class="form-label">Select Project:</label>
            <select
                id="projectSelection"
                class="form-select"
                v-model="selectedProjectId"
                :disabled="loadingProjects"
                @change="handleProjectChange"
            >
              <option
                  v-for="project in availableProjects"
                  :key="project.id"
                  :value="project.id"
              >
                {{ project.name }} {{ project.id === projectId ? '(Current)' : '' }}
              </option>
            </select>
            <div v-if="loadingProjects" class="mt-2">
              <div class="spinner-border spinner-border-sm text-primary" role="status">
                <span class="visually-hidden">Loading projects...</span>
              </div>
              <span class="ms-2 text-muted">Loading projects...</span>
            </div>
          </div>

          <!-- Profiles Table -->
          <div class="mt-4">
            <h6>Profiles</h6>

            <!-- Loading indicator for profiles -->
            <div v-if="loadingProfiles" class="text-center py-4">
              <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading profiles...</span>
              </div>
              <p class="mt-2">Loading profiles...</p>
            </div>

            <!-- No profiles message -->
            <div v-else-if="availableProfiles.length === 0" class="alert alert-info">
              No profiles found for this project.
            </div>

            <!-- Profiles table -->
            <div v-else class="table-responsive">
              <table class="table table-hover">
                <thead>
                <tr>
                  <th>Name</th>
                  <th>Created</th>
                  <th>Duration</th>
                  <th>Status</th>
                </tr>
                </thead>
                <tbody>
                <tr
                    v-for="p in availableProfiles"
                    :key="p.id"
                    :class="{ 
                      'table-primary': p.id === profileId && selectedProjectId === projectId,
                      'table-success': p.id === selectedSecondaryProfileId
                    }"
                    style="cursor: pointer;"
                    @click="selectSecondaryProfile(p)"
                >
                  <td>{{ p.name }}</td>
                  <td>{{ p.createdAt }}</td>
                  <td>{{ FormattingService.formatDurationInMillis2Units(p.durationInMillis) }}</td>
                  <td>
                      <span v-if="p.id === profileId && selectedProjectId === projectId" class="badge bg-primary">
                        Primary
                      </span>
                    <span v-else-if="p.id === selectedSecondaryProfileId" class="badge bg-success">
                        Selected
                      </span>
                    <span v-else class="badge bg-light text-dark">
                        Available
                      </span>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
          <button
              type="button"
              class="btn btn-primary"
              data-bs-dismiss="modal"
              :disabled="!secondaryProfile"
          >
            Select and Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import ToastService from '@/services/ToastService';
import ProjectsClient from "@/services/ProjectsClient.ts";
import Profile from "@/services/model/Profile.ts";
import Project from "@/services/model/Project.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import FormattingService from "@/services/FormattingService";
import ProfileInfo from "@/services/project/model/ProfileInfo.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import MessageBus from "@/services/MessageBus.ts";
import GuardianService from "@/services/guardian/GuardianService";
import AutoAnalysisService from "@/services/AutoAnalysisService";
import ProfilePerformanceCountersClient from "@/services/ProfilePerformanceCountersClient";

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;
const profileService = new ProjectProfileClient(projectId);

const profile = ref<Profile | null>(null);
const secondaryProfile = ref<Profile | null>(null);
const selectedSecondaryProfileId = ref<string>('');
const selectedProjectId = ref<string>(projectId);
const availableProfiles = ref<Profile[]>([]);
const availableProjects = ref<Project[]>([]);
const loadingProfiles = ref(false);
const loadingProjects = ref(false);
const loading = ref(true);
const sidebarCollapsed = ref(false);
const modalInstance = ref<any>(null);
const secondaryProfileModalInstance = ref<any>(null);
const warningCount = ref<number>(0);
const autoAnalysisWarningCount = ref<number>(0);
const hasPerformanceCounters = ref<boolean>(true); // Default to true until checked
// Initialize mode from sessionStorage or default to 'JDK'
const getStoredMode = (): 'JDK' | 'Custom' => {
  const stored = sessionStorage.getItem('profile-sidebar-mode');
  return (stored === 'JDK' || stored === 'Custom') ? stored : 'JDK';
};

const selectedMode = ref<'JDK' | 'Custom'>(getStoredMode());

// Watch for mode changes and persist to sessionStorage
watch(selectedMode, (newMode) => {
  sessionStorage.setItem('profile-sidebar-mode', newMode);
});

onMounted(async () => {
  try {
    // Fetch profile details, available projects, and profiles in parallel
    profile.value = await profileService.get(profileId);

    // Load guardian warning count
    try {
      const guardData = await GuardianService.list(projectId, profileId);
      // Count WARNING severity items across all categories
      let count = 0;
      guardData.forEach(category => {
        count += category.results.filter(result => result.severity === "WARNING").length;
      });
      warningCount.value = count;
    } catch (error) {
      console.error('Failed to load guardian data:', error);
      warningCount.value = 0;
    }

    // Load auto analysis warning count
    try {
      const analysisData = await AutoAnalysisService.rules(projectId, profileId);
      // Count WARNING severity items
      autoAnalysisWarningCount.value = analysisData.filter(rule => rule.severity === "WARNING").length;
    } catch (error) {
      console.error('Failed to load auto analysis data:', error);
      autoAnalysisWarningCount.value = 0;
    }

    // Check if performance counters data exists
    try {
      const exists = await ProfilePerformanceCountersClient.exists(projectId, profileId);
      hasPerformanceCounters.value = exists === true;
    } catch (error) {
      console.error('Failed to check performance counters existence:', error);
      hasPerformanceCounters.value = false;
    }

    // Set current project as the selected project
    selectedProjectId.value = projectId;

    // Load profiles for the current project
    await loadProfilesForProject(projectId);

    // Initialize the modals
    if (typeof bootstrap !== 'undefined') {
      const profileModalEl = document.getElementById('profileSelectionModal');
      if (profileModalEl) {
        modalInstance.value = new bootstrap.Modal(profileModalEl);
      }

      const secondaryProfileModalEl = document.getElementById('secondaryProfileModal');
      if (secondaryProfileModalEl) {
        secondaryProfileModalInstance.value = new bootstrap.Modal(secondaryProfileModalEl);
      }
    }

    // Check if there's a previously selected secondary profile in SecondaryProfileService
    const savedProfile = SecondaryProfileService.get();

    if (savedProfile && savedProfile.id !== profileId) {
      selectedSecondaryProfileId.value = savedProfile.id;
      selectedProjectId.value = savedProfile.projectId;

      try {
        loadingProfiles.value = true;
        // Create a profile client for the saved project
        const savedProjectProfileClient = new ProjectProfileClient(savedProfile.projectId);
        secondaryProfile.value = await savedProjectProfileClient.get(savedProfile.id);
      } catch (error) {
        console.error('Failed to load secondary profile:', error);
        SecondaryProfileService.remove(); // Clear invalid secondary profile
      } finally {
        loadingProfiles.value = false;
      }
    }

    // Check if user is trying to access differential pages without a secondary profile
    const currentPath = route.path;
    if (
        (currentPath.includes('/flamegraphs/differential') || currentPath.includes('/subsecond/differential')) &&
        !secondaryProfile.value
    ) {
      // Redirect to the corresponding primary page
      const redirectPath = currentPath.replace('/differential', '/primary');
      router.replace(redirectPath);

      // Show a message
      ToastService.warn('No Secondary Profile', "Please select a secondary profile to view differential analysis");

      // Highlight the secondary profile selection bar with red color
      setTimeout(() => {
        const selectionBar = document.querySelector('.secondary-profile-bar');
        if (selectionBar) {
          selectionBar.classList.add('highlight-selection-bar-error');
          setTimeout(() => {
            selectionBar.classList.remove('highlight-selection-bar-error');
          }, 2000);
        }
      }, 500); // Small delay to ensure the page has rendered
    }
  } catch (error) {
    console.error('Failed to load profile:', error);
    ToastService.error('Failed to load profile');
    router.push(`/projects/${projectId}/profiles`);
  } finally {
    loading.value = false;
  }
});

// Load profiles for a specific project
const loadProfilesForProject = async (projectId: string): Promise<Profile[]> => {
  loadingProfiles.value = true;
  try {
    // Always create a profile client for the specified project
    const projectProfileClient = new ProjectProfileClient(projectId);
    const profiles = await projectProfileClient.list();

    availableProfiles.value = profiles;
    return profiles;
  } catch (error) {
    console.error(`Failed to load profiles for project ${projectId}:`, error);
    ToastService.error('Failed to load profiles');
    return [];
  } finally {
    loadingProfiles.value = false;
  }
};

// Handle project selection change
const handleProjectChange = async () => {
  if (selectedProjectId.value) {
    await loadProfilesForProject(selectedProjectId.value);
  } else {
    availableProfiles.value = []; // Clear profiles if no project selected
  }
};

// Select a secondary profile
const selectSecondaryProfile = async (profile: ProfileInfo) => {
  // Don't allow selecting the primary profile as the secondary profile
  if (profile.id === profileId && selectedProjectId.value === projectId) {
    ToastService.error("Cannot select primary profile as secondary profile");
    return;
  }

  selectedSecondaryProfileId.value = profile.id;

  try {
    loadingProfiles.value = true;
    // Create a profile client for the selected project (which may differ from the current project)
    const selectedProjectProfileClient = new ProjectProfileClient(selectedProjectId.value);
    const secondaryData = await selectedProjectProfileClient.get(selectedSecondaryProfileId.value);
    secondaryProfile.value = secondaryData;

    // Save the secondary profile using SecondaryProfileService
    const profileInfo: ProfileInfo = {
      id: secondaryData.id,
      projectId: selectedProjectId.value,
      name: secondaryData.name
    };
    SecondaryProfileService.update(profileInfo);

    ToastService.success(`Secondary profile`, `"${secondaryData.name}" selected for comparison`);
    // No longer automatically closing the modal to allow multiple selections
  } catch (error) {
    console.error('Failed to load secondary profile:', error);
    ToastService.error('Failed to load secondary profile');
    selectedSecondaryProfileId.value = '';
    secondaryProfile.value = null;
  } finally {
    loadingProfiles.value = false;
  }
};

// Clear the secondary profile
const clearSecondaryProfile = () => {
  secondaryProfile.value = null;
  selectedSecondaryProfileId.value = '';
  // Don't reset the project selection to maintain user's context

  SecondaryProfileService.remove();
  ToastService.success('Secondary profile cleared', 'Now, no secondary profile selected for comparison');
};

const deleteProfile = async () => {
  if (!profile.value) return;

  if (confirm(`Are you sure you want to delete profile "${profile.value.name}"?`)) {
    try {
      await profileService.delete(profileId);
      ToastService.success('Profile deleted successfully')

      // Navigate back to profiles list
      router.push(`/projects/${projectId}/profiles`);
    } catch (error) {
      console.error('Failed to delete profile:', error);
      ToastService.error('Failed to delete profile');
    }
  }
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  MessageBus.emit(MessageBus.SIDEBAR_CHANGED, null);
};

// Navigate to differential pages only if secondary profile is selected
const navigateToDifferentialPage = (type: 'flamegraphs' | 'subsecond') => {
  if (secondaryProfile.value) {
    router.push(`/projects/${projectId}/profiles/${profileId}/${type}/differential`);
  } else {
    // Show a toast message that secondary profile selection is required
    ToastService.warn('No Secondary Profile','Please select a secondary profile for comparison');

    // Highlight the secondary profile selection bar with red color without scrolling
    const selectionBar = document.querySelector('.secondary-profile-bar');
    if (selectionBar) {
      selectionBar.classList.add('highlight-selection-bar-error');
      setTimeout(() => {
        selectionBar.classList.remove('highlight-selection-bar-error');
      }, 2000);
    }
  }
};

// Show the secondary profile modal
const showSecondaryProfileModal = async () => {
  // Reset project selection to current project
  selectedProjectId.value = projectId;

  // Load projects if not already loaded
  if (availableProjects.value.length === 0) {
    loadingProjects.value = true;
    try {
      availableProjects.value = await ProjectsClient.list();
    } catch (error) {
      console.error('Failed to load projects:', error);
      ToastService.warn("Failed to load projects");
    } finally {
      loadingProjects.value = false;
    }
  }

  // Load profiles for the current project
  await loadProfilesForProject(projectId);

  // The modal will be shown by Bootstrap's data-bs-toggle and data-bs-target attributes
};
</script>

<style scoped>
/* Content container styles */
.profile-content-container {
  width: 100%;
}

.card {
  border: none;
  border-radius: 0;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Sidebar styles */
.profile-sidebar {
  width: 280px;
  min-height: 100vh;
  background-color: #fff;
  border-right: 1px solid #dee2e6;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
}

.profile-sidebar.collapsed {
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

.nav-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
  height: 18px;
  min-width: 18px;
  padding: 0 5px;
  margin-left: auto;
}

.nav-badge-danger {
  background-color: #dc3545;
  color: white;
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

.profile-main-content {
  flex: 1;
  padding-left: 1rem;
  transition: all 0.3s ease;
  overflow: hidden;
}

.secondary-profile-bar {
  background-color: #f8f9fa;
  border-radius: 0.25rem 0.25rem 0 0;
  box-shadow: 0 -1px 0 rgba(0, 0, 0, 0.05);
}

.secondary-profile-bar select {
  background-color: white;
  border-color: #dee2e6;
}

.secondary-profile-bar .btn-outline-danger {
  border-color: #dc3545;
  color: #dc3545;
  padding: 0.25rem 0.5rem;
  font-size: 0.875rem;
}

.secondary-profile-bar .btn-outline-danger:hover {
  background-color: #dc3545;
  color: white;
}

.toggle-switch-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.toggle-switch {
  display: flex;
  border-radius: 0.5rem;
  overflow: hidden;
  cursor: pointer;
  height: 30px;
  width: 100%;
  max-width: 200px;
  background-color: #f0f2f5;
  transition: background-color 0.3s ease;
  position: relative;
  border: 1px solid #dfe2e6;
  padding: 2px;
}

.toggle-switch::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  height: calc(100% - 4px);
  width: calc(50% - 4px);
  background-color: #5e64ff;
  border-radius: 0.25rem;
  transition: transform 0.3s ease;
  z-index: 1;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transform: translateX(0);
}

.toggle-switch:has(.toggle-button:last-child.active)::before {
  transform: translateX(calc(100% + 4px));
}

.toggle-button {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 0.75rem;
  font-weight: 600;
  color: #495057;
  background-color: transparent;
  border: none;
  transition: color 0.3s ease;
  position: relative;
  z-index: 2;

  &.active {
    color: #fff;
  }
}

@keyframes pulse-highlight {
  0% {
    background-color: #f8f9fa;
  }
  50% {
    background-color: rgba(0, 123, 255, 0.15);
  }
  100% {
    background-color: #f8f9fa;
  }
}

@keyframes pulse-highlight-error {
  0% {
    background-color: #f8f9fa;
  }
  50% {
    background-color: rgba(220, 53, 69, 0.15);
  }
  100% {
    background-color: #f8f9fa;
  }
}

/* Modal styles */

.selected-profile-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.table {
  font-size: 0.9rem;
}

.table td {
  vertical-align: middle;
}

.badge {
  font-weight: 500;
}

.secondary-profile-placeholder {
  display: flex;
  align-items: center;
}

.highlight-selection-bar {
  animation: pulse-highlight 2s ease-in-out;
  box-shadow: 0 0 8px rgba(0, 123, 255, 0.5);
}

.highlight-selection-bar-error {
  animation: pulse-highlight-error 2s ease-in-out;
  box-shadow: 0 0 8px rgba(220, 53, 69, 0.5);
}
</style>

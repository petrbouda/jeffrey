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
          <div class="p-3 border-bottom d-flex align-items-center">
            <div class="d-flex align-items-center" v-if="!sidebarCollapsed">
              <div
                  class="avatar avatar-l rounded-circle bg-soft-primary me-2 d-flex align-items-center justify-content-center">
                <span class="text-primary fw-bold">P</span>
              </div>
              <div>
                <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 180px;">{{
                    profile?.name || 'Loading...'
                  }}</h5>
                <p class="text-muted mb-0 fs-7">Profile details</p>
              </div>
            </div>
          </div>

          <div class="py-3" v-if="!sidebarCollapsed">
            <div class="nav-category px-3">OVERVIEW</div>
            <ul class="nav flex-column">
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/overview`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-speedometer2 me-2"></i>
                  <span>Overview</span>
                </router-link>
              </li>
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/guardian`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-shield-check me-2"></i>
                  <span>Guardian Analysis</span>
                  <span class="badge rounded-pill bg-warning ms-auto">3</span>
                </router-link>
              </li>
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/information`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-info-circle me-2"></i>
                  <span>Information</span>
                </router-link>
              </li>
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/events`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-list-ul me-2"></i>
                  <span>Events</span>
                </router-link>
              </li>
            </ul>

            <div class="nav-category px-3">Threads</div>
            <ul class="nav flex-column">
              <li class="nav-item px-3 py-1">
                <a href="#" class="nav-link d-flex align-items-center py-2">
                  <i class="bi bi-graph-up me-2"></i>
                  <span>Statistics</span>
                </a>
              </li>
              <li class="nav-item px-3 py-1">
                <a href="#" class="nav-link d-flex align-items-center py-2">
                  <i class="bi bi-clock-history me-2"></i>
                  <span>Timeline</span>
                </a>
              </li>
            </ul>

            <div class="nav-category px-3">Flamegraphs</div>
            <ul class="nav flex-column">
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/flamegraphs/primary`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-fire me-2"></i>
                  <span>Primary</span>
                </router-link>
              </li>
              <li class="nav-item px-3 py-1">
                <a href="#" 
                   @click.prevent="navigateToDifferentialPage('flamegraphs')"
                   class="nav-link d-flex align-items-center py-2"
                   :class="{ 'active': $route.path.includes('/flamegraphs/differential') }"
                >
                  <i class="bi bi-file-diff me-2"></i>
                  <span>Differential</span>
                  <i v-if="!secondaryProfile" class="bi bi-lock ms-auto text-muted" 
                     title="Select a secondary profile to enable this page"></i>
                </a>
              </li>
            </ul>

            <div class="nav-category px-3">SubSecond Graphs</div>
            <ul class="nav flex-column">
              <li class="nav-item px-3 py-1">
                <router-link
                    :to="`/projects/${projectId}/profiles/${profileId}/subsecond/primary`"
                    class="nav-link d-flex align-items-center py-2"
                    active-class="active"
                >
                  <i class="bi bi-bar-chart me-2"></i>
                  <span>Primary</span>
                </router-link>
              </li>
              <li class="nav-item px-3 py-1">
                <a href="#" 
                   @click.prevent="navigateToDifferentialPage('subsecond')"
                   class="nav-link d-flex align-items-center py-2"
                   :class="{ 'active': $route.path.includes('/subsecond/differential') }"
                >
                  <i class="bi bi-file-bar-graph me-2"></i>
                  <span>Differential</span>
                  <i v-if="!secondaryProfile" class="bi bi-lock ms-auto text-muted" 
                     title="Select a secondary profile to enable this page"></i>
                </a>
              </li>
            </ul>

            <div class="nav-category px-3">Actions</div>
            <ul class="nav flex-column">
              <li class="nav-item px-3 py-1">
                <a href="#" class="nav-link d-flex align-items-center py-2" @click.prevent="compareProfiles">
                  <i class="bi bi-bar-chart me-2"></i>
                  <span>Compare with Profile</span>
                </a>
              </li>
              <li class="nav-item px-3 py-1">
                <a href="#" class="nav-link d-flex align-items-center py-2 text-danger" @click.prevent="deleteProfile">
                  <i class="bi bi-trash me-2"></i>
                  <span>Delete</span>
                </a>
              </li>
            </ul>
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
            <select 
              v-model="selectedSecondaryProfileId" 
              class="form-select form-select-sm" 
              style="width: 500px;"
              :disabled="loadingProfiles"
              @change="handleSecondaryProfileChange"
            >
              <option value="">None (Select a profile for comparison)</option>
              <option 
                v-for="p in availableProfiles" 
                :key="p.id" 
                :value="p.id"
                :disabled="p.id === profileId"
              >
                {{ p.name }} {{ p.id === profileId ? '(Current)' : '' }}
              </option>
            </select>
            <div v-if="loadingProfiles" class="spinner-border spinner-border-sm text-primary ms-2" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <div v-if="secondaryProfile" class="d-flex align-items-center">
            <div class="small text-muted me-3">
              <span class="d-inline-block me-3">
                <i class="bi bi-calendar me-1"></i> {{ formatDate(secondaryProfile.createdAt) }}
              </span>
              <span class="d-inline-block">
                <i class="bi bi-clock-history me-1"></i> {{ secondaryProfile.duration ? `${secondaryProfile.duration}s` : 'N/A' }}
              </span>
            </div>
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

  <!-- Toast for messages -->
  <div class="position-fixed bottom-0 end-0 p-3" style="z-index: 11">
    <div id="profileDetailToast" class="toast align-items-center text-white border-0"
         role="alert" aria-live="assertive" aria-atomic="true">
      <div class="d-flex">
        <div class="toast-body">
          {{ toastMessage }}
        </div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto"
                data-bs-dismiss="toast" aria-label="Close"></button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Profile} from '@/types';
import ProfileService from '@/services/ProfileService';
import ToastService from '@/services/ToastService';
import Utils from '@/services/Utils';

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;
const profileService = new ProfileService(projectId);

const profile = ref<Profile | null>(null);
const secondaryProfile = ref<Profile | null>(null);
const selectedSecondaryProfileId = ref<string>('');
const availableProfiles = ref<Profile[]>([]);
const loadingProfiles = ref(false);
const toastMessage = ref('');
const loading = ref(true);
const sidebarCollapsed = ref(false);

onMounted(async () => {
  try {
    // Fetch profile details and available profiles in parallel
    const [profileData, allProfiles] = await Promise.all([
      profileService.get(profileId),
      loadAvailableProfiles()
    ]);
    
    profile.value = profileData;
    
    // Check if there's a previously selected secondary profile in localStorage
    const savedSecondaryProfileId = localStorage.getItem(`secondaryProfile_${projectId}_${profileId}`);
    if (savedSecondaryProfileId && savedSecondaryProfileId !== profileId) {
      selectedSecondaryProfileId.value = savedSecondaryProfileId;
      await handleSecondaryProfileChange();
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
      toastMessage.value = 'Please select a secondary profile to view differential analysis';
      showToast('danger');
      
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
    toastMessage.value = 'Failed to load profile';
    showToast();
    router.push(`/projects/${projectId}/profiles`);
  } finally {
    loading.value = false;
  }
});

// Load all available profiles for the secondary profile selection dropdown
const loadAvailableProfiles = async (): Promise<Profile[]> => {
  loadingProfiles.value = true;
  try {
    const profiles = await profileService.list();
    availableProfiles.value = profiles;
    return profiles;
  } catch (error) {
    console.error('Failed to load available profiles:', error);
    toastMessage.value = 'Failed to load available profiles';
    showToast('danger');
    return [];
  } finally {
    loadingProfiles.value = false;
  }
};

// Handle secondary profile selection change
const handleSecondaryProfileChange = async () => {
  if (selectedSecondaryProfileId.value) {
    try {
      loadingProfiles.value = true;
      const secondaryData = await profileService.get(selectedSecondaryProfileId.value);
      secondaryProfile.value = secondaryData;
      localStorage.setItem(`secondaryProfile_${projectId}_${profileId}`, selectedSecondaryProfileId.value);
      
      toastMessage.value = `Secondary profile "${secondaryData.name}" selected for comparison`;
      showToast();
    } catch (error) {
      console.error('Failed to load secondary profile:', error);
      toastMessage.value = 'Failed to load secondary profile';
      showToast();
      selectedSecondaryProfileId.value = '';
      secondaryProfile.value = null;
    } finally {
      loadingProfiles.value = false;
    }
  } else {
    clearSecondaryProfile();
  }
};

// Clear the secondary profile
const clearSecondaryProfile = () => {
  secondaryProfile.value = null;
  selectedSecondaryProfileId.value = '';
  localStorage.removeItem(`secondaryProfile_${projectId}_${profileId}`);
  
  toastMessage.value = 'Secondary profile cleared';
  showToast();
};

const formatDate = (dateString?: string): string => {
  if (!dateString) return 'N/A';
  return Utils.formatDate(dateString, true);
};

const exportProfile = () => {
  toastMessage.value = 'Profile export started';
  showToast();
};

const compareProfiles = () => {
  toastMessage.value = 'Profile comparison feature coming soon';
  showToast();
};

const shareProfile = () => {
  toastMessage.value = 'Profile sharing feature coming soon';
  showToast();
};

const deleteProfile = async () => {
  if (!profile.value) return;

  if (confirm(`Are you sure you want to delete profile "${profile.value.name}"?`)) {
    try {
      await profileService.delete(profileId);

      toastMessage.value = 'Profile deleted successfully';
      showToast();

      // Navigate back to profiles list
      router.push(`/projects/${projectId}/profiles`);
    } catch (error) {
      console.error('Failed to delete profile:', error);
      toastMessage.value = 'Failed to delete profile';
      showToast();
    }
  }
};

const showToast = (type: 'success' | 'danger' = 'success') => {
  // Get the toast element
  const toastEl = document.getElementById('profileDetailToast');
  if (toastEl) {
    // Remove existing color classes
    toastEl.classList.remove('bg-success', 'bg-danger');
    
    // Add appropriate color class
    if (type === 'danger') {
      toastEl.classList.add('bg-danger');
    } else {
      toastEl.classList.add('bg-success');
    }
  }
  
  // Show the toast
  ToastService.show('profileDetailToast', toastMessage.value);
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const openAnalysisPanel = () => {
  toastMessage.value = 'Analysis panel opened';
  showToast();
};

const openFiltersPanel = () => {
  toastMessage.value = 'Filters panel opened';
  showToast();
};

const openAnnotationsPanel = () => {
  toastMessage.value = 'Annotations panel opened';
  showToast();
};

// Navigate to differential pages only if secondary profile is selected
const navigateToDifferentialPage = (type: 'flamegraphs' | 'subsecond') => {
  if (secondaryProfile.value) {
    router.push(`/projects/${projectId}/profiles/${profileId}/${type}/differential`);
  } else {
    // Show a toast message that secondary profile selection is required
    toastMessage.value = 'Please select a secondary profile for comparison';
    showToast('danger');
    
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

.highlight-selection-bar {
  animation: pulse-highlight 2s ease-in-out;
  box-shadow: 0 0 8px rgba(0, 123, 255, 0.5);
}

.highlight-selection-bar-error {
  animation: pulse-highlight-error 2s ease-in-out;
  box-shadow: 0 0 8px rgba(220, 53, 69, 0.5);
}

@keyframes pulse-highlight {
  0% { background-color: #f8f9fa; }
  50% { background-color: rgba(0, 123, 255, 0.15); }
  100% { background-color: #f8f9fa; }
}

@keyframes pulse-highlight-error {
  0% { background-color: #f8f9fa; }
  50% { background-color: rgba(220, 53, 69, 0.15); }
  100% { background-color: #f8f9fa; }
}
</style>

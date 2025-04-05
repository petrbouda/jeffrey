<template>
  <div class="card w-100">
    <div class="card-header bg-soft-blue text-white">
      <h5 class="card-title mb-0">Profiles</h5>
    </div>

    <div class="card-body">
      <!-- Search Box -->
      <div class="search-box mb-3">
        <div class="input-group input-group-sm phoenix-search">
          <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
            <i class="bi bi-search text-primary"></i>
          </span>
          <input
              type="text"
              class="form-control border-0 py-2"
              placeholder="Search profiles..."
              v-model="searchQuery"
              @input="filterProfiles"
          >
        </div>
      </div>

      <!-- Loading Indicator -->
      <div v-if="loading" class="text-center py-4">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
        <p class="mt-2">Loading profiles...</p>
      </div>

      <!-- Profiles Table -->
      <div v-else class="table-responsive">
        <table class="table table-hover">
          <thead>
          <tr>
            <th style="width: 5%"></th>
            <th style="width: 50%">Name</th>
            <th style="width: 25%">Created at</th>
            <th style="width: 20%" class="text-end">Actions</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="profile in filteredProfiles" :key="profile.id">
            <td>
              <div v-if="!profile.enabled" class="initializing-badge"
                   data-bs-toggle="tooltip" title="Profile is initializing">
                <div class="spinner-grow spinner-grow-sm text-warning me-1" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
              <router-link v-else
                           :to="`/projects/${projectId}/profiles/${profile.id}`"
                           class="btn btn-primary btn-sm"
                           data-bs-toggle="tooltip"
                           @click="selectProfile"
                           title="View Profile">
                <i class="bi bi-eye"></i>
              </router-link>
            </td>
            <td class="fw-bold">
              {{ profile.name }}
              <span v-if="!profile.enabled" class="badge bg-warning text-dark ms-2 small">
                  <span class="spinner-border spinner-border-sm me-1" role="status"
                        style="width: 0.5rem; height: 0.5rem;"></span>
                  Initializing
                </span>
            </td>
            <td>{{ profile.createdAt }}</td>
            <td>
              <div class="d-flex gap-2 justify-content-end">
                <button class="btn btn-outline-secondary btn-sm"
                        @click="editProfile(profile)"
                        data-bs-toggle="tooltip"
                        title="Edit Profile">
                  <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-danger btn-sm"
                        @click="deleteProfile(profile)"
                        data-bs-toggle="tooltip"
                        title="Delete Profile">
                  <i class="bi bi-trash"></i>
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="filteredProfiles.length === 0 && !loading">
            <td colspan="4" class="text-center py-3">
              No profiles found. Create a new profile to get started.
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- Edit Profile Modal -->
  <div class="modal fade" id="editProfileModal" tabindex="-1"
       :class="{ 'show': showEditProfileModal }"
       :style="{ display: showEditProfileModal ? 'block' : 'none' }">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Edit Profile</h5>
          <button type="button" class="btn-close" @click="showEditProfileModal = false"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <label for="editProfileName" class="form-label">Profile Name</label>
            <input
                type="text"
                class="form-control"
                id="editProfileName"
                v-model="editProfileName"
                @keyup.enter="updateProfile"
                placeholder="Enter profile name"
            >
          </div>
          <div v-if="errorMessage" class="alert alert-danger mt-2">
            {{ errorMessage }}
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="showEditProfileModal = false">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="updateProfile" :disabled="updatingProfile">
            <span v-if="updatingProfile" class="spinner-border spinner-border-sm me-2" role="status"></span>
            Update
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show" v-if="showEditProfileModal"></div>

  <!-- Toast for success message -->
  <div class="position-fixed bottom-0 end-0 p-3" style="z-index: 11">
    <div id="profileToast" class="toast align-items-center text-white bg-success border-0"
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
import {onMounted, onUnmounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import ToastService from '@/services/ToastService';
import Profile from "@/services/model/Profile.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";

const route = useRoute();
const projectId = route.params.projectId as string;

const profileClient = new ProjectProfileClient(projectId);

// Data
const profiles = ref<Profile[]>([]);
const filteredProfiles = ref<Profile[]>([]);
const searchQuery = ref('');
const showEditProfileModal = ref(false);
const editProfileName = ref('');
const selectedProfileId = ref('');
const errorMessage = ref('');
const toastMessage = ref('');
const loading = ref(true);
const updatingProfile = ref(false);
const pollInterval = ref<number | null>(null);

// Fetch profiles on component mount
onMounted(async () => {
  try {
    await fetchProfiles();

    // If there are initializing profiles, start polling for updates
    if (profiles.value.some(p => !p.enabled)) {
      startPolling();
    }
  } catch (error) {
    console.error('Failed to load profiles:', error);
    showToast('Failed to load profiles');
  } finally {
    loading.value = false;
  }
});

// Clean up on component unmount
onUnmounted(() => {
  stopPolling();
});

// Methods
const fetchProfiles = async () => {
  const data = await profileClient.list();
  profiles.value = data;
  filterProfiles();
};

const selectProfile = () => {
  SecondaryProfileService.remove()
};

const filterProfiles = () => {
  if (!searchQuery.value) {
    filteredProfiles.value = [...profiles.value];
    return;
  }

  const query = searchQuery.value.toLowerCase();
  filteredProfiles.value = profiles.value.filter(profile =>
      profile.name.toLowerCase().includes(query)
  );
};

const editProfile = (profile: Profile) => {
  selectedProfileId.value = profile.id;
  editProfileName.value = profile.name;
  errorMessage.value = '';
  showEditProfileModal.value = true;
};

const updateProfile = async () => {
  if (!editProfileName.value || editProfileName.value.trim() === '') {
    errorMessage.value = 'Profile name cannot be empty';
    return;
  }

  errorMessage.value = '';
  updatingProfile.value = true;

  try {
    filterProfiles();

    // Reset and close modal
    selectedProfileId.value = '';
    editProfileName.value = '';
    showEditProfileModal.value = false;

    // Show success toast
    toastMessage.value = 'Profile updated successfully!';
    showToast(toastMessage.value);
  } catch (error) {
    console.error('Failed to update profile:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Failed to update profile';
  } finally {
    updatingProfile.value = false;
  }
};

const deleteProfile = async (profile: Profile) => {
  if (confirm(`Are you sure you want to delete profile "${profile.name}"?`)) {
    try {
      await profileClient.delete(profile.id);

      // Remove the profile from the list
      profiles.value = profiles.value.filter(p => p.id !== profile.id);
      filterProfiles();

      // Show success toast
      toastMessage.value = 'Profile deleted successfully!';
      showToast(toastMessage.value);
    } catch (error) {
      console.error('Failed to delete profile:', error);
      toastMessage.value = 'Failed to delete profile';
      showToast(toastMessage.value);
    }
  }
};

const startPolling = () => {
  if (pollInterval.value !== null) return;

  pollInterval.value = window.setInterval(async () => {
    try {
      await fetchProfiles();

      // If no profiles are initializing anymore, stop polling
      if (!profiles.value.some(p => !p.enabled)) {
        stopPolling();
      }
    } catch (error) {
      console.error('Error while polling profiles:', error);
    }
  }, 5000) as unknown as number;
};

const stopPolling = () => {
  if (pollInterval.value !== null) {
    window.clearInterval(pollInterval.value);
    pollInterval.value = null;
  }
};

const showToast = (message: string) => {
  ToastService.show('profileToast', message);
};
</script>

<style scoped>
.modal {
  background-color: rgba(0, 0, 0, 0.5);
}

.phoenix-search {
  border: 1px solid #e0e5eb;
  border-radius: 0.375rem;
  overflow: hidden;

  .search-icon-container {
    width: 40px;
    display: flex;
    justify-content: center;
    background-color: transparent;
  }

  .form-control {
    height: 40px;
    font-size: 0.9rem;

    &:focus {
      box-shadow: none;
    }
  }
}

.initializing-badge {
  display: flex;
  justify-content: center;
  animation: pulse 2s infinite;
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
</style>

<template>
  <div class="row g-4">
    <!-- Page Header -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3">
        <i class="bi bi-person-vcard fs-4 me-2 text-primary"></i>
        <h3 class="mb-0">Profiles</h3>
      </div>
      <p class="text-muted mb-2">
        View and analyze performance profiles created from recordings. Profiles provide
        <span class="fst-italic">detailed insights into application behavior and performance metrics</span>.
      </p>
    </div>

    <!-- Profiles List Header -->
    <div class="col-12">
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
        <table class="table table-hover border">
          <thead class="table-light">
          <tr>
            <th style="width: 5%"></th>
            <th style="width: 60%">Name</th>
            <th style="width: 20%">Created at</th>
            <th style="width: 15%" class="text-end">Actions</th>
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
              <!-- Source type badge - assuming 'JDK' for demonstration -->
              <!-- Note: The sourceType property may need to be added to the Profile model -->
              <span class="badge ms-2 source-badge"
                    :class="profile.sourceType === 'JDK' ? 'jdk-source' : 'default-source'">
                  {{ profile.sourceType || 'JDK' }}
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
       :class="{ 'show': showEditProfileModal, 'd-block': showEditProfileModal }"
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

  <!-- Delete Profile Confirmation Modal -->
  <ConfirmationDialog
      v-model:show="deleteProfileDialog"
      title="Confirm Deletion"
      :message="profileToDelete ? `Are you sure you want to delete the profile '${profileToDelete.name}'?` : 'Are you sure you want to delete this profile?'"
      sub-message="This action cannot be undone."
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteProfileButton"
      modal-id="deleteProfileModal"
      @confirm="confirmDeleteProfile"
  >
    <template #confirm-button>
      <span v-if="deletingProfile" class="spinner-border spinner-border-sm me-2" role="status"></span>
      Delete
    </template>
  </ConfirmationDialog>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import ToastService from '@/services/ToastService';
import Profile from "@/services/model/Profile.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import MessageBus from "@/services/MessageBus";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';

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
const loading = ref(true);
const updatingProfile = ref(false);
const pollInterval = ref<number | null>(null);
// Delete profile modal state
const deleteProfileDialog = ref(false);
const profileToDelete = ref<Profile | null>(null);
const deletingProfile = ref(false);

// Fetch profiles on component mount
onMounted(async () => {
  try {
    await fetchProfiles();

    // If there are initializing profiles, start polling for updates
    if (profiles.value.some(p => !p.enabled)) {
      startPolling();
    }
  } catch (error) {
    ToastService.error('Failed to load profiles');
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

  // Notify sidebar of profile count change
  MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, data.length);
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

    ToastService.success('Profile updated successfully!');
  } catch (error) {
    console.error('Failed to update profile:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Failed to update profile';
  } finally {
    updatingProfile.value = false;
  }
};

const deleteProfile = async (profile: Profile) => {
  profileToDelete.value = profile;
  deleteProfileDialog.value = true;
};

const confirmDeleteProfile = async () => {
  if (!profileToDelete.value) return;

  deletingProfile.value = true;

  try {
    await profileClient.delete(profileToDelete.value.id);

    // Remove the profile from the list
    profiles.value = profiles.value.filter(p => p.id !== profileToDelete.value.id);
    filterProfiles();

    // Notify sidebar of profile count change
    MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, profiles.value.length);

    ToastService.success('Delete profile', 'Profile "' + profileToDelete.value?.name + '" successfully deleted!');
  } catch (error) {
    console.error('Failed to delete profile:', error);
    ToastService.error('Delete Profile', 'Failed to delete profile: ' + profileToDelete.value?.name);
  } finally {
    deletingProfile.value = false;
    deleteProfileDialog.value = false;
    profileToDelete.value = null;
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

.source-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.3rem 0.6rem;
  border-radius: 6px;
  font-size: 0.7rem;
  font-weight: 500;
  white-space: nowrap;
}

.jdk-source {
  background-color: rgba(13, 202, 240, 0.15); /* Light bg-info */
  color: #0991ad; /* Darker shade of info blue */
}

.default-source {
  background-color: rgba(138, 43, 226, 0.15); /* Light blueviolet */
  color: #6a1eae; /* Darker shade of blueviolet */
}
</style>

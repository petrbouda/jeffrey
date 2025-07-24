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
          <tr v-for="profile in filteredProfiles" :key="profile.id" :class="{ 'table-secondary': profile.deleting || !profile.enabled }">
            <td>
              <router-link :to="`/projects/${projectId}/profiles/${profile.id}`"
                           class="btn btn-primary btn-sm"
                           data-bs-toggle="tooltip"
                           @click="selectProfile"
                           title="View Profile"
                           :class="{ 'disabled': profile.deleting || !profile.enabled }"
                           :style="{ 'pointer-events': (profile.deleting || !profile.enabled) ? 'none' : 'auto' }">
                <i class="bi bi-eye"></i>
              </router-link>
            </td>
            <td class="fw-bold" :class="{ 'text-muted': profile.deleting || !profile.enabled }">
              {{ profile.name }}
              <div v-if="profile.deleting" class="ms-2 d-flex align-items-center">
                  <span class="spinner-border spinner-border-sm me-1" role="status"
                        style="width: 0.5rem; height: 0.5rem;"></span>
                  <Badge value="Deleting" variant="danger" size="xs" />
                </div>
              <div v-else-if="!profile.enabled" class="ms-2 d-flex align-items-center">
                  <span class="spinner-border spinner-border-sm me-1" role="status"
                        style="width: 0.5rem; height: 0.5rem;"></span>
                  <Badge value="Initializing" variant="warning" size="xs" />
                </div>
              <!-- Source type badge - assuming 'JDK' for demonstration -->
              <!-- Note: The sourceType property may need to be added to the Profile model -->
              <Badge class="ms-2" :value="profile.sourceType || 'JDK'" :variant="getSourceVariant(profile.sourceType || 'JDK')" size="xs" />
            </td>
            <td :class="{ 'text-muted': profile.deleting || !profile.enabled }">{{ profile.createdAt }}</td>
            <td>
              <div class="d-flex gap-2 justify-content-end">
                <button class="btn btn-outline-secondary btn-sm"
                        @click="editProfile(profile)"
                        data-bs-toggle="tooltip"
                        title="Edit Profile"
                        :disabled="profile.deleting || !profile.enabled">
                  <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-danger btn-sm"
                        @click="deleteProfile(profile)"
                        data-bs-toggle="tooltip"
                        title="Delete Profile"
                        :disabled="profile.deleting">
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
import Badge from '@/components/Badge.vue';

const route = useRoute();
const projectId = route.params.projectId as string;

const profileClient = new ProjectProfileClient(projectId);

// Persistent storage for deleting profiles
const DELETING_PROFILES_KEY = `deleting_profiles_${projectId}`;

const getDeletingProfiles = (): Set<string> => {
  const stored = sessionStorage.getItem(DELETING_PROFILES_KEY);
  return stored ? new Set(JSON.parse(stored)) : new Set();
};

const getSourceVariant = (sourceType: string) => {
  if (sourceType === 'ASYNC_PROFILER' || sourceType === 'Async-Profiler') {
    return 'purple';
  }
  return 'info';
};

const addDeletingProfile = (profileId: string) => {
  const deletingProfiles = getDeletingProfiles();
  deletingProfiles.add(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY, JSON.stringify(Array.from(deletingProfiles)));
};

const removeDeletingProfile = (profileId: string) => {
  const deletingProfiles = getDeletingProfiles();
  deletingProfiles.delete(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY, JSON.stringify(Array.from(deletingProfiles)));
};

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

    // If there are profiles being deleted, start polling for updates
    if (getDeletingProfiles().size > 0) {
      startPolling();
    }
  } catch (error) {
    ToastService.error('Failed to load profiles', 'An error occurred while loading profiles.');
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
  const deletingProfiles = getDeletingProfiles();
  
  // Restore deleting state from storage
  data.forEach(profile => {
    if (deletingProfiles.has(profile.id)) {
      profile.deleting = true;
    }
  });
  
  profiles.value = data;
  filterProfiles();

  // Notify sidebar of profile count change
  MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, data.length);
};

const selectProfile = () => {
  SecondaryProfileService.remove();
  // Clear sidebar mode when selecting a profile from the list
  sessionStorage.removeItem('profile-sidebar-mode');
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
    const updatedProfile = await profileClient.update(selectedProfileId.value, editProfileName.value.trim());
    
    // Update the profile in the local array
    const profileIndex = profiles.value.findIndex(p => p.id === selectedProfileId.value);
    if (profileIndex !== -1) {
      profiles.value[profileIndex] = updatedProfile;
      filterProfiles();
    }

    // Reset and close modal
    const updatedName = editProfileName.value;
    selectedProfileId.value = '';
    editProfileName.value = '';
    showEditProfileModal.value = false;

    ToastService.success('Profile Updated!', 'Profile "' + updatedName + '" successfully updated!');
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
  const profileId = profileToDelete.value.id;
  const profileName = profileToDelete.value.name;

  // Mark profile as deleting in storage and UI
  addDeletingProfile(profileId);
  const profile = profiles.value.find(p => p.id === profileId);
  if (profile) {
    profile.deleting = true;
  }

  // Start polling when deletion begins
  startPolling();

  try {
    profileClient.delete(profileId)
        .then(() => {
          ToastService.success('Profile Deleted', 'Profile "' + profileName + '" successfully deleted!');
          // Remove from storage and UI on success
          removeDeletingProfile(profileId);
          profiles.value = profiles.value.filter(p => p.id !== profileId);
          filterProfiles();
          MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, profiles.value.length);
        })
        .catch(error => {
          console.error('Failed to delete profile:', error);
          ToastService.error('Delete Profile', 'Failed to delete profile: ' + profileName);
          // Remove from storage and reset UI state on error
          removeDeletingProfile(profileId);
          if (profile) {
            profile.deleting = false;
          }
        });

    filterProfiles();

  } catch (error) {
    console.error('Failed to delete profile:', error);
    ToastService.error('Delete Profile', 'Failed to delete profile: ' + profileName);
    // Remove from storage and reset UI state on error
    removeDeletingProfile(profileId);
    if (profile) {
      profile.deleting = false;
    }
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

      // Stop polling if no profiles are initializing and no profiles are being deleted
      if (!profiles.value.some(p => !p.enabled) && getDeletingProfiles().size === 0) {
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

/* Add styling for modal header to properly position the close button */
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header .btn-close {
  margin: -0.5rem -0.5rem -0.5rem auto;
  padding: 0.5rem;
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

</style>

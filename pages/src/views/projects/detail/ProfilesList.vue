<template>
  <PageHeader
    title="Profiles"
    description="View and analyze performance profiles created from recordings. Profiles provide detailed insights into application behavior and performance metrics."
    icon="bi-person-vcard"
  >
    <!-- Search Box -->
    <SearchBox
      v-model="searchQuery"
      placeholder="Search profiles..."
      @update:model-value="filterProfiles"
      class="mb-3"
    />

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading profiles..." />

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
            <router-link :to="generateProfileUrl('overview', profile.id)"
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
            <Badge class="ms-2" :value="profile.eventSource || RecordingEventSource.JDK" :variant="getSourceVariant(profile.eventSource || RecordingEventSource.JDK)" size="xs" />
            <Badge v-if="profile.deleting" value="Deleting" variant="red" size="xs" icon="spinner-border spinner-border-sm" class="ms-1" />
            <Badge v-else-if="!profile.enabled" value="Initializing" variant="orange" size="xs" icon="spinner-border spinner-border-sm" class="ms-1" />
            <!-- Source type badge - assuming 'JDK' for demonstration -->
            <!-- Note: The sourceType property may need to be added to the Profile model -->
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
          <td colspan="4">
            <EmptyState
              icon="bi-person-vcard"
              title="No Profiles Found"
              description="No profiles found. Create a new profile from a recording to get started."
            />
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </PageHeader>

  <!-- Edit Profile Modal -->
  <BaseModal
    ref="editProfileModal"
    modal-id="editProfileModal"
    title="Edit Profile"
    icon="bi-pencil"
    primary-button-text="Update"
    :loading="updatingProfile"
    @submit="updateProfile"
    @cancel="closeEditModal"
  >
    <template #body>
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
    </template>
  </BaseModal>

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
import {onMounted, onUnmounted, ref, computed} from 'vue';
import ToastService from '@/services/ToastService';
import Profile from "@/services/api/model/Profile.ts";
import ProjectProfileClient from "@/services/api/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import MessageBus from "@/services/MessageBus";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import SearchBox from '@/components/SearchBox.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import BaseModal from '@/components/BaseModal.vue';
import RecordingEventSource from "@/services/api/model/RecordingEventSource.ts";
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, generateProfileUrl } = useNavigation();

let profileClient: ProjectProfileClient;

// Initialize client when route params are available
function initializeClient() {
  if (workspaceId.value && projectId.value) {
    profileClient = new ProjectProfileClient(workspaceId.value, projectId.value);
  }
}

// Persistent storage for deleting profiles - initialize this reactively
const DELETING_PROFILES_KEY = computed(() => `deleting_profiles_${workspaceId.value}_${projectId.value}`);

const getDeletingProfiles = (): Set<string> => {
  const stored = sessionStorage.getItem(DELETING_PROFILES_KEY.value);
  return stored ? new Set(JSON.parse(stored)) : new Set();
};

const getSourceVariant = (eventSource: string) => {
  if (eventSource === RecordingEventSource.ASYNC_PROFILER) {
    return 'purple';
  }
  return 'info';
};

const addDeletingProfile = (profileId: string) => {
  const deletingProfiles = getDeletingProfiles();
  deletingProfiles.add(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(deletingProfiles)));
};

const removeDeletingProfile = (profileId: string) => {
  const deletingProfiles = getDeletingProfiles();
  deletingProfiles.delete(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(deletingProfiles)));
};

// Data
const profiles = ref<Profile[]>([]);
const filteredProfiles = ref<Profile[]>([]);
const searchQuery = ref('');
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
// Edit modal ref
const editProfileModal = ref<InstanceType<typeof BaseModal>>();

// Fetch profiles on component mount
onMounted(async () => {
  try {
    // Initialize client first
    initializeClient();

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
  if (!profileClient) {
    console.error('Profile client not initialized');
    return;
  }

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
  editProfileModal.value?.showModal();
};

const closeEditModal = () => {
  editProfileModal.value?.hideModal();
};

const updateProfile = async () => {
  if (!editProfileName.value || editProfileName.value.trim() === '') {
    editProfileModal.value?.setValidationErrors(['Profile name cannot be empty']);
    return;
  }

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
    editProfileModal.value?.hideModal();

    ToastService.success('Profile Updated!', 'Profile "' + updatedName + '" successfully updated!');
  } catch (error) {
    console.error('Failed to update profile:', error);
    editProfileModal.value?.setValidationErrors([error instanceof Error ? error.message : 'Failed to update profile']);
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
/* Minimal scoped styles - shared styles come from shared-components.css */
</style>

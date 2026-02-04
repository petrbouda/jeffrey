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

    <!-- Profiles Header Bar -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3 gap-3">
        <div class="profiles-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">Profiles ({{ filteredProfiles.length }})</span>
        </div>
      </div>
    </div>

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading profiles..." />

    <!-- Profiles List -->
    <div v-else class="col-12">
      <EmptyState
        v-if="filteredProfiles.length === 0"
        icon="bi-person-vcard"
        title="No Profiles Found"
        description="No profiles found. Create a new profile from a recording to get started."
      />

      <div v-else>
        <div v-for="profile in filteredProfiles" :key="profile.id"
             class="child-row p-3 mb-2 rounded"
             :class="{ 'disabled-profile': profile.deleting || !profile.enabled }">
          <div class="d-flex justify-content-between align-items-center">
            <!-- Left: View button + Profile info -->
            <div class="d-flex align-items-center">
              <router-link
                :to="generateProfileUrl('overview', profile.id)"
                class="btn btn-primary view-btn me-3"
                @click="selectProfile"
                :class="{ 'disabled': profile.deleting || !profile.enabled }"
                :style="{ 'pointer-events': (profile.deleting || !profile.enabled) ? 'none' : 'auto' }"
              >
                <i class="bi bi-eye"></i>
              </router-link>
              <div>
                <div class="fw-bold">
                  <i class="bi bi-person-vcard me-2 text-secondary"></i>
                  {{ profile.name }}
                  <!-- Event source badge -->
                  <Badge
                    class="ms-2"
                    :value="Utils.formatEventSource(profile.eventSource || RecordingEventSource.JDK)"
                    :variant="Utils.getEventSourceVariant(profile.eventSource || RecordingEventSource.JDK)"
                    size="xs"
                  />
                  <!-- Status badges -->
                  <Badge
                    v-if="profile.deleting"
                    value="Deleting"
                    variant="red"
                    size="xs"
                    icon="spinner-border spinner-border-sm"
                    class="ms-1"
                  />
                  <Badge
                    v-else-if="!profile.enabled"
                    value="Initializing"
                    variant="orange"
                    size="xs"
                    icon="spinner-border spinner-border-sm"
                    class="ms-1"
                  />
                </div>
                <!-- Metadata row -->
                <div class="d-flex text-muted small mt-1">
                  <div class="me-3">
                    <i class="bi bi-stopwatch me-1"></i>
                    {{ FormattingService.formatDurationInMillis2Units(profile.durationInMillis) }}
                  </div>
                  <div class="me-3">
                    <i class="bi bi-hdd me-1"></i>
                    {{ FormattingService.formatBytes(profile.sizeInBytes) }}
                  </div>
                  <div>
                    <i class="bi bi-calendar me-1"></i>
                    {{ profile.createdAt }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Right: Action buttons -->
            <div class="d-flex">
              <button
                class="action-btn action-menu-btn action-info-btn me-2"
                @click="editProfile(profile)"
                :disabled="profile.deleting || !profile.enabled"
                title="Edit Profile"
              >
                <i class="bi bi-pencil"></i>
              </button>
              <button
                class="action-btn action-menu-btn action-danger-btn"
                @click="deleteProfile(profile)"
                :disabled="profile.deleting"
                title="Delete Profile"
              >
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
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
import Utils from "@/services/Utils";
import FormattingService from "@/services/FormattingService.ts";
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
/* Child row styling (from RecordingsList.vue) */
.child-row {
  background-color: white;
  border: 1px solid #e9ecef;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
  border-left: 3px solid #6c757d;
}

.child-row:hover {
  background-color: rgba(248, 249, 250, 0.8);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.12);
  border-left-color: #5e64ff;
}

/* Disabled profile styling */
.disabled-profile {
  opacity: 0.6;
}

.disabled-profile:hover {
  transform: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border-left-color: #6c757d;
}

/* Action button styling (from RecordingsList.vue) */
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: none;
  border-radius: 4px;
  height: 28px;
  width: 28px;
  padding: 0;
  font-size: 0.85rem;
  transition: all 0.15s ease;
}

.action-menu-btn {
  color: #5e64ff;
  background-color: rgba(94, 100, 255, 0.1);
  border-radius: 4px;
  height: 30px;
  width: 30px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.action-menu-btn:hover {
  background-color: rgba(94, 100, 255, 0.18);
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
}

.action-menu-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.action-info-btn {
  color: #fff;
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.action-info-btn:hover:not(:disabled) {
  background-color: #4a50e3;
  border-color: #4a50e3;
  color: #fff;
}

.action-danger-btn {
  color: #fff;
  background-color: #dc3545;
  border-color: #dc3545;
}

.action-danger-btn:hover:not(:disabled) {
  background-color: #c82333;
  border-color: #bd2130;
  color: #fff;
}

/* Profiles header bar styling */
.profiles-header-bar {
  background: linear-gradient(135deg, #5e64ff 0%, #4a50e2 100%);
  border: 1px solid #4a50e2;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.25);
  position: relative;
  height: 31px;
}

.header-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(1px);
}

/* View button square styling (matching RecordingsList) */
.view-btn {
  width: 40px;
  height: 40px;
  min-width: 40px;
  padding: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.view-btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

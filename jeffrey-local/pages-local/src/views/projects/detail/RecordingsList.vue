<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue';
import { useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ProjectRecordingClient from '@/services/api/ProjectRecordingClient';
import ProjectRecordingFolderClient from '@/services/api/ProjectRecordingFolderClient';
import {ToastService} from '@/services/ToastService';
import Recording from "@/services/api/model/Recording.ts";
import RecordingFolder from "@/services/api/model/RecordingFolder.ts";
import ProjectProfileClient from "@/services/api/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import MessageBus from "@/services/MessageBus";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
import RecordingCard from '@/components/RecordingCard.vue';
import RecordingFileGroupList from '@/components/RecordingFileGroupList.vue';
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import BaseModal from '@/components/BaseModal.vue';
import '@/styles/shared-components.css';

const toast = ToastService;
const recordings = ref<Recording[]>([]);
const loading = ref(true);
const deleteRecordingDialog = ref(false);
const recordingToDelete = ref<Recording | null>();
const deleteFolderDialog = ref(false);
const folderToDelete = ref<RecordingFolder | null>();

// Services
let projectProfileClient: ProjectProfileClient;
let projectRecordingClient: ProjectRecordingClient;
let projectRecordingFolderClient: ProjectRecordingFolderClient;

// Track expanded folders
const expandedFolders = ref<Set<string>>(new Set());

// Track expanded recording files sections
const expandedRecordingFiles = ref<Set<string>>(new Set());

const router = useRouter();
const { workspaceId, projectId, generateProfileUrl } = useNavigation();

// --- Profile Management State ---
const editProfileModal = ref<InstanceType<typeof BaseModal>>();
const editProfileName = ref('');
const selectedProfileId = ref('');
const updatingProfile = ref(false);
const pollInterval = ref<number | null>(null);

// Persistent storage for deleting profiles
const DELETING_PROFILES_KEY = computed(() => `deleting_profiles_${workspaceId.value}_${projectId.value}`);

const getDeletingProfiles = (): Set<string> => {
  const stored = sessionStorage.getItem(DELETING_PROFILES_KEY.value);
  return stored ? new Set(JSON.parse(stored)) : new Set();
};

const addDeletingProfile = (profileId: string) => {
  const profiles = getDeletingProfiles();
  profiles.add(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(profiles)));
};

const removeDeletingProfile = (profileId: string) => {
  const profiles = getDeletingProfiles();
  profiles.delete(profileId);
  sessionStorage.setItem(DELETING_PROFILES_KEY.value, JSON.stringify(Array.from(profiles)));
};

// Track profile creation states for each recording
const profileCreationStates = ref<Map<string, boolean>>(new Map());

const folders = ref<RecordingFolder[]>([]);
const newFolderName = ref('');
const createFolderModal = ref<InstanceType<typeof BaseModal>>();

onMounted(async () => {
  if (!workspaceId.value || !projectId.value) return;

  projectProfileClient = new ProjectProfileClient(workspaceId.value, projectId.value);
  projectRecordingClient = new ProjectRecordingClient(workspaceId.value, projectId.value);
  projectRecordingFolderClient = new ProjectRecordingFolderClient(workspaceId.value, projectId.value);

  expandedFolders.value.add('root');

  await loadData();

  // Start polling if any profiles are initializing or deleting
  if (hasInitializingOrDeletingProfiles()) {
    startPolling();
  }
});

onUnmounted(() => {
  stopPolling();
});

const hasInitializingOrDeletingProfiles = (): boolean => {
  const hasInitializing = recordings.value.some(r => r.hasProfile && !r.profileEnabled);
  const hasDeleting = getDeletingProfiles().size > 0;
  return hasInitializing || hasDeleting;
};

// Toggle the recording files section
const toggleRecordingFiles = (recording: Recording) => {
  if (expandedRecordingFiles.value.has(recording.id)) {
    expandedRecordingFiles.value.delete(recording.id);
  } else {
    expandedRecordingFiles.value.add(recording.id);
  }
};

// Download a recording file
const downloadFile = async (recordingId: string, fileId: string) => {
  try {
    await projectRecordingClient.downloadFile(recordingId, fileId);
  } catch (error: any) {
    toast.error('Failed to download file', error.message);
  }
};

const loadData = async () => {
  // Only show loader on initial load, not on refreshes
  const isInitialLoad = recordings.value.length === 0 && folders.value.length === 0;
  if (isInitialLoad) {
    loading.value = true;
  }
  try {
    const [recordingsData, foldersData] = await Promise.all([
      projectRecordingClient.list(),
      projectRecordingFolderClient.list()
    ]);

    // Restore deleting state from storage
    const deletingProfiles = getDeletingProfiles();
    recordingsData.forEach(recording => {
      if (recording.profileId && deletingProfiles.has(recording.profileId)) {
        (recording as any)._profileDeleting = true;
      }
    });

    recordings.value = recordingsData;
    folders.value = foldersData;
  } catch (error: any) {
    toast.error('Failed to load data', error.message);
  } finally {
    MessageBus.emit(MessageBus.RECORDINGS_COUNT_CHANGED, recordings.value.length);
    MessageBus.emit(MessageBus.PROFILES_COUNT_CHANGED, recordings.value.filter(r => r.hasProfile).length);
    loading.value = false;
  }
};

// Organize recordings by folders
const organizedRecordings = computed(() => {
  const validFolderIds = new Set(folders.value.map(folder => folder.id));

  const rootRecordings = recordings.value.filter(recording =>
      recording.folderId == null || !validFolderIds.has(recording.folderId))
      .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime());

  const folderRecordings = new Map<string, Recording[]>();

  recordings.value.forEach(recording => {
    const folderId = recording.folderId;
    if (folderId && validFolderIds.has(folderId)) {
      if (!folderRecordings.has(folderId)) {
        folderRecordings.set(folderId, []);
      }
      folderRecordings.get(folderId)?.push(recording);
    }
  });

  folderRecordings.forEach((folderRecs, folderId) => {
    folderRecordings.set(
      folderId,
      folderRecs.sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    );
  });

  return { rootRecordings, folderRecordings };
});

// --- Profile Actions ---

const isProfileDeleting = (recording: Recording): boolean => {
  if (!recording.profileId) return false;
  return getDeletingProfiles().has(recording.profileId) || (recording as any)._profileDeleting === true;
};

const selectProfile = () => {
  SecondaryProfileService.remove();
  sessionStorage.removeItem('profile-sidebar-mode');
};

const createProfile = async (recording: Recording) => {
  if (profileCreationStates.value.get(recording.id) || recording.hasProfile) {
    return;
  }

  profileCreationStates.value.set(recording.id, true);
  recording.hasProfile = true;

  try {
    MessageBus.emit(MessageBus.PROFILE_INITIALIZATION_STARTED, true);
    await projectProfileClient.create(recording.id);
    await loadData();

    const updatedRecording = recordings.value.find(r => r.id === recording.id);
    if (updatedRecording) {
      updatedRecording.hasProfile = true;
    }

    // Start polling for initialization
    startPolling();
  } catch (error: any) {
    recording.hasProfile = false;
    toast.error('Profile Creation Failed', error.message);
  } finally {
    profileCreationStates.value.delete(recording.id);
  }
};

const editProfile = (recording: Recording) => {
  if (!recording.profileId || !recording.profileName) return;
  selectedProfileId.value = recording.profileId;
  editProfileName.value = recording.profileName;
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
    await projectProfileClient.update(selectedProfileId.value, editProfileName.value.trim());

    const updatedName = editProfileName.value;
    selectedProfileId.value = '';
    editProfileName.value = '';
    editProfileModal.value?.hideModal();

    await loadData();
  } catch (error) {
    console.error('Failed to update profile:', error);
    editProfileModal.value?.setValidationErrors([error instanceof Error ? error.message : 'Failed to update profile']);
  } finally {
    updatingProfile.value = false;
  }
};

const deleteProfile = (recording: Recording) => {
  if (!recording.profileId) return;

  const profileId = recording.profileId;
  const profileName = recording.profileName || recording.name;

  addDeletingProfile(profileId);
  (recording as any)._profileDeleting = true;

  startPolling();

  projectProfileClient.delete(profileId)
      .then(() => {
        removeDeletingProfile(profileId);
        loadData();
      })
      .catch(error => {
        console.error('Failed to delete profile:', error);
        toast.error('Delete Profile', 'Failed to delete profile: ' + profileName);
        removeDeletingProfile(profileId);
        loadData();
      });
};

// --- Polling ---
// Lightweight polling: only fetches profiles list and merges state changes
// into existing recordings in-place (no full reload, no blinking).
// Does a full loadData() once when all transitions finish.

const startPolling = () => {
  if (pollInterval.value !== null) return;

  pollInterval.value = window.setInterval(async () => {
    try {
      const profiles = await projectProfileClient.list();
      const profileByRecordingId = new Map<string, { id: string; name: string; enabled: boolean; sizeInBytes: number }>();
      const profileIds = new Set<string>();
      for (const p of profiles) {
        profileIds.add(p.id);
      }

      // Check which "deleting" profiles are actually gone
      const deletingProfiles = getDeletingProfiles();
      let deletionCompleted = false;
      for (const profileId of deletingProfiles) {
        if (!profileIds.has(profileId)) {
          removeDeletingProfile(profileId);
          deletionCompleted = true;
        }
      }

      // Build lookup by scanning recordings for their profileId
      for (const p of profiles) {
        // Find which recording this profile belongs to
        const rec = recordings.value.find(r => r.profileId === p.id);
        if (rec) {
          profileByRecordingId.set(rec.id, { id: p.id, name: p.name, enabled: p.enabled, sizeInBytes: p.sizeInBytes });
        }
      }

      // Merge state changes in-place
      let initializationCompleted = false;
      for (const rec of recordings.value) {
        const profile = profileByRecordingId.get(rec.id);
        if (profile && rec.hasProfile && !rec.profileEnabled && profile.enabled) {
          // Profile just finished initializing
          rec.profileEnabled = true;
          rec.profileName = profile.name;
          rec.profileSizeInBytes = profile.sizeInBytes;
          initializationCompleted = true;
        }
        // Clear deleting flag if profile is gone
        if (rec.profileId && !profileIds.has(rec.profileId) && (rec as any)._profileDeleting) {
          rec.hasProfile = false;
          rec.profileId = null;
          rec.profileName = null;
          rec.profileEnabled = undefined;
          rec.profileSizeInBytes = undefined;
          (rec as any)._profileDeleting = false;
        }
      }

      // If all transitions are done, do one final full reload and stop
      if (!hasInitializingOrDeletingProfiles()) {
        stopPolling();
        if (initializationCompleted || deletionCompleted) {
          await loadData();
        }
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

// --- Recording Actions ---

const confirmDeleteRecording = (recording: Recording) => {
  recordingToDelete.value = recording;
  deleteRecordingDialog.value = true;
};

const deleteRecording = async () => {
  if (!recordingToDelete.value) return;

  try {
    await projectRecordingClient.delete(recordingToDelete.value.id);
    await loadData();
    deleteRecordingDialog.value = false;
    recordingToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};

const confirmDeleteFolder = (folder: RecordingFolder) => {
  folderToDelete.value = folder;
  deleteFolderDialog.value = true;
};

const deleteFolder = async () => {
  if (!folderToDelete.value) return;

  try {
    await projectRecordingFolderClient.delete(folderToDelete.value.id);
    await loadData();
    deleteFolderDialog.value = false;
    folderToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};

const createFolder = async () => {
  if (!newFolderName.value.trim()) {
    createFolderModal.value?.setValidationErrors(['Folder name cannot be empty']);
    return;
  }

  try {
    await projectRecordingFolderClient.create(newFolderName.value.trim());
    newFolderName.value = '';
    createFolderModal.value?.hideModal();
    await loadData();
  } catch (error: any) {
    createFolderModal.value?.setValidationErrors([error.message || 'Failed to create folder']);
  }
};

const openCreateFolderDialog = () => {
  newFolderName.value = '';
  createFolderModal.value?.showModal();
};

const isRecordingCreatingProfile = (recordingId: string): boolean => {
  return profileCreationStates.value.get(recordingId) || false;
};

const handleRecordingCardClick = (recording: Recording) => {
  if (isProfileDeleting(recording) || isRecordingCreatingProfile(recording.id)) return;
  if (recording.hasProfile && recording.profileEnabled) {
    navigateToProfile(recording);
  } else if (!recording.hasProfile) {
    createProfile(recording);
  }
};

const navigateToProfile = (recording: Recording) => {
  if (!recording.profileId) return;
  selectProfile();
  router.push(generateProfileUrl('overview', recording.profileId));
};
</script>

<template>
  <PageHeader
    title="Recordings"
    description="Manage recordings and their profiles. Organize with folders and create profiles for performance analysis."
    icon="bi-record-circle"
  >
    <!-- Recordings Header Bar -->
    <div class="col-12">
      <SectionHeaderBar :text="`Recordings (${recordings.length})`">
        <template #actions>
          <button class="btn btn-primary btn-sm" @click="openCreateFolderDialog">
            <i class="bi bi-folder-plus me-1"></i>New Folder
          </button>
        </template>
      </SectionHeaderBar>
    </div>

    <!-- Recordings List -->
    <div class="col-12">
      <LoadingState v-if="loading" message="Loading recordings..." />

      <EmptyState
        v-else-if="recordings.length === 0 && folders.length === 0"
        icon="bi-folder-x"
        title="No Recordings Available"
        description="Recordings from sessions will appear here."
      />

      <div v-else>
            <!-- Folders with their recordings -->
            <div v-for="folder in folders" :key="`folder-group-${folder.id}`" class="mb-3">
              <!-- Folder header -->
              <div class="folder-row p-3 rounded"
                  @click="expandedFolders.has(folder.id) ? expandedFolders.delete(folder.id) : expandedFolders.add(folder.id)">
                <div class="d-flex justify-content-between align-items-center">
                  <div class="d-flex align-items-center">
                    <i class="bi fs-5 me-2 text-primary" :class="expandedFolders.has(folder.id) ? 'bi-folder2-open' : 'bi-folder2'"></i>
                    <div class="fw-bold">
                      <i class="bi me-2"
                         :class="expandedFolders.has(folder.id) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                      {{ folder.name }}
                      <Badge
                        :value="`${organizedRecordings.folderRecordings.get(folder.id)?.length || 0} recording${(organizedRecordings.folderRecordings.get(folder.id)?.length || 0) !== 1 ? 's' : ''}`"
                        variant="primary"
                        size="xs"
                        class="ms-2"
                      />
                    </div>
                  </div>
                  <div class="d-flex">
                    <button
                        class="action-btn action-menu-btn action-danger-btn"
                        @click.stop="confirmDeleteFolder(folder)"
                        title="Delete folder and all its recordings">
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Folder recordings (shown when expanded) -->
              <div v-if="expandedFolders.has(folder.id)" class="ps-4 pt-2">
                <RecordingCard
                    v-for="recording in organizedRecordings.folderRecordings.get(folder.id) || []"
                    :key="`recording-${recording.id}`"
                    class="mb-2"
                    :recording-id="recording.id"
                    :name="recording.profileName || recording.name"
                    :size-in-bytes="recording.sizeInBytes"
                    :duration-in-millis="recording.durationInMillis"
                    :uploaded-at="recording.uploadedAt"
                    :source-type="recording.sourceType"
                    :has-profile="!!recording.hasProfile"
                    :profile-id="recording.profileId"
                    :profile-enabled="recording.profileEnabled ?? true"
                    :profile-size-in-bytes="recording.profileSizeInBytes"
                    :file-count="recording.recordingFiles.length"
                    :creating-profile="isRecordingCreatingProfile(recording.id)"
                    :deleting-profile="isProfileDeleting(recording)"
                    :expandable="true"
                    :expanded="expandedRecordingFiles.has(recording.id)"
                    @click="handleRecordingCardClick(recording)"
                    @create-profile="createProfile(recording)"
                    @open-profile="navigateToProfile(recording)"
                    @edit-profile="editProfile(recording)"
                    @delete-profile="deleteProfile(recording)"
                    @toggle-expand="toggleRecordingFiles(recording)"
                    @delete-recording="confirmDeleteRecording(recording)"
                >
                  <template #expanded-content>
                    <RecordingFileGroupList
                        v-if="recording.recordingFiles && recording.recordingFiles.length > 0"
                        :recording-id="recording.id"
                        :files="recording.recordingFiles"
                        @download="downloadFile"
                    />
                    <div v-else class="small py-1 text-muted">
                      <i class="bi bi-exclamation-circle me-1"></i>
                      No recording files available
                    </div>
                  </template>
                </RecordingCard>
              </div>
            </div>


            <!-- Root Recordings -->
            <div v-if="organizedRecordings.rootRecordings.length > 0" class="mt-3">
              <div class="mb-3" v-if="folders.length > 0">
                <div class="root-recordings-bar d-flex align-items-center px-3">
                  <span class="root-header-text">Root Recordings ({{ organizedRecordings.rootRecordings.length }})</span>
                </div>
              </div>

              <div class="root-recordings-list">
                <RecordingCard
                    v-for="recording in organizedRecordings.rootRecordings"
                    :key="recording.id"
                    class="mb-2"
                    :recording-id="recording.id"
                    :name="recording.profileName || recording.name"
                    :size-in-bytes="recording.sizeInBytes"
                    :duration-in-millis="recording.durationInMillis"
                    :uploaded-at="recording.uploadedAt"
                    :source-type="recording.sourceType"
                    :has-profile="!!recording.hasProfile"
                    :profile-id="recording.profileId"
                    :profile-enabled="recording.profileEnabled ?? true"
                    :profile-size-in-bytes="recording.profileSizeInBytes"
                    :file-count="recording.recordingFiles.length"
                    :creating-profile="isRecordingCreatingProfile(recording.id)"
                    :deleting-profile="isProfileDeleting(recording)"
                    :expandable="true"
                    :expanded="expandedRecordingFiles.has(recording.id)"
                    @click="handleRecordingCardClick(recording)"
                    @create-profile="createProfile(recording)"
                    @open-profile="navigateToProfile(recording)"
                    @edit-profile="editProfile(recording)"
                    @delete-profile="deleteProfile(recording)"
                    @toggle-expand="toggleRecordingFiles(recording)"
                    @delete-recording="confirmDeleteRecording(recording)"
                >
                  <template #expanded-content>
                    <RecordingFileGroupList
                        v-if="recording.recordingFiles && recording.recordingFiles.length > 0"
                        :recording-id="recording.id"
                        :files="recording.recordingFiles"
                        @download="downloadFile"
                    />
                    <div v-else class="small py-1 text-muted">
                      <i class="bi bi-exclamation-circle me-1"></i>
                      No recording files available
                    </div>
                  </template>
                </RecordingCard>
              </div>
            </div>
          </div>
    </div>

      <!-- Delete Recording Confirmation Dialog -->
      <ConfirmationDialog
        v-model:show="deleteRecordingDialog"
        title="Confirm Delete"
        :message="recordingToDelete ? `Are you sure you want to delete the recording: ${recordingToDelete.name}?` : 'Are you sure you want to delete this recording?'"
        sub-message="This action cannot be undone."
        confirm-label="Delete"
        confirm-button-class="btn-danger"
        confirm-button-id="deleteRecordingButton"
        modal-id="deleteRecordingModal"
        @confirm="deleteRecording"
      />

      <!-- Delete Folder Confirmation Dialog -->
      <ConfirmationDialog
        v-model:show="deleteFolderDialog"
        title="Confirm Delete Folder"
        :message="folderToDelete ? `Are you sure you want to delete the folder: ${folderToDelete.name}?` : 'Are you sure you want to delete this folder?'"
        sub-message="This will also delete all recordings within the folder."
        confirm-label="Delete Folder"
        confirm-button-class="btn-danger"
        confirm-button-id="deleteFolderButton"
        modal-id="deleteFolderModal"
        @confirm="deleteFolder"
      />



      <!-- Create Folder Dialog -->
      <BaseModal
        ref="createFolderModal"
        modal-id="createFolderModal"
        title="Create New Folder"
        icon="bi-folder-plus"
        primary-button-text="Create"
        @submit="createFolder"
      >
        <template #body>
          <div class="form-group">
            <label for="newFolderNameInput" class="form-label">Folder Name</label>
            <input
                type="text"
                class="form-control"
                id="newFolderNameInput"
                v-model="newFolderName"
                placeholder="Enter folder name"
                @keyup.enter="createFolder"
            >
          </div>
        </template>
      </BaseModal>

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
  </PageHeader>
</template>

<style scoped>
.modal {
  background-color: rgba(0, 0, 0, 0.5);
}

.d-block {
  display: block !important;
}

.btn-sm i {
  font-size: 0.8rem;
}

.cursor-pointer {
  cursor: pointer;
}

/* Folder structure styles */
.folder-row {
  background-color: white;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid #eee;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.folder-row:hover {
  background-color: rgba(94, 100, 255, 0.03);
  transform: translateY(-1px);
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.08);
}

.ps-4 {
  padding-left: 2.5rem !important;
}

/* Icons for folder expansion */
.bi-folder2, .bi-folder2-open {
  color: #5e64ff !important;
}

.folder-row .bi-chevron-right,
.folder-row .bi-chevron-down {
  transition: transform 0.2s ease;
}

.folder-row:hover .bi-chevron-right {
  transform: translateX(2px);
}

/* Action button styling */
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

/* Action info button style */
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

/* Root recordings bar styling */
.root-recordings-bar {
  background: white;
  border: 1px solid #4a50e2;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.15);
  position: relative;
  height: 31px;
}

.root-header-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: #4a50e2;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

/* Profile creation button loading state */
.btn-outline-success {
  color: #198754;
  border-color: #198754;
  background-color: transparent;
  position: relative;
}

.btn-outline-success:disabled {
  background-color: rgba(25, 135, 84, 0.05);
  border-color: rgba(25, 135, 84, 0.3);
  color: rgba(25, 135, 84, 0.7);
}

.btn-outline-success .spinner-border-sm {
  width: 0.875rem;
  height: 0.875rem;
  border-width: 0.1em;
}
</style>

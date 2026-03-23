<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import ProjectRecordingClient from '@/services/api/ProjectRecordingClient';
import ProjectRecordingFolderClient from '@/services/api/ProjectRecordingFolderClient';
import {ToastService} from '@/services/ToastService';
import Recording from "@/services/api/model/Recording.ts";
import RecordingFolder from "@/services/api/model/RecordingFolder.ts";
import ProjectProfileClient from "@/services/api/ProjectProfileClient.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import FormattingService from "@/services/FormattingService.ts";
import MessageBus from "@/services/MessageBus";
import Utils from "@/services/Utils";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
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
const uploadFiles = ref<File[]>([]);
const dragActive = ref(false);
interface UploadProgressEntry {
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
}
const uploadProgress = ref<Record<string, UploadProgressEntry>>({});
const uploadPanelExpanded = ref(false);

// Services
let projectProfileClient: ProjectProfileClient;
let projectRecordingClient: ProjectRecordingClient;
let projectRecordingFolderClient: ProjectRecordingFolderClient;

// Track expanded folders
const expandedFolders = ref<Set<string>>(new Set());

// Track expanded recording files sections
const expandedRecordingFiles = ref<Set<string>>(new Set());

const { workspaceId, projectId, generateProfileUrl } = useNavigation();

// --- Profile Management State ---
const editProfileModal = ref<InstanceType<typeof BaseModal>>();
const editProfileName = ref('');
const selectedProfileId = ref('');
const updatingProfile = ref(false);
const deleteProfileDialog = ref(false);
const profileToDelete = ref<Recording | null>(null);
const deletingProfile = ref(false);
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
const selectedFolderId = ref<string | null>(null);
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
  loading.value = true;
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
    toast.success('Profile Creation Started', `Asynchronous Profile Creation started from recording: ${recording.name}`);

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

    toast.success('Profile Updated!', 'Profile "' + updatedName + '" successfully updated!');

    await loadData();
  } catch (error) {
    console.error('Failed to update profile:', error);
    editProfileModal.value?.setValidationErrors([error instanceof Error ? error.message : 'Failed to update profile']);
  } finally {
    updatingProfile.value = false;
  }
};

const deleteProfile = (recording: Recording) => {
  profileToDelete.value = recording;
  deleteProfileDialog.value = true;
};

const confirmDeleteProfile = async () => {
  if (!profileToDelete.value || !profileToDelete.value.profileId) return;

  deletingProfile.value = true;
  const profileId = profileToDelete.value.profileId;
  const profileName = profileToDelete.value.profileName || profileToDelete.value.name;

  addDeletingProfile(profileId);
  (profileToDelete.value as any)._profileDeleting = true;

  startPolling();

  try {
    projectProfileClient.delete(profileId)
        .then(() => {
          toast.success('Profile Deleted', 'Profile "' + profileName + '" successfully deleted!');
          removeDeletingProfile(profileId);
          loadData();
        })
        .catch(error => {
          console.error('Failed to delete profile:', error);
          toast.error('Delete Profile', 'Failed to delete profile: ' + profileName);
          removeDeletingProfile(profileId);
          loadData();
        });
  } catch (error) {
    console.error('Failed to delete profile:', error);
    toast.error('Delete Profile', 'Failed to delete profile: ' + profileName);
    removeDeletingProfile(profileId);
  } finally {
    deletingProfile.value = false;
    deleteProfileDialog.value = false;
    profileToDelete.value = null;
  }
};

// --- Polling ---

const startPolling = () => {
  if (pollInterval.value !== null) return;

  pollInterval.value = window.setInterval(async () => {
    try {
      await loadData();

      if (!hasInitializingOrDeletingProfiles()) {
        stopPolling();
      }
    } catch (error) {
      console.error('Error while polling recordings:', error);
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
    toast.success('Recording Deleted', `Recording ${recordingToDelete.value.name} has been deleted`);
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
    toast.success('Folder Deleted', `Folder ${folderToDelete.value.name} has been deleted along with all its recordings`);
    await loadData();
    deleteFolderDialog.value = false;
    folderToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement | null;
  const dragEvent = event as DragEvent;
  const files = target?.files || dragEvent.dataTransfer?.files;
  if (files && files.length) {
    const jfrFiles = Array.from(files).filter((file: File) => file.name.endsWith('.jfr') || file.name.endsWith('.jfr.lz4'));
    if (jfrFiles.length === 0) {
      toast.warn('Invalid Files', 'Only JFR files (.jfr, .jfr.lz4) are supported');
      return;
    }
    uploadFiles.value = [...uploadFiles.value, ...jfrFiles];
  }
};

const createFolder = async () => {
  if (!newFolderName.value.trim()) {
    createFolderModal.value?.setValidationErrors(['Folder name cannot be empty']);
    return;
  }

  try {
    await projectRecordingFolderClient.create(newFolderName.value.trim());
    toast.success('Folder Created', `Folder "${newFolderName.value.trim()}" has been created`);
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

const uploadRecordings = async () => {
  if (!uploadFiles.value.length) return;

  uploadProgress.value = {};

  for (let i = 0; i < uploadFiles.value.length; i++) {
    const file = uploadFiles.value[i];
    uploadProgress.value[file.name] = { progress: 0, status: 'pending' };
  }

  const uploadPromises = uploadFiles.value.map(async (file) => {
    try {
      uploadProgress.value[file.name].status = 'uploading';
      const progressInterval = setInterval(() => {
        if (uploadProgress.value[file.name].progress < 90) {
          uploadProgress.value[file.name].progress += Math.floor(Math.random() * 10) + 5;
        }
      }, 300);

      await projectRecordingClient.upload(file, selectedFolderId.value || null);

      clearInterval(progressInterval);
      uploadProgress.value[file.name].progress = 100;
      uploadProgress.value[file.name].status = 'complete';

      return {file, success: true};
    } catch (error) {
      uploadProgress.value[file.name].status = 'error';
      return {file, success: false, error};
    }
  });

  try {
    const results = await Promise.all(uploadPromises);
    const successCount = results.filter(r => r.success).length;

    if (successCount === uploadFiles.value.length) {
      toast.success('Upload Complete', `${successCount} recordings uploaded successfully`);
    } else {
      toast.warn('Upload Partially Complete', `${successCount} of ${uploadFiles.value.length} recordings uploaded successfully`);
    }

    await loadData();

    setTimeout(() => {
      uploadFiles.value = [];
      uploadProgress.value = {};
      uploadPanelExpanded.value = false;
    }, 2000);
  } catch (error) {
    toast.error('Upload Failed', (error as Error).message);
  }
};

const handleDragOver = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = true;
};

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
};

const handleDrop = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
  if (!uploadPanelExpanded.value) {
    uploadPanelExpanded.value = true;
  }
  handleFileUpload(event);
};

const removeFile = (index: number) => {
  const newFiles = [...uploadFiles.value];
  newFiles.splice(index, 1);
  uploadFiles.value = newFiles;
};

const isRecordingCreatingProfile = (recordingId: string): boolean => {
  return profileCreationStates.value.get(recordingId) || false;
};
</script>

<template>
  <PageHeader
    title="Recordings"
    description="Manage recordings and their profiles. Upload JFR files, organize with folders, and create profiles for performance analysis."
    icon="bi-record-circle"
  >
    <!-- File Upload Panel -->
  <div class="col-12">
    <div class="card shadow-sm border-0 mb-4">
      <div class="card-header bg-light d-flex justify-content-between align-items-center cursor-pointer py-3"
           @click="uploadPanelExpanded = !uploadPanelExpanded">
        <div class="d-flex align-items-center">
          <i class="bi bi-upload fs-4 me-2 text-primary"></i>
          <h5 class="card-title mb-0">Upload Recordings</h5>
        </div>
        <div class="d-flex align-items-center">
          <div class="text-muted small me-3">Only .jfr and .jfr.lz4 files are supported</div>
          <button class="btn btn-sm btn-outline-primary" @click.stop="uploadPanelExpanded = !uploadPanelExpanded">
            <i class="bi" :class="uploadPanelExpanded ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
          </button>
        </div>
      </div>

      <div v-if="uploadPanelExpanded" class="card-body">
        <div class="form-group mb-3">
          <label for="folderSelect" class="form-label fw-bold">Target Folder</label>
          <select id="folderSelect" class="form-select" v-model="selectedFolderId">
            <option :value="null">Root directory (no folder)</option>
            <option v-for="folder in folders" :key="folder.id" :value="folder.id">
              {{ folder.name }}
            </option>
          </select>
          <div class="text-muted small mt-1">
            Files will be uploaded to
            {{ selectedFolderId ? folders.find(f => f.id === selectedFolderId)?.name : 'root directory' }}
          </div>
        </div>

        <div
            class="upload-dropzone p-4"
            :class="{ 'active': dragActive }"
            @dragover="handleDragOver"
            @dragleave="handleDragLeave"
            @drop="handleDrop"
        >
          <div v-if="uploadFiles.length === 0" class="text-center py-4">
            <i class="bi bi-cloud-upload display-4 text-primary mb-3"></i>
            <h5>Drag & Drop JFR Files Here</h5>

            <input
                type="file"
                id="fileUploadInput"
                class="d-none"
                accept=".jfr,.jfr.lz4"
                multiple
                @change="handleFileUpload"
            >
            <label for="fileUploadInput" class="btn btn-primary mt-2">
              <i class="bi bi-folder me-2"></i>Browse Files
            </label>
          </div>

          <div v-else>
            <div class="d-flex justify-content-between mb-3">
              <div>
                <h6 class="mb-0"><i class="bi bi-files me-2"></i>Selected Files ({{ uploadFiles.length }})</h6>
                <div class="text-muted small mt-1">
                  {{ uploadFiles.length }} file(s) selected
                </div>
              </div>
              <div>
                <button class="btn btn-success btn-sm me-2" @click="uploadRecordings">
                  <i class="bi bi-cloud-upload me-1"></i>Upload All
                </button>
                <button class="btn btn-outline-secondary btn-sm" @click="uploadFiles = []">
                  <i class="bi bi-x-lg me-1"></i>Clear
                </button>
              </div>
            </div>

            <div class="selected-files">
              <div v-for="(file, index) in uploadFiles" :key="file.name + index" class="file-item p-2 mb-2">
                <div class="d-flex justify-content-between align-items-center">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-file-earmark-binary text-primary me-2 fs-5"></i>
                    <div>
                      <div class="fw-bold">{{ file.name }}</div>
                      <div class="text-muted small">{{ FormattingService.formatBytes(file.size) }}</div>
                    </div>
                  </div>
                  <button class="btn btn-sm btn-outline-danger" @click="removeFile(index)">
                    <i class="bi bi-x"></i>
                  </button>
                </div>

                <div v-if="uploadProgress[file.name]" class="mt-2">
                  <div class="progress">
                    <div
                        class="progress-bar"
                        :class="{
                      'bg-success': uploadProgress[file.name].status === 'complete',
                      'bg-danger': uploadProgress[file.name].status === 'error',
                      'progress-bar-striped progress-bar-animated': uploadProgress[file.name].status === 'uploading'
                    }"
                        role="progressbar"
                        :style="{ width: uploadProgress[file.name].progress + '%' }"
                        :aria-valuenow="uploadProgress[file.name].progress"
                        aria-valuemin="0"
                        aria-valuemax="100">
                      {{ uploadProgress[file.name].progress }}%
                    </div>
                  </div>
                  <div class="text-end mt-1 small">
                  <span v-if="uploadProgress[file.name].status === 'complete'" class="text-success">
                    <i class="bi bi-check-circle me-1"></i>Complete
                  </span>
                    <span v-else-if="uploadProgress[file.name].status === 'error'" class="text-danger">
                    <i class="bi bi-exclamation-circle me-1"></i>Error
                  </span>
                    <span v-else-if="uploadProgress[file.name].status === 'uploading'" class="text-primary">
                    <i class="bi bi-arrow-clockwise me-1"></i>Uploading...
                  </span>
                    <span v-else class="text-muted">
                    <i class="bi bi-hourglass me-1"></i>Pending
                  </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

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
        description="Upload a JFR file or create a folder to get started."
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
                <div v-for="recording in organizedRecordings.folderRecordings.get(folder.id) || []"
                     :key="`recording-${recording.id}`"
                     class="child-row p-3 mb-2 rounded"
                     :class="{ 'disabled-profile': isProfileDeleting(recording) }">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <!-- Profile Action Button -->
                      <!-- Has profile & enabled: View button -->
                      <router-link
                        v-if="recording.hasProfile && recording.profileEnabled && !isProfileDeleting(recording)"
                        :to="generateProfileUrl('overview', recording.profileId!)"
                        class="btn btn-primary view-btn me-3"
                        @click="selectProfile"
                        title="View Profile"
                      >
                        <i class="bi bi-eye"></i>
                      </router-link>
                      <!-- Has profile but initializing -->
                      <button
                        v-else-if="recording.hasProfile && !recording.profileEnabled && !isRecordingCreatingProfile(recording.id)"
                        class="btn btn-outline-warning view-btn me-3"
                        disabled
                        title="Profile is initializing..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <!-- Profile deleting -->
                      <button
                        v-else-if="isProfileDeleting(recording)"
                        class="btn btn-outline-secondary view-btn me-3"
                        disabled
                        title="Profile is being deleted..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <!-- Creating profile (optimistic) -->
                      <button
                        v-else-if="isRecordingCreatingProfile(recording.id)"
                        class="btn btn-outline-success view-btn me-3"
                        disabled
                        title="Creating profile..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <!-- No profile: Create button -->
                      <button
                        v-else
                        class="btn btn-success view-btn me-3"
                        @click="createProfile(recording)"
                        title="Create profile from recording"
                      >
                        <i class="bi bi-plus-circle"></i>
                      </button>

                      <div>
                        <div class="fw-bold">
                          <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                          {{ recording.name }}
                          <Badge
                            :value="Utils.formatEventSource(recording.sourceType || 'UNKNOWN')"
                            :variant="Utils.getEventSourceVariant(recording.sourceType || 'UNKNOWN')"
                            size="xs"
                            class="ms-2"
                          />
                          <Badge
                            v-if="isProfileDeleting(recording)"
                            value="Deleting"
                            variant="red"
                            size="xs"
                            icon="spinner-border spinner-border-sm"
                            class="ms-1"
                          />
                          <Badge
                            v-else-if="recording.hasProfile && !recording.profileEnabled"
                            value="Initializing"
                            variant="orange"
                            size="xs"
                            icon="spinner-border spinner-border-sm"
                            class="ms-1"
                          />
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div class="me-3"><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                          <div class="me-3"><i class="bi bi-files me-1"></i>{{ recording.recordingFiles.length }} file{{ recording.recordingFiles.length !== 1 ? 's' : '' }}</div>
                          <div v-if="recording.hasProfile && recording.profileSizeInBytes" class="text-primary">
                            <i class="bi bi-person-vcard me-1"></i>Profile: {{ FormattingService.formatBytes(recording.profileSizeInBytes) }}
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="d-flex">
                      <!-- Profile actions (when profile exists and enabled) -->
                      <button
                        v-if="recording.hasProfile && recording.profileEnabled && !isProfileDeleting(recording)"
                        class="action-btn action-menu-btn action-info-btn me-2"
                        @click="editProfile(recording)"
                        title="Edit Profile"
                      >
                        <i class="bi bi-pencil"></i>
                      </button>
                      <button
                        v-if="recording.hasProfile && !isProfileDeleting(recording)"
                        class="action-btn action-menu-btn action-danger-btn me-2"
                        @click="deleteProfile(recording)"
                        title="Delete Profile"
                      >
                        <i class="bi bi-person-x"></i>
                      </button>
                      <button
                          class="action-btn action-menu-btn action-info-btn me-2"
                          @click="toggleRecordingFiles(recording)"
                          :title="expandedRecordingFiles.has(recording.id) ? 'Hide recording files' : 'Show recording files'"
                      >
                        <i class="bi" :class="expandedRecordingFiles.has(recording.id) ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
                      </button>
                      <button
                          class="action-btn action-menu-btn action-danger-btn"
                          @click="confirmDeleteRecording(recording)"
                          title="Delete recording"
                      >
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </div>

                  <!-- Recording Files (Expanded) -->
                  <div v-if="expandedRecordingFiles.has(recording.id)" class="ps-3 mt-2 mb-1 border-start border-2 ms-2">
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
                  </div>
                </div>
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
                <div v-for="recording in organizedRecordings.rootRecordings" :key="recording.id"
                     class="child-row p-3 mb-2 rounded"
                     :class="{ 'disabled-profile': isProfileDeleting(recording) }">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <!-- Profile Action Button -->
                      <router-link
                        v-if="recording.hasProfile && recording.profileEnabled && !isProfileDeleting(recording)"
                        :to="generateProfileUrl('overview', recording.profileId!)"
                        class="btn btn-primary view-btn me-3"
                        @click="selectProfile"
                        title="View Profile"
                      >
                        <i class="bi bi-eye"></i>
                      </router-link>
                      <button
                        v-else-if="recording.hasProfile && !recording.profileEnabled && !isRecordingCreatingProfile(recording.id)"
                        class="btn btn-outline-warning view-btn me-3"
                        disabled
                        title="Profile is initializing..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <button
                        v-else-if="isProfileDeleting(recording)"
                        class="btn btn-outline-secondary view-btn me-3"
                        disabled
                        title="Profile is being deleted..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <button
                        v-else-if="isRecordingCreatingProfile(recording.id)"
                        class="btn btn-outline-success view-btn me-3"
                        disabled
                        title="Creating profile..."
                      >
                        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                      </button>
                      <button
                        v-else
                        class="btn btn-success view-btn me-3"
                        @click="createProfile(recording)"
                        title="Create profile from recording"
                      >
                        <i class="bi bi-plus-circle"></i>
                      </button>

                      <div>
                        <div class="fw-bold">
                          <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                          {{ recording.name }}
                          <Badge
                            :value="Utils.formatEventSource(recording.sourceType || 'UNKNOWN')"
                            :variant="Utils.getEventSourceVariant(recording.sourceType || 'UNKNOWN')"
                            size="xs"
                            class="ms-2"
                          />
                          <Badge
                            v-if="isProfileDeleting(recording)"
                            value="Deleting"
                            variant="red"
                            size="xs"
                            icon="spinner-border spinner-border-sm"
                            class="ms-1"
                          />
                          <Badge
                            v-else-if="recording.hasProfile && !recording.profileEnabled"
                            value="Initializing"
                            variant="orange"
                            size="xs"
                            icon="spinner-border spinner-border-sm"
                            class="ms-1"
                          />
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div class="me-3"><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                          <div class="me-3"><i class="bi bi-files me-1"></i>{{ recording.recordingFiles.length }} file{{ recording.recordingFiles.length !== 1 ? 's' : '' }}</div>
                          <div v-if="recording.hasProfile && recording.profileSizeInBytes" class="text-primary">
                            <i class="bi bi-person-vcard me-1"></i>Profile: {{ FormattingService.formatBytes(recording.profileSizeInBytes) }}
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="d-flex">
                      <button
                        v-if="recording.hasProfile && recording.profileEnabled && !isProfileDeleting(recording)"
                        class="action-btn action-menu-btn action-info-btn me-2"
                        @click="editProfile(recording)"
                        title="Edit Profile"
                      >
                        <i class="bi bi-pencil"></i>
                      </button>
                      <button
                        v-if="recording.hasProfile && !isProfileDeleting(recording)"
                        class="action-btn action-menu-btn action-danger-btn me-2"
                        @click="deleteProfile(recording)"
                        title="Delete Profile"
                      >
                        <i class="bi bi-person-x"></i>
                      </button>
                      <button
                          class="action-btn action-menu-btn action-info-btn me-2"
                          @click="toggleRecordingFiles(recording)"
                          :title="expandedRecordingFiles.has(recording.id) ? 'Hide recording files' : 'Show recording files'"
                      >
                        <i class="bi" :class="expandedRecordingFiles.has(recording.id) ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
                      </button>
                      <button
                          class="action-btn action-menu-btn action-danger-btn"
                          @click="confirmDeleteRecording(recording)"
                          title="Delete recording">
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </div>

                  <!-- Recording Files (Expanded) -->
                  <div v-if="expandedRecordingFiles.has(recording.id)" class="ps-3 mt-2 mb-1 border-start border-2 ms-2">
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
                  </div>
                </div>
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

      <!-- Delete Profile Confirmation Dialog -->
      <ConfirmationDialog
        v-model:show="deleteProfileDialog"
        title="Confirm Profile Deletion"
        :message="profileToDelete ? `Are you sure you want to delete the profile for '${profileToDelete.profileName || profileToDelete.name}'?` : 'Are you sure you want to delete this profile?'"
        sub-message="This action cannot be undone. The recording will be preserved."
        confirm-label="Delete Profile"
        confirm-button-class="btn-danger"
        confirm-button-id="deleteProfileButton"
        modal-id="deleteProfileModal"
        @confirm="confirmDeleteProfile"
      >
        <template #confirm-button>
          <span v-if="deletingProfile" class="spinner-border spinner-border-sm me-2" role="status"></span>
          Delete Profile
        </template>
      </ConfirmationDialog>

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
    </div>
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

.upload-dropzone {
  border: 2px dashed #ccc;
  border-radius: 5px;
  transition: all 0.3s ease;
  background-color: #fafafa;
}

.upload-dropzone.active {
  border-color: #5e64ff;
  background-color: #f0f2ff;
}

.file-item {
  border: 1px solid #eee;
  border-radius: 4px;
  background-color: #f9f9f9;
}

.progress {
  height: 10px;
  border-radius: 5px;
  font-size: 0.6rem;
}

.display-4 {
  font-size: 3.5rem;
  line-height: 1.2;
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

@keyframes pulse {
  0% { opacity: 0.7; }
  50% { opacity: 1; }
  100% { opacity: 0.7; }
}

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

/* View button square styling */
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

/* Disabled profile styling */
.disabled-profile {
  opacity: 0.6;
}

.disabled-profile:hover {
  transform: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border-left-color: #6c757d;
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

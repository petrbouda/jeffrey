<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import ProjectRecordingClient from '@/services/api/ProjectRecordingClient';
import ProjectRecordingFolderClient from '@/services/api/ProjectRecordingFolderClient';
import {ToastService} from '@/services/ToastService';
import Recording from "@/services/api/model/Recording.ts";
import RecordingFolder from "@/services/api/model/RecordingFolder.ts";
import ProjectProfileClient from "@/services/api/ProjectProfileClient.ts";
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
const uploadProgress = ref({});
const uploadPanelExpanded = ref(false);

// Services
let projectProfileClient: ProjectProfileClient;
let projectRecordingClient: ProjectRecordingClient;
let projectRecordingFolderClient: ProjectRecordingFolderClient;

// Track expanded folders
const expandedFolders = ref<Set<string>>(new Set());

// Track expanded recording files sections
const expandedRecordingFiles = ref<Set<string>>(new Set());

const { workspaceId, projectId } = useNavigation();

onMounted(() => {
  if (!workspaceId.value || !projectId.value) return;

  projectProfileClient = new ProjectProfileClient(workspaceId.value, projectId.value);
  projectRecordingClient = new ProjectRecordingClient(workspaceId.value, projectId.value);
  projectRecordingFolderClient = new ProjectRecordingFolderClient(workspaceId.value, projectId.value);

  // Initialize with root folder expanded by default
  expandedFolders.value.add('root');
  
  loadData();
});

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

const folders = ref<RecordingFolder[]>([]);
const newFolderName = ref('');
const selectedFolderId = ref<string | null>(null);
// Create folder modal ref
const createFolderModal = ref<InstanceType<typeof BaseModal>>();

// Track profile creation states for each recording
const profileCreationStates = ref<Map<string, boolean>>(new Map());

const loadData = async () => {
  loading.value = true;
  try {
    // Load recordings and folders in parallel
    const [recordingsData, foldersData] = await Promise.all([
      projectRecordingClient.list(),
      projectRecordingFolderClient.list()
    ]);

    recordings.value = recordingsData;
    folders.value = foldersData;
  } catch (error: any) {
    toast.error('Failed to load data', error.message);
  } finally {
    // Notify sidebar of recording count change
    MessageBus.emit(MessageBus.RECORDINGS_COUNT_CHANGED, recordings.value.length);
    loading.value = false;
  }
};

// Organize recordings by folders
const organizedRecordings = computed(() => {
  // Create a set of valid folder IDs for quick lookup
  const validFolderIds = new Set(folders.value.map(folder => folder.id));

  // Root level recordings (no folder OR invalid folder ID)
  const rootRecordings = recordings.value.filter(recording =>
      recording.folderId == null || !validFolderIds.has(recording.folderId))
      // Sort from latest to oldest
      .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime());

  // Grouped by folder_id
  const folderRecordings = new Map<string, Recording[]>();

  // Group recordings by folder_id, but only if the folder actually exists
  recordings.value.forEach(recording => {
    // Only add to folder if folder exists
    const folderId = recording.folderId;
    if (folderId && validFolderIds.has(folderId)) {
      if (!folderRecordings.has(folderId)) {
        folderRecordings.set(folderId, []);
      }
      folderRecordings.get(folderId)?.push(recording);
    }
  });
  
  // Sort each folder's recordings from latest to oldest
  folderRecordings.forEach((folderRecs, folderId) => {
    folderRecordings.set(
      folderId, 
      folderRecs.sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    );
  });

  return {
    rootRecordings,
    folderRecordings
  };
});


const createProfile = async (recording: Recording) => {
  // Prevent multiple concurrent requests for the same recording
  if (profileCreationStates.value.get(recording.id) || recording.hasProfile) {
    return;
  }

  // Set loading state for this specific recording
  profileCreationStates.value.set(recording.id, true);

  // Optimistic UI update - set hasProfile to true immediately
  recording.hasProfile = true;

  try {
    // Emit event that profile initialization started (before API call)
    // This will show the "Initializing" badge in the sidebar immediately
    MessageBus.emit(MessageBus.PROFILE_INITIALIZATION_STARTED, true);

    await projectProfileClient.create(recording.id);
    toast.success('Profile Creation Started', `Asynchronous Profile Creation started from recording: ${recording.name}`);

    // Refresh recordings list but preserve hasProfile state for this recording
    await loadData();

    // Ensure the recording hasProfile flag remains true after data reload
    const updatedRecording = recordings.value.find(r => r.id === recording.id);
    if (updatedRecording) {
      updatedRecording.hasProfile = true;
    }
  } catch (error: any) {
    // If there was an error, revert the hasProfile flag
    recording.hasProfile = false;
    toast.error('Profile Creation Failed', error.message);
  } finally {
    // Always clear the loading state for this recording
    profileCreationStates.value.delete(recording.id);
  }
};

const confirmDeleteRecording = (recording: Recording) => {
  recordingToDelete.value = recording;
  deleteRecordingDialog.value = true;
};

const deleteRecording = async () => {
  if (!recordingToDelete.value) return;

  try {
    await projectRecordingClient.delete(recordingToDelete.value.id);
    toast.success('Recording Deleted', `Recording ${recordingToDelete.value.name} has been deleted`);

    // Refresh all data
    await loadData();

    // Close dialog
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

    // Refresh all data
    await loadData();

    // Close dialog
    deleteFolderDialog.value = false;
    folderToDelete.value = null;
  } catch (error: any) {
    toast.error('Delete Failed', error.message);
  }
};


const handleFileUpload = (event) => {
  const files = event.target.files || event.dataTransfer?.files;
  if (files && files.length) {
    const jfrFiles = Array.from(files).filter(file => file.name.endsWith('.jfr') || file.name.endsWith('.jfr.lz4'));
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

    // Reset form and close dialog
    newFolderName.value = '';
    createFolderModal.value?.hideModal();

    // Refresh data
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
    uploadProgress.value[file.name] = {
      progress: 0,
      status: 'pending'
    };
  }

  const uploadPromises = uploadFiles.value.map(async (file) => {
    try {
      uploadProgress.value[file.name].status = 'uploading';

      // Simulate progress updates
      const progressInterval = setInterval(() => {
        if (uploadProgress.value[file.name].progress < 90) {
          uploadProgress.value[file.name].progress += Math.floor(Math.random() * 10) + 5;
        }
      }, 300);

      // Upload the file using ProjectRecordingClient with selected folder
      // Pass null explicitly if no folder is selected to avoid "undefined" string
      await projectRecordingClient.upload(file, selectedFolderId.value || null);

      // Complete the progress
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

    // Refresh recordings list
    await loadData();

    // Reset files after a short delay to show completed status
    setTimeout(() => {
      uploadFiles.value = [];
      uploadProgress.value = {};
      // Close the upload panel after successful upload
      uploadPanelExpanded.value = false;
    }, 2000);
  } catch (error) {
    toast.error('Upload Failed', error.message);
  }
};

const handleDragOver = (event) => {
  event.preventDefault();
  dragActive.value = true;
};

const handleDragLeave = (event) => {
  event.preventDefault();
  dragActive.value = false;
};

const handleDrop = (event) => {
  event.preventDefault();
  dragActive.value = false;
  // Expand upload panel if it's collapsed
  if (!uploadPanelExpanded.value) {
    uploadPanelExpanded.value = true;
  }
  handleFileUpload(event);
};

const removeFile = (index) => {
  const newFiles = [...uploadFiles.value];
  newFiles.splice(index, 1);
  uploadFiles.value = newFiles;
};

// Helper function to check if a recording is currently creating a profile
const isRecordingCreatingProfile = (recordingId: string): boolean => {
  return profileCreationStates.value.get(recordingId) || false;
};
</script>

<template>
  <PageHeader
    title="Recordings"
    description="Contains the recordings uploaded to the server. You can create folders to organize your recordings."
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
        <!-- Folder Selection - Moved to the top of the panel -->
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
                     class="child-row p-3 mb-2 rounded">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <button
                          class="btn btn-sm me-3"
                          :class="{
                            'btn-success': !recording.hasProfile && !isRecordingCreatingProfile(recording.id),
                            'btn-outline-success': isRecordingCreatingProfile(recording.id)
                          }"
                          @click="createProfile(recording)"
                          :disabled="recording.hasProfile || isRecordingCreatingProfile(recording.id)"
                          :title="recording.hasProfile ? 'Profile already exists' : (isRecordingCreatingProfile(recording.id) ? 'Creating profile...' : 'Create profile from recording')"
                      >
                        <span v-if="isRecordingCreatingProfile(recording.id)" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                        <i v-else class="bi bi-plus-circle"></i>
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
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div class="me-3"><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                          <div><i class="bi bi-files me-1"></i>{{ recording.recordingFiles.length }} file{{ recording.recordingFiles.length !== 1 ? 's' : '' }}</div>
                        </div>
                      </div>
                    </div>
                    <div class="d-flex">
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


            <!-- Root Recordings (displayed directly without synthetic folder) -->
            <div v-if="organizedRecordings.rootRecordings.length > 0" class="mt-3">
              <div class="mb-3" v-if="folders.length > 0">
                <div class="root-recordings-bar d-flex align-items-center px-3">
                  <span class="root-header-text">Root Recordings ({{ organizedRecordings.rootRecordings.length }})</span>
                </div>
              </div>

              <div class="root-recordings-list">
                <div v-for="recording in organizedRecordings.rootRecordings" :key="recording.id" class="child-row p-3 mb-2 rounded">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <button
                          class="btn btn-sm me-3"
                          :class="{
                            'btn-success': !recording.hasProfile && !isRecordingCreatingProfile(recording.id),
                            'btn-outline-success': isRecordingCreatingProfile(recording.id)
                          }"
                          @click="createProfile(recording)"
                          :disabled="recording.hasProfile || isRecordingCreatingProfile(recording.id)"
                          :title="recording.hasProfile ? 'Profile already exists' : (isRecordingCreatingProfile(recording.id) ? 'Creating profile...' : 'Create profile from recording')"
                      >
                        <span v-if="isRecordingCreatingProfile(recording.id)" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                        <i v-else class="bi bi-plus-circle"></i>
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
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div class="me-3"><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                          <div><i class="bi bi-files me-1"></i>{{ recording.recordingFiles.length }} file{{ recording.recordingFiles.length !== 1 ? 's' : '' }}</div>
                        </div>
                      </div>
                    </div>
                    <div class="d-flex">
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

.action-danger-btn {
  color: #fff;
  background-color: #dc3545;
  border-color: #dc3545;
}

.action-danger-btn:hover {
  background-color: #c82333;
  border-color: #bd2130;
  color: #fff;
}

/* Section divider styling */
.section-divider {
  height: 1px;
  background-color: #e9ecef;
  margin: 1.5rem 0 1rem;
}

.root-folder-row {
  background-color: rgba(94, 100, 255, 0.03);
  border-left: 3px solid #5e64ff;
}

.modern-empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #adb5bd;
  background-color: white;
  border-radius: 10px;
  padding: 3rem;
  text-align: center;
  box-shadow: 0 2px 15px rgba(0, 0, 0, 0.05);
  margin-bottom: 2rem;
}

/* Download button styling */
.download-file-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: opacity 0.15s ease;
}

.download-file-btn:hover {
  opacity: 1;
}

.recording-file-row:hover .download-file-btn {
  opacity: 0.8;
}

/* Action info button style */
.action-info-btn {
  color: #fff;
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.action-info-btn:hover {
  background-color: #4a50e3;
  border-color: #4a50e3;
  color: #fff;
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

/* Square button with rounded corners for profile creation */
.btn-sm.me-3 {
  width: 40px;
  height: 40px;
  min-width: 40px;
  padding: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 0.875rem;
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
</style>

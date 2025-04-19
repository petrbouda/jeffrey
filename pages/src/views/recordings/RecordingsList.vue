<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import ProjectRecordingClient from '@/services/ProjectRecordingClient';
import ProjectRecordingFolderClient from '@/services/ProjectRecordingFolderClient';
import {ToastService} from '@/services/ToastService';
import Recording from "@/services/model/Recording.ts";
import RecordingFolder from "@/services/model/RecordingFolder.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import FormattingService from "../../services/FormattingService.ts";

const route = useRoute();
const toast = ToastService;
const recordings = ref<Recording[]>([]);
const loading = ref(true);
const deleteRecordingDialog = ref(false);
const recordingToDelete = ref<Recording | null>();
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

onMounted(() => {
  const projectId = route.params.projectId as string;
  projectProfileClient = new ProjectProfileClient(projectId);
  projectRecordingClient = new ProjectRecordingClient(projectId);
  projectRecordingFolderClient = new ProjectRecordingFolderClient(projectId);

  loadData();
});

const folders = ref<RecordingFolder[]>([]);
const createFolderDialog = ref(false);
const newFolderName = ref('');
const selectedFolderId = ref<string | null>(null);

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
    loading.value = false;
  }
};

// Organize recordings by folders
const organizedRecordings = computed(() => {
  // Create a set of valid folder IDs for quick lookup
  const validFolderIds = new Set(folders.value.map(folder => folder.id));

  // Root level recordings (no folder OR invalid folder ID)
  const rootRecordings = recordings.value.filter(recording =>
      recording.folderId == null || !validFolderIds.has(recording.folderId));

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

  return {
    rootRecordings,
    folderRecordings
  };
});


const createProfile = async (recording: Recording) => {
  // Set the recording's hasProfile to true immediately to update UI
  recording.hasProfile = true;

  try {
    await projectProfileClient.create(recording.id);
    toast.success('Profile Created', `Profile created from recording: ${recording.name}`);

    // Refresh recordings list
    await loadData();
  } catch (error: any) {
    // If there was an error, revert the hasProfile flag
    recording.hasProfile = false;
    toast.error('Profile Creation Failed', error.message);
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


const handleFileUpload = (event) => {
  const files = event.target.files || event.dataTransfer?.files;
  if (files && files.length) {
    const jfrFiles = Array.from(files).filter(file => file.name.endsWith('.jfr'));
    if (jfrFiles.length === 0) {
      toast.warn('Invalid Files', 'Only JFR files are supported');
      return;
    }
    uploadFiles.value = [...uploadFiles.value, ...jfrFiles];
  }
};


const createFolder = async () => {
  if (!newFolderName.value.trim()) {
    toast.warn('Validation Error', 'Folder name cannot be empty');
    return;
  }

  try {
    await projectRecordingFolderClient.create(newFolderName.value.trim());
    toast.success('Folder Created', `Folder "${newFolderName.value.trim()}" has been created`);

    // Reset form and close dialog
    newFolderName.value = '';
    createFolderDialog.value = false;

    // Refresh data
    await loadData();
  } catch (error: any) {
    toast.error('Failed to create folder', error.message);
  }
};

const openCreateFolderDialog = () => {
  createFolderDialog.value = true;
  // Focus the input field after dialog is shown
  nextTick(() => {
    const inputEl = document.getElementById('newFolderNameInput');
    if (inputEl) {
      inputEl.focus();
    }
  });
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
</script>

<template>
  <!-- Page Header -->
  <div class="col-12">
    <div class="d-flex align-items-center mb-3">
      <i class="bi bi-calendar-check fs-4 me-2 text-primary"></i>
      <h3 class="mb-0">Recordings</h3>
    </div>
    <p class="text-muted mb-4">
      Contains the recordings uploaded to the server. You can create folders to organize your recordings.
    </p>
  </div>

  <!-- File Upload Panel -->
  <div class="col-12">
    <div class="card shadow-sm border-0 mb-4">
      <div class="card-header bg-light d-flex justify-content-between align-items-center cursor-pointer py-3"
           @click="uploadPanelExpanded = !uploadPanelExpanded">
        <div class="d-flex align-items-center">
          <i class="bi bi-upload fs-4 me-2"></i>
          <h5 class="card-title mb-0">Upload Recordings</h5>
        </div>
        <div class="d-flex align-items-center">
          <div class="text-muted small me-3">Only .jfr files are supported</div>
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
                accept=".jfr"
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

    <!-- Recordings List -->
    <div class="col-12">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-record-circle fs-4 me-2"></i>
            <h5 class="card-title mb-0">Recordings</h5>
          </div>
          <div>
            <button class="btn btn-primary btn-sm" @click="openCreateFolderDialog">
              <i class="bi bi-folder-plus me-1"></i>New Folder
            </button>
          </div>
        </div>

        <div class="card-body">
          <div v-if="loading" class="text-center p-5">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-2">Loading recordings...</p>
          </div>

          <div v-else-if="recordings.length === 0 && folders.length === 0" class="alert alert-info">
            <i class="bi bi-info-circle-fill me-2"></i>
            No recordings or folders found. Upload a JFR file or create a folder to get started.
          </div>

          <div v-else class="table-responsive">
            <table class="table table-hover">
              <thead>
              <tr>
                <th style="width: 50px"></th>
                <th>Name</th>
                <th>Size</th>
                <th>Duration</th>
                <th>Uploaded At</th>
                <th class="text-end">Actions</th>
              </tr>
              </thead>
              <tbody>
              <!-- Folders with their recordings - one folder at a time -->
              <template v-for="folder in folders" :key="`folder-group-${folder.id}`">
                <!-- Folder row -->
                <tr class="folder-row"
                    @click="expandedFolders.has(folder.id) ? expandedFolders.delete(folder.id) : expandedFolders.add(folder.id)">
                  <td class="text-center">
                    <i class="bi fs-5" :class="expandedFolders.has(folder.id) ? 'bi-folder2-open' : 'bi-folder2'"></i>
                  </td>
                  <td class="fw-bold" colspan="4">
                    <div class="d-flex align-items-center">
                      <i class="bi me-2"
                         :class="expandedFolders.has(folder.id) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                      {{ folder.name }}
                      <span class="badge bg-secondary ms-2">
                    {{ organizedRecordings.folderRecordings.get(folder.id)?.length || 0 }} files
                  </span>
                    </div>
                  </td>
                  <td class="text-end">
                    <div class="d-flex justify-content-end">
                      <!-- Add folder actions if needed -->
                    </div>
                  </td>
                </tr>

                <!-- Recordings belonging to this folder -->
                <tr v-for="recording in expandedFolders.has(folder.id) ? (organizedRecordings.folderRecordings.get(folder.id) || []) : []"
                    :key="`recording-${recording.id}`"
                    class="child-row">
                  <td class="text-center ps-4">
                    <button
                        class="btn btn-sm btn-success"
                        @click="createProfile(recording)"
                        :disabled="recording.hasProfile"
                        :title="recording.hasProfile ? 'Profile already exists' : 'Create profile from recording'"
                    >
                      <i class="bi bi-plus-circle"></i>
                    </button>
                  </td>
                  <td class="fw-bold ps-4">
                    <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                    {{ recording.name }}
                  </td>
                  <td>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</td>
                  <td>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</td>
                  <td>{{ recording.uploadedAt }}</td>
                  <td class="text-end">
                    <div class="d-flex justify-content-end">
                      <button
                          class="btn btn-sm btn-outline-danger"
                          @click="confirmDeleteRecording(recording)"
                      >
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </td>
                </tr>
              </template>

              <!-- Root Recordings (no folder) - directly in the table -->
              <tr v-for="recording in organizedRecordings.rootRecordings" :key="recording.id">
                <td class="text-center">
                  <button
                      class="btn btn-sm btn-success"
                      @click="createProfile(recording)"
                      :disabled="recording.hasProfile"
                      :title="recording.hasProfile ? 'Profile already exists' : 'Create profile from recording'"
                  >
                    <i class="bi bi-plus-circle"></i>
                  </button>
                </td>
                <td class="fw-bold">
                  <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                  {{ recording.name }}
                </td>
                <td>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</td>
                <td>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</td>
                <td>{{ recording.uploadedAt }}</td>
                <td class="text-end">
                  <div class="d-flex justify-content-end">
                    <button
                        class="btn btn-sm btn-outline-danger"
                        @click="confirmDeleteRecording(recording)">
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Delete Confirmation Dialog -->
      <div class="modal" :class="{ 'd-block': deleteRecordingDialog, 'd-none': !deleteRecordingDialog }">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Confirm Delete</h5>
              <button type="button" class="btn-close" @click="deleteRecordingDialog = false"></button>
            </div>
            <div class="modal-body" v-if="recordingToDelete">
              <p>Are you sure you want to delete the recording: <strong>{{ recordingToDelete.name }}</strong>?</p>
              <p class="text-danger">This action cannot be undone.</p>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" @click="deleteRecordingDialog = false">Cancel</button>
              <button type="button" class="btn btn-danger" @click="deleteRecording">Delete</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Create Folder Dialog -->
      <div class="modal" :class="{ 'd-block': createFolderDialog, 'd-none': !createFolderDialog }">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Create New Folder</h5>
              <button type="button" class="btn-close" @click="createFolderDialog = false"></button>
            </div>
            <div class="modal-body">
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
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" @click="createFolderDialog = false">Cancel</button>
              <button type="button" class="btn btn-primary" @click="createFolder">Create</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
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
  background-color: #f8f9fa;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.2s;
}

.folder-row:hover {
  background-color: #f0f2ff;
}

.folder-row.active {
  background-color: #edf2ff;
  border-bottom: 1px solid #ddd;
}

.folder-row.folder-dragover {
  background-color: #e6f0ff;
  border: 2px dashed #5e64ff;
}

.folder-name {
  cursor: pointer;
}

.upload-hint {
  color: #5e64ff;
  font-size: 0.85rem;
  font-weight: normal;
  opacity: 0.9;
  animation: pulse 1.5s infinite;
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
  background-color: #fcfcff;
  transition: all 0.3s ease;
}

.child-row:hover {
  background-color: #f5f7ff;
}

.ps-4 {
  padding-left: 2.5rem !important;
}

/* Icons for folder expansion */
.bi-folder2, .bi-folder2-open {
  color: #ffc107 !important;
}

.folder-row .bi-chevron-right,
.folder-row .bi-chevron-down {
  transition: transform 0.2s ease;
}

.folder-row:hover .bi-chevron-right {
  transform: translateX(2px);
}
</style>

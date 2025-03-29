<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import ProjectRecordingClient from '@/services/ProjectRecordingClient';
import RecordingService from '@/services/RecordingService';
import ProfileService from '@/services/ProfileService';
import Utils from '@/services/Utils';
import {ToastService} from '@/services/ToastService';

const route = useRoute();
const toast = ToastService;
const recordings = ref([]);
const loading = ref(true);
const createFolderDialog = ref(false);
const newFolderName = ref('');
const deleteRecordingDialog = ref(false);
const recordingToDelete = ref(null);
const uploadFiles = ref<File[]>([]);
const dragActive = ref(false);
const uploadProgress = ref({});
const selectedFolder = ref(null);
const uploadTargetFolder = ref('');
const uploadPanelExpanded = ref(false);

// Services
let recordingService;
let profileService;
let projectRecordingClient: ProjectRecordingClient;

onMounted(() => {
  const projectId = route.params.projectId as string;
  recordingService = new RecordingService(projectId);
  profileService = new ProfileService(projectId);
  projectRecordingClient = new ProjectRecordingClient(projectId);

  loadRecordings();
});

const loadRecordings = async () => {
  loading.value = true;
  try {
    recordings.value = await recordingService.list();
  } catch (error) {
    toast.error('Failed to load recordings', error.message);
  } finally {
    loading.value = false;
  }
};

// Get the list of folders
const folders = computed(() => {
  return recordings.value
      .filter(recording => recording.isFolder)
      .map(folder => folder.name);
});

// Organize recordings in a tree structure
const organizedRecordings = computed(() => {
  const result = [];

  // Add folders first
  const foldersList = recordings.value.filter(recording => recording.isFolder);
  result.push(...foldersList);

  // Add recordings without folders
  const rootRecordings = recordings.value.filter(
      recording => !recording.isFolder && !recording.folder
  );
  result.push(...rootRecordings);

  return result;
});

// Filter recordings by folder
const getRecordingsForFolder = (folderName) => {
  return recordings.value.filter(
      recording => !recording.isFolder && recording.folder === folderName
  );
};

// Check if a folder is expanded
const isFolderExpanded = (folderName) => {
  return selectedFolder.value === folderName;
};

// Toggle folder expansion
const toggleFolder = (folderName) => {
  if (selectedFolder.value === folderName) {
    selectedFolder.value = null;
  } else {
    selectedFolder.value = folderName;
  }
};

// Handle drag over folder row
const folderDragState = ref({
  draggedOver: null
});

const handleFolderDragOver = (event, folderName) => {
  event.preventDefault();
  event.stopPropagation();
  folderDragState.value.draggedOver = folderName;
};

const handleFolderDragLeave = (event) => {
  event.preventDefault();
  event.stopPropagation();
  folderDragState.value.draggedOver = null;
};

const handleFolderDrop = (event, folderName) => {
  event.preventDefault();
  event.stopPropagation();
  folderDragState.value.draggedOver = null;

  // Set target folder and process files
  uploadTargetFolder.value = folderName;
  handleFileUpload(event);
};

const createProfile = async (recording) => {
  try {
    await profileService.create(recording.name.replace('.jfr', ''));
    await recordingService.markHasProfile(recording.id);

    toast.success('Profile Created', `Profile created from recording: ${recording.name}`);

    // Refresh recordings list
    await loadRecordings();
  } catch (error) {
    toast.error('Profile Creation Failed', error.message);
  }
};

const confirmDeleteRecording = (recording) => {
  recordingToDelete.value = recording;
  deleteRecordingDialog.value = true;
};

const deleteRecording = async () => {
  if (!recordingToDelete.value) return;

  try {
    await recordingService.delete(recordingToDelete.value.id);
    toast.success('Recording Deleted', `Recording ${recordingToDelete.value.name} has been deleted`);

    // Refresh recordings list
    await loadRecordings();

    // Close dialog
    deleteRecordingDialog.value = false;
    recordingToDelete.value = null;
  } catch (error) {
    toast.error('Delete Failed', error.message);
  }
};


const openCreateFolderDialog = () => {
  createFolderDialog.value = true;
  newFolderName.value = '';
};

const createFolder = async () => {
  if (!newFolderName.value.trim()) {
    toast.warn('Invalid Folder Name', 'Please enter a valid folder name');
    return;
  }

  try {
    await recordingService.createFolder(newFolderName.value.trim());
    toast.success('Folder Created', `Folder ${newFolderName.value} created successfully`);

    // Refresh recordings list
    await loadRecordings();

    // Close dialog
    createFolderDialog.value = false;
    newFolderName.value = '';
  } catch (error) {
    toast.error('Failed to Create Folder', error.message);
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

      // Upload the file
      await projectRecordingClient.upload(file, uploadTargetFolder.value || null);
      await recordingService.upload(file, uploadTargetFolder.value || null);

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
    await loadRecordings();

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
  <!-- File Upload Panel -->
  <div class="card w-100 mb-4">
    <div class="card-header bg-light d-flex justify-content-between align-items-center cursor-pointer"
         @click="uploadPanelExpanded = !uploadPanelExpanded">
      <div class="d-flex align-items-center">
        <i class="bi bi-upload me-2"></i>
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
      <!-- Folder selection for drag & drop area -->
      <div class="mb-3">
        <label for="dragDropFolder" class="form-label">Select Destination Folder</label>
        <select
            class="form-select"
            id="dragDropFolder"
            v-model="uploadTargetFolder"
        >
          <option value="">Root (No Folder)</option>
          <option v-for="folder in folders" :key="folder" :value="folder">
            {{ folder }}
          </option>
        </select>
        <div class="form-text">
          <i class="bi bi-info-circle me-1"></i>Files will be uploaded to the selected folder
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
          <p class="text-muted">
            Files will be uploaded to
            <span v-if="uploadTargetFolder" class="fw-bold text-primary">
              <i class="bi bi-folder me-1"></i>{{ uploadTargetFolder }}
            </span>
            <span v-else>
              the root folder
            </span>
          </p>
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
                Uploading to
                <span v-if="uploadTargetFolder" class="fw-medium text-primary">
                  <i class="bi bi-folder me-1"></i>{{ uploadTargetFolder }}
                </span>
                <span v-else>
                  root folder
                </span>
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
                    <div class="text-muted small">{{ Utils.formatFileSize(file.size) }}</div>
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
  <div class="card w-100">
    <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white">
      <h5 class="card-title mb-0">Recordings</h5>
      <div>
        <button class="btn btn-sm btn-light" @click="openCreateFolderDialog">
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

      <div v-else-if="recordings.length === 0" class="alert alert-info">
        <i class="bi bi-info-circle-fill me-2"></i>
        No recordings found. Upload a JFR file or create a folder to get started.
      </div>

      <div v-else class="table-responsive">
        <table class="table table-hover">
          <thead>
          <tr>
            <th style="width: 50px"></th>
            <th>Name</th>
            <th>Size</th>
            <th>Duration</th>
            <th>Recorded At</th>
            <th class="text-end">Actions</th>
          </tr>
          </thead>
          <tbody>
          <!-- Render the organized tree structure -->
          <template v-for="recording in organizedRecordings" :key="recording.id">
            <!-- Folder row -->
            <tr v-if="recording.isFolder"
                class="folder-row"
                :class="{ 
                  'active': isFolderExpanded(recording.name),
                  'folder-dragover': folderDragState.draggedOver === recording.name 
                }"
                @dragover="handleFolderDragOver($event, recording.name)"
                @dragleave="handleFolderDragLeave"
                @drop="handleFolderDrop($event, recording.name)"
            >
              <td class="text-center">
                <button
                    class="btn btn-sm btn-outline-primary"
                    @click="toggleFolder(recording.name)"
                >
                  <i class="bi" :class="isFolderExpanded(recording.name) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                </button>
              </td>
              <td class="fw-bold folder-name" @click="toggleFolder(recording.name)">
                <i class="bi bi-folder me-2 text-primary"></i>
                {{ recording.name }}
                <span v-if="folderDragState.draggedOver === recording.name" class="upload-hint ms-2">
                    <i class="bi bi-upload"></i> Drop to upload here
                  </span>
              </td>
              <td>-</td>
              <td>-</td>
              <td>{{ Utils.formatDate(recording.recordedAt) }}</td>
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

            <!-- Child recordings within a folder -->
            <template v-if="recording.isFolder && isFolderExpanded(recording.name)">
              <tr v-for="childRecording in getRecordingsForFolder(recording.name)" :key="childRecording.id"
                  class="child-row">
                <td class="text-center">
                  <button
                      class="btn btn-sm btn-success"
                      @click="createProfile(childRecording)"
                      :disabled="childRecording.hasProfile"
                      :title="childRecording.hasProfile ? 'Profile already exists' : 'Create profile from recording'"
                  >
                    <i class="bi bi-plus-circle"></i>
                  </button>
                </td>
                <td class="fw-bold ps-4">
                  <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                  {{ childRecording.name }}
                </td>
                <td>{{ Utils.formatFileSize(childRecording.size) }}</td>
                <td>{{ Utils.formatDuration(childRecording.duration) }}</td>
                <td>{{ Utils.formatDate(childRecording.recordedAt) }}</td>
                <td class="text-end">
                  <div class="d-flex justify-content-end">
                    <button
                        class="btn btn-sm btn-outline-danger"
                        @click="confirmDeleteRecording(childRecording)"
                    >
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </td>
              </tr>
            </template>

            <!-- Regular recording (not in a folder) -->
            <tr v-if="!recording.isFolder">
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
              <td>{{ Utils.formatFileSize(recording.size) }}</td>
              <td>{{ Utils.formatDuration(recording.duration) }}</td>
              <td>{{ Utils.formatDate(recording.recordedAt) }}</td>
              <td>
                <div class="d-flex justify-content-center">
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
          </tbody>
        </table>
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
          <div class="mb-3">
            <label for="newFolderName" class="form-label">Folder Name</label>
            <input
                type="text"
                class="form-control"
                id="newFolderName"
                v-model="newFolderName"
                placeholder="Enter folder name"
            >
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="createFolderDialog = false">Cancel</button>
          <button
              type="button"
              class="btn btn-primary"
              :disabled="!newFolderName.trim()"
              @click="createFolder"
          >
            Create
          </button>
        </div>
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
          <p>Are you sure you want to delete
            <span v-if="recordingToDelete.isFolder">the folder: <strong>{{ recordingToDelete.name }}</strong>?</span>
            <span v-else>the recording: <strong>{{ recordingToDelete.name }}</strong>?</span>
          </p>
          <p v-if="recordingToDelete.isFolder" class="text-danger">
            Deleting a folder will also delete all recordings inside it.
          </p>
          <p class="text-danger">This action cannot be undone.</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="deleteRecordingDialog = false">Cancel</button>
          <button type="button" class="btn btn-danger" @click="deleteRecording">Delete</button>
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
}

.child-row:hover {
  background-color: #f5f7ff;
}

.ps-4 {
  padding-left: 2.5rem !important;
}
</style>

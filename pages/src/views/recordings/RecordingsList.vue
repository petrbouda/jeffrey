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
import MessageBus from "@/services/MessageBus";

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

  // Initialize with root folder expanded by default
  expandedFolders.value.add('root');
  
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
  // Set the recording's hasProfile to true immediately to update UI
  recording.hasProfile = true;

  try {
    // Emit event that profile initialization started (before API call)
    // This will show the "Initializing" badge in the sidebar immediately
    MessageBus.emit(MessageBus.PROFILE_INITIALIZATION_STARTED, true);
    
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
          <i class="bi bi-upload fs-4 me-2 text-primary"></i>
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
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-collection fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Recordings</h5>
          <span class="badge modern-badge ms-2">
            {{ recordings.length }} recording{{ recordings.length !== 1 ? 's' : '' }}
          </span>
          <div class="ms-auto">
            <button class="btn btn-primary btn-sm" @click="openCreateFolderDialog">
              <i class="bi bi-folder-plus me-1"></i>New Folder
            </button>
          </div>
        </div>
        
        <div class="card-body">
          <div v-if="loading" class="modern-empty-state loading">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-3">Loading recordings...</p>
          </div>

          <div v-else-if="recordings.length === 0 && folders.length === 0" class="modern-empty-state">
            <i class="bi bi-folder-x display-4 text-muted"></i>
            <h5 class="mt-3">No Recordings Available</h5>
            <p class="text-muted">Upload a JFR file or create a folder to get started.</p>
          </div>

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
                      <span class="badge modern-count-badge ms-2">
                        {{ organizedRecordings.folderRecordings.get(folder.id)?.length || 0 }} recordings
                      </span>
                    </div>
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
                          class="btn btn-sm btn-success me-3"
                          @click="createProfile(recording)"
                          :disabled="recording.hasProfile"
                          :title="recording.hasProfile ? 'Profile already exists' : 'Create profile from recording'"
                      >
                        <i class="bi bi-plus-circle"></i>
                      </button>
                      <div>
                        <div class="fw-bold">
                          <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                          {{ recording.name }}
                          <span class="badge ms-2 source-badge" :class="recording.sourceType === 'JDK' ? 'jdk-source' : 'default-source'">
                            {{ recording.sourceType || 'Unknown' }}
                          </span>
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                        </div>
                      </div>
                    </div>
                    <button
                        class="action-btn action-menu-btn action-danger-btn"
                        @click="confirmDeleteRecording(recording)"
                        title="Delete recording"
                    >
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Divider between folders and root recordings -->
            <div v-if="folders.length > 0" class="section-divider"></div>
            
            <!-- Root Recordings (no folder) -->
            <div class="mt-3">
              <div class="folder-row p-3 rounded mb-3 root-folder-row"
                   @click="expandedFolders.has('root') ? expandedFolders.delete('root') : expandedFolders.add('root')">
                <div class="d-flex justify-content-between align-items-center">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-hdd-stack fs-5 me-2 text-primary"></i>
                    <div class="fw-bold">
                      <i class="bi me-2"
                         :class="expandedFolders.has('root') ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                      Root Recordings
                      <span class="badge modern-count-badge ms-2">
                        {{ organizedRecordings.rootRecordings.length }} recordings
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="expandedFolders.has('root')" class="ps-4 pt-2">
                <div v-for="recording in organizedRecordings.rootRecordings" :key="recording.id" class="child-row p-3 mb-2 rounded">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <button
                          class="btn btn-sm btn-success me-3"
                          @click="createProfile(recording)"
                          :disabled="recording.hasProfile"
                          :title="recording.hasProfile ? 'Profile already exists' : 'Create profile from recording'"
                      >
                        <i class="bi bi-plus-circle"></i>
                      </button>
                      <div>
                        <div class="fw-bold">
                          <i class="bi bi-file-earmark-binary me-2 text-secondary"></i>
                          {{ recording.name }}
                          <span class="badge ms-2 source-badge" :class="recording.sourceType === 'JDK' ? 'jdk-source' : 'default-source'">
                            {{ recording.sourceType || 'Unknown' }}
                          </span>
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationInMillis2Units(recording.durationInMillis) }}</div>
                          <div class="me-3"><i class="bi bi-hdd me-1"></i>{{ FormattingService.formatBytes(recording.sizeInBytes) }}</div>
                          <div><i class="bi bi-calendar me-1"></i>{{ recording.uploadedAt }}</div>
                        </div>
                      </div>
                    </div>
                    <button
                        class="action-btn action-menu-btn action-danger-btn"
                        @click="confirmDeleteRecording(recording)"
                        title="Delete recording">
                      <i class="bi bi-trash"></i>
                    </button>
                  </div>
                </div>
              </div>
            </div>
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

.folder-row.active-session {
  background-color: rgba(94, 100, 255, 0.07);
  border-left: 3px solid #5e64ff;
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
  background-color: white;
  border: 1px solid #eee;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;
}

.child-row:hover {
  background-color: rgba(247, 248, 252, 0.8);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.child-row.source-in-progress {
  background-color: rgba(255, 248, 230, 0.3);
  border-left: 3px solid #ffc107;
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

/* Badge styling */
.modern-badge {
  background-color: #5e64ff;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
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

.modern-count-badge {
  background-color: #5e64ff;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.size-badge {
  background-color: #e9ecef;
  color: #495057 !important;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.in-progress-badge {
  background-color: #ffc107;
  color: #212529;
  font-weight: 500;
  font-size: 0.75rem;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
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
</style>

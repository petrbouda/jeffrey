<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import RecordingService from '@/services/RecordingService';
import ProfileService from '@/services/ProfileService';
import Utils from '@/services/Utils';
import { ToastService } from '@/services/ToastService';

const route = useRoute();
const toast = ToastService;
const recordings = ref([]);
const loading = ref(true);
const uploadDialog = ref(false);
const deleteRecordingDialog = ref(false);
const recordingToDelete = ref(null);
const uploadFiles = ref([]);
const dragActive = ref(false);
const uploadProgress = ref({});

// Services
let recordingService;
let profileService;

onMounted(() => {
  const projectId = route.params.projectId || 'default';
  recordingService = new RecordingService(projectId);
  profileService = new ProfileService(projectId);
  
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

const openUploadDialog = () => {
  uploadDialog.value = true;
  uploadFiles.value = [];
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
      await recordingService.upload(file);
      
      // Complete the progress
      clearInterval(progressInterval);
      uploadProgress.value[file.name].progress = 100;
      uploadProgress.value[file.name].status = 'complete';
      
      return { file, success: true };
    } catch (error) {
      uploadProgress.value[file.name].status = 'error';
      return { file, success: false, error };
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
    <div class="card-header bg-light d-flex justify-content-between align-items-center">
      <h5 class="card-title mb-0"><i class="bi bi-upload me-2"></i>Upload Recordings</h5>
      <div class="text-muted small">Only .jfr files are supported</div>
    </div>
    
    <div class="card-body">
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
          <p class="text-muted">Or click to select files</p>
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
            <h6 class="mb-0"><i class="bi bi-files me-2"></i>Selected Files ({{ uploadFiles.length }})</h6>
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
    <div class="card-header bg-soft-blue text-white">
      <h5 class="card-title mb-0">Recordings</h5>
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
        No recordings found. Upload a JFR file to get started.
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
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="recording in recordings" :key="recording.id">
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
              <td class="fw-bold">{{ recording.name }}</td>
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
          </tbody>
        </table>
      </div>
    </div>
  </div>
  
  <!-- Upload Dialog -->
  <div class="modal" :class="{ 'd-block': uploadDialog, 'd-none': !uploadDialog }">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Upload Recordings</h5>
          <button type="button" class="btn-close" @click="uploadDialog = false"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <label for="fileUpload" class="form-label">Select JFR files</label>
            <input 
              type="file" 
              class="form-control" 
              id="fileUpload" 
              accept=".jfr" 
              multiple 
              @change="handleFileUpload"
            >
          </div>
          
          <div v-if="uploadFiles.length > 0" class="mt-3">
            <p class="mb-2">Selected files:</p>
            <ul class="list-group">
              <li v-for="(file, index) in uploadFiles" :key="index" class="list-group-item d-flex justify-content-between align-items-center">
                {{ file.name }}
                <span class="badge bg-primary rounded-pill">{{ Utils.formatFileSize(file.size) }}</span>
              </li>
            </ul>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="uploadDialog = false">Cancel</button>
          <button 
            type="button" 
            class="btn btn-primary" 
            :disabled="!uploadFiles.length" 
            @click="uploadRecordings"
          >
            Upload
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
</style>
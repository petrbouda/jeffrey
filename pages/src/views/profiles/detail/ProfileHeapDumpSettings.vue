<template>
  <!-- Delete Heap Dump Confirmation Modal -->
  <ConfirmationDialog
      v-model:show="deleteHeapDumpDialog"
      title="Delete Heap Dump"
      message="Are you sure you want to delete the heap dump?"
      sub-message="This will remove all heap dump files including the cache. This action cannot be undone."
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteHeapDumpButton"
      modal-id="deleteHeapDumpModal"
      @confirm="confirmDeleteHeapDump"
  />

  <LoadingState v-if="loading" message="Loading heap dump status..." />

  <div v-else-if="!heapExists">
    <PageHeader
        title="Heap Dump Overview"
        description="Memory analysis summary and processing status"
        icon="bi-memory">
    </PageHeader>

    <!-- Upload Dropzone -->
    <div
        ref="dropzoneRef"
        class="upload-dropzone p-4"
        :class="{ 'active': dragActive, 'has-file': uploadFile, 'clickable': !uploadFile }"
        tabindex="0"
        @dragover="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
        @click="handleDropzoneClick"
        @keyup.enter="handleEnterKey"
    >
      <!-- No file selected -->
      <div v-if="!uploadFile" class="text-center py-4">
        <i class="bi bi-cloud-upload display-4 text-primary mb-3"></i>
        <h5>Drag & Drop Heap Dump Here</h5>
        <p class="text-muted small mb-3">or click anywhere to browse files</p>
        <input
            type="file"
            ref="fileInputRef"
            id="heapDumpFileInput"
            class="d-none"
            accept=".hprof,.hprof.gz"
            @change="handleFileSelect"
        >
        <label for="heapDumpFileInput" class="btn btn-primary" @click.stop>
          <i class="bi bi-folder me-2"></i>Browse Files
        </label>
      </div>

      <!-- File selected -->
      <div v-else>
        <div class="d-flex justify-content-between mb-3">
          <div>
            <h6 class="mb-0"><i class="bi bi-file-earmark-binary me-2"></i>Selected File</h6>
          </div>
          <div>
            <button class="btn btn-success btn-sm me-2" @click="uploadHeapDump" :disabled="uploading">
              <i class="bi bi-cloud-upload me-1"></i>Upload
            </button>
            <button class="btn btn-outline-secondary btn-sm" @click="removeFile" :disabled="uploading">
              <i class="bi bi-x-lg me-1"></i>Clear
            </button>
          </div>
        </div>

        <div class="file-item p-2">
          <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center">
              <i class="bi bi-file-earmark-binary text-primary me-2 fs-5"></i>
              <div>
                <div class="fw-bold">{{ uploadFile.name }}</div>
                <div class="text-muted small">{{ FormattingService.formatBytes(uploadFile.size) }}</div>
              </div>
            </div>
            <span v-if="!uploading" class="text-muted small">Press <kbd>Enter</kbd> to upload</span>
          </div>

          <div v-if="uploading" class="mt-2">
            <div class="progress">
              <div
                  class="progress-bar progress-bar-striped progress-bar-animated"
                  role="progressbar"
                  :style="{ width: uploadProgress + '%' }"
                  :aria-valuenow="uploadProgress"
                  aria-valuemin="0"
                  aria-valuemax="100">
                {{ uploadProgress }}%
              </div>
            </div>
            <div class="text-center mt-1 small text-primary">
              <i class="bi bi-arrow-clockwise me-1"></i>Uploading...
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Heap Dump Overview"
        description="Memory analysis summary and processing status"
        icon="bi-memory">
    </PageHeader>

    <!-- Summary Stats -->
    <StatsTable v-if="lastSummary" :metrics="summaryMetrics" class="mb-4" />

    <!-- Initialize Card - Not Processed State -->
    <div v-if="!cacheReady && !processing && !lastSummary" class="init-section">
      <div class="init-main-card">
        <div class="init-header">
          <div class="init-header-icon">
            <i class="bi bi-cpu"></i>
          </div>
          <h4>Heap Dump Not Initialized</h4>
          <p>Process the heap dump to build indexes and enable memory analysis features. This may take a few minutes for large heap dumps.</p>
        </div>
        <button class="ready-card action primary init-panel-btn" @click="processHeapDump">
          <div class="ready-card-icon primary large">
            <i class="bi bi-lightning-charge-fill"></i>
          </div>
          <div class="ready-card-content">
            <h6>Initialize Heap Dump</h6>
            <p>Build indexes and prepare for analysis</p>
          </div>
        </button>
      </div>
      <button class="ready-card action" @click="deleteHeapDump">
        <div class="ready-card-icon">
          <i class="bi bi-trash"></i>
        </div>
        <div class="ready-card-content">
          <h6>Delete Heap Dump</h6>
          <p>Remove heap dump file</p>
        </div>
      </button>
    </div>

    <!-- Processing Progress -->
    <div v-if="processing" class="processing-card">
      <div class="processing-icon">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>
      <h4>{{ processingMessage }}</h4>
      <div class="progress-container">
        <div class="progress">
          <div
              class="progress-bar"
              role="progressbar"
              :style="{ width: processingProgress + '%' }"
              :aria-valuenow="processingProgress"
              aria-valuemin="0"
              aria-valuemax="100">
          </div>
        </div>
        <span class="progress-label">{{ processingProgress }}%</span>
      </div>
      <p class="processing-hint">Please wait while we analyze the heap structure...</p>
    </div>

    <!-- Success State with Management Actions -->
    <div v-if="cacheReady && lastSummary" class="ready-section">
      <div class="ready-card success">
        <div class="ready-card-icon success">
          <i class="bi bi-check-lg"></i>
        </div>
        <div class="ready-card-content">
          <h6>Heap Dump Ready</h6>
          <p>Analysis complete. Use the sidebar to explore memory insights.</p>
        </div>
      </div>
      <button class="ready-card action" @click="clearCache">
        <div class="ready-card-icon">
          <i class="bi bi-arrow-repeat"></i>
        </div>
        <div class="ready-card-content">
          <h6>Clear Cache</h6>
          <p>Rebuild indexes on next analysis</p>
        </div>
      </button>
      <button class="ready-card action danger" @click="deleteHeapDump">
        <div class="ready-card-icon danger">
          <i class="bi bi-trash"></i>
        </div>
        <div class="ready-card-content">
          <h6>Delete Heap Dump</h6>
          <p>Remove heap dump and cached data</p>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapSummary from '@/services/api/model/HeapSummary';
import FormattingService from '@/services/FormattingService';
import { ToastService } from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const lastSummary = ref<HeapSummary | null>(null);

// Processing state
const processing = ref(false);
const processingProgress = ref(0);
const processingMessage = ref('');

// Upload state
const uploadFile = ref<File | null>(null);
const uploading = ref(false);
const uploadProgress = ref(0);
const dragActive = ref(false);

// Delete heap dump dialog state
const deleteHeapDumpDialog = ref(false);

// File input ref
const fileInputRef = ref<HTMLInputElement | null>(null);
const dropzoneRef = ref<HTMLDivElement | null>(null);

let client: HeapDumpClient;

// Summary metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!lastSummary.value) return [];
  return [
    {
      icon: 'collection',
      title: 'Total Objects',
      value: FormattingService.formatNumber(lastSummary.value.totalInstances),
      variant: 'highlight' as const
    },
    {
      icon: 'hdd',
      title: 'Heap Size',
      value: FormattingService.formatBytes(lastSummary.value.totalBytes),
      variant: 'info' as const
    },
    {
      icon: 'layers',
      title: 'Classes',
      value: FormattingService.formatNumber(lastSummary.value.classCount),
      variant: 'info' as const
    },
    {
      icon: 'diagram-3',
      title: 'GC Roots',
      value: FormattingService.formatNumber(lastSummary.value.gcRootCount),
      variant: 'success' as const
    }
  ];
});

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const processHeapDump = async () => {
  processing.value = true;
  processingProgress.value = 0;
  processingMessage.value = 'Loading heap dump...';

  try {
    processingProgress.value = 20;
    await sleep(300);

    processingMessage.value = 'Parsing heap structure...';
    processingProgress.value = 40;
    await sleep(300);

    processingMessage.value = 'Building indexes...';
    processingProgress.value = 60;

    const summary = await client.getSummary();
    lastSummary.value = summary;

    processingMessage.value = 'Running string analysis...';
    processingProgress.value = 70;

    // Run string analysis as part of initialization
    await client.runStringAnalysis(100);

    processingMessage.value = 'Running thread analysis...';
    processingProgress.value = 85;

    // Run thread analysis as part of initialization
    await client.runThreadAnalysis();

    processingMessage.value = 'Done';
    processingProgress.value = 100;
    await sleep(200);

    cacheReady.value = true;
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, true);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to process heap dump';
  } finally {
    processing.value = false;
    processingProgress.value = 0;
  }
};

const clearCache = async () => {
  try {
    await client.deleteCache();
    // Also delete analyses so they get regenerated on next initialization
    await client.deleteStringAnalysis();
    await client.deleteThreadAnalysis();
    cacheReady.value = false;
    lastSummary.value = null;
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, false);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to clear cache';
  }
};

const deleteHeapDump = () => {
  deleteHeapDumpDialog.value = true;
};

const confirmDeleteHeapDump = async () => {
  try {
    await client.deleteHeapDump();
    heapExists.value = false;
    cacheReady.value = false;
    lastSummary.value = null;
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, false);
    ToastService.success('Heap Dump Deleted', 'Heap dump and cache have been removed successfully.');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to delete heap dump';
  }
};

// Upload handlers
const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    const file = input.files[0];
    if (isValidHeapDumpFile(file.name)) {
      uploadFile.value = file;
      nextTick(() => focusDropzone());
    } else {
      ToastService.warn('Invalid File', 'Only .hprof and .hprof.gz files are supported');
    }
  }
};

const isValidHeapDumpFile = (filename: string): boolean => {
  const lower = filename.toLowerCase();
  return lower.endsWith('.hprof') || lower.endsWith('.hprof.gz');
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

  if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
    const file = event.dataTransfer.files[0];
    if (isValidHeapDumpFile(file.name)) {
      uploadFile.value = file;
      nextTick(() => focusDropzone());
    } else {
      ToastService.warn('Invalid File', 'Only .hprof and .hprof.gz files are supported');
    }
  }
};

const handleDropzoneClick = () => {
  if (!uploadFile.value && fileInputRef.value) {
    fileInputRef.value.click();
  }
};

const handleEnterKey = () => {
  if (uploadFile.value && !uploading.value) {
    uploadHeapDump();
  }
};

const focusDropzone = () => {
  dropzoneRef.value?.focus();
};

const removeFile = () => {
  uploadFile.value = null;
};

const uploadHeapDump = async () => {
  if (!uploadFile.value || !client) return;

  uploading.value = true;
  uploadProgress.value = 0;

  try {
    // Simulate progress
    const progressInterval = setInterval(() => {
      if (uploadProgress.value < 90) {
        uploadProgress.value += Math.floor(Math.random() * 10) + 5;
      }
    }, 300);

    await client.uploadHeapDump(uploadFile.value);

    clearInterval(progressInterval);
    uploadProgress.value = 100;

    ToastService.success('Upload Complete', 'Heap dump uploaded successfully');

    // Reload data to show the heap dump
    await loadData();

    // Reset upload state
    uploadFile.value = null;
  } catch (err) {
    ToastService.error('Upload Failed', err instanceof Error ? err.message : 'Failed to upload heap dump');
  } finally {
    uploading.value = false;
    uploadProgress.value = 0;
  }
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(workspaceId.value, projectId.value, profileId);

    // Check if heap dump exists
    heapExists.value = await client.exists();

    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    // Check if cache is ready
    cacheReady.value = await client.isCacheReady();

    if (cacheReady.value) {
      // Load summary automatically
      const summary = await client.getSummary();
      lastSummary.value = summary;
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load heap dump status';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  scrollToTop();
  loadData();
});
</script>

<style scoped>
/* Processing Card */
.processing-card {
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 3rem 2rem;
  text-align: center;
  max-width: 520px;
  margin: 0 auto;
}

.processing-icon {
  margin-bottom: 1.5rem;
}

.processing-icon .spinner-border {
  width: 3rem;
  height: 3rem;
  color: #6f42c1;
}

.processing-card h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 1.25rem;
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: 320px;
  margin: 0 auto 1rem;
}

.progress-container .progress {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background-color: #e9ecef;
}

.progress-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #6f42c1;
  min-width: 36px;
}

.processing-hint {
  font-size: 0.8125rem;
  color: #94a3b8;
  margin: 0;
}

/* Init Section */
.init-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.init-main-card {
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 2.5rem 2rem;
  text-align: center;
}

.init-header {
  margin-bottom: 1.5rem;
}

.init-header-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 1.25rem;
  background-color: rgba(111, 66, 193, 0.1);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.init-header-icon i {
  font-size: 1.5rem;
  color: #6f42c1;
}

.init-header h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 0.5rem;
}

.init-header p {
  color: #64748b;
  font-size: 0.875rem;
  line-height: 1.6;
  margin: 0 auto;
  max-width: 420px;
}

.init-panel-btn.ready-card.action.primary {
  max-width: 340px;
  margin: 0 auto;
  border: none !important;
  background: linear-gradient(135deg, #0d6efd 0%, #0a58ca 100%) !important;
  box-shadow: 0 4px 12px rgba(13, 110, 253, 0.3);
}

.init-panel-btn.ready-card.action.primary:hover {
  background: linear-gradient(135deg, #0b5ed7 0%, #084298 100%) !important;
  box-shadow: 0 6px 16px rgba(13, 110, 253, 0.4);
  transform: translateY(-1px);
}

.init-panel-btn .ready-card-icon.primary {
  background-color: rgba(255, 255, 255, 0.2) !important;
}

.init-panel-btn .ready-card-icon.primary i {
  color: white !important;
}

.init-panel-btn .ready-card-content h6 {
  color: white !important;
  font-size: 0.9375rem;
}

.init-panel-btn .ready-card-content p {
  color: rgba(255, 255, 255, 0.8) !important;
}

/* Ready Section - Success Card + Management */
.ready-section {
  display: flex;
  gap: 0.75rem;
}

.ready-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.875rem;
  padding: 1rem 1.25rem;
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  text-align: left;
}

.ready-card.action {
  cursor: pointer;
  transition: all 0.15s ease;
}

.ready-card.action:hover {
  border-color: #adb5bd;
  background-color: #f8f9fa;
}

.ready-card.action.danger:hover {
  border-color: #f1aeb5;
  background-color: #fff5f5;
}

.ready-card-icon {
  width: 40px;
  height: 40px;
  background-color: #f1f5f9;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ready-card-icon i {
  font-size: 1.125rem;
  color: #64748b;
}

.ready-card-icon.success {
  background-color: rgba(25, 135, 84, 0.1);
}

.ready-card-icon.success i {
  color: #198754;
}

.ready-card-icon.info {
  background-color: rgba(111, 66, 193, 0.1);
}

.ready-card-icon.info i {
  color: #6f42c1;
}

.ready-card-icon.primary {
  background-color: rgba(13, 110, 253, 0.1);
}

.ready-card-icon.primary i {
  color: #0d6efd;
}

.ready-card-icon.danger {
  background-color: #fef2f2;
}

.ready-card-icon.danger i {
  color: #dc3545;
}

.ready-card.action.primary:hover {
  border-color: #86b7fe;
  background-color: #f0f7ff;
}

/* Dominant button style */
.ready-card.action.dominant {
  flex: 2;
  border-color: #0d6efd;
  background-color: #f8fbff;
}

.ready-card.action.dominant:hover {
  background-color: #eef5ff;
}

.ready-card.action.dominant .ready-card-content h6 {
  color: #0d6efd;
}

.ready-card-icon.large {
  width: 48px;
  height: 48px;
}

.ready-card-icon.large i {
  font-size: 1.375rem;
}

.ready-card-content h6 {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 0.125rem 0;
}

.ready-card-content p {
  font-size: 0.75rem;
  color: #94a3b8;
  margin: 0;
}

.btn-outline-purple {
  border-color: #6f42c1;
  color: #6f42c1;
}

.btn-outline-purple:hover {
  background-color: #6f42c1;
  border-color: #6f42c1;
  color: white;
}

/* Upload dropzone styles */
.upload-dropzone {
  border: 2px dashed #ccc;
  border-radius: 8px;
  transition: all 0.3s ease;
  background-color: #fafafa;
}

.upload-dropzone.active {
  border-color: #5e64ff;
  background-color: #f0f2ff;
}

.upload-dropzone.has-file {
  border-style: solid;
  border-color: #dee2e6;
  background-color: white;
}

.upload-dropzone.clickable {
  cursor: pointer;
}

.upload-dropzone.clickable:hover {
  border-color: #0d6efd;
  background-color: #f8fbff;
}

.file-item {
  border: 1px solid #eee;
  border-radius: 4px;
  background-color: #f9f9f9;
}

.display-4 {
  font-size: 3.5rem;
  line-height: 1.2;
}

.progress {
  height: 8px;
  border-radius: 4px;
}

/* Responsive adjustments */
@media (max-width: 992px) {
  .ready-section {
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .init-panel-btn {
    max-width: 100%;
  }
}

@media (max-width: 576px) {
  .processing-card,
  .init-header-card {
    padding: 2rem 1.5rem;
  }
}
</style>

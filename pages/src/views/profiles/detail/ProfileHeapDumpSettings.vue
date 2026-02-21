<template>
  <div>
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

    <!-- Initialization Error Panel -->
    <transition name="slide-fade">
      <div v-if="initError" class="upload-error-panel">
        <div class="error-panel-content">
          <div class="error-icon-wrapper">
            <i class="bi bi-exclamation-triangle-fill"></i>
          </div>
          <div class="error-details">
            <h5 class="error-title">Heap Dump Initialization Failed</h5>
            <p class="error-message">{{ initError }}</p>
            <div class="error-suggestions">
              <h6><i class="bi bi-lightbulb me-2"></i>Possible Solutions</h6>
              <ul>
                <li>Verify the original heap dump file is valid using <code>jhat</code> or VisualVM</li>
                <li>Ensure the heap dump process completed successfully without interruption</li>
                <li>The file may have been truncated during upload - try uploading again</li>
                <li>For large files (>2GB), ensure sufficient server resources are available</li>
              </ul>
            </div>
          </div>
          <button class="error-dismiss-btn" @click="dismissInitError" title="Dismiss">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>
    </transition>
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

    <!-- Heap Dump Corruption Recovery Panel -->
    <transition name="slide-fade">
      <div v-if="needsSanitization && !processing" class="sanitize-panel">
        <div class="sanitize-panel-content">
          <div class="sanitize-icon-wrapper">
            <i class="bi bi-wrench-adjustable"></i>
          </div>
          <div class="sanitize-details">
            <h5 class="sanitize-title">Heap Dump Needs Repair</h5>
            <p class="sanitize-message">
              The heap dump file appears to be corrupted, likely due to an ungraceful JVM shutdown
              (OOMKill, SIGKILL, or crash). The file structure is incomplete but may be recoverable.
            </p>
            <div class="sanitize-info">
              <h6><i class="bi bi-info-circle me-2"></i>What will happen</h6>
              <ul>
                <li>The repair tool will scan the binary file and fix structural issues</li>
                <li>Missing end markers and truncated records will be corrected</li>
                <li>A repaired copy will be created (original is preserved)</li>
                <li>Some objects near the end of the file may be lost</li>
              </ul>
            </div>
            <div class="sanitize-actions">
              <button class="btn btn-warning-gradient" @click="sanitizeHeapDump" :disabled="sanitizing">
                <span v-if="sanitizing">
                  <span class="spinner-border spinner-border-sm me-2"></span>Repairing...
                </span>
                <span v-else>
                  <i class="bi bi-wrench me-2"></i>Repair &amp; Initialize
                </span>
              </button>
              <button class="btn btn-outline-secondary btn-sm ms-2" @click="deleteHeapDump">
                <i class="bi bi-trash me-1"></i>Delete Instead
              </button>
            </div>
          </div>
          <button class="sanitize-dismiss-btn" @click="dismissSanitize" title="Dismiss">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>
    </transition>

    <!-- Initialize Card - Not Processed State -->
    <div v-if="!cacheReady && !processing && !lastSummary && !needsSanitization" class="init-section">
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
        <div class="init-options">
          <div class="init-options-row">
            <div class="init-option-group">
              <label class="option-label mb-2">Pre-computation</label>
              <label class="form-check">
                <input type="checkbox" class="form-check-input" v-model="includeDominatorTree">
                <span class="form-check-label">
                  Dominator Tree
                  <small class="text-muted d-block">Computes retained sizes upfront. Can be slow for large heaps.</small>
                </span>
              </label>
            </div>
            <div class="init-option-delimiter"></div>
            <div class="init-option-group">
              <label class="option-label mb-2">Compressed Oops</label>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="compressedOops" id="coopsAuto" value="auto" v-model="compressedOopsChoice">
                <label class="form-check-label" for="coopsAuto">
                  Auto-detect <small class="text-muted">(recommended)</small>
                </label>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="compressedOops" id="coopsEnabled" value="enabled" v-model="compressedOopsChoice">
                <label class="form-check-label" for="coopsEnabled">Enabled</label>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="compressedOops" id="coopsDisabled" value="disabled" v-model="compressedOopsChoice">
                <label class="form-check-label" for="coopsDisabled">Disabled</label>
              </div>
            </div>
          </div>
        </div>
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
      <div class="processing-header">
        <h4>Initializing Heap Dump</h4>
        <p class="processing-hint">Please wait while we analyze the heap structure...</p>
      </div>

      <div class="steps-container">
        <div
          v-for="(step, index) in processingSteps"
          :key="step.id"
          class="step-item"
          :class="step.status"
        >
          <div class="step-top">
            <div class="step-indicator">
              <i v-if="step.status === 'completed'" class="bi bi-check-circle-fill text-success"></i>
              <i v-else-if="step.status === 'skipped'" class="bi bi-dash-circle text-secondary"></i>
              <div v-else-if="step.status === 'in_progress'" class="spinner-border spinner-border-sm text-primary" role="status">
                <span class="visually-hidden">Processing...</span>
              </div>
              <i v-else class="bi bi-circle text-muted"></i>
            </div>
            <div class="step-connector" v-if="index < processingSteps.length - 1"></div>
          </div>
          <span class="step-label">{{ step.label }}<span v-if="step.status === 'skipped'" class="skipped-badge">skipped</span></span>
        </div>
      </div>
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
import HeapDumpConfig from '@/services/api/model/HeapDumpConfig';
import FormattingService from '@/services/FormattingService';
import { ToastService } from '@/services/ToastService';
import MessageBus from '@/services/MessageBus';
import { ApiError } from '@/services/HttpInterceptor';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const lastSummary = ref<HeapSummary | null>(null);
const heapConfig = ref<HeapDumpConfig | null>(null);

// Processing state
const processing = ref(false);
const initError = ref<string | null>(null);

// Sanitization state
const needsSanitization = ref(false);
const sanitizing = ref(false);

// Compressed oops choice: 'auto' | 'enabled' | 'disabled'
const compressedOopsChoice = ref<string>('auto');

// Dominator tree pre-computation
const includeDominatorTree = ref(true);

// Processing steps
interface ProcessingStep {
  id: string;
  label: string;
  status: 'pending' | 'in_progress' | 'completed' | 'skipped';
}

// Dynamic steps based on checkbox
const getProcessingSteps = (): ProcessingStep[] => {
  return [
    { id: 'load', label: 'Loading heap dump', status: 'pending' },
    { id: 'parse', label: 'Parsing heap structure', status: 'pending' },
    { id: 'index', label: 'Building indexes', status: 'pending' },
    { id: 'strings', label: 'Analyzing strings', status: 'pending' },
    { id: 'threads', label: 'Analyzing threads', status: 'pending' },
    { id: 'dominator', label: 'Computing dominator tree', status: includeDominatorTree.value ? 'pending' : 'skipped' },
    { id: 'biggest', label: 'Finding biggest objects', status: 'pending' },
    { id: 'collections', label: 'Analyzing collections', status: 'pending' },
    { id: 'leaks', label: 'Detecting leak suspects', status: 'pending' }
  ];
};

// Mutable copy for status updates during processing
const processingSteps = ref<ProcessingStep[]>(getProcessingSteps());

const resetSteps = () => {
  // Rebuild steps based on current checkbox state
  processingSteps.value = getProcessingSteps();
};

const setStepStatus = (id: string, status: 'pending' | 'in_progress' | 'completed' | 'skipped') => {
  const step = processingSteps.value.find(s => s.id === id);
  if (step) step.status = status;
};

const completeStep = (id: string) => {
  setStepStatus(id, 'completed');
};

const startStep = (id: string) => {
  setStepStatus(id, 'in_progress');
};

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
      variant: 'info' as const,
      breakdown: [
        { label: 'GC Roots', value: FormattingService.formatNumber(lastSummary.value.gcRootCount) }
      ]
    },
    {
      icon: 'gear',
      title: 'Compressed Oops',
      value: heapConfig.value ? (heapConfig.value.compressedOops ? 'Enabled' : 'Disabled') : '—',
      variant: 'success' as const
    }
  ];
});

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const processHeapDump = async () => {
  processing.value = true;
  resetSteps();

  try {
    // Step 1: Load
    startStep('load');
    await sleep(300);
    completeStep('load');

    // Step 2: Parse
    startStep('parse');
    await sleep(300);
    completeStep('parse');

    // Step 3: Index (resolve compressed oops + build indexes)
    startStep('index');
    const compressedOopsOverride = compressedOopsChoice.value === 'enabled' ? true
        : compressedOopsChoice.value === 'disabled' ? false
        : undefined;
    const summary = await client.initialize(compressedOopsOverride);
    lastSummary.value = summary;
    heapConfig.value = await client.getConfig();
    completeStep('index');

    // Step 4: Strings
    startStep('strings');
    await client.runStringAnalysis(100);
    completeStep('strings');

    // Step 5: Threads
    startStep('threads');
    await client.runThreadAnalysis();
    completeStep('threads');

    // Step 6: Dominator Tree
    if (includeDominatorTree.value) {
      startStep('dominator');
      await client.getDominatorTreeRoots(50);
      completeStep('dominator');
    }

    // Step 7: Biggest Objects
    startStep('biggest');
    await client.runBiggestObjects(20);
    completeStep('biggest');

    // Step 8: Collections
    startStep('collections');
    await client.runCollectionAnalysis();
    completeStep('collections');

    // Step 9: Leak Suspects
    startStep('leaks');
    await client.runLeakSuspects();
    completeStep('leaks');

    cacheReady.value = true;
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, true);
  } catch (err) {
    // Check if this is a heap dump corruption error that can be repaired
    if (err instanceof ApiError && err.errorResponse?.code === 'HEAP_DUMP_NEEDS_SANITIZATION') {
      needsSanitization.value = true;
      // File still exists - don't set heapExists to false
    } else if (err instanceof ApiError && err.errorResponse?.code === 'HEAP_DUMP_CORRUPTED') {
      initError.value = err.errorResponse.message;
      heapExists.value = false; // File was deleted by backend
    } else {
      error.value = err instanceof Error ? err.message : 'Failed to process heap dump';
    }
  } finally {
    processing.value = false;
  }
};

const clearCache = async () => {
  try {
    await client.deleteCache();
    cacheReady.value = false;
    lastSummary.value = null;
    heapConfig.value = null;
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
    heapConfig.value = null;
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

    // Clear any previous initialization error
    initError.value = null;

    // Reload data to show the heap dump
    await loadData();

    // Reset upload state
    uploadFile.value = null;
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Failed to upload heap dump';
    ToastService.error('Upload Failed', errorMessage);
  } finally {
    uploading.value = false;
    uploadProgress.value = 0;
  }
};

const dismissInitError = () => {
  initError.value = null;
};

const sanitizeHeapDump = async () => {
  sanitizing.value = true;
  try {
    await client.sanitize();
    needsSanitization.value = false;
    // Now proceed with normal initialization
    await processHeapDump();
  } catch (err) {
    const msg = err instanceof Error ? err.message : 'Repair failed';
    initError.value = msg;
    needsSanitization.value = false;
  } finally {
    sanitizing.value = false;
  }
};

const dismissSanitize = () => {
  needsSanitization.value = false;
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(profileId);

    // Check if heap dump exists
    heapExists.value = await client.exists();

    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    // Check if cache is ready
    cacheReady.value = await client.isCacheReady();

    if (cacheReady.value) {
      // Load summary and config automatically
      const summary = await client.getSummary();
      lastSummary.value = summary;
      heapConfig.value = await client.getConfig();
    }
  } catch (err) {
    // Check if this is a heap dump corruption error that can be repaired
    if (err instanceof ApiError && err.errorResponse?.code === 'HEAP_DUMP_NEEDS_SANITIZATION') {
      needsSanitization.value = true;
      // File still exists - keep heapExists as true
    } else if (err instanceof ApiError && err.errorResponse?.code === 'HEAP_DUMP_CORRUPTED') {
      initError.value = err.errorResponse.message;
      heapExists.value = false; // File was deleted by backend
    } else {
      error.value = err instanceof Error ? err.message : 'Failed to load heap dump status';
    }
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
  padding: 2rem 2rem 2.5rem;
  max-width: 1300px;
  margin: 0 auto;
}

/* Step Progress Styles */
.processing-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.processing-header h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 0.5rem;
}

.processing-hint {
  font-size: 0.8125rem;
  color: #94a3b8;
  margin: 0;
}

.steps-container {
  display: flex;
  flex-direction: row;
  justify-content: center;
  gap: 0;
  margin: 0 auto;
  width: 100%;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  position: relative;
  max-width: 160px;
}

.step-top {
  display: flex;
  align-items: center;
  width: 100%;
  position: relative;
}

.step-indicator {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  background: white;
  margin: 0 auto;
}

.step-indicator i {
  font-size: 1.5rem;
}

.step-indicator .spinner-border-sm {
  width: 1.25rem;
  height: 1.25rem;
}

.step-connector {
  position: absolute;
  top: 50%;
  left: calc(50% + 16px);
  right: calc(-50% + 16px);
  height: 2px;
  background-color: #dee2e6;
  transform: translateY(-50%);
}

.step-item.completed .step-connector {
  background-color: #198754;
}

.step-label {
  font-size: 0.8125rem;
  color: #64748b;
  text-align: center;
  margin-top: 0.5rem;
  line-height: 1.3;
  word-wrap: break-word;
  max-width: 120px;
}

.step-item.completed .step-label {
  color: #1a1a2e;
}

.step-item.in_progress .step-label {
  color: #0d6efd;
  font-weight: 500;
}

.step-item.pending .step-label {
  color: #94a3b8;
}

.step-item.skipped .step-label {
  color: #94a3b8;
}

.step-item.skipped .step-connector {
  background-color: #dee2e6;
}

.skipped-badge {
  display: inline-block;
  font-size: 0.625rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
  background-color: #e9ecef;
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  margin-left: 0.375rem;
  vertical-align: middle;
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

/* Upload Error Panel */
.upload-error-panel {
  margin-top: 1rem;
  background: linear-gradient(135deg, #fff5f5 0%, #fef2f2 100%);
  border: 1px solid #fecaca;
  border-left: 4px solid #ef4444;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.15);
}

.error-panel-content {
  display: flex;
  padding: 1.25rem;
  gap: 1rem;
  position: relative;
}

.error-icon-wrapper {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 8px rgba(239, 68, 68, 0.3);
}

.error-icon-wrapper i {
  font-size: 1.5rem;
  color: white;
}

.error-details {
  flex: 1;
  min-width: 0;
}

.error-title {
  font-size: 1rem;
  font-weight: 700;
  color: #991b1b;
  margin: 0 0 0.5rem 0;
}

.error-message {
  font-size: 0.875rem;
  color: #b91c1c;
  margin: 0 0 1rem 0;
  line-height: 1.5;
}

.error-suggestions {
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  padding: 0.875rem 1rem;
}

.error-suggestions h6 {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #92400e;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
}

.error-suggestions h6 i {
  color: #f59e0b;
}

.error-suggestions ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8125rem;
  color: #78350f;
}

.error-suggestions li {
  margin-bottom: 0.25rem;
  line-height: 1.5;
}

.error-suggestions li:last-child {
  margin-bottom: 0;
}

.error-suggestions code {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.75rem;
}

.error-dismiss-btn {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 6px;
  color: #dc2626;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.error-dismiss-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #991b1b;
}

.error-dismiss-btn i {
  font-size: 0.875rem;
}

/* Slide fade transition */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.2s ease-in;
}

.slide-fade-enter-from {
  transform: translateY(-10px);
  opacity: 0;
}

.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

/* Init Options */
.init-options {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e9ecef;
  text-align: left;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.init-options-row {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
}

.init-option-group {
  flex: 1;
  min-width: 0;
}

.init-option-delimiter {
  width: 1px;
  align-self: stretch;
  background-color: #e9ecef;
}

.init-options .option-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #495057;
  display: block;
}

.init-options .form-check {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  cursor: pointer;
  padding-left: 1.5rem;
}

.init-options .form-check-input {
  margin-top: 0.25rem;
}

.init-options .form-check-label {
  font-size: 0.8125rem;
  color: #495057;
}

.init-options .form-check-label small {
  font-size: 0.75rem;
  margin-top: 0.25rem;
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

/* Sanitize Recovery Panel */
.sanitize-panel {
  margin-bottom: 1rem;
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border: 1px solid #fcd34d;
  border-left: 4px solid #f59e0b;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.15);
}

.sanitize-panel-content {
  display: flex;
  padding: 1.25rem;
  gap: 1rem;
  position: relative;
}

.sanitize-icon-wrapper {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 8px rgba(245, 158, 11, 0.3);
}

.sanitize-icon-wrapper i {
  font-size: 1.5rem;
  color: white;
}

.sanitize-details {
  flex: 1;
  min-width: 0;
}

.sanitize-title {
  font-size: 1rem;
  font-weight: 700;
  color: #92400e;
  margin: 0 0 0.5rem 0;
}

.sanitize-message {
  font-size: 0.875rem;
  color: #a16207;
  margin: 0 0 1rem 0;
  line-height: 1.5;
}

.sanitize-info {
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  padding: 0.875rem 1rem;
  margin-bottom: 1rem;
}

.sanitize-info h6 {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #92400e;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
}

.sanitize-info h6 i {
  color: #f59e0b;
}

.sanitize-info ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8125rem;
  color: #78350f;
}

.sanitize-info li {
  margin-bottom: 0.25rem;
  line-height: 1.5;
}

.sanitize-info li:last-child {
  margin-bottom: 0;
}

.sanitize-actions {
  display: flex;
  align-items: center;
}

.btn-warning-gradient {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 6px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
  transition: all 0.15s ease;
}

.btn-warning-gradient:hover:not(:disabled) {
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.4);
  transform: translateY(-1px);
  color: white;
}

.btn-warning-gradient:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.sanitize-dismiss-btn {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(245, 158, 11, 0.1);
  border-radius: 6px;
  color: #d97706;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.sanitize-dismiss-btn:hover {
  background: rgba(245, 158, 11, 0.2);
  color: #92400e;
}

.sanitize-dismiss-btn i {
  font-size: 0.875rem;
}
</style>

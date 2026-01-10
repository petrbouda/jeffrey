<template>
  <LoadingState v-if="loading" message="Loading heap dump status..." />

  <div v-else-if="!heapExists">
    <PageHeader
        title="Heap Dump Overview"
        description="Memory analysis summary and processing status"
        icon="bi-memory">
    </PageHeader>

    <!-- Big Centered Dropzone -->
    <div class="upload-container">
    <div
        class="upload-dropzone-big"
        :class="{ 'active': dragActive, 'has-file': uploadFile }"
        @dragover="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
    >
      <!-- No file selected -->
      <div v-if="!uploadFile" class="dropzone-inner-big">
        <div class="dropzone-icon-big">
          <i class="bi bi-cloud-arrow-up-fill"></i>
        </div>
        <h4 class="dropzone-title">Drag & Drop Your Heap Dump</h4>
        <p class="dropzone-subtitle">or click to browse files</p>
        <input
            type="file"
            id="heapDumpFileInput"
            class="d-none"
            accept=".hprof,.hprof.gz"
            @change="handleFileSelect"
        >
        <label for="heapDumpFileInput" class="btn-browse-big">
          <i class="bi bi-folder2-open me-2"></i>Browse Files
        </label>
        <p class="dropzone-formats">Supports .hprof and .hprof.gz files</p>
      </div>

      <!-- File selected -->
      <div v-else class="selected-file-big">
        <div class="file-card-big">
          <div class="file-icon-big">
            <i class="bi bi-file-earmark-binary"></i>
          </div>
          <div class="file-details-big">
            <div class="file-name-big">{{ uploadFile.name }}</div>
            <div class="file-size-big">{{ FormattingService.formatBytes(uploadFile.size) }}</div>
          </div>
          <button
              class="btn-remove-big"
              @click="removeFile"
              :disabled="uploading"
          >
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <!-- Upload progress -->
        <div v-if="uploading" class="upload-progress-big">
          <div class="progress-track-big">
            <div class="progress-fill-big" :style="{ width: uploadProgress + '%' }"></div>
          </div>
          <span class="progress-text-big">Uploading... {{ uploadProgress }}%</span>
        </div>

        <!-- Upload button -->
        <button
            v-else
            class="btn-upload-big"
            @click="uploadHeapDump"
        >
          <i class="bi bi-cloud-upload me-2"></i>Upload Heap Dump
        </button>
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
      <template v-if="cacheReady && !processing" #actions>
        <button
            class="btn btn-sm btn-outline-purple me-2"
            @click="clearCache"
        >
          <i class="bi bi-arrow-repeat me-1"></i>
          Clear Cache
        </button>
        <button
            class="btn btn-sm btn-outline-danger"
            @click="deleteHeapDump"
        >
          <i class="bi bi-trash me-1"></i>
          Delete Heap Dump
        </button>
      </template>
    </PageHeader>

    <!-- Summary Stats -->
    <StatsTable v-if="lastSummary" :metrics="summaryMetrics" class="mb-4" />

    <!-- Initialize Hero Card - Not Processed State -->
    <div v-if="!cacheReady && !processing && !lastSummary" class="initialize-hero">
      <div class="hero-content">
        <div class="hero-icon">
          <i class="bi bi-cpu"></i>
        </div>
        <h3 class="hero-title">Initialize Heap Analysis</h3>
        <p class="hero-description">
          Process the heap dump to build indexes and enable memory analysis features.
          This may take a few minutes for large heap dumps.
        </p>
        <div class="hero-actions">
          <button class="btn-initialize" @click="processHeapDump">
            <i class="bi bi-lightning-charge-fill me-2"></i>
            Initialize Heap Dump
          </button>
          <div class="secondary-actions">
            <button class="btn-secondary-action" @click="clearCache">
              <i class="bi bi-arrow-repeat me-1"></i>
              Clear Cache
            </button>
            <button class="btn-secondary-action danger" @click="deleteHeapDump">
              <i class="bi bi-trash me-1"></i>
              Delete Heap Dump
            </button>
          </div>
        </div>
      </div>

      <!-- Feature Preview Cards -->
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">
            <i class="bi bi-bar-chart-fill"></i>
          </div>
          <div class="feature-text">
            <h6>Class Histogram</h6>
            <p>Analyze memory distribution across classes</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon">
            <i class="bi bi-code-square"></i>
          </div>
          <div class="feature-text">
            <h6>OQL Queries</h6>
            <p>Query objects with Object Query Language</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon">
            <i class="bi bi-diagram-3-fill"></i>
          </div>
          <div class="feature-text">
            <h6>GC Roots</h6>
            <p>Explore garbage collection root references</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon">
            <i class="bi bi-layers-fill"></i>
          </div>
          <div class="feature-text">
            <h6>Thread Analysis</h6>
            <p>Inspect thread states and stack traces</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Processing Progress -->
    <div v-if="processing" class="processing-hero">
      <div class="processing-visual">
        <div class="processing-rings">
          <div class="ring ring-1"></div>
          <div class="ring ring-2"></div>
          <div class="ring ring-3"></div>
          <div class="processing-icon">
            <i class="bi bi-gear-fill"></i>
          </div>
        </div>
      </div>
      <h4 class="processing-title">{{ processingMessage }}</h4>
      <div class="processing-progress-container">
        <div class="processing-progress-bar">
          <div class="processing-progress-fill" :style="{ width: processingProgress + '%' }"></div>
        </div>
        <span class="processing-percentage">{{ processingProgress }}%</span>
      </div>
      <p class="processing-hint">Please wait while we analyze the heap structure...</p>
    </div>

    <!-- Success State -->
    <div v-if="cacheReady && lastSummary" class="success-card">
      <div class="success-header">
        <div class="success-icon">
          <i class="bi bi-check-lg"></i>
        </div>
        <div class="success-text">
          <h5>Heap Dump Ready</h5>
          <p>Analysis complete. Navigate to explore memory insights.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapSummary from '@/services/api/model/HeapSummary';
import FormattingService from '@/services/FormattingService';
import { ToastService } from '@/services/ToastService';

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
    processingProgress.value = 25;
    await sleep(300);

    processingMessage.value = 'Parsing heap structure...';
    processingProgress.value = 50;
    await sleep(300);

    processingMessage.value = 'Building indexes...';
    processingProgress.value = 75;

    const summary = await client.getSummary();
    lastSummary.value = summary;

    processingMessage.value = 'Done';
    processingProgress.value = 100;
    await sleep(200);

    cacheReady.value = true;
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
    cacheReady.value = false;
    lastSummary.value = null;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to clear cache';
  }
};

const deleteHeapDump = async () => {
  if (!confirm('Are you sure you want to delete the heap dump? This will remove all heap dump files including the cache.')) {
    return;
  }

  try {
    await client.deleteHeapDump();
    heapExists.value = false;
    cacheReady.value = false;
    lastSummary.value = null;
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
    } else {
      ToastService.warn('Invalid File', 'Only .hprof and .hprof.gz files are supported');
    }
  }
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
  loadData();
});
</script>

<style scoped>
/* Initialize Hero Card */
.initialize-hero {
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.hero-content {
  padding: 3rem 2rem;
  text-align: center;
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f4ff 100%);
  border-bottom: 1px solid #e8ecf4;
}

.hero-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 1.5rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.35);
}

.hero-icon i {
  font-size: 2.25rem;
  color: white;
}

.hero-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 0.75rem;
}

.hero-description {
  color: #64748b;
  max-width: 440px;
  margin: 0 auto 1.75rem;
  line-height: 1.6;
}

.btn-initialize {
  display: inline-flex;
  align-items: center;
  padding: 0.875rem 2rem;
  font-size: 1rem;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.btn-initialize:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.5);
}

.btn-initialize:active {
  transform: translateY(0);
}

/* Hero Actions */
.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.25rem;
}

.secondary-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-secondary-action {
  display: inline-flex;
  align-items: center;
  padding: 0.5rem 1rem;
  font-size: 0.8125rem;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary-action:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #475569;
}

.btn-secondary-action.danger:hover {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

/* Feature Preview Cards */
.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background: #e8ecf4;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: white;
  transition: background 0.2s ease;
}

.feature-card:hover {
  background: #fafbff;
}

.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #f0f4ff;
}

.feature-icon i {
  font-size: 1.25rem;
  color: #667eea;
}

.feature-text h6 {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 0.25rem;
}

.feature-text p {
  font-size: 0.75rem;
  color: #94a3b8;
  margin: 0;
}

/* Processing Hero */
.processing-hero {
  background: white;
  border-radius: 16px;
  padding: 3rem 2rem;
  text-align: center;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.processing-visual {
  margin-bottom: 2rem;
}

.processing-rings {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto;
}

.ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid transparent;
  animation: pulse 2s ease-in-out infinite;
}

.ring-1 {
  inset: 0;
  border-color: rgba(102, 126, 234, 0.15);
  animation-delay: 0s;
}

.ring-2 {
  inset: 15px;
  border-color: rgba(102, 126, 234, 0.25);
  animation-delay: 0.3s;
}

.ring-3 {
  inset: 30px;
  border-color: rgba(102, 126, 234, 0.4);
  animation-delay: 0.6s;
}

.processing-icon {
  position: absolute;
  inset: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.processing-icon i {
  font-size: 1.5rem;
  color: white;
  animation: spin 2s linear infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.7; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.processing-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 1.5rem;
}

.processing-progress-container {
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: 400px;
  margin: 0 auto 1rem;
}

.processing-progress-bar {
  flex: 1;
  height: 8px;
  background: #e8ecf4;
  border-radius: 4px;
  overflow: hidden;
}

.processing-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.processing-percentage {
  font-size: 0.875rem;
  font-weight: 700;
  color: #667eea;
  min-width: 40px;
}

.processing-hint {
  font-size: 0.875rem;
  color: #94a3b8;
  margin: 0;
}

/* Success Card */
.success-card {
  background: white;
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border-left: 4px solid #34A853;
}

.success-header {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.success-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #34A853, #2d9348);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.success-icon i {
  font-size: 1.25rem;
  color: white;
}

.success-text h5 {
  font-size: 1rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 0.25rem;
}

.success-text p {
  font-size: 0.875rem;
  color: #64748b;
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

/* Upload Container */
.upload-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: 2rem;
}

/* Big Centered Dropzone */
.upload-dropzone-big {
  width: 100%;
  max-width: 540px;
  border: 2px dashed #cbd5e1;
  border-radius: 20px;
  padding: 3rem 2rem;
  transition: all 0.25s ease;
  background: linear-gradient(135deg, #fafbff 0%, #f5f7ff 100%);
  cursor: pointer;
}

.upload-dropzone-big.active {
  border-color: #667eea;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8ecff 100%);
  transform: scale(1.01);
}

.upload-dropzone-big.has-file {
  border-style: solid;
  border-color: #e2e8f0;
  background: white;
  cursor: default;
}

.dropzone-inner-big {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.dropzone-icon-big {
  width: 88px;
  height: 88px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.5rem;
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.35);
}

.dropzone-icon-big i {
  font-size: 2.5rem;
  color: white;
}

.dropzone-title {
  font-size: 1.375rem;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.dropzone-subtitle {
  font-size: 0.9375rem;
  color: #64748b;
  margin-bottom: 1.5rem;
}

.btn-browse-big {
  display: inline-flex;
  align-items: center;
  padding: 0.875rem 1.75rem;
  font-size: 1rem;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.btn-browse-big:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.5);
}

.dropzone-formats {
  font-size: 0.8125rem;
  color: #94a3b8;
  margin-top: 1.25rem;
  margin-bottom: 0;
}

/* Selected File Big */
.selected-file-big {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
}

.file-card-big {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: #f8fafc;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  width: 100%;
  max-width: 400px;
}

.file-icon-big {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon-big i {
  font-size: 1.5rem;
  color: white;
}

.file-details-big {
  flex: 1;
  min-width: 0;
}

.file-name-big {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size-big {
  font-size: 0.875rem;
  color: #64748b;
  margin-top: 0.25rem;
}

.btn-remove-big {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-remove-big:hover {
  background: #fef2f2;
  border-color: #fecaca;
  color: #ef4444;
}

.btn-remove-big:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Upload Progress Big */
.upload-progress-big {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  max-width: 400px;
}

.progress-track-big {
  height: 10px;
  background: #e2e8f0;
  border-radius: 5px;
  overflow: hidden;
}

.progress-fill-big {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 5px;
  transition: width 0.3s ease;
}

.progress-text-big {
  font-size: 0.875rem;
  color: #64748b;
  text-align: center;
}

/* Upload Button Big */
.btn-upload-big {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1rem 2rem;
  font-size: 1rem;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(34, 197, 94, 0.35);
}

.btn-upload-big:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(34, 197, 94, 0.45);
}

.btn-upload-big:active {
  transform: translateY(0);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .features-grid {
    grid-template-columns: 1fr;
  }

  .hero-content {
    padding: 2rem 1.5rem;
  }

  .btn-initialize {
    width: 100%;
    justify-content: center;
  }

  .secondary-actions {
    flex-direction: column;
    width: 100%;
  }

  .btn-secondary-action {
    justify-content: center;
  }

  .upload-container {
    padding: 1rem;
    min-height: 300px;
  }

  .upload-dropzone-big {
    padding: 2rem 1.5rem;
  }

  .dropzone-icon-big {
    width: 72px;
    height: 72px;
  }

  .dropzone-icon-big i {
    font-size: 2rem;
  }

  .dropzone-title {
    font-size: 1.125rem;
  }

  .btn-browse-big,
  .btn-upload-big {
    width: 100%;
    justify-content: center;
  }

  .file-card-big {
    max-width: 100%;
  }

  .upload-progress-big {
    max-width: 100%;
  }
}
</style>

<template>
  <LoadingState v-if="loading" message="Loading heap dump status..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Heap Dump Overview"
        description="Memory analysis summary and processing status"
        icon="bi-memory">
      <template #actions>
        <button
            v-if="!cacheReady && !processing"
            class="btn btn-sm btn-primary"
            @click="processHeapDump"
        >
          <i class="bi bi-play-fill me-1"></i>
          Process Heap Dump
        </button>
        <button
            v-if="processing"
            class="btn btn-sm btn-secondary"
            disabled
        >
          <span class="spinner-border spinner-border-sm me-1"></span>
          Processing...
        </button>
        <button
            v-if="cacheReady && !processing"
            class="btn btn-sm btn-outline-purple me-2"
            @click="clearCache"
        >
          <i class="bi bi-arrow-repeat me-1"></i>
          Clear Cache
        </button>
        <button
            v-if="!processing"
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

    <!-- Processing Progress -->
    <div v-if="processing" class="processing-card mb-4">
      <div class="d-flex align-items-center justify-content-between mb-3">
        <div class="d-flex align-items-center">
          <div class="spinner-border spinner-border-sm text-primary me-2"></div>
          <span class="fw-medium">{{ processingMessage }}</span>
        </div>
        <span class="text-primary fw-bold">{{ processingProgress }}%</span>
      </div>
      <div class="progress" style="height: 6px;">
        <div
            class="progress-bar"
            :style="{ width: processingProgress + '%' }"
        ></div>
      </div>
    </div>

    <!-- Not Processed Info -->
    <div v-if="!cacheReady && !processing && !lastSummary" class="info-card">
      <div class="d-flex align-items-start">
        <i class="bi bi-info-circle text-primary me-3 fs-4"></i>
        <div>
          <h6 class="mb-2">Heap Dump Not Processed</h6>
          <p class="text-muted mb-0 small">
            Click "Process Heap Dump" to parse the heap dump file and create the analysis index.
            This may take a few minutes for large heap dumps. Once processed, the data will be cached for fast access.
          </p>
        </div>
      </div>
    </div>

    <!-- Success State -->
    <div v-if="cacheReady && lastSummary" class="info-card success">
      <div class="d-flex align-items-start">
        <i class="bi bi-check-circle text-success me-3 fs-4"></i>
        <div>
          <h6 class="mb-2">Heap Dump Ready</h6>
          <p class="text-muted mb-0 small">
            The heap dump has been processed and cached. Navigate to the analysis pages to explore class histogram, OQL queries, threads, and GC roots.
          </p>
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
.no-heap-dump {
  padding: 2rem;
}

.processing-card {
  background: white;
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.progress {
  background-color: #e9ecef;
  border-radius: 3px;
}

.progress-bar {
  background: linear-gradient(90deg, #4285F4, #34A853);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.info-card {
  background: white;
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  border-left: 4px solid #4285F4;
}

.info-card.success {
  border-left-color: #34A853;
}

.info-card h6 {
  color: #2c3e50;
  font-weight: 600;
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
</style>

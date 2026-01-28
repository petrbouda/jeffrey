<template>
  <LoadingState v-if="loading" message="Loading biggest objects..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="trophy"
      message="The heap dump needs to be initialized before you can view biggest objects. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
        title="Biggest Objects"
        description="Individual objects consuming the most memory"
        icon="bi-trophy"
    />

    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">Biggest Objects Analysis Not Available</h6>
        <p class="mb-2 small">The biggest objects analysis was not found. This can happen if the option was disabled during initialization or the heap dump was initialized before this feature was added.</p>
        <p class="mb-2 small text-muted"><i class="bi bi-clock me-1"></i>Note: This analysis can take several minutes for large heap dumps.</p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run Biggest Objects Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
        title="Biggest Objects"
        description="Individual objects consuming the most memory"
        icon="bi-trophy"
    />

    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status">
        <span class="visually-hidden">Running...</span>
      </div>
      <div>
        <h6 class="mb-1">Analyzing Biggest Objects...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

  <!-- Analysis Results -->
  <div v-else>
    <PageHeader
        title="Biggest Objects"
        description="Individual objects consuming the most memory"
        icon="bi-trophy"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Data Table -->
    <div class="table-card">
      <div class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
          <tr>
            <th style="width: 50px;">#</th>
            <th>Class Name</th>
            <th class="text-end" style="width: 120px;">Shallow Size</th>
            <th class="text-end" style="width: 120px;">Retained Size</th>
            <th style="width: 200px;">% of Heap</th>
            <th style="width: 100px;">Actions</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(entry, index) in report!.entries" :key="entry.objectId">
            <td class="text-muted">{{ index + 1 }}</td>
            <td>
              <div class="class-info">
                <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                <span class="package-name">{{ packageName(entry.className) }}</span>
              </div>
              <span class="display-value text-muted">{{ truncateValue(entry.displayValue) }}</span>
            </td>
            <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.shallowSize) }}</td>
            <td class="text-end font-monospace text-warning">{{ FormattingService.formatBytes(entry.retainedSize) }}</td>
            <td>
              <div class="d-flex align-items-center gap-2">
                <div class="progress flex-grow-1" style="height: 6px;">
                  <div
                      class="progress-bar"
                      :style="{ width: getHeapPercentage(entry) + '%', backgroundColor: '#4285F4' }"
                  ></div>
                </div>
                <small class="text-muted" style="min-width: 45px;">{{ getHeapPercentage(entry).toFixed(1) }}%</small>
              </div>
            </td>
            <td>
              <InstanceActionButtons
                  :object-id="entry.objectId"
                  @show-referrers="openTreeModal($event, 'REFERRERS')"
                  @show-reachables="openTreeModal($event, 'REACHABLES')"
                  @show-g-c-root-path="openGCRootPathModal"
              />
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Instance Tree Modal -->
    <InstanceTreeModal
        v-if="treeModalObjectId !== null"
        :show="showTreeModal"
        :object-id="treeModalObjectId"
        :initial-mode="treeModalMode"
        :profile-id="profileId"
        @update:show="showTreeModal = $event"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import BiggestObjectsReport, { BiggestObjectEntry } from '@/services/api/model/BiggestObjectsReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const analysisExists = ref(false);
const analysisRunning = ref(false);
const report = ref<BiggestObjectsReport | null>(null);

// Tree modal state
const showTreeModal = ref(false);
const treeModalObjectId = ref<number | null>(null);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'hdd',
      title: 'Total Retained',
      value: FormattingService.formatBytes(report.value.totalRetainedSize),
      variant: 'highlight' as const
    },
    {
      icon: 'pie-chart',
      title: 'Total Heap',
      value: FormattingService.formatBytes(report.value.totalHeapSize),
      variant: 'info' as const
    }
  ];
});

const getHeapPercentage = (entry: BiggestObjectEntry): number => {
  if (!report.value || report.value.totalHeapSize === 0) return 0;
  return (entry.retainedSize / report.value.totalHeapSize) * 100;
};

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
};

const truncateValue = (value: string): string => {
  if (!value) return '';
  return value.length > 80 ? value.substring(0, 80) + '...' : value;
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runBiggestObjects(50);
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run biggest objects analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.biggestObjectsExists();
  if (analysisExists.value) {
    report.value = await client.getBiggestObjects();
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
    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(profileId);

    heapExists.value = await client.exists();
    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    cacheReady.value = await client.isCacheReady();
    if (!cacheReady.value) {
      loading.value = false;
      return;
    }

    await loadAnalysis();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load biggest objects analysis';
    console.error('Error loading biggest objects:', err);
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
.no-heap-dump {
  padding: 2rem;
}

.class-info {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  margin-bottom: 2px;
}

.class-name {
  font-size: 0.8rem;
  font-weight: 600;
  background-color: transparent;
  color: #495057;
  white-space: nowrap;
}

.package-name {
  font-size: 0.8rem;
  color: #adb5bd;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.display-value {
  font-size: 0.75rem;
  word-break: break-all;
  max-width: 400px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.table thead th {
  background-color: #fafbfc;
  font-weight: 600;
  color: #495057;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid #e9ecef;
}

.table td {
  font-size: 0.8rem;
  padding: 0.6rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.8rem;
}

/* Darker warning color for better readability */
.text-warning {
  color: #b8860b !important;
}
</style>

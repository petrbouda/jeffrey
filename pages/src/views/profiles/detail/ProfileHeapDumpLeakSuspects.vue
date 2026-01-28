<template>
  <LoadingState v-if="loading" message="Loading leak suspects..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="search"
      message="The heap dump needs to be initialized before you can view leak suspects."
  />

  <ErrorState v-else-if="error" :message="error" />

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
        title="Leak Suspects"
        description="Automated analysis to identify potential memory leaks"
        icon="bi-bug"
    />
    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">Leak Suspects Analysis Not Available</h6>
        <p class="mb-2 small">Run the analysis to identify objects and classes that may indicate memory leaks based on heuristic detection.</p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run Leak Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
        title="Leak Suspects"
        description="Automated analysis to identify potential memory leaks"
        icon="bi-bug"
    />
    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status"></div>
      <div>
        <h6 class="mb-1">Analyzing Heap for Leak Suspects...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

  <!-- Results -->
  <div v-else-if="report">
    <PageHeader
        title="Leak Suspects"
        description="Automated analysis to identify potential memory leaks"
        icon="bi-bug"
    />

    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- No suspects found -->
    <div v-if="report.suspects.length === 0" class="text-center text-muted py-5">
      <i class="bi bi-shield-check fs-1 mb-3 d-block text-success"></i>
      <h6>No Leak Suspects Detected</h6>
      <p class="small">The heuristic analysis did not find any objects or classes that indicate potential memory leaks.</p>
    </div>

    <!-- Suspect cards -->
    <div v-else class="suspects-list">
      <div v-for="suspect in report.suspects" :key="suspect.rank" class="suspect-card">
        <div class="suspect-header">
          <div class="suspect-rank" :class="getSeverityClass(suspect)">
            #{{ suspect.rank }}
          </div>
          <div class="suspect-title">
            <h6 class="mb-0">{{ simpleClassName(suspect.className) }}</h6>
            <span class="text-muted small">{{ suspect.className }}</span>
          </div>
          <div class="suspect-percent">
            <span class="percent-value">{{ suspect.heapPercentage.toFixed(1) }}%</span>
            <span class="percent-label">of heap</span>
          </div>
        </div>

        <div class="suspect-body">
          <div class="suspect-stats">
            <div class="stat-item">
              <span class="stat-label">Retained Size</span>
              <span class="stat-value font-monospace">{{ FormattingService.formatBytes(suspect.retainedSize) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">Instances</span>
              <span class="stat-value font-monospace">{{ FormattingService.formatNumber(suspect.instanceCount) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">Accumulation</span>
              <span class="stat-value">{{ suspect.accumulationPoint }}</span>
            </div>
          </div>

          <div class="suspect-reason">
            <i class="bi bi-lightbulb me-2 text-warning"></i>
            {{ suspect.reason }}
          </div>

          <!-- Heap percentage bar -->
          <div class="suspect-bar">
            <div class="progress" style="height: 8px;">
              <div
                  class="progress-bar"
                  :class="getBarClass(suspect)"
                  :style="{ width: Math.min(suspect.heapPercentage, 100) + '%' }"
              ></div>
            </div>
          </div>

          <!-- Actions -->
          <div class="suspect-actions" v-if="suspect.objectId">
            <InstanceActionButtons
                :object-id="suspect.objectId"
                @show-referrers="openTreeModal($event, 'REFERRERS')"
                @show-reachables="openTreeModal($event, 'REACHABLES')"
                @show-g-c-root-path="openGCRootPath"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Modals -->
    <InstanceTreeModal
        v-if="treeModalObjectId !== null"
        :show="showTreeModal"
        :object-id="treeModalObjectId"
        :initial-mode="treeModalMode"
        :profile-id="profileId"
        @close="showTreeModal = false"
    />

    <GCRootPathModal
        :show="showGCRootModal"
        :object-id="gcRootObjectId"
        :profile-id="profileId"
        @close="showGCRootModal = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import GCRootPathModal from '@/components/heap/GCRootPathModal.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import LeakSuspectsReport, { LeakSuspect } from '@/services/api/model/LeakSuspectsReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const analysisExists = ref(false);
const analysisRunning = ref(false);
const report = ref<LeakSuspectsReport | null>(null);

// Modal state
const showTreeModal = ref(false);
const treeModalObjectId = ref<number | null>(null);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');
const showGCRootModal = ref(false);
const gcRootObjectId = ref<number>(0);

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'bug',
      title: 'Suspects Found',
      value: report.value.suspects.length,
      variant: report.value.suspects.length > 0 ? 'danger' as const : 'success' as const
    },
    {
      icon: 'hdd',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(report.value.totalHeapSize),
      variant: 'highlight' as const
    }
  ];
});

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const getSeverityClass = (suspect: LeakSuspect): string => {
  if (suspect.heapPercentage >= 30) return 'severity-critical';
  if (suspect.heapPercentage >= 15) return 'severity-warning';
  return 'severity-info';
};

const getBarClass = (suspect: LeakSuspect): string => {
  if (suspect.heapPercentage >= 30) return 'bg-danger';
  if (suspect.heapPercentage >= 15) return 'bg-warning';
  return 'bg-info';
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPath = (objectId: number) => {
  gcRootObjectId.value = objectId;
  showGCRootModal.value = true;
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runLeakSuspects();
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run leak suspects analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.leakSuspectsExists();
  if (analysisExists.value) {
    report.value = await client.getLeakSuspects();
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    client = new HeapDumpClient(profileId);

    heapExists.value = await client.exists();
    if (!heapExists.value) { loading.value = false; return; }

    cacheReady.value = await client.isCacheReady();
    if (!cacheReady.value) { loading.value = false; return; }

    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load leak suspects';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) workspaceContent.scrollTop = 0;
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.suspects-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.suspect-card {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  overflow: hidden;
  background: white;
}

.suspect-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  background: #fafbfc;
  border-bottom: 1px solid #e9ecef;
}

.suspect-rank {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
  flex-shrink: 0;
}

.severity-critical {
  background: #fee2e2;
  color: #dc2626;
}

.severity-warning {
  background: #fef3c7;
  color: #d97706;
}

.severity-info {
  background: #dbeafe;
  color: #2563eb;
}

.suspect-title {
  flex: 1;
  min-width: 0;
}

.suspect-title h6 {
  font-size: 0.95rem;
  font-weight: 600;
}

.suspect-title span {
  font-size: 0.75rem;
  word-break: break-all;
}

.suspect-percent {
  text-align: right;
  flex-shrink: 0;
}

.percent-value {
  display: block;
  font-size: 1.25rem;
  font-weight: 700;
  color: #343a40;
  font-family: monospace;
}

.percent-label {
  font-size: 0.7rem;
  color: #6c757d;
  text-transform: uppercase;
}

.suspect-body {
  padding: 1rem 1.25rem;
}

.suspect-stats {
  display: flex;
  gap: 2rem;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.stat-label {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
}

.stat-value {
  font-size: 0.85rem;
  color: #343a40;
}

.suspect-reason {
  font-size: 0.85rem;
  color: #495057;
  padding: 0.75rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  margin-bottom: 0.75rem;
}

.suspect-bar {
  margin-bottom: 0.75rem;
}

.suspect-actions {
  display: flex;
  gap: 0.5rem;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.85rem;
}
</style>

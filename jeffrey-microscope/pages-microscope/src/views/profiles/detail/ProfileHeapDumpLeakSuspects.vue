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
        <p class="mb-2 small">
          Run the analysis to identify objects and classes that may indicate memory leaks based on
          heuristic detection.
        </p>
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
      <p class="small">
        The heuristic analysis did not find any objects or classes that indicate potential memory
        leaks.
      </p>
    </div>

    <!-- Suspect cards -->
    <div v-else class="suspects-list">
      <div v-for="suspect in report.suspects" :key="suspect.rank" class="va-card">
        <div class="va-top-bar" :class="getSeverityClass(suspect)"></div>
        <div class="va-content">
          <div
            class="va-severity-ring"
            :class="getSeverityClass(suspect)"
            :style="ringGradientStyle(suspect)"
          >
            <div class="va-ring-inner">#{{ suspect.rank }}</div>
          </div>
          <div class="va-info">
            <div class="va-classname">{{ simpleClassName(suspect.className) }}</div>
            <div class="va-fqn">{{ suspect.className }}</div>
            <div class="va-stats">
              <div class="va-stat">
                <span class="va-stat-label">Retained Size</span>
                <span class="va-stat-value font-monospace">{{
                  FormattingService.formatBytes(suspect.retainedSize)
                }}</span>
              </div>
              <div class="va-stat">
                <span class="va-stat-label">Instances</span>
                <span class="va-stat-value font-monospace">{{
                  FormattingService.formatNumber(suspect.instanceCount)
                }}</span>
              </div>
              <div class="va-stat">
                <span class="va-stat-label">Accumulation</span>
                <span class="va-stat-value">{{ suspect.accumulationPoint }}</span>
              </div>
            </div>
          </div>
          <div class="va-percent">
            <span class="va-percent-value" :class="getSeverityClass(suspect)">{{
              suspect.heapPercentage.toFixed(1)
            }}%</span>
            <span class="va-percent-label">of heap</span>
          </div>
        </div>
        <div class="va-footer">
          <i class="bi bi-lightbulb-fill"></i>
          {{ suspect.reason }}
        </div>
        <div class="va-actions" v-if="suspect.objectId">
          <InstanceActionButtons
            :object-id="suspect.objectId"
            @show-referrers="openTreeModal($event, 'REFERRERS')"
            @show-reachables="openTreeModal($event, 'REACHABLES')"
            @show-g-c-root-path="openGCRootPath"
          />
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
import LeakSuspectsReport, { LeakSuspect } from '@/services/api/model/LeakSuspectsReport';
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
const report = ref<LeakSuspectsReport | null>(null);

// Modal state
const showTreeModal = ref(false);
const treeModalObjectId = ref<number | null>(null);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');
let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'bug',
      title: 'Suspects Found',
      value: report.value.suspects.length,
      variant: report.value.suspects.length > 0 ? ('danger' as const) : ('success' as const)
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
  if (suspect.heapPercentage >= 30) return 'critical';
  if (suspect.heapPercentage >= 15) return 'warning';
  return 'info';
};

const ringGradientStyle = (suspect: LeakSuspect): Record<string, string> => {
  const pct = Math.min(suspect.heapPercentage, 100);
  const colorVar =
    suspect.heapPercentage >= 30
      ? 'var(--color-danger)'
      : suspect.heapPercentage >= 15
        ? 'var(--color-warning)'
        : 'var(--color-info)';
  const bgVar =
    suspect.heapPercentage >= 30
      ? 'rgba(230, 55, 87, 0.12)'
      : suspect.heapPercentage >= 15
        ? 'rgba(245, 128, 62, 0.12)'
        : 'rgba(57, 175, 209, 0.12)';
  return {
    background: `conic-gradient(${colorVar} 0% ${pct}%, ${bgVar} ${pct}% 100%)`
  };
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPath = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
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

/* Glass Morphism Card */
.va-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.06),
    0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.25s ease;
}

.va-card:hover {
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.1),
    0 2px 6px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

/* Top bar */
.va-top-bar {
  height: 4px;
  background: var(--color-danger);
}

.va-top-bar.warning {
  background: var(--color-warning);
}

.va-top-bar.info {
  background: var(--color-info);
}

/* Content grid: ring | info | percent */
.va-content {
  padding: 1.25rem 1.5rem;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 1.25rem;
  align-items: start;
}

/* Severity ring with conic-gradient */
.va-severity-ring {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.va-ring-inner {
  background: var(--color-white);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
  color: var(--color-dark);
}

/* Info section */
.va-info {
  min-width: 0;
}

.va-classname {
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-dark);
  margin-bottom: 0.15rem;
}

.va-fqn {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  word-break: break-all;
  margin-bottom: 0.75rem;
}

.va-stats {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.va-stat {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.va-stat-label {
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
}

.va-stat-value {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-dark);
}

/* Percentage display */
.va-percent {
  text-align: center;
  min-width: 70px;
}

.va-percent-value {
  display: block;
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1;
  font-family: monospace;
}

.va-percent-value.critical {
  color: var(--color-danger-hover);
}

.va-percent-value.warning {
  color: var(--color-warning);
}

.va-percent-value.info {
  color: var(--color-info);
}

.va-percent-label {
  font-size: 0.65rem;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

/* Reason footer */
.va-footer {
  padding: 0.75rem 1.5rem;
  background: var(--color-amber-bg);
  border-top: 1px solid rgba(245, 158, 11, 0.1);
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: var(--color-text);
}

.va-footer i {
  color: var(--color-amber-highlight);
  margin-top: 0.15rem;
  flex-shrink: 0;
}

/* Actions row */
.va-actions {
  padding: 0.75rem 1.5rem;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}
</style>

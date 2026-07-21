<template>
  <LoadingState v-if="loading" message="Loading biggest objects..." />

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
    icon="box-seam"
    message="The heap dump needs to be initialized before you can view the biggest objects."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="box-seam-fill"
    message="The biggest objects analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else-if="report">
    <PageHeader
      title="Biggest Objects"
      description="Single objects retaining the most memory — the direct dominator-tree roots"
      icon="bi-box-seam-fill"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <div v-if="report.entries.length > 0">
      <DataTable>
        <template #toolbar>
          <TableToolbar :show-search="false">
            <span class="toolbar-info">Showing {{ report.entries.length }} objects</span>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th style="width: 40px">#</th>
            <th>Class</th>
            <th class="text-end" style="width: 120px">Shallow</th>
            <th class="text-end" style="width: 120px">Retained</th>
            <th style="width: 200px">% of Heap</th>
            <th style="width: 140px">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(entry, index) in report.entries" :key="entry.objectId">
            <td class="text-muted">{{ index + 1 }}</td>
            <td>
              <ClassNameDisplay :class-name="entry.className" />
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatBytes(entry.shallowSize) }}
            </td>
            <td class="text-end font-monospace text-warning">
              {{ FormattingService.formatBytes(entry.retainedSize) }}
            </td>
            <td>
              <div class="heap-pct-container">
                <div class="heap-pct-bar">
                  <div class="heap-pct-inner" :style="{ width: heapPercent(entry) + '%' }"></div>
                </div>
                <span class="heap-pct-value">{{ heapPercent(entry).toFixed(1) }}%</span>
              </div>
            </td>
            <td>
              <InstanceActionButtons
                :object-id="entry.objectId"
                @show-referrers="openTreeModal($event, 'REFERRERS')"
                @show-reachables="openTreeModal($event, 'REACHABLES')"
                @show-g-c-root-path="openGCRootPath"
              />
            </td>
          </tr>
        </tbody>
      </DataTable>
    </div>
    <div v-else class="text-center text-muted py-5">
      <i class="bi bi-box-seam-fill fs-1 mb-3 d-block"></i>
      <p>No object data available.</p>
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

import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type BiggestObjectsReport from '@/services/api/model/BiggestObjectsReport';
import type { BiggestObjectEntry } from '@/services/api/model/BiggestObjectsReport';
import FormattingService from '@shared/services/FormattingService';

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<BiggestObjectsReport | null>(null);

const showTreeModal = ref(false);
const treeModalObjectId = ref<number | null>(null);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!report.value) {
    return [];
  }
  const top = report.value.entries[0];
  const totalHeap = report.value.totalHeapSize;
  return [
    {
      icon: 'box-seam-fill',
      title: 'Objects Listed',
      value: FormattingService.formatNumber(report.value.entries.length),
      variant: 'highlight' as const
    },
    {
      icon: 'hdd-fill',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(totalHeap),
      variant: 'info' as const
    },
    {
      icon: 'pie-chart-fill',
      title: 'Retained by Listed',
      value: FormattingService.formatBytes(report.value.totalRetainedSize),
      variant: 'warning' as const,
      breakdown:
        totalHeap > 0
          ? [
              {
                label: 'Of heap',
                value: ((report.value.totalRetainedSize * 100) / totalHeap).toFixed(1) + '%'
              }
            ]
          : []
    },
    {
      icon: 'trophy-fill',
      title: 'Top Object',
      value: top ? FormattingService.formatBytes(top.retainedSize) : '-',
      variant: 'danger' as const,
      breakdown: top ? [{ label: 'Class', value: top.className }] : []
    }
  ];
});

const heapPercent = (entry: BiggestObjectEntry): number => {
  if (!report.value || report.value.totalHeapSize === 0) {
    return 0;
  }
  return (entry.retainedSize * 100) / report.value.totalHeapSize;
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPath = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const loadAnalysis = async () => {
  report.value = await client.getBiggestObjects();
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
    error.value = err instanceof Error ? err.message : 'Failed to load biggest objects';
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

.heap-pct-container {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.heap-pct-bar {
  height: 5px;
  background-color: var(--color-border);
  border-radius: var(--radius-sm);
  flex: 1;
  min-width: 40px;
}

.heap-pct-inner {
  height: 100%;
  border-radius: var(--radius-sm);
  background-color: var(--color-danger);
}

.heap-pct-value {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  min-width: 35px;
}

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.font-monospace {
  font-size: 0.8rem;
}

.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

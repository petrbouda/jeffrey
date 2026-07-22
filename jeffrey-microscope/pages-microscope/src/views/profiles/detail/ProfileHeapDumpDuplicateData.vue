<template>
  <LoadingState v-if="loading" message="Loading duplicate data analysis..." />

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
    icon="files"
    message="The heap dump needs to be initialized before you can view duplicate data."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="files"
    message="The duplicate data analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else-if="report">
    <PageHeader
      title="Duplicate Data"
      description="Byte-identical primitive arrays that could be shared as a single copy"
      icon="bi-files"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <DataTable v-if="report.topGroups.length > 0">
      <template #toolbar>
        <TableToolbar v-model="groupsView.query" search-placeholder="Filter by type or content...">
          <span class="toolbar-info">{{ groupsView.matchCount }} duplicate groups</span>
        </TableToolbar>
      </template>
      <thead>
        <tr>
          <th style="width: 40px">#</th>
          <th style="width: 110px">Type</th>
          <th>Content Preview</th>
          <th class="text-end" style="width: 100px">Length</th>
          <th class="text-end" style="width: 90px">Copies</th>
          <th class="text-end" style="width: 120px">Per Copy</th>
          <th class="text-end" style="width: 120px">Wasted</th>
          <th style="width: 130px">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(group, index) in groupsView.visible" :key="index">
          <td class="text-muted">{{ index + 1 }}</td>
          <td>
            <ClassNameDisplay :class-name="group.typeName" />
          </td>
          <td class="content-preview" :title="group.contentPreview">
            {{ group.contentPreview || '—' }}
          </td>
          <td class="text-end font-monospace">
            {{ FormattingService.formatNumber(group.arrayLength) }}
          </td>
          <td class="text-end font-monospace">
            {{ FormattingService.formatNumber(group.count) }}
          </td>
          <td class="text-end font-monospace">
            {{ FormattingService.formatBytes(group.shallowSize) }}
          </td>
          <td class="text-end font-monospace text-warning">
            {{ FormattingService.formatBytes(group.wastedBytes) }}
          </td>
          <td>
            <InstanceActionButtons
              v-if="group.sampleObjectIds.length > 0"
              :object-id="group.sampleObjectIds[0]"
              @show-referrers="openTreeModal($event, 'REFERRERS')"
              @show-reachables="openTreeModal($event, 'REACHABLES')"
              @show-g-c-root-path="openGCRootPath"
            />
          </td>
        </tr>
      </tbody>
      <template #footer>
        <TableShowMore
          :shown="groupsView.visible.length"
          :match-count="groupsView.matchCount"
          :total="groupsView.total"
          :expanded="groupsView.expanded"
          :page-size="groupsView.pageSize"
          @toggle="groupsView.toggle"
        />
      </template>
    </DataTable>
    <div v-else class="text-center text-muted py-5">
      <i class="bi bi-files fs-1 mb-3 d-block"></i>
      <p>No duplicate primitive arrays found — nothing to reclaim here.</p>
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
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type DuplicateDataReport from '@/services/api/model/DuplicateDataReport';
import type { DuplicateArrayGroup } from '@/services/api/model/DuplicateDataReport';
import FormattingService from '@shared/services/FormattingService';

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<DuplicateDataReport | null>(null);

const showTreeModal = ref(false);
const treeModalObjectId = ref<number | null>(null);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

let client: HeapDumpClient;

const groupsView = useTableView<DuplicateArrayGroup>(() => report.value?.topGroups ?? [], {
  searchableText: row => row.typeName + ' ' + row.contentPreview
});

const summaryMetrics = computed(() => {
  if (!report.value) {
    return [];
  }
  return [
    {
      icon: 'files',
      title: 'Duplicate Groups',
      value: FormattingService.formatNumber(report.value.duplicateGroups),
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Redundant copies',
          value: FormattingService.formatNumber(report.value.duplicateArrayCount)
        }
      ]
    },
    {
      icon: 'recycle',
      title: 'Potential Savings',
      value: FormattingService.formatBytes(report.value.potentialSavings),
      variant: 'danger' as const
    },
    {
      icon: 'grid-3x3-gap',
      title: 'Primitive Arrays Scanned',
      value: FormattingService.formatNumber(report.value.totalPrimitiveArrays),
      variant: 'info' as const,
      breakdown:
        report.value.oversizedSkipped > 0
          ? [
              {
                label: 'Oversized skipped',
                value: FormattingService.formatNumber(report.value.oversizedSkipped)
              }
            ]
          : []
    },
    {
      icon: 'hdd-fill',
      title: 'Total Array Bytes',
      value: FormattingService.formatBytes(report.value.totalPrimitiveArrayBytes),
      variant: 'info' as const
    }
  ];
});

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPath = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const loadAnalysis = async () => {
  report.value = await client.getDuplicateData();
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
    error.value = err instanceof Error ? err.message : 'Failed to load duplicate data analysis';
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

.content-preview {
  font-family: var(--font-monospace, monospace);
  font-size: 0.75rem;
  color: var(--color-text-muted);
  max-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

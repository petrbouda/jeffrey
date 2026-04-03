<template>
  <LoadingState v-if="loading" message="Loading duplicate objects..." />

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
    icon="copy"
    message="The heap dump needs to be initialized before you can view duplicate objects."
  />

  <ErrorState v-else-if="error" :message="error" />

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
      title="Duplicate Objects"
      description="Find duplicate object instances wasting memory"
      icon="bi-copy"
    />
    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">Duplicate Objects Analysis Not Available</h6>
        <p class="mb-2 small">
          Run the analysis to identify duplicate objects with identical content that waste heap
          memory.
        </p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run Duplicate Objects Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
      title="Duplicate Objects"
      description="Find duplicate object instances wasting memory"
      icon="bi-copy"
    />
    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status">
        <span class="visually-hidden">Running...</span>
      </div>
      <div>
        <h6 class="mb-1">Analyzing Heap for Duplicate Objects...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

  <!-- Analysis Results -->
  <div v-else-if="report">
    <PageHeader
      title="Duplicate Objects"
      description="Find duplicate object instances wasting memory"
      icon="bi-copy"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- No duplicates found -->
    <div v-if="report.duplicates.length === 0" class="text-center text-muted py-5">
      <i class="bi bi-shield-check fs-1 mb-3 d-block text-success"></i>
      <h6>No Duplicate Objects Detected</h6>
      <p class="small">The analysis did not find any significant duplicate objects in the heap.</p>
    </div>

    <!-- Duplicates Table -->
    <div v-else>
      <div class="filter-controls mb-3">
        <div class="row align-items-center">
          <div class="col-auto ms-auto">
            <small class="text-muted"
              >Showing {{ report.duplicates.length }} duplicate groups</small
            >
          </div>
        </div>
      </div>
      <div class="table-card">
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th style="width: 50px">#</th>
                <SortableTableHeader
                  column="className"
                  label="Class Name"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  @sort="toggleSort"
                />
                <th>Content Preview</th>
                <SortableTableHeader
                  column="duplicateCount"
                  label="Duplicate Count"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="130px"
                  @sort="toggleSort"
                />
                <SortableTableHeader
                  column="individualSize"
                  label="Individual Size"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="130px"
                  @sort="toggleSort"
                />
                <SortableTableHeader
                  column="totalWastedBytes"
                  label="Total Wasted"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="130px"
                  @sort="toggleSort"
                />
              </tr>
            </thead>
            <tbody>
              <tr v-for="(entry, index) in sortedDuplicates" :key="index">
                <td class="text-muted">{{ index + 1 }}</td>
                <td>
                  <div class="class-info">
                    <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                    <span class="package-name">{{ packageName(entry.className) }}</span>
                  </div>
                </td>
                <td>
                  <span class="content-preview font-monospace" :title="entry.contentPreview">{{
                    truncatePreview(entry.contentPreview)
                  }}</span>
                </td>
                <td class="text-end font-monospace">
                  {{ FormattingService.formatNumber(entry.duplicateCount) }}
                </td>
                <td class="text-end font-monospace">
                  {{ FormattingService.formatBytes(entry.individualSize) }}
                </td>
                <td class="text-end font-monospace text-warning">
                  {{ FormattingService.formatBytes(entry.totalWastedBytes) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
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
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type DuplicateObjectsReport from '@/services/api/model/DuplicateObjectsReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const analysisExists = ref(false);
const analysisRunning = ref(false);
const report = ref<DuplicateObjectsReport | null>(null);

// Sort state
const sortColumn = ref('totalWastedBytes');
const sortDirection = ref<'asc' | 'desc'>('desc');

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'search',
      title: 'Instances Analyzed',
      value: FormattingService.formatNumber(report.value.totalInstancesAnalyzed),
      variant: 'highlight' as const
    },
    {
      icon: 'hdd',
      title: 'Total Wasted Bytes',
      value: FormattingService.formatBytes(report.value.totalWastedBytes),
      variant: 'danger' as const
    }
  ];
});

const sortedDuplicates = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.duplicates];
  const dir = sortDirection.value === 'asc' ? 1 : -1;

  switch (sortColumn.value) {
    case 'className':
      entries.sort((a, b) => dir * a.className.localeCompare(b.className));
      break;
    case 'duplicateCount':
      entries.sort((a, b) => dir * (a.duplicateCount - b.duplicateCount));
      break;
    case 'individualSize':
      entries.sort((a, b) => dir * (a.individualSize - b.individualSize));
      break;
    case 'totalWastedBytes':
      entries.sort((a, b) => dir * (a.totalWastedBytes - b.totalWastedBytes));
      break;
  }
  return entries;
});

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = column === 'className' ? 'asc' : 'desc';
  }
};

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
};

const truncatePreview = (preview: string): string => {
  if (!preview) return '-';
  return preview.length > 80 ? preview.substring(0, 80) + '...' : preview;
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runDuplicateObjects();
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run duplicate objects analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.duplicateObjectsExists();
  if (analysisExists.value) {
    report.value = await client.getDuplicateObjects();
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
    error.value = err instanceof Error ? err.message : 'Failed to load duplicate objects';
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
}

.class-name {
  font-size: 0.8rem;
  font-weight: 600;
  background-color: transparent;
  color: var(--color-text);
  white-space: nowrap;
}

.package-name {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.content-preview {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  max-width: 300px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-lg);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.table thead th {
  background-color: var(--color-light);
  font-weight: 600;
  color: var(--color-text);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.table td {
  font-size: 0.8rem;
  padding: 0.6rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid var(--color-border-row);
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.filter-controls {
  background-color: var(--color-light);
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border);
}

.font-monospace {
  font-size: 0.8rem;
}

/* Darker warning colors */
.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

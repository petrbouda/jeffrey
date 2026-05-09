<template>
  <LoadingState v-if="loading" message="Loading class loader analysis..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">
          No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a
          heap dump and add it to the recording folder.
        </p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
    v-else-if="!cacheReady"
    icon="diagram-3"
    message="The heap dump needs to be initialized before you can view class loader analysis. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="diagram-3"
    message="The class loader analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else>
    <PageHeader
      title="Class Loader Analysis"
      description="Analyze class loaders and detect duplicate classes"
      icon="bi-diagram-3"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Analysis Section -->
    <ChartSectionWithTabs
      icon="diagram-3"
      :tabs="analysisTabs"
      :full-width="true"
      id-prefix="classloader-"
      @tab-change="onTabChange"
    >
      <!-- Class Loaders Tab -->
      <template #class-loaders>
        <div v-if="report && report.classLoaders.length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">Showing {{ report.classLoaders.length }} class loaders</span>
              </TableToolbar>
            </template>
                <thead>
                  <tr>
                    <th style="width: 50px">#</th>
                    <th>Class Loader Class</th>
                    <SortableTableHeader
                      column="classCount"
                      label="Class Count"
                      :sort-column="loaderSortColumn"
                      :sort-direction="loaderSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleLoaderSort"
                    />
                    <SortableTableHeader
                      column="totalClassSize"
                      label="Total Class Size"
                      :sort-column="loaderSortColumn"
                      :sort-direction="loaderSortDirection"
                      align="end"
                      width="140px"
                      @sort="toggleLoaderSort"
                    />
                    <SortableTableHeader
                      column="retainedSize"
                      label="Retained Size"
                      :sort-column="loaderSortColumn"
                      :sort-direction="loaderSortDirection"
                      align="end"
                      width="140px"
                      @sort="toggleLoaderSort"
                    />
                    <th style="width: 180px">% of Max</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedClassLoaders" :key="entry.objectId">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <div class="class-info">
                        <code class="class-name">{{
                          simpleClassName(entry.classLoaderClassName)
                        }}</code>
                        <span class="package-name">{{
                          packageName(entry.classLoaderClassName)
                        }}</span>
                      </div>
                    </td>
                    <td class="text-end font-monospace">
                      {{ FormattingService.formatNumber(entry.classCount) }}
                    </td>
                    <td class="text-end font-monospace">
                      {{ FormattingService.formatBytes(entry.totalClassSize) }}
                    </td>
                    <td class="text-end font-monospace text-warning">
                      {{ FormattingService.formatBytes(entry.retainedSize) }}
                    </td>
                    <td>
                      <div class="d-flex align-items-center gap-2">
                        <div class="progress flex-grow-1" style="height: 6px">
                          <div
                            class="progress-bar"
                            :style="{
                              width: getLoaderPercentage(entry) + '%',
                              backgroundColor: '#4285F4'
                            }"
                          ></div>
                        </div>
                        <small class="text-muted" style="min-width: 45px"
                          >{{ getLoaderPercentage(entry).toFixed(1) }}%</small
                        >
                      </div>
                    </td>
                  </tr>
                </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-diagram-3 fs-1 mb-3 d-block"></i>
          <p>No class loader data available.</p>
        </div>
      </template>

      <!-- Duplicate Classes Tab -->
      <template #duplicate-classes>
        <div v-if="report && report.duplicateClasses.length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">Showing {{ report.duplicateClasses.length }} duplicate classes</span>
              </TableToolbar>
            </template>
                <thead>
                  <tr>
                    <th style="width: 50px">#</th>
                    <th>Class Name</th>
                    <SortableTableHeader
                      column="loaderCount"
                      label="Loader Count"
                      :sort-column="dupSortColumn"
                      :sort-direction="dupSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleDupSort"
                    />
                    <th>Class Loader Names</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedDuplicateClasses" :key="entry.className">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <div class="class-info">
                        <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                        <span class="package-name">{{ packageName(entry.className) }}</span>
                      </div>
                    </td>
                    <td class="text-end font-monospace">
                      <span :class="entry.loaderCount > 2 ? 'badge bg-warning text-dark' : ''">
                        {{ entry.loaderCount }}
                      </span>
                    </td>
                    <td>
                      <span class="loader-names">{{ entry.classLoaderNames.join(', ') }}</span>
                    </td>
                  </tr>
                </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-check-circle fs-1 mb-3 d-block"></i>
          <p>No duplicate classes detected.</p>
        </div>
      </template>
    </ChartSectionWithTabs>
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
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type ClassLoaderReport from '@/services/api/model/ClassLoaderReport';
import type { ClassLoaderInfo } from '@/services/api/model/ClassLoaderReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();

const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<ClassLoaderReport | null>(null);
const activeTab = ref('class-loaders');

// Sort state for class loaders table
const loaderSortColumn = ref('retainedSize');
const loaderSortDirection = ref<'asc' | 'desc'>('desc');

// Sort state for duplicate classes table
const dupSortColumn = ref('loaderCount');
const dupSortDirection = ref<'asc' | 'desc'>('desc');

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'class-loaders', label: 'Class Loaders', icon: 'diagram-3' },
  { id: 'duplicate-classes', label: 'Duplicate Classes', icon: 'files' }
];

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'diagram-3',
      title: 'Total Class Loaders',
      value: FormattingService.formatNumber(report.value.totalClassLoaders),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Classes', value: FormattingService.formatNumber(report.value.totalClasses) }
      ]
    },
    {
      icon: 'files',
      title: 'Duplicate Class Count',
      value: FormattingService.formatNumber(report.value.duplicateClassCount),
      variant: report.value.duplicateClassCount > 0 ? ('warning' as const) : ('success' as const)
    }
  ];
});

const maxLoaderRetainedSize = computed(() => {
  if (!report.value || report.value.classLoaders.length === 0) return 0;
  return Math.max(...report.value.classLoaders.map(e => e.retainedSize));
});

// Sorted class loaders
const sortedClassLoaders = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.classLoaders];
  const direction = loaderSortDirection.value === 'asc' ? 1 : -1;

  switch (loaderSortColumn.value) {
    case 'classCount':
      entries.sort((a, b) => direction * (a.classCount - b.classCount));
      break;
    case 'totalClassSize':
      entries.sort((a, b) => direction * (a.totalClassSize - b.totalClassSize));
      break;
    case 'retainedSize':
      entries.sort((a, b) => direction * (a.retainedSize - b.retainedSize));
      break;
  }
  return entries;
});

// Sorted duplicate classes
const sortedDuplicateClasses = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.duplicateClasses];
  const direction = dupSortDirection.value === 'asc' ? 1 : -1;

  switch (dupSortColumn.value) {
    case 'loaderCount':
      entries.sort((a, b) => direction * (a.loaderCount - b.loaderCount));
      break;
  }
  return entries;
});

const toggleLoaderSort = (column: string) => {
  if (loaderSortColumn.value === column) {
    loaderSortDirection.value = loaderSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    loaderSortColumn.value = column;
    loaderSortDirection.value = 'desc';
  }
};

const toggleDupSort = (column: string) => {
  if (dupSortColumn.value === column) {
    dupSortDirection.value = dupSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    dupSortColumn.value = column;
    dupSortDirection.value = 'desc';
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

const getLoaderPercentage = (entry: ClassLoaderInfo): number => {
  if (maxLoaderRetainedSize.value === 0) return 0;
  return (entry.retainedSize / maxLoaderRetainedSize.value) * 100;
};

const onTabChange = (_tabIndex: number, tab: { id: string; label: string; icon?: string }) => {
  activeTab.value = tab.id;
};

const loadAnalysis = async () => {
  report.value = await client.getClassLoaderAnalysis();
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
    error.value = err instanceof Error ? err.message : 'Failed to load class loader analysis';
    console.error('Error loading class loader analysis:', err);
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

.loader-names {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.progress {
  background-color: var(--color-border);
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.8rem;
}

/* Darker warning color for better readability */
.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

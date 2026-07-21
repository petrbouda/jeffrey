<template>
  <LoadingState v-if="loading" message="Loading memory consumers..." />

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
    icon="pie-chart"
    message="The heap dump needs to be initialized before you can view memory consumers."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="pie-chart-fill"
    message="The consumer report is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else-if="report">
    <PageHeader
      title="Memory Consumers"
      description="Which packages and class loaders own the most heap memory"
      icon="bi-pie-chart-fill"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Results -->
    <TabBar v-model="activeTab" :tabs="analysisTabs" class="mb-3" />

    <!-- By Package Tab (rolled up across class loaders) -->
    <div v-show="activeTab === 'by-package'">
      <DataTable v-if="packageRows.length > 0">
        <template #toolbar>
          <TableToolbar v-model="packagesView.query" search-placeholder="Filter packages...">
            <span class="toolbar-info">{{ packagesView.matchCount }} packages</span>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th style="width: 40px">#</th>
            <th>Package</th>
            <th class="text-end" style="width: 130px">Shallow Size</th>
            <th style="width: 200px">% of Heap</th>
            <th class="text-end" style="width: 100px">Classes</th>
            <th class="text-end" style="width: 120px">Instances</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(entry, index) in packagesView.visible" :key="entry.packageName">
            <td class="text-muted">{{ index + 1 }}</td>
            <td class="font-monospace package-name">{{ entry.packageName }}</td>
            <td class="text-end font-monospace text-warning">
              {{ FormattingService.formatBytes(entry.shallowSize) }}
            </td>
            <td>
              <div class="heap-pct-container">
                <div class="heap-pct-bar">
                  <div
                    class="heap-pct-inner"
                    :style="{ width: heapPercent(entry.shallowSize) + '%' }"
                  ></div>
                </div>
                <span class="heap-pct-value">{{ heapPercent(entry.shallowSize).toFixed(1) }}%</span>
              </div>
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(entry.classCount) }}
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(entry.instanceCount) }}
            </td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="packagesView.visible.length"
            :match-count="packagesView.matchCount"
            :total="packagesView.total"
            :expanded="packagesView.expanded"
            :page-size="packagesView.pageSize"
            @toggle="packagesView.toggle"
          />
        </template>
      </DataTable>
      <div v-else class="text-center text-muted py-5">
        <i class="bi bi-pie-chart-fill fs-1 mb-3 d-block"></i>
        <p>No consumer data available.</p>
      </div>
    </div>

    <!-- By Package & Class Loader Tab -->
    <div v-show="activeTab === 'by-package-loader'">
      <DataTable v-if="report.topConsumers.length > 0">
        <template #toolbar>
          <TableToolbar v-model="consumersView.query" search-placeholder="Filter packages or loaders...">
            <span class="toolbar-info">{{ consumersView.matchCount }} consumers</span>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th style="width: 40px">#</th>
            <th>Package</th>
            <th>Class Loader</th>
            <th class="text-end" style="width: 130px">Shallow Size</th>
            <th style="width: 200px">% of Heap</th>
            <th class="text-end" style="width: 100px">Classes</th>
            <th class="text-end" style="width: 120px">Instances</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(entry, index) in consumersView.visible"
            :key="entry.packageName + ':' + entry.classLoaderId"
          >
            <td class="text-muted">{{ index + 1 }}</td>
            <td class="font-monospace package-name">{{ entry.packageName }}</td>
            <td>
              <ClassNameDisplay
                v-if="entry.classLoaderClassName"
                :class-name="entry.classLoaderClassName"
              />
              <span v-else class="text-muted loader-empty">bootstrap</span>
            </td>
            <td class="text-end font-monospace text-warning">
              {{ FormattingService.formatBytes(entry.shallowSize) }}
            </td>
            <td>
              <div class="heap-pct-container">
                <div class="heap-pct-bar">
                  <div
                    class="heap-pct-inner"
                    :style="{ width: heapPercent(entry.shallowSize) + '%' }"
                  ></div>
                </div>
                <span class="heap-pct-value">{{ heapPercent(entry.shallowSize).toFixed(1) }}%</span>
              </div>
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(entry.classCount) }}
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(entry.instanceCount) }}
            </td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="consumersView.visible.length"
            :match-count="consumersView.matchCount"
            :total="consumersView.total"
            :expanded="consumersView.expanded"
            :page-size="consumersView.pageSize"
            @toggle="consumersView.toggle"
          />
        </template>
      </DataTable>
      <div v-else class="text-center text-muted py-5">
        <i class="bi bi-pie-chart-fill fs-1 mb-3 d-block"></i>
        <p>No consumer data available.</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import TabBar from '@shared/components/TabBar.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type ConsumerReport from '@/services/api/model/ConsumerReport';
import type { ComponentEntry, ConsumerEntry } from '@/services/api/model/ConsumerReport';
import FormattingService from '@shared/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<ConsumerReport | null>(null);

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'by-package', label: 'By Package', icon: 'box' },
  { id: 'by-package-loader', label: 'By Package & Class Loader', icon: 'diagram-2' }
];
const activeTab = ref(analysisTabs[0].id);

/**
 * Per-package rollup across class loaders. The backend's componentReport is
 * used when populated; otherwise it is derived from topConsumers client-side.
 */
const packageRows = computed<ComponentEntry[]>(() => {
  if (!report.value) {
    return [];
  }
  if (report.value.componentReport.length > 0) {
    return report.value.componentReport;
  }
  const byPackage = new Map<string, ComponentEntry>();
  for (const entry of report.value.topConsumers) {
    const existing = byPackage.get(entry.packageName);
    if (existing) {
      byPackage.set(entry.packageName, {
        packageName: entry.packageName,
        retainedSize: existing.retainedSize + entry.retainedSize,
        shallowSize: existing.shallowSize + entry.shallowSize,
        classCount: existing.classCount + entry.classCount,
        instanceCount: existing.instanceCount + entry.instanceCount
      });
    } else {
      byPackage.set(entry.packageName, {
        packageName: entry.packageName,
        retainedSize: entry.retainedSize,
        shallowSize: entry.shallowSize,
        classCount: entry.classCount,
        instanceCount: entry.instanceCount
      });
    }
  }
  return Array.from(byPackage.values()).sort((a, b) => b.shallowSize - a.shallowSize);
});

const packagesView = useTableView<ComponentEntry>(() => packageRows.value, {
  searchableText: row => row.packageName
});

const consumersView = useTableView<ConsumerEntry>(() => report.value?.topConsumers ?? [], {
  searchableText: row => row.packageName + ' ' + (row.classLoaderClassName ?? '')
});

const summaryMetrics = computed(() => {
  if (!report.value) {
    return [];
  }
  const topPackage = packageRows.value[0];
  return [
    {
      icon: 'hdd-fill',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(report.value.totalHeapSize),
      variant: 'highlight' as const
    },
    {
      icon: 'box',
      title: 'Packages',
      value: FormattingService.formatNumber(packageRows.value.length),
      variant: 'info' as const
    },
    {
      icon: 'diagram-2',
      title: 'Package/Loader Cells',
      value: FormattingService.formatNumber(report.value.topConsumers.length),
      variant: 'info' as const
    },
    {
      icon: 'trophy-fill',
      title: 'Top Package',
      value: topPackage ? FormattingService.formatBytes(topPackage.shallowSize) : '-',
      variant: 'danger' as const,
      breakdown: topPackage ? [{ label: 'Package', value: topPackage.packageName }] : []
    }
  ];
});

const heapPercent = (bytes: number): number => {
  if (!report.value || report.value.totalHeapSize === 0) {
    return 0;
  }
  return (bytes * 100) / report.value.totalHeapSize;
};

const loadAnalysis = async () => {
  report.value = await client.getConsumerReport();
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
    error.value = err instanceof Error ? err.message : 'Failed to load memory consumers';
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

.package-name {
  font-size: 0.8rem;
}

.loader-empty {
  font-size: 0.85rem;
  font-style: italic;
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
  background-color: var(--color-primary);
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

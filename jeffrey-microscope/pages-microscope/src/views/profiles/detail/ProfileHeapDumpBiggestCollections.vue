<template>
  <LoadingState v-if="loading" message="Loading biggest collections..." />

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
    icon="collection"
    message="The heap dump needs to be initialized before you can view biggest collections."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="collection-fill"
    message="The biggest collections analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else-if="report">
    <PageHeader
      title="Biggest Collections"
      description="Find the largest collection instances by element count and retained size"
      icon="bi-collection-fill"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Results -->
    <TabBar v-model="activeTab" :tabs="analysisTabs" class="mb-3" />

    <!-- By Element Count Tab -->
    <div v-show="activeTab === 'by-element-count'">
        <div v-if="report.byElementCount.length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">Showing {{ report.byElementCount.length }} collections</span>
              </TableToolbar>
            </template>
                <thead>
                  <tr>
                    <th style="width: 40px">#</th>
                    <th style="width: 28%">Collection</th>
                    <th style="width: 28%">Owner</th>
                    <th class="text-end" style="width: 220px">
                      <div>Usage</div>
                      <div class="usage-sublabel">
                        <span class="usage-size">size</span>
                        <span class="usage-sep">/</span>
                        <span class="usage-cap">capacity</span>
                      </div>
                    </th>
                    <th class="text-end" style="width: 110px">Retained</th>
                    <th style="width: 180px">Fill Ratio</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedByElementCount" :key="index">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <ClassNameDisplay :class-name="entry.className" />
                    </td>
                    <td>
                      <ClassNameDisplay v-if="entry.ownerClassName" :class-name="entry.ownerClassName" />
                      <span v-else class="text-muted owner-empty">—</span>
                    </td>
                    <td class="text-end font-monospace">
                      <div>{{ FormattingService.formatNumber(entry.elementCount) }}</div>
                      <div class="capacity-hint">
                        {{ FormattingService.formatNumber(entry.capacity) }}
                      </div>
                    </td>
                    <td class="text-end font-monospace text-warning">
                      {{ FormattingService.formatBytes(entry.retainedSize) }}
                    </td>
                    <td>
                      <div class="fill-bar-container">
                        <div class="fill-bar">
                          <div
                            class="fill-bar-inner"
                            :style="{
                              width: entry.fillRatio * 100 + '%',
                              backgroundColor: getFillColor(entry.fillRatio)
                            }"
                          ></div>
                        </div>
                        <span class="fill-pct">{{ (entry.fillRatio * 100).toFixed(1) }}%</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-collection-fill fs-1 mb-3 d-block"></i>
          <p>No collection data available.</p>
        </div>
    </div>

    <!-- By Retained Size Tab -->
    <div v-show="activeTab === 'by-retained-size'">
        <div v-if="report.byRetainedSize.length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">Showing {{ report.byRetainedSize.length }} collections</span>
              </TableToolbar>
            </template>
                <thead>
                  <tr>
                    <th style="width: 40px">#</th>
                    <th style="width: 28%">Collection</th>
                    <th style="width: 28%">Owner</th>
                    <th class="text-end" style="width: 220px">
                      <div>Usage</div>
                      <div class="usage-sublabel">
                        <span class="usage-size">size</span>
                        <span class="usage-sep">/</span>
                        <span class="usage-cap">capacity</span>
                      </div>
                    </th>
                    <th class="text-end" style="width: 110px">Retained</th>
                    <th style="width: 180px">Fill Ratio</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedByRetainedSize" :key="index">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <ClassNameDisplay :class-name="entry.className" />
                    </td>
                    <td>
                      <ClassNameDisplay v-if="entry.ownerClassName" :class-name="entry.ownerClassName" />
                      <span v-else class="text-muted owner-empty">—</span>
                    </td>
                    <td class="text-end font-monospace">
                      <div>{{ FormattingService.formatNumber(entry.elementCount) }}</div>
                      <div class="capacity-hint">
                        {{ FormattingService.formatNumber(entry.capacity) }}
                      </div>
                    </td>
                    <td class="text-end font-monospace text-warning">
                      {{ FormattingService.formatBytes(entry.retainedSize) }}
                    </td>
                    <td>
                      <div class="fill-bar-container">
                        <div class="fill-bar">
                          <div
                            class="fill-bar-inner"
                            :style="{
                              width: entry.fillRatio * 100 + '%',
                              backgroundColor: getFillColor(entry.fillRatio)
                            }"
                          ></div>
                        </div>
                        <span class="fill-pct">{{ (entry.fillRatio * 100).toFixed(1) }}%</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-collection-fill fs-1 mb-3 d-block"></i>
          <p>No collection data available.</p>
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
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import TabBar from '@/components/TabBar.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type BiggestCollectionsReport from '@/services/api/model/BiggestCollectionsReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<BiggestCollectionsReport | null>(null);

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'by-element-count', label: 'By Element Count', icon: 'hash' },
  { id: 'by-retained-size', label: 'By Retained Size', icon: 'hdd' }
];
const activeTab = ref(analysisTabs[0].id);

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  const largest = report.value.byElementCount[0];
  const topRetained = report.value.byRetainedSize[0];
  return [
    {
      icon: 'collection-fill',
      title: 'Collections Analyzed',
      value: FormattingService.formatNumber(report.value.totalCollectionsAnalyzed),
      variant: 'highlight' as const
    },
    {
      icon: 'speedometer2',
      title: 'Avg Fill Ratio',
      value: (() => {
        const entries = report.value.byElementCount;
        if (entries.length === 0) return '-';
        const avg = entries.reduce((sum, e) => sum + e.fillRatio, 0) / entries.length;
        return (avg * 100).toFixed(1) + '%';
      })(),
      variant: 'warning' as const
    },
    {
      icon: 'hash',
      title: 'Largest Collection',
      value: largest ? FormattingService.formatNumber(largest.elementCount) + ' items' : '-',
      variant: 'info' as const,
      breakdown: largest
        ? [
            { label: 'Size', value: FormattingService.formatNumber(largest.elementCount) },
            { label: 'Capacity', value: FormattingService.formatNumber(largest.capacity) }
          ]
        : []
    },
    {
      icon: 'hdd',
      title: 'Top Retained',
      value: topRetained ? FormattingService.formatBytes(topRetained.retainedSize) : '-',
      variant: 'danger' as const,
      breakdown: topRetained
        ? [
            { label: 'Size', value: FormattingService.formatNumber(topRetained.elementCount) },
            { label: 'Capacity', value: FormattingService.formatNumber(topRetained.capacity) }
          ]
        : []
    }
  ];
});

const sortedByElementCount = computed(() => {
  if (!report.value) return [];
  return report.value.byElementCount;
});

const sortedByRetainedSize = computed(() => {
  if (!report.value) return [];
  return report.value.byRetainedSize;
});

const getFillColor = (ratio: number): string => {
  const pct = ratio * 100;
  if (pct >= 75) return '#28a745';
  if (pct >= 50) return '#28a745';
  if (pct >= 25) return '#ffc107';
  return '#ffc107';
};

const loadAnalysis = async () => {
  report.value = await client.getBiggestCollections();
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
    error.value = err instanceof Error ? err.message : 'Failed to load biggest collections';
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

.owner-empty {
  font-size: 0.85rem;
}

.fill-bar-container {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.fill-bar {
  height: 5px;
  background-color: var(--color-border);
  border-radius: 3px;
  flex: 1;
  min-width: 40px;
}

.fill-bar-inner {
  height: 100%;
  border-radius: 3px;
}

.capacity-hint {
  color: var(--color-purple);
}

.usage-sublabel {
  font-size: 0.65rem;
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
  margin-top: 1px;
}

.usage-sublabel .usage-size {
  color: var(--color-text);
}

.usage-sublabel .usage-cap {
  color: var(--color-purple);
}

.usage-sublabel .usage-sep {
  color: var(--color-text-light);
  margin: 0 0.15rem;
}

.fill-pct {
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

/* Darker warning color for better readability */
.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

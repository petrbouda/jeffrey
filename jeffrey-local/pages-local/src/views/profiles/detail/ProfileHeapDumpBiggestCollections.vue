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

  <!-- Analysis Not Yet Run -->
  <div v-else-if="!analysisExists && !analysisRunning">
    <PageHeader
      title="Biggest Collections"
      description="Find the largest collection instances by element count and retained size"
      icon="bi-collection-fill"
    />
    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-3 fs-4"></i>
      <div class="flex-grow-1">
        <h6 class="mb-1">Biggest Collections Analysis Not Available</h6>
        <p class="mb-2 small">
          Run the analysis to identify the largest collection instances in the heap by element count
          and retained memory size.
        </p>
        <button class="btn btn-primary btn-sm" @click="runAnalysis">
          <i class="bi bi-play-fill me-1"></i>
          Run Biggest Collections Analysis
        </button>
      </div>
    </div>
  </div>

  <!-- Analysis Running -->
  <div v-else-if="analysisRunning">
    <PageHeader
      title="Biggest Collections"
      description="Find the largest collection instances by element count and retained size"
      icon="bi-collection-fill"
    />
    <div class="alert alert-info d-flex align-items-center">
      <div class="spinner-border spinner-border-sm me-3" role="status">
        <span class="visually-hidden">Running...</span>
      </div>
      <div>
        <h6 class="mb-1">Analyzing Collection Instances...</h6>
        <p class="mb-0 small">This may take a few moments depending on the heap dump size.</p>
      </div>
    </div>
  </div>

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
    <ChartSectionWithTabs
      icon="collection-fill"
      :tabs="analysisTabs"
      :full-width="true"
      id-prefix="biggest-collections-"
    >
      <!-- By Element Count Tab -->
      <template #by-element-count>
        <div v-if="report.byElementCount.length > 0">
          <div class="filter-controls mb-3">
            <div class="row align-items-center">
              <div class="col-auto ms-auto">
                <small class="text-muted"
                  >Showing {{ report.byElementCount.length }} collections</small
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
                    <th style="width: 45%">Collection</th>
                    <th class="text-end" style="width: 220px">Usage</th>
                    <th style="width: 130px">Fill Ratio</th>
                    <th class="text-end" style="width: 100px">Shallow</th>
                    <th class="text-end" style="width: 110px">Retained</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedByElementCount" :key="index">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <div class="class-info">
                        <div class="class-name-line">
                          <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                          <span class="package-name">{{ packageName(entry.className) }}</span>
                        </div>
                        <div class="detail-line" v-if="entry.ownerClassName">
                          <span class="owner-label">owner</span>
                          <span class="field-tag">{{ entry.ownerClassName }}</span>
                        </div>
                      </div>
                    </td>
                    <td class="text-end font-monospace">
                      <div>{{ FormattingService.formatNumber(entry.elementCount) }}</div>
                      <div class="capacity-hint">
                        {{ FormattingService.formatNumber(entry.capacity) }}
                      </div>
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
                    <td class="text-end font-monospace">
                      {{ FormattingService.formatBytes(entry.shallowSize) }}
                    </td>
                    <td class="text-end font-monospace text-warning">
                      {{ FormattingService.formatBytes(entry.retainedSize) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-collection-fill fs-1 mb-3 d-block"></i>
          <p>No collection data available.</p>
        </div>
      </template>

      <!-- By Retained Size Tab -->
      <template #by-retained-size>
        <div v-if="report.byRetainedSize.length > 0">
          <div class="filter-controls mb-3">
            <div class="row align-items-center">
              <div class="col-auto ms-auto">
                <small class="text-muted"
                  >Showing {{ report.byRetainedSize.length }} collections</small
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
                    <th style="width: 45%">Collection</th>
                    <th class="text-end" style="width: 220px">Usage</th>
                    <th style="width: 130px">Fill Ratio</th>
                    <th class="text-end" style="width: 100px">Shallow</th>
                    <th class="text-end" style="width: 110px">Retained</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedByRetainedSize" :key="index">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <div class="class-info">
                        <div class="class-name-line">
                          <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                          <span class="package-name">{{ packageName(entry.className) }}</span>
                        </div>
                        <div class="detail-line" v-if="entry.ownerClassName">
                          <span class="owner-label">owner</span>
                          <span class="field-tag">{{ entry.ownerClassName }}</span>
                        </div>
                      </div>
                    </td>
                    <td class="text-end font-monospace">
                      <div>{{ FormattingService.formatNumber(entry.elementCount) }}</div>
                      <div class="capacity-hint">
                        {{ FormattingService.formatNumber(entry.capacity) }}
                      </div>
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
                    <td class="text-end font-monospace">
                      {{ FormattingService.formatBytes(entry.shallowSize) }}
                    </td>
                    <td class="text-end font-monospace text-warning">
                      {{ FormattingService.formatBytes(entry.retainedSize) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-collection-fill fs-1 mb-3 d-block"></i>
          <p>No collection data available.</p>
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
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type BiggestCollectionsReport from '@/services/api/model/BiggestCollectionsReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const analysisExists = ref(false);
const analysisRunning = ref(false);
const report = ref<BiggestCollectionsReport | null>(null);

let client: HeapDumpClient;

const analysisTabs = [
  { id: 'by-element-count', label: 'By Element Count', icon: 'hash' },
  { id: 'by-retained-size', label: 'By Retained Size', icon: 'hdd' }
];

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

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
};

const runAnalysis = async () => {
  try {
    analysisRunning.value = true;
    await client.runBiggestCollections();
    await loadAnalysis();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run biggest collections analysis';
  } finally {
    analysisRunning.value = false;
  }
};

const loadAnalysis = async () => {
  analysisExists.value = await client.biggestCollectionsExists();
  if (analysisExists.value) {
    report.value = await client.getBiggestCollections();
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

.class-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.class-name-line {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.detail-line {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: 0.75rem;
  margin-top: 1px;
}

.detail-sep {
  color: var(--color-text-light);
  user-select: none;
}

.owner-label {
  color: var(--color-text-light);
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.field-tag {
  color: var(--bs-purple);
  font-style: italic;
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
  color: var(--color-text-light);
}

.fill-pct {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  min-width: 35px;
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

/* Darker warning color for better readability */
.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

<template>
  <LoadingState v-if="loading" message="Loading class histogram..." />

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
    icon="bar-chart-steps"
    message="The heap dump needs to be initialized before you can view the class histogram. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
      title="Class Histogram"
      description="Memory usage breakdown by class"
      icon="bi-bar-chart-steps"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Data Table -->
    <EmptyState
      v-if="histogramData.length === 0"
      icon="bi-bar-chart"
      title="No histogram data available"
    />
    <DataTable v-else>
      <template #toolbar>
        <TableToolbar :show-search="false">
          <span class="toolbar-info">Showing {{ histogramData.length }} classes</span>
          <template #filters>
            <div class="d-flex align-items-center">
              <label class="form-label mb-0 me-2 toolbar-info">Top:</label>
              <select
                v-model="histogramTopN"
                class="form-select form-select-sm select-narrow"
                @change="loadHistogram"
              >
                <option :value="50">50</option>
                <option :value="100">100</option>
                <option :value="200">200</option>
                <option :value="500">500</option>
              </select>
            </div>
          </template>
        </TableToolbar>
      </template>
          <thead>
            <tr>
              <th style="width: 40px">#</th>
              <th>Class Name</th>
              <SortableTableHeader
                column="COUNT"
                label="Instances"
                :sort-column="histogramSortBy"
                :sort-direction="'desc'"
                align="end"
                width="120px"
                @sort="handleSort"
              />
              <SortableTableHeader
                column="SIZE"
                label="Total Size"
                :sort-column="histogramSortBy"
                :sort-direction="'desc'"
                align="end"
                width="120px"
                @sort="handleSort"
              />
              <th style="width: 200px">% of Max</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(entry, index) in histogramData" :key="entry.className">
              <tr :class="{ 'row-expanded': expandedClass === entry.className }">
                <td class="text-muted">{{ index + 1 }}</td>
                <td>
                  <div class="class-cell">
                    <button
                      class="btn btn-expand"
                      :disabled="instancesLoading === entry.className"
                      @click="toggleExpand(entry)"
                    >
                      <span
                        v-if="instancesLoading === entry.className"
                        class="spinner-border spinner-border-sm spinner-inline"
                      ></span>
                      <i
                        v-else-if="expandedClass === entry.className"
                        class="bi bi-chevron-down"
                      ></i>
                      <i v-else class="bi bi-chevron-right"></i>
                    </button>
                    <ClassNameDisplay :class-name="entry.className" />
                  </div>
                </td>
                <td class="text-end font-monospace">
                  {{ FormattingService.formatNumber(entry.instanceCount) }}
                </td>
                <td class="text-end font-monospace text-warning">
                  {{ FormattingService.formatBytes(entry.totalSize) }}
                </td>
                <td>
                  <div class="d-flex align-items-center gap-2">
                    <div class="progress flex-grow-1" style="height: 6px">
                      <div
                        class="progress-bar"
                        :style="{
                          width: getDistributionPercentage(entry) + '%',
                          backgroundColor: '#4285F4'
                        }"
                      ></div>
                    </div>
                    <small class="text-muted" style="min-width: 45px"
                      >{{ getDistributionPercentage(entry).toFixed(1) }}%</small
                    >
                  </div>
                </td>
              </tr>

              <!-- Expansion drawer -->
              <tr
                v-if="expandedClass === entry.className && instancesError"
                class="drawer-row"
              >
                <td colspan="5">
                  <div class="drawer-panel drawer-error">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    {{ instancesError }}
                  </div>
                </td>
              </tr>
              <tr
                v-else-if="
                  expandedClass === entry.className &&
                  instancesByClass[entry.className] &&
                  instancesByClass[entry.className].instances.length > 0
                "
                class="drawer-row"
              >
                <td colspan="5">
                  <div class="drawer-panel">
                    <div class="drawer-title">
                      Top {{ instancesByClass[entry.className].instances.length }} instances
                      <span class="sort-hint">— retained size, descending</span>
                    </div>

                    <div class="split-panel">
                      <!-- Left aggregate panel -->
                      <div class="split-left">
                        <div class="sp-stat-label">Top {{ TOP_INSTANCES_LIMIT }} retained</div>
                        <div class="sp-stat-value text-warning">
                          {{ FormattingService.formatBytes(getTopRetainedTotal(entry.className)) }}
                        </div>

                        <div class="sp-stat-label">% of class total</div>
                        <div class="sp-stat-value">
                          {{ getTopRetainedPercent(entry).toFixed(2) }}%
                        </div>

                        <div class="sp-stat-label">Distribution</div>
                        <div class="sparkline">
                          <div
                            v-for="(inst, idx) in instancesByClass[entry.className].instances"
                            :key="idx"
                            class="sb"
                            :style="{
                              height: getInstanceBarPercent(entry.className, inst) + '%'
                            }"
                          ></div>
                        </div>
                        <div class="sp-caption">
                          <span>{{
                            FormattingService.formatBytes(
                              getMaxRetained(entry.className)
                            )
                          }}</span>
                          <span>{{
                            FormattingService.formatBytes(
                              getMinRetained(entry.className)
                            )
                          }}</span>
                        </div>

                        <div
                          v-if="instancesByClass[entry.className].totalInstances > TOP_INSTANCES_LIMIT"
                          class="sp-footnote"
                        >
                          {{
                            FormattingService.formatNumber(
                              instancesByClass[entry.className].totalInstances
                            )
                          }}
                          total instances of this class
                        </div>
                      </div>

                      <!-- Right scrollable list -->
                      <div class="split-right">
                        <div
                          v-for="(inst, idx) in instancesByClass[entry.className].instances"
                          :key="inst.objectId"
                          class="sp-list-row"
                        >
                          <span class="spr-rank">{{
                            String(idx + 1).padStart(2, '0')
                          }}</span>
                          <span class="spr-id">{{
                            FormattingService.formatObjectId(inst.objectId)
                          }}</span>
                          <span
                            class="spr-preview"
                            :title="inst.contentPreview ?? ''"
                            >{{ inst.contentPreview ?? '' }}</span
                          >
                          <span class="spr-bar">
                            <span
                              class="spr-fill"
                              :style="{
                                width: getInstanceBarPercent(entry.className, inst) + '%'
                              }"
                            ></span>
                          </span>
                          <span class="spr-retained">{{
                            inst.retainedSize !== null
                              ? FormattingService.formatBytes(inst.retainedSize)
                              : '—'
                          }}</span>
                          <span class="spr-actions">
                            <InstanceActionButtons
                              :object-id="inst.objectId"
                              :show-instance-detail="true"
                              @show-referrers="openTreeModal($event, 'REFERRERS')"
                              @show-reachables="openTreeModal($event, 'REACHABLES')"
                              @show-g-c-root-path="openGCRootPathModal"
                              @show-instance-detail="openInstanceDetailPanel"
                            />
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
              <tr
                v-else-if="
                  expandedClass === entry.className &&
                  instancesByClass[entry.className] &&
                  instancesByClass[entry.className].instances.length === 0
                "
                class="drawer-row"
              >
                <td colspan="5">
                  <div class="drawer-panel drawer-empty">
                    No instances available for this class.
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
    </DataTable>

    <!-- Instance Tree Modal -->
    <InstanceTreeModal
      :show="treeModalVisible"
      :object-id="treeModalObjectId"
      :initial-mode="treeModalMode"
      :profile-id="profileId"
      @update:show="treeModalVisible = $event"
    />

    <!-- Instance Details Side Panel -->
    <InstanceDetailPanel
      v-if="client"
      :is-open="detailPanelOpen"
      :object-id="detailPanelObjectId"
      :client="client"
      @close="detailPanelOpen = false"
      @navigate="detailPanelObjectId = $event"
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
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import EmptyState from '@/components/EmptyState.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import ClassHistogramEntry from '@/services/api/model/ClassHistogramEntry';
import type ClassInstancesResponse from '@/services/api/model/ClassInstancesResponse';
import type { ClassInstanceEntry } from '@/services/api/model/ClassInstancesResponse';
import HeapSummary from '@/services/api/model/HeapSummary';
import FormattingService from '@/services/FormattingService';

const TOP_INSTANCES_LIMIT = 20;

const route = useRoute();
const router = useRouter();

const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const histogramData = ref<ClassHistogramEntry[]>([]);
const summary = ref<HeapSummary | null>(null);
const histogramTopN = ref(50);
const histogramSortBy = ref('SIZE');

// Expansion state — only one class expanded at a time keeps the page compact.
const expandedClass = ref<string | null>(null);
const instancesLoading = ref<string | null>(null);
const instancesError = ref<string | null>(null);
const instancesByClass = ref<Record<string, ClassInstancesResponse>>({});

// Tree modal state (referrers / reachables)
const treeModalVisible = ref(false);
const treeModalObjectId = ref(0);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

// Instance detail side-panel state
const detailPanelOpen = ref(false);
const detailPanelObjectId = ref<number | null>(null);

let client: HeapDumpClient;

// Computed metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!summary.value) {
    return [];
  }
  return [
    {
      icon: 'layers',
      title: 'Total Classes',
      value: FormattingService.formatNumber(summary.value.classCount),
      variant: 'highlight' as const
    },
    {
      icon: 'collection',
      title: 'Total Instances',
      value: FormattingService.formatNumber(summary.value.totalInstances),
      variant: 'info' as const
    },
    {
      icon: 'hdd',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(summary.value.totalBytes),
      variant: 'info' as const
    },
    {
      icon: 'diagram-3',
      title: 'GC Roots',
      value: FormattingService.formatNumber(summary.value.gcRootCount),
      variant: 'success' as const
    }
  ];
});

// Computed max value for percentage calculation based on sort field
const maxValue = computed(() => {
  if (histogramData.value.length === 0) {
    return 0;
  }

  if (histogramSortBy.value === 'COUNT') {
    return Math.max(...histogramData.value.map(entry => entry.instanceCount));
  }
  return Math.max(...histogramData.value.map(entry => entry.totalSize));
});

// Calculate distribution percentage based on sort field
const getDistributionPercentage = (entry: ClassHistogramEntry): number => {
  if (maxValue.value === 0) {
    return 0;
  }

  if (histogramSortBy.value === 'COUNT') {
    return (entry.instanceCount / maxValue.value) * 100;
  }
  return (entry.totalSize / maxValue.value) * 100;
};

const loadHistogram = async () => {
  try {
    histogramData.value = await client.getHistogram(histogramTopN.value, histogramSortBy.value);
    // Collapse any expansion that no longer matches a row.
    if (expandedClass.value && !histogramData.value.some(e => e.className === expandedClass.value)) {
      expandedClass.value = null;
    }
  } catch (err) {
    console.error('Error loading histogram:', err);
  }
};

const handleSort = (column: string) => {
  histogramSortBy.value = column;
  loadHistogram();
};

const toggleExpand = async (entry: ClassHistogramEntry) => {
  if (expandedClass.value === entry.className) {
    expandedClass.value = null;
    instancesError.value = null;
    return;
  }

  expandedClass.value = entry.className;
  instancesError.value = null;

  if (instancesByClass.value[entry.className]) {
    return;
  }

  instancesLoading.value = entry.className;
  try {
    const response = await client.getClassInstances(
      entry.className,
      TOP_INSTANCES_LIMIT,
      0,
      true,
      'RETAINED_SIZE'
    );
    instancesByClass.value[entry.className] = response;
  } catch (err) {
    instancesError.value =
      err instanceof Error ? err.message : 'Failed to load instances for this class.';
    console.error('Error loading class instances:', err);
  } finally {
    instancesLoading.value = null;
  }
};

const getTopRetainedTotal = (className: string): number => {
  const response = instancesByClass.value[className];
  if (!response) {
    return 0;
  }
  return response.instances.reduce((sum, inst) => sum + (inst.retainedSize ?? 0), 0);
};

const getTopRetainedPercent = (entry: ClassHistogramEntry): number => {
  if (entry.totalSize === 0) {
    return 0;
  }
  return (getTopRetainedTotal(entry.className) / entry.totalSize) * 100;
};

const getMaxRetained = (className: string): number => {
  const response = instancesByClass.value[className];
  if (!response || response.instances.length === 0) {
    return 0;
  }
  return response.instances[0].retainedSize ?? 0;
};

const getMinRetained = (className: string): number => {
  const response = instancesByClass.value[className];
  if (!response || response.instances.length === 0) {
    return 0;
  }
  return response.instances[response.instances.length - 1].retainedSize ?? 0;
};

const getInstanceBarPercent = (className: string, inst: ClassInstanceEntry): number => {
  const max = getMaxRetained(className);
  if (max === 0 || inst.retainedSize === null) {
    return 0;
  }
  return (inst.retainedSize / max) * 100;
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  treeModalVisible.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const openInstanceDetailPanel = (objectId: number) => {
  detailPanelObjectId.value = objectId;
  detailPanelOpen.value = true;
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

    // Load summary and histogram in parallel
    const [summaryData, histogramResult] = await Promise.all([
      client.getSummary(),
      client.getHistogram(histogramTopN.value, histogramSortBy.value)
    ]);

    summary.value = summaryData;
    histogramData.value = histogramResult;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load class histogram';
    console.error('Error loading class histogram:', err);
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

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.select-narrow {
  width: 80px;
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

/* --- Expandable row chrome --------------------------------------- */
.class-cell {
  display: flex;
  align-items: center;
}

.btn-expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  border-radius: var(--radius-sm);
  margin-right: 0.4rem;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.btn-expand:hover:not(:disabled) {
  background-color: rgba(111, 66, 193, 0.1);
  color: var(--color-purple);
}

.btn-expand:disabled {
  opacity: 0.6;
  cursor: wait;
}

.btn-expand i {
  font-size: 0.7rem;
}

.spinner-inline {
  width: 12px;
  height: 12px;
  border-width: 1.5px;
}

tr.row-expanded > td {
  background-color: var(--color-bg-hover-alt);
}

/* --- Drawer ------------------------------------------------------- */
.drawer-row > td {
  background-color: var(--color-bg-hover-alt);
  padding: 0;
}

.drawer-panel {
  padding: 1rem 1.5rem 1.1rem 3rem;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.drawer-panel.drawer-error {
  color: var(--color-danger);
  font-size: 0.85rem;
}

.drawer-panel.drawer-empty {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  text-align: center;
}

.drawer-title {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
  margin: 0 0 0.65rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sort-hint {
  text-transform: none;
  letter-spacing: 0;
  font-weight: var(--font-weight-normal);
  font-size: 0.7rem;
  color: var(--color-text-light);
}

.split-panel {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 1.25rem;
}

.split-left {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 1rem 1.1rem;
}

.sp-stat-label {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
}

.sp-stat-value {
  font-size: 1.15rem;
  font-weight: var(--font-weight-bold);
  font-family: monospace;
  color: var(--color-dark);
  margin-bottom: 0.85rem;
}

.sparkline {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 56px;
  margin: 0.65rem 0 0.4rem;
}

.sparkline .sb {
  flex: 1;
  background: linear-gradient(180deg, var(--color-primary), rgba(94, 100, 255, 0.4));
  border-radius: 1px;
  min-height: 4px;
}

.sp-caption {
  font-size: 0.68rem;
  color: var(--color-text-muted);
  display: flex;
  justify-content: space-between;
}

.sp-footnote {
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--color-border-light);
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.split-right {
  max-height: 360px;
  overflow-y: auto;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
}

.sp-list-row {
  display: grid;
  grid-template-columns: 28px 130px minmax(0, 1fr) 120px 80px auto;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.95rem;
  border-bottom: 1px solid var(--color-border-row);
  font-size: 0.78rem;
}

.sp-list-row:last-child {
  border-bottom: none;
}

.sp-list-row:hover {
  background-color: var(--color-bg-hover);
}

.spr-rank {
  color: var(--color-text-light);
  font-family: monospace;
}

.spr-id {
  font-family: monospace;
  color: var(--color-text);
}

.spr-preview {
  font-family: monospace;
  font-size: 0.78rem;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.spr-bar {
  height: 5px;
  background-color: var(--color-light);
  border-radius: 2px;
  overflow: hidden;
}

.spr-fill {
  display: block;
  height: 100%;
  background-color: var(--color-primary);
  border-radius: 2px;
}

.spr-retained {
  font-family: monospace;
  text-align: right;
  color: var(--color-goldenrod);
}

.spr-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

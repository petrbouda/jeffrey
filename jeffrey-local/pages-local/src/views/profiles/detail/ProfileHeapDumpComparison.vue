<template>
  <LoadingState v-if="loading" message="Loading heap dump comparison..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="arrow-left-right"
      message="The heap dump needs to be initialized before you can compare histograms. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Heap Dump Comparison"
        description="Compare class histograms between two profiles"
        icon="bi-arrow-left-right"
    />

    <!-- Comparison Input -->
    <div v-if="!comparisonResult" class="comparison-input-section">
      <div class="card">
        <div class="card-body">
          <h6 class="card-title mb-3">
            <i class="bi bi-sliders me-2"></i>
            Configure Comparison
          </h6>
          <p class="text-muted small mb-3">
            Enter the profile ID of the baseline heap dump to compare against the current profile's histogram.
            The comparison will show which classes grew, shrank, appeared, or were removed.
          </p>
          <div class="row align-items-end">
            <div class="col-md-8">
              <label class="form-label">Baseline Profile ID</label>
              <input
                  v-model="baselineProfileId"
                  type="text"
                  class="form-control"
                  placeholder="Enter baseline profile ID..."
                  :disabled="comparing"
                  @keyup.enter="runComparison"
              />
            </div>
            <div class="col-md-4">
              <button
                  class="btn btn-primary w-100"
                  :disabled="!baselineProfileId.trim() || comparing"
                  @click="runComparison"
              >
                <span v-if="comparing">
                  <span class="spinner-border spinner-border-sm me-1" role="status"></span>
                  Comparing...
                </span>
                <span v-else>
                  <i class="bi bi-arrow-left-right me-1"></i>
                  Compare
                </span>
              </button>
            </div>
          </div>
          <div v-if="comparisonError" class="alert alert-danger mt-3 mb-0">
            <i class="bi bi-exclamation-triangle me-2"></i>
            {{ comparisonError }}
          </div>
        </div>
      </div>
    </div>

    <!-- Comparison Results -->
    <div v-if="comparisonResult">
      <!-- Summary Metrics -->
      <StatsTable :metrics="summaryMetrics" class="mb-4" />

      <!-- Filter Controls -->
      <div class="filter-controls mb-3">
        <div class="row align-items-center">
          <div class="col-auto">
            <label class="form-label mb-0 me-2">Status:</label>
            <select v-model="statusFilter" class="form-select form-select-sm d-inline-block select-status">
              <option value="ALL">All</option>
              <option value="GREW">Grew Only</option>
              <option value="SHRANK">Shrank Only</option>
              <option value="NEW">New Only</option>
              <option value="REMOVED">Removed Only</option>
            </select>
          </div>
          <div class="col-auto">
            <button class="btn btn-outline-secondary btn-sm" @click="resetComparison">
              <i class="bi bi-arrow-counterclockwise me-1"></i>
              New Comparison
            </button>
          </div>
          <div class="col-auto ms-auto">
            <small class="text-muted">Showing {{ filteredEntries.length }} of {{ comparisonResult.entries.length }} classes</small>
          </div>
        </div>
      </div>

      <!-- Data Table -->
      <div class="table-card">
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
            <tr>
              <th style="width: 50px;">#</th>
              <th>Class Name</th>
              <th class="text-end" style="width: 120px;">Baseline Size</th>
              <th class="text-end" style="width: 120px;">Current Size</th>
              <SortableTableHeader
                  column="sizeDelta"
                  label="Size Delta"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="120px"
                  @sort="toggleSort"
              />
              <th class="text-end" style="width: 110px;">Baseline Count</th>
              <th class="text-end" style="width: 110px;">Current Count</th>
              <SortableTableHeader
                  column="countDelta"
                  label="Count Delta"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="110px"
                  @sort="toggleSort"
              />
              <th style="width: 100px;">Status</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(entry, index) in filteredEntries" :key="entry.className">
              <td class="text-muted">{{ index + 1 }}</td>
              <td>
                <div class="class-info">
                  <code class="class-name">{{ simpleClassName(entry.className) }}</code>
                  <span class="package-name">{{ packageName(entry.className) }}</span>
                </div>
              </td>
              <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.baselineSize) }}</td>
              <td class="text-end font-monospace">{{ FormattingService.formatBytes(entry.currentSize) }}</td>
              <td class="text-end font-monospace" :class="deltaClass(entry.sizeDelta)">
                {{ formatDelta(entry.sizeDelta, true) }}
              </td>
              <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.baselineCount) }}</td>
              <td class="text-end font-monospace">{{ FormattingService.formatNumber(entry.currentCount) }}</td>
              <td class="text-end font-monospace" :class="deltaClass(entry.countDelta)">
                {{ formatCountDelta(entry.countDelta) }}
              </td>
              <td>
                <span class="badge" :class="statusBadgeClass(entry.status)">{{ entry.status }}</span>
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
import type { HeapDumpComparisonReport } from '@/services/api/model/HeapDumpComparisonReport';
import FormattingService from '@/services/FormattingService';

const route = useRoute();

const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);

const baselineProfileId = ref('');
const comparing = ref(false);
const comparisonError = ref<string | null>(null);
const comparisonResult = ref<HeapDumpComparisonReport | null>(null);

const statusFilter = ref('ALL');
const sortColumn = ref('sizeDelta');
const sortDirection = ref<'asc' | 'desc'>('desc');

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  if (!comparisonResult.value) return [];
  const r = comparisonResult.value;
  return [
    {
      icon: 'hdd',
      title: 'Baseline Total',
      value: FormattingService.formatBytes(r.baselineTotalBytes),
      variant: 'info' as const
    },
    {
      icon: 'hdd-fill',
      title: 'Current Total',
      value: FormattingService.formatBytes(r.currentTotalBytes),
      variant: 'info' as const
    },
    {
      icon: 'arrow-up-down',
      title: 'Delta',
      value: formatDelta(r.totalBytesDelta, true),
      variant: r.totalBytesDelta > 0 ? 'danger' as const : r.totalBytesDelta < 0 ? 'success' as const : 'info' as const
    },
    {
      icon: 'layers',
      title: 'Baseline Classes',
      value: FormattingService.formatNumber(r.baselineClassCount),
      variant: 'highlight' as const
    },
    {
      icon: 'layers-fill',
      title: 'Current Classes',
      value: FormattingService.formatNumber(r.currentClassCount),
      variant: 'highlight' as const
    }
  ];
});

const filteredEntries = computed(() => {
  if (!comparisonResult.value) return [];
  let entries = [...comparisonResult.value.entries];

  // Filter by status
  if (statusFilter.value !== 'ALL') {
    entries = entries.filter(e => e.status === statusFilter.value);
  }

  // Sort
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  switch (sortColumn.value) {
    case 'sizeDelta':
      entries.sort((a, b) => direction * (Math.abs(a.sizeDelta) - Math.abs(b.sizeDelta)));
      break;
    case 'countDelta':
      entries.sort((a, b) => direction * (Math.abs(a.countDelta) - Math.abs(b.countDelta)));
      break;
  }

  return entries;
});

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = 'desc';
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

const formatDelta = (delta: number, asBytes: boolean): string => {
  if (delta === 0) return '0';
  const prefix = delta > 0 ? '+' : '';
  if (asBytes) {
    return prefix + FormattingService.formatBytes(Math.abs(delta));
  }
  return prefix + FormattingService.formatNumber(delta);
};

const formatCountDelta = (delta: number): string => {
  if (delta === 0) return '0';
  const prefix = delta > 0 ? '+' : '-';
  return prefix + FormattingService.formatNumber(Math.abs(delta));
};

const deltaClass = (delta: number): string => {
  if (delta > 0) return 'text-danger';
  if (delta < 0) return 'text-success';
  return '';
};

const statusBadgeClass = (status: string): string => {
  switch (status) {
    case 'GREW': return 'bg-danger';
    case 'SHRANK': return 'bg-success';
    case 'NEW': return 'bg-primary';
    case 'REMOVED': return 'bg-secondary';
    case 'UNCHANGED': return 'bg-light text-dark';
    default: return 'bg-light text-dark';
  }
};

const runComparison = async () => {
  if (!baselineProfileId.value.trim()) return;

  try {
    comparing.value = true;
    comparisonError.value = null;

    const baselineClient = new HeapDumpClient(baselineProfileId.value.trim());

    // Load current and baseline histograms and summaries in parallel
    const [currentHistogram, currentSummary, baselineHistogram, baselineSummary] = await Promise.all([
      client.getHistogram(500),
      client.getSummary(),
      baselineClient.getHistogram(500),
      baselineClient.getSummary()
    ]);

    // Run comparison
    comparisonResult.value = await client.compareHistograms({
      baseline: baselineHistogram,
      current: currentHistogram,
      baselineTotalBytes: baselineSummary.totalBytes,
      currentTotalBytes: currentSummary.totalBytes
    });

  } catch (err) {
    comparisonError.value = err instanceof Error ? err.message : 'Failed to compare histograms. Verify the baseline profile ID is correct and its heap dump is initialized.';
    console.error('Error comparing histograms:', err);
  } finally {
    comparing.value = false;
  }
};

const resetComparison = () => {
  comparisonResult.value = null;
  comparisonError.value = null;
  statusFilter.value = 'ALL';
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

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load heap dump comparison';
    console.error('Error loading heap dump comparison:', err);
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

.comparison-input-section {
  margin-bottom: 1.5rem;
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
  color: #495057;
  white-space: nowrap;
}

.package-name {
  font-size: 0.8rem;
  color: #868e96;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.table thead th {
  background-color: #fafbfc;
  font-weight: 600;
  color: #495057;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid #e9ecef;
}

.table td {
  font-size: 0.8rem;
  padding: 0.6rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.filter-controls {
  background-color: #f8f9fa;
  padding: 0.75rem 1rem;
  border: 1px solid #dee2e6;
}

.select-status {
  width: 150px;
}

.font-monospace {
  font-size: 0.8rem;
}

.badge {
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.card {
  border: 1px solid #dee2e6;
}

.card-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #495057;
}
</style>

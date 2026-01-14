<template>
  <LoadingState v-if="loading" message="Loading thread information..." />

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
      icon="cpu"
      message="The heap dump needs to be initialized before you can view thread information. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Threads"
        description="Thread objects captured in the heap dump"
        icon="bi-cpu"
    />

    <div v-if="threadsData && threadsData.length > 0">
      <!-- Summary Metrics -->
      <StatsTable :metrics="summaryMetrics" class="mb-4" />

      <!-- Results Toolbar -->
      <div class="table-card">
        <div class="results-toolbar">
          <div class="results-info">
            <span class="results-count">{{ filteredThreads.length }} threads</span>
            <span v-if="filteredThreads.length !== threadsData.length" class="filtered-badge">
              filtered from {{ threadsData.length }}
            </span>
          </div>
          <div class="results-controls">
            <input
                type="text"
                v-model="searchQuery"
                class="form-control form-control-sm filter-input"
                placeholder="Filter..."
            />
            <select v-model="daemonFilter" class="form-select form-select-sm type-select">
              <option value="all">All Threads</option>
              <option value="daemon">Daemon Only</option>
              <option value="non-daemon">Non-Daemon Only</option>
            </select>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
            <tr>
              <th style="width: 50px;">#</th>
              <SortableTableHeader
                  column="name"
                  label="Thread"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  @sort="toggleSort"
              />
              <SortableTableHeader
                  v-if="hasRetainedSize"
                  column="retained"
                  label="Retained Size"
                  :sort-column="sortColumn"
                  :sort-direction="sortDirection"
                  align="end"
                  width="140px"
                  @sort="toggleSort"
              />
            </tr>
            </thead>
            <tbody>
            <tr v-for="(thread, index) in filteredThreads" :key="thread.objectId">
              <td class="text-muted">{{ index + 1 }}</td>
              <td class="thread-cell">
                <div class="thread-header">
                  <span class="thread-name">{{ thread.name }}</span>
                  <InstanceActionButtons
                      :object-id="thread.objectId"
                      @show-referrers="openTreeModal($event, 'REFERRERS')"
                      @show-reachables="openTreeModal($event, 'REACHABLES')"
                  />
                </div>
                <div class="thread-meta">
                  <span class="meta-label">{{ thread.daemon ? 'Daemon' : 'Non-Daemon' }}</span>
                  <span class="meta-separator">•</span>
                  <span class="meta-label" :class="'priority-' + getPriorityClass(thread.priority)">
                    Priority {{ thread.priority }}
                  </span>
                </div>
              </td>
              <td v-if="hasRetainedSize" class="text-end align-middle">
                <span class="retained-size">
                  {{ thread.retainedSize != null ? FormattingService.formatBytes(thread.retainedSize) : '-' }}
                </span>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <div class="text-center py-5">
        <i class="bi bi-cpu text-muted" style="font-size: 3rem;"></i>
        <p class="text-muted mt-3 mb-0">No thread information available in this heap dump.</p>
      </div>
    </div>

    <!-- Instance Tree Modal -->
    <InstanceTreeModal
        v-if="workspaceId && projectId && selectedObjectId !== null"
        v-model:show="showTreeModal"
        :object-id="selectedObjectId"
        :initial-mode="treeMode"
        :workspace-id="workspaceId"
        :project-id="projectId"
        :profile-id="profileId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapThreadInfo from '@/services/api/model/HeapThreadInfo';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const threadsData = ref<HeapThreadInfo[]>([]);
const totalRetainedSize = ref<number>(0);
const searchQuery = ref('');
const daemonFilter = ref('all');
const sortColumn = ref('retained');
const sortDirection = ref<'asc' | 'desc'>('desc');
const showTreeModal = ref(false);
const selectedObjectId = ref<number | null>(null);
const treeMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

let client: HeapDumpClient;

// Check if any thread has retained size
const hasRetainedSize = computed(() =>
    threadsData.value.some(t => t.retainedSize != null)
);

// Computed counts
const daemonCount = computed(() => threadsData.value.filter(t => t.daemon).length);
const nonDaemonCount = computed(() => threadsData.value.filter(t => !t.daemon).length);
const lowPriorityCount = computed(() => threadsData.value.filter(t => t.priority <= 5).length);
const highPriorityCount = computed(() => threadsData.value.filter(t => t.priority > 5).length);
const highestPriority = computed(() => {
  if (threadsData.value.length === 0) return 0;
  return Math.max(...threadsData.value.map(t => t.priority));
});

// Computed metrics for StatsTable
const summaryMetrics = computed(() => [
  {
    icon: 'cpu',
    title: 'Total Threads',
    value: threadsData.value.length.toString(),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Daemon', value: daemonCount.value, color: '#6c757d' },
      { label: 'Non-Daemon', value: nonDaemonCount.value, color: '#4285F4' }
    ]
  },
  {
    icon: 'lightning',
    title: 'Highest Priority',
    value: highestPriority.value.toString(),
    variant: 'info' as const,
    breakdown: [
      { label: 'Less Equal 5', value: lowPriorityCount.value, color: '#6c757d' },
      { label: 'Higher 5', value: highPriorityCount.value, color: '#fd7e14' }
    ]
  }
]);

const filteredThreads = computed(() => {
  let result = [...threadsData.value];

  // Search filter
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(t => t.name.toLowerCase().includes(query));
  }

  // Daemon filter
  if (daemonFilter.value === 'daemon') {
    result = result.filter(t => t.daemon);
  } else if (daemonFilter.value === 'non-daemon') {
    result = result.filter(t => !t.daemon);
  }

  // Sorting
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  if (sortColumn.value === 'retained') {
    result.sort((a, b) => direction * ((a.retainedSize ?? 0) - (b.retainedSize ?? 0)));
  } else {
    result.sort((a, b) => direction * a.name.localeCompare(b.name));
  }

  return result;
});

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = 'desc';
  }
};

const getPriorityClass = (priority: number): string => {
  if (priority >= 7) return 'high';
  if (priority >= 5) return 'normal';
  return 'low';
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  selectedObjectId.value = objectId;
  treeMode.value = mode;
  showTreeModal.value = true;
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(workspaceId.value, projectId.value, profileId);

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

    // Load from pre-computed analysis (created during heap dump initialization)
    const report = await client.getThreadAnalysis();
    if (report) {
      threadsData.value = report.threads;
      totalRetainedSize.value = report.totalRetainedSize;
    }

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load thread information';
    console.error('Error loading thread information:', err);
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

/* Table Card */
.table-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

/* Results Toolbar - matching OQL style */
.results-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.results-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.results-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.results-count {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6c757d;
  background-color: #e9ecef;
  padding: 0.125rem 0.5rem;
}

.filtered-badge {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #856404;
  background-color: #fff3cd;
  padding: 0.125rem 0.375rem;
}

.filter-input {
  width: 140px;
}

.type-select {
  width: 130px;
}

/* Table Styles - matching OQL */
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
  background-color: rgba(66, 133, 244, 0.02);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

/* Thread Cell - Two-line layout */
.thread-cell {
  padding: 0.75rem !important;
}

.thread-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.thread-name {
  font-size: 0.8rem;
  font-weight: 500;
  color: #6f42c1;
  word-break: break-all;
  line-height: 1.4;
}

.thread-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.3rem;
}

.meta-label {
  font-size: 0.7rem;
  font-weight: 500;
  color: #6c757d;
}

.meta-label.priority-high {
  color: #dc3545;
}

.meta-label.priority-normal {
  color: #856404;
}

.meta-label.priority-low {
  color: #6c757d;
}

.meta-separator {
  color: #dee2e6;
  font-size: 0.5rem;
}

.retained-size {
  font-size: 0.8rem;
}

.empty-state {
  background: white;
  border: 1px solid #dee2e6;
}
</style>

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

      <!-- Filter Controls -->
      <div class="filter-controls mb-3">
        <div class="row align-items-center g-3">
          <div class="col-auto">
            <div class="input-group input-group-sm">
              <span class="input-group-text"><i class="bi bi-search"></i></span>
              <input
                  type="text"
                  v-model="searchQuery"
                  class="form-control"
                  placeholder="Search thread name..."
                  style="min-width: 220px;"
              />
            </div>
          </div>
          <div class="col-auto">
            <select v-model="daemonFilter" class="form-select form-select-sm">
              <option value="all">All Threads</option>
              <option value="daemon">Daemon Only</option>
              <option value="non-daemon">Non-Daemon Only</option>
            </select>
          </div>
          <div class="col-auto">
            <select v-model="priorityFilter" class="form-select form-select-sm">
              <option value="all">All Priorities</option>
              <option value="high">High (7-10)</option>
              <option value="normal">Normal (5-6)</option>
              <option value="low">Low (1-4)</option>
            </select>
          </div>
          <div class="col-auto ms-auto">
            <small class="text-muted">
              <i class="bi bi-funnel me-1"></i>
              Showing {{ filteredThreads.length }} of {{ threadsData.length }} threads
            </small>
          </div>
        </div>
      </div>

      <!-- Threads Table -->
      <div class="table-card">
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
            <tr>
              <th style="width: 50px;">#</th>
              <th>Thread Name</th>
              <th class="text-center" style="width: 100px;">Type</th>
              <th class="text-center" style="width: 100px;">Priority</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(thread, index) in filteredThreads" :key="thread.objectId">
              <td class="text-muted">{{ index + 1 }}</td>
              <td>
                <div class="d-flex align-items-center">
                  <span
                      class="thread-indicator"
                      :class="thread.daemon ? 'daemon' : 'user'"
                  ></span>
                  <span class="thread-name">{{ thread.name }}</span>
                </div>
              </td>
              <td class="text-center">
                <Badge
                    :value="thread.daemon ? 'Daemon' : 'User'"
                    :variant="thread.daemon ? 'secondary' : 'primary'"
                    size="s"
                />
              </td>
              <td class="text-center">
                <span class="priority-badge" :class="getPriorityClass(thread.priority)">
                  {{ thread.priority }}
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
import Badge from '@/components/Badge.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapThreadInfo from '@/services/api/model/HeapThreadInfo';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const threadsData = ref<HeapThreadInfo[]>([]);
const searchQuery = ref('');
const daemonFilter = ref('all');
const priorityFilter = ref('all');

let client: HeapDumpClient;

// Computed counts
const daemonCount = computed(() => threadsData.value.filter(t => t.daemon).length);
const nonDaemonCount = computed(() => threadsData.value.filter(t => !t.daemon).length);
const highPriorityCount = computed(() => threadsData.value.filter(t => t.priority >= 7).length);

// Computed metrics for StatsTable
const summaryMetrics = computed(() => [
  {
    icon: 'cpu',
    title: 'Total Threads',
    value: threadsData.value.length.toString(),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Daemon', value: daemonCount.value, color: '#6c757d' },
      { label: 'User', value: nonDaemonCount.value, color: '#4285F4' }
    ]
  },
  {
    icon: 'gear',
    title: 'Daemon Threads',
    value: daemonCount.value.toString(),
    variant: 'info' as const
  },
  {
    icon: 'person',
    title: 'User Threads',
    value: nonDaemonCount.value.toString(),
    variant: 'success' as const
  },
  {
    icon: 'lightning',
    title: 'High Priority',
    value: highPriorityCount.value.toString(),
    variant: 'warning' as const
  }
]);

const filteredThreads = computed(() => {
  let result = threadsData.value;

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

  // Priority filter
  if (priorityFilter.value === 'high') {
    result = result.filter(t => t.priority >= 7);
  } else if (priorityFilter.value === 'normal') {
    result = result.filter(t => t.priority >= 5 && t.priority < 7);
  } else if (priorityFilter.value === 'low') {
    result = result.filter(t => t.priority < 5);
  }

  return result;
});

const getPriorityClass = (priority: number): string => {
  if (priority >= 7) return 'high';
  if (priority >= 5) return 'normal';
  return 'low';
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

    threadsData.value = await client.getThreads();

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

.filter-controls {
  background-color: #f8f9fa;
  padding: 0.875rem 1rem;
  border: 1px solid #dee2e6;
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
  font-size: 0.85rem;
  padding: 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.thread-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 0.5rem;
  flex-shrink: 0;
}

.thread-indicator.daemon {
  background-color: #6c757d;
}

.thread-indicator.user {
  background-color: #4285F4;
}

.thread-name {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.8rem;
  word-break: break-all;
}

.priority-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 22px;
  padding: 0 8px;
  font-size: 0.75rem;
  font-weight: 600;
}

.priority-badge.high {
  background-color: #fff5f5;
  color: #dc3545;
  border: 1px solid #f5c2c7;
}

.priority-badge.normal {
  background-color: #fff8e6;
  color: #856404;
  border: 1px solid #ffc107;
}

.priority-badge.low {
  background-color: #f8f9fa;
  color: #6c757d;
  border: 1px solid #dee2e6;
}

.empty-state {
  background: white;
  border: 1px solid #dee2e6;
}
</style>

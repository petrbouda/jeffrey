<template>
  <LoadingState v-if="loading" message="Loading OQL query interface..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="OQL Query"
        description="Execute Object Query Language queries against the heap dump"
        icon="bi-terminal">
      <template #actions>
        <button
            class="btn btn-sm"
            :class="showExamples ? 'btn-purple' : 'btn-outline-purple'"
            @click="showExamples = !showExamples"
        >
          <i class="bi bi-lightbulb me-1"></i>
          Examples
        </button>
      </template>
    </PageHeader>

    <!-- Example Queries Section (Collapsible) -->
    <div v-if="showExamples" class="examples-card mb-4">
      <div class="examples-header">
        <h6 class="mb-0"><i class="bi bi-code-square me-2"></i>Example Queries</h6>
      </div>
      <div class="examples-body">
        <div class="examples-list">
          <template v-for="(example, index) in exampleQueries" :key="index">
            <div v-if="example.divider" class="example-divider">
              {{ example.title.replace(/---/g, '').trim() }}
            </div>
            <div v-else class="example-item" @click="useExample(example.query)">
              <div class="example-title">{{ example.title }}</div>
              <code class="example-query">{{ example.query }}</code>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- Query Input Section -->
    <div class="query-editor mb-4">
      <textarea
          v-model="oqlQuery"
          class="query-input"
          rows="3"
          placeholder="select s from java.lang.String s where s.value.length > 100"
          @keydown.ctrl.enter="executeQuery"
          @keydown.meta.enter="executeQuery"
      ></textarea>
      <div class="query-toolbar">
        <div class="d-flex align-items-center gap-2">
          <button class="btn btn-sm btn-primary" @click="executeQuery" :disabled="oqlLoading || !oqlQuery.trim()">
            <span v-if="oqlLoading" class="spinner-border spinner-border-sm me-1"></span>
            <i v-else class="bi bi-play-fill me-1"></i>
            Execute
          </button>
          <button class="btn btn-sm btn-outline-secondary" @click="clearResults" :disabled="!oqlResult && !oqlError">
            <i class="bi bi-x-lg me-1"></i>
            Clear
          </button>
        </div>
        <div class="d-flex align-items-center gap-3">
          <div class="form-check form-check-inline mb-0">
            <input type="checkbox" class="form-check-input" id="retainedSizeCheck" v-model="includeRetainedSize" />
            <label class="form-check-label small" for="retainedSizeCheck">Retained Size</label>
          </div>
          <div class="d-flex align-items-center gap-2">
            <label class="form-label mb-0 small">Limit:</label>
            <select v-model="oqlLimit" class="form-select form-select-sm select-narrow">
              <option :value="50">50</option>
              <option :value="100">100</option>
              <option :value="500">500</option>
              <option :value="1000">1000</option>
            </select>
          </div>
        </div>
      </div>
    </div>

    <!-- Error Display -->
    <div v-if="oqlError" class="alert alert-danger d-flex align-items-start mb-4">
      <i class="bi bi-exclamation-triangle-fill me-2 mt-1"></i>
      <div>
        <strong>Query Error</strong>
        <p class="mb-0 mt-1">{{ oqlError }}</p>
      </div>
    </div>

    <!-- Results Section -->
    <div v-if="oqlResult" class="results-section">
      <!-- Results Table -->
      <div class="table-card">
        <div class="results-toolbar">
          <div class="results-info">
            <span class="results-count">{{ filteredResults.length }} of {{ oqlResult.totalCount }}</span>
            <span v-if="oqlResult.hasMore" class="truncated-badge">truncated</span>
            <span class="meta-item"><i class="bi bi-stopwatch me-1"></i>{{ oqlResult.executionTimeMs }}ms</span>
          </div>
          <div class="results-controls">
            <input
                type="text"
                v-model="resultFilter"
                class="form-control form-control-sm filter-input"
                placeholder="Filter..."
            />
            <select v-model="resultSort" class="form-select form-select-sm sort-select">
              <option value="retained-desc">Retained &nbsp;↓</option>
              <option value="retained-asc">Retained &nbsp;↑</option>
              <option value="size-desc">Size &nbsp;↓</option>
              <option value="size-asc">Size &nbsp;↑</option>
            </select>
          </div>
        </div>
        <div class="results-list">
          <div class="results-header-row">
            <div class="col-index">#</div>
            <div class="col-content">Object</div>
            <div class="col-size">Size</div>
            <div v-if="hasRetainedSize" class="col-retained">Retained</div>
          </div>
          <div v-for="(entry, index) in filteredResults" :key="index" class="result-row">
            <div class="col-index">{{ index + 1 }}</div>
            <div class="col-content">
              <code v-if="entry.className">{{ entry.className }}</code>
              <span class="value" :title="entry.value">{{ truncateValue(entry.value, 300) }}</span>
            </div>
            <div class="col-size">{{ entry.size ? FormattingService.formatBytes(entry.size) : '-' }}</div>
            <div v-if="hasRetainedSize" class="col-retained">{{ entry.retainedSize ? FormattingService.formatBytes(entry.retainedSize) : '-' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!oqlResult && !oqlError && !oqlLoading" class="empty-state">
      <div class="text-center py-5">
        <i class="bi bi-terminal text-muted" style="font-size: 3rem;"></i>
        <p class="text-muted mt-3 mb-0">Enter an OQL query above and click Execute to see results.</p>
        <button class="btn btn-outline-primary btn-sm mt-3" @click="showExamples = true" v-if="!showExamples">
          <i class="bi bi-lightbulb me-1"></i>
          Show Example Queries
        </button>
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
import HeapDumpClient from '@/services/api/HeapDumpClient';
import OQLQueryResult from '@/services/api/model/OQLQueryResult';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);

const oqlQuery = ref('');
const oqlLimit = ref(50);
const oqlLoading = ref(false);
const oqlResult = ref<OQLQueryResult | null>(null);
const oqlError = ref<string | null>(null);
const showExamples = ref(false);
const includeRetainedSize = ref(true);
const resultFilter = ref('');
const resultSort = ref('retained-desc');

let client: HeapDumpClient;

// Example queries organized by function category
const exampleQueries = [
  // Object/Size Functions
  { title: '--- Object/Size Functions ---', query: '', divider: true },
  { title: 'sizeof() - Shallow Size >10KB', query: 'select o from instanceof java.lang.Object o where sizeof(o) > 10240' },
  { title: 'rsizeof() - Retained Size >1MB', query: 'select o from instanceof java.lang.Object o where rsizeof(o) > 1048576' },
  { title: 'objectid() - Find by ID', query: 'select heap.findObject(12345)' },
  { title: 'classof() - Get Class Name', query: 'select classof(o).name from instanceof java.lang.Object o where sizeof(o) > 10240' },

  // Reference Chain Functions
  { title: '--- Reference Chain Functions ---', query: '', divider: true },
  { title: 'referrers() - Objects with Many Refs', query: 'select o from instanceof java.lang.Object o where count(referrers(o)) > 10' },
  { title: 'referees() - Referenced Objects', query: 'select referees(m) from java.util.HashMap m where m.size > 100' },
  { title: 'reachables() - All Reachable', query: 'select reachables(t) from java.lang.Thread t' },
  { title: 'root() - Find GC Root', query: 'select root(s) from java.lang.String s where s.toString().contains("Error")' },

  // Heap Functions
  { title: '--- Heap Functions ---', query: '', divider: true },
  { title: 'heap.classes() - All Classes', query: 'select heap.classes()' },
  { title: 'heap.objects() - Class Instances', query: 'select heap.objects("java.lang.String")' },
  { title: 'heap.findClass() - Find Class', query: 'select heap.findClass("java.util.HashMap")' },
  { title: 'heap.roots() - All GC Roots', query: 'select heap.roots()' },

  // Array/Collection Functions
  { title: '--- Array/Collection Functions ---', query: '', divider: true },
  { title: 'count() - Count Instances', query: 'select count(heap.objects("java.lang.String"))' },
  { title: 'length() - Array Length', query: 'select a from byte[] a where length(a) > 10240' },
  { title: 'map() - Transform Results', query: 'select map(heap.objects("java.lang.Thread"), "it.name")' },
  { title: 'filter() - Filter Results', query: 'select filter(heap.objects("java.lang.Thread"), "it.daemon == true")' },
  { title: 'sort() - Sort Results', query: 'select sort(heap.objects("java.lang.String"), "sizeof(it)")' },
  { title: 'unique() - Unique Values', query: 'select unique(map(heap.objects("java.lang.Thread"), "it.threadStatus"))' },

  // Common Use Cases
  { title: '--- Common Use Cases ---', query: '', divider: true },
  { title: 'Strings containing text', query: 'select s from java.lang.String s where s.toString().contains("Exception")' },
  { title: 'Long Strings (>100 chars)', query: 'select s from java.lang.String s where s.value.length > 100' },
  { title: 'Large HashMaps (>100)', query: 'select m from java.util.HashMap m where m.size > 100' },
  { title: 'Empty Collections', query: 'select c from instanceof java.util.Collection c where c.size == 0' },
  { title: 'All Thread Names', query: 'select t.name from java.lang.Thread t' },
  { title: 'Large byte[] Arrays', query: 'select a from byte[] a where a.length > 10240' }
];

// Check if results have retained size data
const hasRetainedSize = computed(() => {
  if (!oqlResult.value || oqlResult.value.results.length === 0) return false;
  return oqlResult.value.results.some(entry => entry.retainedSize !== null);
});

// Filtered and sorted results
const filteredResults = computed(() => {
  if (!oqlResult.value) return [];

  let results = [...oqlResult.value.results];

  // Apply filter
  if (resultFilter.value.trim()) {
    const filter = resultFilter.value.toLowerCase();
    results = results.filter(entry =>
      (entry.className && entry.className.toLowerCase().includes(filter)) ||
      (entry.value && entry.value.toLowerCase().includes(filter))
    );
  }

  // Apply sorting
  if (resultSort.value !== 'none') {
    results.sort((a, b) => {
      switch (resultSort.value) {
        case 'size-asc':
          return (a.size || 0) - (b.size || 0);
        case 'size-desc':
          return (b.size || 0) - (a.size || 0);
        case 'retained-asc':
          return (a.retainedSize || 0) - (b.retainedSize || 0);
        case 'retained-desc':
          return (b.retainedSize || 0) - (a.retainedSize || 0);
        default:
          return 0;
      }
    });
  }

  return results;
});

const useExample = (query: string) => {
  oqlQuery.value = query;
  showExamples.value = false;
};

const truncateValue = (value: string, maxLength: number = 150): string => {
  if (value.length <= maxLength) return value;
  return value.substring(0, maxLength) + '...';
};

const executeQuery = async () => {
  if (!oqlQuery.value.trim()) return;

  oqlLoading.value = true;
  oqlError.value = null;
  oqlResult.value = null;
  resultFilter.value = '';
  resultSort.value = 'retained-desc';

  try {
    const result = await client.executeQuery(oqlQuery.value, oqlLimit.value, 0, includeRetainedSize.value);
    if (result.errorMessage) {
      oqlError.value = result.errorMessage;
    } else {
      oqlResult.value = result;
    }
  } catch (err) {
    oqlError.value = err instanceof Error ? err.message : 'Query execution failed';
  } finally {
    oqlLoading.value = false;
  }
};

const clearResults = () => {
  oqlResult.value = null;
  oqlError.value = null;
};

const loadData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(workspaceId.value, projectId.value, profileId);

    heapExists.value = await client.exists();

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to initialize OQL interface';
    console.error('Error initializing OQL interface:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

/* Examples Card */
.examples-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.examples-header {
  padding: 0.75rem 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.examples-header h6 {
  color: #6f42c1;
  font-size: 0.875rem;
  font-weight: 600;
}

.examples-body {
  padding: 0.75rem;
}

.examples-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}

.example-divider {
  grid-column: 1 / -1;
  font-size: 0.7rem;
  font-weight: 700;
  color: #6f42c1;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 0.5rem 0.5rem 0.25rem;
  border-bottom: 1px solid #e9ecef;
  margin-top: 0.25rem;
}

.example-divider:first-child {
  margin-top: 0;
}

.example-item {
  padding: 0.5rem 0.75rem;
  border: 1px solid #e9ecef;
  cursor: pointer;
  transition: all 0.2s ease;
}

.example-item:hover {
  background-color: #f8f9fa;
  border-color: #6f42c1;
}

.example-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.125rem;
}

.example-query {
  display: block;
  font-size: 0.7rem;
  color: #6c757d;
  background: transparent;
  padding: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Query Editor - Compact Design */
.query-editor {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.query-input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: none;
  border-bottom: 1px solid #dee2e6;
  background-color: #f8f9fa;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.875rem;
  resize: vertical;
  min-height: 80px;
  transition: background-color 0.2s ease;
}

.query-input:focus {
  outline: none;
  background-color: #fff;
  border-bottom: 2px solid #4285F4;
}

.query-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: #fff;
}

.select-narrow {
  width: 80px;
}

/* Table Card */
.table-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

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

.filter-input {
  width: 140px;
}

.sort-select {
  width: 120px;
  padding-right: 1.75rem;
}

.results-count {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6c757d;
  background-color: #e9ecef;
  padding: 0.125rem 0.5rem;
}

.truncated-badge {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #856404;
  background-color: #fff3cd;
  padding: 0.125rem 0.375rem;
}

.results-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.meta-item {
  font-size: 0.75rem;
  color: #6c757d;
}

/* Results List */
.results-list {
  display: grid;
  grid-template-columns: 40px 1fr 80px;
}

.results-list:has(.col-retained) {
  grid-template-columns: 40px 1fr 80px 90px;
}

.results-header-row {
  display: contents;
}

.results-header-row > div {
  padding: 0.5rem 0.75rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.result-row {
  display: contents;
}

.result-row > div {
  padding: 0.625rem 0.75rem;
  font-size: 0.8rem;
  border-bottom: 1px solid #f0f0f0;
}

.result-row:hover > div {
  background-color: #fafbfc;
}

.col-index {
  color: #adb5bd;
  font-size: 0.75rem;
  text-align: right;
  padding-top: 0.75rem;
}

.col-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.col-content code {
  font-size: 0.8rem;
  color: #6f42c1;
  background: transparent;
  word-break: break-all;
  line-height: 1.3;
}

.col-content .value {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.7rem;
  color: #6c757d;
  word-break: break-word;
  line-height: 1.4;
}

.col-size,
.col-retained {
  font-size: 0.8rem;
  color: #495057;
  text-align: right;
  padding-top: 0.75rem;
}

.empty-state {
  background: white;
  border: 1px solid #dee2e6;
}

.btn-purple {
  background-color: #6f42c1;
  border-color: #6f42c1;
  color: white;
}

.btn-purple:hover {
  background-color: #5a32a3;
  border-color: #5a32a3;
  color: white;
}

.btn-outline-purple {
  border-color: #6f42c1;
  color: #6f42c1;
}

.btn-outline-purple:hover {
  background-color: #6f42c1;
  border-color: #6f42c1;
  color: white;
}
</style>

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

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="terminal"
      message="The heap dump needs to be initialized before you can execute OQL queries. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="OQL Query"
        description="Execute Object Query Language queries against the heap dump"
        icon="bi-terminal"
    />

    <!-- Example Queries Section (Collapsible) -->
    <div v-if="showExamples" class="examples-card mb-4">
      <div class="examples-header">
        <h6 class="mb-0"><i class="bi bi-code-square me-2"></i>Example Queries</h6>
        <button class="btn btn-sm btn-outline-secondary" @click="showExamples = false">
          <i class="bi bi-x-lg me-1"></i>Hide
        </button>
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
          <button class="btn btn-sm btn-outline-secondary" @click="clearResults" :disabled="!oqlResult && !oqlError && !oqlQuery.trim()">
            <i class="bi bi-x-lg me-1"></i>
            Clear
          </button>
          <div class="toolbar-divider"></div>
          <button
              v-if="aiAvailable"
              class="btn btn-sm btn-ai-assistant"
              @click="openAssistant"
          >
            <i class="bi bi-stars me-1"></i>
            AI Assistant
          </button>
          <button
              class="btn btn-sm"
              :class="showExamples ? 'btn-purple' : 'btn-outline-purple'"
              @click="showExamples = !showExamples"
          >
            <i class="bi bi-lightbulb me-1"></i>
            Examples
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
            <span class="results-count">{{ filteredResults.length }} results</span>
            <span v-if="oqlResult.hasMore" class="truncated-badge">limit reached</span>
            <span class="meta-item"><i class="bi bi-stopwatch me-1"></i>{{ oqlResult.executionTimeMs }}ms</span>
          </div>
          <div class="results-controls">
            <input
                type="text"
                v-model="resultFilter"
                class="form-control form-control-sm filter-input"
                placeholder="Filter..."
            />
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th style="width: 50px;">#</th>
                <th>Object</th>
                <SortableTableHeader
                    column="size"
                    label="Size"
                    :sort-column="sortColumn"
                    :sort-direction="sortDirection"
                    align="end"
                    width="100px"
                    @sort="toggleSort"
                />
                <SortableTableHeader
                    v-if="hasRetainedSize"
                    column="retained"
                    label="Retained"
                    :sort-column="sortColumn"
                    :sort-direction="sortDirection"
                    align="end"
                    width="100px"
                    @sort="toggleSort"
                />
              </tr>
            </thead>
            <tbody>
              <tr v-for="(entry, index) in filteredResults" :key="index" class="result-row">
                <td class="text-muted">{{ index + 1 }}</td>
                <td class="object-cell">
                  <div class="object-header">
                    <code v-if="entry.className" class="class-name">{{ entry.className }}</code>
                    <InstanceActionButtons
                        :object-id="entry.objectId ?? null"
                        @show-referrers="openTreeModal($event, 'REFERRERS')"
                        @show-reachables="openTreeModal($event, 'REACHABLES')"
                    />
                  </div>
                  <div v-if="entry.value" class="value-text" :title="entry.value">{{ truncateValue(entry.value, 300) }}</div>
                </td>
                <td class="text-end font-monospace">{{ entry.size ? FormattingService.formatBytes(entry.size) : '-' }}</td>
                <td v-if="hasRetainedSize" class="text-end font-monospace">{{ entry.retainedSize ? FormattingService.formatBytes(entry.retainedSize) : '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!oqlResult && !oqlError && !oqlLoading" class="empty-state">
      <div class="text-center py-5">
        <i class="bi bi-terminal text-muted" style="font-size: 3rem;"></i>
        <p class="text-muted mt-3 mb-0">Enter an OQL query above and click Execute to see results.</p>
        <button
            v-if="aiAvailable"
            class="btn btn-ai-assistant-lg mt-4"
            @click="openAssistant"
        >
          <i class="bi bi-stars me-2"></i>
          Ask AI Assistant
        </button>
      </div>
    </div>

    <!-- AI Assistant Not Configured Panel -->
    <div v-if="!aiAvailable && !oqlLoading" class="ai-config-panel" :class="{ 'ai-config-minimized': aiPanelMinimized }">
      <button class="ai-config-toggle" @click="aiPanelMinimized = !aiPanelMinimized">
        <span>{{ aiPanelMinimized ? 'Show' : 'Hide' }}</span>
        <i class="bi" :class="aiPanelMinimized ? 'bi-chevron-down' : 'bi-chevron-up'"></i>
      </button>

      <!-- Minimized View -->
      <div v-if="aiPanelMinimized" class="ai-config-minimized-content" @click="aiPanelMinimized = false">
        <div class="ai-config-minimized-icon">
          <i class="bi bi-stars"></i>
        </div>
        <span class="ai-config-minimized-text">AI Assistant available - click to configure</span>
      </div>

      <!-- Expanded View -->
      <div v-else class="ai-config-content">
        <div class="ai-config-icon">
          <i class="bi bi-stars"></i>
        </div>
        <div class="ai-config-text">
          <h5 class="ai-config-title">AI Assistant Available</h5>
          <p class="ai-config-description">
            Unlock the power of AI to help you write OQL queries. Describe what you're looking for in natural language and let AI generate the query for you.
          </p>
          <div class="ai-providers-note">
            <i class="bi bi-check-circle-fill"></i>
            <span>Supports&nbsp;<strong>Anthropic Claude</strong>&nbsp;and&nbsp;<strong>OpenAI ChatGPT</strong></span>
          </div>
        </div>
        <div class="ai-config-features">
          <div class="ai-feature">
            <i class="bi bi-chat-dots"></i>
            <span>Natural language queries</span>
          </div>
          <div class="ai-feature">
            <i class="bi bi-lightning-charge"></i>
            <span>Instant query generation</span>
          </div>
          <div class="ai-feature">
            <i class="bi bi-mortarboard"></i>
            <span>Learn OQL syntax</span>
          </div>
        </div>

        <div class="ai-config-setup">
          <div class="config-section">
            <div class="config-section-title">
              <i class="bi bi-file-earmark-code me-2"></i>
              application.properties
            </div>
            <div class="config-code">
              <code>jeffrey.ai.enabled=<span class="code-value">true</span></code>
              <code>jeffrey.ai.provider=<span class="code-value">anthropic</span></code>
              <code>spring.ai.anthropic.chat.options.model=<span class="code-value">claude-sonnet-4-5-20250929</span></code>
            </div>
          </div>

          <div class="config-section">
            <div class="config-section-title">
              <i class="bi bi-key me-2"></i>
              secrets.properties
            </div>
            <div class="config-code">
              <code>spring.ai.anthropic.api-key=<span class="code-value">sk-ant-...</span></code>
            </div>
            <div class="config-hint-text">
              Get your API key from <a href="https://console.anthropic.com" target="_blank" rel="noopener">console.anthropic.com</a>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!aiPanelMinimized" class="ai-config-decoration">
        <div class="decoration-circle circle-1"></div>
        <div class="decoration-circle circle-2"></div>
        <div class="decoration-circle circle-3"></div>
      </div>
    </div>

    <!-- AI Assistant -->
    <OqlAssistant
        :is-open="showAssistant"
        :is-expanded="assistantExpanded"
        :profile-id="profileId"
        @close="closeAssistant"
        @expand="openAssistant"
        @minimize="assistantExpanded = false"
        @apply="applyQueryFromAssistant"
        @run="runQueryFromAssistant"
    />

    <!-- Instance Tree Modal -->
    <InstanceTreeModal
        v-if="selectedObjectId !== null"
        v-model:show="showTreeModal"
        :object-id="selectedObjectId"
        :initial-mode="treeMode"
        :profile-id="profileId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import OqlAssistant from '@/components/oql/OqlAssistant.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import OqlAssistantClient from '@/services/api/OqlAssistantClient';
import OQLQueryResult from '@/services/api/model/OQLQueryResult';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);

const oqlQuery = ref('');
const oqlLimit = ref(50);
const oqlLoading = ref(false);
const oqlResult = ref<OQLQueryResult | null>(null);
const oqlError = ref<string | null>(null);
const showExamples = ref(false);
const includeRetainedSize = ref(true);
const resultFilter = ref('');
const sortColumn = ref('retained');
const sortDirection = ref<'asc' | 'desc'>('desc');
const showAssistant = ref(false);
const assistantExpanded = ref(false);  // Start minimized so button is visible on page load
const aiAvailable = ref(false);
const aiPanelMinimized = ref(sessionStorage.getItem('oql-ai-panel-minimized') === 'true');

// Persist minimized state in session storage
watch(aiPanelMinimized, (value) => {
  sessionStorage.setItem('oql-ai-panel-minimized', String(value));
});
const showTreeModal = ref(false);
const selectedObjectId = ref<number | null>(null);
const treeMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

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
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  if (sortColumn.value === 'size') {
    results.sort((a, b) => direction * ((a.size || 0) - (b.size || 0)));
  } else if (sortColumn.value === 'retained') {
    results.sort((a, b) => direction * ((a.retainedSize || 0) - (b.retainedSize || 0)));
  }

  return results;
});

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = 'desc';
  }
};

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
  sortColumn.value = 'retained';
  sortDirection.value = 'desc';

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
  oqlQuery.value = '';
  oqlResult.value = null;
  oqlError.value = null;
};

const openAssistant = () => {
  showAssistant.value = true;
  assistantExpanded.value = true;
};

const closeAssistant = () => {
  showAssistant.value = false;
  assistantExpanded.value = false;  // Return to minimized state - button stays visible
};

const applyQueryFromAssistant = (query: string) => {
  oqlQuery.value = query;
  closeAssistant();
};

const runQueryFromAssistant = async (query: string) => {
  oqlQuery.value = query;
  closeAssistant();
  await executeQuery();
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  selectedObjectId.value = objectId;
  treeMode.value = mode;
  showTreeModal.value = true;
};

const checkAiAvailability = async () => {
  try {
    const aiClient = new OqlAssistantClient(profileId);
    const status = await aiClient.getStatus();
    aiAvailable.value = status.enabled && status.configured;
  } catch {
    aiAvailable.value = false;
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

    if (heapExists.value) {
      cacheReady.value = await client.isCacheReady();
      // Check AI availability in parallel
      checkAiAvailability();
    }

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to initialize OQL interface';
    console.error('Error initializing OQL interface:', err);
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

/* Examples Card */
.examples-card {
  background: white;
  border: 1px solid #dee2e6;
  overflow: hidden;
}

.examples-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  padding: 1rem 1.25rem;
  border: none;
  border-bottom: 1px solid #dee2e6;
  background-color: #f8f9fa;
  font-family: var(--font-family-base);
  font-size: 0.875rem;
  resize: vertical;
  min-height: 120px;
  transition: background-color 0.2s ease;
}

.query-input::placeholder {
  color: #adb5bd;
  opacity: 0.7;
  font-style: italic;
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

.query-toolbar .btn {
  padding: 0.4rem 0.75rem;
  font-size: 0.8rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background-color: #dee2e6;
  margin: 0 0.25rem;
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

/* Table Styles - matching Class Histogram */
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

.class-name {
  font-size: 0.8rem;
  font-weight: 500;
  color: #6f42c1;
  word-break: break-all;
  line-height: 1.4;
}

.value-text {
  font-size: 0.75rem;
  color: #6c757d;
  word-break: break-word;
  line-height: 1.4;
  margin-top: 0.25rem;
}

.font-monospace {
  font-size: 0.8rem;
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

/* AI Assistant Button - Eye-catching design */
.btn-ai-assistant {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 50%, #6d28d9 100%);
  border: none;
  color: white;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.4);
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.btn-ai-assistant::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.2),
    transparent
  );
  transition: left 0.5s ease;
}

.btn-ai-assistant:hover {
  background: linear-gradient(135deg, #a78bfa 0%, #8b5cf6 50%, #7c3aed 100%);
  box-shadow: 0 4px 16px rgba(124, 58, 237, 0.5);
  transform: translateY(-1px);
  color: white;
}

.btn-ai-assistant:hover::before {
  left: 100%;
}

.btn-ai-assistant:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.4);
}

.btn-ai-assistant i {
  animation: sparkle 3s ease-in-out infinite;
}

@keyframes sparkle {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.15);
  }
}

/* Large AI Assistant Button for empty state */
.btn-ai-assistant-lg {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 50%, #6d28d9 100%);
  border: none;
  color: white;
  font-weight: 600;
  font-size: 1rem;
  padding: 0.75rem 1.75rem;
  border-radius: 8px;
  box-shadow: 0 4px 15px rgba(124, 58, 237, 0.4);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.btn-ai-assistant-lg::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.25),
    transparent
  );
  transition: left 0.6s ease;
}

.btn-ai-assistant-lg:hover {
  background: linear-gradient(135deg, #a78bfa 0%, #8b5cf6 50%, #7c3aed 100%);
  box-shadow: 0 6px 25px rgba(124, 58, 237, 0.5);
  transform: translateY(-2px);
  color: white;
}

.btn-ai-assistant-lg:hover::before {
  left: 100%;
}

.btn-ai-assistant-lg:active {
  transform: translateY(0);
  box-shadow: 0 4px 15px rgba(124, 58, 237, 0.4);
}

.btn-ai-assistant-lg i {
  animation: sparkle 3s ease-in-out infinite;
}

/* Object cell with inline action buttons */
.object-cell {
  position: relative;
}

.object-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* AI Configuration Panel - Attractive Design */
.ai-config-panel {
  position: relative;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 50%, #ede9fe 100%);
  border: 1px solid #e9d5ff;
  border-radius: 12px;
  padding: 1.5rem 2rem;
  margin-top: 1rem;
  overflow: hidden;
  transition: all 0.3s ease;
}

.ai-config-panel.ai-config-minimized {
  padding: 0.75rem 1rem;
}

.ai-config-toggle {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  padding: 0.375rem 0.75rem;
  border: 1px solid #c4b5fd;
  background: white;
  color: #7c3aed;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  transition: all 0.2s ease;
  z-index: 2;
  box-shadow: 0 1px 3px rgba(124, 58, 237, 0.1);
}

.ai-config-toggle:hover {
  background: #f5f3ff;
  border-color: #a78bfa;
  color: #6d28d9;
  box-shadow: 0 2px 6px rgba(124, 58, 237, 0.15);
}

.ai-config-toggle i {
  font-size: 0.7rem;
}

.ai-config-minimized-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  padding-right: 2rem;
}

.ai-config-minimized-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-config-minimized-icon i {
  font-size: 1rem;
  color: white;
  animation: sparkle 3s ease-in-out infinite;
}

.ai-config-minimized-text {
  font-size: 0.85rem;
  font-weight: 500;
  color: #6d28d9;
}

.ai-config-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.ai-config-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 50%, #6d28d9 100%);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
  box-shadow: 0 8px 24px rgba(124, 58, 237, 0.3);
}

.ai-config-icon i {
  font-size: 1.75rem;
  color: white;
  animation: sparkle 3s ease-in-out infinite;
}

.ai-config-text {
  max-width: 480px;
  margin-bottom: 1.25rem;
}

.ai-config-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: #5b21b6;
  margin-bottom: 0.5rem;
}

.ai-config-description {
  font-size: 0.875rem;
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 0.5rem;
}

.ai-providers-note {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8rem;
  color: #059669;
  background: rgba(5, 150, 105, 0.1);
  padding: 0.35rem 0.75rem;
  border-radius: 20px;
}

.ai-providers-note strong {
  color: #047857;
}

.ai-config-features {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 1.25rem;
  flex-wrap: wrap;
  justify-content: center;
}

.ai-feature {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #7c3aed;
  background: white;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.1);
  border: 1px solid #e9d5ff;
}

.ai-feature i {
  font-size: 1rem;
}

/* Configuration Setup Section */
.ai-config-setup {
  display: flex;
  gap: 1.5rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
  justify-content: center;
}

.config-section {
  background: white;
  border: 1px solid #e9d5ff;
  border-radius: 8px;
  padding: 1rem 1.25rem;
  min-width: 280px;
  text-align: left;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.08);
}

.config-section-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: #7c3aed;
  margin-bottom: 0.75rem;
  display: flex;
  align-items: center;
}

.config-code {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.config-code code {
  display: block;
  font-size: 0.75rem;
  color: #374151;
  background: #f8f5ff;
  padding: 0.375rem 0.625rem;
  border-radius: 4px;
  border: 1px solid #ede9fe;
}

.config-code .code-value {
  color: #7c3aed;
  font-weight: 600;
}

.config-hint-text {
  font-size: 0.7rem;
  color: #6b7280;
  margin-top: 0.5rem;
}

.config-hint-text a {
  color: #7c3aed;
  text-decoration: none;
  font-weight: 500;
}

.config-hint-text a:hover {
  text-decoration: underline;
}

/* Decorative Elements */
.ai-config-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  top: -80px;
  right: -60px;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, #a78bfa, #8b5cf6);
  bottom: -40px;
  left: -30px;
  animation: float 6s ease-in-out infinite reverse;
}

.circle-3 {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #c4b5fd, #a78bfa);
  top: 50%;
  left: 15%;
  animation: float 10s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.05);
  }
}
</style>

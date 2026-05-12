<template>
  <LoadingState v-if="loading" message="Loading SQL query interface..." />

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
    icon="terminal"
    message="The heap dump needs to be initialized before you can execute SQL queries. This process builds the index and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
      title="SQL Query"
      description="Execute DuckDB SQL queries against the heap-dump index"
      icon="bi-terminal"
    />

    <!-- Sub-page tabs: Executor / Examples / Schema -->
    <TabBar v-model="activeTab" :tabs="sqlTabs" class="mb-3" />

    <!-- ==== EXAMPLES TAB ============================================ -->
    <div v-show="activeTab === 'examples'" class="examples-panel">
      <aside class="picker-rail">
        <template v-for="group in queryGroups" :key="group.title">
          <div class="picker-cat">{{ group.title }}</div>
          <div
            v-for="ex in group.items"
            :key="ex.title"
            class="picker-item"
            :class="{ active: selectedQuery?.title === ex.title }"
            @click="selectQuery(ex)"
          >
            <i class="bi bi-chevron-right picker-icon"></i>
            <span class="picker-label">{{ ex.title }}</span>
          </div>
        </template>
      </aside>
      <section class="picker-preview">
        <div v-if="!selectedQuery" class="picker-empty">
          <i class="bi bi-arrow-left me-2"></i>
          Pick an <strong>example</strong> on the left to see its SQL.
        </div>
        <div v-else>
          <h6 class="picker-title">{{ selectedQuery.title }}</h6>
          <p class="picker-desc">{{ selectedQuery.description }}</p>
          <pre class="example-sql"><code>{{ selectedQuery.query }}</code></pre>
          <button class="btn btn-sm btn-primary" @click="useExample(selectedQuery.query)">
            <i class="bi bi-arrow-right-circle me-1"></i>
            Use this query
          </button>
        </div>
      </section>
    </div>

    <!-- ==== SCHEMA TAB ============================================== -->
    <div v-show="activeTab === 'schema'" class="examples-panel">
      <aside class="picker-rail">
        <div
          v-for="t in heapDumpIndexSchema"
          :key="t.name"
          class="picker-item"
          :class="{ active: selectedSchema === t.name }"
          @click="selectSchema(t.name)"
        >
          <i class="bi bi-table picker-icon"></i>
          <span class="picker-label">{{ t.name }}</span>
        </div>
      </aside>
      <section class="picker-preview">
        <div v-if="!schemaInFocus" class="picker-empty">
          <i class="bi bi-arrow-left me-2"></i>
          Pick a <strong>table</strong> on the left to see its columns.
        </div>
        <div v-else>
          <h6 class="picker-title">
            <i class="bi bi-table me-2"></i>{{ schemaInFocus.name }}
          </h6>
          <p class="picker-desc">{{ schemaInFocus.description }}</p>
          <div class="table-responsive">
            <table class="table table-sm table-hover mb-0 schema-table">
              <thead>
                <tr>
                  <th>Column</th>
                  <th>Type</th>
                  <th>Null</th>
                  <th>Notes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in schemaInFocus.columns" :key="c.name">
                  <td><code>{{ c.name }}</code></td>
                  <td><code>{{ c.type }}</code></td>
                  <td>{{ c.nullable ? 'YES' : '—' }}</td>
                  <td class="schema-note">{{ c.note || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="schemaInFocus.primaryKey && schemaInFocus.primaryKey.length" class="schema-meta">
            <strong>Primary key:</strong>
            <code>{{ schemaInFocus.primaryKey.join(', ') }}</code>
          </p>
          <p v-if="schemaInFocus.indexed && schemaInFocus.indexed.length" class="schema-meta">
            <strong>Indexed:</strong>
            <code>{{ schemaInFocus.indexed.join(', ') }}</code>
          </p>
        </div>
      </section>
    </div>

    <!-- ==== EXECUTOR TAB (textarea + results) ====================== -->
    <div v-show="activeTab === 'executor'">

    <!-- Query Input Section -->
    <div class="query-editor mb-4">
      <textarea
        v-model="oqlQuery"
        class="query-input"
        rows="3"
        placeholder="SELECT c.name, COUNT(*) AS n FROM instance i JOIN class c USING (class_id) GROUP BY c.name ORDER BY n DESC LIMIT 20"
        @keydown.ctrl.enter="executeQuery"
        @keydown.meta.enter="executeQuery"
      ></textarea>
      <div class="query-toolbar">
        <div class="d-flex align-items-center gap-2">
          <button
            class="btn btn-sm btn-primary"
            @click="executeQuery"
            :disabled="oqlLoading || !oqlQuery.trim()"
          >
            <span v-if="oqlLoading" class="spinner-border spinner-border-sm me-1"></span>
            <i v-else class="bi bi-play-fill me-1"></i>
            Execute
          </button>
          <button
            class="btn btn-sm btn-outline-secondary"
            @click="clearResults"
            :disabled="!oqlResult && !oqlError && !oqlQuery.trim()"
          >
            <i class="bi bi-x-lg me-1"></i>
            Clear
          </button>
          <div class="toolbar-divider"></div>
          <button v-if="aiAvailable" class="btn btn-sm btn-ai-assistant" @click="openAssistant">
            <i class="bi bi-stars me-1"></i>
            AI Assistant
          </button>
        </div>
        <div class="d-flex align-items-center gap-3">
          <div class="form-check form-check-inline mb-0">
            <input
              type="checkbox"
              class="form-check-input"
              id="retainedSizeCheck"
              v-model="includeRetainedSize"
            />
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
      <DataTable>
        <template #toolbar>
          <TableToolbar v-model="resultFilter" search-placeholder="Filter...">
            <span class="toolbar-count">{{ filteredResults.length }} results</span>
            <span v-if="oqlResult.hasMore" class="toolbar-badge-warning">limit reached</span>
            <span class="toolbar-meta"><i class="bi bi-stopwatch me-1"></i>{{ oqlResult.executionTimeMs }}ms</span>
          </TableToolbar>
        </template>
            <thead>
              <tr>
                <th style="width: 50px">#</th>
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
                      :object-id="entry.objectId || null"
                      :show-instance-detail="true"
                      @show-referrers="openTreeModal($event, 'REFERRERS')"
                      @show-reachables="openTreeModal($event, 'REACHABLES')"
                      @show-g-c-root-path="openGCRootPathModal"
                      @show-instance-detail="openInstanceDetailPanel"
                    />
                  </div>
                  <div v-if="entry.value" class="value-text" :title="entry.value">
                    {{ truncateValue(entry.value, 300) }}
                  </div>
                </td>
                <td class="text-end font-monospace">
                  {{ entry.size ? FormattingService.formatBytes(entry.size) : '-' }}
                </td>
                <td v-if="hasRetainedSize" class="text-end font-monospace">
                  {{ entry.retainedSize ? FormattingService.formatBytes(entry.retainedSize) : '-' }}
                </td>
              </tr>
            </tbody>
      </DataTable>
    </div>

    <!-- Empty State -->
    <div v-if="!oqlResult && !oqlError && !oqlLoading" class="empty-state">
      <div class="text-center py-5">
        <i class="bi bi-terminal text-muted" style="font-size: 3rem"></i>
        <p class="text-muted mt-3 mb-0">
          Enter a SQL query above and click Execute to see results.
        </p>
        <button v-if="aiAvailable" class="btn btn-ai-assistant mt-4" @click="openAssistant">
          <i class="bi bi-stars me-1"></i>
          Ask AI Assistant
        </button>
      </div>
    </div>

    <!-- AI Assistant Not Configured Panel -->
    <div
      v-if="aiChecked && !aiAvailable && !oqlLoading"
      class="ai-config-panel"
      :class="{ 'ai-config-minimized': aiPanelMinimized }"
    >
      <button class="ai-config-toggle" @click="aiPanelMinimized = !aiPanelMinimized">
        <span>{{ aiPanelMinimized ? 'Show' : 'Hide' }}</span>
        <i class="bi" :class="aiPanelMinimized ? 'bi-chevron-down' : 'bi-chevron-up'"></i>
      </button>

      <!-- Minimized View -->
      <div
        v-if="aiPanelMinimized"
        class="ai-config-minimized-content"
        @click="aiPanelMinimized = false"
      >
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
            Unlock the power of AI to help you write DuckDB SQL queries against the heap-dump
            index. Describe what you're looking for in natural language and let AI generate the
            query for you.
          </p>
          <div class="ai-providers-note">
            <i class="bi bi-check-circle-fill"></i>
            <span
              >Supports&nbsp;<strong>Anthropic Claude</strong>&nbsp;and&nbsp;<strong
                >OpenAI ChatGPT</strong
              ></span
            >
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
            <span>Learn the schema</span>
          </div>
        </div>

        <div class="ai-config-setup">
          <div class="config-section">
            <div class="config-section-title">
              <i class="bi bi-file-earmark-code me-2"></i>
              application.properties
            </div>
            <div class="config-code">
              <code>jeffrey.ai.provider=<span class="code-value">claude</span></code>
              <code
                ># Claude: claude-opus-4-5-20251101, claude-sonnet-4-5-20250929,
                claude-sonnet-4-20250514</code
              >
              <code># ChatGPT: gpt-4o, gpt-4o-mini, o3-mini</code>
              <code
                >jeffrey.ai.model=<span class="code-value">claude-sonnet-4-5-20250929</span></code
              >
            </div>
          </div>

          <div class="config-section">
            <div class="config-section-title">
              <i class="bi bi-key me-2"></i>
              secrets.properties
            </div>
            <div class="config-code">
              <code>jeffrey.ai.api-key=<span class="code-value">sk-ant-...</span></code>
            </div>
            <div class="config-hint-text">
              Get your API key from
              <a href="https://console.anthropic.com" target="_blank" rel="noopener"
                >console.anthropic.com</a
              >
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

    <!-- Instance Details Side Panel -->
    <InstanceDetailPanel
      v-if="client"
      :is-open="detailPanelOpen"
      :object-id="detailPanelObjectId"
      :client="client"
      @close="detailPanelOpen = false"
      @navigate="detailPanelObjectId = $event"
    />

    </div><!-- /executor tab -->
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import OqlAssistant from '@/components/oql/OqlAssistant.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import TabBar, { type TabBarItem } from '@/components/TabBar.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import OqlAssistantClient from '@/services/api/OqlAssistantClient';
import OQLQueryResult from '@/services/api/model/OQLQueryResult';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const router = useRouter();

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
type SqlTab = 'executor' | 'examples' | 'schema';
const activeTab = ref<SqlTab>('executor');
const includeRetainedSize = ref(true);
const resultFilter = ref('');
const sortColumn = ref('retained');
const sortDirection = ref<'asc' | 'desc'>('desc');
const showAssistant = ref(false);
const assistantExpanded = ref(false); // Start minimized so button is visible on page load
const aiAvailable = ref(false);
const aiChecked = ref(false);
const aiPanelMinimized = ref(sessionStorage.getItem('oql-ai-panel-minimized') === 'true');

// Persist minimized state in session storage
watch(aiPanelMinimized, value => {
  sessionStorage.setItem('oql-ai-panel-minimized', String(value));
});
const showTreeModal = ref(false);
const selectedObjectId = ref<number | null>(null);
const treeMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

// Instance Details side-panel state
const detailPanelOpen = ref(false);
const detailPanelObjectId = ref<number | null>(null);
const openInstanceDetailPanel = (objectId: number) => {
  detailPanelObjectId.value = objectId;
  detailPanelOpen.value = true;
};

let client: HeapDumpClient;

// Heap-dump index schema — sourced from
// jeffrey-microscope/profiles/heap-dump/src/main/resources/db/migration/heap-dump-index/V001__init.sql.
// V001 is modified in place per project policy (never V002), so the constant
// only needs to change when V001 itself changes.
interface SchemaColumn {
  name: string;
  type: string;
  nullable: boolean;
  note?: string;
}
interface SchemaTable {
  name: string;
  description: string;
  columns: SchemaColumn[];
  primaryKey?: string[];
  indexed?: string[];
}

const heapDumpIndexSchema: SchemaTable[] = [
  {
    name: 'dump_metadata',
    description: 'Single-row parse run info — id-size, compressed-oops flag, parser version, warning count.',
    columns: [
      { name: 'hprof_path', type: 'VARCHAR', nullable: false },
      { name: 'hprof_size_bytes', type: 'BIGINT', nullable: false },
      { name: 'hprof_mtime_ms', type: 'BIGINT', nullable: false },
      { name: 'id_size', type: 'INTEGER', nullable: false, note: '4 or 8 bytes (HPROF id width)' },
      { name: 'hprof_version', type: 'VARCHAR', nullable: false },
      { name: 'timestamp_ms', type: 'BIGINT', nullable: false },
      { name: 'bytes_parsed', type: 'BIGINT', nullable: false },
      { name: 'record_count', type: 'BIGINT', nullable: false },
      { name: 'warning_count', type: 'BIGINT', nullable: false },
      { name: 'truncated', type: 'BOOLEAN', nullable: false },
      { name: 'parser_version', type: 'VARCHAR', nullable: false },
      { name: 'parsed_at_ms', type: 'BIGINT', nullable: false },
      { name: 'compressed_oops', type: 'BOOLEAN', nullable: false, note: 'Inferred at index build; baked into shallow_size' }
    ]
  },
  {
    name: 'string',
    description: 'HPROF UTF-8 string pool. Class names + field names reference this via string_id.',
    columns: [
      { name: 'string_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'value', type: 'VARCHAR', nullable: false }
    ],
    primaryKey: ['string_id']
  },
  {
    name: 'class',
    description: 'One row per loaded Java class. name is dot-notation (post-ClassNameFormatter).',
    columns: [
      { name: 'class_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'class_serial', type: 'INTEGER', nullable: false },
      { name: 'name', type: 'VARCHAR', nullable: false, note: "Dot-notation, e.g. 'java.util.HashMap'" },
      { name: 'is_array', type: 'BOOLEAN', nullable: false },
      { name: 'super_class_id', type: 'BIGINT', nullable: true, note: 'NULL for java.lang.Object and primitive arrays' },
      { name: 'classloader_id', type: 'BIGINT', nullable: true },
      { name: 'signers_id', type: 'BIGINT', nullable: true },
      { name: 'protection_domain_id', type: 'BIGINT', nullable: true },
      { name: 'instance_size', type: 'INTEGER', nullable: false },
      { name: 'static_fields_size', type: 'INTEGER', nullable: false },
      { name: 'file_offset', type: 'BIGINT', nullable: false }
    ],
    primaryKey: ['class_id'],
    indexed: ['name', 'super_class_id', 'is_array']
  },
  {
    name: 'instance',
    description: 'One row per object in the heap (INSTANCE_DUMP, OBJECT_ARRAY_DUMP, PRIMITIVE_ARRAY_DUMP).',
    columns: [
      { name: 'instance_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'class_id', type: 'BIGINT', nullable: true, note: 'NULL only for primitive arrays' },
      { name: 'file_offset', type: 'BIGINT', nullable: false },
      { name: 'record_kind', type: 'TINYINT', nullable: false, note: '0=instance, 1=object_array, 2=primitive_array' },
      { name: 'shallow_size', type: 'INTEGER', nullable: false, note: 'Header + payload; MAT @usedHeapSize' },
      { name: 'array_length', type: 'INTEGER', nullable: true, note: 'Only for arrays; MAT @length' },
      { name: 'primitive_type', type: 'TINYINT', nullable: true, note: 'Only for primitive arrays' }
    ],
    primaryKey: ['instance_id'],
    indexed: ['class_id']
  },
  {
    name: 'class_instance_field',
    description: 'Per-class instance field descriptors (one row per field, in declaration order).',
    columns: [
      { name: 'class_id', type: 'BIGINT', nullable: false, note: 'PK part 1' },
      { name: 'field_index', type: 'INTEGER', nullable: false, note: 'PK part 2; 0-based within this class only' },
      { name: 'name', type: 'VARCHAR', nullable: false },
      { name: 'basic_type', type: 'TINYINT', nullable: false, note: 'HPROF basic type tag' }
    ],
    primaryKey: ['class_id', 'field_index']
  },
  {
    name: 'gc_root',
    description: 'One row per GC root reference. root_kind maps to the HPROF sub-tag byte.',
    columns: [
      { name: 'instance_id', type: 'BIGINT', nullable: false },
      { name: 'root_kind', type: 'TINYINT', nullable: false, note: 'HPROF sub-tag byte (jni global, java frame, thread block, …)' },
      { name: 'thread_serial', type: 'INTEGER', nullable: true },
      { name: 'frame_index', type: 'INTEGER', nullable: true },
      { name: 'file_offset', type: 'BIGINT', nullable: false }
    ],
    indexed: ['instance_id']
  },
  {
    name: 'outbound_ref',
    description: 'Every object-to-object reference. The full reference graph.',
    columns: [
      { name: 'source_id', type: 'BIGINT', nullable: false },
      { name: 'target_id', type: 'BIGINT', nullable: false },
      { name: 'field_kind', type: 'TINYINT', nullable: false, note: '0=instance_field, 1=array_element, 2=class_static' },
      { name: 'field_id', type: 'INTEGER', nullable: false, note: 'Field index for instance/static, array index for arrays' }
    ],
    indexed: ['source_id', 'target_id']
  },
  {
    name: 'dominator',
    description: 'Immediate dominator per instance. Built lazily — empty until dominator analysis runs.',
    columns: [
      { name: 'instance_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'dominator_id', type: 'BIGINT', nullable: false, note: '0 = directly rooted at virtual root' }
    ],
    primaryKey: ['instance_id'],
    indexed: ['dominator_id']
  },
  {
    name: 'retained_size',
    description: 'Total bytes reclaimable per instance. Populated alongside dominator (lazy).',
    columns: [
      { name: 'instance_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'bytes', type: 'BIGINT', nullable: false }
    ],
    primaryKey: ['instance_id']
  },
  {
    name: 'stack_frame',
    description: 'HPROF STACK_FRAME records. class_name is resolved at index build time.',
    columns: [
      { name: 'frame_id', type: 'BIGINT', nullable: false, note: 'PK' },
      { name: 'class_name', type: 'VARCHAR', nullable: false },
      { name: 'method_name', type: 'VARCHAR', nullable: false },
      { name: 'method_signature', type: 'VARCHAR', nullable: false },
      { name: 'source_file', type: 'VARCHAR', nullable: true },
      { name: 'line_number', type: 'INTEGER', nullable: false, note: '≥1 normal, -1 no info, -2 compiled, -3 native' }
    ],
    primaryKey: ['frame_id']
  },
  {
    name: 'stack_trace_frame',
    description: 'Ordered membership of frames in a stack trace. frame_index 0 is topmost.',
    columns: [
      { name: 'trace_serial', type: 'INTEGER', nullable: false, note: 'PK part 1' },
      { name: 'thread_serial', type: 'INTEGER', nullable: false },
      { name: 'frame_index', type: 'INTEGER', nullable: false, note: 'PK part 2; 0 = topmost' },
      { name: 'frame_id', type: 'BIGINT', nullable: false }
    ],
    primaryKey: ['trace_serial', 'frame_index'],
    indexed: ['thread_serial']
  },
  {
    name: 'parse_warning',
    description: 'Forensic record of skipped / truncated / recovered HPROF records.',
    columns: [
      { name: 'file_offset', type: 'BIGINT', nullable: false },
      { name: 'record_kind', type: 'INTEGER', nullable: true },
      { name: 'severity', type: 'TINYINT', nullable: false, note: '0=info, 1=warn, 2=error' },
      { name: 'message', type: 'VARCHAR', nullable: false }
    ]
  }
];

// Example queries — DuckDB SQL against the heap-dump index schema.
interface ExampleQuery {
  title: string;
  description: string;
  query: string;
  divider?: never;
}
interface ExampleDivider {
  title: string;
  divider: true;
}
type ExampleEntry = ExampleQuery | ExampleDivider;

const exampleQueries: ExampleEntry[] = [
  { title: '--- Class Histogram ---', divider: true },
  {
    title: 'Top classes by total shallow size',
    description: 'Aggregates every live instance by its class and ranks by summed shallow_size. Single GROUP BY against the indexed instance table.',
    query:
      "SELECT c.name, COUNT(*) AS n, SUM(i.shallow_size) AS total_bytes\n" +
      "FROM instance i JOIN class c USING (class_id)\n" +
      "GROUP BY c.name ORDER BY total_bytes DESC LIMIT 50"
  },
  {
    title: 'Top classes by instance count',
    description: 'Same shape but ranked by row count. Useful for spotting object-explosion patterns (millions of tiny instances).',
    query:
      "SELECT c.name, COUNT(*) AS n\n" +
      "FROM instance i JOIN class c USING (class_id)\n" +
      "GROUP BY c.name ORDER BY n DESC LIMIT 50"
  },

  { title: '--- Retained Size (requires dominator tree) ---', divider: true },
  {
    title: 'Top retained classes',
    description: 'Sums retained_size per class. Requires the dominator tree — LEFT JOIN so the query still runs (with NULLs) if the tree has not been built.',
    query:
      "SELECT c.name, SUM(r.bytes) AS retained\n" +
      "FROM instance i JOIN class c USING (class_id)\n" +
      "LEFT JOIN retained_size r USING (instance_id)\n" +
      "GROUP BY c.name ORDER BY retained DESC NULLS LAST LIMIT 20"
  },
  {
    title: 'Single largest retained objects',
    description: 'Individual instances ranked by retained heap. The answer to "what one object is keeping all that memory alive?"',
    query:
      "SELECT r.instance_id, c.name, r.bytes AS retained\n" +
      "FROM retained_size r\n" +
      "JOIN instance i USING (instance_id)\n" +
      "JOIN class c USING (class_id)\n" +
      "ORDER BY r.bytes DESC LIMIT 20"
  },

  { title: '--- Reference Graph ---', divider: true },
  {
    title: 'Outbound refs of one object',
    description: 'Direct fan-out from a single instance. Replace 12345 with the object id you are inspecting.',
    query: "SELECT * FROM outbound_ref WHERE source_id = 12345"
  },
  {
    title: 'Inbound refs of one object',
    description: 'Who points at this instance? Indexed on target_id so this is cheap even on large heaps.',
    query: "SELECT * FROM outbound_ref WHERE target_id = 12345"
  },
  {
    title: 'Most-referenced targets',
    description: 'Hot spots in the reference graph — objects that many other instances point at (caches, interned strings, singletons).',
    query:
      "SELECT target_id, COUNT(*) AS in_count\n" +
      "FROM outbound_ref\n" +
      "GROUP BY target_id ORDER BY in_count DESC LIMIT 20"
  },

  { title: '--- GC Roots ---', divider: true },
  {
    title: 'GC root kinds and counts',
    description: 'Distribution of GC root types in the dump. root_kind is the raw HPROF sub-tag byte — see the gc_root schema notes for the mapping.',
    query:
      "SELECT root_kind, COUNT(*) AS n\n" +
      "FROM gc_root\n" +
      "GROUP BY root_kind ORDER BY n DESC"
  },
  {
    title: 'GC-rooted classes',
    description: 'Which classes have the most GC-rooted instances. Often dominated by Thread, ClassLoader, jni globals.',
    query:
      "SELECT c.name, COUNT(*) AS n\n" +
      "FROM gc_root g JOIN instance i USING (instance_id) JOIN class c USING (class_id)\n" +
      "GROUP BY c.name ORDER BY n DESC LIMIT 20"
  },

  { title: '--- Arrays ---', divider: true },
  {
    title: 'Largest object arrays by element count',
    description: 'Object arrays (record_kind = 1) ranked by length — finds oversized HashMap.table[], ArrayList.elementData, etc.',
    query:
      "SELECT i.instance_id, c.name, i.array_length\n" +
      "FROM instance i JOIN class c USING (class_id)\n" +
      "WHERE i.record_kind = 1 AND i.array_length IS NOT NULL\n" +
      "ORDER BY i.array_length DESC LIMIT 20"
  },
  {
    title: 'Largest primitive arrays by shallow size',
    description: 'byte[] / char[] / int[] etc. by shallow_size. Common offenders for hidden memory waste (big buffers, mis-sized caches).',
    query:
      "SELECT i.instance_id, i.primitive_type, i.array_length, i.shallow_size\n" +
      "FROM instance i\n" +
      "WHERE i.record_kind = 2\n" +
      "ORDER BY i.shallow_size DESC LIMIT 20"
  },

  { title: '--- Class Universe ---', divider: true },
  {
    title: 'Find classes by name regex',
    description: 'DuckDB ~ operator runs a POSIX regex against class.name. Useful to enumerate "all classes from package X".',
    query: "SELECT class_id, name FROM class WHERE name ~ 'com\\\\.example\\\\..*' ORDER BY name"
  },
  {
    title: 'Subclasses of a given class (recursive)',
    description: 'Walks super_class_id transitively. The DuckDB-SQL equivalent of MAT-OQL "FROM INSTANCEOF java.util.AbstractMap".',
    query:
      "WITH RECURSIVE subs(class_id) AS (\n" +
      "  SELECT class_id FROM class WHERE name = 'java.util.AbstractMap'\n" +
      "  UNION ALL\n" +
      "  SELECT c.class_id FROM class c JOIN subs s ON c.super_class_id = s.class_id\n" +
      ")\n" +
      "SELECT cl.name, COUNT(i.instance_id) AS n\n" +
      "FROM subs JOIN class cl USING (class_id)\n" +
      "LEFT JOIN instance i USING (class_id)\n" +
      "GROUP BY cl.name ORDER BY n DESC"
  },

  { title: '--- Dump Metadata ---', divider: true },
  {
    title: 'Heap dump shape + parser metadata',
    description: 'Single-row table with id-size, compressed-oops flag, parse-warning count, parser version. Call this once to orient yourself.',
    query: "SELECT * FROM dump_metadata"
  }
];

// Group consecutive non-divider examples under the preceding divider so the
// Examples tab can render category headers + their cards without rebuilding
// the logic per-row.
interface QueryGroup {
  title: string;
  items: ExampleQuery[];
}
const queryGroups = computed<QueryGroup[]>(() => {
  const groups: QueryGroup[] = [];
  let current: QueryGroup | null = null;
  for (const e of exampleQueries) {
    if (e.divider) {
      current = { title: e.title.replace(/---/g, '').trim(), items: [] };
      groups.push(current);
    } else if (current) {
      current.items.push(e);
    }
  }
  return groups;
});

const totalExampleCount = computed(() =>
  queryGroups.value.reduce((n, g) => n + g.items.length, 0)
);

const sqlTabs = computed<TabBarItem[]>(() => [
  { id: 'executor', label: 'Executor', icon: 'terminal' },
  { id: 'examples', label: 'Examples', icon: 'lightbulb', badge: totalExampleCount.value },
  { id: 'schema', label: 'Schema', icon: 'table', badge: heapDumpIndexSchema.length }
]);

// Examples tab — rail item selection
const selectedQuery = ref<ExampleQuery | null>(null);
const selectQuery = (ex: ExampleQuery) => {
  selectedQuery.value = ex;
};

// Schema tab — rail item selection
const selectedSchema = ref<string | null>(null);
const schemaInFocus = computed(() =>
  selectedSchema.value
    ? heapDumpIndexSchema.find(t => t.name === selectedSchema.value) ?? null
    : null
);
const selectSchema = (name: string) => {
  selectedSchema.value = name;
};

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
    results = results.filter(
      entry =>
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
  activeTab.value = 'executor';
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
    const result = await client.executeQuery(
      oqlQuery.value,
      oqlLimit.value,
      0,
      includeRetainedSize.value
    );
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
  assistantExpanded.value = false; // Return to minimized state - button stays visible
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

const openGCRootPathModal = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const checkAiAvailability = async () => {
  try {
    const aiClient = new OqlAssistantClient(profileId);
    const status = await aiClient.getStatus();
    aiAvailable.value = status.enabled === true && status.configured === true;
  } catch {
    aiAvailable.value = false;
  } finally {
    aiChecked.value = true;
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

/* Two-pane snippet picker — reused by both Examples and Schema tabs. */
.examples-panel {
  display: grid;
  grid-template-columns: 340px 1fr;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  overflow: hidden;
  min-height: 480px;
}

.picker-rail {
  border-right: 1px solid var(--color-border);
  overflow-y: auto;
  padding: 0.25rem 0;
}

.picker-cat {
  font-size: 0.65rem;
  font-weight: 700;
  color: var(--color-purple);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 0.625rem 1rem 0.25rem;
}

.picker-cat:not(:first-child) {
  margin-top: 0.5rem;
  border-top: 1px solid var(--color-border-light);
  padding-top: 0.75rem;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 1rem;
  font-size: 0.8rem;
  color: var(--color-text);
  cursor: pointer;
  border-left: 2px solid transparent;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.picker-item:hover {
  background-color: var(--color-bg-hover);
}

.picker-item.active {
  background-color: var(--color-primary-light);
  color: var(--color-purple);
  border-left-color: var(--color-purple);
  font-weight: 600;
}

.picker-icon {
  color: var(--color-text-light);
  font-size: 0.75rem;
  flex-shrink: 0;
}

.picker-item.active .picker-icon {
  color: var(--color-purple);
}

.picker-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-preview {
  padding: 1rem 1.25rem;
  background: var(--color-bg-hover);
  overflow-y: auto;
}

.picker-empty {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  padding: 1rem 0;
}

.picker-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 0.5rem;
  display: flex;
  align-items: center;
}

.picker-desc {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin: 0 0 0.75rem;
  line-height: 1.5;
}

.schema-table th {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  font-weight: 600;
  background: white;
}

.schema-table td {
  font-size: 0.8rem;
  vertical-align: middle;
}

.schema-table code {
  font-size: 0.75rem;
  background: transparent;
  color: var(--color-text);
  padding: 0;
}

.schema-note {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.schema-meta {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.schema-meta code {
  background: var(--color-code-bg);
  color: var(--color-text);
  padding: 0.05rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
}

.example-sql {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 0.75rem 0.875rem;
  margin: 0 0 0.75rem;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.75rem;
  line-height: 1.55;
  color: var(--color-text);
  overflow-x: auto;
  max-height: 320px;
  white-space: pre;
}

/* Query Editor - Compact Design */
.query-editor {
  background: white;
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.query-input {
  width: 100%;
  padding: 1rem 1.25rem;
  border: none;
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-light);
  font-family: var(--font-family-base);
  font-size: 0.875rem;
  resize: vertical;
  min-height: 120px;
  transition: background-color 0.2s ease;
}

.query-input::placeholder {
  color: var(--color-text-light);
  opacity: 0.7;
  font-style: italic;
}

.query-input:focus {
  outline: none;
  background-color: var(--color-white);
  border-bottom: 2px solid var(--color-blue-500);
}

.query-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: var(--color-white);
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
  background-color: var(--color-border);
  margin: 0 0.25rem;
}

.select-narrow {
  width: 80px;
}

/* Toolbar badges */
.toolbar-count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

.toolbar-badge-warning {
  font-size: 0.65rem;
  background: var(--color-warning-bg);
  color: var(--color-warning-text, #856404);
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.toolbar-meta {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.class-name {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-purple);
  word-break: break-all;
  line-height: 1.4;
}

.value-text {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  word-break: break-word;
  line-height: 1.4;
  margin-top: 0.25rem;
}

.font-monospace {
  font-size: 0.8rem;
}

.empty-state {
  background: white;
  border: 1px solid var(--color-border);
}

.empty-state .btn-ai-assistant {
  font-size: 0.8rem !important;
  padding: 0.5rem 1rem !important;
  border-radius: 6px !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: 0.4rem !important;
}

.empty-state .btn-ai-assistant i {
  font-size: 0.9rem !important;
  color: white !important;
  line-height: 1 !important;
}

.btn-purple {
  background-color: var(--color-purple);
  border-color: var(--color-purple);
  color: white;
}

.btn-purple:hover {
  background-color: var(--color-purple-hover);
  border-color: var(--color-purple-hover);
  color: white;
}

.btn-outline-purple {
  border-color: var(--color-purple);
  color: var(--color-purple);
}

.btn-outline-purple:hover {
  background-color: var(--color-purple);
  border-color: var(--color-purple);
  color: white;
}

/* AI Assistant Button - Eye-catching design */
.btn-ai-assistant {
  background: linear-gradient(
    135deg,
    var(--color-violet) 0%,
    var(--color-violet-dark) 50%,
    var(--color-violet-deeper) 100%
  );
  border: none;
  color: white;
  font-weight: 500;
  font-size: 0.8rem;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.4);
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.btn-ai-assistant::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.btn-ai-assistant:hover {
  background: linear-gradient(
    135deg,
    var(--color-violet-border-light) 0%,
    var(--color-violet) 50%,
    var(--color-violet-dark) 100%
  );
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
  font-size: 0.9rem;
  line-height: 1;
}

@keyframes sparkle {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.15);
  }
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
  background: linear-gradient(
    135deg,
    var(--color-violet-light-bg) 0%,
    var(--color-violet-lighter-bg) 50%,
    var(--color-violet-lightest-bg) 100%
  );
  border: 1px solid var(--color-violet-border);
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
  border: 1px solid var(--color-violet-border-light);
  background: white;
  color: var(--color-violet-dark);
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
  background: var(--color-violet-hover-bg);
  border-color: var(--color-violet-border-light);
  color: var(--color-violet-deeper);
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
  background: linear-gradient(135deg, var(--color-violet) 0%, var(--color-violet-dark) 100%);
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
  color: var(--color-violet-deeper);
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
  background: linear-gradient(
    135deg,
    var(--color-violet) 0%,
    var(--color-violet-dark) 50%,
    var(--color-violet-deeper) 100%
  );
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
  color: var(--color-violet-darkest);
  margin-bottom: 0.5rem;
}

.ai-config-description {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin-bottom: 0.5rem;
}

.ai-providers-note {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8rem;
  color: var(--color-success-hover);
  background: rgba(5, 150, 105, 0.1);
  padding: 0.35rem 0.75rem;
  border-radius: 20px;
}

.ai-providers-note strong {
  color: var(--color-success-hover);
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
  color: var(--color-violet-dark);
  background: white;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.1);
  border: 1px solid var(--color-violet-border);
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
  border: 1px solid var(--color-violet-border);
  border-radius: 8px;
  padding: 1rem 1.25rem;
  min-width: 280px;
  text-align: left;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.08);
}

.config-section-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-violet-dark);
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
  color: var(--color-text);
  background: var(--color-violet-light-bg);
  padding: 0.375rem 0.625rem;
  border-radius: 4px;
  border: 1px solid var(--color-violet-lightest-bg);
}

.config-code .code-value {
  color: var(--color-violet-dark);
  font-weight: 600;
}

.config-hint-text {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  margin-top: 0.5rem;
}

.config-hint-text a {
  color: var(--color-violet-dark);
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
  background: linear-gradient(135deg, var(--color-violet), var(--color-violet-dark));
  top: -80px;
  right: -60px;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, var(--color-violet-border-light), var(--color-violet));
  bottom: -40px;
  left: -30px;
  animation: float 6s ease-in-out infinite reverse;
}

.circle-3 {
  width: 80px;
  height: 80px;
  background: linear-gradient(
    135deg,
    var(--color-violet-border-light),
    var(--color-violet-border-light)
  );
  top: 50%;
  left: 15%;
  animation: float 10s ease-in-out infinite;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.05);
  }
}
</style>

<template>
  <LoadingState v-if="loading" message="Loading OQL query interface..." />

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
    message="The heap dump needs to be initialized before you can execute OQL queries. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
      title="OQL Query"
      description="Execute Object Query Language queries against the heap dump"
      icon="bi-terminal"
    />

    <TabBar v-model="activeTab" :tabs="oqlTabs" class="mb-3" />

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
          Pick an <strong>example</strong> on the left to see its OQL.
        </div>
        <div v-else>
          <h6 class="picker-title">{{ selectedQuery.title }}</h6>
          <p v-if="selectedQuery.description" class="picker-desc">
            {{ selectedQuery.description }}
          </p>
          <pre class="example-oql"><code>{{ selectedQuery.query }}</code></pre>
          <button class="btn btn-sm btn-primary" @click="useExample(selectedQuery.query)">
            <i class="bi bi-arrow-right-circle me-1"></i>
            Use this query
          </button>
        </div>
      </section>
    </div>

    <!-- ==== EXECUTOR TAB ============================================ -->
    <div v-show="activeTab === 'executor'">
      <!-- Editor -->
      <div class="editor">
        <div class="editor-band">
          <span class="lang-tag">OQL</span>
          <span class="band-label">Object Query Language</span>
          <span v-if="running" class="band-status status-run">
            <span class="spinner-border spinner-border-sm me-1"></span>
            running…
          </span>
          <span v-else-if="latestRun?.status === 'success'" class="band-status status-ok">
            <i class="bi bi-check2-circle me-1"></i>success
          </span>
          <span v-else-if="latestRun?.status === 'error'" class="band-status status-err">
            <i class="bi bi-exclamation-triangle-fill me-1"></i>error
          </span>
          <span v-else class="band-status status-idle">ready</span>
        </div>
        <div class="editor-overlay-wrap">
          <pre class="editor-overlay" aria-hidden="true" v-html="highlightedActiveQuery"></pre>
          <textarea
            ref="textareaRef"
            v-model="query"
            class="editor-input"
            spellcheck="false"
            placeholder="select s from java.lang.String s where s.value.length > 100"
            @keydown.ctrl.enter.prevent="executeActive"
            @keydown.meta.enter.prevent="executeActive"
            @input="resizeTextarea"
          ></textarea>
        </div>
        <div class="editor-toolbar">
          <div class="toolbar-group">
            <button
              class="btn btn-sm btn-primary"
              @click="executeActive"
              :disabled="running || !query.trim()"
            >
              <span v-if="running" class="spinner-border spinner-border-sm me-1"></span>
              <i v-else class="bi bi-play-fill me-1"></i>
              Execute
            </button>
            <button
              class="btn btn-sm btn-outline-secondary"
              @click="clearEditor"
              :disabled="!query.trim() && !latestRun"
            >
              <i class="bi bi-x-lg me-1"></i>
              Clear
            </button>
            <div class="toolbar-divider"></div>
            <button v-if="aiAvailable" class="btn btn-sm btn-ai-assistant" @click="openAssistant">
              <i class="bi bi-stars me-1"></i>
              AI Assistant
            </button>
            <span class="kbd-hint d-none d-md-inline"> <kbd>⌘</kbd><kbd>↵</kbd> to run </span>
          </div>
          <div class="toolbar-group">
            <div
              class="form-check form-check-inline mb-0"
              title="Also scan java.lang.String instances whose decoded content exceeded the indexer's cap. Slower; off by default — the SQL pushdown path already covers all Strings within the cap."
            >
              <input
                type="checkbox"
                class="form-check-input"
                id="scanLargeStringsCheck"
                v-model="scanLargeStrings"
              />
              <label class="form-check-label small" for="scanLargeStringsCheck">
                Scan large Strings
              </label>
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

      <!-- Latest result -->
      <div v-if="latestRun" class="result-block">
        <div v-if="latestRun.status === 'error'" class="result-error">
          <strong><i class="bi bi-exclamation-triangle-fill me-2"></i>Query Error</strong>
          <p class="mb-0 mt-1">{{ latestRun.errorMessage }}</p>
        </div>
        <DataTable v-else-if="latestRun.result">
          <template #toolbar>
            <TableToolbar v-model="latestRun.resultFilter" search-placeholder="Filter...">
              <span class="latest-tag">Latest</span>
              <span class="toolbar-count">{{ getFilteredResults(latestRun).length }} results</span>
              <span v-if="latestRun.result.hasMore" class="toolbar-badge-warning"
                >limit reached</span
              >
              <span class="toolbar-meta">
                <i class="bi bi-stopwatch me-1"></i>{{ latestRun.result.executionTimeMs }}ms
              </span>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th style="width: 50px">#</th>
              <th>Object</th>
              <SortableTableHeader
                v-if="hasRetainedSize(latestRun)"
                column="retained"
                label="Retained"
                :sort-column="latestRun.sortColumn"
                :sort-direction="latestRun.sortDirection"
                align="end"
                width="100px"
                @sort="toggleSort(latestRun, $event)"
              />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(entry, index) in getFilteredResults(latestRun)"
              :key="index"
              class="result-row"
            >
              <td class="text-muted">{{ index + 1 }}</td>
              <td class="object-cell">
                <div class="object-header">
                  <code v-if="entry.className" class="class-name">{{ entry.className }}</code>
                  <InstanceActionButtons
                    :object-id="entry.objectId ?? null"
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
              <td v-if="hasRetainedSize(latestRun)" class="text-end font-monospace">
                {{ entry.retainedSize ? FormattingService.formatBytes(entry.retainedSize) : '-' }}
              </td>
            </tr>
          </tbody>
        </DataTable>
      </div>

      <!-- History (chip strip) -->
      <div v-if="history.length > 0" class="history-section">
        <div class="history-title">
          <i class="bi bi-clock-history me-1"></i>
          <span>History</span>
          <span class="count">{{ history.length }}</span>
          <button class="btn-remove-history" @click="clearHistory">
            <i class="bi bi-trash me-1"></i>
            Remove history
          </button>
        </div>
        <div class="chip-strip">
          <div
            v-for="run in history"
            :key="run.id"
            class="chip"
            :class="{ selected: selectedHistoryId === run.id }"
            @click="toggleChip(run.id)"
          >
            <span class="chip-num">{{ run.id }}</span>
            <span class="chip-q" v-html="highlightInline(run.query)"></span>
            <span class="chip-dot" :class="run.status === 'success' ? 'ok' : 'err'"></span>
          </div>
        </div>

        <!-- Selected-chip popover -->
        <div v-if="selectedRun" class="chip-popover">
          <div class="chip-popover-head">
            <span class="chip-popover-num">[{{ selectedRun.id }}]</span>
            <span class="pill" :class="`pill-${selectedRun.status}`">{{ selectedRun.status }}</span>
            <span v-if="selectedRun.result" class="popover-meta">
              <strong>{{ selectedRun.result.results.length.toLocaleString() }}</strong> rows
            </span>
            <span class="popover-meta">
              <i class="bi bi-stopwatch me-1"></i>{{ formatElapsed(selectedRun.elapsedMs) }}
            </span>
            <div class="chip-popover-actions">
              <button
                class="btn-ghost"
                @click="rerunFromHistory(selectedRun)"
                title="Re-run this query"
              >
                <i class="bi bi-arrow-clockwise"></i>
              </button>
              <button class="btn-ghost" @click="copyToEditor(selectedRun)" title="Copy into editor">
                <i class="bi bi-arrow-up-square"></i>
              </button>
              <button class="btn-ghost" @click="selectedHistoryId = null" title="Close">
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>
          <pre class="chip-popover-q" v-html="highlightQuery(selectedRun.query)"></pre>

          <div v-if="selectedRun.status === 'error'" class="popover-error">
            <strong><i class="bi bi-exclamation-triangle-fill me-2"></i>Query Error</strong>
            <p class="mb-0 mt-1">{{ selectedRun.errorMessage }}</p>
          </div>
          <DataTable v-else-if="selectedRun.result">
            <template #toolbar>
              <TableToolbar v-model="selectedRun.resultFilter" search-placeholder="Filter...">
                <span class="toolbar-count"
                  >{{ getFilteredResults(selectedRun).length }} results</span
                >
                <span v-if="selectedRun.result.hasMore" class="toolbar-badge-warning"
                  >limit reached</span
                >
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th style="width: 50px">#</th>
                <th>Object</th>
                <SortableTableHeader
                  v-if="hasRetainedSize(selectedRun)"
                  column="retained"
                  label="Retained"
                  :sort-column="selectedRun.sortColumn"
                  :sort-direction="selectedRun.sortDirection"
                  align="end"
                  width="100px"
                  @sort="toggleSort(selectedRun, $event)"
                />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(entry, index) in getFilteredResults(selectedRun)"
                :key="index"
                class="result-row"
              >
                <td class="text-muted">{{ index + 1 }}</td>
                <td class="object-cell">
                  <div class="object-header">
                    <code v-if="entry.className" class="class-name">{{ entry.className }}</code>
                    <InstanceActionButtons
                      :object-id="entry.objectId ?? null"
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
                <td v-if="hasRetainedSize(selectedRun)" class="text-end font-monospace">
                  {{ entry.retainedSize ? FormattingService.formatBytes(entry.retainedSize) : '-' }}
                </td>
              </tr>
            </tbody>
          </DataTable>
        </div>
      </div>

      <!-- AI Assistant Not Configured Panel -->
      <div
        v-if="aiChecked && !aiAvailable"
        class="ai-config-panel"
        :class="{ 'ai-config-minimized': aiPanelMinimized }"
      >
        <button class="ai-config-toggle" @click="aiPanelMinimized = !aiPanelMinimized">
          <span>{{ aiPanelMinimized ? 'Show' : 'Hide' }}</span>
          <i class="bi" :class="aiPanelMinimized ? 'bi-chevron-down' : 'bi-chevron-up'"></i>
        </button>

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

        <div v-else class="ai-config-content">
          <div class="ai-config-icon">
            <i class="bi bi-stars"></i>
          </div>
          <div class="ai-config-text">
            <h5 class="ai-config-title">AI Assistant Available</h5>
            <p class="ai-config-description">
              Unlock the power of AI to help you write OQL queries. Describe what you're looking for
              in natural language and let AI generate the query for you.
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
    </div>

    <!-- AI Assistant (overlay, always mounted) -->
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

    <!-- Instance Tree Modal (overlay, always mounted) -->
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
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import OqlAssistant from '@/components/oql/OqlAssistant.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TabBar, { type TabBarItem } from '@/components/TabBar.vue';
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

const oqlLimit = ref(50);
const scanLargeStrings = ref(false);

const showAssistant = ref(false);
const assistantExpanded = ref(false);
const aiAvailable = ref(false);
const aiChecked = ref(false);
const aiPanelMinimized = ref(sessionStorage.getItem('oql-ai-panel-minimized') === 'true');

watch(aiPanelMinimized, value => {
  sessionStorage.setItem('oql-ai-panel-minimized', String(value));
});

const showTreeModal = ref(false);
const selectedObjectId = ref<number | null>(null);
const treeMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

type OqlTab = 'executor' | 'examples';
const activeTab = ref<OqlTab>('executor');

type RunStatus = 'success' | 'error';
type SortColumn = 'retained';
type SortDir = 'asc' | 'desc';

interface QueryRun {
  id: number;
  query: string;
  status: RunStatus;
  elapsedMs: number;
  result?: OQLQueryResult;
  errorMessage?: string;
  resultFilter: string;
  sortColumn: SortColumn;
  sortDirection: SortDir;
}

const MAX_HISTORY = 20;
const HL_STR_OPEN = '__OQL_STR_OPEN__';
const HL_STR_CLOSE = '__OQL_STR_CLOSE__';

const query = ref('');
const latestRun = ref<QueryRun | null>(null);
const history = ref<QueryRun[]>([]);
const selectedHistoryId = ref<number | null>(null);
const running = ref(false);
const nextRunId = ref(1);
const textareaRef = ref<HTMLTextAreaElement | null>(null);

let client: HeapDumpClient;

const detailPanelOpen = ref(false);
const detailPanelObjectId = ref<number | null>(null);

// =============================================================================
// Example queries
// =============================================================================

interface ExampleQuery {
  title: string;
  description?: string;
  query: string;
  divider?: never;
}
interface ExampleDivider {
  title: string;
  divider: true;
  query?: never;
}
type ExampleEntry = ExampleQuery | ExampleDivider;

const exampleQueries: ExampleEntry[] = [
  { title: '--- Basics ---', divider: true },
  {
    title: 'All Strings (capped)',
    description: 'Every live java.lang.String instance, capped to the result limit.',
    query: 'SELECT * FROM java.lang.String LIMIT 50'
  },
  {
    title: 'Thread display names',
    description: 'Every live Thread instance with its Class@hex display name.',
    query: 'SELECT t.@displayName FROM java.lang.Thread t'
  },
  {
    title: 'Object IDs of Strings',
    description: 'Project just the heap-instance id for each String — useful for follow-up lookups.',
    query: 'SELECT s.@objectId, s.@displayName FROM java.lang.String s LIMIT 50'
  },

  { title: '--- Filters & string predicates ---', divider: true },
  {
    title: 'Shallow size > 1 KB',
    description: 'Strings whose shallow size exceeds 1 KB.',
    query: 'SELECT s FROM java.lang.String s WHERE sizeof(s) > 1024'
  },
  {
    title: 'Strings starting with "java."',
    description: 'String content predicate — startsWith works directly on the binding.',
    query: 'SELECT s FROM java.lang.String s WHERE startsWith(s, "java.")'
  },
  {
    title: 'Strings ending with ".class"',
    description: 'String content predicate.',
    query: 'SELECT s FROM java.lang.String s WHERE endsWith(s, ".class")'
  },
  {
    title: 'Strings containing "Exception"',
    description: 'Quick scan for embedded error messages.',
    query: 'SELECT s FROM java.lang.String s WHERE contains(s, "Exception")'
  },
  {
    title: 'Regex match on String content',
    description: 'URL-shaped Strings via a Java Pattern regex.',
    query: 'SELECT s FROM java.lang.String s WHERE matchesRegex(s, "^https?://.*")'
  },
  {
    title: 'Case-insensitive equality',
    description: 'equalsIgnoreCase on String content.',
    query: 'SELECT s FROM java.lang.String s WHERE equalsIgnoreCase(s, "OK")'
  },
  {
    title: 'Exact-equality match',
    description: 'equalsString — case-sensitive equality on decoded String content.',
    query: 'SELECT s FROM java.lang.String s WHERE equalsString(s, "java.lang.Object")'
  },
  {
    title: 'Empty Strings',
    description: 'isEmptyString — find live String instances whose decoded content is the empty string.',
    query: 'SELECT s FROM java.lang.String s WHERE isEmptyString(s)'
  },

  { title: '--- Class hierarchy ---', divider: true },
  {
    title: 'INSTANCEOF AbstractMap',
    description: 'Every instance whose class descends from java.util.AbstractMap.',
    query: 'SELECT o FROM INSTANCEOF java.util.AbstractMap o'
  },
  {
    title: 'INSTANCEOF Throwable',
    description: 'Every live throwable in the heap — exceptions, errors, custom subclasses.',
    query: 'SELECT o FROM INSTANCEOF java.lang.Throwable o'
  },
  {
    title: 'IMPLEMENTS java.util.Map',
    description:
      'Every class that implements java.util.Map. Note: standard HPROF dumps don\\u2019t record interface info; this may return empty.',
    query: 'SELECT o FROM IMPLEMENTS java.util.Map o'
  },

  { title: '--- Sizes & retention ---', divider: true },
  {
    title: 'Retained size > 1 MB (descending)',
    description: 'Top retained-size objects — likely memory hogs keeping large subgraphs alive.',
    query:
      'SELECT o.@displayName, o.@retainedHeapSize FROM INSTANCEOF java.lang.Object o ' +
      'WHERE o.@retainedHeapSize > 1048576 ORDER BY o.@retainedHeapSize DESC LIMIT 20'
  },
  {
    title: 'AS RETAINED SET for big HashMaps',
    description:
      'Expand the matched HashMaps to their full retained subgraphs — the bytes that would be freed.',
    query:
      'SELECT * AS RETAINED SET FROM java.util.HashMap m WHERE m.@retainedHeapSize > 10485760'
  },

  { title: '--- Aggregates ---', divider: true },
  {
    title: 'Class histogram with totals',
    description: 'Count and total shallow size per class — heaviest types first.',
    query:
      'SELECT classof(o).name AS cls, count(*) AS n, sum(sizeof(o)) AS total ' +
      'FROM INSTANCEOF java.lang.Object o ' +
      'GROUP BY classof(o).name HAVING count(*) > 100 ' +
      'ORDER BY total DESC LIMIT 20'
  },
  {
    title: 'Total String count',
    description: 'Just count how many Strings live in the heap.',
    query: 'SELECT count(*) FROM java.lang.String'
  },
  {
    title: 'Min/max/avg shallow size',
    description: 'Distribution of String shallow size.',
    query: 'SELECT min(sizeof(s)), max(sizeof(s)), avg(sizeof(s)) FROM java.lang.String s'
  },

  { title: '--- Path expressions & arrays ---', divider: true },
  {
    title: 'Strings with backing array > 1000',
    description: 's.value.length walks two fields (decode happens row-by-row).',
    query: 'SELECT s FROM java.lang.String s WHERE s.value.length > 1000'
  },
  {
    title: 'First entry slot of HashMap',
    description: 'Array indexing into HashMap.table[0].',
    query: 'SELECT m.table[0] FROM java.util.HashMap m'
  },
  {
    title: 'Large byte[] arrays',
    description: 'byte[] arrays over 10 KB — common for I/O buffers, encoded payloads, or copies.',
    query: 'SELECT a FROM byte[] a WHERE a.length > 10240 ORDER BY a.length DESC'
  },

  { title: '--- String accessors ---', divider: true },
  {
    title: 'Lower-cased String content',
    description: 'lower(toString(s)) — projection-side string transformation.',
    query: 'SELECT lower(toString(s)) FROM java.lang.String s LIMIT 50'
  },
  {
    title: 'Substring(0, 80) preview',
    description: 'Truncate long Strings to an 80-char preview.',
    query: 'SELECT substring(toString(s), 0, 80) FROM java.lang.String s WHERE stringLength(s) > 80'
  },
  {
    title: 'Trimmed leading-whitespace Strings',
    description: 'Find leading-whitespace Strings and show their trimmed form.',
    query: 'SELECT trim(toString(s)) FROM java.lang.String s WHERE startsWith(s, " ")'
  },
  {
    title: 'Upper-cased String content',
    description: 'upper(toString(s)) — projection-side string transformation.',
    query: 'SELECT upper(toString(s)) FROM java.lang.String s LIMIT 50'
  },
  {
    title: 'indexOf("://") on URL-shaped Strings',
    description: 'Position of "://" in each URL String — handy alongside contains().',
    query:
      'SELECT toString(s), indexOf(toString(s), "://") AS pos FROM java.lang.String s ' +
      'WHERE contains(s, "://")'
  },
  {
    title: 'lastIndexOf(".") for file extensions',
    description: 'Last dot position — useful for grouping by file extension.',
    query:
      'SELECT toString(s), lastIndexOf(toString(s), ".") AS pos FROM java.lang.String s ' +
      'WHERE endsWith(s, ".class")'
  },
  {
    title: 'First-char histogram via charAt',
    description: 'Count Strings by their first character.',
    query:
      'SELECT charAt(toString(s), 0), count(*) FROM java.lang.String s ' +
      'WHERE NOT isEmptyString(s) GROUP BY charAt(toString(s), 0) ORDER BY count(*) DESC LIMIT 20'
  },

  { title: '--- Fuzzy text ---', divider: true },
  {
    title: 'Levenshtein distance to "OutOfMemoryError"',
    description: 'Sort Strings by edit distance to a target term — useful for typo-finding.',
    query:
      'SELECT toString(s), levenshtein(toString(s), "OutOfMemoryError") AS dist ' +
      'FROM java.lang.String s ORDER BY dist ASC LIMIT 20'
  },
  {
    title: 'Jaro-Winkler similar to HashMap class name',
    description: 'Similarity > 0.85 to "java.util.HashMap" — fuzzy class-name lookup.',
    query:
      'SELECT toString(s) FROM java.lang.String s ' +
      'WHERE jaroWinklerSimilarity(toString(s), "java.util.HashMap") > 0.85'
  },

  { title: '--- Wrapper toString ---', divider: true },
  {
    title: 'Integer value decoded',
    description: 'Generalised toString decodes the boxed value field.',
    query: 'SELECT toString(i) FROM java.lang.Integer i LIMIT 50'
  },
  {
    title: 'Boolean wrappers',
    description: 'Decoded boolean wrapper instances.',
    query: 'SELECT toString(b) FROM java.lang.Boolean b'
  },

  { title: '--- Numeric & control flow ---', divider: true },
  {
    title: 'Round average shallow size',
    description: 'round(avg(...), 2) — aggregate with rounding.',
    query: 'SELECT round(avg(sizeof(s)), 2) FROM java.lang.String s'
  },
  {
    title: 'Size buckets via CASE',
    description: 'Group every Object into tiny/small/big buckets and count.',
    query:
      "SELECT CASE WHEN sizeof(o) < 64 THEN 'tiny' WHEN sizeof(o) < 1024 THEN 'small' ELSE 'big' END AS bucket, " +
      'count(*) FROM INSTANCEOF java.lang.Object o ' +
      'GROUP BY bucket ORDER BY count(*) DESC'
  },
  {
    title: 'format() template per Thread',
    description: 'SLF4J-style format placeholders.',
    query: "SELECT format('class={} size={}', classof(o).name, sizeof(o)) FROM java.lang.Thread o"
  },

  { title: '--- Graph traversal ---', divider: true },
  {
    title: 'Outbound refs per Thread',
    description: 'One row per outbound reference; each row is the referenced object.',
    query: 'SELECT outbounds(t) FROM java.lang.Thread t'
  },
  {
    title: 'Inbound refs to large Strings',
    description: 'Who references a String > 1000 chars?',
    query: 'SELECT inbounds(s) FROM java.lang.String s WHERE s.value.length > 1000'
  },
  {
    title: 'reachables() from a Thread',
    description: 'Transitive forward walk — full per-thread footprint (one row per ref, capped).',
    query: 'SELECT reachables(t) FROM java.lang.Thread t LIMIT 1'
  },
  {
    title: 'referrers() to a cache class',
    description: 'Transitive backward walk from instances of *Cache*.',
    query: 'SELECT referrers(c) FROM java.lang.Class c WHERE c.@displayName LIKE ".*Cache.*"'
  },

  { title: '--- GC roots ---', divider: true },
  {
    title: 'GC-root path for a heavy String',
    description: 'Render the GC-root path as a single string column per match.',
    query: 'SELECT root(s) FROM java.lang.String s WHERE s.value.length > 10000'
  },
  {
    title: 'GC-root path for big retained objects',
    description: 'Top retained-size offenders and the path keeping each one alive.',
    query:
      'SELECT root(o) FROM INSTANCEOF java.lang.Object o ' +
      'WHERE o.@retainedHeapSize > 10485760 LIMIT 5'
  },
  {
    title: 'All GC roots',
    description: 'Enumerate every GC root in the dump.',
    query: 'SELECT * FROM heap.roots()'
  },

  { title: '--- Dominators ---', divider: true },
  {
    title: 'Direct dominator of each HashMap',
    description: 'Who keeps each HashMap alive at the dominator-tree level?',
    query: 'SELECT dominatorof(o) FROM java.util.HashMap o'
  },
  {
    title: 'Children of big retained objects',
    description: 'Top retained-size objects and what they directly dominate.',
    query:
      'SELECT dominators(o) FROM INSTANCEOF java.lang.Object o ' +
      'WHERE o.@retainedHeapSize > 1048576 LIMIT 20'
  },

  { title: '--- Heap helpers ---', divider: true },
  {
    title: 'All loaded classes',
    description: 'Enumerate every loaded class in the dump.',
    query: 'SELECT * FROM heap.classes()'
  },
  {
    title: 'Lookup class by name',
    description: 'Resolve a class — building block for follow-up queries.',
    query: 'SELECT * FROM heap.findClass("java.lang.String")'
  },
  {
    title: 'Lookup object by address',
    description: 'Resolve a specific instance by its hex object address.',
    query: 'SELECT * FROM heap.findObject(0xCAFEBABE)'
  },

  { title: '--- UNION & subqueries ---', divider: true },
  {
    title: 'UNION of two FROM sources',
    description: 'Combine results of two SELECTs.',
    query:
      '(SELECT s FROM java.lang.String s WHERE startsWith(s, "java.")) UNION ' +
      '(SELECT t FROM java.lang.Thread t)'
  },
  {
    title: 'count(*) of a filtered subquery',
    description: 'Aggregate over a derived table.',
    query:
      'SELECT count(*) FROM (SELECT s FROM java.lang.String s WHERE stringLength(s) > 100)'
  }
];

const queryGroups = computed(() => {
  const groups: { title: string; items: ExampleQuery[] }[] = [];
  let current: { title: string; items: ExampleQuery[] } | null = null;
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

const oqlTabs = computed<TabBarItem[]>(() => [
  { id: 'executor', label: 'Executor', icon: 'terminal' },
  { id: 'examples', label: 'Examples', icon: 'lightbulb' }
]);

const selectedQuery = ref<ExampleQuery | null>(null);
const selectQuery = (ex: ExampleQuery) => {
  selectedQuery.value = ex;
};

// =============================================================================
// Syntax highlighting
// =============================================================================

const OQL_KEYWORDS = new Set([
  'select',
  'from',
  'where',
  'instanceof',
  'and',
  'or',
  'not',
  'distinct',
  'order',
  'by',
  'asc',
  'desc',
  'group'
]);

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function highlightQuery(input: string): string {
  if (!input) return '';
  const strings: string[] = [];
  let placeholder = input.replace(/"([^"\\]*(?:\\.[^"\\]*)*)"/g, (_match, content: string) => {
    strings.push(content);
    return HL_STR_OPEN + (strings.length - 1) + HL_STR_CLOSE;
  });
  placeholder = escapeHtml(placeholder);
  placeholder = placeholder.replace(/\b([A-Za-z_][A-Za-z0-9_]*)\b/g, token => {
    if (OQL_KEYWORDS.has(token.toLowerCase())) {
      return '<span class="hl-kw">' + token + '</span>';
    }
    return token;
  });
  const re = new RegExp(HL_STR_OPEN + '(\\d+)' + HL_STR_CLOSE, 'g');
  placeholder = placeholder.replace(re, (_match, idx: string) => {
    const raw = strings[Number(idx)] ?? '';
    return '<span class="hl-str">&quot;' + escapeHtml(raw) + '&quot;</span>';
  });
  return placeholder;
}

function highlightInline(input: string): string {
  return highlightQuery(input.replace(/\s+/g, ' ').trim());
}

const highlightedActiveQuery = computed(() => {
  const trailing = query.value.endsWith('\n') ? ' ' : '';
  return highlightQuery(query.value) + trailing;
});

// =============================================================================
// Helpers
// =============================================================================

function formatElapsed(ms: number): string {
  if (ms < 1000) return Math.round(ms) + 'ms';
  return (ms / 1000).toFixed(2) + 's';
}

const truncateValue = (value: string, maxLength: number = 150): string => {
  if (value.length <= maxLength) return value;
  return value.substring(0, maxLength) + '...';
};

function hasRetainedSize(run: QueryRun): boolean {
  if (!run.result || run.result.results.length === 0) return false;
  return run.result.results.some(entry => entry.retainedSize !== null);
}

function getFilteredResults(run: QueryRun) {
  if (!run.result) return [];
  let results = [...run.result.results];
  const filter = run.resultFilter.trim().toLowerCase();
  if (filter) {
    results = results.filter(
      entry =>
        (entry.className && entry.className.toLowerCase().includes(filter)) ||
        (entry.value && entry.value.toLowerCase().includes(filter))
    );
  }
  const direction = run.sortDirection === 'asc' ? 1 : -1;
  if (run.sortColumn === 'retained') {
    results.sort((a, b) => direction * ((a.retainedSize || 0) - (b.retainedSize || 0)));
  }
  return results;
}

function toggleSort(run: QueryRun, column: string) {
  if (run.sortColumn === column) {
    run.sortDirection = run.sortDirection === 'asc' ? 'desc' : 'asc';
  } else {
    run.sortColumn = column as SortColumn;
    run.sortDirection = 'desc';
  }
}

// =============================================================================
// Editor sizing
// =============================================================================

function resizeTextarea() {
  nextTick(() => {
    const el = textareaRef.value;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.max(el.scrollHeight, 80) + 'px';
  });
}

watch(query, () => resizeTextarea(), { flush: 'post' });

// =============================================================================
// Execute / Clear / History
// =============================================================================

function makeRun(
  status: RunStatus,
  src: string,
  elapsedMs: number,
  result?: OQLQueryResult,
  errorMessage?: string
): QueryRun {
  return {
    id: nextRunId.value++,
    query: src,
    status,
    elapsedMs,
    result,
    errorMessage,
    resultFilter: '',
    sortColumn: 'retained',
    sortDirection: 'desc'
  };
}

async function executeActive() {
  if (!query.value.trim() || running.value) return;
  selectedHistoryId.value = null;
  running.value = true;
  const start = performance.now();
  const previousLatest = latestRun.value;
  const sourceQuery = query.value;

  try {
    const result = await client.executeQuery(
      sourceQuery,
      oqlLimit.value,
      0,
      true,
      scanLargeStrings.value
    );
    const clientElapsed = performance.now() - start;
    let newRun: QueryRun;
    if (result.errorMessage) {
      newRun = makeRun('error', sourceQuery, clientElapsed, undefined, result.errorMessage);
    } else {
      newRun = makeRun('success', sourceQuery, result.executionTimeMs ?? clientElapsed, result);
    }
    promoteToHistory(previousLatest, sourceQuery);
    latestRun.value = newRun;
  } catch (err) {
    const newRun = makeRun(
      'error',
      sourceQuery,
      performance.now() - start,
      undefined,
      err instanceof Error ? err.message : 'Query execution failed'
    );
    promoteToHistory(previousLatest, sourceQuery);
    latestRun.value = newRun;
  } finally {
    running.value = false;
  }
}

function promoteToHistory(previous: QueryRun | null, newQuery: string) {
  if (!previous) return;
  if (previous.query === newQuery) return;
  history.value = [previous, ...history.value].slice(0, MAX_HISTORY);
}

function clearEditor() {
  query.value = '';
  latestRun.value = null;
  resizeTextarea();
  focusEditor();
}

function clearHistory() {
  history.value = [];
  selectedHistoryId.value = null;
}

function toggleChip(id: number) {
  selectedHistoryId.value = selectedHistoryId.value === id ? null : id;
}

const selectedRun = computed<QueryRun | null>(() => {
  if (selectedHistoryId.value === null) return null;
  return history.value.find(r => r.id === selectedHistoryId.value) ?? null;
});

function rerunFromHistory(run: QueryRun) {
  query.value = run.query;
  selectedHistoryId.value = null;
  void executeActive();
}

function copyToEditor(run: QueryRun) {
  query.value = run.query;
  selectedHistoryId.value = null;
  focusEditor();
}

function focusEditor() {
  nextTick(() => {
    textareaRef.value?.focus();
  });
}

// =============================================================================
// Examples -> editor
// =============================================================================

const useExample = (q: string) => {
  query.value = q;
  activeTab.value = 'executor';
  focusEditor();
};

// =============================================================================
// AI Assistant
// =============================================================================

const openAssistant = () => {
  showAssistant.value = true;
  assistantExpanded.value = true;
};

const closeAssistant = () => {
  showAssistant.value = false;
  assistantExpanded.value = false;
};

const applyQueryFromAssistant = (q: string) => {
  query.value = q;
  closeAssistant();
  focusEditor();
};

const runQueryFromAssistant = async (q: string) => {
  query.value = q;
  closeAssistant();
  await executeActive();
};

// =============================================================================
// Modals
// =============================================================================

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  selectedObjectId.value = objectId;
  treeMode.value = mode;
  showTreeModal.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  router.push('/profiles/' + profileId + '/heap-dump/gc-root-path?objectId=' + objectId);
};

const openInstanceDetailPanel = (objectId: number) => {
  detailPanelObjectId.value = objectId;
  detailPanelOpen.value = true;
};

// =============================================================================
// AI availability + page load
// =============================================================================

const checkAiAvailability = async () => {
  try {
    const aiClient = new OqlAssistantClient(profileId);
    const status = await aiClient.getStatus();
    aiAvailable.value = status.enabled && status.configured;
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
  resizeTextarea();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

/* ==== EXAMPLES TAB ============================================ */
.examples-panel {
  display: grid;
  grid-template-columns: 340px 1fr;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  overflow: hidden;
  /* Pin the panel to the top of the workspace scroll container with a
     viewport-height cap; the rail scrolls internally so the preview pane
     on the right (selected example + "Use this query" button) is always
     visible. */
  position: sticky;
  top: 1rem;
  height: calc(100vh - 2rem);
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
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
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
.example-oql {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 0.75rem 1rem;
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--color-text);
  white-space: pre-wrap;
  margin: 0 0 0.75rem;
}

/* ==== EDITOR =============================================================== */
.editor {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: white;
  overflow: hidden;
  margin-bottom: 0.9rem;
}
.editor-band {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.85rem;
  background: var(--color-bg-hover);
  border-bottom: 1px solid var(--color-border-light);
  font-size: 0.72rem;
  color: var(--color-text-muted);
}
.lang-tag {
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-weight: 700;
  color: var(--color-purple);
  background: var(--color-primary-light);
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 0.65rem;
  letter-spacing: 0.4px;
}
.band-label {
  color: var(--color-text-muted);
}
.band-status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  font-size: 0.72rem;
}
.band-status.status-idle {
  color: var(--color-text-light);
}
.band-status.status-run {
  color: var(--color-info);
}
.band-status.status-ok {
  color: var(--color-success-hover);
}
.band-status.status-err {
  color: var(--color-danger);
}

/* Overlay editor */
.editor-overlay-wrap {
  position: relative;
  background: white;
}
.editor-overlay,
.editor-input {
  margin: 0;
  padding: 0.85rem 1rem;
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.875rem;
  line-height: 1.65;
  letter-spacing: 0;
  border: 0;
  box-sizing: border-box;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-wrap: break-word;
  tab-size: 2;
}
.editor-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  color: var(--color-text);
  background: transparent;
  overflow: hidden;
}
.editor-input {
  position: relative;
  display: block;
  width: 100%;
  min-height: 80px;
  background: transparent;
  color: transparent;
  caret-color: var(--color-text);
  resize: none;
  outline: none;
  overflow: hidden;
}
.editor-input::placeholder {
  color: var(--color-text-light);
  -webkit-text-fill-color: var(--color-text-light);
  font-style: italic;
}

:deep(.hl-kw) {
  color: var(--color-purple);
  font-weight: 600;
}
:deep(.hl-str) {
  color: var(--color-warning-hover);
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.85rem;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-hover);
  gap: 0.6rem;
  flex-wrap: wrap;
}
.toolbar-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.editor-toolbar .btn {
  padding: 0.35rem 0.7rem;
  font-size: 0.78rem;
  display: inline-flex;
  align-items: center;
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
.kbd-hint {
  font-size: 0.7rem;
  color: var(--color-text-light);
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}
.kbd-hint kbd {
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.65rem;
  background: white;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: 3px;
  padding: 0 4px;
  color: var(--color-text-muted);
}

/* ==== RESULT block ======================================================== */
.result-block {
  margin-bottom: 1rem;
}
.result-error,
.popover-error {
  padding: 0.85rem 1rem;
  background: var(--color-danger-light);
  color: var(--color-danger);
  font-size: 0.85rem;
  border: 1px solid var(--color-danger-light);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-base);
}
.result-error strong,
.popover-error strong {
  display: block;
  font-weight: 600;
}

.latest-tag {
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-weight: 700;
  color: var(--color-purple);
  background: var(--color-primary-light);
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 0.65rem;
  letter-spacing: 0.4px;
  text-transform: uppercase;
}

.toolbar-count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}
.toolbar-badge-warning {
  font-size: 0.65rem;
  background: var(--color-warning-light);
  color: var(--color-warning-hover);
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
.object-cell {
  position: relative;
}
.object-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* ==== HISTORY (chip strip) =============================================== */
.history-section {
  margin-top: 0.75rem;
  margin-bottom: 1rem;
}
.history-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
  margin: 0 0 0.65rem;
}
.history-title .count {
  background: var(--color-border-light);
  color: var(--color-text);
  font-size: 0.65rem;
  padding: 1px 7px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0;
}
.btn-remove-history {
  margin-left: auto;
  appearance: none;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 0.25rem 0.65rem;
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease;
}
.btn-remove-history:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
  background: var(--color-danger-light);
}

.chip-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.72rem;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 0.25rem 0.7rem 0.25rem 0.4rem;
  cursor: pointer;
  max-width: 320px;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}
.chip:hover {
  border-color: var(--color-purple);
  background: var(--color-primary-light);
}
.chip.selected {
  border-color: var(--color-purple);
  background: var(--color-primary-light);
}
.chip-num {
  font-weight: 700;
  color: var(--color-purple);
  font-size: 0.65rem;
  background: var(--color-primary-light);
  padding: 1px 6px;
  border-radius: 999px;
  flex-shrink: 0;
}
.chip.selected .chip-num {
  background: white;
}
.chip-q {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
}
.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.chip-dot.ok {
  background: var(--color-success);
}
.chip-dot.err {
  background: var(--color-danger);
}

.chip-popover {
  margin-top: 0.65rem;
  background: white;
  border: 1px solid var(--color-purple);
  border-radius: var(--radius-base);
  padding: 0.65rem 0.85rem 0.85rem;
  box-shadow: 0 4px 12px rgba(111, 66, 193, 0.08);
}
.chip-popover-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
  flex-wrap: wrap;
}
.chip-popover-num {
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-purple);
}
.popover-meta {
  font-size: 0.74rem;
  color: var(--color-text-muted);
}
.popover-meta strong {
  color: var(--color-text);
}
.chip-popover-actions {
  margin-left: auto;
  display: flex;
  gap: 0.1rem;
}
.btn-ghost {
  appearance: none;
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  padding: 0.25rem 0.45rem;
  border-radius: var(--radius-base);
  font-size: 0.85rem;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}
.btn-ghost:hover {
  background: var(--color-primary-light);
  color: var(--color-purple);
}
.chip-popover-q {
  font-family: var(--font-family-monospace, ui-monospace, monospace);
  font-size: 0.78rem;
  background: var(--color-bg-hover);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-base);
  padding: 0.5rem 0.75rem;
  margin: 0 0 0.6rem;
  line-height: 1.55;
  white-space: pre-wrap;
}

.pill {
  font-size: 0.62rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 2px 7px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
}
.pill-success {
  color: var(--color-success-hover);
  background: var(--color-success-light);
}
.pill-error {
  color: var(--color-danger-hover);
  background: var(--color-danger-light);
}

/* ==== AI Assistant Button ================================================ */
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

/* AI Configuration Panel */
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

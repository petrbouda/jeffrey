<template>
  <LoadingState v-if="loading" message="Loading class loader analysis..." />

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
    icon="diagram-3"
    message="The heap dump needs to be initialized before you can view class loader analysis. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <HeapDumpNotInitialized
    v-else-if="!report"
    icon="diagram-3"
    message="The class loader analysis is not available for this heap dump. Re-initialize the heap dump from the Heap Dump Overview to populate it."
  />

  <!-- Analysis Results -->
  <div v-else>
    <PageHeader
      title="Class Loader Analysis"
      description="Analyze class loaders and detect duplicate classes"
      icon="bi-diagram-3"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Tabbed Analysis Section -->
    <TabBar v-model="activeTab" :tabs="analysisTabs" class="mb-3" />

    <!-- Suspicious Loaders Tab -->
    <div v-show="activeTab === 'suspicious-loaders'">
        <div v-if="report && (report.leakChains ?? []).length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">
                  Showing {{ report.leakChains.length }} suspicious class loaders
                </span>
              </TableToolbar>
            </template>
            <thead>
              <tr>
                <th style="width: 40px">#</th>
                <th>Class Loader</th>
                <th class="text-end" style="width: 150px">Class Count</th>
                <th class="text-end" style="width: 140px">Retained Size</th>
                <th>Cause Hints</th>
                <th style="width: 110px">Details</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(chain, idx) in report.leakChains"
                :key="chain.classLoaderId"
                class="clickable-row"
                @click="openDrawer(chain.classLoaderId)"
              >
                <td class="text-muted">{{ idx + 1 }}</td>
                <td>
                  <ClassNameDisplay :class-name="chain.classLoaderClassName" />
                  <span
                    v-if="chain.hasDuplicateClasses"
                    class="badge bg-warning text-dark mt-1"
                  >duplicate classes</span>
                </td>
                <td class="text-end font-monospace">
                  {{ FormattingService.formatNumber(chain.classCount) }}
                </td>
                <td class="text-end font-monospace">
                  {{ FormattingService.formatBytes(chain.retainedSize) }}
                </td>
                <td>
                  <span
                    v-for="(hint, hi) in chain.causeHints"
                    :key="hi"
                    class="badge bg-info text-dark me-1"
                    :title="hint.description"
                  >{{ hintLabel(hint) }}</span>
                  <span
                    v-if="chain.causeHints.length === 0"
                    class="text-muted small"
                  >no patterns matched</span>
                </td>
                <td>
                  <button
                    class="btn btn-sm btn-outline-primary"
                    @click.stop="openDrawer(chain.classLoaderId)"
                  >
                    <i class="bi bi-arrow-right-circle me-1"></i> Open
                  </button>
                </td>
              </tr>
            </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-shield-check fs-1 mb-3 d-block text-success"></i>
          <p>No suspicious class loaders detected.</p>
        </div>
    </div>

    <!-- Class Loaders (tree) Tab -->
    <div v-show="activeTab === 'class-loaders'">
      <ClassLoaderHierarchyTree
        v-if="report"
        :loaders="report.classLoaders"
        :hierarchy-edges="report.hierarchyEdges ?? []"
        :unloadability="report.unloadability ?? {}"
        :loader-types="report.loaderTypes ?? {}"
        :suspicious-loader-ids="suspiciousLoaderIds"
        :max-retained-size="maxLoaderRetainedSize"
        @loader-click="openDrawer"
      />
    </div>

    <!-- Unloadability Tab -->
    <div v-show="activeTab === 'unloadability'">
      <div class="unloadability-toolbar">
        <div class="btn-group filter-chips" role="group" aria-label="Filter by verdict">
          <button
            v-for="opt in unloadabilityFilterOptions"
            :key="opt.id"
            type="button"
            class="btn btn-sm"
            :class="unloadabilityFilter === opt.id ? 'btn-primary' : 'btn-outline-secondary'"
            @click="unloadabilityFilter = opt.id"
          >
            {{ opt.label }}
            <span class="chip-count">{{ unloadabilityCounts[opt.id] }}</span>
          </button>
        </div>
      </div>

      <div v-if="unloadabilityRows.length === 0" class="text-center text-muted py-5">
        <i class="bi bi-shield fs-1 mb-3 d-block"></i>
        <p>Unloadability data is not available in this report. Re-run the analysis to populate it.</p>
      </div>

      <div v-else-if="sortedUnloadabilityRows.length === 0" class="text-center text-muted py-5">
        <i class="bi bi-funnel fs-1 mb-3 d-block"></i>
        <p>No class loaders match the selected filter.</p>
      </div>

      <DataTable v-else>
        <template #toolbar>
          <TableToolbar :show-search="false">
            <span class="toolbar-info">
              Showing {{ sortedUnloadabilityRows.length }} of {{ unloadabilityRows.length }} class loaders
            </span>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th style="width: 40px">#</th>
            <th>Class Loader</th>
            <th style="width: 170px">Verdict</th>
            <SortableTableHeader
              column="classCount"
              label="Class Count"
              :sort-column="unloadabilitySortColumn"
              :sort-direction="unloadabilitySortDirection"
              align="end"
              width="130px"
              @sort="toggleUnloadabilitySort"
            />
            <SortableTableHeader
              column="liveInstanceCount"
              label="Live Instances"
              :sort-column="unloadabilitySortColumn"
              :sort-direction="unloadabilitySortDirection"
              align="end"
              width="150px"
              @sort="toggleUnloadabilitySort"
            />
            <th>Top Blocking Classes</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, idx) in sortedUnloadabilityRows"
            :key="row.loaderId"
            class="clickable-row"
            @click="openDrawer(row.loaderId)"
          >
            <td class="text-muted">{{ idx + 1 }}</td>
            <td>
              <div class="class-cell">
                <ClassNameDisplay :class-name="row.loaderClassName" />
                <span
                  v-if="suspiciousLoaderIds.has(row.loaderId)"
                  class="badge bg-danger text-white"
                >suspicious</span>
              </div>
            </td>
            <td>
              <Badge
                :value="verdictLabel(row.loaderId)"
                :variant="verdictVariant(row.loaderId)"
                size="xxs"
              />
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(row.classCount) }}
            </td>
            <td class="text-end font-monospace">
              {{ FormattingService.formatNumber(row.liveInstanceCount) }}
            </td>
            <td>
              <span v-if="row.topBlockingClasses.length === 0" class="text-muted small">
                no blocking classes
              </span>
              <span v-else class="blocking-preview">
                <span
                  v-for="(cls, i) in row.topBlockingClasses.slice(0, 2)"
                  :key="cls.classId"
                  class="blocking-chip"
                  :title="cls.name"
                >
                  {{ simpleClassName(cls.name) }}
                  <span class="blocking-count">{{ FormattingService.formatNumber(cls.instanceCount) }}</span>
                  <span v-if="i === 0 && row.topBlockingClasses.slice(0, 2).length > 1" class="blocking-sep">·</span>
                </span>
                <span v-if="row.topBlockingClasses.length > 2" class="blocking-more">
                  +{{ row.topBlockingClasses.length - 2 }} more
                </span>
              </span>
            </td>
          </tr>
        </tbody>
      </DataTable>
    </div>

    <!-- Duplicate Classes Tab -->
    <div v-show="activeTab === 'duplicate-classes'">
        <div v-if="report && report.duplicateClasses.length > 0">
          <DataTable>
            <template #toolbar>
              <TableToolbar :show-search="false">
                <span class="toolbar-info">Showing {{ report.duplicateClasses.length }} duplicate classes</span>
              </TableToolbar>
            </template>
                <thead>
                  <tr>
                    <th style="width: 40px">#</th>
                    <th>Class Name</th>
                    <SortableTableHeader
                      column="loaderCount"
                      label="Loader Count"
                      :sort-column="dupSortColumn"
                      :sort-direction="dupSortDirection"
                      align="end"
                      width="120px"
                      @sort="toggleDupSort"
                    />
                    <th>Class Loader Names</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(entry, index) in sortedDuplicateClasses" :key="entry.className">
                    <td class="text-muted">{{ index + 1 }}</td>
                    <td>
                      <ClassNameDisplay :class-name="entry.className" />
                    </td>
                    <td class="text-end font-monospace">
                      <span :class="entry.loaderCount > 2 ? 'badge bg-warning text-dark' : ''">
                        {{ entry.loaderCount }}
                      </span>
                    </td>
                    <td>
                      <span class="loader-names">{{ entry.classLoaderNames.join(', ') }}</span>
                    </td>
                  </tr>
                </tbody>
          </DataTable>
        </div>
        <div v-else class="text-center text-muted py-5">
          <i class="bi bi-check-circle fs-1 mb-3 d-block"></i>
          <p>No duplicate classes detected.</p>
        </div>
    </div>

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'about'">
      <div class="about-container">
        <!-- Header Section -->
        <div class="about-header">
          <div class="about-header-icon">
            <i class="bi bi-diagram-3"></i>
          </div>
          <div>
            <h5 class="mb-1">Understanding Class Loaders</h5>
            <p class="text-muted mb-0">How the JVM organises class definitions and what keeps them alive</p>
          </div>
        </div>

        <!-- Intro -->
        <div class="about-intro">
          <p class="mb-2">
            A class loader is the JVM object responsible for turning a binary class file into a
            <code>Class&lt;?&gt;</code> the runtime can execute. Every loaded class is tied to exactly
            one loader, and loaders form a parent-child chain: a request to load
            <code>java.util.List</code> walks up to the bootstrap loader before any application
            loader is asked. This delegation model lets containers (Tomcat, OSGi, Spring Boot fat
            jars) safely isolate webapps while still sharing the JDK underneath.
          </p>
          <p class="mb-0">
            Loaders that cannot be garbage collected become a problem on hot redeploys: every reload
            cycle leaks an entire copy of the application's classes, eventually exhausting metaspace.
            The four views on this page help you locate the loaders living on your heap, see how they
            relate to each other, and identify the ones the GC cannot reclaim.
          </p>
        </div>

        <!-- The four views -->
        <h6 class="section-title">
          <i class="bi bi-grid-3x3-gap me-2"></i>
          The Views
        </h6>

        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon icon-primary">
              <i class="bi bi-diagram-3"></i>
            </div>
            <div class="feature-content">
              <h6>Class Loaders</h6>
              <p>
                Parent-child tree of every loader, reconstructed from each loader's
                <code>parent</code> field. Bootstrap is the root; everything else branches below
                it. Each node shows type, unloadability verdict, class count, and retained size.
                Click a row to open the detail drawer.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-warning">
              <i class="bi bi-shield-exclamation"></i>
            </div>
            <div class="feature-content">
              <h6>Unloadability</h6>
              <p>
                Audit view ranking every loader the GC <em>cannot</em> currently reclaim. Filter
                chips at the top let you focus on <code>pinned (transitive)</code> — the leak
                signature — or browse all verdicts. Each row previews the top blocking classes
                inline.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-info">
              <i class="bi bi-files"></i>
            </div>
            <div class="feature-content">
              <h6>Duplicate Classes</h6>
              <p>
                Class names that show up under more than one loader. Some duplication is normal in
                container apps (each webapp has its own copy of common libraries), but excessive
                duplication usually means a shaded jar or version conflict that bloats metaspace.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-danger">
              <i class="bi bi-bug"></i>
            </div>
            <div class="feature-content">
              <h6>Suspicious Loaders</h6>
              <p>
                Shown only when leak patterns match. Each suspect is annotated with the canonical
                cause hints (ThreadLocal, JDBC, JNI, ServiceLoader, Logger, context ClassLoader) and
                a GC-root path. Start here when you suspect a redeploy leak.
              </p>
            </div>
          </div>
        </div>

        <!-- Unloadability verdicts -->
        <h6 class="section-title">
          <i class="bi bi-shield me-2"></i>
          Unloadability Verdicts
        </h6>

        <div class="about-intro">
          <p class="mb-0">
            For each loader the analyzer answers: <em>could the next metaspace-aware GC cycle
            actually unload this class loader?</em> The answer combines two facts — whether the
            loader instance itself is a GC root, and whether any class it defined still has live
            instances on the heap.
          </p>
        </div>

        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon icon-success">
              <i class="bi bi-check-circle"></i>
            </div>
            <div class="feature-content">
              <h6>
                <Badge value="unloadable" variant="success" size="xxs" class="me-1" />
              </h6>
              <p>
                Not a GC root and no live instances of its classes remain. The JVM is free to
                unload this loader at the next metaspace-aware GC. A clean redeploy should leave
                the old loader in this state.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-secondary">
              <i class="bi bi-anchor"></i>
            </div>
            <div class="feature-content">
              <h6>
                <Badge value="rooted" variant="secondary" size="xxs" class="me-1" />
              </h6>
              <p>
                The loader instance is itself referenced by a GC root — typically the case for
                Bootstrap, Platform and System loaders, or for any application loader still held by
                a running thread. Its lifetime matches the JVM's.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-warning">
              <i class="bi bi-exclamation-triangle"></i>
            </div>
            <div class="feature-content">
              <h6>
                <Badge value="pinned" variant="warning" size="xxs" class="me-1" />
              </h6>
              <p>
                The loader is not a GC root, yet instances of its classes are still reachable on
                the heap. This is the canonical "classloader leak" signature — something else on
                the heap is keeping classes from the old loader alive.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-info">
              <i class="bi bi-info-circle"></i>
            </div>
            <div class="feature-content">
              <h6>Heuristic caveat</h6>
              <p>
                The verdict only inspects whether <em>this</em> loader instance is in the GC-root
                table. Parent loaders that are kept alive only through their rooted child (a
                <code>parent</code> back-pointer) can show up as <code>pinned</code> even when
                they're behaving normally. Treat a single <code>pinned</code> badge as a hint, not
                a proof of leak.
              </p>
            </div>
          </div>
        </div>

        <!-- Common leak patterns -->
        <h6 class="section-title">
          <i class="bi bi-lightning-charge me-2"></i>
          Common Leak Patterns (Cause Hints)
        </h6>

        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon icon-purple">
              <i class="bi bi-arrow-repeat"></i>
            </div>
            <div class="feature-content">
              <h6>ThreadLocal</h6>
              <p>
                A <code>ThreadLocal&lt;?&gt;</code> entry on a thread pool worker still holds an
                object from the old loader. Because the worker thread itself is rooted, the chain
                back through <code>ThreadLocalMap.Entry</code> keeps the loader alive forever.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-info">
              <i class="bi bi-server"></i>
            </div>
            <div class="feature-content">
              <h6>JDBC Driver</h6>
              <p>
                <code>java.sql.DriverManager</code> holds a static <code>Vector</code> of every
                registered driver. If a webapp's JDBC driver was registered but never deregistered
                on undeploy, DriverManager keeps the webapp's loader alive through the driver's
                <code>Class</code> reference.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-warning">
              <i class="bi bi-cpu"></i>
            </div>
            <div class="feature-content">
              <h6>JNI Global</h6>
              <p>
                A native library created a JNI global reference to an object in the webapp's
                loader. The JVM can't track these from Java code — only an explicit
                <code>DeleteGlobalRef</code> in the native side frees them.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-success">
              <i class="bi bi-plug"></i>
            </div>
            <div class="feature-content">
              <h6>ServiceLoader</h6>
              <p>
                <code>ServiceLoader</code> caches discovered providers. When that cache is held by
                a longer-lived loader (the JDK's system loader, for example) and the providers come
                from a shorter-lived loader, the cache pins the providers' loader.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-primary">
              <i class="bi bi-card-text"></i>
            </div>
            <div class="feature-content">
              <h6>Logger</h6>
              <p>
                Static logger registries (<code>LogManager</code>, log4j/logback context maps)
                cache Logger instances by class. The Logger's <code>resourceBundleName</code>
                resolution can pin the loader that contributed the logger class.
              </p>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-danger">
              <i class="bi bi-link-45deg"></i>
            </div>
            <div class="feature-content">
              <h6>Context ClassLoader</h6>
              <p>
                A thread's <code>contextClassLoader</code> still points at the old webapp loader.
                Common with thread pools shared across redeploys: every worker holds a back
                reference to the loader that started it.
              </p>
            </div>
          </div>
        </div>

        <!-- Reading the numbers -->
        <h6 class="section-title">
          <i class="bi bi-rulers me-2"></i>
          Reading the Numbers
        </h6>

        <div class="benefits-list">
          <div class="benefit-item">
            <i class="bi bi-info-circle-fill text-info"></i>
            <span>
              <strong>Retained Size</strong> on every row is the dominator subtree of the loader's
              own <code>ClassLoader</code> object — not the sum of all classes it defined. Summing
              retained bytes across every class would double-count shared subgraphs and exceed the
              heap. Bootstrap therefore always reports 0 B (there's no real instance).
            </span>
          </div>
          <div class="benefit-item">
            <i class="bi bi-info-circle-fill text-info"></i>
            <span>
              <strong>Class Count</strong> includes synthetic primitive-array classes
              (<code>int[]</code>, <code>byte[]</code>, &hellip;) attributed to the bootstrap
              loader — which is why bootstrap dominates the class count.
            </span>
          </div>
          <div class="benefit-item">
            <i class="bi bi-info-circle-fill text-info"></i>
            <span>
              <strong>Live Instances</strong> on the detail drawer is the count of <em>all</em>
              instances of every class loaded by this loader, excluding the loader instance itself.
              It's the figure that drives the <code>pinned</code> verdict and the "top blocking
              classes" panel.
            </span>
          </div>
          <div class="benefit-item">
            <i class="bi bi-info-circle-fill text-info"></i>
            <span>
              <strong>Total Shallow</strong> in the per-class table is the sum of every instance's
              shallow size (header + payload) — and that's the only size metric shown per class.
              Retained-heap per class is deliberately omitted: summing it would double-count
              nested dominator subtrees (tries, linked lists) and routinely exceed the whole heap.
            </span>
          </div>
        </div>

        <!-- Note -->
        <div class="about-note">
          <div class="note-icon">
            <i class="bi bi-lightbulb-fill"></i>
          </div>
          <div class="note-content">
            <strong>Investigating a Classloader Leak?</strong>
            <p class="mb-0">
              Start in <em>Suspicious Loaders</em> — the GC-root path and cause hints will usually
              point at the exact reference chain. If nothing matches, switch to
              <em>Unloadability</em> and select the <code>pinned (transitive)</code> chip:
              everything the GC can't reclaim will be ranked by live-instance count, with the top
              blocking classes shown inline. A loader that defines only a handful of classes but
              stays pinned is the classic redeploy-leak signature.
            </p>
          </div>
        </div>
      </div>
    </div>

    <ClassLoaderDetailDrawer
      v-model:show="showDrawer"
      :loader-id="selectedLoaderId"
      :profile-id="profileId"
      @open-loader="openDrawer"
    />
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
import TabBar from '@/components/TabBar.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import Badge from '@/components/Badge.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import ClassLoaderHierarchyTree from '@/components/heap/ClassLoaderHierarchyTree.vue';
import ClassLoaderDetailDrawer from '@/components/heap/ClassLoaderDetailDrawer.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type ClassLoaderReport from '@/services/api/model/ClassLoaderReport';
import type { CauseHint } from '@/services/api/model/ClassLoaderReport';
import FormattingService from '@/services/FormattingService';
import type { Variant } from '@/types/ui';

const route = useRoute();

const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const report = ref<ClassLoaderReport | null>(null);
const activeTab = ref<string>('class-loaders');

// Detail drawer state
const showDrawer = ref(false);
const selectedLoaderId = ref<number | null>(null);

const openDrawer = (loaderId: number) => {
  selectedLoaderId.value = loaderId;
  showDrawer.value = true;
};

const hintLabel = (hint: CauseHint): string => {
  switch (hint.kind) {
    case 'THREAD_LOCAL':
      return 'ThreadLocal';
    case 'JDBC_DRIVER':
      return 'JDBC';
    case 'JNI_GLOBAL':
      return 'JNI';
    case 'SERVICE_LOADER':
      return 'ServiceLoader';
    case 'LOGGER':
      return 'Logger';
    case 'CONTEXT_CLASSLOADER':
      return 'ctx CL';
    default:
      return hint.kind;
  }
};

const suspiciousLoaderIds = computed<Set<number>>(() => {
  const ids = new Set<number>();
  (report.value?.leakChains ?? []).forEach((c) => ids.add(c.classLoaderId));
  return ids;
});

// Sort state for duplicate classes table
const dupSortColumn = ref('loaderCount');
const dupSortDirection = ref<'asc' | 'desc'>('desc');

// Unloadability tab state
type UnloadabilityFilter = 'all' | 'UNLOADABLE' | 'PINNED_ROOTED' | 'PINNED_TRANSITIVE';
const unloadabilityFilter = ref<UnloadabilityFilter>('PINNED_TRANSITIVE');
const unloadabilitySortColumn = ref<string>('liveInstanceCount');
const unloadabilitySortDirection = ref<'asc' | 'desc'>('desc');

const unloadabilityFilterOptions: Array<{ id: UnloadabilityFilter; label: string }> = [
  { id: 'all', label: 'All' },
  { id: 'UNLOADABLE', label: 'Unloadable' },
  { id: 'PINNED_ROOTED', label: 'Rooted' },
  { id: 'PINNED_TRANSITIVE', label: 'Pinned (transitive)' }
];

let client: HeapDumpClient;

const analysisTabs = computed(() => {
  const tabs = [
    { id: 'class-loaders', label: 'Class Loaders', icon: 'diagram-3' },
    { id: 'unloadability', label: 'Unloadability', icon: 'shield-exclamation' },
    { id: 'duplicate-classes', label: 'Duplicate Classes', icon: 'files' },
    { id: 'about', label: 'How It Works', icon: 'info-circle' }
  ];
  if ((report.value?.leakChains ?? []).length > 0) {
    tabs.unshift({ id: 'suspicious-loaders', label: 'Suspicious Loaders', icon: 'bug' });
  }
  return tabs;
});

const summaryMetrics = computed(() => {
  if (!report.value) return [];
  return [
    {
      icon: 'diagram-3',
      title: 'Total Class Loaders',
      value: FormattingService.formatNumber(report.value.totalClassLoaders),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Classes', value: FormattingService.formatNumber(report.value.totalClasses) }
      ]
    },
    {
      icon: 'files',
      title: 'Duplicate Class Count',
      value: FormattingService.formatNumber(report.value.duplicateClassCount),
      variant: report.value.duplicateClassCount > 0 ? ('warning' as const) : ('success' as const)
    }
  ];
});

const maxLoaderRetainedSize = computed(() => {
  if (!report.value || report.value.classLoaders.length === 0) return 0;
  return Math.max(...report.value.classLoaders.map(e => e.retainedSize));
});

// Sorted duplicate classes
const sortedDuplicateClasses = computed(() => {
  if (!report.value) return [];
  const entries = [...report.value.duplicateClasses];
  const direction = dupSortDirection.value === 'asc' ? 1 : -1;

  switch (dupSortColumn.value) {
    case 'loaderCount':
      entries.sort((a, b) => direction * (a.loaderCount - b.loaderCount));
      break;
  }
  return entries;
});

const toggleDupSort = (column: string) => {
  if (dupSortColumn.value === column) {
    dupSortDirection.value = dupSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    dupSortColumn.value = column;
    dupSortDirection.value = 'desc';
  }
};

const verdictFor = (loaderId: number) => {
  return report.value?.unloadability?.[String(loaderId)];
};

const verdictLabel = (loaderId: number): string => {
  const v = verdictFor(loaderId);
  if (!v) {
    return '';
  }
  switch (v.verdict) {
    case 'UNLOADABLE':
      return 'unloadable';
    case 'PINNED_ROOTED':
      return 'rooted';
    case 'PINNED_TRANSITIVE':
      return 'pinned';
  }
  return '';
};

const verdictVariant = (loaderId: number): Variant => {
  const v = verdictFor(loaderId);
  if (!v) {
    return 'secondary';
  }
  switch (v.verdict) {
    case 'UNLOADABLE':
      return 'success';
    case 'PINNED_ROOTED':
      return 'secondary';
    case 'PINNED_TRANSITIVE':
      return 'warning';
  }
  return 'secondary';
};

interface UnloadabilityRow {
  loaderId: number;
  loaderClassName: string;
  classCount: number;
  liveInstanceCount: number;
  verdict: 'UNLOADABLE' | 'PINNED_ROOTED' | 'PINNED_TRANSITIVE';
  topBlockingClasses: Array<{ classId: number; name: string; instanceCount: number }>;
}

const unloadabilityRows = computed<UnloadabilityRow[]>(() => {
  if (!report.value) {
    return [];
  }
  const out: UnloadabilityRow[] = [];
  for (const loader of report.value.classLoaders) {
    const u = report.value.unloadability?.[String(loader.objectId)];
    if (!u) {
      continue;
    }
    out.push({
      loaderId: loader.objectId,
      loaderClassName: loader.classLoaderClassName,
      classCount: loader.classCount,
      liveInstanceCount: u.liveInstanceCount,
      verdict: u.verdict,
      topBlockingClasses: u.topBlockingClasses ?? []
    });
  }
  return out;
});

const filteredUnloadabilityRows = computed<UnloadabilityRow[]>(() => {
  if (unloadabilityFilter.value === 'all') {
    return unloadabilityRows.value;
  }
  return unloadabilityRows.value.filter(r => r.verdict === unloadabilityFilter.value);
});

const verdictOrder: Record<UnloadabilityRow['verdict'], number> = {
  PINNED_TRANSITIVE: 0,
  PINNED_ROOTED: 1,
  UNLOADABLE: 2
};

const sortedUnloadabilityRows = computed<UnloadabilityRow[]>(() => {
  const rows = [...filteredUnloadabilityRows.value];
  const direction = unloadabilitySortDirection.value === 'asc' ? 1 : -1;
  rows.sort((a, b) => {
    switch (unloadabilitySortColumn.value) {
      case 'classCount':
        return direction * (a.classCount - b.classCount);
      case 'verdict':
        return direction * (verdictOrder[a.verdict] - verdictOrder[b.verdict]);
      case 'liveInstanceCount':
      default:
        return direction * (a.liveInstanceCount - b.liveInstanceCount);
    }
  });
  return rows;
});

const toggleUnloadabilitySort = (column: string) => {
  if (unloadabilitySortColumn.value === column) {
    unloadabilitySortDirection.value = unloadabilitySortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    unloadabilitySortColumn.value = column;
    unloadabilitySortDirection.value = 'desc';
  }
};

const unloadabilityCounts = computed<Record<UnloadabilityFilter, number>>(() => {
  const counts: Record<UnloadabilityFilter, number> = {
    all: unloadabilityRows.value.length,
    UNLOADABLE: 0,
    PINNED_ROOTED: 0,
    PINNED_TRANSITIVE: 0
  };
  for (const row of unloadabilityRows.value) {
    counts[row.verdict]++;
  }
  return counts;
});

const simpleClassName = (fqcn: string): string => {
  if (!fqcn) {
    return '';
  }
  const lastDot = fqcn.lastIndexOf('.');
  return lastDot > 0 ? fqcn.substring(lastDot + 1) : fqcn;
};

const loadAnalysis = async () => {
  report.value = await client.getClassLoaderAnalysis();
  if ((report.value?.leakChains ?? []).length > 0) {
    activeTab.value = 'suspicious-loaders';
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
    error.value = err instanceof Error ? err.message : 'Failed to load class loader analysis';
    console.error('Error loading class loader analysis:', err);
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

.class-cell {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.loader-names {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
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

.clickable-row {
  cursor: pointer;
}

/* Unloadability tab */
.unloadability-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-3);
  flex-wrap: wrap;
}

.filter-chips .btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: 0.8rem;
}

.chip-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.5em;
  padding: 0 0.4em;
  border-radius: 999px;
  background-color: rgba(0, 0, 0, 0.12);
  color: inherit;
  font-size: 0.7rem;
  line-height: 1.6;
}

.blocking-preview {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-1);
}

.blocking-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--color-text);
}

.blocking-count {
  font-family: var(--font-family-monospace, monospace);
  font-size: 0.7rem;
  color: var(--color-text-muted);
  background-color: var(--color-border-light);
  padding: 0 0.35em;
  border-radius: var(--radius-sm);
}

.blocking-sep {
  color: var(--color-text-muted);
}

.blocking-more {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-style: italic;
}

/* Darker warning color for better readability */
.text-warning {
  color: var(--color-goldenrod) !important;
}

/* How It Works tab */
.about-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
}

.about-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
}

.about-header-icon {
  width: 48px;
  height: 48px;
  background-color: var(--color-primary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5rem;
  flex-shrink: 0;
}

.about-header h5 {
  font-weight: 600;
}

.about-intro {
  background-color: var(--color-bg-hover);
  border-radius: var(--radius-md);
  padding: 1rem 1.25rem;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-text);
}

.about-intro code {
  background-color: var(--color-border-light);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.85em;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
}

.section-title i {
  color: var(--color-text-muted);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

@media (max-width: 768px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

.feature-card {
  display: flex;
  gap: 0.875rem;
  padding: 1rem;
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition:
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.feature-card:hover {
  box-shadow: var(--shadow-sm);
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.1rem;
  flex-shrink: 0;
}

.icon-primary {
  background-color: var(--color-primary);
}

.icon-info {
  background-color: var(--color-info);
}

.icon-success {
  background-color: var(--color-success);
}

.icon-warning {
  background-color: var(--color-warning);
}

.icon-danger {
  background-color: var(--color-danger);
}

.icon-secondary {
  background-color: var(--color-secondary);
}

.icon-purple {
  background-color: var(--color-purple);
}

.feature-content h6 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.feature-content p {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin-bottom: 0;
  line-height: 1.5;
}

.feature-content code {
  background-color: var(--color-border-light);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.85em;
}

.benefits-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.benefit-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: var(--color-text);
  padding: 0.5rem 0;
  line-height: 1.5;
}

.benefit-item i {
  flex-shrink: 0;
  margin-top: 0.15rem;
  color: var(--color-info);
}

.benefit-item code {
  background-color: var(--color-border-light);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.85em;
}

.about-note {
  display: flex;
  gap: 1rem;
  background-color: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  padding: 1rem;
}

.note-icon {
  color: var(--color-amber);
  font-size: 1.25rem;
  flex-shrink: 0;
}

.note-content {
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-warning-text);
}

.note-content code {
  background-color: rgba(255, 224, 130, 0.4);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.9em;
}
</style>

<template>
  <LoadingState v-if="loading" message="Loading thread information..." />

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
      <DataTable>
        <template #toolbar>
          <TableToolbar v-model="searchQuery" search-placeholder="Filter...">
            <span class="toolbar-count">{{ filteredThreads.length }} threads</span>
            <span v-if="filteredThreads.length !== threadsData.length" class="toolbar-badge-filtered">
              filtered from {{ threadsData.length }}
            </span>
            <template #filters>
              <select v-model="sortByKey" class="form-select form-select-sm sort-select">
                <option value="retained-desc">Sort: Retained ↓</option>
                <option value="retained-asc">Sort: Retained ↑</option>
                <option value="frames-desc">Sort: Frames ↓</option>
                <option value="locals-desc">Sort: Locals bytes ↓</option>
                <option value="name-asc">Sort: Name A–Z</option>
                <option value="name-desc">Sort: Name Z–A</option>
              </select>
              <select v-model="daemonFilter" class="form-select form-select-sm">
                <option value="all">All Threads</option>
                <option value="daemon">Daemon Only</option>
                <option value="non-daemon">Non-Daemon Only</option>
              </select>
            </template>
          </TableToolbar>
        </template>
            <thead>
              <tr>
                <th style="width: 40px">#</th>
                <th>Thread</th>
                <th style="width: 36px"></th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(thread, index) in filteredThreads" :key="thread.objectId">
                <tr
                  class="thread-row"
                  :class="{ 'is-expanded': expandedThread === thread.objectId }"
                  @click="toggleStack(thread.objectId)"
                >
                  <td class="text-muted align-middle">{{ index + 1 }}</td>
                  <td class="thread-cell">
                    <div class="thread-header">
                      <span class="thread-name">{{ thread.name }}</span>
                      <span class="thread-id">id 0x{{ thread.objectId.toString(16) }}</span>
                      <span @click.stop>
                        <InstanceActionButtons
                          :object-id="thread.objectId"
                          :show-gc-root-path="false"
                          @show-referrers="openTreeModal($event, 'REFERRERS')"
                          @show-reachables="openTreeModal($event, 'REACHABLES')"
                        />
                      </span>
                    </div>
                    <div class="thread-meta">
                      <span v-if="thread.state" class="inline-stat">
                        State
                        <Badge :value="thread.state" :variant="stateVariant(thread.state)" size="xs" borderless />
                      </span>
                      <span v-if="thread.state" class="meta-separator">•</span>

                      <span v-if="thread.frameCount != null" class="inline-stat">
                        Frames <strong>{{ thread.frameCount }}</strong>
                      </span>
                      <span v-if="thread.frameCount != null" class="meta-separator">•</span>

                      <span v-if="thread.localsCount != null" class="inline-stat">
                        Locals
                        <strong>{{ thread.localsCount }} ·
                          {{ FormattingService.formatBytes(thread.localsBytes ?? 0) }}</strong>
                      </span>
                      <span v-if="thread.localsCount != null" class="meta-separator">•</span>

                      <span v-if="thread.retainedSize != null" class="inline-stat">
                        Retained <strong class="retained-strong">{{ FormattingService.formatBytes(thread.retainedSize) }}</strong>
                      </span>
                      <span v-if="thread.retainedSize != null" class="meta-separator">•</span>

                      <span class="inline-stat">{{ thread.daemon ? 'Daemon' : 'Non-Daemon' }}</span>
                      <span class="meta-separator">•</span>
                      <span class="inline-stat" :class="'priority-' + getPriorityClass(thread.priority)">
                        Priority <strong>{{ thread.priority }}</strong>
                      </span>
                    </div>
                  </td>
                  <td class="text-center align-middle chevron-cell">
                    <i
                      class="bi"
                      :class="expandedThread === thread.objectId ? 'bi-chevron-up' : 'bi-chevron-down'"
                    ></i>
                  </td>
                </tr>
                <!-- Stack expansion row — inline two-pane inspector -->
                <tr v-if="expandedThread === thread.objectId" class="stack-expansion-row">
                  <td :colspan="columnCount">
                    <div v-if="stackLoading" class="text-center py-3">
                      <div class="spinner-border spinner-border-sm text-secondary" role="status">
                        <span class="visually-hidden">Loading...</span>
                      </div>
                      <span class="ms-2 text-muted small">Loading stack frames...</span>
                    </div>
                    <div v-else-if="stackFrames.length === 0" class="text-muted small py-2">
                      No stack frames available for this thread.
                    </div>

                    <div v-else class="inspector">
                      <div class="inspector-split">
                        <div class="frames-pane">
                          <div class="pane-header">
                            <span class="pane-title">Frames</span>
                            <span class="pane-hint">click to inspect</span>
                          </div>
                          <div class="frame-list">
                            <div
                              v-for="(frame, idx) in stackFrames"
                              :key="idx"
                              class="frame-row"
                              :class="{
                                selected: idx === selectedFrameIndex,
                                'has-locals': frame.locals && frame.locals.length > 0
                              }"
                              @click="selectedFrameIndex = idx"
                            >
                              <span class="frame-depth">{{ idx }}</span>
                              <div class="frame-row-body">
                                <div class="frame-row-method">
                                  {{ simpleClassName(frame.className) }}.{{ frame.methodName }}
                                </div>
                                <div class="frame-row-source">
                                  {{ frameSourceLabel(frame) }}
                                  <span v-if="frame.locals && frame.locals.length > 0">
                                    · {{ frame.locals.length }} local{{ frame.locals.length === 1 ? '' : 's' }}
                                  </span>
                                </div>
                              </div>
                              <span class="frame-row-marker"></span>
                            </div>
                          </div>
                        </div>

                        <div class="detail-pane" v-if="stackFrames[selectedFrameIndex]">
                          <div class="detail-header">
                            <div class="crumb-row">
                              <div class="crumb">
                                <span class="crumb-tag">FRAME {{ selectedFrameIndex }} / {{ stackFrames.length - 1 }}</span>
                                <span class="crumb-divider">·</span>
                                <span>{{ frameKindLabel(stackFrames[selectedFrameIndex]) }}</span>
                              </div>
                              <div class="nav-row">
                                <button
                                  class="nav-btn"
                                  :disabled="selectedFrameIndex === 0"
                                  :title="`Caller (frame ${selectedFrameIndex - 1})`"
                                  @click="selectedFrameIndex = Math.max(0, selectedFrameIndex - 1)"
                                >
                                  <i class="bi bi-arrow-up"></i> Caller
                                </button>
                                <button
                                  class="nav-btn"
                                  :disabled="selectedFrameIndex >= stackFrames.length - 1"
                                  :title="`Callee (frame ${selectedFrameIndex + 1})`"
                                  @click="selectedFrameIndex = Math.min(stackFrames.length - 1, selectedFrameIndex + 1)"
                                >
                                  Callee <i class="bi bi-arrow-down"></i>
                                </button>
                              </div>
                            </div>
                            <div class="signature">
                              {{ simpleClassName(stackFrames[selectedFrameIndex].className) }}.<span class="method">{{ stackFrames[selectedFrameIndex].methodName }}</span><span class="parens">()</span>
                            </div>
                            <div
                              v-if="packageName(stackFrames[selectedFrameIndex].className)"
                              class="sig-package"
                              :class="isJdkPackage(packageName(stackFrames[selectedFrameIndex].className)) ? 'sig-pkg-jdk' : 'sig-pkg-other'"
                            >{{ packageName(stackFrames[selectedFrameIndex].className) }}</div>
                            <div class="meta-line">
                              <span
                                v-if="stackFrames[selectedFrameIndex].sourceFile"
                                class="item source"
                              >
                                <i class="bi bi-file-earmark-code icon"></i>
                                <strong>{{ stackFrames[selectedFrameIndex].sourceFile }}</strong>
                                <template v-if="stackFrames[selectedFrameIndex].lineNumber > 0">
                                  : <strong>{{ stackFrames[selectedFrameIndex].lineNumber }}</strong>
                                </template>
                              </span>
                              <span
                                v-if="stackFrames[selectedFrameIndex].locals.length > 0"
                                class="sep"
                              >|</span>
                              <span
                                v-if="stackFrames[selectedFrameIndex].locals.length > 0"
                                class="item badges"
                              >
                                <Badge
                                  size="xs"
                                  variant="warning"
                                  :uppercase="false"
                                  key-label="Retained"
                                  :value="FormattingService.formatBytes(localsBytes(stackFrames[selectedFrameIndex]))"
                                />
                                <Badge
                                  size="xs"
                                  variant="secondary"
                                  :uppercase="false"
                                  key-label="Locals"
                                  :value="String(stackFrames[selectedFrameIndex].locals.length)"
                                />
                              </span>
                            </div>
                          </div>

                          <div class="detail-body">
                            <div
                              v-if="stackFrames[selectedFrameIndex].locals.length > 0"
                              class="section"
                            >
                              <div class="section-title">
                                Locals
                                <span class="section-count">{{ stackFrames[selectedFrameIndex].locals.length }}</span>
                              </div>
                              <div class="locals-list">
                                <div
                                  v-for="local in stackFrames[selectedFrameIndex].locals"
                                  :key="local.objectId"
                                  class="local-row"
                                >
                                  <ClassNameDisplay :class-name="local.className" />
                                  <div class="local-size">
                                    {{ FormattingService.formatBytes(local.shallowSize) }}
                                  </div>
                                  <div class="local-id">
                                    0x{{ local.objectId.toString(16) }}
                                  </div>
                                  <div class="local-actions">
                                    <InstanceActionButtons
                                      :object-id="local.objectId"
                                      :show-gc-root-path="false"
                                      @show-referrers="openTreeModal($event, 'REFERRERS')"
                                      @show-reachables="openTreeModal($event, 'REACHABLES')"
                                    />
                                  </div>
                                </div>
                              </div>
                            </div>
                            <div v-else class="section empty-locals">
                              No locals captured in this frame.
                            </div>

                            <div
                              v-if="frameInsight(stackFrames[selectedFrameIndex], selectedFrameIndex, stackFrames)"
                              class="section insight"
                            >
                              <div class="section-title">Insight</div>
                              <div class="insight-body">
                                <i class="bi bi-info-circle me-2"></i>
                                {{ frameInsight(stackFrames[selectedFrameIndex], selectedFrameIndex, stackFrames) }}
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
      </DataTable>
    </div>

    <div v-else class="empty-state">
      <div class="text-center py-5">
        <i class="bi bi-cpu text-muted" style="font-size: 3rem"></i>
        <p class="text-muted mt-3 mb-0">No thread information available in this heap dump.</p>
      </div>
    </div>

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
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import Badge from '@/components/Badge.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import '@/styles/shared-components.css';
import HeapThreadInfo from '@/services/api/model/HeapThreadInfo';
import type ThreadStackFrame from '@/services/api/model/ThreadStackFrame';
import FormattingService from '@/services/FormattingService';
import { isJdkPackage } from '@/services/JavaPackage';
import type { Variant } from '@/types/ui';

const route = useRoute();

const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const threadsData = ref<HeapThreadInfo[]>([]);
const totalRetainedSize = ref<number>(0);
const searchQuery = ref('');
const daemonFilter = ref('all');
// Combined sort key for the toolbar dropdown ("<field>-<asc|desc>").
const sortByKey = ref<string>('retained-desc');
const showTreeModal = ref(false);
const selectedObjectId = ref<number | null>(null);
const treeMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');
const expandedThread = ref<number | null>(null);
const stackFrames = ref<ThreadStackFrame[]>([]);
const stackLoading = ref(false);
// Inspector — index of the frame currently shown in the right detail pane.
// Reset to 0 every time a new thread's stack is loaded so the user sees the
// top-of-stack first (most relevant for blocked/parked threads).
const selectedFrameIndex = ref(0);

let client: HeapDumpClient;

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

  // Sorting — driven by the combined sortByKey ("<field>-<asc|desc>").
  const [sortField, sortDir] = sortByKey.value.split('-');
  const direction = sortDir === 'asc' ? 1 : -1;
  switch (sortField) {
    case 'frames':
      result.sort((a, b) => direction * ((a.frameCount ?? 0) - (b.frameCount ?? 0)));
      break;
    case 'locals':
      result.sort((a, b) => direction * ((a.localsBytes ?? 0) - (b.localsBytes ?? 0)));
      break;
    case 'name':
      result.sort((a, b) => direction * a.name.localeCompare(b.name));
      break;
    case 'retained':
    default:
      result.sort((a, b) => direction * ((a.retainedSize ?? 0) - (b.retainedSize ?? 0)));
  }

  return result;
});

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

// Column layout: #, Thread (with all inline meta), chevron.
const columnCount = 3;

const stateVariant = (state: string | undefined): Variant => {
  switch (state) {
    case 'PARKED':
    case 'WAITING':
    case 'SLEEPING':
    case 'NATIVE':
      return 'warning';
    case 'RUNNABLE':
      return 'success';
    default:
      return 'secondary';
  }
};

const toggleStack = async (objectId: number) => {
  if (expandedThread.value === objectId) {
    expandedThread.value = null;
    stackFrames.value = [];
    selectedFrameIndex.value = 0;
    return;
  }

  expandedThread.value = objectId;
  stackFrames.value = [];
  selectedFrameIndex.value = 0;
  stackLoading.value = true;

  try {
    stackFrames.value = await client.getThreadStack(objectId);
    selectedFrameIndex.value = 0;
  } catch (err) {
    console.error('Error loading thread stack:', err);
    stackFrames.value = [];
  } finally {
    stackLoading.value = false;
  }
};

// ---- Inspector helpers ----------------------------------------------------

const simpleClassName = (fqn: string): string => {
  if (!fqn) return '';
  const lastDot = fqn.lastIndexOf('.');
  return lastDot < 0 ? fqn : fqn.substring(lastDot + 1);
};

const packageName = (fqn: string): string => {
  if (!fqn) return '';
  const lastDot = fqn.lastIndexOf('.');
  return lastDot < 0 ? '' : fqn.substring(0, lastDot);
};

const frameSourceLabel = (frame: ThreadStackFrame): string => {
  if (!frame.sourceFile) {
    return frame.lineNumber === -3 ? '(native)' : '';
  }
  if (frame.lineNumber > 0) {
    return `${frame.sourceFile}:${frame.lineNumber}`;
  }
  return frame.sourceFile;
};

const frameKindLabel = (frame: ThreadStackFrame): string => {
  if (frame.lineNumber === -3) return 'native frame';
  if (frame.lineNumber === -2) return 'compiled frame';
  const cls = frame.className || '';
  if (cls.startsWith('java.') || cls.startsWith('jdk.') || cls.startsWith('sun.')) {
    return 'jdk frame';
  }
  return 'user code';
};

const localsBytes = (frame: ThreadStackFrame): number =>
  frame.locals.reduce((sum, l) => sum + (l.shallowSize ?? 0), 0);

const totalLocalsCount = (frames: ThreadStackFrame[]): number =>
  frames.reduce((sum, f) => sum + f.locals.length, 0);

const totalLocalsBytes = (frames: ThreadStackFrame[]): number =>
  frames.reduce((sum, f) => sum + localsBytes(f), 0);

// Heuristic thread-state label derived from the top frame. JFR/HPROF doesn't
// carry the JVM Thread.State directly, but the top frame usually tells the
// story (Unsafe.park, Object.wait, Thread.sleep, …). Falls back to "Runnable"
// when nothing matches.
const threadStateLabel = (frames: ThreadStackFrame[]): string => {
  if (frames.length === 0) return 'Unknown';
  const top = frames[0];
  const method = `${top.className}.${top.methodName}`;
  if (method === 'jdk.internal.misc.Unsafe.park' || method === 'sun.misc.Unsafe.park') return 'PARKED';
  if (method === 'java.lang.Object.wait' || method === 'java.lang.Object.wait0') return 'WAITING';
  if (method === 'java.lang.Thread.sleep' || method === 'java.lang.Thread.sleep0' || method === 'java.lang.Thread.sleepNanos') return 'SLEEPING';
  if (top.lineNumber === -3) return 'NATIVE';
  return 'RUNNABLE';
};

const threadStatePillClass = (frames: ThreadStackFrame[]): string => {
  switch (threadStateLabel(frames)) {
    case 'PARKED':
    case 'WAITING':
    case 'SLEEPING':
      return 'state-waiting';
    case 'NATIVE':
      return 'state-native';
    case 'RUNNABLE':
      return 'state-runnable';
    default:
      return 'state-unknown';
  }
};

// Best-effort one-liner explaining what the frame is doing. Keeps the
// inspector useful even when the user isn't fluent in JDK internals.
const frameInsight = (
    frame: ThreadStackFrame, index: number, frames: ThreadStackFrame[]): string | null => {
  const method = `${frame.className}.${frame.methodName}`;
  if (method === 'jdk.internal.misc.Unsafe.park' || method === 'sun.misc.Unsafe.park') {
    return 'Thread is parked on a synchronizer (LockSupport.park). It will resume when another thread unparks it.';
  }
  if (frame.className === 'java.util.concurrent.locks.LockSupport' && frame.methodName === 'park') {
    return 'LockSupport.park — the JDK\'s lowest-level blocking primitive. Typically used by AQS-based locks and BlockingQueue.';
  }
  if (frame.className.endsWith('ConditionObject') && frame.methodName === 'await') {
    return 'Awaiting a Condition. Another thread holding the same lock must call signal() / signalAll() to wake this thread up.';
  }
  if (frame.className.endsWith('DelayedWorkQueue') && frame.methodName === 'take') {
    return 'Worker thread of a ScheduledExecutorService waiting for the next scheduled task.';
  }
  if (frame.className === 'java.util.concurrent.ThreadPoolExecutor' && frame.methodName === 'getTask') {
    return 'ThreadPool worker waiting for the next task on its work queue (idle).';
  }
  if (frame.className === 'java.lang.Object' && (frame.methodName === 'wait' || frame.methodName === 'wait0')) {
    return 'Classic Object.wait() — waiting for a notify()/notifyAll() on this object\'s monitor.';
  }
  if (frame.lineNumber === -3 && index === 0) {
    return 'Top of stack is a native method — typically a blocking syscall (park, epoll, accept, read).';
  }
  if (index === frames.length - 1 && frame.className === 'java.lang.Thread' && frame.methodName === 'run') {
    return 'Bottom of stack — the thread\'s entry point. Everything above this is the work the thread was performing.';
  }
  return null;
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

/* Toolbar badges */
.toolbar-count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

.toolbar-badge-filtered {
  font-size: 0.65rem;
  background: var(--color-primary-light);
  color: var(--color-primary);
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
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
  color: var(--color-purple);
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
  color: var(--color-text-muted);
}

.meta-label.priority-high {
  color: var(--color-danger);
}

.meta-label.priority-normal {
  color: var(--color-warning-text);
}

.meta-label.priority-low {
  color: var(--color-text-muted);
}

.meta-separator {
  color: var(--color-border);
  font-size: 0.5rem;
}

.retained-size {
  font-size: 0.8rem;
}

.empty-state {
  background: white;
  border: 1px solid var(--color-border);
}

/* Inspector — inline two-pane stack viewer */
.stack-expansion-row td {
  background-color: var(--color-light) !important;
  padding: 0 !important;
}
.stack-expansion-row:hover td {
  background-color: var(--color-light) !important;
}

.inspector {
  padding: 1rem 1.25rem 1.25rem;
  border-top: 1px solid var(--color-border);
}

/* Clickable thread row */
.thread-row {
  cursor: pointer;
  transition: background-color 0.1s;
}
.thread-row:hover {
  background-color: var(--color-light);
}
.thread-row.is-expanded {
  background-color: var(--color-primary-light);
}
.thread-row.is-expanded:hover {
  background-color: var(--color-primary-light);
}
.chevron-cell {
  color: var(--color-text-light);
  font-size: 0.9rem;
}
.thread-row.is-expanded .chevron-cell {
  color: var(--color-primary);
}

/* Inline stats — every row */
.thread-meta {
  flex-wrap: wrap;
  row-gap: 0.25rem;
}
.thread-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.7rem;
  color: var(--color-text-light);
  margin-left: 0.25rem;
}
.inline-stat {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.inline-stat strong {
  color: var(--color-heading-dark);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.inline-stat .retained-strong {
  color: var(--color-goldenrod);
}
.inline-stat.priority-high strong {
  color: var(--color-danger);
}
.inline-stat.priority-normal strong {
  color: var(--color-warning-text);
}
.inline-stat.priority-low strong {
  color: var(--color-text-muted);
}
.sort-select {
  min-width: 168px;
}

/* Split */
.inspector-split {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 0.875rem;
  align-items: start;
}
@media (max-width: 1100px) {
  .inspector-split {
    grid-template-columns: 1fr;
  }
}

/* Frame list (left) */
.frames-pane {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--card-border-radius, 8px);
  overflow: hidden;
}
.pane-header {
  padding: 0.625rem 0.875rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pane-title {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-heading-dark);
}
.pane-hint {
  font-size: 0.7rem;
  color: var(--color-text-muted);
}
.frame-list {
  max-height: 560px;
  overflow-y: auto;
}
.frame-row {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--color-border-row);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  transition: background-color 0.1s;
}
.frame-row:last-child {
  border-bottom: none;
}
.frame-row:hover {
  background: var(--color-light);
}
.frame-row.selected {
  background: var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
  padding-left: calc(0.75rem - 3px);
}
.frame-depth {
  flex-shrink: 0;
  width: 26px;
  text-align: right;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-text-light);
}
.frame-row.selected .frame-depth {
  color: var(--color-primary);
}
.frame-row-body {
  flex: 1;
  min-width: 0;
}
.frame-row-method {
  font-size: 0.78rem;
  color: var(--color-heading-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.frame-row.selected .frame-row-method {
  font-weight: 600;
}
.frame-row-source {
  font-size: 0.68rem;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.frame-row-marker {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-light);
  flex-shrink: 0;
}
.frame-row.has-locals .frame-row-marker {
  background: var(--color-primary);
}

/* Detail pane (right) — Clean List design */
.detail-pane {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--card-border-radius, 8px);
  min-height: 320px;
  overflow: hidden;
}

/* Header */
.detail-header {
  padding: 1rem 1.25rem 0.875rem;
  border-bottom: 1px solid var(--color-border);
}
.crumb-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.625rem;
  gap: 0.75rem;
}
.crumb {
  font-size: 0.66rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.crumb-tag {
  background: var(--color-primary-light);
  color: var(--color-primary);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.crumb-divider {
  color: var(--color-text-light);
}
.nav-row {
  display: flex;
  gap: 0.375rem;
}
.nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.3rem 0.625rem;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 6px);
  color: var(--color-text);
  font-size: 0.74rem;
  cursor: pointer;
  transition: all 0.12s;
}
.nav-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
}
.nav-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Class.method() on the first line, package on the second — coloured by the
   two-tone rule used in ClassNameDisplay (blue for JDK, dark green for user /
   third-party). */
.signature {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-heading-dark);
  word-break: break-word;
  font-weight: 600;
  margin-bottom: 2px;
}
.signature .method {
  color: var(--color-primary);
}
.signature .parens {
  color: var(--color-text-light);
  font-weight: 500;
}
.sig-package {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.78rem;
  font-weight: 500;
  line-height: 1.3;
  margin-bottom: 0.625rem;
}
.sig-pkg-jdk {
  color: var(--color-primary);
}
.sig-pkg-other {
  color: var(--color-green-text);
}

/* Meta line: source file (muted) + Badge components for Retained / Locals */
.meta-line {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}
.meta-line .item {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}
.meta-line .item.badges {
  gap: 0.375rem;
  font-family: inherit;
}
.meta-line .item.source strong {
  color: var(--color-text);
  font-weight: 500;
}
.meta-line .sep {
  color: var(--color-border);
  font-weight: 700;
}
.meta-line .icon {
  color: var(--color-text-light);
}

/* Body */
.detail-body {
  padding: 1rem 1.25rem;
}
.section {
  margin-bottom: 1.125rem;
}
.section:last-child {
  margin-bottom: 0;
}
.section-title {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-heading-dark);
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.section-count {
  background: var(--color-primary-light);
  color: var(--color-primary);
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 0.66rem;
  font-weight: 700;
}

/* Locals — borderless list (no table chrome) */
.locals-list {
  border-top: 1px solid var(--color-border-row);
}
.local-row {
  display: grid;
  grid-template-columns: 1fr 88px 130px 60px;
  align-items: center;
  gap: 0.875rem;
  padding: 0.625rem 0;
  border-bottom: 1px solid var(--color-border-row);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.local-row:last-child {
  border-bottom: none;
}
.local-row:hover {
  background: var(--color-light);
}
.local-size {
  text-align: right;
  font-size: 0.84rem;
  color: var(--color-goldenrod);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.local-id {
  text-align: right;
  font-size: 0.76rem;
  color: var(--color-text-light);
}
.local-actions {
  display: flex;
  justify-content: flex-end;
}
.empty-locals {
  font-size: 0.82rem;
  color: var(--color-text-muted);
  font-style: italic;
}
.insight .insight-body {
  background: var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
  border-radius: 0 var(--radius-md, 6px) var(--radius-md, 6px) 0;
  padding: 0.625rem 0.875rem;
  font-size: 0.82rem;
  color: var(--color-text);
}
.insight .insight-body i {
  color: var(--color-primary);
}

.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

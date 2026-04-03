<template>
  <LoadingState v-if="loading" message="Loading dominator tree..." />

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
    message="The heap dump needs to be initialized before you can view the dominator tree. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
      title="Dominator Tree"
      description="Memory ownership hierarchy — who holds what"
      icon="bi-diagram-3"
    />

    <!-- Summary Metrics -->
    <StatsTable :metrics="summaryMetrics" class="mb-4" />

    <!-- Data Table -->
    <EmptyState
      v-if="treeData.length === 0"
      icon="bi-diagram-3"
      title="No dominator tree data available"
    />
    <div v-else class="table-card">
      <div class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
            <tr>
              <th style="width: 40%">Class Name</th>
              <th class="text-end" style="width: 120px">Shallow Size</th>
              <th class="text-end" style="width: 120px">Retained Size</th>
              <th style="width: 180px">% of Parent</th>
              <th style="width: 80px"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in treeData"
              :key="item.node.objectId + '-' + item.depth + '-' + index"
            >
              <!-- Load More row -->
              <template v-if="item.isLoadMore">
                <td colspan="5">
                  <div
                    class="d-flex align-items-center gap-3"
                    :style="{ paddingLeft: item.depth * 1.5 + 'rem' }"
                  >
                    <span class="expand-placeholder me-1"></span>
                    <span
                      v-if="item.loading"
                      class="spinner-border spinner-border-sm spinner-inline text-muted"
                    ></span>
                    <template v-else>
                      <button class="btn btn-load-more" @click="loadMoreChildren(item, index)">
                        <i class="bi bi-plus-circle me-1"></i>
                        Load 50 more
                      </button>
                      <button class="btn btn-load-more" @click="loadAllChildren(item, index)">
                        <i class="bi bi-arrow-down-circle me-1"></i>
                        Load all
                      </button>
                    </template>
                  </div>
                </td>
              </template>
              <!-- Normal node row -->
              <template v-else>
                <!-- Class Name with expand/collapse -->
                <td>
                  <div
                    class="d-flex align-items-center"
                    :style="{ paddingLeft: item.depth * 1.5 + 'rem' }"
                  >
                    <button
                      v-if="item.node.hasChildren"
                      class="btn btn-expand me-1"
                      @click="toggleExpand(item, index)"
                      :disabled="item.loading"
                    >
                      <span
                        v-if="item.loading"
                        class="spinner-border spinner-border-sm spinner-inline"
                      ></span>
                      <i v-else-if="item.expanded" class="bi bi-chevron-down"></i>
                      <i v-else class="bi bi-chevron-right"></i>
                    </button>
                    <span v-else class="expand-placeholder me-1"></span>
                    <div class="class-info">
                      <div class="class-name-line">
                        <code class="class-name">{{ simpleClassName(item.node.className) }}</code>
                        <span class="package-name">{{ packageName(item.node.className) }}</span>
                      </div>
                      <div class="detail-line">
                        <span v-if="item.node.fieldName" class="field-name">{{
                          item.node.fieldName
                        }}</span>
                        <span v-if="item.node.fieldName" class="detail-separator">·</span>
                        <span class="object-id-text">{{
                          FormattingService.formatObjectId(item.node.objectId)
                        }}</span>
                        <template v-if="Object.keys(item.node.objectParams).length > 0">
                          <span class="detail-separator">·</span>
                          <span class="object-params-text">{{
                            FormattingService.formatObjectParams(item.node.objectParams)
                          }}</span>
                        </template>
                        <template v-if="item.node.gcRootKind">
                          <span class="detail-separator">·</span>
                          <span class="gc-root-badge">{{ gcRootLabel(item.node.gcRootKind) }}</span>
                        </template>
                      </div>
                    </div>
                  </div>
                </td>
                <!-- Shallow Size -->
                <td class="text-end font-monospace">
                  {{ FormattingService.formatBytes(item.node.shallowSize) }}
                </td>
                <!-- Retained Size -->
                <td class="text-end font-monospace text-warning">
                  {{ FormattingService.formatBytes(item.node.retainedSize) }}
                </td>
                <!-- % of Parent -->
                <td>
                  <div class="d-flex align-items-center gap-2">
                    <div class="progress flex-grow-1" style="height: 6px">
                      <div
                        class="progress-bar"
                        :style="{
                          width: item.node.retainedPercent + '%',
                          backgroundColor: getBarColor(item.node.retainedPercent)
                        }"
                      ></div>
                    </div>
                    <small class="text-muted" style="min-width: 45px"
                      >{{ item.node.retainedPercent.toFixed(1) }}%</small
                    >
                  </div>
                </td>
                <!-- Action Buttons -->
                <td>
                  <InstanceActionButtons
                    :object-id="item.node.objectId"
                    @show-referrers="openTreeModal($event, 'REFERRERS')"
                    @show-reachables="openTreeModal($event, 'REACHABLES')"
                    @show-g-c-root-path="openGCRootPathModal"
                  />
                </td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Instance Tree Modal -->
    <InstanceTreeModal
      :show="treeModalVisible"
      :object-id="treeModalObjectId"
      :initial-mode="treeModalMode"
      :profile-id="profileId"
      @update:show="treeModalVisible = $event"
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
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import EmptyState from '@/components/EmptyState.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type DominatorTreeResponse from '@/services/api/model/DominatorTreeResponse';
import type { DominatorNode } from '@/services/api/model/DominatorTreeResponse';
import FormattingService from '@/services/FormattingService';

interface TreeItem {
  node: DominatorNode;
  depth: number;
  expanded: boolean;
  loading: boolean;
  childCount: number;
  hasMore: boolean;
  loadedCount: number;
  isLoadMore?: boolean;
  loadMoreParentObjectId?: number;
}

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const treeData = ref<TreeItem[]>([]);
const totalHeapSize = ref(0);
const compressedOops = ref(false);

// Modal state
const treeModalVisible = ref(false);
const treeModalObjectId = ref(0);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');
let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  return [
    {
      icon: 'hdd',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(totalHeapSize.value),
      variant: 'highlight' as const
    },
    {
      icon: 'cpu',
      title: 'Compressed Oops',
      value: compressedOops.value ? 'Enabled' : 'Disabled',
      variant: compressedOops.value ? ('success' as const) : ('info' as const)
    }
  ];
});

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
};

const gcRootLabel = (kind: string): string => {
  const labels: Record<string, string> = {
    'Java frame': 'Local Variable',
    'thread object': 'Thread Object',
    'JNI global': 'JNI Global',
    'JNI local': 'JNI Local',
    'sticky class': 'Sticky Class',
    'native stack': 'Native Stack',
    'thread block': 'Thread Block',
    'monitor used': 'Monitor',
    'VM internal': 'VM Internal'
  };
  return labels[kind] ?? kind;
};

const getBarColor = (percent: number): string => {
  if (percent >= 50) return '#dc3545';
  if (percent >= 25) return '#fd7e14';
  if (percent >= 10) return '#ffc107';
  return '#4285F4';
};

const CHILDREN_PAGE_SIZE = 50;
const LOAD_ALL_LIMIT = 100000;

const createLoadMoreItem = (parentObjectId: number, depth: number): TreeItem => ({
  node: {
    objectId: 0,
    className: '',
    objectParams: {},
    fieldName: null,
    shallowSize: 0,
    retainedSize: 0,
    retainedPercent: 0,
    hasChildren: false,
    gcRootKind: null
  },
  depth,
  expanded: false,
  loading: false,
  childCount: 0,
  hasMore: false,
  loadedCount: 0,
  isLoadMore: true,
  loadMoreParentObjectId: parentObjectId
});

const toggleExpand = async (item: TreeItem, index: number) => {
  if (item.expanded) {
    // Collapse: remove all children at deeper depth following this item
    let removeCount = 0;
    for (let i = index + 1; i < treeData.value.length; i++) {
      if (treeData.value[i].depth > item.depth) {
        removeCount++;
      } else {
        break;
      }
    }
    treeData.value.splice(index + 1, removeCount);
    item.expanded = false;
    item.childCount = 0;
    item.hasMore = false;
    item.loadedCount = 0;
  } else {
    // Expand: load first page of children
    item.loading = true;
    try {
      const response: DominatorTreeResponse = await client.getDominatorTreeChildren(
        item.node.objectId,
        0,
        CHILDREN_PAGE_SIZE
      );
      const children: TreeItem[] = response.nodes.map(node => ({
        node,
        depth: item.depth + 1,
        expanded: false,
        loading: false,
        childCount: 0,
        hasMore: false,
        loadedCount: 0
      }));

      if (response.hasMore) {
        children.push(createLoadMoreItem(item.node.objectId, item.depth + 1));
      }

      treeData.value.splice(index + 1, 0, ...children);
      item.expanded = true;
      item.childCount = children.length;
      item.hasMore = response.hasMore;
      item.loadedCount = response.nodes.length;
    } catch (err) {
      console.error('Error loading dominator tree children:', err);
    } finally {
      item.loading = false;
    }
  }
};

const findParent = (
  loadMoreIndex: number,
  parentObjectId: number
): { item: TreeItem; index: number } | null => {
  for (let i = loadMoreIndex - 1; i >= 0; i--) {
    if (treeData.value[i].node.objectId === parentObjectId) {
      return { item: treeData.value[i], index: i };
    }
  }
  return null;
};

const loadMoreChildren = async (loadMoreItem: TreeItem, loadMoreIndex: number) => {
  const parent = findParent(loadMoreIndex, loadMoreItem.loadMoreParentObjectId!);
  if (!parent) return;

  loadMoreItem.loading = true;
  try {
    const offset = parent.item.loadedCount;
    const response: DominatorTreeResponse = await client.getDominatorTreeChildren(
      parent.item.node.objectId,
      offset,
      CHILDREN_PAGE_SIZE
    );

    const newChildren: TreeItem[] = response.nodes.map(node => ({
      node,
      depth: loadMoreItem.depth,
      expanded: false,
      loading: false,
      childCount: 0,
      hasMore: false,
      loadedCount: 0
    }));

    // Remove the current "Load More" row
    treeData.value.splice(loadMoreIndex, 1);

    // Insert new children at the same position
    if (response.hasMore) {
      newChildren.push(createLoadMoreItem(parent.item.node.objectId, loadMoreItem.depth));
    }
    treeData.value.splice(loadMoreIndex, 0, ...newChildren);

    parent.item.childCount += newChildren.length - (response.hasMore ? 0 : 1);
    parent.item.loadedCount += response.nodes.length;
    parent.item.hasMore = response.hasMore;
  } catch (err) {
    console.error('Error loading more dominator tree children:', err);
  } finally {
    loadMoreItem.loading = false;
  }
};

const loadAllChildren = async (loadMoreItem: TreeItem, loadMoreIndex: number) => {
  const parent = findParent(loadMoreIndex, loadMoreItem.loadMoreParentObjectId!);
  if (!parent) return;

  loadMoreItem.loading = true;
  try {
    const offset = parent.item.loadedCount;
    const response: DominatorTreeResponse = await client.getDominatorTreeChildren(
      parent.item.node.objectId,
      offset,
      LOAD_ALL_LIMIT
    );

    const newChildren: TreeItem[] = response.nodes.map(node => ({
      node,
      depth: loadMoreItem.depth,
      expanded: false,
      loading: false,
      childCount: 0,
      hasMore: false,
      loadedCount: 0
    }));

    // Remove the "Load More" row and insert all remaining children
    treeData.value.splice(loadMoreIndex, 1, ...newChildren);

    parent.item.childCount += newChildren.length - 1;
    parent.item.loadedCount += response.nodes.length;
    parent.item.hasMore = false;
  } catch (err) {
    console.error('Error loading all dominator tree children:', err);
  } finally {
    loadMoreItem.loading = false;
  }
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  treeModalVisible.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
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

    const response: DominatorTreeResponse = await client.getDominatorTreeRoots(50);
    totalHeapSize.value = response.totalHeapSize;
    compressedOops.value = response.compressedOops;
    treeData.value = response.nodes.map(node => ({
      node,
      depth: 0,
      expanded: false,
      loading: false,
      childCount: 0,
      hasMore: false,
      loadedCount: 0
    }));
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load dominator tree';
    console.error('Error loading dominator tree:', err);
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

.class-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.class-name-line {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.detail-line {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: 0.75rem;
  margin-top: 1px;
}

.detail-separator {
  color: var(--color-text-light);
  user-select: none;
}

.field-name {
  font-size: 0.8rem;
  color: var(--bs-purple);
  font-style: italic;
  white-space: nowrap;
}

.object-id-text {
  font-family: monospace;
  color: var(--color-text-muted);
}

.object-params-text {
  font-family: monospace;
  color: var(--color-text-muted);
}

.gc-root-badge {
  display: inline-block;
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--bs-blue);
  background-color: rgba(13, 110, 253, 0.1);
  border: 1px solid rgba(13, 110, 253, 0.25);
  border-radius: 3px;
  padding: 0 4px;
  line-height: 1.4;
  white-space: nowrap;
}

.class-name {
  font-size: 0.8rem;
  font-weight: 600;
  background-color: transparent;
  color: var(--color-text);
  white-space: nowrap;
}

.package-name {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-lg);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.table thead th {
  background-color: var(--color-light);
  font-weight: 600;
  color: var(--color-text);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.table td {
  font-size: 0.8rem;
  padding: 0.6rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid var(--color-border-row);
}

.table tbody tr:hover {
  background-color: rgba(66, 133, 244, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
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
  border-radius: 3px;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.btn-expand:hover:not(:disabled) {
  background-color: rgba(111, 66, 193, 0.1);
  color: var(--bs-purple);
}

.btn-expand:disabled {
  opacity: 0.6;
  cursor: wait;
}

.btn-expand i {
  font-size: 0.7rem;
}

.expand-placeholder {
  display: inline-block;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.spinner-inline {
  width: 12px;
  height: 12px;
  border-width: 1.5px;
}

.btn-load-more {
  display: inline-flex;
  align-items: center;
  border: none;
  background: transparent;
  color: var(--bs-purple);
  font-size: 0.8rem;
  font-weight: 500;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-load-more:hover {
  background-color: rgba(111, 66, 193, 0.1);
}

.btn-load-more i {
  font-size: 0.75rem;
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
</style>

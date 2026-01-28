<template>
  <LoadingState v-if="loading" message="Loading dominator tree..." />

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
    <div class="table-card">
      <div class="table-responsive">
        <table class="table table-sm table-hover mb-0">
          <thead>
          <tr>
            <th style="width: 40%;">Class Name</th>
            <th>Display Value</th>
            <th class="text-end" style="width: 120px;">Shallow Size</th>
            <th class="text-end" style="width: 120px;">Retained Size</th>
            <th style="width: 180px;">% of Parent</th>
            <th style="width: 80px;"></th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(item, index) in treeData" :key="item.node.objectId + '-' + item.depth + '-' + index">
            <!-- Class Name with expand/collapse -->
            <td>
              <div class="d-flex align-items-center" :style="{ paddingLeft: item.depth * 1.5 + 'rem' }">
                <button
                    v-if="item.node.hasChildren"
                    class="btn btn-expand me-1"
                    @click="toggleExpand(item, index)"
                    :disabled="item.loading"
                >
                  <span v-if="item.loading" class="spinner-border spinner-border-sm spinner-inline"></span>
                  <i v-else-if="item.expanded" class="bi bi-chevron-down"></i>
                  <i v-else class="bi bi-chevron-right"></i>
                </button>
                <span v-else class="expand-placeholder me-1"></span>
                <code class="class-name">{{ item.node.className }}</code>
              </div>
            </td>
            <!-- Display Value -->
            <td class="text-muted display-value">
              <span v-if="item.node.displayValue">{{ truncateValue(item.node.displayValue) }}</span>
            </td>
            <!-- Shallow Size -->
            <td class="text-end font-monospace">{{ FormattingService.formatBytes(item.node.shallowSize) }}</td>
            <!-- Retained Size -->
            <td class="text-end font-monospace">{{ FormattingService.formatBytes(item.node.retainedSize) }}</td>
            <!-- % of Parent -->
            <td>
              <div class="d-flex align-items-center gap-2">
                <div class="progress flex-grow-1" style="height: 6px;">
                  <div
                      class="progress-bar"
                      :style="{ width: item.node.retainedPercent + '%', backgroundColor: getBarColor(item.node.retainedPercent) }"
                  ></div>
                </div>
                <small class="text-muted" style="min-width: 45px;">{{ item.node.retainedPercent.toFixed(1) }}%</small>
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

    <!-- GC Root Path Modal -->
    <GCRootPathModal
        :show="gcRootPathModalVisible"
        :object-id="gcRootPathObjectId"
        :profile-id="profileId"
        @close="gcRootPathModalVisible = false"
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
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import GCRootPathModal from '@/components/heap/GCRootPathModal.vue';
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
}

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const treeData = ref<TreeItem[]>([]);
const totalHeapSize = ref(0);

// Modal state
const treeModalVisible = ref(false);
const treeModalObjectId = ref(0);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');
const gcRootPathModalVisible = ref(false);
const gcRootPathObjectId = ref(0);

let client: HeapDumpClient;

const summaryMetrics = computed(() => {
  return [
    {
      icon: 'hdd',
      title: 'Total Heap Size',
      value: FormattingService.formatBytes(totalHeapSize.value),
      variant: 'highlight' as const
    }
  ];
});

const truncateValue = (value: string): string => {
  return value.length > 60 ? value.substring(0, 60) + '...' : value;
};

const getBarColor = (percent: number): string => {
  if (percent >= 50) return '#dc3545';
  if (percent >= 25) return '#fd7e14';
  if (percent >= 10) return '#ffc107';
  return '#4285F4';
};

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
  } else {
    // Expand: load children and insert after this item
    item.loading = true;
    try {
      const response: DominatorTreeResponse = await client.getDominatorTreeChildren(item.node.objectId, 50);
      const children: TreeItem[] = response.nodes.map(node => ({
        node,
        depth: item.depth + 1,
        expanded: false,
        loading: false,
        childCount: 0
      }));
      treeData.value.splice(index + 1, 0, ...children);
      item.expanded = true;
      item.childCount = children.length;
    } catch (err) {
      console.error('Error loading dominator tree children:', err);
    } finally {
      item.loading = false;
    }
  }
};

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  treeModalVisible.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  gcRootPathObjectId.value = objectId;
  gcRootPathModalVisible.value = true;
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
    treeData.value = response.nodes.map(node => ({
      node,
      depth: 0,
      expanded: false,
      loading: false,
      childCount: 0
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

.class-name {
  font-size: 0.8rem;
  word-break: break-all;
  background-color: transparent;
  color: #495057;
}

.display-value {
  font-size: 0.8rem;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.btn-expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  color: #6c757d;
  border-radius: 3px;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.btn-expand:hover:not(:disabled) {
  background-color: rgba(111, 66, 193, 0.1);
  color: #6f42c1;
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

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  transition: width 0.3s ease;
}

.font-monospace {
  font-size: 0.8rem;
}
</style>

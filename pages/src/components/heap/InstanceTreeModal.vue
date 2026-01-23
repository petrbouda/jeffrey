<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="modal modal-overlay"
       :class="{ 'd-block': show, 'd-none': !show }"
       tabindex="-1"
       @keyup.esc="closeModal"
       @click.self="closeModal">
    <div class="modal-dialog modal-xl modal-dialog-scrollable">
      <div class="modal-content">
        <!-- Header -->
        <div class="modal-header">
          <div class="header-content">
            <h5 class="modal-title">
              <i class="bi bi-diagram-3 me-2"></i>
              Instance Tree
            </h5>
            <div class="root-info" v-if="rootNode">
              <code class="class-name">{{ rootNode.className }}</code>
            </div>
          </div>
          <div class="header-controls">
            <!-- Mode Toggle -->
            <div class="btn-group btn-group-sm">
              <button
                  class="btn"
                  :class="mode === 'REFERRERS' ? 'btn-primary' : 'btn-outline-primary'"
                  @click="switchMode('REFERRERS')"
                  title="Objects that reference this instance">
                <i class="bi bi-arrow-left-circle me-1"></i>Referrers
              </button>
              <button
                  class="btn"
                  :class="mode === 'REACHABLES' ? 'btn-primary' : 'btn-outline-primary'"
                  @click="switchMode('REACHABLES')"
                  title="Objects referenced by this instance">
                Reachables<i class="bi bi-arrow-right-circle ms-1"></i>
              </button>
            </div>
            <button type="button" class="close-btn ms-3" @click="closeModal" aria-label="Close" title="Close">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
        </div>

        <!-- Body -->
        <div class="modal-body p-0">
          <!-- Loading State -->
          <div v-if="loading" class="loading-state">
            <span class="spinner-border spinner-border-sm me-2"></span>
            Loading instance tree...
          </div>

          <!-- Error State -->
          <div v-else-if="error" class="error-state">
            <i class="bi bi-exclamation-triangle me-2"></i>
            {{ error }}
          </div>

          <!-- Tree Content -->
          <div v-else class="tree-container">
            <!-- Tree Header -->
            <div class="tree-header">
              <div class="tree-stats">
                <span class="stat-item" v-if="rootNode">
                  <i class="bi bi-box me-1"></i>
                  {{ totalCount }} {{ mode === 'REFERRERS' ? 'referrers' : 'reachables' }}
                </span>
              </div>
              <div class="tree-actions">
                <button class="btn btn-sm btn-outline-secondary" @click="collapseAll" title="Collapse all">
                  <i class="bi bi-arrows-collapse"></i>
                </button>
              </div>
            </div>

            <!-- Tree View -->
            <div class="tree-content">
              <!-- Root Node -->
              <InstanceTreeNodeItem
                  v-if="rootNode"
                  :node="rootNode"
                  :mode="mode"
                  :depth="0"
                  :expanded="true"
                  :children="children"
                  :has-more="hasMore"
                  :client="client"
                  @select="handleNodeSelect"
                  @load-more="loadMoreChildren"
              />
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="modal-footer">
          <button type="button" class="btn btn-outline-secondary" @click="closeModal">
            <i class="bi bi-x-circle me-1"></i>Close
          </button>
        </div>
      </div>
    </div>

    <!-- Instance Detail Panel -->
    <InstanceDetailPanel
        :is-open="!!selectedNode"
        :object-id="selectedNode?.objectId ?? null"
        :client="client"
        @close="selectedNode = null"
        @navigate="navigateToInstance"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, watch, onMounted } from 'vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import InstanceTreeNodeItem from '@/components/heap/InstanceTreeNodeItem.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import type InstanceTreeNode from '@/services/api/model/InstanceTreeNode';

type TreeMode = 'REFERRERS' | 'REACHABLES';

interface Props {
  show: boolean;
  objectId: number;
  initialMode?: TreeMode;
  profileId: string;
}

const props = withDefaults(defineProps<Props>(), {
  initialMode: 'REFERRERS'
});

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
}>();

const client = shallowRef<HeapDumpClient | null>(null);
const mode = ref<TreeMode>(props.initialMode);
const loading = ref(false);
const error = ref<string | null>(null);
const rootNode = ref<InstanceTreeNode | null>(null);
const children = ref<InstanceTreeNode[]>([]);
const hasMore = ref(false);
const totalCount = ref(0);
const selectedNode = ref<InstanceTreeNode | null>(null);

const closeModal = () => {
  emit('update:show', false);
};

const switchMode = (newMode: TreeMode) => {
  if (mode.value !== newMode) {
    mode.value = newMode;
    loadTree();
  }
};

const loadTree = async () => {
  if (!client.value) return;

  loading.value = true;
  error.value = null;

  try {
    const response = mode.value === 'REFERRERS'
        ? await client.value.getReferrers(props.objectId)
        : await client.value.getReachables(props.objectId);

    rootNode.value = response.root;
    children.value = response.children;
    hasMore.value = response.hasMore;
    totalCount.value = response.totalCount;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load instance tree';
    console.error('Error loading instance tree:', err);
  } finally {
    loading.value = false;
  }
};

const loadMoreChildren = async () => {
  if (!client.value || !hasMore.value) return;

  try {
    const response = mode.value === 'REFERRERS'
        ? await client.value.getReferrers(props.objectId, 50, children.value.length)
        : await client.value.getReachables(props.objectId, 50, children.value.length);

    children.value = [...children.value, ...response.children];
    hasMore.value = response.hasMore;
  } catch (err) {
    console.error('Error loading more children:', err);
  }
};

const handleNodeSelect = (node: InstanceTreeNode) => {
  selectedNode.value = node;
};

const navigateToInstance = (_objectId: number) => {
  // Close detail panel and navigate to new instance
  selectedNode.value = null;
  // Update the modal to show the new instance's tree
  // Note: This resets the tree to show the new instance as root
  emit('update:show', false);
  // Parent component will handle opening with new objectId
};

const collapseAll = () => {
  // Trigger collapse by reloading tree
  loadTree();
};

// Initialize client and load tree when modal opens
watch(() => props.show, async (show) => {
  if (show) {
    client.value = new HeapDumpClient(props.profileId);
    mode.value = props.initialMode;
    await loadTree();
  }
});

// Reset when objectId changes
watch(() => props.objectId, async () => {
  if (props.show) {
    await loadTree();
  }
});

onMounted(() => {
  if (props.show) {
    client.value = new HeapDumpClient(props.profileId);
    loadTree();
  }
});
</script>

<style scoped>
.modal-dialog {
  max-width: 1400px;
  max-height: 95vh;
  margin: 1rem auto;
}

.modal-content {
  height: 93vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.75rem 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.modal-title {
  font-size: 1rem;
  font-weight: 600;
  margin: 0;
  color: #495057;
}

.root-info {
  margin-top: 0.25rem;
}

.class-name {
  font-size: 0.8rem;
  color: #6f42c1;
  background: transparent;
}

.header-controls {
  display: flex;
  align-items: center;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  background-color: #fff;
  color: #6c757d;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.close-btn:hover {
  background-color: #dc3545;
  border-color: #dc3545;
  color: #fff;
}

.close-btn:focus {
  outline: none;
  box-shadow: 0 0 0 0.2rem rgba(220, 53, 69, 0.25);
}

.close-btn i {
  font-size: 1rem;
}

.modal-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.loading-state,
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: #6c757d;
}

.error-state {
  color: #dc3545;
}

.tree-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.tree-stats {
  display: flex;
  gap: 1rem;
}

.stat-item {
  font-size: 0.75rem;
  color: #6c757d;
}

.tree-actions {
  display: flex;
  gap: 0.25rem;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.modal-footer {
  padding: 0.75rem 1rem;
  border-top: 1px solid #dee2e6;
}
</style>

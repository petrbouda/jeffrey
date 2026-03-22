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
  <div class="tree-node">
    <!-- Node Row -->
    <div
        class="node-row"
        :style="{ paddingLeft: depth * 20 + 'px' }"
        :class="{ 'expandable': node.hasChildren, 'root-node': node.relationshipType === 'ROOT' }"
        @click="handleSelect"
    >
      <!-- Expand/Collapse Toggle -->
      <button
          v-if="node.hasChildren"
          class="expand-btn"
          @click.stop="toggleExpand"
      >
        <i class="bi" :class="isExpanded ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
      </button>
      <span v-else class="expand-placeholder"></span>

      <!-- Relationship Badge -->
      <span class="relationship-badge" :class="relationshipClass">
        {{ relationshipLabel }}
      </span>

      <!-- Field Name -->
      <span v-if="node.fieldName" class="field-name">
        {{ node.fieldName }}:
      </span>

      <!-- Class Name -->
      <code class="class-name">{{ simpleClassName }}</code>

      <!-- Value Preview -->
      <span class="value-preview" :title="node.value">
        {{ truncatedValue }}
      </span>

      <!-- Size Info -->
      <span class="size-info">
        {{ FormattingService.formatBytes(node.shallowSize) }}
      </span>
    </div>

    <!-- Children (Lazy Loaded) -->
    <div v-if="isExpanded && loadedChildren.length > 0" class="node-children">
      <InstanceTreeNodeItem
          v-for="child in loadedChildren"
          :key="child.objectId"
          :node="child"
          :mode="mode"
          :depth="depth + 1"
          :client="client"
          @select="(node: InstanceTreeNode) => emit('select', node)"
          @load-more="() => emit('load-more')"
      />

      <!-- Load More Button -->
      <button
          v-if="childrenHasMore"
          class="btn btn-sm btn-outline-primary load-more-btn"
          :style="{ marginLeft: (depth + 1) * 20 + 24 + 'px' }"
          @click="loadMoreChildNodes"
          :disabled="loadingMore"
      >
        <span v-if="loadingMore" class="spinner-border spinner-border-sm me-1"></span>
        <i v-else class="bi bi-plus-circle me-1"></i>
        Load more ({{ remainingCount }} remaining)
      </button>
    </div>

    <!-- Provided children for root node -->
    <div v-else-if="isExpanded && children && children.length > 0" class="node-children">
      <InstanceTreeNodeItem
          v-for="child in children"
          :key="child.objectId"
          :node="child"
          :mode="mode"
          :depth="depth + 1"
          :client="client"
          @select="(node: InstanceTreeNode) => emit('select', node)"
          @load-more="() => emit('load-more')"
      />

      <!-- Load More Button for root children -->
      <button
          v-if="hasMore"
          class="btn btn-sm btn-outline-primary load-more-btn"
          :style="{ marginLeft: (depth + 1) * 20 + 24 + 'px' }"
          @click="$emit('load-more')"
      >
        <i class="bi bi-plus-circle me-1"></i>
        Load more...
      </button>
    </div>

    <!-- Loading Indicator -->
    <div v-if="loadingChildren" class="loading-children" :style="{ marginLeft: (depth + 1) * 20 + 'px' }">
      <span class="spinner-border spinner-border-sm me-2"></span>
      Loading...
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import FormattingService from '@/services/FormattingService';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type InstanceTreeNode from '@/services/api/model/InstanceTreeNode';

type TreeMode = 'REFERRERS' | 'REACHABLES';

interface Props {
  node: InstanceTreeNode;
  mode: TreeMode;
  depth: number;
  client: HeapDumpClient | null;
  expanded?: boolean;
  children?: InstanceTreeNode[];
  hasMore?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  expanded: false,
  hasMore: false
});

const emit = defineEmits<{
  select: [node: InstanceTreeNode];
  'load-more': [];
}>();

const isExpanded = ref(props.expanded);
const loadedChildren = ref<InstanceTreeNode[]>([]);
const childrenHasMore = ref(false);
const childrenTotalCount = ref(0);
const loadingChildren = ref(false);
const loadingMore = ref(false);

const simpleClassName = computed(() => {
  const fullName = props.node.className;
  const lastDot = fullName.lastIndexOf('.');
  return lastDot > 0 ? fullName.substring(lastDot + 1) : fullName;
});

const truncatedValue = computed(() => {
  const value = props.node.value;
  const maxLen = 500;
  if (!value || value.length <= maxLen) return value;
  return value.substring(0, maxLen) + '...';
});

const relationshipClass = computed(() => {
  switch (props.node.relationshipType) {
    case 'REFERRER':
      return 'referrer';
    case 'REACHABLE':
      return 'reachable';
    case 'ROOT':
      return 'root';
    default:
      return '';
  }
});

const relationshipLabel = computed(() => {
  switch (props.node.relationshipType) {
    case 'REFERRER':
      return 'refs';
    case 'REACHABLE':
      return 'field';
    case 'ROOT':
      return 'root';
    default:
      return '';
  }
});

const remainingCount = computed(() => {
  return childrenTotalCount.value - loadedChildren.value.length;
});

const toggleExpand = async () => {
  if (!props.node.hasChildren) return;

  isExpanded.value = !isExpanded.value;

  // Load children if expanding and haven't loaded yet
  if (isExpanded.value && loadedChildren.value.length === 0 && !props.children) {
    await loadChildren();
  }
};

const loadChildren = async () => {
  if (!props.client) return;

  loadingChildren.value = true;

  try {
    const response = props.mode === 'REFERRERS'
        ? await props.client.getReferrers(props.node.objectId)
        : await props.client.getReachables(props.node.objectId);

    loadedChildren.value = response.children;
    childrenHasMore.value = response.hasMore;
    childrenTotalCount.value = response.totalCount;
  } catch (err) {
    console.error('Error loading children:', err);
  } finally {
    loadingChildren.value = false;
  }
};

const loadMoreChildNodes = async () => {
  if (!props.client || !childrenHasMore.value) return;

  loadingMore.value = true;

  try {
    const response = props.mode === 'REFERRERS'
        ? await props.client.getReferrers(props.node.objectId, 50, loadedChildren.value.length)
        : await props.client.getReachables(props.node.objectId, 50, loadedChildren.value.length);

    loadedChildren.value = [...loadedChildren.value, ...response.children];
    childrenHasMore.value = response.hasMore;
  } catch (err) {
    console.error('Error loading more children:', err);
  } finally {
    loadingMore.value = false;
  }
};

const handleSelect = () => {
  emit('select', props.node);
};

// Auto-expand root node
watch(() => props.expanded, (newVal) => {
  isExpanded.value = newVal;
}, { immediate: true });
</script>

<style scoped>
.tree-node {
  font-size: 0.8rem;
}

.node-row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  cursor: pointer;
  transition: background-color 0.15s ease;
  border-left: 3px solid transparent;
}

.node-row:hover {
  background-color: rgba(66, 133, 244, 0.06);
}

.node-row.root-node {
  background-color: rgba(111, 66, 193, 0.05);
  border-left-color: #6f42c1;
}

.expand-btn {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #6c757d;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  transition: all 0.15s ease;
}

.expand-btn:hover {
  background-color: rgba(0, 0, 0, 0.08);
  color: #212529;
}

.expand-placeholder {
  width: 20px;
  flex-shrink: 0;
}

.relationship-badge {
  flex-shrink: 0;
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
}

.relationship-badge.referrer {
  background-color: #fff3cd;
  color: #856404;
}

.relationship-badge.reachable {
  background-color: #cce5ff;
  color: #004085;
}

.relationship-badge.root {
  background-color: #e2d9f3;
  color: #6f42c1;
}

.field-name {
  color: #495057;
  font-weight: 500;
}

.class-name {
  color: #6f42c1;
  font-size: 0.75rem;
  background: transparent;
  font-weight: 500;
}

.value-preview {
  color: #6c757d;
  font-size: 0.7rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.size-info {
  flex-shrink: 0;
  color: #495057;
  font-size: 0.75rem;
  font-weight: 600;
  margin-left: auto;
  background-color: #e9ecef;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
}

.node-children {
  border-left: 1px solid #e9ecef;
  margin-left: 10px;
}

.load-more-btn {
  margin: 0.5rem 0;
  font-size: 0.7rem;
}

.loading-children {
  display: flex;
  align-items: center;
  padding: 0.5rem;
  color: #6c757d;
  font-size: 0.75rem;
}
</style>

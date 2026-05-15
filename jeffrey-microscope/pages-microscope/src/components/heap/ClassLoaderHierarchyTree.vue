<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
  <div>
    <div class="tree-toolbar">
      <label class="toggle">
        <input type="checkbox" v-model="hideJdk" />
        <span>Hide JDK loaders</span>
      </label>
      <label class="toggle">
        <input type="checkbox" v-model="onlyPinnedTransitive" />
        <span>Only pinned (transitive)</span>
      </label>
      <button class="btn btn-sm btn-outline-secondary" @click="expandAll">Expand all</button>
      <button class="btn btn-sm btn-outline-secondary" @click="collapseAll">Collapse all</button>
    </div>

    <EmptyState
      v-if="visibleRoots.length === 0"
      icon="bi-diagram-3"
      title="No loaders to show"
      description="The current filters hide every loader. Adjust the toolbar above."
    />

    <div v-else class="tree-container">
      <div
        v-for="row in flatRows"
        :key="row.loader.objectId"
        class="tree-row"
        :class="{ 'tree-row-suspicious': isSuspicious(row.loader.objectId) }"
        :style="{ '--depth': row.depth }"
        @click="$emit('loader-click', row.loader.objectId)"
      >
        <span class="row-indent">
          <button
            v-if="row.hasChildren"
            class="chevron"
            :class="{ open: isExpanded(row.loader.objectId) }"
            @click.stop="toggleExpanded(row.loader.objectId)"
            :aria-label="isExpanded(row.loader.objectId) ? 'Collapse' : 'Expand'"
          >
            <i class="bi bi-chevron-right"></i>
          </button>
          <span v-else class="chevron-placeholder"></span>
        </span>

        <span class="row-name">
          <ClassNameDisplay :class-name="row.loader.classLoaderClassName" />
        </span>

        <span class="row-badges">
          <Badge :value="typeLabel(row.loader.objectId)" :variant="typeVariant(row.loader.objectId)" size="xxs" />
          <Badge
            v-if="verdict(row.loader.objectId)"
            :value="verdictLabel(row.loader.objectId)"
            :variant="verdictVariant(row.loader.objectId)"
            size="xxs"
          />
          <Badge
            v-if="isSuspicious(row.loader.objectId)"
            value="suspicious"
            variant="danger"
            size="xxs"
          />
        </span>

        <span class="row-count font-monospace">{{ FormattingService.formatNumber(row.loader.classCount) }}</span>
        <span class="row-retained font-monospace">{{ FormattingService.formatBytes(row.loader.retainedSize) }}</span>
        <span class="row-bar">
          <span class="progress">
            <span
              class="progress-bar"
              :style="{ width: percentage(row.loader) + '%' }"
            ></span>
          </span>
          <span class="bar-label">{{ percentage(row.loader).toFixed(1) }}%</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import FormattingService from '@/services/FormattingService';
import type {
  ClassLoaderHierarchyEdge,
  ClassLoaderInfo,
  ClassLoaderUnloadability,
  LoaderType
} from '@/services/api/model/ClassLoaderReport';
import type { Variant } from '@/types/ui';

const BOOTSTRAP_LOADER_ID = 0;

const JDK_TYPES = new Set<LoaderType>(['BOOTSTRAP', 'PLATFORM', 'SYSTEM']);

interface Props {
  loaders: ClassLoaderInfo[];
  hierarchyEdges: ClassLoaderHierarchyEdge[];
  unloadability: Record<string, ClassLoaderUnloadability>;
  loaderTypes: Record<string, LoaderType>;
  suspiciousLoaderIds: Set<number>;
  maxRetainedSize: number;
}

const props = defineProps<Props>();
defineEmits<{
  (e: 'loader-click', loaderId: number): void;
}>();

const hideJdk = ref(false);
const onlyPinnedTransitive = ref(false);
const expandedIds = ref<Set<number>>(new Set());

interface TreeNode {
  loader: ClassLoaderInfo;
  children: TreeNode[];
}

const loadersById = computed<Map<number, ClassLoaderInfo>>(() => {
  const m = new Map<number, ClassLoaderInfo>();
  for (const loader of props.loaders) {
    m.set(loader.objectId, loader);
  }
  return m;
});

const bootstrapPlaceholder: ClassLoaderInfo = {
  objectId: BOOTSTRAP_LOADER_ID,
  classLoaderClassName: '<bootstrap>',
  classCount: 0,
  totalClassSize: 0,
  retainedSize: 0
};

const tree = computed<TreeNode[]>(() => {
  const childrenByParent = new Map<number, ClassLoaderInfo[]>();
  for (const edge of props.hierarchyEdges) {
    const child = loadersById.value.get(edge.childId);
    if (!child) {
      continue;
    }
    if (!childrenByParent.has(edge.parentId)) {
      childrenByParent.set(edge.parentId, []);
    }
    childrenByParent.get(edge.parentId)!.push(child);
  }

  const build = (loader: ClassLoaderInfo): TreeNode => ({
    loader,
    children: (childrenByParent.get(loader.objectId) ?? [])
      .sort((a, b) => b.retainedSize - a.retainedSize)
      .map(build)
  });

  // Root is bootstrap (id 0). The aggregate row for bootstrap may or may not
  // exist in `loaders`; fall back to a synthetic entry so the tree always
  // has a stable spine.
  const bootstrap = loadersById.value.get(BOOTSTRAP_LOADER_ID) ?? bootstrapPlaceholder;
  const rootNode = build(bootstrap);
  return [rootNode];
});

const verdict = (loaderId: number): ClassLoaderUnloadability | undefined =>
  props.unloadability[String(loaderId)];

const verdictLabel = (loaderId: number): string => {
  const v = verdict(loaderId);
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
  const v = verdict(loaderId);
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

const typeLabel = (loaderId: number): string => {
  const t = props.loaderTypes[String(loaderId)];
  if (!t) {
    return 'unknown';
  }
  return t.toLowerCase();
};

const typeVariant = (loaderId: number): Variant => {
  const t = props.loaderTypes[String(loaderId)];
  switch (t) {
    case 'BOOTSTRAP':
    case 'PLATFORM':
    case 'SYSTEM':
      return 'secondary';
    case 'WEB':
      return 'info';
    case 'OSGI':
      return 'purple';
    case 'APP':
      return 'primary';
    case 'CUSTOM':
      return 'primary';
  }
  return 'secondary';
};

const isSuspicious = (loaderId: number): boolean => props.suspiciousLoaderIds.has(loaderId);

const percentage = (loader: ClassLoaderInfo): number => {
  if (props.maxRetainedSize === 0) {
    return 0;
  }
  return (loader.retainedSize / props.maxRetainedSize) * 100;
};

const isExpanded = (loaderId: number): boolean => expandedIds.value.has(loaderId);

const toggleExpanded = (loaderId: number) => {
  const next = new Set(expandedIds.value);
  if (next.has(loaderId)) {
    next.delete(loaderId);
  } else {
    next.add(loaderId);
  }
  expandedIds.value = next;
};

const collectAllIds = (nodes: TreeNode[], out: Set<number>) => {
  for (const node of nodes) {
    if (node.children.length > 0) {
      out.add(node.loader.objectId);
      collectAllIds(node.children, out);
    }
  }
};

const expandAll = () => {
  const all = new Set<number>();
  collectAllIds(tree.value, all);
  expandedIds.value = all;
};

const collapseAll = () => {
  expandedIds.value = new Set();
};

// Initialise expanded set to include the root so the tree opens with at least
// the bootstrap branch visible.
expandAll();

const matchesFilters = (loaderId: number): boolean => {
  if (hideJdk.value && JDK_TYPES.has(props.loaderTypes[String(loaderId)])) {
    return false;
  }
  if (onlyPinnedTransitive.value) {
    return verdict(loaderId)?.verdict === 'PINNED_TRANSITIVE';
  }
  return true;
};

// Subtree-aware filter: keep a node if it passes filters OR any descendant
// does, so the path remains navigable.
const filteredTree = computed<TreeNode[]>(() => {
  const prune = (node: TreeNode): TreeNode | null => {
    const keptChildren = node.children
      .map(prune)
      .filter((c): c is TreeNode => c !== null);
    if (matchesFilters(node.loader.objectId) || keptChildren.length > 0) {
      return { loader: node.loader, children: keptChildren };
    }
    return null;
  };
  return tree.value.map(prune).filter((n): n is TreeNode => n !== null);
});

const visibleRoots = computed(() => filteredTree.value);

interface FlatRow {
  loader: ClassLoaderInfo;
  depth: number;
  hasChildren: boolean;
}

const flatRows = computed<FlatRow[]>(() => {
  const rows: FlatRow[] = [];
  const walk = (nodes: TreeNode[], depth: number) => {
    for (const node of nodes) {
      rows.push({
        loader: node.loader,
        depth,
        hasChildren: node.children.length > 0
      });
      if (isExpanded(node.loader.objectId)) {
        walk(node.children, depth + 1);
      }
    }
  };
  walk(filteredTree.value, 0);
  return rows;
});
</script>

<style scoped>
.tree-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-2) var(--spacing-3);
  margin-bottom: var(--spacing-2);
  background-color: var(--color-bg-hover);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: 0.8rem;
  color: var(--color-text);
  cursor: pointer;
  margin-bottom: 0;
}

.toggle input {
  margin: 0;
}

.tree-container {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  overflow: hidden;
}

.tree-row {
  display: grid;
  grid-template-columns: 36px 1fr auto 100px 110px 180px;
  align-items: center;
  gap: var(--spacing-2);
  padding: 6px var(--spacing-3) 6px calc(var(--spacing-3) + (var(--depth, 0) * 18px));
  border-bottom: 1px solid var(--color-border-row);
  background-color: var(--color-bg-card);
  cursor: pointer;
  font-size: 0.85rem;
}

.tree-row:hover {
  background-color: var(--color-bg-hover);
}

.tree-row:last-child {
  border-bottom: none;
}

.tree-row-suspicious {
  background-color: var(--color-danger-light);
}

.row-indent {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.chevron {
  border: none;
  background: transparent;
  padding: 2px 4px;
  cursor: pointer;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.chevron i {
  transition: transform 0.15s ease;
}

.chevron.open i {
  transform: rotate(90deg);
}

.chevron-placeholder {
  width: 22px;
}

.row-badges {
  display: inline-flex;
  gap: 4px;
  flex-wrap: wrap;
}

.row-count,
.row-retained {
  text-align: right;
  font-size: 0.8rem;
}

.row-bar {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
}

.progress {
  flex-grow: 1;
  height: 5px;
  background-color: var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.progress-bar {
  display: block;
  height: 100%;
  background-color: var(--color-primary);
  transition: width 0.3s ease;
}

.bar-label {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  min-width: 45px;
  text-align: right;
}

.font-monospace {
  font-family: var(--font-family-monospace, monospace);
}
</style>

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
  <GenericModal
    modal-id="classLoaderDetailDrawer"
    v-model:show="showInternal"
    :show-footer="false"
    size="xl"
    modal-dialog-class="cl-detail-modal-dialog"
    title="Class Loader Detail"
    icon="bi-diagram-3"
  >
    <LoadingState v-if="loading" message="Loading loader detail..." />
    <ErrorState v-else-if="error" :message="error" />
    <div v-else-if="!detail" class="text-muted py-3">
      <EmptyState icon="bi-question-circle" title="Loader not found" description="This loader id is not present in the heap dump index." />
    </div>

    <div v-else>
      <!-- Header -->
      <div class="detail-header">
        <div class="header-row">
          <ClassNameDisplay :class-name="detail.displayName" class="loader-name" />
          <Badge :value="typeBadgeLabel" :variant="typeBadgeVariant" size="s" />
          <Badge :value="verdictBadgeLabel" :variant="verdictBadgeVariant" size="s" />
        </div>
        <div class="header-meta">
          <span class="meta-item">
            <span class="meta-label">Loader ID</span>
            <span class="meta-value font-monospace">{{ formatId(detail.loaderId) }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">Parent</span>
            <span class="meta-value">
              <a
                href="#"
                class="parent-link"
                @click.prevent="openParent"
                :class="{ disabled: detail.parentLoaderId === 0 }"
              >
                {{ parentSimpleName }}
              </a>
            </span>
          </span>
          <span class="meta-item">
            <span class="meta-label">Classes</span>
            <span class="meta-value font-monospace">{{ FormattingService.formatNumber(detail.classCount) }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">Live instances</span>
            <span class="meta-value font-monospace">{{ FormattingService.formatNumber(detail.instanceCount) }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">Retained</span>
            <span class="meta-value font-monospace">{{ FormattingService.formatBytes(detail.retainedSize) }}</span>
          </span>
        </div>
      </div>

      <div class="classes-section">
        <div v-if="detail.classes.length === 0">
          <EmptyState
            icon="bi-collection"
            title="No classes recorded"
            description="The index contains no classes attributed to this loader."
          />
        </div>
        <template v-else>
          <div class="classes-toolbar">
            <SearchInput
              v-model="classSearchQuery"
              placeholder="Filter classes by name…"
              aria-label="Filter classes"
            />
            <span class="results-summary">
              {{ resultsSummary }}
            </span>
          </div>

          <div v-if="filteredClasses.length === 0" class="empty-search">
            <EmptyState
              icon="bi-search"
              title="No matches"
              :description="`No class name contains “${classSearchQuery}”.`"
            />
          </div>
          <div v-else class="table-responsive">
            <table class="table table-sm table-hover mb-0">
              <thead>
                <tr>
                  <th>Class</th>
                  <SortableTableHeader
                    column="instanceCount"
                    label="Instances"
                    :sort-column="classSortColumn"
                    :sort-direction="classSortDirection"
                    align="end"
                    width="130px"
                    @sort="toggleClassSort"
                  />
                  <SortableTableHeader
                    column="totalInstanceSize"
                    label="Total shallow"
                    :sort-column="classSortColumn"
                    :sort-direction="classSortDirection"
                    align="end"
                    width="170px"
                    @sort="toggleClassSort"
                  />
                </tr>
              </thead>
              <tbody>
                <tr v-for="cls in visibleClasses" :key="cls.classId">
                  <td><ClassNameDisplay :class-name="cls.name" /></td>
                  <td class="text-end font-monospace">
                    {{ FormattingService.formatNumber(cls.instanceCount) }}
                  </td>
                  <td class="text-end font-monospace">
                    {{ FormattingService.formatBytes(cls.totalInstanceSize) }}
                  </td>
                </tr>
                <tr v-if="hiddenClassCount > 0" class="truncate-row">
                  <td colspan="3">
                    <div class="truncate-row-content">
                      <i class="bi bi-three-dots"></i>
                      <span>
                        {{ FormattingService.formatNumber(hiddenClassCount) }} more
                        {{ hiddenClassCount === 1 ? 'class' : 'classes' }} hidden
                      </span>
                      <button
                        type="button"
                        class="btn btn-sm btn-outline-secondary"
                        @click="showMore"
                      >
                        Show {{ Math.min(PAGE_SIZE, hiddenClassCount) }} more
                      </button>
                      <button
                        v-if="hiddenClassCount > PAGE_SIZE"
                        type="button"
                        class="btn btn-sm btn-link p-0 ms-1"
                        @click="showAll"
                      >
                        Show all
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import SearchInput from '@/components/form/SearchInput.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import type ClassLoaderDetail from '@/services/api/model/ClassLoaderDetail';
import FormattingService from '@/services/FormattingService';
import type { Variant } from '@/types/ui';

const PAGE_SIZE = 50;

interface Props {
  show: boolean;
  loaderId: number | null;
  profileId: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'open-loader', loaderId: number): void;
}>();

const showInternal = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value)
});

const loading = ref(false);
const error = ref<string | null>(null);
const detail = ref<ClassLoaderDetail | null>(null);
const classSortColumn = ref<string>('instanceCount');
const classSortDirection = ref<'asc' | 'desc'>('desc');
const classSearchQuery = ref<string>('');
const visibleClassCount = ref<number>(PAGE_SIZE);

const formatId = (id: number) => `0x${id.toString(16)}`;

const typeBadgeLabel = computed(() => detail.value?.type ?? 'UNKNOWN');

const typeBadgeVariant = computed<Variant>(() => {
  switch (detail.value?.type) {
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
});

const verdictBadgeLabel = computed(() => {
  switch (detail.value?.unloadability.verdict) {
    case 'UNLOADABLE':
      return 'Unloadable';
    case 'PINNED_ROOTED':
      return 'Pinned (GC root)';
    case 'PINNED_TRANSITIVE':
      return 'Pinned (transitive)';
  }
  return '';
});

const verdictBadgeVariant = computed<Variant>(() => {
  switch (detail.value?.unloadability.verdict) {
    case 'UNLOADABLE':
      return 'success';
    case 'PINNED_ROOTED':
      return 'secondary';
    case 'PINNED_TRANSITIVE':
      return 'warning';
  }
  return 'secondary';
});

const parentSimpleName = computed(() => {
  const name = detail.value?.parentDisplayName ?? '';
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
});

const filteredClasses = computed(() => {
  if (!detail.value) {
    return [];
  }
  const query = classSearchQuery.value.trim().toLowerCase();
  const base = query
    ? detail.value.classes.filter(c => c.name.toLowerCase().includes(query))
    : [...detail.value.classes];
  const direction = classSortDirection.value === 'asc' ? 1 : -1;
  base.sort((a, b) => {
    switch (classSortColumn.value) {
      case 'instanceCount':
      default:
        return direction * (a.instanceCount - b.instanceCount);
      case 'totalInstanceSize':
        return direction * (a.totalInstanceSize - b.totalInstanceSize);
    }
  });
  return base;
});

const visibleClasses = computed(() => filteredClasses.value.slice(0, visibleClassCount.value));

const hiddenClassCount = computed(() =>
  Math.max(0, filteredClasses.value.length - visibleClasses.value.length)
);

const resultsSummary = computed(() => {
  const total = detail.value?.classes.length ?? 0;
  const matching = filteredClasses.value.length;
  const shown = Math.min(visibleClassCount.value, matching);
  if (classSearchQuery.value.trim().length > 0) {
    return `Showing ${shown} of ${matching} matching (${total} total)`;
  }
  return `Showing ${shown} of ${total}`;
});

const showMore = () => {
  visibleClassCount.value += PAGE_SIZE;
};

const showAll = () => {
  visibleClassCount.value = filteredClasses.value.length;
};

// Reset the visible window when the filter changes or a new loader is loaded.
watch([classSearchQuery, () => detail.value?.loaderId], () => {
  visibleClassCount.value = PAGE_SIZE;
});

const toggleClassSort = (column: string) => {
  if (classSortColumn.value === column) {
    classSortDirection.value = classSortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    classSortColumn.value = column;
    classSortDirection.value = 'desc';
  }
};

const openParent = () => {
  if (detail.value && detail.value.parentLoaderId !== 0) {
    emit('open-loader', detail.value.parentLoaderId);
  }
};

const loadDetail = async () => {
  if (props.loaderId === null) {
    detail.value = null;
    return;
  }
  loading.value = true;
  error.value = null;
  try {
    const client = new HeapDumpClient(props.profileId);
    detail.value = await client.getClassLoaderDetail(props.loaderId);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load class loader detail';
    detail.value = null;
  } finally {
    loading.value = false;
  }
};

watch(
  () => [props.show, props.loaderId] as const,
  ([show]) => {
    if (show) {
      classSearchQuery.value = '';
      visibleClassCount.value = PAGE_SIZE;
      loadDetail();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.detail-header {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  padding-bottom: var(--spacing-3);
  border-bottom: 1px solid var(--color-border);
}

.header-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.loader-name {
  font-weight: 600;
  font-size: 0.95rem;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-4);
  margin-top: var(--spacing-1);
}

.meta-item {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.meta-value {
  font-size: 0.85rem;
  color: var(--color-text);
}

.parent-link {
  text-decoration: none;
  color: var(--color-primary);
}

.parent-link:hover {
  text-decoration: underline;
}

.parent-link.disabled {
  color: var(--color-text-muted);
  pointer-events: none;
  text-decoration: none;
}

.classes-section {
  margin-top: var(--spacing-4);
}

.classes-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-2);
}

.classes-toolbar :deep(.search-container) {
  max-width: 320px;
  flex-shrink: 0;
}

.results-summary {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  flex-grow: 1;
}

.empty-search {
  padding: var(--spacing-3) 0;
}

.truncate-row {
  background-color: var(--color-bg-hover);
}

.truncate-row:hover {
  background-color: var(--color-bg-hover);
}

.truncate-row td {
  border-top: 1px dashed var(--color-border);
}

.truncate-row-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  padding: var(--spacing-1) 0;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  font-style: italic;
}

.truncate-row-content i {
  font-size: 1rem;
  color: var(--color-text-muted);
}

.truncate-row-content .btn {
  font-style: normal;
}

.font-monospace {
  font-size: 0.8rem;
}

.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

<style>
/* Non-scoped so it can reach .modal-dialog inside GenericModal. */
.cl-detail-modal-dialog {
  max-width: min(1600px, 95vw);
}
</style>

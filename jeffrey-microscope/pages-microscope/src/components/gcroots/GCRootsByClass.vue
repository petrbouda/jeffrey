<template>
  <LoadingState v-if="loading" message="Aggregating roots by class..." />
  <ErrorState v-else-if="error" :message="error" />
  <EmptyState
    v-else-if="rows.length === 0"
    icon="bi-diagram-2"
    title="No class-level aggregates available"
  />
  <DataTable v-else>
    <template #toolbar>
      <TableToolbar :show-search="false">
        <span class="toolbar-info">{{ rows.length }} classes</span>
      </TableToolbar>
    </template>
    <thead>
      <tr>
        <th>Class</th>
        <th class="text-end" style="width: 110px"># Roots</th>
        <th>Root kinds</th>
        <th class="text-end" style="width: 120px">Total Retained</th>
        <th style="width: 200px">Share</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="r in rows" :key="r.className">
        <td><ClassNameDisplay :class-name="r.className" /></td>
        <td class="text-end font-monospace">{{ FormattingService.formatNumber(r.rootCount) }}</td>
        <td>
          <Badge
            v-for="k in r.rootKinds"
            :key="k"
            :value="k"
            :variant="kindVariant(k)"
            size="xxs"
            class="me-1"
          />
        </td>
        <td class="text-end font-monospace text-warning">
          {{ FormattingService.formatBytes(r.totalRetainedBytes) }}
        </td>
        <td>
          <div class="d-flex align-items-center gap-2">
            <div class="progress flex-grow-1" style="height: 6px">
              <div
                class="progress-bar"
                :style="{ width: sharePct(r.totalRetainedBytes) + '%' }"
              ></div>
            </div>
            <small class="text-muted" style="min-width: 45px"
              >{{ sharePct(r.totalRetainedBytes).toFixed(0) }}%</small
            >
          </div>
        </td>
      </tr>
    </tbody>
  </DataTable>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import Badge from '@shared/components/Badge.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type GCRootClassAggregate from '@/services/api/model/GCRootClassAggregate';
import FormattingService from '@shared/services/FormattingService';
import type { Variant } from '@shared/types/ui';

const props = defineProps<{
  client: HeapDumpClient;
  active: boolean;
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const rows = ref<GCRootClassAggregate[]>([]);
const loaded = ref(false);

const maxRetained = computed(() =>
  rows.value.length > 0 ? Math.max(...rows.value.map(r => r.totalRetainedBytes)) : 0
);

const sharePct = (bytes: number): number =>
  maxRetained.value > 0 ? (bytes / maxRetained.value) * 100 : 0;

const kindVariant = (kind: string): Variant => {
  if (kind.startsWith('JNI')) {
    return 'green';
  }
  if (kind.includes('Thread')) {
    return 'violet';
  }
  if (kind === 'Sticky class') {
    return 'orange';
  }
  if (kind === 'Monitor used') {
    return 'red';
  }
  return 'blue';
};

const reload = async () => {
  loading.value = true;
  error.value = null;
  try {
    rows.value = await props.client.getRootsByClass(100);
    loaded.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to aggregate roots by class';
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.active,
  active => {
    if (active && !loaded.value && !loading.value) {
      reload();
    }
  }
);

onMounted(() => {
  if (props.active) {
    reload();
  }
});
</script>

<style scoped>
.toolbar-info {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.progress {
  background-color: var(--color-border);
}
.progress-bar {
  background-color: var(--color-accent-blue);
  transition: width 0.3s ease;
}
.font-monospace {
  font-size: 0.8rem;
}
.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

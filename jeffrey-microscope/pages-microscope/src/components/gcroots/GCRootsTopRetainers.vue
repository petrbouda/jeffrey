<template>
  <LoadingState v-if="loading" message="Ranking GC roots by retained size..." />
  <ErrorState v-else-if="error" :message="error" />
  <EmptyState
    v-else-if="rows.length === 0"
    icon="bi-trophy"
    title="No GC roots ranked yet"
  />
  <DataTable v-else>
    <template #toolbar>
      <TableToolbar :show-search="false">
        <span class="toolbar-info">Showing top {{ rows.length }} roots</span>
        <template #filters>
          <div class="d-flex align-items-center">
            <label class="form-label mb-0 me-2 toolbar-info">Top:</label>
            <select v-model="limit" class="form-select form-select-sm select-narrow" @change="reload">
              <option :value="50">50</option>
              <option :value="100">100</option>
              <option :value="200">200</option>
              <option :value="500">500</option>
            </select>
          </div>
        </template>
      </TableToolbar>
    </template>
    <thead>
      <tr>
        <th style="width: 40px">#</th>
        <th>Class</th>
        <th style="width: 90px">Root Kind</th>
        <th class="text-end" style="width: 160px"></th>
        <th class="text-end" style="width: 110px">Retained</th>
        <th style="width: 180px">% of heap</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="(r, i) in rows" :key="r.objectId">
        <td class="text-muted">{{ i + 1 }}</td>
        <td><ClassNameDisplay :class-name="r.className" /></td>
        <td><Badge :value="r.rootKind" :variant="kindVariant(r.rootKind)" size="xxs" /></td>
        <td class="text-end">
          <InstanceActionButtons
            :object-id="r.objectId"
            :show-instance-detail="true"
            @show-referrers="emit('showReferrers', $event)"
            @show-reachables="emit('showReachables', $event)"
            @show-g-c-root-path="emit('showGCRootPath', $event)"
            @show-instance-detail="emit('showInstanceDetail', $event)"
          />
        </td>
        <td class="text-end font-monospace text-warning">
          {{ FormattingService.formatBytes(r.retainedSize) }}
        </td>
        <td>
          <div class="d-flex align-items-center gap-2">
            <div class="progress flex-grow-1" style="height: 6px">
              <div
                class="progress-bar"
                :style="{ width: pctOfHeap(r.retainedSize) + '%', backgroundColor: 'var(--color-accent-blue)' }"
              ></div>
            </div>
            <small class="text-muted" style="min-width: 45px">{{ pctOfHeap(r.retainedSize).toFixed(1) }}%</small>
          </div>
        </td>
      </tr>
    </tbody>
  </DataTable>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import Badge from '@/components/Badge.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type GCRootRetainer from '@/services/api/model/GCRootRetainer';
import FormattingService from '@/services/FormattingService';
import type { Variant } from '@/types/ui';

const props = defineProps<{
  client: HeapDumpClient;
  totalHeapBytes: number;
  active: boolean;
}>();

const emit = defineEmits<{
  showReferrers: [objectId: number];
  showReachables: [objectId: number];
  showGCRootPath: [objectId: number];
  showInstanceDetail: [objectId: number];
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const rows = ref<GCRootRetainer[]>([]);
const limit = ref(100);
const loaded = ref(false);

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

const pctOfHeap = (retained: number): number => {
  if (props.totalHeapBytes <= 0) {
    return 0;
  }
  return Math.min(100, (retained / props.totalHeapBytes) * 100);
};

const reload = async () => {
  loading.value = true;
  error.value = null;
  try {
    rows.value = await props.client.getTopRetainers(limit.value);
    loaded.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load top retainers';
  } finally {
    loading.value = false;
  }
};

// Lazy-load: only fetch when this tab is first activated.
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

.select-narrow {
  width: 80px;
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

.text-warning {
  color: var(--color-goldenrod) !important;
}
</style>

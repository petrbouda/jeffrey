<template>
  <LoadingState v-if="loading" message="Grouping roots by classloader..." />
  <ErrorState v-else-if="error" :message="error" />
  <EmptyState
    v-else-if="rows.length === 0"
    icon="bi-stack"
    title="No classloader aggregates available"
  />
  <div v-else>
    <div class="treemap-grid">
      <div
        v-for="(r, i) in rows"
        :key="loaderKey(r, i)"
        class="cl-block"
        :style="blockStyle(r, i)"
        :title="r.classloaderClass"
      >
        <div class="cl-name">
          {{ shortName(r.classloaderClass) }}
        </div>
        <div class="cl-stats">
          <span>{{ FormattingService.formatNumber(r.rootCount) }} roots</span>
          <span>{{ FormattingService.formatBytes(r.totalRetainedBytes) }}</span>
        </div>
      </div>
    </div>

    <div class="legend">
      <p class="legend-hint">
        Block size is proportional to retained bytes. The
        <code>Bootstrap</code> loader (system classes that never unload) is usually
        the largest — what matters is the <em>second-tier</em> loaders that retain
        unexpectedly much.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type GCRootClassLoaderAggregate from '@/services/api/model/GCRootClassLoaderAggregate';
import FormattingService from '@/services/FormattingService';

const props = defineProps<{
  client: HeapDumpClient;
  active: boolean;
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const rows = ref<GCRootClassLoaderAggregate[]>([]);
const loaded = ref(false);

const palette = [
  'var(--color-violet-text)',
  'var(--color-accent-blue)',
  'var(--color-emerald)',
  'var(--color-amber-highlight)',
  'var(--color-orange)',
  'var(--color-danger)',
  'var(--color-text-muted)'
];

const totalRetained = computed(() => rows.value.reduce((s, r) => s + r.totalRetainedBytes, 0));

const loaderKey = (r: GCRootClassLoaderAggregate, i: number): string =>
  r.classloaderObjectId === null ? 'bootstrap' : `${r.classloaderObjectId}-${i}`;

const shortName = (fqn: string): string => {
  if (fqn === 'Bootstrap') {
    return 'Bootstrap';
  }
  const lastDot = fqn.lastIndexOf('.');
  return lastDot > 0 ? fqn.substring(lastDot + 1) : fqn;
};

const blockStyle = (r: GCRootClassLoaderAggregate, i: number) => {
  // Treemap-ish: span-cols/rows proportional to share of total retained.
  const share = totalRetained.value > 0 ? r.totalRetainedBytes / totalRetained.value : 0;
  let cols = 1;
  let rows = 1;
  if (share >= 0.4) { cols = 3; rows = 2; }
  else if (share >= 0.2) { cols = 2; rows = 2; }
  else if (share >= 0.1) { cols = 2; rows = 1; }
  return {
    backgroundColor: palette[i % palette.length],
    gridColumn: `span ${cols}`,
    gridRow: `span ${rows}`
  };
};

const reload = async () => {
  loading.value = true;
  error.value = null;
  try {
    rows.value = await props.client.getRootsByClassLoader(30);
    loaded.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to group roots by classloader';
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
.treemap-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
}

.cl-block {
  padding: 0.75rem 0.9rem;
  border-radius: var(--radius-sm);
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 90px;
  font-family: monospace;
  cursor: default;
}

.cl-name {
  font-size: 0.78rem;
  font-weight: 600;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cl-stats {
  font-size: 0.7rem;
  opacity: 0.92;
  margin-top: 0.45rem;
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
}

.legend {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  background: var(--color-light);
  border-radius: var(--radius-sm);
}

.legend-hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  line-height: 1.55;
}

.legend-hint code {
  background: var(--color-code-bg);
  color: var(--color-code-text);
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
  font-size: 0.86em;
}
</style>

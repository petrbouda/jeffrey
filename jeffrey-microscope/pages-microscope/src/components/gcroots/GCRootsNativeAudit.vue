<template>
  <LoadingState v-if="loading" message="Loading native / JNI roots..." />
  <ErrorState v-else-if="error" :message="error" />
  <div v-else>
    <div class="native-grid">
      <div v-for="(panel, idx) in panels" :key="idx" class="native-panel">
        <div class="native-panel-header">
          <span>{{ panel.title }}</span>
          <span class="ct">
            {{ FormattingService.formatNumber(panel.rows.length) }} roots
            <template v-if="panel.totalRetained > 0">
              · {{ FormattingService.formatBytes(panel.totalRetained) }}
            </template>
          </span>
        </div>
        <EmptyState v-if="panel.rows.length === 0" icon="bi-cpu" title="None" />
        <div v-else class="native-rows">
          <div v-for="r in panel.rows.slice(0, 12)" :key="r.objectId" class="native-row">
            <div class="row-main">
              <ClassNameDisplay :class-name="r.className" />
            </div>
            <div class="row-sub">
              <span class="mono">{{ FormattingService.formatObjectId(r.objectId) }}</span>
              <span class="retained mono">{{ FormattingService.formatBytes(r.retainedSize) }}</span>
              <InstanceActionButtons
                :object-id="r.objectId"
                :show-instance-detail="true"
                @show-referrers="emit('showReferrers', $event)"
                @show-reachables="emit('showReachables', $event)"
                @show-g-c-root-path="emit('showGCRootPath', $event)"
                @show-instance-detail="emit('showInstanceDetail', $event)"
              />
            </div>
          </div>
          <div v-if="panel.rows.length > 12" class="native-row more">
            + {{ panel.rows.length - 12 }} more
          </div>
        </div>
      </div>
    </div>

    <div class="legend">
      <p class="legend-hint">
        <strong>JNI Global</strong> is the leak-prone category — long-lived references held by
        native code via <code>NewGlobalRef</code>. Missing <code>DeleteGlobalRef</code> on a code
        path is the canonical cause of growing RSS without growing Java heap. The other two are
        typically transient.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import InstanceActionButtons from '@/components/heap/InstanceActionButtons.vue';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type GCRootRetainer from '@/services/api/model/GCRootRetainer';
import FormattingService from '@shared/services/FormattingService';

// HPROF root-kind sub-tags
const ROOT_JNI_GLOBAL = 0x01;
const ROOT_JNI_LOCAL = 0x02;
const ROOT_NATIVE_STACK = 0x04;

const props = defineProps<{
  client: HeapDumpClient;
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
const loaded = ref(false);
const jniGlobal = ref<GCRootRetainer[]>([]);
const jniLocal = ref<GCRootRetainer[]>([]);
const nativeStack = ref<GCRootRetainer[]>([]);

const panels = computed(() => [
  {
    title: 'JNI Global',
    rows: jniGlobal.value,
    totalRetained: jniGlobal.value.reduce((s, r) => s + r.retainedSize, 0)
  },
  {
    title: 'JNI Local',
    rows: jniLocal.value,
    totalRetained: jniLocal.value.reduce((s, r) => s + r.retainedSize, 0)
  },
  {
    title: 'Native Stack',
    rows: nativeStack.value,
    totalRetained: nativeStack.value.reduce((s, r) => s + r.retainedSize, 0)
  }
]);

const reload = async () => {
  loading.value = true;
  error.value = null;
  try {
    // Three parallel requests — each filters to a single root-kind.
    const [g, l, n] = await Promise.all([
      props.client.getTopRetainers(50, [ROOT_JNI_GLOBAL]),
      props.client.getTopRetainers(50, [ROOT_JNI_LOCAL]),
      props.client.getTopRetainers(50, [ROOT_NATIVE_STACK])
    ]);
    jniGlobal.value = g;
    jniLocal.value = l;
    nativeStack.value = n;
    loaded.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load native / JNI roots';
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
.native-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1rem;
}

@media (max-width: 992px) {
  .native-grid {
    grid-template-columns: 1fr;
  }
}

.native-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
}

.native-panel-header {
  padding: 0.6rem 0.95rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-hover);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.native-panel-header .ct {
  font-family: monospace;
  font-size: 0.72rem;
  color: var(--color-text);
  text-transform: none;
  letter-spacing: 0;
  font-weight: 500;
}

.native-row {
  padding: 0.55rem 0.95rem;
  border-bottom: 1px solid var(--color-border-row);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.78rem;
}

.native-row:last-child {
  border-bottom: none;
}

.native-row.more {
  color: var(--color-text-muted);
  font-style: italic;
}

.row-main {
  min-width: 0;
}

.row-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.row-sub .mono {
  font-family: monospace;
  color: var(--color-text-muted);
  font-size: 0.74rem;
}

.row-sub .retained {
  color: var(--color-goldenrod);
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

<template>
  <LoadingState v-if="loading" message="Running leak heuristics..." />
  <ErrorState v-else-if="error" :message="error" />
  <EmptyState
    v-else-if="findings.length === 0"
    icon="bi-check-circle"
    title="No leak hints — heap looks clean"
  />
  <div v-else class="findings">
    <div
      v-for="(f, idx) in findings"
      :key="idx"
      class="finding"
      :class="severityClass(f.severity)"
    >
      <div class="finding-icon">
        <i :class="severityIcon(f.severity)"></i>
      </div>
      <div class="finding-body">
        <Badge
          :value="f.severity"
          size="xxs"
          :variant="severityVariant(f.severity)"
          class="mb-1"
        />
        <h3 class="finding-title">{{ f.title }}</h3>
        <p class="finding-desc">{{ f.details }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type LeakHintFinding from '@/services/api/model/LeakHintFinding';
import type { LeakHintSeverity } from '@/services/api/model/LeakHintFinding';
import type { Variant } from '@/types/ui';

const props = defineProps<{
  client: HeapDumpClient;
  active: boolean;
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const findings = ref<LeakHintFinding[]>([]);
const loaded = ref(false);

const severityClass = (s: LeakHintSeverity): string => `sev-${s.toLowerCase()}`;

const severityIcon = (s: LeakHintSeverity): string => {
  if (s === 'HIGH') {
    return 'bi bi-exclamation-triangle-fill';
  }
  if (s === 'MEDIUM') {
    return 'bi bi-info-circle-fill';
  }
  return 'bi bi-info-circle';
};

const severityVariant = (s: LeakHintSeverity): Variant => {
  if (s === 'HIGH') {
    return 'danger';
  }
  if (s === 'MEDIUM') {
    return 'warning';
  }
  return 'info';
};

const reload = async () => {
  loading.value = true;
  error.value = null;
  try {
    findings.value = await props.client.getLeakHints();
    loaded.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to run leak hints';
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
.findings {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.finding {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 0.85rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  align-items: flex-start;
}

.finding.sev-high { border-left: 3px solid var(--color-danger); }
.finding.sev-medium { border-left: 3px solid var(--color-amber); }
.finding.sev-low { border-left: 3px solid var(--color-blue-500); }

.finding-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-base);
  background: var(--color-light);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: 1.1rem;
  flex-shrink: 0;
}

.sev-high .finding-icon { background: var(--color-danger-bg-lighter); color: var(--color-danger); }
.sev-medium .finding-icon { background: var(--color-warning-bg); color: var(--color-amber-highlight); }
.sev-low .finding-icon { background: var(--color-blue-bg); color: var(--color-blue-text); }

.finding-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-dark);
  margin: 0 0 0.25rem 0;
}

.finding-desc {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  line-height: 1.55;
  margin: 0;
}
</style>

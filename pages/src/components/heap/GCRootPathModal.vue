<template>
  <div v-if="show" class="modal fade show d-block" tabindex="-1" @click.self="$emit('close')">
    <div class="modal-dialog modal-lg modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            <i class="bi bi-signpost-2 me-2"></i>
            Path to GC Root
          </h5>
          <button type="button" class="btn-close" @click="$emit('close')"></button>
        </div>
        <div class="modal-body">
          <!-- Options -->
          <div class="d-flex align-items-center gap-3 mb-3">
            <div class="form-check">
              <input class="form-check-input" type="checkbox" v-model="excludeWeakRefs" id="excludeWeak"
                     @change="loadPaths">
              <label class="form-check-label small" for="excludeWeak">Exclude weak/soft/phantom refs</label>
            </div>
          </div>

          <!-- Loading -->
          <div v-if="loading" class="text-center py-4">
            <div class="spinner-border spinner-border-sm me-2" role="status"></div>
            <span class="text-muted">Finding paths to GC roots...</span>
          </div>

          <!-- Error -->
          <div v-else-if="error" class="alert alert-danger">{{ error }}</div>

          <!-- No paths found -->
          <div v-else-if="paths.length === 0" class="text-center text-muted py-4">
            <i class="bi bi-x-circle fs-3 d-block mb-2"></i>
            <p>No GC root path found for this object.</p>
          </div>

          <!-- Paths -->
          <div v-else>
            <div v-for="(path, pathIndex) in paths" :key="pathIndex" class="gc-root-path mb-4">
              <div class="path-header mb-2">
                <span class="badge bg-secondary">Path {{ pathIndex + 1 }}</span>
                <span class="text-muted small ms-2">{{ path.steps.length }} steps via {{ path.rootType }}</span>
              </div>
              <div class="path-chain">
                <div v-for="(step, stepIndex) in path.steps" :key="stepIndex"
                     class="path-step"
                     :class="{ 'path-step-root': stepIndex === 0, 'path-step-target': step.isTarget }">
                  <div class="step-connector" v-if="stepIndex > 0">
                    <div class="connector-line"></div>
                    <span class="connector-label" v-if="step.fieldName">{{ step.fieldName }}</span>
                  </div>
                  <div class="step-node">
                    <div class="step-icon">
                      <i v-if="stepIndex === 0" class="bi bi-circle-fill text-success"></i>
                      <i v-else-if="step.isTarget" class="bi bi-circle-fill text-primary"></i>
                      <i v-else class="bi bi-circle text-muted"></i>
                    </div>
                    <div class="step-info">
                      <div class="step-class">{{ step.className }}</div>
                      <div class="step-meta">
                        <span class="step-size">{{ FormattingService.formatBytes(step.shallowSize) }}</span>
                        <span v-if="step.displayValue" class="step-value text-muted">{{ truncateValue(step.displayValue) }}</span>
                      </div>
                      <div v-if="stepIndex === 0" class="step-badge">
                        <span class="badge bg-success-subtle text-success">GC Root: {{ path.rootType }}</span>
                      </div>
                      <div v-if="step.isTarget" class="step-badge">
                        <span class="badge bg-primary-subtle text-primary">TARGET</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div v-if="show" class="modal-backdrop fade show"></div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import { GCRootPath } from '@/services/api/model/GCRootPath';
import FormattingService from '@/services/FormattingService';

interface Props {
  show: boolean;
  objectId: number;
  profileId: string;
}

const props = defineProps<Props>();
defineEmits<{ close: [] }>();

const loading = ref(false);
const error = ref<string | null>(null);
const paths = ref<GCRootPath[]>([]);
const excludeWeakRefs = ref(true);

const truncateValue = (value: string): string => {
  return value.length > 80 ? value.substring(0, 80) + '...' : value;
};

const loadPaths = async () => {
  if (!props.show || !props.objectId) return;

  loading.value = true;
  error.value = null;
  paths.value = [];

  try {
    const client = new HeapDumpClient(props.profileId);
    paths.value = await client.getPathToGCRoot(props.objectId, excludeWeakRefs.value, 3);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to find GC root paths';
  } finally {
    loading.value = false;
  }
};

watch(() => props.show, (newVal) => {
  if (newVal) {
    loadPaths();
  }
});
</script>

<style scoped>
.modal-content {
  max-height: 85vh;
  overflow-y: auto;
}

.gc-root-path {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 1rem;
  background: #fafbfc;
}

.path-header {
  display: flex;
  align-items: center;
}

.path-chain {
  padding-left: 0.5rem;
}

.path-step {
  position: relative;
}

.step-connector {
  display: flex;
  align-items: center;
  padding: 0.25rem 0 0.25rem 0.55rem;
}

.connector-line {
  width: 2px;
  height: 20px;
  background: #dee2e6;
  position: absolute;
  left: 0.75rem;
}

.connector-label {
  font-size: 0.75rem;
  font-family: monospace;
  color: #6f42c1;
  margin-left: 1.5rem;
  background: #f3eeff;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
}

.step-node {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.35rem 0;
}

.step-icon {
  flex-shrink: 0;
  font-size: 0.6rem;
  margin-top: 0.3rem;
}

.step-info {
  min-width: 0;
}

.step-class {
  font-size: 0.85rem;
  font-weight: 500;
  color: #343a40;
  word-break: break-all;
}

.step-meta {
  display: flex;
  gap: 0.75rem;
  font-size: 0.75rem;
  margin-top: 0.15rem;
}

.step-size {
  color: #495057;
  font-family: monospace;
}

.step-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.step-badge {
  margin-top: 0.25rem;
}

.bg-success-subtle {
  background-color: #d1e7dd;
}

.bg-primary-subtle {
  background-color: #cfe2ff;
}
</style>

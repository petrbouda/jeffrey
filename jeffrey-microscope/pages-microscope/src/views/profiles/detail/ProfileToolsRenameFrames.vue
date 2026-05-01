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
  <PageHeader
    icon="bi-pencil-square"
    title="Rename Frames"
    description="Rename packages and classes in frame data to anonymize proprietary code before sharing graphs."
  >
    <!-- Success State -->
    <div
      v-if="state === 'success'"
      class="alert alert-success d-flex align-items-center justify-content-between mb-4 py-2 small"
    >
      <div>
        <i class="bi bi-check-circle me-2"></i>
        <strong>{{ lastRenameCount }}</strong> frames renamed successfully.
      </div>
      <button class="btn btn-sm btn-outline-success" @click="resetToIdle">Dismiss</button>
    </div>

    <!-- Main Content -->
    <div>
      <!-- Input Fields -->
      <div class="row align-items-center mb-3">
        <div class="col">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted">Search</label>
          <div class="input-group search-container">
            <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
            <input
              type="text"
              class="form-control search-input"
              placeholder="e.g. com.company.internal"
              v-model="search"
              :disabled="isLocked"
              @keyup.enter="handlePreview"
            />
          </div>
        </div>
        <div class="col-auto d-flex" style="margin-top: 1.4rem; height: 38px; align-items: center">
          <i class="bi bi-arrow-right text-muted"></i>
        </div>
        <div class="col">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted"
            >Replace with</label
          >
          <div class="input-group search-container">
            <span class="input-group-text"><i class="bi bi-pencil search-icon"></i></span>
            <input
              type="text"
              class="form-control search-input"
              placeholder="e.g. com.example.app"
              v-model="replacement"
              :disabled="isLocked"
              @keyup.enter="handlePreview"
            />
          </div>
        </div>
      </div>

      <!-- Action Bar (normal state) -->
      <div v-if="state !== 'confirming'" class="d-flex align-items-center gap-2 mb-4">
        <button
          class="btn btn-sm btn-outline-primary"
          :disabled="!canPreview"
          @click="handlePreview"
        >
          <span v-if="state === 'previewing'" class="spinner-border spinner-border-sm me-1"></span>
          <i v-else class="bi bi-eye me-1"></i>
          Preview
        </button>

        <button
          v-if="state === 'preview-ok'"
          class="btn btn-sm btn-primary"
          @click="state = 'confirming'"
        >
          <i class="bi bi-check2-all me-1"></i>
          Apply Rename
        </button>

        <div v-if="state === 'preview-empty'" class="ms-auto">
          <span class="text-muted small">
            <i class="bi bi-info-circle me-1"></i>
            No matching frames found
          </span>
        </div>
      </div>

      <!-- Confirmation Bar -->
      <div
        v-if="state === 'confirming'"
        class="alert alert-danger d-flex align-items-center justify-content-between mb-4"
      >
        <div>
          <i class="bi bi-exclamation-diamond-fill me-1"></i>
          Rename <strong>{{ previewResult!.affectedFrames }}</strong> frames? This is permanent and
          cannot be undone. To revert, you will need to regenerate the whole profile.
        </div>
        <div class="d-flex gap-2 flex-shrink-0">
          <button class="btn btn-sm btn-outline-secondary" @click="state = 'preview-ok'">
            Cancel
          </button>
          <button class="btn btn-sm btn-danger" @click="handleApply">Confirm Rename</button>
        </div>
      </div>

      <!-- Applying state -->
      <div v-if="state === 'applying'" class="alert alert-info d-flex align-items-center mb-4">
        <span class="spinner-border spinner-border-sm me-2"></span>
        Renaming frames...
      </div>

      <!-- Error State -->
      <div v-if="state === 'error'" class="alert alert-danger d-flex align-items-start mb-4">
        <i class="bi bi-x-circle me-2 mt-1"></i>
        <div>
          {{ errorMessage }}
          <button class="btn btn-sm btn-outline-secondary ms-2" @click="resetToIdle">
            Try Again
          </button>
        </div>
      </div>

      <!-- Preview Results Table -->
      <div
        v-if="
          previewResult &&
          previewResult.samples.length > 0 &&
          (state === 'preview-ok' || state === 'confirming')
        "
      >
        <div class="d-flex align-items-center justify-content-between mb-3">
          <div class="d-flex align-items-center gap-2">
            <i class="bi bi-eye text-primary"></i>
            <span class="fw-semibold small">Preview</span>
            <span class="text-muted small">— unique classes only</span>
          </div>
          <Badge
            :value="previewResult.affectedFrames + ' frames affected'"
            variant="blue"
            size="xs"
          />
        </div>

        <div class="d-flex flex-column gap-2 mb-4">
          <div v-for="(sample, index) in uniqueSamples" :key="index" class="sample-card">
            <code class="sample-original">{{ sample.originalClassName }}</code>
            <i class="bi bi-arrow-right sample-arrow"></i>
            <code class="sample-renamed">{{ sample.renamedClassName }}</code>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import Badge from '@/components/Badge.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import ProfileToolsClient from '@/services/api/ProfileToolsClient';
import type RenameFramesPreview from '@/services/api/model/RenameFramesPreview';
import ToastService from '@/services/ToastService';
import '@/styles/shared-components.css';

type ToolState =
  | 'idle'
  | 'previewing'
  | 'preview-ok'
  | 'preview-empty'
  | 'confirming'
  | 'applying'
  | 'success'
  | 'error';

const route = useRoute();
const profileId = route.params.profileId as string;

const state = ref<ToolState>('idle');
const search = ref('');
const replacement = ref('');
const previewResult = ref<RenameFramesPreview | null>(null);
const lastRenameCount = ref(0);
const errorMessage = ref('');

const canPreview = computed(() => {
  return search.value.trim().length > 0 && state.value !== 'previewing';
});

const isLocked = computed(() => {
  return state.value === 'confirming' || state.value === 'applying';
});

const uniqueSamples = computed(() => {
  if (!previewResult.value) return [];
  const seen = new Set<string>();
  return previewResult.value.samples.filter(s => {
    const key = s.originalClassName + '→' + s.renamedClassName;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
});

// Reset when inputs change
watch([search, replacement], () => {
  if (state.value === 'preview-ok' || state.value === 'preview-empty' || state.value === 'error') {
    state.value = 'idle';
    previewResult.value = null;
  }
});

const resetToIdle = () => {
  state.value = 'idle';
  search.value = '';
  replacement.value = '';
  previewResult.value = null;
  errorMessage.value = '';
};

const handlePreview = async () => {
  if (!canPreview.value) return;

  state.value = 'previewing';
  try {
    const client = new ProfileToolsClient(profileId);
    const result = await client.previewRename(search.value, replacement.value);
    previewResult.value = result;
    state.value = result.affectedFrames > 0 ? 'preview-ok' : 'preview-empty';
  } catch (error) {
    console.error('Preview failed:', error);
    state.value = 'error';
    errorMessage.value = 'Failed to preview rename operation';
  }
};

const handleApply = async () => {
  state.value = 'applying';
  try {
    const client = new ProfileToolsClient(profileId);
    const result = await client.executeRename(search.value, replacement.value);
    lastRenameCount.value = result.renamedFrames;
    search.value = '';
    replacement.value = '';
    previewResult.value = null;
    state.value = 'success';
  } catch (error) {
    console.error('Rename failed:', error);
    state.value = 'error';
    errorMessage.value = 'Failed to execute rename operation';
    ToastService.error('Rename failed', 'An error occurred while renaming frames');
  }
};
</script>

<style scoped>
/* Sample cards */
.sample-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-white);
}

.sample-card:hover {
  background: var(--color-light);
}

.sample-original {
  font-size: 0.82rem;
  color: var(--color-danger-dark);
  background: rgba(220, 53, 69, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  word-break: break-all;
}

.sample-arrow {
  color: var(--color-text-light);
  flex-shrink: 0;
}

.sample-renamed {
  font-size: 0.82rem;
  color: var(--color-success-dark);
  background: rgba(25, 135, 84, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  word-break: break-all;
}

/* Success state centering + animation */
.success-state {
  padding: 24px 0;
}

.success-icon-wrapper {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(25, 135, 84, 0.1);
  border: 2px solid rgba(25, 135, 84, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: successPulse 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.success-icon-wrapper i {
  font-size: 2rem;
  color: var(--color-success);
}

@keyframes successPulse {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.15);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>

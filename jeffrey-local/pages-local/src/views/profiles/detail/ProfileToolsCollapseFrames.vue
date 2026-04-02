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
    icon="bi-layers"
    title="Collapse Frames"
    description="Replace consecutive stacktrace frames matching class name patterns with a single synthetic frame. Simplifies flamegraphs by hiding framework internals."
  >
    <!-- Success State -->
    <div
      v-if="state === 'success'"
      class="alert alert-success d-flex align-items-center justify-content-between mb-4 py-2 small"
    >
      <div>
        <i class="bi bi-check-circle me-2"></i>
        <strong>{{ lastResult!.affectedStacktraces }}</strong> stacktraces collapsed
        <span v-if="lastResult!.mergedStacktraces > 0">
          ({{ lastResult!.mergedStacktraces }} merged)</span
        >.
      </div>
      <button class="btn btn-sm btn-outline-success" @click="resetToIdle">Dismiss</button>
    </div>

    <!-- Presets -->
    <div class="preset-section">
      <label class="form-label mb-1 small text-uppercase fw-semibold text-muted">Presets</label>
      <div class="preset-cards">
        <div
          v-for="preset in PRESETS"
          :key="preset.id"
          class="preset-card"
          :class="{ active: activePreset === preset.id }"
          :style="
            activePreset === preset.id
              ? { borderColor: preset.color, background: preset.color + '0a' }
              : {}
          "
          @click="applyPreset(preset)"
        >
          <div class="preset-card-title">
            <span class="card-icon">
              <img :src="preset.image" :alt="preset.name" class="card-icon-img" />
            </span>
            {{ preset.name }}
          </div>
          <div class="preset-card-desc">{{ preset.description }}</div>
          <div class="preset-card-count">{{ preset.patterns.length }} patterns</div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div>
      <!-- Input Fields -->
      <div class="row align-items-start mb-3">
        <div class="col">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted"
            >Class Name Patterns</label
          >
          <div class="d-flex flex-column gap-2">
            <div v-for="(_, index) in patterns" :key="index" class="input-group search-container">
              <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
              <input
                type="text"
                class="form-control search-input"
                placeholder="e.g. org.springframework"
                v-model="patterns[index]"
                :disabled="isLocked"
                @keyup.enter="handlePreview"
              />
              <button
                v-if="patterns.length > 1"
                class="btn btn-outline-secondary btn-sm"
                type="button"
                :disabled="isLocked"
                @click="removePattern(index)"
                title="Remove pattern"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>
          <button
            class="btn btn-sm btn-outline-secondary mt-2"
            style="display: inline-flex; align-items: center"
            :disabled="isLocked"
            @click="addPattern"
          >
            <i class="bi bi-plus-lg me-1"></i>
            Add Pattern
          </button>
        </div>
        <div class="col-auto d-flex" style="margin-top: 1.4rem; height: 38px; align-items: center">
          <i class="bi bi-arrow-right text-muted"></i>
        </div>
        <div class="col">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted"
            >Synthetic Frame Label</label
          >
          <div class="input-group search-container">
            <span class="input-group-text"><i class="bi bi-tag search-icon"></i></span>
            <input
              type="text"
              class="form-control search-input"
              placeholder="e.g. Spring Framework"
              v-model="label"
              :disabled="isLocked"
              @keyup.enter="handlePreview"
            />
          </div>
        </div>
      </div>

      <!-- Action Bar -->
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
          Apply Collapse
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
          Collapse <strong>{{ previewResult!.affectedStacktraces }}</strong> stacktraces? This is
          permanent and cannot be undone.
        </div>
        <div class="d-flex gap-2 flex-shrink-0">
          <button class="btn btn-sm btn-outline-secondary" @click="state = 'preview-ok'">
            Cancel
          </button>
          <button class="btn btn-sm btn-danger" @click="handleApply">Confirm Collapse</button>
        </div>
      </div>

      <!-- Applying state -->
      <div v-if="state === 'applying'" class="alert alert-info d-flex align-items-center mb-4">
        <span class="spinner-border spinner-border-sm me-2"></span>
        Collapsing frames...
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

      <!-- Preview Results -->
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
            <span class="fw-semibold small">Matching Frames</span>
          </div>
          <div class="d-flex gap-2">
            <Badge :value="previewResult.matchingFrames + ' frames'" variant="blue" size="xs" />
            <Badge
              :value="previewResult.affectedStacktraces + ' stacktraces'"
              variant="grey"
              size="xs"
            />
          </div>
        </div>

        <div class="d-flex flex-column gap-2 mb-4">
          <div v-for="(sample, index) in previewResult.samples" :key="index" class="sample-card">
            <code class="sample-original">{{ sample.className }}.{{ sample.methodName }}</code>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import Badge from '@/components/Badge.vue';
import ProfileToolsClient from '@/services/api/ProfileToolsClient';
import type CollapseFramesPreview from '@/services/api/model/CollapseFramesPreview';
import type CollapseFramesResult from '@/services/api/model/CollapseFramesResult';
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

interface CollapsePreset {
  id: string;
  name: string;
  image: string;
  color: string;
  description: string;
  patterns: string[];
  label: string;
}

const PRESETS: CollapsePreset[] = [
  {
    id: 'spring',
    name: 'Spring Framework',
    image: '/spring-logo.webp',
    color: '#6db33f',
    description: 'Tomcat, Catalina, Coyote, Spring, Jakarta Servlet',
    patterns: [
      'org.apache.tomcat',
      'org.apache.coyote',
      'org.apache.catalina',
      'org.springframework',
      'jakarta.servlet'
    ],
    label: 'Spring Framework'
  }
];

const route = useRoute();
const profileId = route.params.profileId as string;

const state = ref<ToolState>('idle');
const patterns = ref<string[]>(['']);
const label = ref('');
const previewResult = ref<CollapseFramesPreview | null>(null);
const lastResult = ref<CollapseFramesResult | null>(null);
const errorMessage = ref('');

const activePreset = ref<string | null>(null);

const applyPreset = (preset: CollapsePreset) => {
  patterns.value = [...preset.patterns];
  label.value = preset.label;
  activePreset.value = preset.id;
  state.value = 'idle';
  previewResult.value = null;
};

const nonEmptyPatterns = computed(() => patterns.value.filter(p => p.trim().length > 0));

const canPreview = computed(() => {
  return (
    nonEmptyPatterns.value.length > 0 &&
    label.value.trim().length > 0 &&
    state.value !== 'previewing'
  );
});

const isLocked = computed(() => {
  return state.value === 'confirming' || state.value === 'applying';
});

const addPattern = () => {
  patterns.value.push('');
};

const removePattern = (index: number) => {
  patterns.value.splice(index, 1);
};

watch(
  [patterns, label],
  () => {
    activePreset.value = null;
    if (
      state.value === 'preview-ok' ||
      state.value === 'preview-empty' ||
      state.value === 'error'
    ) {
      state.value = 'idle';
      previewResult.value = null;
    }
  },
  { deep: true }
);

const resetToIdle = () => {
  state.value = 'idle';
  patterns.value = [''];
  label.value = '';
  previewResult.value = null;
  errorMessage.value = '';
};

const handlePreview = async () => {
  if (!canPreview.value) return;

  state.value = 'previewing';
  try {
    const client = new ProfileToolsClient(profileId);
    const result = await client.previewCollapse(nonEmptyPatterns.value, label.value);
    previewResult.value = result;
    state.value = result.matchingFrames > 0 ? 'preview-ok' : 'preview-empty';
  } catch (error) {
    console.error('Preview failed:', error);
    state.value = 'error';
    errorMessage.value = 'Failed to preview collapse operation';
  }
};

const handleApply = async () => {
  state.value = 'applying';
  try {
    const client = new ProfileToolsClient(profileId);
    lastResult.value = await client.executeCollapse(nonEmptyPatterns.value, label.value);
    patterns.value = [''];
    label.value = '';
    previewResult.value = null;
    state.value = 'success';
  } catch (error) {
    console.error('Collapse failed:', error);
    state.value = 'error';
    errorMessage.value = 'Failed to execute collapse operation';
    ToastService.error('Collapse failed', 'An error occurred while collapsing frames');
  }
};
</script>

<style scoped>
.preset-section {
  margin-bottom: 20px;
}

.preset-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.preset-card {
  padding: 12px 14px;
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  background: var(--card-bg);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.preset-card:hover {
  border-color: rgba(94, 100, 255, 0.25);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.08);
}

.preset-card.active {
  /* border-color and background set dynamically via inline style */
}

.preset-card-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-text, #1a202c);
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-icon {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
}

.card-icon-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.preset-card-desc {
  font-size: 0.72rem;
  color: var(--color-text-muted, #64748b);
  margin-top: 6px;
}

.preset-card-count {
  font-size: 0.68rem;
  color: var(--color-text-light, #94a3b8);
  margin-top: 3px;
}

.sample-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  background: var(--card-bg);
}

.sample-card:hover {
  background: var(--color-light);
}

.sample-original {
  font-size: 0.82rem;
  color: #b02a37;
  background: rgba(220, 53, 69, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  word-break: break-all;
}
</style>

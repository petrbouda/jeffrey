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
  <div class="run-bar" :class="{ failed: isFailed }">
    <div class="stages">
      <div
        v-for="stage in stages"
        :key="stage"
        class="stage"
        :class="{ done: stageIndex(stage) < currentIndex, active: stage === progress?.status }"
      >
        <span class="dot"></span>
        <span class="label">{{ stageLabel(stage) }}</span>
      </div>
    </div>

    <div class="message">
      <i :class="isFailed ? 'bi bi-x-octagon-fill' : 'bi bi-hourglass-split'"></i>
      <span>{{
        isFailed ? (progress?.errorMessage ?? progress?.message) : progress?.message
      }}</span>
    </div>

    <button v-if="isRunning" type="button" class="cancel" @click="$emit('cancel')">Cancel</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { AdvisorProgress } from '@/services/api/model/Advisor';

const props = defineProps<{
  progress: AdvisorProgress | null;
  stages: string[];
  isRunning: boolean;
}>();

defineEmits<{ cancel: [] }>();

const isFailed = computed(() => props.progress?.status === 'FAILED');

const stageIndex = (stage: string): number => props.stages.indexOf(stage);

/**
 * Once a run completes, every stage is behind it — so the cursor sits past the end rather than on the
 * last stage, which would render "verifying" as still in progress.
 */
const currentIndex = computed(() => {
  const status = props.progress?.status;
  if (status == null) {
    return -1;
  }
  if (status === 'COMPLETED') {
    return props.stages.length;
  }
  return stageIndex(status);
});

const stageLabel = (stage: string): string =>
  stage
    .toLowerCase()
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
</script>

<style scoped>
.run-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: var(--color-light);
}

.run-bar.failed {
  background: color-mix(in srgb, var(--color-danger) 8%, var(--color-white));
  border-color: var(--color-danger);
}

.stages {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.stage {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.stage .dot {
  width: 9px;
  height: 9px;
  border-radius: var(--radius-circle);
  background: var(--color-border);
}

.stage.done .dot {
  background: var(--color-success);
}

.stage.active .dot {
  background: var(--color-primary);
}

.stage.active .label {
  color: var(--color-text);
  font-weight: 600;
}

.message {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.cancel {
  margin-left: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-white);
  padding: 0.25rem 0.75rem;
  font-size: 0.8rem;
  cursor: pointer;
}
</style>

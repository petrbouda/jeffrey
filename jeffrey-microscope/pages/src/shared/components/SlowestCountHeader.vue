<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <div class="slowest-count-header">
    <div class="count-stats">
      <template v-if="shown < effectiveTotal">
        <Badge key-label="Showing" :value="shown" variant="primary" size="m" icon="bi bi-eye-fill" />
        <Badge
          key-label="Total"
          :value="effectiveTotal"
          variant="light"
          size="m"
          icon="bi bi-collection"
          class="total-badge"
        />
      </template>
      <Badge
        v-else
        key-label="Showing all"
        :value="effectiveTotal"
        variant="primary"
        size="m"
        icon="bi bi-check2-circle"
      />
    </div>
    <span class="count-note"><i class="bi bi-sort-down"></i>{{ note }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';

const props = withDefaults(
  defineProps<{
    shown: number;
    total: number;
    note?: string;
  }>(),
  {
    note: 'sorted by duration'
  }
);

// Guard against a total that is somehow smaller than what is displayed, so the
// header never reads "Showing 20 of 18".
const effectiveTotal = computed(() => Math.max(props.total, props.shown));
</script>

<style scoped>
.slowest-count-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.625rem 1rem;
  border-bottom: 1px solid var(--color-border-light);
}

.count-stats {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* The neutral "Total" badge ships with an almost-invisible border; give it a clearly visible one. */
:deep(.total-badge) {
  border-color: var(--color-grey-border) !important;
}

.count-note {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.count-note i {
  font-size: 0.9em;
  color: var(--color-primary);
}
</style>

<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<!--
  One quiet line in the project tab row; clicking it opens the full breakdown. Replaces the stat
  cards that used to sit above the instance list.
-->
<template>
  <div ref="root" class="stats-summary">
    <button
      ref="trigger"
      type="button"
      class="summary-trigger"
      :class="{ open }"
      :aria-expanded="open"
      :aria-controls="POPOVER_ID"
      @click="open = !open"
    >
      <span class="summary-text">
        <strong>{{ stats.total }}</strong> {{ stats.total === 1 ? 'instance' : 'instances' }}
        <template v-if="storage.kind === 'measured'">
          · <strong>{{ storage.value }} {{ storage.unit }}</strong>
        </template>
      </span>
      <i class="bi bi-chevron-down summary-chevron"></i>
    </button>

    <div
      v-if="open"
      :id="POPOVER_ID"
      class="stats-popover"
      role="dialog"
      aria-label="Instance statistics"
    >
      <section class="popover-section">
        <div class="popover-title"><i class="bi bi-box"></i>Instances</div>
        <div v-for="row in rows" :key="row.label" class="popover-row">
          <span>{{ row.label }}</span>
          <strong>{{ row.value }}</strong>
        </div>
      </section>

      <section v-if="storage.kind !== 'loading'" class="popover-section storage-section">
        <div class="popover-title storage-title"><i class="bi bi-hdd"></i>Storage</div>
        <ErrorState v-if="storage.kind === 'error'" message="Repository size could not be loaded" />
        <p v-else-if="storage.kind === 'unmeasured'" class="storage-caption">
          Not measured here — the Jeffrey Hub's Storage page shows what each project occupies
        </p>
        <template v-else>
          <div class="storage-figure">
            <span class="storage-value">{{ storage.value }}</span>
            <span class="storage-unit">{{ storage.unit }}</span>
          </div>
          <p class="storage-caption">
            across every session of this project, as of the hub's last retention pass
          </p>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import ErrorState from '@shared/components/ErrorState.vue';
import FormattingService from '@shared/services/FormattingService';
import type { InstanceStats, StorageFigure } from './instanceStats';

const POPOVER_ID = 'instance-stats-popover';
const ESCAPE_KEY = 'Escape';

interface StatRow {
  label: string;
  value: string | number;
}

const props = defineProps<{
  stats: InstanceStats;
  storage: StorageFigure;
}>();

const open = ref(false);
const root = ref<HTMLElement | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);

const rows = computed<StatRow[]>(() => {
  const stats = props.stats;
  const firstInstance =
    stats.oldestCreatedAt === null
      ? '—'
      : FormattingService.formatRelativeTime(stats.oldestCreatedAt);

  return [
    { label: 'Total instances', value: stats.total },
    { label: 'First instance', value: firstInstance }
  ];
});

function onDocumentClick(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) {
    open.value = false;
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === ESCAPE_KEY) {
    open.value = false;
    trigger.value?.focus();
  }
}

function removeListeners() {
  document.removeEventListener('click', onDocumentClick, true);
  document.removeEventListener('keydown', onDocumentKeydown);
}

// Listen only while open: a closed summary costs the page nothing.
watch(open, isOpen => {
  if (isOpen) {
    document.addEventListener('click', onDocumentClick, true);
    document.addEventListener('keydown', onDocumentKeydown);
  } else {
    removeListeners();
  }
});

onBeforeUnmount(removeListeners);
</script>

<style scoped>
.stats-summary {
  position: relative;
}

.summary-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 12px;
  font-size: 0.8rem;
  color: var(--color-text);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast);
}

.summary-trigger:hover,
.summary-trigger.open {
  background: var(--color-primary-lighter);
  border-color: var(--color-primary-border-light);
}

.summary-text strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.summary-chevron {
  font-size: 0.7rem;
  color: var(--color-primary);
  transition: transform var(--transition-fast);
}

.summary-trigger.open .summary-chevron {
  transform: rotate(180deg);
}

.stats-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: var(--z-dropdown);
  overflow: hidden;
}

.popover-section {
  padding: 14px 18px 10px;
}

.popover-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.popover-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.popover-row strong {
  color: var(--color-dark);
  font-weight: var(--font-weight-semibold);
}

.storage-section {
  padding-bottom: 14px;
  border-top: 1px solid var(--color-border);
  background: var(--color-light);
}

.storage-title {
  color: var(--color-success-hover);
}

.storage-figure {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.storage-value {
  font-size: var(--font-size-xxl);
  font-weight: var(--font-weight-semibold);
  line-height: 1.1;
  color: var(--color-dark);
  font-variant-numeric: tabular-nums;
}

.storage-unit {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
}

.storage-caption {
  margin: 4px 0 0;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
</style>

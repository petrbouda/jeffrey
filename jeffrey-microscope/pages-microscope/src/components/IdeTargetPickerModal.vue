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
  <GenericModal
    modal-id="ideTargetPickerModal"
    :show="store.show.value"
    title="Select IDE Target"
    icon="bi bi-window-stack"
    size="lg"
    :show-footer="true"
    @update:show="onShowChange"
  >
    <p class="picker-intro">Choose which open IDE window to use for this profile. Your choice is remembered.</p>

    <div v-for="instance in store.instances.value" :key="instance.port" class="ide-group">
      <div class="ide-group-header">
        <i class="bi bi-window"></i>
        <span class="ide-name">{{ instance.ideName }}</span>
        <Badge variant="secondary" size="xs" :value="':' + instance.port" />
        <span class="ide-pid">PID {{ instance.pid }}</span>
      </div>
      <label
        v-for="project in instance.projects"
        :key="project.id"
        class="ide-row"
        :class="{ 'is-selected': selectedKey === rowKey(instance.port, project.id) }"
      >
        <input
          type="radio"
          name="ideTarget"
          :value="rowKey(instance.port, project.id)"
          v-model="selectedKey"
        />
        <div class="ide-row-main">
          <div class="ide-row-title">
            <strong>{{ project.name }}</strong>
            <Badge v-if="project.hasClass" variant="green" size="xs" value="has class" />
            <Badge v-if="project.focused" variant="primary" size="xs" value="focused" />
          </div>
          <div class="ide-row-meta">
            <span v-if="project.vcsBranch" class="ide-branch">{{ project.vcsBranch }}</span>
            <span v-if="project.basePath" class="ide-path">{{ project.basePath }}</span>
          </div>
        </div>
      </label>
    </div>

    <EmptyState v-if="store.instances.value.length === 0" message="No running IDE detected." />

    <template #footer>
      <button type="button" class="btn btn-outline-secondary btn-sm" @click="onCancel">Cancel</button>
      <button type="button" class="btn btn-primary btn-sm" :disabled="!selectedKey" @click="onConnect">
        Connect
      </button>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import GenericModal from '@shared/components/GenericModal.vue';
import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ideTargetPickerStore from '@/stores/ideTargetPickerStore';

const store = ideTargetPickerStore;
const selectedKey = ref<string>('');

const KEY_SEPARATOR = '::';

function rowKey(port: number, projectId: string): string {
  return `${port}${KEY_SEPARATOR}${projectId}`;
}

function initSelection(): void {
  const flat = store.instances.value.flatMap((instance) =>
    instance.projects.map((project) => ({ port: instance.port, project }))
  );
  if (flat.length === 0) {
    selectedKey.value = '';
    return;
  }
  const cached = flat.find((entry) => entry.project.id === store.selectedProjectId.value);
  const match = flat.find((entry) => entry.project.hasClass);
  const chosen = cached ?? match ?? flat[0];
  selectedKey.value = rowKey(chosen.port, chosen.project.id);
}

watch(
  () => store.show.value,
  (visible) => {
    if (visible) {
      initSelection();
    }
  }
);

function onConnect(): void {
  if (!selectedKey.value) {
    return;
  }
  const [port, projectId] = selectedKey.value.split(KEY_SEPARATOR);
  store.choose({ port: parseInt(port, 10), projectId });
}

function onCancel(): void {
  store.cancel();
}

function onShowChange(value: boolean): void {
  if (!value) {
    store.cancel();
  }
}
</script>

<style scoped>
.picker-intro {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-bottom: var(--spacing-3);
}

.ide-group {
  margin-bottom: var(--spacing-3);
}

.ide-group-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base) var(--radius-base) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-dark);
}

.ide-group-header .ide-name {
  font-weight: var(--font-weight-semibold);
}

.ide-group-header .ide-pid {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.ide-row {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-3);
  padding: var(--spacing-3);
  border: 1px solid var(--color-border);
  border-top: none;
  cursor: pointer;
  transition: background var(--transition-base);
}

.ide-row:last-child {
  border-radius: 0 0 var(--radius-base) var(--radius-base);
}

.ide-row:hover {
  background: var(--color-bg-hover);
}

.ide-row.is-selected {
  background: var(--color-primary-light);
  box-shadow: inset 2px 0 0 0 var(--color-primary);
}

.ide-row input {
  margin-top: 3px;
  accent-color: var(--color-primary);
}

.ide-row-main {
  flex: 1;
  min-width: 0;
}

.ide-row-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.ide-row-title strong {
  color: var(--color-dark);
  font-size: var(--font-size-base);
}

.ide-row-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-top: var(--spacing-1);
  flex-wrap: wrap;
}

.ide-branch,
.ide-path {
  font-family: var(--font-mono, monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}
</style>

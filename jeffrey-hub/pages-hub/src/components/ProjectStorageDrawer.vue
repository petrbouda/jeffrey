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
  <div class="drawer-backdrop" @click="emit('close')"></div>
  <aside class="drawer" role="dialog" aria-modal="true" :aria-label="displayName">
    <div class="drawer-head">
      <span class="status-dot" :class="{ inactive: !active }"></span>
      <span class="project-name">{{ displayName }}</span>
      <span class="workspace-tag">{{ project.workspaceName }}</span>
      <button class="close-btn" aria-label="Close" @click="emit('close')">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <div class="stats-row">
      <div class="stat">
        <div class="stat-key">Size</div>
        <div class="stat-value">{{ formatBytes(project.totalSizeBytes) }}</div>
      </div>
      <div class="stat">
        <div class="stat-key">Files</div>
        <div class="stat-value">{{ project.totalFiles.toLocaleString() }}</div>
      </div>
      <div class="stat">
        <div class="stat-key">Of workspace</div>
        <div class="stat-value">{{ workspaceShare }}</div>
      </div>
    </div>

    <div class="drawer-section">
      <div class="section-label">
        Composition · {{ project.fileTypes.length }} of {{ STORAGE_FILE_TYPE_COUNT }} file types
      </div>
      <div class="composition-bar">
        <span
            v-for="usage in groups"
            :key="usage.group.key"
            :style="{ width: barWidth(usage.sizeBytes), background: usage.group.color }"
        ></span>
      </div>
      <template v-for="usage in groups" :key="usage.group.key">
        <div class="group-row">
          <span class="group-swatch" :style="{ background: usage.group.color }"></span>
          <span>{{ usage.group.label }}</span>
          <span class="group-size">{{ formatBytes(usage.sizeBytes) }}</span>
        </div>
        <div v-for="fileType in usage.fileTypes" :key="fileType.type" class="type-row">
          <span class="type-label">{{ meta(fileType.type).label }}</span>
          <span v-if="meta(fileType.type).extension" class="type-ext">{{ meta(fileType.type).extension }}</span>
          <span class="type-size">{{ formatBytes(fileType.sizeBytes) }}</span>
          <span class="type-count">{{ fileType.fileCount }} {{ fileType.fileCount === 1 ? 'file' : 'files' }}</span>
        </div>
      </template>
      <div v-if="groups.length === 0" class="empty-note">No files stored yet</div>
      <div v-if="absentLabels.length > 0" class="absent-note">
        <b>Not stored:</b> {{ absentLabels.join(' · ') }}
      </div>
    </div>

    <div v-if="project.largestFiles.length > 0" class="drawer-section">
      <div class="section-label">Largest files</div>
      <div v-for="file in project.largestFiles" :key="file.fileName" class="file-row">
        <span class="file-name">{{ file.fileName }}</span>
        <span class="file-size">{{ formatBytes(file.sizeBytes) }}</span>
      </div>
    </div>

    <div class="drawer-section">
      <div v-if="project.lastActivityTimeMillis > 0" class="meta-row">
        <span>Last activity</span>
        <b>{{ formatRelativeTime(project.lastActivityTimeMillis) }}</b>
      </div>
      <div class="meta-row">
        <span>Status</span>
        <b>{{ active ? 'Active' : 'Inactive' }}</b>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import type { ProjectStorage } from '@/services/api/model/StorageOverview';
import {
  absentFileTypeLabels,
  fileTypeMeta,
  groupUsages,
  STORAGE_FILE_TYPE_COUNT
} from '@/services/storage/StorageFileTypes';

const props = defineProps<{
  project: ProjectStorage;
  active: boolean;
  workspaceShare: string;
}>();

const emit = defineEmits<{
  close: [];
}>();

const formatBytes = (bytes: number) => FormattingService.formatBytesShort(bytes);
const formatRelativeTime = (millis: number) => FormattingService.formatRelativeTime(millis);
const meta = fileTypeMeta;

const displayName = computed(() => {
  return props.project.projectLabel?.trim() ? props.project.projectLabel : props.project.projectName;
});

const groups = computed(() => groupUsages(props.project.fileTypes));
const absentLabels = computed(() => absentFileTypeLabels(props.project.fileTypes));

const barWidth = (sizeBytes: number) => {
  if (props.project.totalSizeBytes === 0) {
    return '0%';
  }
  return (sizeBytes / props.project.totalSizeBytes * 100) + '%';
};
</script>

<style scoped>
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(26, 26, 46, 0.28);
  z-index: 1040;
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 92vw;
  background: white;
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
  z-index: 1050;
  overflow-y: auto;
}

.drawer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-grey-bg);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-emerald);
  flex-shrink: 0;
}

.status-dot.inactive {
  background: var(--color-slate-light);
}

.project-name {
  font-size: 0.98rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.workspace-tag {
  font-size: 0.69rem;
  font-weight: 600;
  color: var(--color-slate-muted);
  background: var(--color-grey-bg);
  padding: 2px 8px;
  border-radius: 9px;
}

.close-btn {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--color-slate-muted);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.close-btn:hover {
  color: var(--color-heading-dark);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid var(--color-grey-bg);
}

.stat {
  padding: 12px 18px;
  border-right: 1px solid var(--color-grey-bg);
}

.stat:last-child {
  border-right: none;
}

.stat-key {
  font-size: 0.66rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-light);
}

.stat-value {
  font-size: 1.02rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  font-variant-numeric: tabular-nums;
}

.drawer-section {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-grey-bg);
}

.drawer-section:last-child {
  border-bottom: none;
}

.section-label {
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-light);
  margin-bottom: 9px;
}

.composition-bar {
  display: flex;
  gap: 2px;
  height: 13px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--color-grey-bg);
  margin-bottom: 11px;
}

.composition-bar span {
  display: block;
  height: 100%;
}

.group-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 3px;
  font-size: 0.79rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.group-swatch {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  flex-shrink: 0;
}

.group-size {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.type-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 3px 0 3px 17px;
  font-size: 0.76rem;
  color: var(--color-slate-text);
}

.type-ext {
  font-family: var(--bs-font-monospace, monospace);
  font-size: 0.66rem;
  color: var(--color-slate-muted);
  background: var(--color-grey-bg);
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.type-size {
  margin-left: auto;
  font-weight: 600;
  color: var(--color-heading-dark);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.type-count {
  color: var(--color-text-light);
  font-size: 0.72rem;
  min-width: 52px;
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.empty-note {
  padding: 6px 0;
  font-size: 0.78rem;
  color: var(--color-slate-light);
}

.absent-note {
  margin-top: 11px;
  padding-top: 9px;
  border-top: 1px dashed var(--color-border);
  font-size: 0.72rem;
  color: var(--color-text-light);
  line-height: 1.6;
}

.absent-note b {
  color: var(--color-slate-muted);
  font-weight: 600;
}

.file-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 5px 0;
  font-size: 0.78rem;
}

.file-name {
  font-family: var(--bs-font-monospace, monospace);
  font-size: 0.74rem;
  color: var(--color-slate-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  margin-left: auto;
  font-weight: 600;
  color: var(--color-heading-dark);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.76rem;
  color: var(--color-slate-muted);
  padding: 4px 0;
}

.meta-row b {
  color: var(--color-heading-dark);
  font-weight: 600;
}
</style>

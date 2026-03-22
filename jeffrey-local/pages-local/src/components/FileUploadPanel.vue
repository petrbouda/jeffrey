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
  <div class="upload-panel">
    <!-- Collapsible header -->
    <div class="upload-header" @click="expanded = !expanded">
      <div class="upload-header-left">
        <i class="bi bi-cloud-upload upload-header-icon"></i>
        <span class="upload-header-title">Upload Recordings</span>
        <span v-if="files.length > 0" class="upload-file-count">{{ files.length }}</span>
      </div>
      <div class="upload-header-right">
        <span class="upload-hint">{{ formatHint }}</span>
        <i class="bi upload-chevron" :class="expanded ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
      </div>
    </div>

    <!-- Expanded body -->
    <div v-if="expanded" class="upload-body">
      <!-- Group selector -->
      <div class="upload-group-row">
        <label class="upload-group-label">Target Group</label>
        <select class="upload-group-select" :value="selectedGroupId" @change="emit('update:selectedGroupId', ($event.target as HTMLSelectElement).value || null)">
          <option :value="''">No group</option>
          <option v-for="group in groups" :key="group.id" :value="group.id">
            {{ group.name }}
          </option>
        </select>
      </div>

      <!-- Dropzone (when no files) -->
      <div
          class="upload-dropzone"
          :class="{ 'drag-over': dragActive, 'has-files': files.length > 0 }"
          @dragover.prevent="dragActive = true"
          @dragleave.prevent="dragActive = false"
          @drop.prevent="handleDrop"
      >
        <div v-if="files.length === 0" class="dropzone-empty" @click="triggerFileInput">
          <input
              ref="fileInputRef"
              type="file"
              :accept="acceptedFormats"
              multiple
              class="file-input-hidden"
              @change="handleFileInput"
          >
          <i class="bi bi-cloud-upload dropzone-icon"></i>
          <span class="dropzone-text">Drop files here or click to browse</span>
        </div>

        <!-- File list (when files selected) -->
        <div v-else class="upload-file-section">
          <div class="upload-file-toolbar">
            <span class="upload-file-title">
              <i class="bi bi-files"></i>
              Selected Files ({{ files.length }})
            </span>
            <div class="upload-file-actions">
              <button class="upload-btn-add" @click="triggerFileInput" :disabled="isUploading" title="Add more files">
                <i class="bi bi-plus-lg"></i>
                Add
              </button>
              <input
                  ref="fileInputRef"
                  type="file"
                  :accept="acceptedFormats"
                  multiple
                  class="file-input-hidden"
                  @change="handleFileInput"
              >
              <button class="upload-btn-clear" @click="emit('clear')" :disabled="isUploading">
                Clear
              </button>
              <button class="upload-btn-upload" @click="emit('upload')" :disabled="isUploading">
                <i class="bi bi-cloud-upload"></i>
                Upload All
              </button>
            </div>
          </div>

          <div class="upload-file-list">
            <div v-for="(file, index) in files" :key="file.name + index" class="upload-file-item">
              <i :class="getFileIcon(file.name)" class="upload-file-icon"></i>
              <span class="upload-file-name">{{ file.name }}</span>
              <span class="upload-file-size">{{ FormattingService.formatBytes(file.size) }}</span>

              <!-- Progress -->
              <template v-if="progress[file.name]">
                <div class="upload-progress">
                  <div class="upload-progress-track">
                    <div
                        class="upload-progress-fill"
                        :class="progress[file.name].status"
                        :style="{ width: Math.min(progress[file.name].progress, 100) + '%' }"
                    ></div>
                  </div>
                </div>
                <span class="upload-status" :class="'status-' + progress[file.name].status">
                  <template v-if="progress[file.name].status === 'complete'">
                    <i class="bi bi-check-circle"></i>
                  </template>
                  <template v-else-if="progress[file.name].status === 'error'">
                    <i class="bi bi-exclamation-circle"></i>
                  </template>
                  <template v-else-if="progress[file.name].status === 'uploading'">
                    {{ progress[file.name].progress }}%
                  </template>
                  <template v-else>
                    Pending
                  </template>
                </span>
              </template>

              <!-- Remove button -->
              <button v-else class="upload-file-remove" @click="emit('remove', index)" title="Remove">
                <i class="bi bi-x"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import FormattingService from '@/services/FormattingService';

interface UploadProgressEntry {
  progress: number;
  status: 'pending' | 'uploading' | 'complete' | 'error';
}

interface GroupOption {
  id: string;
  name: string;
}

interface Props {
  files: File[];
  progress: Record<string, UploadProgressEntry>;
  groups?: GroupOption[];
  selectedGroupId?: string | null;
  acceptedFormats?: string;
  formatHint?: string;
  isUploading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  groups: () => [],
  selectedGroupId: null,
  acceptedFormats: '.jfr,.lz4,.hprof,.gz',
  formatHint: '.jfr  .lz4  .hprof  .hprof.gz',
  isUploading: false,
});

const expanded = ref(true);
const dragActive = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

const emit = defineEmits<{
  (e: 'update:files', files: File[]): void;
  (e: 'update:selectedGroupId', id: string | null): void;
  (e: 'upload'): void;
  (e: 'clear'): void;
  (e: 'remove', index: number): void;
}>();

const triggerFileInput = () => {
  fileInputRef.value?.click();
};

const handleFileInput = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    emit('update:files', [...props.files, ...Array.from(input.files)]);
    if (!expanded.value) {
      expanded.value = true;
    }
  }
  input.value = '';
};

const handleDrop = (event: DragEvent) => {
  dragActive.value = false;
  const files = event.dataTransfer?.files;
  if (files && files.length > 0) {
    emit('update:files', [...props.files, ...Array.from(files)]);
    if (!expanded.value) {
      expanded.value = true;
    }
  }
};

const getFileIcon = (filename: string): string => {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.hprof') || lower.endsWith('.hprof.gz')) {
    return 'bi bi-database';
  }
  return 'bi bi-file-earmark-binary';
};
</script>

<style scoped>
/* Panel container */
.upload-panel {
  border-bottom: 1px solid rgba(94, 100, 255, 0.06);
}

/* Header */
.upload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  cursor: pointer;
  transition: background 0.15s ease;
  border-radius: 4px;
}

.upload-header:hover {
  background: rgba(94, 100, 255, 0.03);
}

.upload-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-header-icon {
  font-size: 0.95rem;
  color: #5e64ff;
}

.upload-header-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #374151;
}

.upload-file-count {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  padding: 0 7px;
  border-radius: 10px;
  font-size: 0.65rem;
  font-weight: 600;
}

.upload-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-hint {
  font-size: 0.7rem;
  color: #9ca3af;
}

.upload-chevron {
  font-size: 0.65rem;
  color: #9ca3af;
  transition: transform 0.15s ease;
}

/* Body */
.upload-body {
  padding-bottom: 12px;
  animation: expandIn 0.2s ease-out;
}

@keyframes expandIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Group selector */
.upload-group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.upload-group-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6b7280;
  white-space: nowrap;
}

.upload-group-select {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 4px 24px 4px 10px;
  font-size: 0.75rem;
  height: 30px;
  background: #f9fafb;
  cursor: pointer;
  transition: border-color 0.15s ease;
  appearance: auto;
  max-width: 220px;
}

.upload-group-select:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.1);
  outline: none;
}

/* Dropzone */
.file-input-hidden {
  display: none;
}

.upload-dropzone {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.03), rgba(118, 75, 162, 0.03));
  border: 1.5px dashed rgba(94, 100, 255, 0.25);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.upload-dropzone.drag-over {
  border-color: #5e64ff;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.1), rgba(118, 75, 162, 0.1));
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.12);
}

.upload-dropzone.has-files {
  border-style: solid;
  border-color: rgba(94, 100, 255, 0.12);
  background: transparent;
}

.dropzone-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.dropzone-empty:hover {
  background: rgba(94, 100, 255, 0.04);
  border-radius: 6px;
}

.dropzone-icon {
  font-size: 1.1rem;
  color: #5e64ff;
  opacity: 0.6;
}

.dropzone-text {
  font-size: 0.8rem;
  font-weight: 500;
  color: #6b7280;
}

/* File section (when files are selected) */
.upload-file-section {
  padding: 10px 12px;
}

.upload-file-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.upload-file-title {
  font-size: 0.78rem;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 5px;
}

.upload-file-title i {
  color: #5e64ff;
}

.upload-file-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.upload-btn-add {
  background: transparent;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 3px 10px;
  border-radius: 5px;
  font-size: 0.7rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 3px;
  transition: all 0.15s ease;
}

.upload-btn-add:hover {
  border-color: #5e64ff;
  color: #5e64ff;
}

.upload-btn-clear {
  background: transparent;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  padding: 3px 10px;
  border-radius: 5px;
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.upload-btn-clear:hover {
  border-color: #dc2626;
  color: #dc2626;
}

.upload-btn-upload {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  border: none;
  padding: 4px 12px;
  border-radius: 5px;
  font-size: 0.7rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.upload-btn-upload:hover {
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.35);
}

.upload-btn-upload:disabled,
.upload-btn-add:disabled,
.upload-btn-clear:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* File list */
.upload-file-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.upload-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.15s ease;
}

.upload-file-item:hover {
  background: #f0f3ff;
}

.upload-file-icon {
  font-size: 0.85rem;
  color: #5e64ff;
  flex-shrink: 0;
}

.upload-file-name {
  font-size: 0.78rem;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.upload-file-size {
  font-size: 0.7rem;
  color: #9ca3af;
  flex-shrink: 0;
}

.upload-file-remove {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 0.85rem;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.upload-file-remove:hover {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.08);
}

/* Progress */
.upload-progress {
  width: 80px;
  flex-shrink: 0;
}

.upload-progress-track {
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.upload-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
  background: #5e64ff;
}

.upload-progress-fill.uploading {
  background: linear-gradient(90deg, #5e64ff, #764ba2);
  animation: progressPulse 1.5s ease-in-out infinite;
}

.upload-progress-fill.complete {
  background: #00d27a;
}

.upload-progress-fill.error {
  background: #dc2626;
}

@keyframes progressPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.upload-status {
  font-size: 0.65rem;
  font-weight: 500;
  flex-shrink: 0;
  width: 45px;
  text-align: right;
}

.upload-status.status-pending {
  color: #9ca3af;
}

.upload-status.status-uploading {
  color: #5e64ff;
}

.upload-status.status-complete {
  color: #00d27a;
}

.upload-status.status-error {
  color: #dc2626;
}
</style>

<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <!-- Floating Button (visible when panel is closed or minimized) -->
  <AssistantMinimizedButton
      v-if="!isOpen || !isExpanded"
      :icon="minimizedIcon"
      :status="minimizedStatus"
      :is-spinning="isSpinningIcon"
      :order="2"
      @click="handleButtonClick"
      title="Quick JFR Analysis"
  />

  <!-- Expanded State - Panel -->
  <AssistantPanel
      :is-open="isOpen"
      :is-expanded="isExpanded"
      width="380px"
      header-gradient="linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
      :show-backdrop="true"
      @close="$emit('close')"
  >
    <template #header-icon>
      <i class="bi bi-lightning-charge-fill text-warning me-2"></i>
    </template>

    <template #header-title>
      Quick Analysis
    </template>

    <template #header-actions>
      <button class="btn-icon" @click="$emit('minimize')" title="Minimize">
        <i class="bi bi-dash-lg"></i>
      </button>
      <button
          v-if="!isProcessing"
          class="btn-icon"
          @click="$emit('close')"
          title="Close"
      >
        <i class="bi bi-x-lg"></i>
      </button>
    </template>

    <template #body>
      <div class="quick-analysis-body">
        <!-- Dropzone -->
        <div
            class="dropzone"
            :class="{ 'drag-over': isDragOver, 'processing': isProcessing, 'has-file': selectedFile }"
            @click="triggerFileInput"
            @drop.prevent="handleFileDrop"
            @dragenter.prevent="isDragOver = true"
            @dragover.prevent
            @dragleave.prevent="isDragOver = false"
        >
          <input
              ref="fileInputRef"
              type="file"
              accept=".jfr"
              class="file-input-hidden"
              @change="handleFileSelect"
          >

          <!-- Default state -->
          <template v-if="!isProcessing && !selectedFile">
            <i class="bi bi-cloud-upload dropzone-icon"></i>
            <span class="dropzone-text">Drop your JFR file here</span>
            <span class="dropzone-subtext">or click to select</span>
          </template>

          <!-- File selected state -->
          <template v-else-if="!isProcessing && selectedFile">
            <i class="bi bi-file-earmark-binary file-icon"></i>
            <span class="file-name">{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatBytes(selectedFile.size) }}</span>
            <button class="btn-start" @click.stop="$emit('start-analysis')">
              <i class="bi bi-play-fill me-1"></i>
              Start Analysis
            </button>
          </template>

          <!-- Processing state -->
          <template v-else>
            <div class="processing-content">
              <div class="spinner-container">
                <div class="spinner"></div>
              </div>
              <div class="processing-file">
                <span class="file-name">{{ selectedFile?.name }}</span>
              </div>
              <div class="processing-status">
                <span>{{ statusMessage }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- Error Message -->
        <div v-if="errorMessage" class="error-message">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          {{ errorMessage }}
        </div>

        <!-- Panel Hint -->
        <div class="panel-hint">
          <i class="bi bi-info-circle"></i>
          <span>Temporary profiles - cleared on restart</span>
        </div>

        <!-- Recent Analyses -->
        <div v-if="recentProfiles.length > 0" class="recent-analyses">
          <div class="recent-header">
            <span class="recent-title">Recent Analyses</span>
            <span class="recent-count">{{ recentProfiles.length }}</span>
          </div>
          <div class="recent-list">
            <div
                v-for="profile in recentProfiles"
                :key="profile.id"
                class="recent-item"
                @click="$emit('open-profile', profile.id)"
            >
              <div class="item-info">
                <i class="bi bi-graph-up item-icon"></i>
                <div class="item-details">
                  <span class="item-name">{{ profile.name }}</span>
                  <span class="item-meta">
                    {{ formatRelativeTime(profile.createdAt) }}
                    <span class="meta-divider">-</span>
                    {{ formatBytes(profile.sizeInBytes) }}
                  </span>
                </div>
              </div>
              <div class="item-actions">
                <button class="btn-open" title="Open profile">
                  <i class="bi bi-arrow-right"></i>
                </button>
                <button class="btn-delete" @click.stop="$emit('delete-profile', profile.id)" title="Delete profile">
                  <i class="bi bi-trash"></i>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state when no recent profiles -->
        <div v-else class="empty-state">
          <i class="bi bi-clock-history"></i>
          <span>No recent analyses</span>
        </div>
      </div>
    </template>
  </AssistantPanel>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import FormattingService from '@/services/FormattingService';
import QuickAnalysisProfile from '@/services/api/model/QuickAnalysisProfile';
import AssistantPanel from '@/components/assistants/AssistantPanel.vue';
import AssistantMinimizedButton from '@/components/assistants/AssistantMinimizedButton.vue';
import type { QuickAnalysisStatus } from '@/stores/assistants/quickAnalysisAssistantStore';

interface Props {
  isOpen: boolean;
  isExpanded: boolean;
  selectedFile: File | null;
  status: QuickAnalysisStatus;
  statusMessage: string;
  recentProfiles: QuickAnalysisProfile[];
  errorMessage: string | null;
  isProcessing: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits([
  'open',
  'close',
  'expand',
  'minimize',
  'set-selected-file',
  'start-analysis',
  'open-profile',
  'delete-profile'
]);

// Local refs
const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragOver = ref(false);

// Formatting
const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);
const formatRelativeTime = (dateString: string) => {
  const timestamp = new Date(dateString).getTime();
  return FormattingService.formatRelativeTime(timestamp);
};

// Minimized button computed properties
const minimizedIcon = computed(() => {
  switch (props.status) {
    case 'completed':
      return 'bi bi-check-lg';
    case 'failed':
      return 'bi bi-exclamation-lg';
    case 'parsing':
      return 'bi bi-gear-fill';
    default:
      return 'bi bi-lightning-charge-fill';
  }
});

const minimizedStatus = computed(() => {
  switch (props.status) {
    case 'completed':
      return 'completed';
    case 'failed':
      return 'failed';
    case 'parsing':
      return 'processing';
    default:
      return 'default';
  }
});

const isSpinningIcon = computed(() => props.status === 'parsing');

// File handling
const triggerFileInput = () => {
  if (!props.isProcessing) {
    fileInputRef.value?.click();
  }
};

const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('set-selected-file', file);
  }
  // Reset input so same file can be selected again
  input.value = '';
};

const handleFileDrop = (event: DragEvent) => {
  isDragOver.value = false;
  const file = event.dataTransfer?.files[0];
  if (file) {
    emit('set-selected-file', file);
    // Start analysis immediately when file is dropped
    emit('start-analysis');
  }
};

const handleButtonClick = () => {
  if (!props.isOpen) {
    emit('open');
  } else {
    emit('expand');
  }
};
</script>

<style scoped>
/* Quick Analysis Body */
.quick-analysis-body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100%;
}

/* Dropzone */
.dropzone {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border: 2px dashed rgba(102, 126, 234, 0.4);
  border-radius: 12px;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-height: 140px;
  justify-content: center;
}

.dropzone:hover:not(.processing) {
  border-color: rgba(102, 126, 234, 0.7);
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
}

.dropzone.drag-over {
  border-color: #667eea;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.15) 0%, rgba(118, 75, 162, 0.15) 100%);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
}

.dropzone.processing {
  cursor: default;
  border-style: solid;
  border-color: rgba(102, 126, 234, 0.4);
}

.dropzone.has-file {
  border-style: solid;
  border-color: rgba(102, 126, 234, 0.5);
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
}

.file-input-hidden {
  display: none;
}

.dropzone-icon {
  font-size: 2.5rem;
  color: #667eea;
}

.dropzone-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: #495057;
}

.dropzone-subtext {
  font-size: 0.75rem;
  color: #6c757d;
}

.file-icon {
  font-size: 2rem;
  color: #667eea;
}

.file-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: #495057;
  word-break: break-all;
  text-align: center;
}

.file-size {
  font-size: 0.75rem;
  color: #6c757d;
}

.btn-start {
  margin-top: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
}

.btn-start:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-start:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Processing Content */
.processing-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 8px 0;
}

.spinner-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(102, 126, 234, 0.2);
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spinner-rotate 0.8s linear infinite;
}

@keyframes spinner-rotate {
  to { transform: rotate(360deg); }
}

.processing-file {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #495057;
}

.processing-file .file-name {
  font-size: 0.85rem;
  font-weight: 500;
}

.processing-status {
  font-size: 0.8rem;
  color: #6c757d;
}

/* Error Message */
.error-message {
  padding: 0.75rem;
  background: #fff5f5;
  border: 1px solid #fed7d7;
  border-radius: 8px;
  color: #c53030;
  font-size: 0.8rem;
}

/* Panel Hint */
.panel-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f8f9fa;
  border-radius: 8px;
  font-size: 0.75rem;
  color: #6c757d;
}

.panel-hint i {
  font-size: 0.85rem;
  color: #667eea;
}

/* Recent Analyses */
.recent-analyses {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.recent-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
}

.recent-count {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  flex: 1;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.recent-item:hover {
  background: #e9ecef;
  border-color: #dee2e6;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.item-icon {
  font-size: 1rem;
  color: #667eea;
}

.item-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.item-name {
  font-size: 0.8rem;
  font-weight: 500;
  color: #495057;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  font-size: 0.7rem;
  color: #6c757d;
}

.meta-divider {
  margin: 0 4px;
}

.item-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.recent-item:hover .item-actions {
  opacity: 1;
}

.btn-open,
.btn-delete {
  background: transparent;
  border: none;
  color: #6c757d;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  transition: all 0.2s ease;
}

.btn-open:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.btn-delete:hover {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}

/* Empty State */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #adb5bd;
  padding: 20px;
}

.empty-state i {
  font-size: 2rem;
}

.empty-state span {
  font-size: 0.8rem;
}
</style>

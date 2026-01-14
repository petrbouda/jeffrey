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
  <!-- Minimized State - Floating Button -->
  <AssistantMinimizedButton
      v-if="isOpen && !isExpanded"
      :icon="minimizedIcon"
      :progress="aggregateProgress"
      :badge-text="badgeText"
      :status="minimizedStatus"
      :is-spinning="isSpinningIcon"
      :order="1"
      @click="$emit('expand')"
      title="Click to expand downloads"
  />

  <!-- Expanded State - Panel -->
  <AssistantPanel
      :is-open="isOpen"
      :is-expanded="isExpanded"
      width="480px"
      :show-backdrop="false"
      @close="$emit('close')"
  >
    <template #header-icon>
      <i class="bi bi-cloud-download me-2"></i>
    </template>

    <template #header-title>
      Download Assistant
    </template>

    <template #header-actions>
      <button class="btn-icon" @click="$emit('minimize')" title="Minimize">
        <i class="bi bi-dash-lg"></i>
      </button>
      <button
          v-if="!hasActiveDownloads"
          class="btn-icon"
          @click="$emit('close')"
          title="Close all"
      >
        <i class="bi bi-x-lg"></i>
      </button>
    </template>

    <template #body>
      <div class="download-body">
        <!-- Download Cards -->
        <div
            v-for="download in downloads"
            :key="download.taskId"
            class="download-card"
            :class="{ 'download-complete': isDownloadComplete(download) }"
        >
          <!-- Download Header -->
          <div class="download-card-header">
            <div class="download-card-title">
              <i :class="getStatusIcon(download)" class="me-2"></i>
              <span class="session-name">{{ download.sessionName || download.taskId }}</span>
            </div>
            <div class="download-card-actions">
              <span class="download-percent">{{ download.percentComplete }}%</span>
              <button
                  v-if="isDownloadComplete(download)"
                  class="btn-close-card"
                  @click="$emit('close-download', download.taskId)"
                  title="Remove"
              >
                <i class="bi bi-x"></i>
              </button>
            </div>
          </div>

          <!-- Progress Bar -->
          <div class="progress download-progress">
            <div
                class="progress-bar progress-bar-striped"
                :class="[getProgressBarClass(download), { 'progress-bar-animated': !isDownloadComplete(download) }]"
                :style="{ width: download.percentComplete + '%' }"
            ></div>
          </div>

          <!-- File Details -->
          <div class="download-details">
            <!-- Active Downloads -->
            <div v-if="download.activeDownloads?.length" class="file-list">
              <div class="file-divider">
                <span class="file-divider-text text-primary">
                  <i class="bi bi-download me-1"></i>Downloading
                </span>
              </div>
              <div
                  v-for="file in download.activeDownloads"
                  :key="file.fileName"
                  class="file-item"
              >
                <i class="bi bi-file-earmark me-1 text-primary"></i>
                <span class="file-name">{{ file.fileName }}</span>
                <span class="file-meta">
                  <span class="file-size-muted">{{ formatBytes(file.fileSize) }}</span>
                  <span class="file-progress">{{ filePercent(file) }}%</span>
                </span>
              </div>
            </div>

            <!-- Completed Downloads -->
            <div v-if="download.completedDownloads?.length" class="file-list">
              <div class="file-divider">
                <span class="file-divider-text text-success">
                  <i class="bi bi-check-circle-fill me-1"></i>Completed
                </span>
              </div>
              <div
                  v-for="file in download.completedDownloads"
                  :key="file.fileName"
                  class="file-item"
              >
                <i class="bi bi-file-earmark-check me-1 text-success"></i>
                <span class="file-name">{{ file.fileName }}</span>
                <span class="file-size">{{ formatBytes(file.fileSize) }}</span>
              </div>
            </div>

            <!-- Status & Actions -->
            <div class="download-footer">
              <div class="download-status">
                <span :class="getStatusClass(download)">
                  <span v-if="!isDownloadComplete(download)" class="spinner-border spinner-border-sm me-1"></span>
                  {{ getStatusMessage(download) }}
                </span>
                <span v-if="download.status === DownloadTaskStatus.COMPLETED" class="recording-note">
                  New Recording created
                </span>
              </div>

              <!-- Cancel Button -->
              <button
                  v-if="canCancelDownload(download)"
                  class="btn btn-sm btn-outline-danger"
                  @click="$emit('cancel-download', download.taskId)"
              >
                <i class="bi bi-x-lg me-1"></i>
                Cancel
              </button>
            </div>
          </div>

          <!-- Error Message -->
          <div v-if="download.errorMessage" class="download-error">
            <i class="bi bi-exclamation-triangle-fill me-1"></i>
            {{ download.errorMessage }}
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="downloads.length === 0" class="empty-state">
          <i class="bi bi-cloud-download"></i>
          <p>No active downloads</p>
        </div>
      </div>
    </template>
  </AssistantPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@/services/FormattingService';
import DownloadProgress, { FileProgress } from '@/services/api/model/DownloadProgress';
import DownloadTaskStatus from '@/services/api/model/DownloadTaskStatus';
import AssistantPanel from '@/components/assistants/AssistantPanel.vue';
import AssistantMinimizedButton from '@/components/assistants/AssistantMinimizedButton.vue';

interface Props {
  isOpen: boolean;
  isExpanded: boolean;
  downloads: DownloadProgress[];
  aggregateProgress: number;
  aggregateStatus: DownloadTaskStatus | null;
  hasActiveDownloads: boolean;
}

const props = defineProps<Props>();
defineEmits(['expand', 'minimize', 'close', 'cancel-download', 'close-download']);

// Minimized button computed properties
const minimizedIcon = computed(() => {
  switch (props.aggregateStatus) {
    case DownloadTaskStatus.COMPLETED:
      return 'bi bi-check-lg';
    case DownloadTaskStatus.FAILED:
      return 'bi bi-exclamation-lg';
    case DownloadTaskStatus.CANCELLED:
      return 'bi bi-dash-lg';
    case DownloadTaskStatus.PROCESSING:
      return 'bi bi-gear-fill';
    default:
      return 'bi bi-cloud-download';
  }
});

const minimizedStatus = computed(() => {
  switch (props.aggregateStatus) {
    case DownloadTaskStatus.COMPLETED:
      return 'completed';
    case DownloadTaskStatus.FAILED:
      return 'failed';
    case DownloadTaskStatus.CANCELLED:
      return 'cancelled';
    case DownloadTaskStatus.PROCESSING:
      return 'processing';
    default:
      return 'downloading';
  }
});

const isSpinningIcon = computed(() => {
  return props.aggregateStatus === DownloadTaskStatus.PROCESSING;
});

const badgeText = computed(() => {
  if (props.downloads.length > 1) {
    return String(props.downloads.length);
  }
  return `${props.aggregateProgress}%`;
});

// Utility functions
const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);

const filePercent = (file: FileProgress): number => {
  if (!file.fileSize) return 0;
  return Math.round((file.downloadedBytes / file.fileSize) * 100);
};

const isDownloadComplete = (download: DownloadProgress): boolean => {
  return download.status === DownloadTaskStatus.COMPLETED ||
      download.status === DownloadTaskStatus.FAILED ||
      download.status === DownloadTaskStatus.CANCELLED;
};

const canCancelDownload = (download: DownloadProgress): boolean => {
  return download.status === DownloadTaskStatus.PENDING ||
      download.status === DownloadTaskStatus.DOWNLOADING;
};

const getProgressBarClass = (download: DownloadProgress): string => {
  switch (download.status) {
    case DownloadTaskStatus.COMPLETED:
      return 'bg-success';
    case DownloadTaskStatus.FAILED:
      return 'bg-danger';
    case DownloadTaskStatus.CANCELLED:
      return 'bg-warning';
    default:
      return 'bg-primary';
  }
};

const getStatusMessage = (download: DownloadProgress): string => {
  switch (download.status) {
    case DownloadTaskStatus.PENDING:
      return 'Preparing...';
    case DownloadTaskStatus.DOWNLOADING:
      return 'Downloading...';
    case DownloadTaskStatus.PROCESSING:
      return 'Processing...';
    case DownloadTaskStatus.COMPLETED:
      return 'Complete';
    case DownloadTaskStatus.FAILED:
      return 'Failed';
    case DownloadTaskStatus.CANCELLED:
      return 'Cancelled';
    default:
      return '';
  }
};

const getStatusIcon = (download: DownloadProgress): string => {
  switch (download.status) {
    case DownloadTaskStatus.COMPLETED:
      return 'bi bi-check-circle-fill text-success';
    case DownloadTaskStatus.FAILED:
      return 'bi bi-x-circle-fill text-danger';
    case DownloadTaskStatus.CANCELLED:
      return 'bi bi-dash-circle-fill text-warning';
    case DownloadTaskStatus.PROCESSING:
      return 'bi bi-gear-fill spin text-info';
    default:
      return 'bi bi-arrow-repeat spin text-primary';
  }
};

const getStatusClass = (download: DownloadProgress): string => {
  switch (download.status) {
    case DownloadTaskStatus.COMPLETED:
      return 'status-text text-success';
    case DownloadTaskStatus.FAILED:
      return 'status-text text-danger';
    case DownloadTaskStatus.CANCELLED:
      return 'status-text text-warning';
    default:
      return 'status-text text-muted';
  }
};
</script>

<style scoped>
/* Download Body - Padding for content */
.download-body {
  padding: 1rem;
}

/* Download Card */
.download-card {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  padding: 1rem;
  margin-bottom: 0.75rem;
  transition: all 0.2s ease;
}

.download-card:last-child {
  margin-bottom: 0;
}

.download-card.download-complete {
  background: linear-gradient(180deg, #f0fdf4 0%, #dcfce7 100%);
  border-color: #86efac;
}

.download-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.download-card-title {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 0.9rem;
}

.session-name {
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.download-card-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.download-percent {
  font-size: 0.85rem;
  font-weight: 600;
  color: #6c757d;
}

.btn-close-card {
  background: transparent;
  border: none;
  color: #6c757d;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.btn-close-card:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #dc3545;
}

/* Progress Bar */
.download-progress {
  height: 8px;
  border-radius: 4px;
  background-color: #e9ecef;
  margin-bottom: 0.75rem;
}

.download-progress .progress-bar {
  border-radius: 4px;
}

/* Download Details */
.download-details {
  font-size: 0.8rem;
}

.file-list {
  margin-bottom: 0.5rem;
}

/* File Divider */
.file-divider {
  display: flex;
  align-items: center;
  margin: 0.5rem 0;
}

.file-divider::before,
.file-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #dee2e6;
}

.file-divider-text {
  padding: 0 0.75rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 0.3rem 0;
}

.file-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: 0.5rem;
}

.file-size-muted {
  color: #6c757d;
  font-size: 0.75rem;
}

.file-progress {
  color: #0d6efd;
  font-weight: 600;
}

.file-size {
  color: #198754;
  margin-left: 0.5rem;
  font-weight: 500;
}

/* Download Footer */
.download-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid #e9ecef;
}

.download-status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.15rem;
}

.status-text {
  font-weight: 600;
  font-size: 0.8rem;
}

.recording-note {
  font-size: 0.7rem;
  color: #166534;
  opacity: 0.8;
}

.download-error {
  margin-top: 0.75rem;
  padding: 0.5rem;
  background: #fff5f5;
  border: 1px solid #fed7d7;
  border-radius: 6px;
  color: #c53030;
  font-size: 0.8rem;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: #6c757d;
}

.empty-state i {
  font-size: 3rem;
  opacity: 0.3;
  margin-bottom: 1rem;
  display: block;
}

.empty-state p {
  margin: 0;
}

/* Animations */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin {
  animation: spin 1s linear infinite;
}
</style>

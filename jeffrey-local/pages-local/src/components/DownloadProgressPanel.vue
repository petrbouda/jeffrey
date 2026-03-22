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
  <Teleport to="body">
    <!-- Expanded Panel -->
    <div v-if="isOpen" class="download-panel" :class="{ expanded: isExpanded }">
      <div v-if="isExpanded" class="panel-content">
        <!-- Header -->
        <div class="panel-header">
          <div class="header-title">
            <i class="bi bi-cloud-download me-2"></i>
            Downloads
            <span class="download-count">({{ downloads.length }})</span>
          </div>
          <div class="header-actions">
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
          </div>
        </div>

        <!-- Body - List of Downloads -->
        <div class="panel-body">
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
                <span class="download-file-count">{{ download.completedFiles }}/{{ download.totalFiles }}</span>
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
                  :style="{ width: fileCountPercent(download) + '%' }"
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
                  <span class="file-size-muted">{{ formatBytes(file.downloadedBytes) }}</span>
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
      </div>
    </div>

    <!-- Minimized Indicator -->
    <div
        v-if="isOpen && !isExpanded"
        class="minimized-indicator"
        :class="aggregateStatusClass"
        @click="$emit('expand')"
        title="Click to expand downloads"
    >
      <svg class="progress-ring" viewBox="0 0 56 56">
        <circle
            class="progress-ring-bg"
            cx="28"
            cy="28"
            r="24"
            fill="none"
            stroke-width="3"
        />
        <circle
            class="progress-ring-bar"
            cx="28"
            cy="28"
            r="24"
            fill="none"
            stroke-width="3"
            stroke-linecap="round"
            :style="progressRingStyle"
        />
      </svg>
      <i :class="aggregateIndicatorIcon" class="indicator-icon"></i>
      <span class="indicator-badge">
        <template v-if="downloads.length > 1">{{ downloads.length }}</template>
        <template v-else-if="downloads.length === 1">{{ downloads[0].completedFiles }}/{{ downloads[0].totalFiles }}</template>
        <template v-else>0</template>
      </span>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FormattingService from '@/services/FormattingService';
import DownloadProgress from '@/services/api/model/DownloadProgress';
import DownloadTaskStatus from '@/services/api/model/DownloadTaskStatus';

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

const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);

const fileCountPercent = (download: DownloadProgress): number => {
  if (!download.totalFiles) return 0;
  return Math.round((download.completedFiles / download.totalFiles) * 100);
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

// Minimized indicator styles
const aggregateStatusClass = computed(() => {
  switch (props.aggregateStatus) {
    case DownloadTaskStatus.COMPLETED:
      return 'status-completed';
    case DownloadTaskStatus.FAILED:
      return 'status-failed';
    case DownloadTaskStatus.CANCELLED:
      return 'status-cancelled';
    case DownloadTaskStatus.PROCESSING:
      return 'status-processing';
    default:
      return 'status-downloading';
  }
});

const aggregateIndicatorIcon = computed(() => {
  switch (props.aggregateStatus) {
    case DownloadTaskStatus.COMPLETED:
      return 'bi bi-check-lg';
    case DownloadTaskStatus.FAILED:
      return 'bi bi-exclamation-lg';
    case DownloadTaskStatus.CANCELLED:
      return 'bi bi-dash-lg';
    case DownloadTaskStatus.PROCESSING:
      return 'bi bi-gear-fill spin';
    default:
      return 'bi bi-cloud-download';
  }
});

const progressRingStyle = computed(() => {
  const percent = props.aggregateProgress;
  const circumference = 2 * Math.PI * 24; // r = 24
  const offset = circumference - (percent / 100) * circumference;
  return {
    strokeDasharray: `${circumference}`,
    strokeDashoffset: `${offset}`
  };
});
</script>

<style scoped>
/* Panel Container */
.download-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 100%;
  z-index: 1040;
  pointer-events: none;
}

.download-panel.expanded {
  pointer-events: auto;
}

/* Panel Content */
.panel-content {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  background: white;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.download-panel.expanded .panel-content {
  transform: translateX(0);
}

/* Header */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.header-title {
  font-weight: 600;
  font-size: 0.95rem;
  display: flex;
  align-items: center;
}

.download-count {
  font-weight: 400;
  opacity: 0.9;
  margin-left: 0.25rem;
}

.header-actions {
  display: flex;
  gap: 0.25rem;
}

.btn-icon {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* Body */
.panel-body {
  flex: 1;
  overflow-y: auto;
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

.download-file-count {
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

.file-size-muted {
  color: #6c757d;
  font-size: 0.75rem;
  margin-left: 0.5rem;
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

/* Minimized Indicator */
.minimized-indicator {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  z-index: 1040;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.minimized-indicator:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
}

.progress-ring {
  position: absolute;
  top: 0;
  left: 0;
  width: 60px;
  height: 60px;
  transform: rotate(-90deg);
}

.progress-ring-bg {
  stroke: #e9ecef;
}

.progress-ring-bar {
  transition: stroke-dashoffset 0.3s ease;
}

/* Status-based colors for progress ring */
.status-downloading .progress-ring-bar {
  stroke: #667eea;
}

.status-processing .progress-ring-bar {
  stroke: #17a2b8;
}

.status-completed .progress-ring-bar {
  stroke: #28a745;
}

.status-failed .progress-ring-bar {
  stroke: #dc3545;
}

.status-cancelled .progress-ring-bar {
  stroke: #ffc107;
}

.indicator-icon {
  font-size: 1.25rem;
  z-index: 1;
}

.status-downloading .indicator-icon {
  color: #667eea;
}

.status-processing .indicator-icon {
  color: #17a2b8;
}

.status-completed .indicator-icon {
  color: #28a745;
}

.status-failed .indicator-icon {
  color: #dc3545;
}

.status-cancelled .indicator-icon {
  color: #ffc107;
}

.indicator-badge {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.65rem;
  font-weight: 700;
  background: white;
  padding: 2px 6px;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
  color: #495057;
}

/* Animations */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin {
  animation: spin 1s linear infinite;
}

/* Pulse animation for pending state */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-downloading .indicator-icon {
  animation: pulse 1.5s ease-in-out infinite;
}
</style>

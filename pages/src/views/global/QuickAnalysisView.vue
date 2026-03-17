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
  <div>
    <!-- Upload Card -->
    <div class="main-card mb-4">
      <div class="main-card-header">
        <i class="bi bi-lightning-charge main-card-header-icon"></i>
        <div>
          <h5 class="main-card-header-title">Quick Analysis</h5>
          <span class="header-subtitle">Analyze JFR and Heap Dump files without creating a project</span>
        </div>
      </div>
      <div class="main-card-content">
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
              accept=".jfr,.lz4,.hprof,.gz"
              class="file-input-hidden"
              @change="handleFileSelect"
          >

          <!-- Default state -->
          <template v-if="!isProcessing && !selectedFile">
            <i class="bi bi-cloud-upload dropzone-icon"></i>
            <span class="dropzone-text">Drop your JFR or Heap Dump file here</span>
            <span class="dropzone-subtext">or click to select (.jfr, .jfr.lz4, .hprof, .hprof.gz)</span>
          </template>

          <!-- File selected state -->
          <template v-else-if="!isProcessing && selectedFile">
            <i :class="selectedFileType === 'hprof' ? 'bi bi-database file-icon' : 'bi bi-file-earmark-binary file-icon'"></i>
            <span class="file-name">{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatBytes(selectedFile.size) }}</span>
            <button class="btn-start" @click.stop="startAnalysis">
              <i class="bi bi-play-fill me-1"></i>
              {{ selectedFileType === 'hprof' ? 'Upload & Analyze' : 'Start Analysis' }}
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

          <span class="dropzone-note">Temporary profiles — cleared on restart</span>
        </div>

        <!-- Error Message -->
        <div v-if="errorMessage" class="error-message mt-3">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          {{ errorMessage }}
        </div>
      </div>
    </div>

    <!-- Recent Analyses Card -->
    <div class="main-card mb-4">
      <div class="main-card-header">
        <i class="bi bi-clock-history main-card-header-icon"></i>
        <h5 class="main-card-header-title">Recent Analyses</h5>
        <span v-if="recentProfiles.length > 0" class="recent-count-badge">{{ recentProfiles.length }}</span>
      </div>
      <div class="main-card-content">
        <!-- Profiles list -->
        <div v-if="recentProfiles.length > 0" class="recent-list">
          <div
              v-for="profile in recentProfiles"
              :key="profile.id"
              class="recent-item"
              @click="openProfile(profile)"
          >
            <div class="item-info">
              <i :class="isHeapDumpProfile(profile) ? 'bi bi-database item-icon' : 'bi bi-graph-up item-icon'"></i>
              <div class="item-details">
                <span class="item-name">{{ profile.name }}</span>
                <span class="item-meta">
                  {{ formatRelativeTime(profile.createdAt) }}
                  <span class="meta-divider">·</span>
                  {{ formatBytes(profile.sizeInBytes) }}
                </span>
              </div>
            </div>
            <div class="item-actions">
              <button class="btn-open" title="Open profile">
                <i class="bi bi-arrow-right"></i>
              </button>
              <button class="btn-delete" @click.stop="deleteProfile(profile.id)" title="Delete profile">
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else class="empty-state">
          <i class="bi bi-clock-history"></i>
          <span>No recent analyses yet</span>
          <span class="empty-hint">Upload a file above to get started</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import FormattingService from '@/services/FormattingService';
import QuickAnalysisClient from '@/services/api/QuickAnalysisClient';
import QuickAnalysisProfile from '@/services/api/model/QuickAnalysisProfile';

type QuickAnalysisFileType = 'jfr' | 'hprof';

const router = useRouter();
const quickAnalysisClient = new QuickAnalysisClient();

// State
const selectedFile = ref<File | null>(null);
const selectedFileType = ref<QuickAnalysisFileType>('jfr');
const isProcessing = ref(false);
const statusMessage = ref('');
const errorMessage = ref<string | null>(null);
const recentProfiles = ref<QuickAnalysisProfile[]>([]);
const isDragOver = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

// Formatting
const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);
const formatRelativeTime = (dateString: string) => {
  const utcString = dateString.endsWith('Z') ? dateString : dateString + 'Z';
  const timestamp = new Date(utcString).getTime();
  return FormattingService.formatRelativeTime(timestamp);
};

const isHeapDumpProfile = (profile: QuickAnalysisProfile): boolean => {
  return profile.eventSource === 'HEAP_DUMP';
};

const detectFileType = (filename: string): QuickAnalysisFileType => {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.hprof') || lower.endsWith('.hprof.gz')) {
    return 'hprof';
  }
  return 'jfr';
};

// File handling
const triggerFileInput = () => {
  if (!isProcessing.value) {
    fileInputRef.value?.click();
  }
};

const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    setSelectedFile(file);
  }
  input.value = '';
};

const handleFileDrop = (event: DragEvent) => {
  isDragOver.value = false;
  const file = event.dataTransfer?.files[0];
  if (file) {
    setSelectedFile(file);
    startAnalysis();
  }
};

const setSelectedFile = (file: File) => {
  selectedFile.value = file;
  errorMessage.value = null;
  selectedFileType.value = detectFileType(file.name);
};

// Analysis
const startAnalysis = async () => {
  const file = selectedFile.value;
  if (!file) return;

  const isHeapDump = selectedFileType.value === 'hprof';

  isProcessing.value = true;
  statusMessage.value = isHeapDump ? 'Uploading heap dump...' : 'Analyzing JFR file...';
  errorMessage.value = null;

  try {
    let profileId: string;

    if (isHeapDump) {
      profileId = await quickAnalysisClient.uploadHeapDump(file);
    } else {
      profileId = await quickAnalysisClient.uploadAndAnalyze(file);
    }

    // Reset and navigate
    selectedFile.value = null;
    isProcessing.value = false;

    if (isHeapDump) {
      await router.push(`/profiles/${profileId}/heap-dump/settings`);
    } else {
      await router.push(`/profiles/${profileId}/overview`);
    }
  } catch (error) {
    isProcessing.value = false;
    const fileTypeLabel = isHeapDump ? 'heap dump' : 'JFR file';
    errorMessage.value = error instanceof Error ? error.message : `Failed to process ${fileTypeLabel}`;
  }
};

// Profile actions
const openProfile = async (profile: QuickAnalysisProfile) => {
  if (isHeapDumpProfile(profile)) {
    await router.push(`/profiles/${profile.id}/heap-dump/settings`);
  } else {
    await router.push(`/profiles/${profile.id}/overview`);
  }
};

const deleteProfile = async (profileId: string) => {
  try {
    await quickAnalysisClient.deleteProfile(profileId);
    await loadRecentProfiles();
  } catch {
    // Toast is shown automatically by HttpInterceptor
  }
};

const loadRecentProfiles = async () => {
  try {
    recentProfiles.value = await quickAnalysisClient.listProfiles();
  } catch (error) {
    console.error('Failed to load recent profiles:', error);
  }
};

onMounted(() => {
  loadRecentProfiles();
});
</script>

<style scoped>
@import '@/styles/shared-components.css';

.header-subtitle {
  font-size: 0.8rem;
  color: #6b7280;
  font-weight: 400;
}

/* Dropzone */
.dropzone {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.03) 0%, rgba(118, 75, 162, 0.03) 100%);
  border: 2px dashed rgba(94, 100, 255, 0.3);
  border-radius: 12px;
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-height: 180px;
  justify-content: center;
}

.dropzone:hover:not(.processing) {
  border-color: rgba(94, 100, 255, 0.6);
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.06) 0%, rgba(118, 75, 162, 0.06) 100%);
}

.dropzone.drag-over {
  border-color: #5e64ff;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.15);
}

.dropzone.processing {
  cursor: default;
  border-style: solid;
  border-color: rgba(94, 100, 255, 0.3);
}

.dropzone.has-file {
  border-style: solid;
  border-color: rgba(94, 100, 255, 0.4);
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
}

.file-input-hidden {
  display: none;
}

.dropzone-icon {
  font-size: 3rem;
  color: #5e64ff;
  opacity: 0.7;
}

.dropzone-text {
  font-size: 1.05rem;
  font-weight: 500;
  color: #374151;
}

.dropzone-subtext {
  font-size: 0.85rem;
  color: #6b7280;
}

.file-icon {
  font-size: 2.25rem;
  color: #5e64ff;
}

.file-name {
  font-weight: 600;
  font-size: 1rem;
  color: #374151;
  word-break: break-all;
  text-align: center;
}

.file-size {
  font-size: 0.85rem;
  color: #6b7280;
}

.btn-start {
  margin-top: 8px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  border: none;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
}

.btn-start:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.4);
}

/* Processing */
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
  border: 3px solid rgba(94, 100, 255, 0.2);
  border-top-color: #5e64ff;
  border-radius: 50%;
  animation: spinner-rotate 0.8s linear infinite;
}

@keyframes spinner-rotate {
  to { transform: rotate(360deg); }
}

.processing-file .file-name {
  font-size: 0.95rem;
  font-weight: 500;
}

.processing-status {
  font-size: 0.9rem;
  color: #6b7280;
}

/* Error */
.error-message {
  padding: 0.75rem 1rem;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  font-size: 0.9rem;
}

.dropzone-note {
  font-size: 0.75rem;
  color: #9ca3af;
  margin-top: 4px;
}

/* Recent Analyses */
.recent-count-badge {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 0.8rem;
  font-weight: 600;
  margin-left: auto;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.recent-item:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.item-icon {
  font-size: 1.15rem;
  color: #5e64ff;
}

.item-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.item-name {
  font-size: 0.9rem;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  font-size: 0.8rem;
  color: #6b7280;
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
  color: #6b7280;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  transition: all 0.2s ease;
}

.btn-open:hover {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
}

.btn-delete:hover {
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #9ca3af;
  padding: 32px 20px;
}

.empty-state i {
  font-size: 2.5rem;
}

.empty-state span {
  font-size: 0.9rem;
}

.empty-hint {
  font-size: 0.8rem !important;
  color: #d1d5db;
}
</style>

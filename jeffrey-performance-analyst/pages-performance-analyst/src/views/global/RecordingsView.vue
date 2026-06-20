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
  <div class="recordings-view">
    <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi-record-circle"
          title="Recordings"
          :badge="recordings.length"
        />
      </template>

      <LoadingState v-if="loading" message="Loading recordings..." />
      <ErrorState v-else-if="error" :message="error" />
      <template v-else>
        <EmptyState
          v-if="recordings.length === 0"
          icon="bi-inbox"
          title="No recordings yet"
          message="Recordings downloaded from a project's sessions will appear here."
        />
        <div v-else class="recording-list">
          <div v-for="recording in recordings" :key="recording.id" class="recording-item">
            <div class="recording-row" @click="toggleExpand(recording.id)">
              <i
                class="bi expand-caret"
                :class="isExpanded(recording.id) ? 'bi-chevron-down' : 'bi-chevron-right'"
              ></i>
              <div class="recording-main">
                <div class="recording-name" :title="recording.filename">
                  {{ recording.filename }}
                </div>
                <div class="recording-meta">
                  <Badge variant="info" size="sm">{{ recording.eventSource }}</Badge>
                  <span class="meta-sep">{{ recording.files.length }} file(s)</span>
                  <span class="meta-sep">{{ formatBytes(recording.sizeInBytes) }}</span>
                  <span v-if="recording.durationInMillis > 0" class="meta-sep">
                    {{ formatDuration(recording.durationInMillis) }}
                  </span>
                  <span class="meta-sep">{{ formatTimestamp(recording.uploadedAt) }}</span>
                </div>
              </div>
              <button
                v-if="hasJfr(recording)"
                class="btn btn-sm btn-outline-primary ai-prompt-btn"
                :disabled="generating[recording.id]"
                title="Generate AI flamegraph prompt"
                @click.stop="generateAiPrompt(recording)"
              >
                <i class="bi" :class="generating[recording.id] ? 'bi-hourglass-split' : 'bi-robot'"></i>
                {{ generating[recording.id] ? 'Generating…' : 'AI Prompt' }}
              </button>
            </div>
            <div v-if="isExpanded(recording.id)" class="recording-files">
              <RecordingFileGroupList
                :recording-id="recording.id"
                :files="recording.files"
                @download="downloadFile"
              />
            </div>
          </div>
        </div>
      </template>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import RecordingFileGroupList from '@workspaces/components/RecordingFileGroupList.vue';
import RecordingsClient from '@workspaces/services/api/RecordingsClient';
import type Recording from '@workspaces/services/api/model/Recording';
import RecordingFileType from '@workspaces/services/api/model/RecordingFileType';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import FlamegraphAiExportClient from '@/services/api/FlamegraphAiExportClient';

const recordingsClient = new RecordingsClient();
const aiExportClient = new FlamegraphAiExportClient();

const recordings = ref<Recording[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const expanded = ref<Record<string, boolean>>({});
const generating = ref<Record<string, boolean>>({});

const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);
const formatTimestamp = (millis: number) => FormattingService.formatTimestamp(millis);
const formatDuration = (millis: number) => FormattingService.formatDurationMillisCompact(millis);

const isExpanded = (recordingId: string): boolean => !!expanded.value[recordingId];

const toggleExpand = (recordingId: string) => {
  expanded.value[recordingId] = !expanded.value[recordingId];
};

const downloadFile = (recordingId: string, fileId: string) => {
  recordingsClient.downloadFile(recordingId, fileId);
};

const hasJfr = (recording: Recording): boolean =>
  recording.files.some(
    file => file.type === RecordingFileType.JFR || file.type === RecordingFileType.JFR_LZ4
  );

const generateAiPrompt = async (recording: Recording) => {
  generating.value[recording.id] = true;
  try {
    await aiExportClient.generate(recording.id);
    ToastService.success(
      'AI prompt generated',
      'Flamegraph AI prompt(s) printed to the server console.'
    );
  } catch (e) {
    console.error('Failed to generate AI prompt:', e);
    ToastService.error('Generation failed', 'Could not generate the AI flamegraph prompt.');
  } finally {
    generating.value[recording.id] = false;
  }
};

const loadRecordings = async () => {
  loading.value = true;
  error.value = null;
  try {
    recordings.value = await recordingsClient.listRecordings();
  } catch (e) {
    console.error('Failed to load recordings:', e);
    error.value = 'Failed to load recordings.';
  } finally {
    loading.value = false;
  }
};

onMounted(loadRecordings);
</script>

<style scoped>
.recordings-view {
  padding: 1rem;
}

.recording-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.recording-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.recording-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
  transition: background-color 0.15s;
}

.recording-row:hover {
  background-color: var(--color-light);
}

.expand-caret {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.recording-main {
  flex: 1;
  min-width: 0;
}

.ai-prompt-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.recording-name {
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recording-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.recording-files {
  padding: 0.5rem 1rem 1rem;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
}
</style>

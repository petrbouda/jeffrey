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
  One recording session, read on its own when it is shown: its figures, its files by type, and
  what can be done with them — download the session or a run of its chunks, one file, or delete
  the session. Key it by the session's id: a different session is a fresh read and a fresh
  selection.
-->

<template>
  <div class="session-detail">
    <div class="session-toolbar">
      <button
        v-if="!isCollectorOnly"
        class="btn btn-sm btn-primary"
        type="button"
        :disabled="!detail || detail.failed"
        title="Download the session's recordings"
        @click="downloadSession"
      >
        <i class="bi bi-folder-symlink me-1"></i>Download session
      </button>
      <button
        class="btn btn-sm btn-outline-secondary"
        :class="{ active: selecting }"
        type="button"
        :disabled="!detail || detail.failed || files.length === 0"
        title="Pick files to download together"
        @click="toggleSelecting"
      >
        <i class="bi me-1" :class="selecting ? 'bi-check2-square' : 'bi-square'"></i>
        {{ selecting ? 'Done selecting' : 'Select files' }}
      </button>
      <div class="session-toolbar-end">
        <button
          class="btn btn-sm btn-outline-secondary"
          type="button"
          title="Read the session again"
          :disabled="reading"
          @click="loadInBackground"
        >
          <i class="bi bi-arrow-clockwise"></i>
        </button>
        <button
          v-if="!remoteReadOnly"
          class="btn btn-sm btn-outline-danger"
          type="button"
          :disabled="session.newest"
          :title="session.newest ? NEWEST_UNDELETABLE : 'Delete the session'"
          @click="deleteDialog = true"
        >
          <i class="bi bi-trash me-1"></i>Delete
        </button>
      </div>
    </div>

    <div class="session-content">
      <ErrorState
        v-if="!detail && readFailed"
        message="The files of this session could not be read"
        @retry="loadInBackground"
      />
      <LoadingState v-else-if="!detail" message="Reading the session's files..." />

      <template v-else>
        <div class="session-figures">
          <TimelineBar
            :created-at="session.createdAt"
            :last-data-at="detail.lastDataAt"
            :duration="detail.duration"
          />
        </div>

        <div class="session-summary">
          <Badge
            key-label="Files"
            :value="detail.fileCount"
            variant="secondary"
            size="s"
            :uppercase="false"
            borderless
          />
          <Badge
            key-label="Size"
            :value="FormattingService.formatBytes(detail.sizeBytes)"
            variant="secondary"
            size="s"
            :uppercase="false"
            borderless
          />
          <Badge
            v-if="sizesUnreported"
            value="sizes unavailable"
            variant="warning"
            size="xs"
            icon="bi bi-exclamation-triangle"
            :uppercase="false"
            :title="UNREPORTED_SIZES_HINT"
          />
        </div>

        <EmptyState
          v-if="detail.failed"
          icon="bi-exclamation-octagon"
          title="Ended before writing any data"
          :description="FAILED_SESSION"
        />
        <EmptyState
          v-else-if="files.length === 0"
          icon="bi-inbox"
          title="No files"
          description="This session holds no files yet."
        />

        <template v-else>
          <!-- Multi-select controls -->
          <div v-if="selecting" class="multi-select-controls">
            <button
              class="btn btn-sm btn-outline-primary"
              type="button"
              title="Select every file"
              @click="selectAll(true)"
            >
              <i class="bi bi-check2-square me-1"></i>Select All
            </button>
            <button
              class="btn btn-sm btn-outline-secondary"
              type="button"
              :disabled="selectedCount === 0"
              title="Clear the selection"
              @click="selectAll(false)"
            >
              <i class="bi bi-x-lg me-1"></i>Clear
            </button>
            <div class="multi-select-end">
              <Badge
                :value="`${selectedCount} selected`"
                variant="secondary"
                size="xs"
                :uppercase="false"
              />
              <button
                v-if="!isCollectorOnly"
                class="btn btn-sm btn-primary"
                type="button"
                :disabled="selectedCount === 0 || !downloadableSelection"
                :title="downloadableSelection ? 'Download the selected files' : gapMessage"
                @click="downloadSelected"
              >
                <i class="bi bi-folder-symlink me-1"></i>Download selected
              </button>
            </div>
          </div>

          <!-- Why Download is greyed out: the checkboxes stay free; only a gapped download is refused -->
          <div
            v-if="selecting && !downloadableSelection"
            class="field-alert field-alert-warning"
            role="alert"
          >
            <i class="bi bi-exclamation-triangle"></i>
            <span>{{ gapMessage }}</span>
          </div>

          <!-- JFR Recordings: the session's own chunks, open by default -->
          <div v-if="recordings.length > 0" class="type-panel">
            <div class="type-panel-header" @click="recordingPanelOpen = !recordingPanelOpen">
              <RecordingFileRow
                :filename="RECORDING_DISPLAY.name"
                :fileType="RECORDING_DISPLAY.fileType"
                :sizeInBytes="totalSize(recordings)"
              >
                <template #before>
                  <i
                    class="bi me-1 type-panel-chevron"
                    :class="recordingPanelOpen ? 'bi-chevron-down' : 'bi-chevron-right'"
                  ></i>
                  <div v-if="selecting" class="form-check file-form-check me-2">
                    <input
                      class="form-check-input file-checkbox"
                      type="checkbox"
                      :checked="isGroupSelected(recordings)"
                      @change="toggleGroup(recordings)"
                      @click.stop
                    />
                  </div>
                </template>
                <template #extra-badges>
                  <span class="file-count ms-2">
                    <i class="bi bi-files me-1"></i>{{ plural(recordings.length, 'file') }}
                  </span>
                </template>
              </RecordingFileRow>
            </div>

            <div v-if="recordingPanelOpen" class="type-panel-body">
              <template v-for="entry in visibleRecordingGroups" :key="entry.primary.id">
                <div class="file-wrapper" :class="{ 'file-selected': selected[entry.primary.id] }">
                  <RecordingFileRow
                    :filename="entry.primary.name"
                    :fileType="entry.primary.fileType"
                    :sizeInBytes="entry.primary.size"
                    :timestamp="entry.primary.createdAt"
                  >
                    <template #before>
                      <button
                        v-if="entry.children.length > 0"
                        class="rotation-toggle-btn me-1"
                        type="button"
                        :title="
                          openRotated[entry.primary.name]
                            ? 'Collapse rotated files'
                            : 'Expand rotated files'
                        "
                        @click.stop="toggleRotated(entry.primary.name)"
                      >
                        <i
                          class="bi"
                          :class="
                            openRotated[entry.primary.name] ? 'bi-chevron-down' : 'bi-chevron-right'
                          "
                        ></i>
                      </button>
                      <div
                        v-if="selecting && isDownloadable(entry.primary)"
                        class="form-check file-form-check me-2"
                      >
                        <input
                          :id="'source-' + entry.primary.id"
                          class="form-check-input file-checkbox"
                          type="checkbox"
                          :checked="selected[entry.primary.id]"
                          @change="toggleFile(entry.primary.id)"
                          @click.stop
                        />
                      </div>
                    </template>
                    <template #extra-badges>
                      <Badge
                        v-if="entry.children.length > 0"
                        :value="`+${entry.children.length} rotated · ${FormattingService.formatBytes(entry.totalSize)}`"
                        variant="grey"
                        size="xxs"
                        class="ms-1"
                        :uppercase="false"
                      />
                    </template>
                    <template #actions>
                      <button
                        v-if="isDownloadable(entry.primary)"
                        class="btn btn-sm btn-outline-secondary download-file-btn"
                        type="button"
                        title="Download file"
                        @click.stop="downloadFile(entry.primary)"
                      >
                        <i class="bi bi-download"></i>
                      </button>
                    </template>
                  </RecordingFileRow>
                </div>

                <template v-if="entry.children.length > 0 && openRotated[entry.primary.name]">
                  <div
                    v-for="child in entry.children"
                    :key="child.id"
                    class="file-wrapper rotated-child-row"
                    :class="{ 'file-selected': selected[child.id] }"
                  >
                    <RecordingFileRow
                      :filename="child.name"
                      :fileType="child.fileType"
                      :sizeInBytes="child.size"
                      :timestamp="child.createdAt"
                    >
                      <template #before>
                        <div
                          v-if="selecting && isDownloadable(child)"
                          class="form-check file-form-check me-2"
                        >
                          <input
                            :id="'source-' + child.id"
                            class="form-check-input file-checkbox"
                            type="checkbox"
                            :checked="selected[child.id]"
                            @change="toggleFile(child.id)"
                            @click.stop
                          />
                        </div>
                      </template>
                      <template #actions>
                        <button
                          v-if="isDownloadable(child)"
                          class="btn btn-sm btn-outline-secondary download-file-btn"
                          type="button"
                          title="Download file"
                          @click.stop="downloadFile(child)"
                        >
                          <i class="bi bi-download"></i>
                        </button>
                      </template>
                    </RecordingFileRow>
                  </div>
                </template>
              </template>

              <div v-if="recordingGroups.length > recordingLimit" class="show-more">
                <button
                  class="btn btn-sm btn-outline-primary"
                  type="button"
                  @click="recordingLimit = recordingGroups.length"
                >
                  <i class="bi bi-chevron-down me-1"></i>Show
                  {{ recordingGroups.length - recordingLimit }} more files
                </button>
              </div>
            </div>
          </div>

          <!-- Artifact types with a panel of their own -->
          <div v-for="panel in panels" :key="panel.groupKey" class="type-panel">
            <div class="type-panel-header" @click="togglePanel(panel.groupKey)">
              <RecordingFileRow
                :filename="panel.display.name"
                :fileType="panel.display.fileType"
                :sizeInBytes="panel.totalSize"
              >
                <template #before>
                  <i
                    class="bi me-1 type-panel-chevron"
                    :class="openPanels[panel.groupKey] ? 'bi-chevron-down' : 'bi-chevron-right'"
                  ></i>
                  <div v-if="selecting" class="form-check file-form-check me-2">
                    <input
                      class="form-check-input file-checkbox"
                      type="checkbox"
                      :checked="isGroupSelected(panel.files)"
                      @change="toggleGroup(panel.files)"
                      @click.stop
                    />
                  </div>
                </template>
                <template #extra-badges>
                  <span class="file-count ms-2">
                    <i class="bi bi-files me-1"></i>{{ plural(panel.files.length, 'file') }}
                  </span>
                </template>
              </RecordingFileRow>
            </div>

            <div v-if="openPanels[panel.groupKey]" class="type-panel-body">
              <div
                v-for="file in visiblePanelFiles(panel)"
                :key="file.id"
                class="file-wrapper"
                :class="{ 'file-selected': selected[file.id] }"
              >
                <RecordingFileRow
                  :filename="file.name"
                  :fileType="file.fileType"
                  :sizeInBytes="file.size"
                  :timestamp="file.createdAt"
                >
                  <template #before>
                    <div v-if="selecting && isDownloadable(file)" class="form-check file-form-check me-2">
                      <input
                        :id="'source-' + file.id"
                        class="form-check-input file-checkbox"
                        type="checkbox"
                        :checked="selected[file.id]"
                        @change="toggleFile(file.id)"
                        @click.stop
                      />
                    </div>
                  </template>
                  <template #actions>
                    <button
                      v-if="isDownloadable(file)"
                      class="btn btn-sm btn-outline-secondary download-file-btn"
                      type="button"
                      title="Download file"
                      @click.stop="downloadFile(file)"
                    >
                      <i class="bi bi-download"></i>
                    </button>
                  </template>
                </RecordingFileRow>
              </div>

              <div v-if="panel.files.length > panelLimit(panel)" class="show-more">
                <button
                  class="btn btn-sm btn-outline-primary"
                  type="button"
                  @click="panelLimits[panel.groupKey] = panel.files.length"
                >
                  <i class="bi bi-chevron-down me-1"></i>Show
                  {{ panel.files.length - panelLimit(panel) }} more
                </button>
              </div>
            </div>
          </div>

          <!-- Artifact types with a single file -->
          <div
            v-for="file in standalone"
            :key="file.id"
            class="file-wrapper"
            :class="{ 'file-selected': selected[file.id] }"
          >
            <RecordingFileRow
              :filename="file.name"
              :fileType="file.fileType"
              :sizeInBytes="file.size"
              :timestamp="file.createdAt"
            >
              <template #before>
                <div v-if="selecting && isDownloadable(file)" class="form-check file-form-check me-2">
                  <input
                    :id="'source-' + file.id"
                    class="form-check-input file-checkbox"
                    type="checkbox"
                    :checked="selected[file.id]"
                    @change="toggleFile(file.id)"
                    @click.stop
                  />
                </div>
              </template>
              <template #actions>
                <button
                  v-if="isDownloadable(file)"
                  class="btn btn-sm btn-outline-secondary download-file-btn"
                  type="button"
                  title="Download file"
                  @click.stop="downloadFile(file)"
                >
                  <i class="bi bi-download"></i>
                </button>
              </template>
            </RecordingFileRow>
          </div>
        </template>
      </template>
    </div>

    <ConfirmationDialog
      v-model:show="deleteDialog"
      title="Confirm Deletion"
      :message="`Delete session ${session.id} and every file in it?`"
      sub-message="This action cannot be undone."
      confirm-label="Delete Session"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteSessionButton"
      modal-id="deleteSessionModal"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import Badge from '@shared/components/Badge.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TimelineBar from '@shared/components/TimelineBar.vue';
import FormattingService from '@shared/services/FormattingService.ts';
import { ToastService } from '@shared/services/ToastService';
import RecordingFileRow from '@remote/components/RecordingFileRow.vue';
import { useRemoteReadOnly } from '@remote/composables/useRemoteReadOnly';
import { useSessionReads } from '@remote/composables/useSessionReads';
import ProjectRepositoryClient from '@remote/services/api/ProjectRepositoryClient.ts';
import RecordingSessionHeader from '@remote/services/api/model/RecordingSessionHeader.ts';
import RepositoryFile from '@remote/services/api/model/RepositoryFile.ts';
import { downloadAssistantStore } from '@remote/stores/assistants/downloadAssistantStore';
import {
  chunkSelectionGapMessage,
  downloadableFiles,
  isContiguousChunkSelection,
  isDownloadable
} from '@remote/services/chunkSelection.ts';
import { hasUnreportedSizes } from '@remote/services/sessionSizes.ts';
import {
  type ArtifactTypeGroup,
  type TypeGroupPanel,
  RECORDING_GROUP,
  TYPE_GROUP_DISPLAY,
  groupRotatedFiles,
  recordingFiles,
  standaloneArtifacts,
  totalSize,
  typeGroupPanels
} from '@remote/services/sessionFiles.ts';

const props = withDefaults(
  defineProps<{
    session: RecordingSessionHeader;
    workspaceId: string;
    projectId: string;
    isCollectorOnly?: boolean;
  }>(),
  {
    isCollectorOnly: false
  }
);

const emit = defineEmits<{
  (e: 'deleted', sessionId: string): void;
}>();

const toast = ToastService;

/** How many files a panel shows before its Show-more. */
const DEFAULT_FILES_LIMIT = 15;

const RECORDING_DISPLAY = TYPE_GROUP_DISPLAY[RECORDING_GROUP];

const NEWEST_UNDELETABLE =
  'The newest session of its instance may still grow, so it cannot be deleted';

const FAILED_SESSION =
  'A newer session on this instance started before this one wrote a recording, which usually ' +
  'means the JVM failed to start or its container restarted. There is nothing to download.';

/**
 * What the "sizes unavailable" badge means, and — as importantly — what it does not: the
 * recording is intact, only the figures are missing. Carried as a tooltip rather than a
 * banner because the session is otherwise perfectly usable.
 */
const UNREPORTED_SIZES_HINT =
  'Every file in this session reports zero bytes, after it recorded for long enough to have ' +
  "produced data. Sizes are read from the remote's repository directory, so this usually means " +
  'that mount is serving stale file metadata (an object-storage FUSE, an NFS attribute cache, ' +
  'or an SMB share listing a file another client still has open) rather than that the session ' +
  'is empty. The files can still be downloaded.';

const repository = computed(() => new ProjectRepositoryClient(props.workspaceId, props.projectId));

/** A read-only remote refuses deletions, so they are not offered. */
const remoteReadOnly = useRemoteReadOnly();

const { sessions: reads, loading, failed, read } = useSessionReads(() => repository.value);

const detail = computed(() => reads.value[props.session.id]);
const files = computed<RepositoryFile[]>(() => detail.value?.files ?? []);
const reading = computed(() => loading.value[props.session.id] === true);
const readFailed = computed(() => failed.value[props.session.id] === true);
const sizesUnreported = computed(() => hasUnreportedSizes(detail.value));

// --- What is open and selected ---
const selecting = ref(false);
const selected = ref<Record<string, boolean>>({});
const recordingPanelOpen = ref(true);
const recordingLimit = ref(DEFAULT_FILES_LIMIT);
const openPanels = ref<Partial<Record<ArtifactTypeGroup, boolean>>>({});
const panelLimits = ref<Partial<Record<ArtifactTypeGroup, number>>>({});
const openRotated = ref<Record<string, boolean>>({});
const deleteDialog = ref(false);

// --- The files, laid out ---
const recordings = computed(() => recordingFiles(files.value));
const recordingGroups = computed(() => groupRotatedFiles(recordings.value));
const visibleRecordingGroups = computed(() => recordingGroups.value.slice(0, recordingLimit.value));
const panels = computed(() => typeGroupPanels(files.value));
const standalone = computed(() => standaloneArtifacts(files.value));

const selectedCount = computed(() => Object.values(selected.value).filter(Boolean).length);

/**
 * Whether the ticked recordings may be downloaded together. They become one recording reporting
 * one span, so a skipped chunk would leave a hole the result cannot show. Ticking itself stays
 * free; only the download is refused.
 */
const downloadableSelection = computed(() =>
  isContiguousChunkSelection(files.value, selected.value)
);
const gapMessage = computed(() => chunkSelectionGapMessage(files.value, selected.value));

const plural = (count: number, noun: string): string => `${count} ${noun}${count === 1 ? '' : 's'}`;

const panelLimit = (panel: TypeGroupPanel): number =>
  panelLimits.value[panel.groupKey] ?? DEFAULT_FILES_LIMIT;

const visiblePanelFiles = (panel: TypeGroupPanel): RepositoryFile[] =>
  panel.files.slice(0, panelLimit(panel));

/**
 * Reads the session and rebuilds the selection from its files: a file retention removed in the
 * meantime drops out of the selection instead of being counted as still ticked.
 */
const load = async (): Promise<RepositoryFile[]> => {
  const fresh = await read(props.session.id);
  const previous = selected.value;
  selected.value = Object.fromEntries(fresh.files.map(file => [file.id, previous[file.id] ?? false]));
  return fresh.files;
};

/** A read whose failure the view reports itself — its error state carries the retry. */
const loadInBackground = (): void => {
  load().catch(() => {});
};

const togglePanel = (groupKey: ArtifactTypeGroup) => {
  openPanels.value[groupKey] = !openPanels.value[groupKey];
};

const toggleRotated = (primaryName: string) => {
  openRotated.value[primaryName] = !openRotated.value[primaryName];
};

// --- Selection ---
const toggleSelecting = () => {
  selecting.value = !selecting.value;
  if (!selecting.value) {
    selectAll(false);
  }
};

const toggleFile = (fileId: string) => {
  selected.value[fileId] = !selected.value[fileId];
};

const selectAll = (select: boolean) => {
  for (const file of files.value) {
    if (isDownloadable(file)) {
      selected.value[file.id] = select;
    }
  }
};

const isGroupSelected = (group: RepositoryFile[]): boolean => {
  const selectable = group.filter(isDownloadable);
  return selectable.length > 0 && selectable.every(file => selected.value[file.id]);
};

const toggleGroup = (group: RepositoryFile[]) => {
  const select = !isGroupSelected(group);
  for (const file of group) {
    if (isDownloadable(file)) {
      selected.value[file.id] = select;
    }
  }
};

// --- Downloads and deletion ---
const downloadFile = async (file: RepositoryFile) => {
  try {
    await repository.value.downloadFile(props.session.id, file.id);
  } catch (error: any) {
    toast.error('Download File', error.message || 'Failed to download file');
  }
};

const startDownload = async (fileIds: string[], onComplete: () => void) => {
  await downloadAssistantStore.startDownload(
    props.workspaceId,
    props.projectId,
    props.session.id,
    fileIds,
    async () => {
      onComplete();
    }
  );
};

const downloadSession = async () => {
  try {
    // Read now rather than trusting an earlier read: retention may have trimmed the session since.
    // async-profiler's scratch file is left out; the newest recording goes too, and the download
    // checks it arrived whole
    const downloadable = downloadableFiles(await load());
    if (!downloadable.some(file => file.isRecording)) {
      toast.warn(
        'No recording data available',
        'The session may have been terminated before any data was written.'
      );
      return;
    }
    await startDownload(
      downloadable.map(file => file.id),
      loadInBackground
    );
  } catch (error: any) {
    console.error('Error downloading session:', error);
    toast.error('Download', error.message || 'Failed to download recording session');
  }
};

const downloadSelected = async () => {
  const chosen = files.value.filter(file => selected.value[file.id]);
  if (chosen.length === 0) {
    toast.info('Download', 'No recordings selected');
    return;
  }
  try {
    await startDownload(
      chosen.map(file => file.id),
      () => {
        selectAll(false);
        loadInBackground();
      }
    );
  } catch (error: any) {
    console.error('Error downloading selected recordings:', error);
    toast.error('Download', error.message || 'Failed to download selected recordings');
  }
};

const confirmDelete = async () => {
  try {
    await repository.value.deleteRecordingSession(props.session.id);
    toast.success('Delete Session', `Session ${props.session.id} and its files were deleted`);
    deleteDialog.value = false;
    emit('deleted', props.session.id);
  } catch (error: any) {
    console.error('Error deleting recording session:', error);
    toast.error('Delete Session', error.message || 'Failed to delete recording session');
  }
};

onMounted(loadInBackground);
</script>

<style scoped>
.session-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-4);
  border-bottom: 1px solid var(--color-border);
}

.session-toolbar-end {
  margin-left: auto;
  display: flex;
  gap: var(--spacing-2);
}

.session-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  padding: var(--spacing-3) var(--spacing-4) var(--spacing-4);
}

.session-figures {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.session-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-1);
}

.multi-select-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-primary-lighter);
  border: 1px solid var(--color-primary-light);
  border-radius: var(--radius-base);
}

.multi-select-end {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.form-check-input:checked {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
}

.file-checkbox {
  width: 1.2em !important;
  height: 1.2em !important;
  margin-top: calc(0.15em - 2px);
  cursor: pointer;
  border-width: 1px;
}

.file-form-check {
  min-height: auto;
  margin-bottom: 0;
  padding-left: 1.8em;
}

.type-panel-header {
  cursor: pointer;
  user-select: none;
}

.type-panel-chevron {
  font-size: 0.65rem;
  color: var(--color-text-muted);
  width: 16px;
  text-align: center;
}

.type-panel-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  border-left: 1px solid var(--color-border);
  margin-left: 32px;
  padding: 6px 0 var(--spacing-2) 20px;
}

.file-wrapper {
  border-radius: var(--radius-base);
}

.file-selected {
  background: var(--color-primary-lighter);
}

.file-count {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.rotation-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.rotation-toggle-btn:hover {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}

.rotated-child-row {
  margin-left: 28px;
  border-left: 2px solid var(--color-border);
  padding-left: 10px;
}

.show-more {
  text-align: center;
}

.download-file-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: opacity var(--transition-base);
}

.download-file-btn:hover {
  opacity: 1;
}
</style>

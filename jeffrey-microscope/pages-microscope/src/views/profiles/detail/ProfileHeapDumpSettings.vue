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
  Heap Dump Overview: the summary of a built heap dump, the timeline of its last initialization, and
  re-initialization (optionally forcing compressed oops when auto-detection guessed wrong).

  A standalone heap dump is initialized from its row on the Recordings page, so this page only
  follows a run that is already going and offers to start one when none has finished. A heap dump
  attached to a JFR profile has no row of its own: it is uploaded and initialized here.
-->
<template>
  <div>
    <ConfirmationDialog
      v-model:show="deleteHeapDumpDialog"
      title="Delete Heap Dump"
      message="Are you sure you want to delete the heap dump?"
      sub-message="This will remove all heap dump files including the cache. This action cannot be undone."
      confirm-label="Delete"
      confirm-button-class="btn-danger"
      confirm-button-id="deleteHeapDumpButton"
      modal-id="deleteHeapDumpModal"
      @confirm="confirmDeleteHeapDump"
    />

    <GenericModal
      modal-id="reinitializeHeapDumpModal"
      :show="reinitializeDialog"
      title="Re-initialize Heap Dump"
      icon="bi-arrow-repeat"
      size="md"
      modal-dialog-class="modal-dialog-centered"
      @update:show="reinitializeDialog = $event"
    >
      <p class="reinit-lead">
        Rebuilds the index and every analysis. Progress shows here and on the recording's row.
      </p>
      <fieldset class="reinit-options">
        <legend class="reinit-legend">Compressed oops</legend>
        <label
          v-for="option in COMPRESSED_OOPS_OPTIONS"
          :key="option.value"
          class="reinit-option"
          :class="{ 'reinit-option--selected': compressedOopsChoice === option.value }"
          :for="`compressedOops-${option.value}`"
        >
          <input
            :id="`compressedOops-${option.value}`"
            v-model="compressedOopsChoice"
            class="form-check-input"
            type="radio"
            name="compressedOops"
            :value="option.value"
          />
          <span>
            <span class="reinit-option-title">{{ option.title }}</span>
            <span class="reinit-option-detail">{{ optionDetail(option) }}</span>
          </span>
        </label>
      </fieldset>
      <p class="reinit-note">
        Force a value only when object sizes look wrong, which means auto-detection guessed
        incorrectly.
      </p>
      <template #footer>
        <button class="btn btn-secondary" @click="reinitializeDialog = false">Cancel</button>
        <button class="btn btn-primary" @click="confirmReinitialize">Re-initialize</button>
      </template>
    </GenericModal>

    <LoadingState v-if="loading" message="Loading heap dump status..." />

    <ErrorState v-else-if="error" :message="error" />

    <div v-else>
      <PageHeader
        title="Heap Dump Overview"
        description="Memory analysis summary and processing status"
        icon="bi-memory"
      >
        <template v-if="heapExists && !processing" #actions>
          <button
            v-if="initializedOnce"
            class="btn btn-outline-primary btn-sm"
            @click="openReinitialize"
          >
            <i class="bi bi-arrow-repeat me-1"></i>Re-initialize
          </button>
          <button
            v-if="!isHeapDumpOnlyProfile"
            class="btn btn-outline-danger btn-sm"
            @click="deleteHeapDumpDialog = true"
          >
            <i class="bi bi-trash me-1"></i>Delete heap dump
          </button>
        </template>
      </PageHeader>

      <!-- A standalone heap dump whose copy is gone: only a new Analyze can bring it back. -->
      <EmptyState
        v-if="!heapExists && isHeapDumpOnlyProfile"
        icon="bi-file-earmark-x"
        title="Heap dump file is missing"
        description="Analyze the recording again on the Recordings page to rebuild this profile."
      >
        <template #action>
          <router-link to="/recordings" class="btn btn-primary btn-sm">
            <i class="bi bi-arrow-right me-1"></i>Go to Recordings
          </router-link>
        </template>
      </EmptyState>

      <!-- A JFR profile's heap dump: uploaded here, since it has no Recordings row. -->
      <div
        v-else-if="!heapExists"
        ref="dropzoneRef"
        class="upload-dropzone p-4"
        :class="{ active: dragActive, 'has-file': uploadFile, clickable: !uploadFile }"
        tabindex="0"
        @dragover="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
        @click="handleDropzoneClick"
        @keyup.enter="handleEnterKey"
      >
        <div v-if="!uploadFile" class="text-center py-4">
          <i class="bi bi-cloud-upload upload-icon text-primary mb-3"></i>
          <h5>Drag &amp; Drop Heap Dump Here</h5>
          <p class="text-muted small mb-3">or click anywhere to browse files</p>
          <input
            id="heapDumpFileInput"
            ref="fileInputRef"
            type="file"
            class="d-none"
            accept=".hprof,.hprof.gz"
            @change="handleFileSelect"
          />
          <label for="heapDumpFileInput" class="btn btn-primary" @click.stop>
            <i class="bi bi-folder me-2"></i>Browse Files
          </label>
        </div>

        <div v-else>
          <div class="d-flex justify-content-between mb-3">
            <h6 class="mb-0"><i class="bi bi-file-earmark-binary me-2"></i>Selected File</h6>
            <div>
              <button
                class="btn btn-success btn-sm me-2"
                :disabled="uploading"
                @click.stop="uploadHeapDump"
              >
                <i class="bi bi-cloud-upload me-1"></i>Upload
              </button>
              <button
                class="btn btn-outline-secondary btn-sm"
                :disabled="uploading"
                @click.stop="removeFile"
              >
                <i class="bi bi-x-lg me-1"></i>Clear
              </button>
            </div>
          </div>

          <div class="file-item p-2">
            <div class="d-flex justify-content-between align-items-center">
              <div class="d-flex align-items-center">
                <i class="bi bi-file-earmark-binary text-primary me-2 fs-5"></i>
                <div>
                  <div class="fw-bold">{{ uploadFile.name }}</div>
                  <div class="text-muted small">
                    {{ FormattingService.formatBytes(uploadFile.size) }}
                  </div>
                </div>
              </div>
              <span v-if="!uploading" class="text-muted small"
                >Press <kbd>Enter</kbd> to upload</span
              >
            </div>

            <div v-if="uploading" class="mt-2">
              <div class="progress">
                <div
                  class="progress-bar progress-bar-striped progress-bar-animated"
                  role="progressbar"
                  :style="{ width: uploadProgress + '%' }"
                  :aria-valuenow="uploadProgress"
                  aria-valuemin="0"
                  aria-valuemax="100"
                >
                  {{ uploadProgress }}%
                </div>
              </div>
              <div class="text-center mt-1 small text-primary">
                <i class="bi bi-arrow-clockwise me-1"></i>Uploading...
              </div>
            </div>
          </div>
        </div>
      </div>

      <template v-else>
        <StatsTable v-if="lastSummary" :metrics="summaryMetrics" class="mb-4" />

        <HeapDumpInitTimeline v-if="processing" :steps="processingSteps" :tick-now="tickNow" />

        <!-- Not built, and nothing running: a first run for an attached dump, or a failed one. -->
        <EmptyState
          v-else-if="!cacheReady"
          icon="bi-cpu"
          :title="lastRunFailed ? 'Heap Dump Initialization Failed' : 'Heap Dump Not Initialized'"
          :description="notInitializedDescription"
        >
          <template #action>
            <button class="btn btn-primary btn-sm" @click="initializeHeapDump">
              <i class="bi bi-lightning-charge-fill me-1"></i>
              {{ lastRunFailed ? 'Try again' : 'Initialize' }}
            </button>
          </template>
        </EmptyState>

        <HeapDumpInitTimeline
          v-if="!processing && lastInitResult"
          class="last-init-result"
          :steps="lastInitResultSteps"
          :tick-now="0"
          title="Last Initialization Result"
          :subtitle="lastInitResultSubtitle"
          :show-progress-bar="false"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapDumpInitTimeline, { type TimelineStep } from '@/components/HeapDumpInitTimeline.vue';
import HeapSummary from '@/services/api/model/HeapSummary';
import HeapDumpConfig from '@/services/api/model/HeapDumpConfig';
import type InitPipelineResult from '@/services/api/model/InitPipelineResult';
import type HeapDumpInitProgress from '@/services/api/model/HeapDumpInitProgress';
import RecordingEventSource from '@hubs/services/api/model/RecordingEventSource.ts';
import FormattingService from '@shared/services/FormattingService';
import { ToastService } from '@shared/services/ToastService';
import MessageBus from '@/services/MessageBus';
import { profileStore } from '@/stores/profileStore';

const route = useRoute();

const profileId = route.params.profileId as string;

const client = new HeapDumpClient(profileId);

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const lastSummary = ref<HeapSummary | null>(null);
const heapConfig = ref<HeapDumpConfig | null>(null);
const processing = ref(false);

/** A heap-dump-only profile was built from a Recordings row; a JFR profile only carries a dump. */
const isHeapDumpOnlyProfile = computed(
  () => profileStore.currentProfile.value?.eventSource === RecordingEventSource.HEAP_DUMP
);

// --- Re-initialization -------------------------------------------------------------------------

type CompressedOopsChoice = 'auto' | 'enabled' | 'disabled';

interface CompressedOopsOption {
  value: CompressedOopsChoice;
  title: string;
  detail: string;
}

const COMPRESSED_OOPS_OPTIONS: CompressedOopsOption[] = [
  { value: 'auto', title: 'Auto-detect', detail: 'Recommended' },
  { value: 'enabled', title: 'Force enabled', detail: 'References are 4 bytes (heap under 32 GiB)' },
  {
    value: 'disabled',
    title: 'Force disabled',
    detail: 'References are 8 bytes (large heap or -XX:-UseCompressedOops)'
  }
];

const COMPRESSED_OOPS_OVERRIDE: Record<CompressedOopsChoice, boolean | undefined> = {
  auto: undefined,
  enabled: true,
  disabled: false
};

const reinitializeDialog = ref(false);
const compressedOopsChoice = ref<CompressedOopsChoice>('auto');

/** The auto-detect option says what the last run detected, so forcing it is an informed choice. */
const optionDetail = (option: CompressedOopsOption): string => {
  if (option.value !== 'auto' || !heapConfig.value) {
    return option.detail;
  }
  const detected = heapConfig.value.compressedOops ? 'Enabled' : 'Disabled';
  return `${option.detail} · ${detected} last time`;
};

// --- Pipeline steps — see HeapDumpInitTimeline.vue for layout / labels --------------------------

const STAGE_IDS: string[] = [
  'load',
  'parse',
  'index',
  'strings',
  'dominator',
  'threads',
  'biggest',
  'collections',
  'leaks',
  'classloaders',
  'biggest-collections',
  'consumers',
  'duplicates'
];

const pendingSteps = (): TimelineStep[] => STAGE_IDS.map(id => ({ id, status: 'pending' }));

const processingSteps = ref<TimelineStep[]>(pendingSteps());

const findStep = (id: string): TimelineStep | undefined =>
  processingSteps.value.find(step => step.id === id);

// Live tick for the in-progress elapsed display
const tickNow = ref(Date.now());
let tickInterval: ReturnType<typeof setInterval> | null = null;
const startTick = () => {
  if (tickInterval !== null) {
    return;
  }
  tickNow.value = Date.now();
  tickInterval = setInterval(() => {
    tickNow.value = Date.now();
  }, 250);
};
const stopTick = () => {
  if (tickInterval !== null) {
    clearInterval(tickInterval);
    tickInterval = null;
  }
};

// --- Last run ---------------------------------------------------------------------------------

const lastInitResult = ref<InitPipelineResult | null>(null);

const lastRunFailed = computed(() => lastInitResult.value?.state === 'failed');

/** Whether a run has ever finished, so there is something to re-initialize. */
const initializedOnce = computed(() => cacheReady.value || lastInitResult.value !== null);

const lastInitResultSteps = computed<TimelineStep[]>(() => {
  if (!lastInitResult.value) {
    return [];
  }
  return lastInitResult.value.stages.map(stage => ({
    id: stage.id,
    status: stage.status as TimelineStep['status'],
    durationMs: stage.durationMs ?? undefined,
    subPhases: stage.subPhases ?? undefined
  }));
});

const lastInitResultSubtitle = computed<string>(() => {
  const result = lastInitResult.value;
  if (!result) {
    return '';
  }
  const epochMs = Date.parse(result.completedAt);
  const when = Number.isNaN(epochMs) ? '' : ` ${FormattingService.formatRelativeTime(epochMs)}`;
  // Failed runs are stored too — reading every stored run as a success would tell the user a broken
  // initialization completed.
  if (result.state === 'failed') {
    const reason = result.errorMessage ? ` — ${result.errorMessage}` : '';
    return `Failed${when}${reason}`;
  }
  return when ? `Completed${when}` : '';
});

const notInitializedDescription = computed<string>(() => {
  if (lastRunFailed.value) {
    return lastInitResult.value?.errorMessage ?? 'The last initialization did not finish.';
  }
  if (isHeapDumpOnlyProfile.value) {
    return 'Heap dumps are initialized from the Recordings page. This one has not been built yet.';
  }
  return 'Build the index to enable the memory analysis pages.';
});

// --- Summary ----------------------------------------------------------------------------------

const summaryMetrics = computed(() => {
  if (!lastSummary.value) {
    return [];
  }
  return [
    {
      icon: 'collection',
      title: 'Total Objects',
      value: FormattingService.formatNumber(lastSummary.value.totalInstances),
      variant: 'highlight' as const
    },
    {
      icon: 'hdd',
      title: 'Heap Size',
      value: FormattingService.formatBytes(lastSummary.value.totalBytes),
      variant: 'info' as const
    },
    {
      icon: 'layers',
      title: 'Classes',
      value: FormattingService.formatNumber(lastSummary.value.classCount),
      variant: 'info' as const,
      breakdown: [
        { label: 'GC Roots', value: FormattingService.formatNumber(lastSummary.value.gcRootCount) }
      ]
    },
    {
      icon: 'gear',
      title: 'Compressed Oops',
      value: heapConfig.value ? (heapConfig.value.compressedOops ? 'Enabled' : 'Disabled') : '—',
      variant: 'success' as const
    }
  ];
});

// --- Following a run --------------------------------------------------------------------------

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const POLL_INTERVAL_MS = 750;

/** Consecutive idle polls tolerated before treating the run as lost (e.g. backend restart). */
const MAX_IDLE_POLLS = 5;

let unmounted = false;

/** Maps backend per-stage statuses onto the live timeline steps. */
const applyProgress = (progress: HeapDumpInitProgress) => {
  for (const stage of progress.stages) {
    const step = findStep(stage.id);
    if (!step) {
      continue;
    }
    if (stage.status === 'in_progress') {
      if (step.status !== 'in_progress') {
        step.status = 'in_progress';
        // Backdate by the backend-measured elapsed time so a page reopened
        // mid-run resumes the stage timer instead of restarting it from zero.
        step.startMs = Date.now() - (stage.elapsedMs ?? 0);
      }
    } else if (stage.status === 'completed') {
      step.status = 'completed';
      step.durationMs = stage.durationMs ?? undefined;
      step.subPhases = stage.subPhases ?? undefined;
    }
    // 'failed' stages stay visually pending; the stored result reports the failure.
  }
};

const loadBuiltState = async () => {
  cacheReady.value = await client.isCacheReady();
  if (cacheReady.value) {
    lastSummary.value = await client.getSummary();
    heapConfig.value = await client.getConfig();
  }
  try {
    lastInitResult.value = await client.getInitPipelineResult();
  } catch (snapshotErr) {
    // A missing or unreadable snapshot only hides the last-run timeline.
    console.warn('Failed to load init pipeline result', snapshotErr);
    lastInitResult.value = null;
  }
};

/**
 * Mirrors the backend run until it reaches a terminal state, then loads what it built. Used right
 * after starting a run here and when the page opens on a run started elsewhere (the Recordings row).
 */
const followInitRun = async () => {
  processing.value = true;
  processingSteps.value = pendingSteps();
  startTick();

  try {
    let idlePolls = 0;
    let failedPolls = 0;
    let progress = await client.getInitProgress();
    while (!unmounted && (progress.state === 'running' || progress.state === 'idle')) {
      if (progress.state === 'idle') {
        idlePolls++;
        if (idlePolls > MAX_IDLE_POLLS) {
          throw new Error('Initialization run was lost — please try again.');
        }
      } else {
        idlePolls = 0;
        applyProgress(progress);
      }
      await sleep(POLL_INTERVAL_MS);
      try {
        progress = await client.getInitProgress();
        failedPolls = 0;
      } catch {
        // A failed poll is not a failed run. Only a server that stays unreachable ends the watch.
        failedPolls++;
        if (failedPolls > MAX_IDLE_POLLS) {
          throw new Error('Lost contact with the server while initializing — please reload.');
        }
      }
    }
    if (unmounted) {
      return;
    }
    applyProgress(progress);
    await loadBuiltState();
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, cacheReady.value);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to process heap dump';
  } finally {
    stopTick();
    processing.value = false;
  }
};

/** First run, or a retry after a failed one: compressed oops auto-detected. */
const initializeHeapDump = async () => {
  try {
    await client.initializeAll();
  } catch {
    // Toast shown by HttpInterceptor
    return;
  }
  await followInitRun();
};

const openReinitialize = () => {
  compressedOopsChoice.value = 'auto';
  reinitializeDialog.value = true;
};

const confirmReinitialize = async () => {
  reinitializeDialog.value = false;
  try {
    await client.reinitialize(COMPRESSED_OOPS_OVERRIDE[compressedOopsChoice.value]);
  } catch {
    // Toast shown by HttpInterceptor
    return;
  }
  // The index is gone until the new run builds it again: the analysis pages wait for it.
  cacheReady.value = false;
  lastSummary.value = null;
  MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, false);
  await followInitRun();
};

// --- Attached heap dump: delete and upload ----------------------------------------------------

const deleteHeapDumpDialog = ref(false);

const confirmDeleteHeapDump = async () => {
  try {
    await client.deleteHeapDump();
    heapExists.value = false;
    cacheReady.value = false;
    lastSummary.value = null;
    heapConfig.value = null;
    lastInitResult.value = null;
    MessageBus.emit(MessageBus.HEAP_DUMP_STATUS_CHANGED, false);
    ToastService.success(
      'Heap Dump Deleted',
      'Heap dump and cache have been removed successfully.'
    );
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to delete heap dump';
  }
};

const uploadFile = ref<File | null>(null);
const uploading = ref(false);
const uploadProgress = ref(0);
const dragActive = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);
const dropzoneRef = ref<HTMLDivElement | null>(null);

const isValidHeapDumpFile = (filename: string): boolean => {
  const lower = filename.toLowerCase();
  return lower.endsWith('.hprof') || lower.endsWith('.hprof.gz');
};

const selectFile = (file: File) => {
  if (isValidHeapDumpFile(file.name)) {
    uploadFile.value = file;
    nextTick(() => dropzoneRef.value?.focus());
  } else {
    ToastService.warn('Invalid File', 'Only .hprof and .hprof.gz files are supported');
  }
};

const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (input.files && input.files.length > 0) {
    selectFile(input.files[0]);
  }
};

const handleDragOver = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = true;
};

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
};

const handleDrop = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
  if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
    selectFile(event.dataTransfer.files[0]);
  }
};

const handleDropzoneClick = () => {
  if (!uploadFile.value && fileInputRef.value) {
    fileInputRef.value.click();
  }
};

const handleEnterKey = () => {
  if (uploadFile.value && !uploading.value) {
    uploadHeapDump();
  }
};

const removeFile = () => {
  uploadFile.value = null;
};

const uploadHeapDump = async () => {
  if (!uploadFile.value) {
    return;
  }

  uploading.value = true;
  uploadProgress.value = 0;
  const progressInterval = setInterval(() => {
    if (uploadProgress.value < 90) {
      uploadProgress.value += Math.floor(Math.random() * 10) + 5;
    }
  }, 300);

  try {
    await client.uploadHeapDump(uploadFile.value);
    uploadProgress.value = 100;
    ToastService.success('Upload Complete', 'Heap dump uploaded successfully');
    uploadFile.value = null;
    await loadData();
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Failed to upload heap dump';
    ToastService.error('Upload Failed', errorMessage);
  } finally {
    clearInterval(progressInterval);
    uploading.value = false;
    uploadProgress.value = 0;
  }
};

// --- Loading ----------------------------------------------------------------------------------

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    heapExists.value = await client.exists();
    if (!heapExists.value) {
      return;
    }

    // A run may already be in flight — started from the Recordings row, or before a reload.
    const initProgress = await client.getInitProgress();
    if (initProgress.state === 'running') {
      loading.value = false;
      await followInitRun();
      return;
    }

    await loadBuiltState();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load heap dump status';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  scrollToTop();
  loadData();
});

onBeforeUnmount(() => {
  unmounted = true;
  stopTick();
});
</script>

<style scoped>
.last-init-result {
  margin-top: 2rem;
}

/* Re-initialize dialog */
.reinit-lead {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  margin-bottom: 1rem;
}

.reinit-options {
  border: 0;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.reinit-legend {
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin-bottom: 0.25rem;
}

.reinit-option {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.reinit-option:hover {
  background: var(--color-primary-lighter);
}

.reinit-option--selected {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}

.reinit-option .form-check-input {
  margin-top: 0.2rem;
  flex-shrink: 0;
}

.reinit-option-title {
  display: block;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-heading-dark);
}

.reinit-option-detail {
  display: block;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.reinit-note {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  margin: 0.75rem 0 0;
}

/* Upload dropzone (heap dump attached to a JFR profile) */
.upload-dropzone {
  border: 2px dashed var(--color-grey-border);
  border-radius: var(--radius-md);
  transition: all 0.3s ease;
  background-color: var(--color-neutral-lightest);
}

.upload-dropzone.active {
  border-color: var(--color-primary);
  background-color: var(--color-indigo-bg-light);
}

.upload-dropzone.has-file {
  border-style: solid;
  border-color: var(--color-border);
  background-color: var(--color-white);
}

.upload-dropzone.clickable {
  cursor: pointer;
}

.upload-dropzone.clickable:hover {
  border-color: var(--color-accent-blue);
  background-color: var(--color-blue-bg-light);
}

.upload-icon {
  display: inline-block;
  font-size: 3.5rem;
  line-height: 1.2;
}

.file-item {
  border: 1px solid var(--color-border-row);
  border-radius: var(--radius-sm);
  background-color: var(--color-neutral-bg);
}

.progress {
  height: 8px;
  border-radius: var(--radius-sm);
}
</style>

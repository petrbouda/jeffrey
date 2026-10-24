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

  A heap dump is initialized from its row on the Recordings page, so this page only follows a run
  that is already going and offers to start one when none has finished.
-->
<template>
  <div>
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
        </template>
      </PageHeader>

      <!-- A heap dump whose copy is gone: only a new Analyze can bring it back. -->
      <EmptyState
        v-if="!heapExists"
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

      <template v-else>
        <StatsTable v-if="lastSummary" :metrics="summaryMetrics" class="mb-4" />

        <HeapDumpInitTimeline v-if="processing" :steps="processingSteps" :tick-now="tickNow" />

        <!-- Not built, and nothing running: never initialized, or the last run failed. -->
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import HeapDumpInitTimeline, { type TimelineStep } from '@/components/HeapDumpInitTimeline.vue';
import HeapSummary from '@/services/api/model/HeapSummary';
import HeapDumpConfig from '@/services/api/model/HeapDumpConfig';
import type InitPipelineResult from '@/services/api/model/InitPipelineResult';
import type HeapDumpInitProgress from '@/services/api/model/HeapDumpInitProgress';
import FormattingService from '@shared/services/FormattingService';
import MessageBus from '@/services/MessageBus';

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

// --- Re-initialization -------------------------------------------------------------------------

type CompressedOopsChoice = 'auto' | 'enabled' | 'disabled';

interface CompressedOopsOption {
  value: CompressedOopsChoice;
  title: string;
  detail: string;
}

const COMPRESSED_OOPS_OPTIONS: CompressedOopsOption[] = [
  { value: 'auto', title: 'Auto-detect', detail: 'Recommended' },
  {
    value: 'enabled',
    title: 'Force enabled',
    detail: 'References are 4 bytes (heap under 32 GiB)'
  },
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
  return 'Heap dumps are initialized from the Recordings page. This one has not been built yet.';
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
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
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
</style>

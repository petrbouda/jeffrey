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
  <ConfirmationDialog
    v-model:show="clearResultsDialog"
    title="Clear Advisor Results"
    message="Are you sure you want to clear the Advisor results for this profile?"
    sub-message="This removes every report, its claims, the kept run timeline and the cached profile summaries. Running the Advisor again costs a fresh AI analysis per event type. This action cannot be undone."
    confirm-label="Clear"
    confirm-button-id="clearAdvisorResultsButton"
    modal-id="clearAdvisorResultsModal"
    @confirm="clearResults"
  />

  <LoadingState v-if="loading" message="Loading advisor..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Advisor"
    description="Point the Advisor at your code and it analyzes every profile — mapping the hottest frames to real files, with each cited frame checked against the measured call tree."
    icon="bi-lightbulb"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <!-- A run is in flight: the live, phased timeline. -->
    <template v-else-if="isRunning && batch">
      <div class="run-toolbar">
        <span class="run-note">Analyzing every profile — you can leave and come back.</span>
        <button type="button" class="btn ghost" @click="cancel">Cancel</button>
      </div>
      <ProcessingTimeline
        :phases="livePhases"
        :steps="liveStepsData"
        :tick-now="now"
        title="Analyzing your profiles"
        subtitle="One report per event type — grounding every cited frame against the measured call tree."
      />
    </template>

    <!-- The run failed for every event type: keep the timeline visible and say why, instead of
         collapsing to the first-run setup screen as if nothing had happened. -->
    <template v-else-if="batchFailed && batch">
      <div class="run-toolbar failed">
        <span class="run-note">
          <i class="bi bi-exclamation-triangle"></i>
          The run failed for every event type{{ failedReason ? ` — ${failedReason}` : '' }}
        </span>
        <button type="button" class="btn ghost" :disabled="!sourceConfigured" @click="run()">
          <i class="bi bi-arrow-repeat"></i>
          Try again
        </button>
      </div>
      <ProcessingTimeline
        :phases="livePhases"
        :steps="liveStepsData"
        :tick-now="0"
        title="Run failed"
        subtitle="How far each event type got before it failed."
        :show-progress-bar="false"
      />
    </template>

    <!-- A run has finished: the kept, static timeline with measured times. -->
    <template v-else-if="runResult">
      <div class="run-toolbar">
        <span class="src">
          <i class="bi bi-folder2-open"></i>
          <span class="src-path">{{ settings?.sourcePath || 'No source folder' }}</span>
        </span>
        <router-link :to="findingsPath" class="btn">
          <i class="bi bi-lightbulb"></i>
          View findings
        </router-link>
        <button type="button" class="btn ghost" :disabled="!sourceConfigured" @click="run()">
          <i class="bi bi-arrow-repeat"></i>
          Re-run
        </button>
        <button type="button" class="btn ghost" @click="clearResultsDialog = true">
          <i class="bi bi-trash"></i>
          Clear results
        </button>
      </div>
      <ProcessingTimeline
        :phases="resultPhases"
        :steps="resultStepsData"
        :tick-now="0"
        title="Last run"
        :subtitle="resultSubtitle"
        :show-progress-bar="false"
      />
    </template>

    <!-- Nothing yet: set a source folder and launch a run over every type. -->
    <template v-else>
      <div class="setup">
        <div class="setup-label">Source folder <span class="req">*</span></div>
        <div class="field-row">
          <label class="input-wrap">
            <span class="fic"><i class="bi bi-folder2-open"></i></span>
            <input
              v-model="folderInput"
              type="text"
              spellcheck="false"
              autocomplete="off"
              placeholder="/home/you/projects/order-service"
            />
          </label>
          <button type="button" class="btn primary" :disabled="!canRun" :title="runTitle" @click="onRun">
            <i class="bi bi-stars"></i>
            Run Advisor
          </button>
        </div>
        <p class="setup-hint" :class="{ warn: !folderReady }">
          <i class="bi" :class="folderReady ? 'bi-check-circle' : 'bi-folder2-open'"></i>
          <span v-if="folderReady">Ready — press Run to analyze {{ analyzeCountLabel }}.</span>
          <span v-else>Set a source folder to enable the run — the Advisor reads your working copy in place.</span>
        </p>
      </div>

      <p class="picker-cap">What it will analyze</p>
      <div class="ready-grid">
        <div
          v-for="type in eventTypes"
          :key="type.eventType"
          class="ready-card"
          :style="eventTypeVars(type.eventType)"
        >
          <div class="rc-top">
            <span class="isq"><i class="bi" :class="eventTypeStyle(type.eventType).icon"></i></span>
            <span class="rc-name">{{ type.label }}</span>
            <span class="rc-ready">Ready</span>
          </div>
          <div v-if="eventTypeDescription(type.eventType)" class="rc-desc">
            {{ eventTypeDescription(type.eventType) }}
          </div>
          <div class="rc-code">{{ type.eventType }}</div>
        </div>
      </div>

      <EmptyState
        v-if="eventTypes.length === 0"
        icon="bi-lightbulb"
        title="Nothing to analyze"
        description="This profile has no event types the Advisor can work with."
      />
    </template>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import ProcessingTimeline from '@shared/components/ProcessingTimeline.vue';
import FormattingService from '@shared/services/FormattingService';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import {
  eventTypeDescription,
  eventTypeStyle,
  eventTypeVars
} from '@/views/profiles/detail/advisor/eventTypeStyle';
import {
  liveSteps,
  resultSteps,
  timelinePhases,
  type TimelineType
} from '@/views/profiles/detail/advisor/advisorTimeline';
import { useAdvisor } from '@/composables/useAdvisor';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures?: FeatureType[] }>();

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  loading,
  error,
  eventTypes,
  settings,
  batch,
  runResult,
  now,
  lastSyncAt,
  isRunning,
  sourceConfigured,
  load,
  run,
  cancel,
  clearResults,
  saveSourceFolder
} = useAdvisor(profileId);

const clearResultsDialog = ref(false);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const findingsPath = computed(() => `/profiles/${profileId}/advisor/findings`);

const labelFor = (code: string | null): string =>
  eventTypes.value.find(type => type.eventType === code)?.label ?? code ?? 'Unknown';

const liveTypes = computed<TimelineType[]>(() =>
  (batch.value?.types ?? []).map(type => ({
    eventType: type.eventType ?? '',
    label: labelFor(type.eventType)
  }))
);
const livePhases = computed(() => timelinePhases(liveTypes.value));
const liveStepsData = computed(() => liveSteps(batch.value?.types ?? [], lastSyncAt.value));

const batchFailed = computed(() => batch.value?.status === 'FAILED');

/** The first per-type error, as the failure note — one reason is enough to act on. */
const failedReason = computed(
  () => batch.value?.types.find(type => type.errorMessage)?.errorMessage ?? null
);

const resultTypes = computed<TimelineType[]>(() =>
  (runResult.value?.types ?? []).map(type => ({
    eventType: type.eventType,
    label: labelFor(type.eventType)
  }))
);
const resultPhases = computed(() => timelinePhases(resultTypes.value));
const resultStepsData = computed(() => resultSteps(runResult.value?.types ?? []));

const resultSubtitle = computed(() =>
  runResult.value
    ? `Finished in ${FormattingService.formatDurationMillisCompact(runResult.value.totalElapsedMs)} · ${runResult.value.completedTypes} of ${runResult.value.totalTypes} analyzed`
    : ''
);

const folderInput = ref('');
const folderReady = computed(() => folderInput.value.trim().length > 0);
const canRun = computed(() => folderReady.value && !isRunning.value);

const runTitle = computed(() =>
  folderReady.value
    ? 'Analyze every profile against your source folder'
    : 'Set a source folder first'
);

const analyzeCountLabel = computed(() => {
  const count = eventTypes.value.length;
  return count === 1 ? '1 profile' : `all ${count} profiles`;
});

const onRun = async (): Promise<void> => {
  const path = folderInput.value.trim();
  if (!path) {
    return;
  }
  if (path !== (settings.value?.sourcePath ?? '')) {
    await saveSourceFolder(path);
  }
  await run();
};

onMounted(async () => {
  await load();
  folderInput.value = settings.value?.sourcePath ?? '';
});
</script>

<style scoped>
.run-toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.run-toolbar .run-note {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.run-toolbar.failed .run-note {
  color: var(--color-danger);
}

.run-toolbar .src {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.run-toolbar .src-path {
  font-family: var(--font-family-monospace);
  color: var(--color-heading-dark);
  font-weight: 600;
}

.btn {
  appearance: none;
  cursor: pointer;
  border: none;
  border-radius: var(--radius-base);
  background: var(--color-primary);
  color: var(--color-white);
  font-weight: 600;
  font-size: 0.85rem;
  padding: 0.5rem 1rem;
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.15s;
}

.btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.btn.primary {
  font-weight: 650;
}

.btn.ghost {
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary-border-light);
}

.btn.ghost:hover:not(:disabled) {
  background: var(--color-primary-light);
}

.run-toolbar .btn.ghost {
  margin-left: auto;
}

.btn:disabled {
  background: var(--color-border);
  color: var(--color-text-muted);
  cursor: not-allowed;
  border-color: transparent;
}

/* source folder setup */
.setup {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  padding: 1.05rem 1.15rem;
  margin-bottom: 1.3rem;
  box-shadow: var(--shadow-sm);
}

.setup-label {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  margin-bottom: 0.55rem;
}

.setup-label .req {
  color: var(--color-danger);
}

.field-row {
  display: flex;
  gap: 0.6rem;
  align-items: stretch;
  flex-wrap: wrap;
}

.input-wrap {
  position: relative;
  flex: 1 1 320px;
  display: flex;
  align-items: center;
}

.input-wrap .fic {
  position: absolute;
  left: 0.65rem;
  color: var(--color-text-light);
  pointer-events: none;
}

.setup input {
  width: 100%;
  font-family: var(--font-family-monospace);
  font-size: 0.84rem;
  color: var(--color-heading-dark);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  padding: 0.55rem 0.7rem 0.55rem 2.1rem;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.setup input::placeholder {
  color: var(--color-text-light);
}

.setup input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.setup-hint {
  margin: 0.6rem 0 0;
  font-size: 0.79rem;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.setup-hint.warn {
  color: var(--color-amber-text);
}

/* ready cards */
.picker-cap {
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin: 0 0 0.7rem;
}

.ready-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 14px;
}

.ready-card {
  display: flex;
  flex-direction: column;
  padding: 16px 16px 14px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--et);
  border-radius: var(--radius-md);
}

.rc-top {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
}

.isq {
  width: 38px;
  height: 38px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-base);
  color: var(--et);
  background: var(--et-light);
}

.rc-name {
  font-size: 0.98rem;
  font-weight: 700;
  color: var(--color-heading-dark);
}

.rc-ready {
  margin-left: auto;
  font-size: 0.68rem;
  font-weight: 650;
  color: var(--et);
  background: var(--et-light);
  padding: 2px 9px;
  border-radius: 999px;
}

.rc-desc {
  font-size: 0.79rem;
  color: var(--color-text-muted);
  margin-bottom: 0.7rem;
}

.rc-code {
  margin-top: auto;
  font-family: var(--font-family-monospace);
  font-size: 0.68rem;
  color: var(--color-text-muted);
}
</style>

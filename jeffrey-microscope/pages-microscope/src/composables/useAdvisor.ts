/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { computed, onUnmounted, ref } from 'vue';
import AdvisorClient from '@/services/api/AdvisorClient';
import { ADVISOR_PHASES } from '@/views/profiles/detail/advisor/advisorPipeline';
import type {
  AdvisorEventType,
  AdvisorRecommendation,
  AdvisorSettings
} from '@/services/api/model/Advisor';
import type { TimelineStep } from '@shared/components/StageTimeline.vue';
import type {
  PipelineProgress,
  PipelineRunResult,
  StageProgress
} from '@shared/services/api/model/PipelineRun';
import ToastService from '@shared/services/ToastService';

const POLL_INTERVAL_MS = 750;

/** How often the in-progress stage timer redraws. */
const TICK_INTERVAL_MS = 250;

/**
 * Consecutive idle polls tolerated before treating a run as lost. A backend restart drops the run from
 * memory, and without this the page would poll a run that no longer exists for as long as it stayed open.
 */
const MAX_IDLE_POLLS = 5;

const STAGE_IDS: string[] = ADVISOR_PHASES.flatMap(phase => phase.stages.map(stage => stage.id));

/**
 * Drives one profile's Advisor pages: what can be analyzed, what has been analyzed, and where a run in
 * flight has got to.
 *
 * <p>A run is watched by polling rather than a stream, the same way heap-dump initialization is. The
 * trade is deliberate: the run reports a handful of coarse stages over several minutes, so polling shows
 * everything a stream would, without a connection to keep alive through a sleeping laptop or a proxy
 * timeout.</p>
 */
export function useAdvisor(profileId: string) {
  const client = new AdvisorClient(profileId);

  const loading = ref(true);
  const error = ref<string | null>(null);
  const eventTypes = ref<AdvisorEventType[]>([]);
  const recommendations = ref<AdvisorRecommendation[]>([]);
  const settings = ref<AdvisorSettings | null>(null);
  const progress = ref<PipelineProgress | null>(null);
  const storedRuns = ref<PipelineRunResult[]>([]);
  const steps = ref<TimelineStep[]>(freshSteps());
  const selectedEventType = ref<string | null>(null);
  const tickNow = ref(Date.now());

  let pollTimer: ReturnType<typeof setInterval> | null = null;
  let tickTimer: ReturnType<typeof setInterval> | null = null;
  let idlePolls = 0;

  const isRunning = computed(() => progress.value?.state === 'running');

  const selectedRecommendation = computed(() =>
    recommendations.value.find(item => item.eventType === selectedEventType.value)
  );

  const sourceConfigured = computed(() => settings.value?.configured === true);

  /** The stored run for the selected profile group, so a reload still shows its stage timings. */
  const selectedRun = computed(() =>
    storedRuns.value.find(run => run.scopeId === selectedEventType.value)
  );

  function freshSteps(): TimelineStep[] {
    return STAGE_IDS.map(id => ({ id, status: 'pending' }));
  }

  const stopTimers = (): void => {
    if (pollTimer !== null) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    if (tickTimer !== null) {
      clearInterval(tickTimer);
      tickTimer = null;
    }
  };

  const startTimers = (): void => {
    stopTimers();
    idlePolls = 0;
    tickNow.value = Date.now();
    tickTimer = setInterval(() => {
      tickNow.value = Date.now();
    }, TICK_INTERVAL_MS);
    pollTimer = setInterval(refreshProgress, POLL_INTERVAL_MS);
  };

  /**
   * Maps backend per-stage statuses onto the timeline steps. A stage that has just started is backdated
   * by the backend-measured elapsed time, so a page opened mid-run resumes the stage timer instead of
   * restarting it from zero.
   */
  const applyProgress = (latest: PipelineProgress): void => {
    for (const stage of latest.stages) {
      const step = steps.value.find(candidate => candidate.id === stage.id);
      if (!step) {
        continue;
      }
      applyStage(step, stage);
    }
  };

  const applyStage = (step: TimelineStep, stage: StageProgress): void => {
    if (stage.status === 'in_progress') {
      if (step.status !== 'in_progress') {
        step.status = 'in_progress';
        step.startMs = Date.now() - (stage.elapsedMs ?? 0);
      }
      return;
    }
    if (stage.status === 'completed' || stage.status === 'skipped') {
      step.status = stage.status;
      step.durationMs = stage.durationMs ?? undefined;
      step.subPhases = stage.subPhases ?? undefined;
    }
    // A failed stage stays visually pending; the error banner reports the failure.
  };

  const refreshProgress = async (): Promise<void> => {
    try {
      const latest = await client.progress();

      if (latest.state === 'idle') {
        // The backend forgot the run — almost always a restart. Give it a few polls in case the run
        // simply has not registered yet, then stop rather than polling a ghost forever.
        idlePolls += 1;
        if (idlePolls >= MAX_IDLE_POLLS) {
          stopTimers();
          progress.value = latest;
        }
        return;
      }

      idlePolls = 0;
      progress.value = latest;
      applyProgress(latest);

      if (latest.state === 'completed') {
        stopTimers();
        // The run stores its artifacts in the profile database, so the result arrives by re-reading
        // them rather than by being carried on the progress snapshot.
        [recommendations.value, storedRuns.value] = await Promise.all([
          client.recommendations(),
          client.runs()
        ]);
        ToastService.success('Advisor', 'Recommendations are ready');
      } else if (latest.state === 'failed') {
        stopTimers();
        storedRuns.value = await client.runs();
        ToastService.error('Advisor', latest.errorMessage ?? 'The advisor run failed');
      }
    } catch {
      // A failed poll is not a failed run: keep polling and let the next tick settle it.
    }
  };

  const load = async (): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      const [types, results, currentSettings, currentProgress, runs] = await Promise.all([
        client.eventTypes(),
        client.recommendations(),
        client.settings(),
        client.progress(),
        client.runs()
      ]);

      eventTypes.value = types;
      recommendations.value = results;
      settings.value = currentSettings;
      progress.value = currentProgress;
      storedRuns.value = runs;

      // Prefer a profile group that already has a result, so a returning user lands on something to
      // read rather than on an empty first tab.
      selectedEventType.value =
        results[0]?.eventType ?? currentProgress.scopeId ?? types[0]?.eventType ?? null;

      steps.value = freshSteps();
      applyProgress(currentProgress);
      if (isRunning.value) {
        startTimers();
      }
    } catch (e: any) {
      error.value = e?.response?.data?.message ?? e?.message ?? 'Failed to load the Advisor';
    } finally {
      loading.value = false;
    }
  };

  const generate = async (eventType: string): Promise<void> => {
    try {
      const response = await client.generate(eventType);
      progress.value = response.progress;
      selectedEventType.value = eventType;
      steps.value = freshSteps();
      if (!response.started) {
        ToastService.info('Advisor', 'A run is already in progress for this profile');
      }
      startTimers();
    } catch (e: any) {
      ToastService.error(
        'Advisor',
        e?.response?.data?.message ?? 'Could not start the advisor run'
      );
    }
  };

  const cancel = async (): Promise<void> => {
    try {
      await client.cancel();
      await refreshProgress();
    } catch {
      ToastService.error('Advisor', 'Could not cancel the advisor run');
    }
  };

  onUnmounted(stopTimers);

  return {
    loading,
    error,
    eventTypes,
    recommendations,
    settings,
    progress,
    steps,
    tickNow,
    selectedEventType,
    selectedRecommendation,
    selectedRun,
    sourceConfigured,
    isRunning,
    load,
    generate,
    cancel
  };
}

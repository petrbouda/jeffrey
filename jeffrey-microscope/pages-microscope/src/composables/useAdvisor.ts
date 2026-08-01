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
import type {
  AdvisorEventType,
  AdvisorRecommendation,
  AdvisorRunResult,
  AdvisorSettings,
  BatchAdvisorProgress
} from '@/services/api/model/Advisor';
import ToastService from '@shared/services/ToastService';

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const POLL_INTERVAL_MS = 750;
const TICK_INTERVAL_MS = 250;

/**
 * Consecutive idle polls tolerated before treating the run as lost. A backend restart drops the batch
 * from memory and progress starts answering "no batch"; without this the page would poll a run that no
 * longer exists for as long as it stayed open.
 */
const MAX_IDLE_POLLS = 5;

/**
 * Drives one profile's Advisor page: what can be analyzed, what has been analyzed, and the state of a
 * batch run in flight — one launch that processes every event type at once.
 *
 * <p>The run is watched by polling rather than a stream, matching how heap-dump initialization is
 * followed — same interval, same idle tolerance, and the same sequential loop rather than a timer, so
 * a slow poll delays the next one instead of stacking up behind it. The trade is deliberate: the run
 * reports a handful of coarse stages per type over several minutes, so polling shows everything a
 * stream would, without a connection to keep alive through a sleeping laptop or a proxy timeout.</p>
 */
export function useAdvisor(profileId: string) {
  const client = new AdvisorClient(profileId);

  const loading = ref(true);
  const error = ref<string | null>(null);
  const eventTypes = ref<AdvisorEventType[]>([]);
  const recommendations = ref<AdvisorRecommendation[]>([]);
  const settings = ref<AdvisorSettings | null>(null);
  const batch = ref<BatchAdvisorProgress | null>(null);
  const runResult = ref<AdvisorRunResult | null>(null);
  const selectedEventType = ref<string | null>(null);
  // Ticks while a run is in flight so the live step timers count up smoothly between polls.
  const now = ref(Date.now());
  // Wall-clock at the last poll, so an in-progress step's start can be back-dated from its elapsed time
  // and the timer then advances with `now` between polls instead of snapping back each poll.
  const lastSyncAt = ref(Date.now());

  const syncBatch = (value: BatchAdvisorProgress): void => {
    batch.value = value;
    lastSyncAt.value = Date.now();
  };

  // Polling is a loop rather than a timer, so this flag is what ends it.
  let polling = false;
  let tickTimer: ReturnType<typeof setInterval> | null = null;

  const isRunning = computed(
    () => batch.value?.status === 'QUEUED' || batch.value?.status === 'RUNNING'
  );

  const hasResults = computed(() => recommendations.value.length > 0);

  const selectedRecommendation = computed(() =>
    recommendations.value.find(item => item.eventType === selectedEventType.value)
  );

  const sourceConfigured = computed(() => settings.value?.configured === true);

  const stopPolling = (): void => {
    polling = false;
    if (tickTimer !== null) {
      clearInterval(tickTimer);
      tickTimer = null;
    }
  };

  const pickSelected = (): void => {
    selectedEventType.value =
      recommendations.value[0]?.eventType ??
      selectedEventType.value ??
      eventTypes.value[0]?.eventType ??
      null;
  };

  /** Applies one progress snapshot, returning true while the run is still going. */
  const applyProgress = async (latest: BatchAdvisorProgress): Promise<boolean> => {
    syncBatch(latest);

    if (latest.status === 'COMPLETED') {
      // Each type stores its artifacts in the profile database, so results arrive by re-reading them
      // rather than by being carried on the progress snapshot. The stored timeline is fetched too, so
      // the Overview keeps showing the phased, timed run after it finishes.
      [recommendations.value, runResult.value] = await Promise.all([
        client.recommendations(),
        client.runResult()
      ]);
      pickSelected();
      ToastService.success('Advisor', 'Recommendations are ready');
      return false;
    }
    if (latest.status === 'FAILED') {
      ToastService.error('Advisor', 'The advisor run failed for every event type');
      return false;
    }
    return true;
  };

  const refreshProgress = async (): Promise<void> => {
    try {
      if (!(await applyProgress(await client.runProgress()))) {
        stopPolling();
      }
    } catch {
      // A failed poll is not a failed run: the caller either polls again or is a one-off refresh.
    }
  };

  /**
   * Follows the run until it settles. Sequential rather than a timer, so a poll slower than the
   * interval delays the next one instead of stacking up behind it.
   */
  const pollLoop = async (): Promise<void> => {
    let idlePolls = 0;

    while (polling) {
      await sleep(POLL_INTERVAL_MS);
      if (!polling) {
        break;
      }

      try {
        const latest = await client.runProgress();

        // A null status is the backend saying it holds no batch for this profile — almost always a
        // restart. Tolerate a few in case the launch has not registered yet, then stop rather than
        // polling a run that no longer exists.
        if (latest.status === null) {
          idlePolls += 1;
          if (idlePolls > MAX_IDLE_POLLS) {
            syncBatch(latest);
            stopPolling();
            ToastService.error('Advisor', 'The advisor run was lost — please start it again.');
          }
          continue;
        }

        idlePolls = 0;
        if (!(await applyProgress(latest))) {
          stopPolling();
        }
      } catch {
        // A failed poll is not a failed run: keep polling and let the next one settle it.
      }
    }
  };

  const startPolling = (): void => {
    if (polling) {
      return;
    }
    polling = true;
    now.value = Date.now();
    tickTimer = setInterval(() => {
      now.value = Date.now();
    }, TICK_INTERVAL_MS);
    void pollLoop();
  };

  const load = async (): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      const [types, results, currentSettings, currentBatch, storedResult] = await Promise.all([
        client.eventTypes(),
        client.recommendations(),
        client.settings(),
        client.runProgress(),
        client.runResult()
      ]);

      eventTypes.value = types;
      recommendations.value = results;
      settings.value = currentSettings;
      syncBatch(currentBatch);
      runResult.value = storedResult;
      pickSelected();

      if (isRunning.value) {
        startPolling();
      }
    } catch (e: any) {
      error.value = e?.response?.data?.message ?? e?.message ?? 'Failed to load the Advisor';
    } finally {
      loading.value = false;
    }
  };

  const run = async (selectedTypes: string[] = []): Promise<void> => {
    try {
      runResult.value = null;
      syncBatch(await client.run(selectedTypes));
      startPolling();
    } catch (e: any) {
      ToastService.error('Advisor', e?.response?.data?.message ?? 'Could not start the advisor run');
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

  const saveSourceFolder = async (sourcePath: string): Promise<void> => {
    try {
      settings.value = await client.saveSettings({ sourcePath });
      ToastService.success('Advisor', 'Source folder saved');
    } catch (e: any) {
      ToastService.error('Advisor', e?.response?.data?.message ?? 'Could not save the source folder');
    }
  };

  onUnmounted(stopPolling);

  return {
    loading,
    error,
    eventTypes,
    recommendations,
    settings,
    batch,
    selectedEventType,
    selectedRecommendation,
    sourceConfigured,
    isRunning,
    hasResults,
    runResult,
    now,
    lastSyncAt,
    load,
    run,
    cancel,
    saveSourceFolder
  };
}

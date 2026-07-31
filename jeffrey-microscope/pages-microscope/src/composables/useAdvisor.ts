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
  AdvisorProgress,
  AdvisorRecommendation,
  AdvisorSettings
} from '@/services/api/model/Advisor';
import ToastService from '@shared/services/ToastService';

const POLL_INTERVAL_MS = 2000;

/**
 * Drives one profile's Advisor pages: what can be analyzed, what has been analyzed, and the state of a
 * run in flight.
 *
 * <p>A run is watched by polling rather than a stream, matching how heap-dump initialization is
 * followed. The trade is deliberate: the run reports a handful of coarse stages over several minutes,
 * so a two-second poll shows everything a stream would, without a connection to keep alive through a
 * sleeping laptop or a proxy timeout.</p>
 */
export function useAdvisor(profileId: string) {
  const client = new AdvisorClient(profileId);

  const loading = ref(true);
  const error = ref<string | null>(null);
  const eventTypes = ref<AdvisorEventType[]>([]);
  const recommendations = ref<AdvisorRecommendation[]>([]);
  const settings = ref<AdvisorSettings | null>(null);
  const progress = ref<AdvisorProgress | null>(null);
  const stages = ref<string[]>([]);
  const selectedEventType = ref<string | null>(null);

  let pollTimer: ReturnType<typeof setInterval> | null = null;

  const isRunning = computed(
    () =>
      progress.value?.status != null &&
      progress.value.status !== 'COMPLETED' &&
      progress.value.status !== 'FAILED'
  );

  const selectedRecommendation = computed(() =>
    recommendations.value.find(item => item.eventType === selectedEventType.value)
  );

  const sourceConfigured = computed(() => settings.value?.configured === true);

  const stopPolling = (): void => {
    if (pollTimer !== null) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  };

  const refreshProgress = async (): Promise<void> => {
    try {
      const latest = await client.progress();
      progress.value = latest;

      if (latest.status === 'COMPLETED') {
        stopPolling();
        // The run stores its artifacts in the profile database, so the result arrives by re-reading
        // them rather than by being carried on the progress snapshot.
        recommendations.value = await client.recommendations();
        ToastService.success('Advisor', 'Recommendations are ready');
      } else if (latest.status === 'FAILED') {
        stopPolling();
        ToastService.error('Advisor', latest.errorMessage ?? 'The advisor run failed');
      }
    } catch {
      // A failed poll is not a failed run: keep polling and let the next tick settle it.
    }
  };

  const startPolling = (): void => {
    stopPolling();
    pollTimer = setInterval(refreshProgress, POLL_INTERVAL_MS);
  };

  const load = async (): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      const [types, results, currentSettings, currentProgress, stageOrder] = await Promise.all([
        client.eventTypes(),
        client.recommendations(),
        client.settings(),
        client.progress(),
        client.stages()
      ]);

      eventTypes.value = types;
      recommendations.value = results;
      settings.value = currentSettings;
      progress.value = currentProgress;
      stages.value = stageOrder;

      // Prefer a profile group that already has a result, so a returning user lands on something to
      // read rather than on an empty first tab.
      selectedEventType.value =
        results[0]?.eventType ?? currentProgress.eventType ?? types[0]?.eventType ?? null;

      if (isRunning.value) {
        startPolling();
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
      if (!response.started) {
        ToastService.info('Advisor', 'A run is already in progress for this profile');
      }
      startPolling();
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

  onUnmounted(stopPolling);

  return {
    loading,
    error,
    eventTypes,
    recommendations,
    settings,
    progress,
    stages,
    selectedEventType,
    selectedRecommendation,
    sourceConfigured,
    isRunning,
    load,
    generate,
    cancel
  };
}

/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ref, onBeforeMount } from 'vue';
import { useRoute } from 'vue-router';

import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';
import EventSummariesClient from '@/services/api/EventSummariesClient';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import GraphType from '@/services/flamegraphs/GraphType';

/**
 * Loads the backend-produced flamegraph card grid as a flat, ordered {@link FlamegraphPanel} list. The
 * backend decides which panels a profile shows and how each is presented, so this composable does no
 * bucketing or event-type inference — it just fetches and exposes the panels.
 *
 * @param graphMode  primary or differential (drives the default client + secondary-profile handling)
 * @param fetchPanels optional format-specific source (pprof/OTLP/span) instead of the JFR default
 */
export function useFlamegraphPanels(
  graphMode: string,
  fetchPanels?: () => Promise<FlamegraphPanel[]>
) {
  const route = useRoute();

  const loaded = ref(false);
  const error = ref<string | null>(null);
  const panels = ref<FlamegraphPanel[]>([]);

  async function loadPanels() {
    const profileId = route.params.profileId as string;

    // Back to loading on every call, not just the first. A reload — switching a flamegraph's scope,
    // say — otherwise left the previous scope's panels on screen, with their counts, looking like a
    // settled answer for the scope the user had just moved to.
    loaded.value = false;
    error.value = null;

    try {
      let data: FlamegraphPanel[];

      if (fetchPanels) {
        data = await fetchPanels();
      } else if (graphMode === GraphType.DIFFERENTIAL) {
        const secondaryId = SecondaryProfileService.id();
        if (!secondaryId) {
          // No secondary profile selected — show the empty state.
          loaded.value = true;
          return;
        }
        data = await EventSummariesClient.differential(profileId, secondaryId).panels();
      } else {
        data = await EventSummariesClient.primary(profileId).panels();
      }

      panels.value = data;
      loaded.value = true;
    } catch (err) {
      console.error('Failed to load flamegraph panels:', err);
      error.value = 'Failed to load event data';
      loaded.value = true;
    }
  }

  onBeforeMount(() => {
    loadPanels();
  });

  return {
    loaded,
    error,
    panels,
    reload: loadPanels
  };
}

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

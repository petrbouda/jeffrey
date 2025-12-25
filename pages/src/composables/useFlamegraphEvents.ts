/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import { useNavigation } from '@/composables/useNavigation';
import EventSummary from '@/services/api/model/EventSummary';
import EventSummariesClient from '@/services/api/EventSummariesClient';
import EventTypes from '@/services/EventTypes';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import GraphType from '@/services/flamegraphs/GraphType';

export interface CategorizedEvents {
  executionSampleEvents: EventSummary[];
  methodTraceEvents: EventSummary[];
  objectAllocationEvents: EventSummary[];
  wallClockEvents: EventSummary[];
  blockingEvents: EventSummary[];
  nativeAllocationEvents: EventSummary[];
  nativeLeakEvents: EventSummary[];
}

export function useFlamegraphEvents(graphMode: string) {
  const route = useRoute();
  const { workspaceId, projectId } = useNavigation();

  const loaded = ref(false);
  const error = ref<string | null>(null);

  const executionSampleEvents = ref<EventSummary[]>([]);
  const methodTraceEvents = ref<EventSummary[]>([]);
  const objectAllocationEvents = ref<EventSummary[]>([]);
  const wallClockEvents = ref<EventSummary[]>([]);
  const blockingEvents = ref<EventSummary[]>([]);
  const nativeAllocationEvents = ref<EventSummary[]>([]);
  const nativeLeakEvents = ref<EventSummary[]>([]);

  function categorizeEventTypes(eventTypes: EventSummary[]) {
    // Clear existing arrays
    executionSampleEvents.value = [];
    methodTraceEvents.value = [];
    objectAllocationEvents.value = [];
    wallClockEvents.value = [];
    blockingEvents.value = [];
    nativeAllocationEvents.value = [];
    nativeLeakEvents.value = [];

    for (const event of eventTypes) {
      if (EventTypes.isExecutionEventType(event.code)) {
        executionSampleEvents.value.push(event);
      } else if (EventTypes.isMethodTraceEventType(event.code)) {
        methodTraceEvents.value.push(event);
      } else if (EventTypes.isAllocationEventType(event.code)) {
        objectAllocationEvents.value.push(event);
      } else if (EventTypes.isBlockingEventType(event.code)) {
        blockingEvents.value.push(event);
      } else if (EventTypes.isWallClock(event.code)) {
        wallClockEvents.value.push(event);
      } else if (EventTypes.isMallocAllocationEventType(event.code)) {
        nativeAllocationEvents.value.push(event);
      } else if (EventTypes.isNativeLeakEventType(event.code)) {
        nativeLeakEvents.value.push(event);
      }
    }
  }

  async function loadEvents() {
    const profileId = route.params.profileId as string;

    try {
      let data: EventSummary[];

      if (graphMode === GraphType.DIFFERENTIAL) {
        const secondaryId = SecondaryProfileService.id();
        if (!secondaryId) {
          // No secondary profile selected, set loaded to true to show empty state
          loaded.value = true;
          return;
        }
        data = await EventSummariesClient.differential(
          workspaceId.value!,
          projectId.value!,
          profileId,
          secondaryId
        );
      } else {
        data = await EventSummariesClient.primary(
          workspaceId.value!,
          projectId.value!,
          profileId
        );
      }

      categorizeEventTypes(data);
      loaded.value = true;
    } catch (err) {
      console.error('Failed to load flamegraph events:', err);
      error.value = 'Failed to load event data';
      loaded.value = true;
    }
  }

  onBeforeMount(() => {
    loadEvents();
  });

  return {
    loaded,
    error,
    executionSampleEvents,
    methodTraceEvents,
    objectAllocationEvents,
    wallClockEvents,
    blockingEvents,
    nativeAllocationEvents,
    nativeLeakEvents,
    reload: loadEvents
  };
}

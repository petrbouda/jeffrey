<template>
  <LoadingState v-if="!loaded" message="Loading subsecond data..." />

  <div v-else>
    <PageHeader
      title="Differential SubSecond Graphs"
      description="Compare time-based performance data between primary and secondary profiles at sub-second intervals"
      icon="bi-file-bar-graph"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.DIFFERENTIAL"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      route-name="subsecond"
      button-text="Show SubSecond Graph"
      :suppress-empty-placeholders="isPprofProfile"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GraphType from '@/services/flamegraphs/GraphType';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import { useFlamegraphEvents } from '@/composables/useFlamegraphEvents';
import PprofEventSummariesClient from '@/services/api/PprofEventSummariesClient';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import type Profile from '@/services/api/model/Profile';
import RecordingEventSource from '@workspaces/services/api/model/RecordingEventSource.ts';

const props = defineProps<{
  profile?: Profile;
}>();

const isPprofProfile = computed(() => props.profile?.eventSource === RecordingEventSource.PPROF);

const route = useRoute();
const profileId = route.params.profileId as string;
const fetchEvents = isPprofProfile.value
  ? () => {
      const secondaryId = SecondaryProfileService.id();
      if (!secondaryId) {
        return Promise.resolve([]);
      }
      return PprofEventSummariesClient.differential(profileId, secondaryId).events();
    }
  : undefined;

const {
  loaded,
  executionSampleEvents,
  cpuTimeSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents
} = useFlamegraphEvents(GraphType.DIFFERENTIAL, fetchEvents);
</script>

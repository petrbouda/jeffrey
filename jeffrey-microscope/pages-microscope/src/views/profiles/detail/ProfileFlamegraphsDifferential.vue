<template>
  <LoadingState v-if="!loaded" message="Loading flamegraph data..." />

  <div v-else>
    <PageHeader
      title="Differential Flamegraphs"
      description="Compare performance data between primary and secondary profiles using Flamegraphs"
      icon="bi-file-diff"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.DIFFERENTIAL"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      :suppress-empty-placeholders="isStackSampleProfile"
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
import OtelEventSummariesClient from '@/services/api/OtelEventSummariesClient';
import SecondaryProfileService from '@/services/SecondaryProfileService';
import type Profile from '@/services/api/model/Profile';
import RecordingEventSource from '@workspaces/services/api/model/RecordingEventSource.ts';

const props = defineProps<{
  profile?: Profile;
}>();

const isPprofProfile = computed(() => props.profile?.eventSource === RecordingEventSource.PPROF);
const isOtelProfile = computed(
  () => props.profile?.eventSource === RecordingEventSource.OPEN_TELEMETRY
);
const isStackSampleProfile = computed(() => isPprofProfile.value || isOtelProfile.value);

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
  : isOtelProfile.value
    ? () => {
        const secondaryId = SecondaryProfileService.id();
        if (!secondaryId) {
          return Promise.resolve([]);
        }
        return OtelEventSummariesClient.differential(profileId, secondaryId).events();
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

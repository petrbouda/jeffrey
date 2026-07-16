<template>
  <LoadingState v-if="!loaded" message="Loading flamegraph data..." />

  <div v-else class="flamegraphs-primary-container">
    <PageHeader
      title="Primary Flamegraphs"
      description="View and analyze performance data Flamegraphs"
      icon="bi-fire"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.PRIMARY"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      :blocking-events="blockingEvents"
      :native-allocation-events="nativeAllocationEvents"
      :native-leak-events="nativeLeakEvents"
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
import type Profile from '@/services/api/model/Profile';
import RecordingEventSource from '@workspaces/services/api/model/RecordingEventSource.ts';

const props = defineProps<{
  profile?: Profile;
}>();

// pprof profiles only carry a subset of categories (e.g. Execution or Allocation); suppress the
// greyed JFR placeholder cards so the grid isn't cluttered with categories pprof never produces.
const isPprofProfile = computed(() => props.profile?.eventSource === RecordingEventSource.PPROF);

// pprof profiles fetch their event summaries from the pprof-specific controller, which returns the
// format-resolved category; JFR profiles use the default generic flamegraph endpoint.
const route = useRoute();
const profileId = route.params.profileId as string;
const fetchEvents = isPprofProfile.value
  ? () => PprofEventSummariesClient.primary(profileId).events()
  : undefined;

const {
  loaded,
  executionSampleEvents,
  cpuTimeSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents,
  blockingEvents,
  nativeAllocationEvents,
  nativeLeakEvents
} = useFlamegraphEvents(GraphType.PRIMARY, fetchEvents);
</script>

<style scoped>
.flamegraphs-primary-container .card {
  border: none;
  overflow: hidden;
}
</style>

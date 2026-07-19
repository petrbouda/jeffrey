<template>
  <LoadingState v-if="!loaded" message="Loading SubSecond data..." />

  <div v-else>
    <PageHeader
      title="Primary SubSecond Graphs"
      description="View and analyze time-based performance data at sub-second intervals"
      icon="bi-bar-chart"
    />

    <FlamegraphCardGrid
      :graph-mode="GraphType.PRIMARY"
      :execution-sample-events="executionSampleEvents"
      :cpu-time-sample-events="cpuTimeSampleEvents"
      :method-trace-events="methodTraceEvents"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      route-name="subsecond"
      button-text="Show SubSecond Graph"
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
  ? () => PprofEventSummariesClient.primary(profileId).events()
  : isOtelProfile.value
    ? () => OtelEventSummariesClient.primary(profileId).events()
    : undefined;

const {
  loaded,
  executionSampleEvents,
  cpuTimeSampleEvents,
  methodTraceEvents,
  objectAllocationEvents,
  wallClockEvents
} = useFlamegraphEvents(GraphType.PRIMARY, fetchEvents);
</script>

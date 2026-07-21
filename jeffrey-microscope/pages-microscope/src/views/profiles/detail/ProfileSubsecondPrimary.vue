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
      :panels="panels"
      route-name="subsecond"
      button-text="Show SubSecond Graph"
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
import { useFlamegraphPanels } from '@/composables/useFlamegraphPanels';
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

const route = useRoute();
const profileId = route.params.profileId as string;
const fetchPanels = isPprofProfile.value
  ? () => PprofEventSummariesClient.primary(profileId).panels()
  : isOtelProfile.value
    ? () => OtelEventSummariesClient.primary(profileId).panels()
    : undefined;

const { loaded, panels } = useFlamegraphPanels(GraphType.PRIMARY, fetchPanels);
</script>

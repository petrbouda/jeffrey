<template>
  <LoadingState v-if="!loaded" message="Loading flamegraph data..." />

  <div v-else>
    <PageHeader
      title="Differential Flamegraphs"
      description="Compare performance data between primary and secondary profiles using Flamegraphs"
      icon="bi-file-diff"
    />

    <FlamegraphCardGrid :graph-mode="GraphType.DIFFERENTIAL" :panels="panels" />
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
import SecondaryProfileService from '@/services/SecondaryProfileService';
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';
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

function differentialPanels(
  client: typeof PprofEventSummariesClient | typeof OtelEventSummariesClient
): () => Promise<FlamegraphPanel[]> {
  return () => {
    const secondaryId = SecondaryProfileService.id();
    if (!secondaryId) {
      return Promise.resolve([]);
    }
    return client.differential(profileId, secondaryId).panels();
  };
}

const fetchPanels = isPprofProfile.value
  ? differentialPanels(PprofEventSummariesClient)
  : isOtelProfile.value
    ? differentialPanels(OtelEventSummariesClient)
    : undefined;

const { loaded, panels } = useFlamegraphPanels(GraphType.DIFFERENTIAL, fetchPanels);
</script>

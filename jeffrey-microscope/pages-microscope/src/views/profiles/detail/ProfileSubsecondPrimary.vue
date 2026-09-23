<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

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
import RecordingEventSource from '@hubs/services/api/model/RecordingEventSource.ts';

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

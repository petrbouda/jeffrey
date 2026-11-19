<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <div>
    <LoadingState v-if="!loaded" message="Loading flamegraph events..." />

    <!-- A failed fetch is not an empty profile, and must not be drawn as one. -->
    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <EmptyState
      v-else-if="!hasEvents"
      icon="bi-fire"
      title="No Flamegraph Data"
      description="This profile has no execution, wall-clock or allocation samples to render."
    />

    <FlamegraphCardGrid
      v-else
      :graph-mode="GraphType.PRIMARY"
      :panels="panels"
      :hide-method="true"
      :hide-native="true"
      :hide-blocking="true"
      emit-view
      @view="openFlamegraph"
    />

    <FlamegraphExplorerModal
      v-model:show="showDialog"
      modal-id="spanFlamegraphModal"
      :scope-label="tag || '(no tag)'"
      :event-type="activeEventType"
      :use-weight="activeUseWeight"
      :graph-updater="graphUpdater"
      :flamegraph-tooltip="flamegraphTooltip"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue';

import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import FlamegraphExplorerModal from '@/components/FlamegraphExplorerModal.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';

import SpanFlamegraphClient from '@/services/api/SpanFlamegraphClient';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import GraphType from '@/services/flamegraphs/GraphType';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import { useFlamegraphPanels } from '@/composables/useFlamegraphPanels';

const props = defineProps<{
  profileId: string;
  tag: string;
}>();

// Span-scoped panels so the cards show the real per-span sample/weight counts (matching the flamegraph),
// not the profile-wide totals.
const { loaded, error, panels, reload } = useFlamegraphPanels(GraphType.PRIMARY, () =>
  new ProfileAsyncProfilerClient(props.profileId).getPanels(props.tag)
);

const hasEvents = computed(() => panels.value.some(panel => panel.event.primary.samples > 0));

// Flamegraph modal state
const showDialog = ref(false);
const activeEventType = ref('');
const activeUseWeight = ref(false);
// shallowRef, not ref: these are stateful objects holding the graph's data and its callbacks, and
// deep reactivity over them buys nothing while proxying every node the flamegraph touches.
const flamegraphTooltip = shallowRef<FlamegraphTooltip | null>(null);
const graphUpdater = shallowRef<GraphUpdater | null>(null);

function openFlamegraph(payload: FlamegraphCardViewPayload) {
  activeEventType.value = payload.eventType;
  activeUseWeight.value = payload.useWeight;

  // The backend scopes the graph to this tag's spans (their thread + window), so no time range or
  // thread filter is sent — the result already contains only the samples those spans cover.
  const client = new SpanFlamegraphClient(
    props.profileId,
    props.tag,
    payload.eventType,
    payload.useThreadMode,
    payload.useWeight,
    payload.excludeNonJavaSamples,
    payload.excludeIdleSamples,
    payload.onlyUnsafeAllocationSamples
  );

  graphUpdater.value = new FullGraphUpdater(client, false);
  flamegraphTooltip.value = FlamegraphTooltipFactory.create(
    payload.eventType,
    payload.useWeight,
    false
  );

  showDialog.value = true;

  // Delay so the modal (flamegraph + timeseries) is rendered and callbacks registered.
  setTimeout(() => {
    graphUpdater.value?.initialize();
  }, GraphUpdater.MODAL_INIT_DELAY_MS);
}
</script>

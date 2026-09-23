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

    <!-- A failed fetch is not an empty operation, and must not be drawn as one. -->
    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <!--
      An operation that never left its virtual threads is not "no data recorded" — the samples exist
      and belong to the carrier. Saying which of the two it is saves the reader the investigation.
    -->
    <VirtualThreadFlamegraphNotice v-else-if="!hasEvents && virtualThreadOnly" scope="operation" />

    <EmptyState
      v-else-if="!hasEvents"
      icon="bi-fire"
      title="No Flamegraph Data"
      description="No execution, wall-clock or allocation samples were taken while traces of this operation were running."
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
      modal-id="traceOperationFlamegraphModal"
      :scope-label="operation.name"
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
import VirtualThreadFlamegraphNotice from '@/components/trace/VirtualThreadFlamegraphNotice.vue';
import FlamegraphExplorerModal from '@/components/FlamegraphExplorerModal.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';

import TraceOperationFlamegraphClient from '@/services/api/TraceOperationFlamegraphClient';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import GraphType from '@/services/flamegraphs/GraphType';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import { useFlamegraphPanels } from '@/composables/useFlamegraphPanels';
import type { TraceOperationId } from '@/services/api/model/trace/TraceModels';

const props = defineProps<{
  profileId: string;
  operation: TraceOperationId;
  /** Every span of every trace of this operation ran on a virtual thread, so no sample can match. */
  virtualThreadOnly?: boolean;
}>();

// Operation-scoped panels so the cards show the real per-operation counts (matching the
// flamegraph), not the profile-wide totals.
const { loaded, error, panels, reload } = useFlamegraphPanels(GraphType.PRIMARY, () =>
  new ProfileTracesClient(props.profileId).getOperationPanels(props.operation)
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

  // The backend scopes the graph to this operation's traces (their thread + window), so no time
  // range or thread filter is sent.
  const client = new TraceOperationFlamegraphClient(
    props.profileId,
    props.operation,
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

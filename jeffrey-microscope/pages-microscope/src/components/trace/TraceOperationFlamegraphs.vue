<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

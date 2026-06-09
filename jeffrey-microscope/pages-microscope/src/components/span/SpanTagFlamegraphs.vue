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

    <EmptyState
      v-else-if="!hasEvents"
      icon="bi-fire"
      title="No Flamegraph Data"
      description="This profile has no execution, wall-clock or allocation samples to render."
    />

    <FlamegraphCardGrid
      v-else
      :graph-mode="GraphType.PRIMARY"
      :execution-sample-events="executionSampleEvents"
      :method-trace-events="[]"
      :object-allocation-events="objectAllocationEvents"
      :wall-clock-events="wallClockEvents"
      :hide-method="true"
      :hide-native="true"
      :hide-blocking="true"
      emit-view
      @view="openFlamegraph"
    />

    <!-- Flamegraph Modal with timeseries (mirrors ProfileEventTypes.vue) -->
    <GenericModal
      modal-id="spanFlamegraphModal"
      :show="showDialog"
      size="fullscreen"
      :show-footer="false"
      @update:show="showDialog = $event"
    >
      <template #header>
        <h5 class="modal-title">
          <i class="bi bi-fire me-2"></i>{{ activeTitle }} — {{ tag || '(no tag)' }}
        </h5>
        <button type="button" class="btn-close" @click="showDialog = false" aria-label="Close" />
      </template>
      <div id="scrollable-wrapper" style="padding: 0.75rem" v-if="showDialog">
        <TimeSeriesChart
          :graph-updater="graphUpdater"
          :primary-axis-type="
            TimeseriesEventAxeFormatter.resolveAxisFormatter(activeUseWeight, activeEventType)
          "
          :visible-minutes="60"
          :zoom-enabled="true"
          time-unit="seconds"
        />
        <FlamegraphComponent
          :with-timeseries="true"
          :use-weight="activeUseWeight"
          :use-guardian="null"
          scrollableWrapperClass="scrollable-wrapper"
          :flamegraph-tooltip="flamegraphTooltip"
          :graph-updater="graphUpdater"
          @loaded="onFlamegraphLoaded"
        />
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import GenericModal from '@/components/GenericModal.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';

import SpanFlamegraphClient from '@/services/api/SpanFlamegraphClient';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import GraphType from '@/services/flamegraphs/GraphType';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import { useFlamegraphEvents } from '@/composables/useFlamegraphEvents';

const MODAL_INIT_DELAY_MS = 200;

const props = defineProps<{
  profileId: string;
  tag: string;
}>();

// Span-scoped event summaries so the cards show the real per-span sample/weight counts (matching the
// flamegraph), not the profile-wide totals.
const { loaded, executionSampleEvents, wallClockEvents, objectAllocationEvents } = useFlamegraphEvents(
  GraphType.PRIMARY,
  () => new ProfileAsyncProfilerClient(props.profileId).getEventSummaries(props.tag)
);

const hasEvents = computed(
  () =>
    executionSampleEvents.value.length > 0 ||
    wallClockEvents.value.length > 0 ||
    objectAllocationEvents.value.length > 0
);

// Flamegraph modal state
const showDialog = ref(false);
const activeTitle = ref('');
const activeEventType = ref('');
const activeUseWeight = ref(false);
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

function openFlamegraph(payload: FlamegraphCardViewPayload) {
  activeTitle.value = payload.eventType;
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

  graphUpdater = new FullGraphUpdater(client, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(payload.eventType, payload.useWeight, false);

  showDialog.value = true;

  // Delay so the modal (flamegraph + timeseries) is rendered and callbacks registered.
  setTimeout(() => {
    graphUpdater.initialize();
  }, MODAL_INIT_DELAY_MS);
}

function onFlamegraphLoaded() {
  scrollToTop();
}

function scrollToTop() {
  const wrapper = document.getElementById('scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
</script>

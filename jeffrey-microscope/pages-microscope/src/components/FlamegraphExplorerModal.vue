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

<!--
  A flamegraph and its timeseries, opened over whatever list the reader picked an event type from.

  The three pages that offer this — trace operations, async-profiler span tags and the event-type
  grid — differ only in how they scope the graph, which is entirely the client they hand in. Holding
  the modal here means the scroll wrapper, the render delay and the tooltip wiring are decided once;
  they had been copied three times, and the copies had already begun to drift.
-->
<template>
  <GenericModal
    :modal-id="modalId"
    :show="show"
    size="fullscreen"
    :show-footer="false"
    @update:show="$emit('update:show', $event)"
  >
    <template #header>
      <h5 class="modal-title"><i class="bi bi-fire me-2"></i>{{ eventType }} — {{ scopeLabel }}</h5>
      <button type="button" class="btn-close" @click="$emit('update:show', false)" aria-label="Close" />
    </template>

    <!--
      Mounted only while open: the flamegraph registers callbacks on mount, and a hidden instance
      holding a stale updater is what made a second open draw the previous scope's graph.
    -->
    <div v-if="show && graphUpdater && flamegraphTooltip" :id="SCROLL_ID" style="padding: 0.75rem">
      <TimeSeriesChart
        :graph-updater="graphUpdater"
        :primary-axis-type="TimeseriesEventAxeFormatter.resolveAxisFormatter(useWeight, eventType)"
        :visible-minutes="60"
        :zoom-enabled="true"
        time-unit="seconds"
      />
      <FlamegraphComponent
        :with-timeseries="true"
        :use-weight="useWeight"
        :use-guardian="null"
        :scrollable-wrapper-class="SCROLL_ID"
        :flamegraph-tooltip="flamegraphTooltip"
        :graph-updater="graphUpdater"
        @loaded="scrollToTop"
      />
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import GenericModal from '@shared/components/GenericModal.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';

const SCROLL_ID = 'scrollable-wrapper';

defineProps<{
  show: boolean;
  /** Distinct per page, so two explorers on one route cannot collide in the DOM. */
  modalId: string;
  /** What the graph is scoped to, in the reader's terms: an operation name, a tag, a profile. */
  scopeLabel: string;
  eventType: string;
  useWeight: boolean;
  /**
   * Already pointed at the right scope by the caller — that is the whole of the difference.
   *
   * Null until the reader picks an event type, which is also why the body below is gated on it: the
   * modal exists in the page from the start, and there is nothing to draw before the first pick.
   */
  graphUpdater: GraphUpdater | null;
  flamegraphTooltip: FlamegraphTooltip | null;
}>();

defineEmits<{ (event: 'update:show', value: boolean): void }>();

/** The graph renders from the top; a scroll position left by the previous graph is not meaningful. */
function scrollToTop(): void {
  const wrapper = document.getElementById(SCROLL_ID);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
</script>

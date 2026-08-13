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
  <div class="span-flamegraphs">
    <!--
      Inclusive covers the span's whole window; Self cuts its children out of it. The distinction is
      the difference between "this span took 400 ms" and "this span spent 400 ms in code it owns",
      so it changes both the cards and the graph and is chosen before either is loaded.
    -->
    <div class="scope-toggle" role="group" aria-label="Flamegraph scope">
      <button
        type="button"
        :class="{ active: !selfOnly }"
        :aria-pressed="!selfOnly"
        @click="selfOnly = false"
      >
        Inclusive
      </button>
      <button
        type="button"
        :class="{ active: selfOnly }"
        :aria-pressed="selfOnly"
        @click="selfOnly = true"
      >
        Self only
      </button>
    </div>

    <LoadingState v-if="!loaded" message="Loading flamegraph events..." />

    <EmptyState
      v-else-if="!hasEvents"
      icon="bi-fire"
      title="No Samples In This Span"
      :description="
        selfOnly
          ? 'Nothing was sampled outside this span\'s children. Try the inclusive scope.'
          : 'No execution, wall-clock or allocation samples landed inside this span.'
      "
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

    <GenericModal
      modal-id="traceSpanFlamegraphModal"
      :show="showDialog"
      size="fullscreen"
      :show-footer="false"
      @update:show="showDialog = $event"
    >
      <template #header>
        <h5 class="modal-title">
          <i class="bi bi-fire me-2"></i>{{ activeEventType }} — {{ spanName }}
          <span class="scope-note">{{ selfOnly ? 'self only' : 'inclusive' }}</span>
        </h5>
        <button type="button" class="btn-close" aria-label="Close" @click="showDialog = false" />
      </template>
      <div v-if="showDialog" id="scrollable-wrapper" style="padding: 0.75rem">
        <FlamegraphComponent
          :with-timeseries="false"
          :use-weight="activeUseWeight"
          :use-guardian="null"
          scrollableWrapperClass="scrollable-wrapper"
          :flamegraph-tooltip="flamegraphTooltip"
          :graph-updater="graphUpdater"
          @loaded="scrollToTop"
        />
      </div>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import GenericModal from '@shared/components/GenericModal.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';

import TraceSpanFlamegraphClient from '@/services/api/TraceSpanFlamegraphClient';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import GraphType from '@/services/flamegraphs/GraphType';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import OnlyFlamegraphGraphUpdater from '@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import { useFlamegraphPanels } from '@/composables/useFlamegraphPanels';

const MODAL_INIT_DELAY_MS = 200;

const props = defineProps<{
  profileId: string;
  traceId: string;
  spanId: string;
  spanName: string;
}>();

const selfOnly = ref(false);

// Panels are scoped to the span, so the cards show what actually landed inside it rather than the
// profile-wide totals. Re-fetched when the scope changes, because self-only covers less time.
const { loaded, panels, reload } = useFlamegraphPanels(GraphType.PRIMARY, () =>
  new ProfileTracesClient(props.profileId).getSpanPanels(
    props.traceId,
    props.spanId,
    selfOnly.value
  )
);

watch([selfOnly, () => props.spanId], () => reload());

const hasEvents = computed(() => panels.value.some((panel) => panel.event.primary.samples > 0));

const showDialog = ref(false);
const activeEventType = ref('');
const activeUseWeight = ref(false);
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

function openFlamegraph(payload: FlamegraphCardViewPayload): void {
  activeEventType.value = payload.eventType;
  activeUseWeight.value = payload.useWeight;

  // No timeseries: a span is a single short window, so bucketing it over the recording's timeline
  // would be a chart with one bar.
  const client = new TraceSpanFlamegraphClient(
    props.profileId,
    props.traceId,
    props.spanId,
    selfOnly.value,
    payload.eventType,
    payload.useWeight
  );

  graphUpdater = new OnlyFlamegraphGraphUpdater(client);
  flamegraphTooltip = FlamegraphTooltipFactory.create(payload.eventType, payload.useWeight, false);

  showDialog.value = true;

  // Delay so the modal is rendered and the flamegraph's callbacks are registered.
  setTimeout(() => {
    graphUpdater.initialize();
  }, MODAL_INIT_DELAY_MS);
}

function scrollToTop(): void {
  const wrapper = document.getElementById('scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
</script>

<style scoped>
.span-flamegraphs {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.scope-toggle {
  display: inline-flex;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  overflow: hidden;
  align-self: flex-start;
}

.scope-toggle button {
  font: inherit;
  font-size: var(--font-size-sm);
  border: 0;
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  padding: 0.28rem 0.65rem;
  cursor: pointer;
}

.scope-toggle button.active {
  background: var(--color-primary);
  color: var(--color-white);
  font-weight: 500;
}

.scope-toggle button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.scope-note {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: 400;
  margin-left: 0.4rem;
}
</style>

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
      Inclusive covers the span's whole window; Self cuts its same-thread children out of it. The
      distinction is the difference between "this span took 400 ms" and "this span spent 400 ms in
      code it owns", so it changes both the cards and the graph and is chosen before either loads.
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
      @view="request"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import FlamegraphCardGrid from '@/components/FlamegraphCardGrid.vue';
import type { FlamegraphCardViewPayload } from '@/components/FlamegraphCard.vue';

import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import GraphType from '@/services/flamegraphs/GraphType';
import { useFlamegraphPanels } from '@/composables/useFlamegraphPanels';

/** A card the reader asked to open, together with the scope its counts were taken under. */
export interface TraceSpanFlamegraphRequest {
  payload: FlamegraphCardViewPayload;
  selfOnly: boolean;
}

const props = defineProps<{
  profileId: string;
  traceId: string;
  spanId: string;
}>();

// The graph itself is rendered by whoever hosts this component: the drill-down already lives in a
// fullscreen modal, and a second one stacked on top of it would trap the reader behind two dialogs.
const emit = defineEmits<{ (event: 'view', request: TraceSpanFlamegraphRequest): void }>();

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

const hasEvents = computed(() => panels.value.some(panel => panel.event.primary.samples > 0));

function request(payload: FlamegraphCardViewPayload): void {
  emit('view', { payload, selfOnly: selfOnly.value });
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
</style>

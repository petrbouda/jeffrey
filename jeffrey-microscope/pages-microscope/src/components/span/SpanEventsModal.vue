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
  <GenericModal
    modal-id="spanEventsModal"
    :show="show"
    title="Events during span"
    icon="bi-list-ul"
    size="fullscreen"
    modal-dialog-class="events-modal-dialog"
    :show-footer="false"
    @update:show="$emit('update:show', $event)"
  >
    <div v-if="show" class="span-events">
      <!-- Header chips -->
      <MetaChips :chips="chips" />

      <LoadingState v-if="loading" message="Loading events..." />

      <!-- A failed fetch must not claim the recording holds no events for this window. -->
      <ErrorState v-else-if="error" :message="error" @retry="load" />

      <EmptyState
        v-else-if="events.length === 0"
        title="No events"
        description="No JFR events were recorded on this thread during the span window."
        icon="bi-inboxes"
      />

      <template v-else>
        <!-- Span-scoped flamegraph swapped in over the events body -->
        <div v-if="mode === 'flamegraph'" class="se-fg-view">
          <div class="se-fg-bar">
            <button type="button" class="se-fg-back" @click="backToEvents">
              <i class="bi bi-arrow-left"></i> Back to events
            </button>
            <span class="se-fg-active"><i class="bi bi-fire"></i> {{ activeEventType }}</span>
          </div>
          <div :id="SPAN_FG_SCROLL_ID" class="se-fg-scroll">
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
              :scrollable-wrapper-class="SPAN_FG_SCROLL_ID"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater"
              @loaded="onFlamegraphLoaded"
            />
          </div>
        </div>

        <EventWindowTimeline
          v-else
          :events="events"
          :window-start-millis="startEpochMillis"
          :window-millis="windowMillis"
          @flamegraph="openFlamegraph"
        />
      </template>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import GenericModal from '@shared/components/GenericModal.vue';
import MetaChips from '@shared/components/MetaChips.vue';
import type { MetaChip } from '@shared/components/MetaChips.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import EventWindowTimeline from '@/components/events/EventWindowTimeline.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import FormattingService from '@shared/services/FormattingService';
import EventTypes from '@/services/EventTypes';
import ProfileAsyncProfilerClient from '@/services/api/ProfileAsyncProfilerClient';
import SingleSpanFlamegraphClient from '@/services/api/SingleSpanFlamegraphClient';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import type { SpanEventRow } from '@/services/api/model/span/SpanModels';

const NANOS_PER_MILLI = 1_000_000;
const SPAN_FG_SCROLL_ID = 'span-fg-scroll';

const props = defineProps<{
  show: boolean;
  profileId: string;
  threadHash: string;
  startEpochMillis: number;
  durationNanos: number;
  threadName: string;
  isVirtual?: boolean;
  tag?: string;
}>();

defineEmits<{
  'update:show': [value: boolean];
}>();

const client = new ProfileAsyncProfilerClient(props.profileId);

const loading = ref(false);
const error = ref<string | null>(null);
const events = ref<SpanEventRow[]>([]);

// Span-scoped flamegraph swap-in. graphUpdater/flamegraphTooltip are plain (non-reactive) and are
// assigned in openFlamegraph() before `mode` flips to 'flamegraph', so the re-render reads them fresh.
const mode = ref<'events' | 'flamegraph'>('events');
const activeEventType = ref('');
const activeUseWeight = ref(false);
let graphUpdater: GraphUpdater;
let flamegraphTooltip: FlamegraphTooltip;

const windowMillis = computed(() => Math.max(1, Math.round(props.durationNanos / NANOS_PER_MILLI)));
const windowLabel = computed(() => FormattingService.formatDuration2Units(props.durationNanos));

const distinctTypes = computed(() => new Set(events.value.map(event => event.eventType)).size);

const chips = computed<MetaChip[]>(() => {
  const result: MetaChip[] = [
    { text: `${events.value.length} events`, tone: 'strong' },
    { text: `${distinctTypes.value} types` },
    { icon: 'clock', text: windowLabel.value },
    {
      icon: 'cpu',
      text: props.threadName || 'unknown',
      marker: props.isVirtual ? 'virtual' : undefined
    }
  ];
  if (props.tag) {
    result.push({ icon: 'tag', text: props.tag });
  }
  return result;
});

/**
 * Whether an event type can be rendered as a span-scoped flamegraph, and how its samples are
 * weighted. Only the categories the flamegraph cards offer are supported; the timeline offers a
 * button for exactly these and nothing else, so an unsupported type never reaches here.
 */
function useWeightFor(type: string): boolean | null {
  if (
    EventTypes.isExecutionEventType(type) ||
    EventTypes.isWallClock(type) ||
    EventTypes.isAllocationEventType(type)
  ) {
    return false;
  }
  return null;
}

function openFlamegraph(type: string): void {
  const useWeight = useWeightFor(type);
  if (useWeight === null) {
    return;
  }
  activeEventType.value = type;
  activeUseWeight.value = useWeight;

  const to = props.startEpochMillis + windowMillis.value;
  const fgClient = new SingleSpanFlamegraphClient(
    props.profileId,
    props.threadHash,
    props.startEpochMillis,
    to,
    type,
    false,
    useWeight,
    false,
    false,
    false
  );

  graphUpdater = new FullGraphUpdater(fgClient, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(type, useWeight, false);

  mode.value = 'flamegraph';

  setTimeout(() => {
    graphUpdater.initialize();
  }, GraphUpdater.MODAL_INIT_DELAY_MS);
}

function backToEvents(): void {
  mode.value = 'events';
}

function onFlamegraphLoaded(): void {
  const wrapper = document.getElementById(SPAN_FG_SCROLL_ID);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

async function load() {
  loading.value = true;
  error.value = null;
  events.value = [];
  mode.value = 'events';
  try {
    const to = props.startEpochMillis + windowMillis.value;
    events.value = await client.getSpanEvents(props.threadHash, props.startEpochMillis, to);
  } catch (e: unknown) {
    console.error('Failed to load span events:', e);
    events.value = [];
    error.value = 'Failed to load the events recorded during this span.';
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.show, props.threadHash, props.startEpochMillis, props.durationNanos],
  () => {
    if (props.show) {
      load();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.span-events {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* Timeline + brush */
.se-fg-view {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.se-fg-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.se-fg-back {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.3rem 0.6rem;
  cursor: pointer;
}

.se-fg-back:hover {
  background: var(--color-light);
}

.se-fg-active {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-dark);
}

.se-fg-scroll {
  max-height: calc(100vh - 220px);
  overflow: auto;
}

/* Flat table */
</style>

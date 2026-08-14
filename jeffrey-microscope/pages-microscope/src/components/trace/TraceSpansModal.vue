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
    modal-id="traceSpansModal"
    :show="show"
    :title="rootName || 'Spans in trace'"
    icon="bi-diagram-3"
    size="fullscreen"
    modal-dialog-class="events-modal-dialog"
    :show-footer="false"
    @update:show="$emit('update:show', $event)"
  >
    <div v-if="show" class="trace-spans">
      <MetaChips :chips="chips" />

      <LoadingState v-if="loading" message="Loading trace..." />

      <ErrorState v-else-if="error" :message="error" @retry="load" />

      <EmptyState
        v-else-if="!detail || detail.spans.length === 0"
        title="No spans"
        description="This trace has no spans left to draw."
        icon="bi-inboxes"
      />

      <!--
        The flamegraph replaces the bars rather than opening over them: the drill-down already lives
        in a fullscreen modal, and stacking a second one would trap the reader behind two dialogs.
      -->
      <div v-else-if="mode === 'events'" class="ts-fg-view">
        <div class="ts-fg-bar">
          <button type="button" class="ts-fg-back" @click="goBack">
            <i class="bi bi-arrow-left"></i> {{ backLabel }}
          </button>
          <span class="ts-fg-active">
            <i class="bi bi-list-ul"></i> {{ selected?.name }}
            <span class="ts-fg-scope">events on {{ selected?.threadName ?? 'this thread' }}</span>
          </span>
        </div>

        <LoadingState v-if="eventsLoading" message="Loading events..." />

        <ErrorState v-else-if="eventsError" :message="eventsError" @retry="loadEvents" />

        <EmptyState
          v-else-if="spanEvents.length === 0"
          title="Nothing recorded"
          description="Nothing else ran on this thread while the span was open."
          icon="bi-inbox"
        />

        <EventWindowTimeline
          v-else-if="selected"
          :events="spanEvents"
          :window-start-millis="spanWindowStartMillis"
          :window-millis="spanWindowMillis"
          @flamegraph="openFlamegraphForType"
        />
      </div>

      <div v-else-if="mode === 'flamegraph'" class="ts-fg-view">
        <div class="ts-fg-bar">
          <button type="button" class="ts-fg-back" @click="goBack">
            <i class="bi bi-arrow-left"></i> {{ backLabel }}
          </button>
          <span class="ts-fg-active">
            <i class="bi bi-fire"></i> {{ activeEventType }}
            <span class="ts-fg-scope">{{ activeSelfOnly ? 'self only' : 'inclusive' }}</span>
          </span>
        </div>
        <div :id="TRACE_FG_SCROLL_ID" class="ts-fg-scroll">
          <FlamegraphComponent
            :with-timeseries="false"
            :use-weight="activeUseWeight"
            :use-guardian="null"
            :scrollable-wrapper-class="TRACE_FG_SCROLL_ID"
            :flamegraph-tooltip="flamegraphTooltip"
            :graph-updater="graphUpdater"
            @loaded="scrollToTop"
          />
        </div>
      </div>

      <!--
        The flamegraph chooser gets the dialog to itself: it is a grid of cards, which is wider than
        a row of the waterfall can hold, and the reader has already decided which span they are on.
      -->
      <div v-else-if="mode === 'flamegraph-picker' && selected" class="ts-fg-view">
        <div class="ts-fg-bar">
          <button type="button" class="ts-fg-back" @click="goBack">
            <i class="bi bi-arrow-left"></i> {{ backLabel }}
          </button>
          <span class="ts-fg-active">
            <i class="bi bi-fire"></i> {{ selected.name }}
            <span class="ts-fg-scope">pick an event type</span>
          </span>
        </div>

        <TraceSpanFlamegraphs
          :profile-id="profileId"
          :trace-id="traceId"
          :span-id="selected.spanId"
          :virtual-thread="selected.isVirtual"
          @view="openFlamegraph"
        />
      </div>

      <div v-else class="trace-body">
        <div class="waterfall-pane">
          <TraceWaterfall
            :spans="detail.spans"
            :selected-span-id="selected?.spanId ?? null"
            :event-fields="detail.eventFields ?? {}"
            @select="select"
            @view-events="openEvents"
            @view-flamegraph="openFlamegraphPicker"
          />
        </div>
      </div>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import GenericModal from '@shared/components/GenericModal.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MetaChips from '@shared/components/MetaChips.vue';
import type { MetaChip } from '@shared/components/MetaChips.vue';
import FormattingService from '@shared/services/FormattingService';

import TraceWaterfall from '@/components/trace/TraceWaterfall.vue';
import TraceSpanFlamegraphs from '@/components/trace/TraceSpanFlamegraphs.vue';
import EventWindowTimeline from '@/components/events/EventWindowTimeline.vue';
import type { TraceSpanFlamegraphRequest } from '@/components/trace/TraceSpanFlamegraphs.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';

import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import TraceSpanFlamegraphClient from '@/services/api/TraceSpanFlamegraphClient';
import { errorLabel } from '@/services/trace/traceLabels';
import { ceilNanosToMillis, floorToMillis } from '@/services/trace/timeUnits';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import OnlyFlamegraphGraphUpdater from '@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import type {
  TraceDetail,
  TraceEventRow,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';

const TRACE_FG_SCROLL_ID = 'trace-fg-scroll';
const MODAL_INIT_DELAY_MS = 200;

const props = defineProps<{
  show: boolean;
  profileId: string;
  traceId: string;
  /** Known from the row that opened the modal, so the header reads right before the fetch lands. */
  rootName: string;
}>();

defineEmits<{ (event: 'update:show', value: boolean): void }>();

const detail = ref<TraceDetail | null>(null);
const selected = ref<TraceSpanRow | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const mode = ref<'spans' | 'events' | 'flamegraph-picker' | 'flamegraph'>('spans');
const spanEvents = ref<TraceEventRow[]>([]);
const eventsLoading = ref(false);
const eventsError = ref<string | null>(null);
/** Which view the graph was opened from, so its back button can return there. */
const flamegraphOrigin = ref<'events' | 'flamegraph-picker'>('flamegraph-picker');
const activeEventType = ref('');
const activeUseWeight = ref(false);
const activeSelfOnly = ref(false);
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

/**
 * The handful of facts that orient the reader before they look at a single bar. Thread count is
 * here because it is the one thing a trace has that a flat span does not: more than one means the
 * work was handed off, which is what makes the bars overlap.
 */
const chips = computed<MetaChip[]>(() => {
  if (!detail.value) {
    return [];
  }
  const trace = detail.value.trace;
  const threads = new Set(detail.value.spans.map(span => span.threadHash));
  const threadName = detail.value.spans[0]?.threadName;

  const result: MetaChip[] = [{ text: `${trace.spanCount} spans`, tone: 'strong' }];
  if (trace.errorCount > 0) {
    result.push({
      icon: 'exclamation-triangle',
      text: errorLabel(trace.errorCount),
      tone: 'danger'
    });
  }
  result.push({ icon: 'clock', text: FormattingService.formatDuration2Units(trace.durationNanos) });
  result.push({
    icon: 'cpu',
    text: threads.size === 1 ? (threadName ?? 'unknown') : `${threads.size} threads`
  });
  result.push({ icon: 'diagram-2', text: trace.rootKind });
  result.push({ icon: 'hash', text: trace.traceId });
  return result;
});

/*
 * Ceiled, not rounded. The start below is floored to the millisecond the span's events were filed
 * under, so rounding the length down could end the window before the span did and drop its last
 * events; widening by under a millisecond cannot do the same kind of harm.
 */
const spanWindowMillis = computed(() =>
  Math.max(1, ceilNanosToMillis(selected.value?.durationNanos ?? 0))
);

/**
 * The event timeline is drawn against the events table, whose timestamps are millisecond-resolution,
 * so the span's microsecond start is floored to the millisecond its events were filed under.
 */
const spanWindowStartMillis = computed(() =>
  floorToMillis(selected.value?.startEpochMicros ?? 0)
);

async function loadEvents(): Promise<void> {
  const span = selected.value;
  if (!span) {
    return;
  }
  eventsLoading.value = true;
  eventsError.value = null;
  try {
    spanEvents.value = await new ProfileTracesClient(props.profileId).getSpanEvents(
      props.traceId,
      span.spanId
    );
  } catch {
    spanEvents.value = [];
    eventsError.value = 'Failed to load the events recorded inside this span.';
  } finally {
    eventsLoading.value = false;
  }
}

function openEvents(): void {
  mode.value = 'events';
  loadEvents();
}

/**
 * A flamegraph asked for from the event timeline, which shows the span's whole window — so the
 * graph is scoped inclusively to match what the reader was just looking at.
 */
function openFlamegraphForType(eventType: string): void {
  openFlamegraph({
    payload: {
      eventType,
      useWeight: false,
      useThreadMode: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false
    },
    selfOnly: false
  });
}

/**
 * Clicking the open row again closes it, leaving the waterfall on its own. Nothing is scrolled into
 * view: the detail opens directly under the row that was clicked, which is already where the reader
 * is looking.
 */
function select(span: TraceSpanRow): void {
  selected.value = selected.value?.spanId === span.spanId ? null : span;
}

function openFlamegraphPicker(): void {
  mode.value = 'flamegraph-picker';
}

const backLabel = computed(() => {
  if (mode.value !== 'flamegraph') {
    return 'Back to spans';
  }
  return flamegraphOrigin.value === 'events' ? 'Back to events' : 'Back to event types';
});

function openFlamegraph(request: TraceSpanFlamegraphRequest): void {
  const span = selected.value;
  if (!span) {
    return;
  }

  flamegraphOrigin.value = mode.value === 'events' ? 'events' : 'flamegraph-picker';
  activeEventType.value = request.payload.eventType;
  activeUseWeight.value = request.payload.useWeight;
  activeSelfOnly.value = request.selfOnly;

  // No timeseries: a span is a single short window, so bucketing it over the recording's timeline
  // would be a chart with one bar.
  const client = new TraceSpanFlamegraphClient(
    props.profileId,
    props.traceId,
    span.spanId,
    request.selfOnly,
    request.payload.eventType,
    request.payload.useWeight
  );

  // Initialized below, once the swapped-in view has rendered and registered its callbacks.
  graphUpdater = new OnlyFlamegraphGraphUpdater(client, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(
    request.payload.eventType,
    request.payload.useWeight,
    false
  );

  mode.value = 'flamegraph';

  // Delay so the flamegraph is rendered and its callbacks are registered.
  setTimeout(() => {
    graphUpdater.initialize();
  }, MODAL_INIT_DELAY_MS);
}

/**
 * The graph can be reached from the event timeline or from the event-type chooser, so its back
 * button returns to whichever it was rather than dropping the reader all the way out to the bars.
 */
function goBack(): void {
  mode.value = mode.value === 'flamegraph' ? flamegraphOrigin.value : 'spans';
}

function scrollToTop(): void {
  const wrapper = document.getElementById(TRACE_FG_SCROLL_ID);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  selected.value = null;
  mode.value = 'spans';
  spanEvents.value = [];
  eventsError.value = null;
  try {
    detail.value = await new ProfileTracesClient(props.profileId).getTrace(props.traceId);
  } catch {
    detail.value = null;
    error.value = 'Failed to load this trace.';
  } finally {
    loading.value = false;
  }
}

// Fetched when the modal opens, and re-fetched when it is reopened on a different trace.
watch(
  () => [props.show, props.traceId] as const,
  ([show, traceId]) => {
    if (show && traceId) {
      load();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.trace-spans {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/*
 * The span panel sits under the waterfall rather than beside it: the bars are the widest thing in
 * the dialog, and a side panel took its width from them -- the one dimension a waterfall cannot
 * spare, since every bar is positioned against the trace's full duration.
 */
.trace-body {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  overflow: hidden;
}

/* The waterfall scrolls on its own so a wide trace never scrolls the modal sideways. */
.waterfall-pane {
  overflow-x: auto;
  min-width: 0;
}

.ts-fg-view {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ts-fg-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.ts-fg-back {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font: inherit;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.3rem 0.6rem;
  cursor: pointer;
}

.ts-fg-back:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.ts-fg-active {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-dark);
}

.ts-fg-scope {
  font-size: 0.72rem;
  font-weight: 400;
  color: var(--color-text-muted);
}

.ts-fg-scroll {
  max-height: calc(100vh - 220px);
  overflow: auto;
}
</style>

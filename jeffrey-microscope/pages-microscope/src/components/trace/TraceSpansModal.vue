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
    :backdrop-close="mode === 'spans'"
    @update:show="$emit('update:show', $event)"
  >
    <div v-if="show" class="trace-spans">
      <div class="trace-meta">
        <MetaChips :chips="chips" />
        <div class="trace-actions">
          <!--
            The edge from one trace to all of its kind: this dialog answers "why was THIS one slow",
            and the operation page answers "is it always like this" — the natural next question, and
            previously unreachable from here without retyping the name into another page's filter.
          -->
          <router-link v-if="operationLink" class="trace-op-link" :to="operationLink">
            <i class="bi bi-bar-chart-steps"></i> All {{ detail?.trace?.rootName }} traces
          </router-link>
          <!--
            Beside the trace's own facts rather than in the waterfall toolbar: it exports the whole
            trace, not the view of it, so it should not sit among the controls that change that view.

            Withdrawn while a span's flamegraph is open: the graph carries its own export in its
            toolbar, and two buttons both saying "Copy for AI" left the reader with a trace bundle
            when they had asked for the frames in front of them.
          -->
          <AiExportButton
            v-if="mode !== 'flamegraph'"
            :build-source="buildAiExportSource"
            tooltip="Export this trace for AI analysis"
            :disabled="!detail"
            disabled-tooltip="Waiting for the trace to load"
          />
        </div>
      </div>

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

        The drill-down views are v-if inside this v-else block, but the spans view at the bottom is
        v-show: the waterfall holds fold, filter and overlay state the reader built up, and
        unmounting it on every events/flamegraph visit handed them back a reset tree on return.
      -->
      <template v-else>
        <div v-if="mode === 'events'" class="ts-fg-view">
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

          <template v-else-if="selected">
            <div v-if="eventsTruncated" class="ts-truncated-note">
              <i class="bi bi-info-circle"></i>
              Showing the first {{ spanEvents.length }} events recorded in this window — the span
              held more than the drill-down can list.
            </div>
            <EventWindowTimeline
              :events="spanEvents"
              :window-start-millis="spanWindowStartMillis"
              :window-millis="spanWindowMillis"
              @flamegraph="openFlamegraphForType"
            />
          </template>
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
              :ai-export-context="flamegraphAiExport"
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

        <div v-show="mode === 'spans'" class="trace-body">
          <div class="waterfall-pane">
            <TraceWaterfall
              :profile-id="profileId"
              :spans="detail.spans"
              :selected-span-id="selected?.spanId ?? null"
              :event-fields="detail.eventFields ?? {}"
              :context="context"
              :context-state="contextState"
              :trace-duration-nanos="detail.trace.durationNanos"
              :notifications="detail.notifications ?? []"
              :exceptions="detail.exceptions ?? []"
              @select="select"
              @view-events="openEvents"
              @view-flamegraph="openFlamegraphPicker"
            />
          </div>

          <!--
          Below the bars rather than beside them: it is the conclusion drawn from the trace above,
          and a reader reaches it after looking at the shape, not instead of doing so. The slices
          speak for themselves — no heading over them.
        -->
          <!--
          Always rendered, with words for each state. Vanishing entirely made three different facts
          — still loading, request failed, and "the JVM genuinely never interrupted this trace" —
          indistinguishable, and a reader who saw the panel on one trace had no way to learn why
          another lacked it.
        -->
          <section class="context-pane">
            <TraceWhySlowPanel
              v-if="hasContextFindings"
              :slices="context?.summary ?? []"
              :trace-duration-nanos="detail.trace.durationNanos"
              :profile-id="profileId"
            />
            <p v-else-if="contextState === 'loading'" class="context-note">
              Attributing this trace's time to GC, locks and I/O…
            </p>
            <p v-else-if="contextState === 'failed'" class="context-note">
              The JVM context could not be loaded, so nothing here says why this trace was slow.
              <button type="button" class="context-retry" @click="loadContext">Try again</button>
            </p>
            <p v-else class="context-note">
              No GC pauses, lock waits or I/O were attributed to this trace — its time was spent
              running its own code.
            </p>
          </section>
        </div>
      </template>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import GenericModal from '@shared/components/GenericModal.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import MetaChips from '@shared/components/MetaChips.vue';
import type { MetaChip } from '@shared/components/MetaChips.vue';
import FormattingService from '@shared/services/FormattingService';

import TraceWaterfall from '@/components/trace/TraceWaterfall.vue';
import TraceWhySlowPanel from '@/components/trace/TraceWhySlowPanel.vue';
import AiExportButton from '@/components/ai-analysis/AiExportButton.vue';
import TraceSpanFlamegraphs from '@/components/trace/TraceSpanFlamegraphs.vue';
import EventWindowTimeline from '@/components/events/EventWindowTimeline.vue';
import type { TraceSpanFlamegraphRequest } from '@/components/trace/TraceSpanFlamegraphs.vue';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import type { AiExportContext } from '@/components/FlamegraphComponent.vue';

import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import TraceAiExportClient from '@/services/api/TraceAiExportClient';
import { flamegraphFilenameStem } from '@/composables/useAiExport';
import type { AiExportSource } from '@/composables/useAiExport';
import TraceSpanFlamegraphClient from '@/services/api/TraceSpanFlamegraphClient';
import { errorLabel } from '@/services/trace/traceLabels';
import { ceilNanosToMillis, floorToMillis } from '@/services/trace/timeUnits';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import OnlyFlamegraphGraphUpdater from '@/services/flamegraphs/updater/OnlyFlamegraphGraphUpdater';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import type {
  TraceContext,
  TraceDetail,
  TraceEventRow,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';

const TRACE_FG_SCROLL_ID = 'trace-fg-scroll';

const props = withDefaults(
  defineProps<{
    show: boolean;
    profileId: string;
    traceId: string;
    /** Known from the row that opened the modal, so the header reads right before the fetch lands. */
    rootName: string;
    /**
     * Off when the modal is opened from the operation's own detail page, where "all traces of this
     * kind" is a link to exactly where the reader already stands.
     */
    withOperationLink?: boolean;
  }>(),
  { withOperationLink: true }
);

defineEmits<{ (event: 'update:show', value: boolean): void }>();

const detail = ref<TraceDetail | null>(null);
const selected = ref<TraceSpanRow | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const mode = ref<'spans' | 'events' | 'flamegraph-picker' | 'flamegraph'>('spans');
const spanEvents = ref<TraceEventRow[]>([]);
const eventsTruncated = ref(false);
const eventsLoading = ref(false);
const eventsError = ref<string | null>(null);
/** Which view the graph was opened from, so its back button can return there. */
const flamegraphOrigin = ref<'events' | 'flamegraph-picker'>('flamegraph-picker');
const activeEventType = ref('');
const activeUseWeight = ref(false);
const activeSelfOnly = ref(false);
/** The open span graph's own AI export, so "Copy for AI" over it describes the graph, not the trace. */
const flamegraphAiExport = ref<AiExportContext | null>(null);

/** Null until the second request lands, and after one that failed. The waterfall copes with both. */
const context = ref<TraceContext | null>(null);

/**
 * Distinguished, because the three ways context can be absent are three different facts. A null
 * context used to render identically for "still loading", "the request failed" and "the JVM never
 * interrupted this trace" — and downstream text asserted the third while the first was true.
 */
const contextState = ref<'loading' | 'ready' | 'failed'>('loading');

/** Whether anything beyond the residual was attributed — what decides the panel's wording. */
const hasContextFindings = computed(() =>
  (context.value?.summary ?? []).some(
    slice => slice.category !== 'OWN_WORK' && slice.totalNanos > 0
  )
);
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

  // What the trace carried beside its spans, counted in the header so a reader knows there is
  // something on the rails before they look at them. Absent when there is nothing, rather than
  // reported as zero: most traces say nothing and throw nothing, and two zeroes on every header
  // would be noise.
  const notifications = detail.value.notifications ?? [];
  if (notifications.length > 0) {
    result.push({
      icon: 'chat-square-dots',
      text: notifications.length === 1 ? '1 notification' : `${notifications.length} notifications`,
      tone: 'strong'
    });
  }
  const exceptions = detail.value.exceptions ?? [];
  if (exceptions.length > 0) {
    const escaped = exceptions.filter(exception => exception.escaped).length;
    result.push({
      icon: 'x-octagon',
      text: exceptions.length === 1 ? '1 throw' : `${exceptions.length} throws`,
      tone: escaped > 0 ? 'danger' : undefined
    });
  }

  // Which span decided the trace's duration -- the first thing worth knowing about a slow trace, and
  // not something the bars give up at a glance once there is any concurrency in them.
  const leader = topCriticalSpan.value;
  if (leader !== null) {
    result.push({
      icon: 'signpost-split',
      text: `${leader.name} · ${percentOfTrace(leader.criticalPathNanos, trace.durationNanos)}`
    });
  }

  result.push({ icon: 'hash', text: trace.traceId });
  return result;
});

/**
 * The single largest contributor to the critical path, or none when the trace is one span (where
 * naming the root as its own bottleneck says nothing) or nothing was attributed.
 */
const topCriticalSpan = computed<TraceSpanRow | null>(() => {
  const spans = detail.value?.spans ?? [];
  if (spans.length < 2) {
    return null;
  }
  let leader: TraceSpanRow | null = null;
  for (const span of spans) {
    if (
      span.criticalPathNanos > 0 &&
      (leader === null || span.criticalPathNanos > leader.criticalPathNanos)
    ) {
      leader = span;
    }
  }
  return leader;
});

/**
 * The deep link to this trace's operation on the operations page, carrying the full identifying
 * triple — the name alone would resolve an inbound and an outbound call of the same name to
 * whichever came first. Null until the trace loads, since the triple comes from it.
 */
const operationLink = computed(() => {
  const trace = detail.value?.trace;
  if (!trace || !props.withOperationLink) {
    return null;
  }
  return {
    name: 'profile-traces-operations',
    params: { profileId: props.profileId },
    query: { operation: trace.rootName, kind: trace.rootKind, eventType: trace.rootEventType }
  };
});

function percentOfTrace(part: number, whole: number): string {
  if (whole <= 0) {
    return '—';
  }
  const share = (part / whole) * 100;
  return `${share.toFixed(share < 10 ? 1 : 0)}%`;
}

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
const spanWindowStartMillis = computed(() => floorToMillis(selected.value?.startEpochMicros ?? 0));

async function loadEvents(): Promise<void> {
  const span = selected.value;
  if (!span) {
    return;
  }
  eventsLoading.value = true;
  eventsError.value = null;
  try {
    const page = await new ProfileTracesClient(props.profileId).getSpanEvents(
      props.traceId,
      span.spanId
    );
    spanEvents.value = page.events;
    eventsTruncated.value = page.truncated;
  } catch {
    spanEvents.value = [];
    eventsTruncated.value = false;
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
  flamegraphAiExport.value = spanFlamegraphAiExport(span, request);

  mode.value = 'flamegraph';

  // Delay so the flamegraph is rendered and its callbacks are registered.
  setTimeout(() => {
    graphUpdater.initialize();
  }, GraphUpdater.MODAL_INIT_DELAY_MS);
}

/**
 * The same scope the graph was asked with — span, self-only, event type, filters — so the document
 * and the picture agree. Search is left to the graph, which alone knows what is typed into it.
 */
function spanFlamegraphAiExport(
  span: TraceSpanRow,
  request: TraceSpanFlamegraphRequest
): AiExportContext {
  const client = new TraceAiExportClient(props.profileId);
  const traceId = props.traceId;
  const payload = request.payload;
  return {
    graphMode: 'PRIMARY',
    filenameStem: flamegraphFilenameStem(payload.eventType, `span-${span.name}`),
    generate: () =>
      client.generateSpanFlamegraph(traceId, span.spanId, {
        selfOnly: request.selfOnly,
        eventType: payload.eventType,
        useWeight: payload.useWeight,
        useThreadMode: payload.useThreadMode,
        excludeNonJavaSamples: payload.excludeNonJavaSamples,
        excludeIdleSamples: payload.excludeIdleSamples,
        onlyUnsafeAllocationSamples: payload.onlyUnsafeAllocationSamples
      })
  };
}

/**
 * The graph can be reached from the event timeline or from the event-type chooser, so its back
 * button returns to whichever it was rather than dropping the reader all the way out to the bars.
 */
function goBack(): void {
  mode.value = mode.value === 'flamegraph' ? flamegraphOrigin.value : 'spans';
}

/**
 * Escape walks the drill-down back one level at a time — flamegraph to its origin, events to the
 * waterfall, an open inline detail to nothing — and only a press with nowhere left to step reaches
 * GenericModal and closes the dialog. Without this, Escape three levels deep discarded the whole
 * drill-down at once.
 *
 * Captured on the document because GenericModal focuses its own overlay, so a handler on this
 * component's markup never sees the key.
 */
function onEscapeCapture(event: KeyboardEvent): void {
  if (!props.show || event.key !== 'Escape') {
    return;
  }
  if (mode.value !== 'spans') {
    event.stopPropagation();
    goBack();
    return;
  }
  if (selected.value !== null) {
    event.stopPropagation();
    selected.value = null;
  }
}

onMounted(() => {
  document.addEventListener('keyup', onEscapeCapture, true);
});

onBeforeUnmount(() => {
  document.removeEventListener('keyup', onEscapeCapture, true);
});

function scrollToTop(): void {
  const wrapper = document.getElementById(TRACE_FG_SCROLL_ID);
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}

/**
 * Null until the trace is loaded, so the button cannot export a document describing nothing. The
 * rendering happens on the server, so nothing here has to know what a bundle contains.
 */
function buildAiExportSource(): AiExportSource | null {
  if (!detail.value) {
    return null;
  }
  const client = new TraceAiExportClient(props.profileId);
  const traceId = props.traceId;
  // The name leads and the id follows, truncated: in a downloads folder the operation name is what
  // a person scans for, and the id tail still tells two traces of the same operation apart.
  const idTail = traceId.slice(0, 8);
  return {
    fetch: () => client.generateTrace(traceId),
    label: 'Trace',
    filenameStem: `trace-${detail.value.trace.rootName}-${idTail}`
  };
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  selected.value = null;
  mode.value = 'spans';
  context.value = null;
  spanEvents.value = [];
  eventsTruncated.value = false;
  eventsError.value = null;
  const client = new ProfileTracesClient(props.profileId);
  try {
    detail.value = await client.getTrace(props.traceId);
  } catch {
    detail.value = null;
    error.value = 'Failed to load this trace.';
    return;
  } finally {
    loading.value = false;
  }

  // Fetched after the waterfall is already on screen, and deliberately not awaited with it: the
  // pauses come from a scan of the events table rather than the derived span tables, and the bars
  // are worth reading before the context lands. A failure here leaves the waterfall intact --
  // context is an enrichment, and losing it must not turn a readable trace into an error page.
  loadContext();
}

/** Its own function so the why-slow panel's failed state can offer a retry that refetches only this. */
async function loadContext(): Promise<void> {
  contextState.value = 'loading';
  try {
    context.value = await new ProfileTracesClient(props.profileId).getTraceContext(props.traceId);
    contextState.value = 'ready';
  } catch {
    context.value = null;
    contextState.value = 'failed';
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
/* The conclusion under the evidence, set apart so it reads as a summary rather than another row. */
.context-pane {
  border: 1px solid var(--color-border);
  border-radius: var(--card-border-radius);
  background: var(--color-bg-card);
}

.context-note {
  margin: 0;
  padding: 0.75rem 1rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.context-retry {
  margin-left: 0.5rem;
  padding: 0.15rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-card);
  color: var(--color-text);
  font: inherit;
  font-size: var(--font-size-xs);
  cursor: pointer;
}

.context-retry:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.context-retry:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* The trace's own facts on the left, the actions on the right. */
.trace-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.trace-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.trace-op-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-card);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.trace-op-link:hover {
  border-color: var(--color-primary);
}

.trace-op-link:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

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
/* Two cards, not one: the waterfall and the conclusion drawn from it each hold their own frame. */
.trace-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* The waterfall scrolls on its own so a wide trace never scrolls the modal sideways. The card
   chrome lives on the waterfall's own panels — this is only the scroll container. */
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

.ts-truncated-note {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.4rem 0.6rem;
}
</style>
